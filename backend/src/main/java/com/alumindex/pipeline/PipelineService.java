package com.alumindex.pipeline;

import com.alumindex.entity.*;
import com.alumindex.pipeline.LlmNormalisationService.LlmUnavailableException;
import com.alumindex.pipeline.LlmNormalisationService.NormalisedRow;
import com.alumindex.pipeline.MatchingService.*;
import com.alumindex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the 5-step import pipeline asynchronously.
 * Each row is fully isolated: an LLM or matching failure marks that row failed
 * and processing continues for the rest of the batch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineService {

    private final LlmNormalisationService llm;
    private final MatchingService matcher;
    private final AlumniRepository alumniRepo;
    private final AlumniProfileRepository profileRepo;
    private final ProfileSnapshotRepository snapshotRepo;
    private final CareerEventRepository eventRepo;
    private final ImportBatchRepository batchRepo;
    private final CsvXlsxParser parser;
    private final HeaderMapper headerMapper;

    /** Concurrent normalisation workers. Keep ≤ DB pool size and within your OpenAI rate tier. */
    @Value("${alumindex.import.concurrency:8}")
    private int concurrency;

    /** Rows held in memory at once. Keeps peak heap bounded regardless of file size. */
    @Value("${alumindex.import.chunk-size:500}")
    private int chunkSize;

    /** Rows normalised per OpenAI call. Higher = fewer/cheaper calls, larger prompts. */
    @Value("${alumindex.import.llm-batch-size:20}")
    private int llmBatchSize;

    /** Per-run tallies, shared across chunk workers via atomics. */
    private static final class Counters {
        final AtomicInteger inserted  = new AtomicInteger();
        final AtomicInteger updated    = new AtomicInteger();
        final AtomicInteger unchanged  = new AtomicInteger();
        final AtomicInteger failed     = new AtomicInteger();
        final AtomicInteger processed  = new AtomicInteger();
    }

    /**
     * Streaming import — reads the uploaded file from {@code file} in bounded chunks so peak
     * heap is one chunk (~{@code chunk-size} rows) regardless of a 1K or 1M-row file. Each row
     * is mapped with {@code mapping}, exact-duplicate rows within a chunk are collapsed, and the
     * chunk is processed by the worker pool before the next chunk is read. The temp file is
     * deleted when the run finishes (success or failure).
     */
    @Async
    public void runAsync(UUID tenantId, UUID batchId, Path file, String filename,
                         Map<String, String> mapping, Tenant tenant) {
        var batch = batchRepo.findById(batchId).orElseThrow();
        final UUID ctxTenant = com.alumindex.common.TenantContext.get();
        var errorLog = Collections.synchronizedList(new ArrayList<Object>());
        var llmDown  = new AtomicBoolean(false);
        var c        = new Counters();
        int workers  = Math.max(1, concurrency);
        var pool      = Executors.newFixedThreadPool(workers);
        var normCache = new java.util.concurrent.ConcurrentHashMap<String, NormalisedRow>();
        String today  = LocalDate.now().toString();

        var chunk = new ArrayList<Map<String, String>>(chunkSize);
        try {
            parser.stream(file, filename, new ArrayList<>(), raw -> {
                Map<String, String> mapped = headerMapper.mapRow(raw, mapping, today);
                if (mapped == null) return;          // all-blank row dropped
                chunk.add(mapped);
                if (chunk.size() >= chunkSize) {
                    processChunk(chunk, tenantId, tenant, llmDown, pool, workers, c, errorLog, ctxTenant, normCache);
                    saveProgress(batch, c);
                    chunk.clear();
                }
            });
            if (!chunk.isEmpty()) {
                processChunk(chunk, tenantId, tenant, llmDown, pool, workers, c, errorLog, ctxTenant, normCache);
                chunk.clear();
            }
        } catch (Exception e) {
            log.error("Import batch {} failed while streaming: {}", batchId, e.getMessage(), e);
            errorLog.add(0, Map.of("row", "*", "code", "PARSE_ERROR",
                    "error", "Failed to read file: " + e.getMessage()));
            finalizeBatch(batch, c, errorLog, llmDown, ImportBatch.Status.failed);
            pool.shutdown();
            deleteQuietly(file);
            return;
        }
        pool.shutdown();
        deleteQuietly(file);
        finalizeBatch(batch, c, errorLog, llmDown, ImportBatch.Status.completed);
        log.info("Batch {} completed: +{} ~{} ={} !{}", batchId,
                c.inserted.get(), c.updated.get(), c.unchanged.get(), c.failed.get());
    }

    /**
     * In-memory import — for callers that already hold the mapped rows (and tests). Processes the
     * list in the same bounded chunks as the streaming path.
     */
    @Async
    public void runAsync(UUID tenantId, UUID batchId,
                         List<Map<String, String>> rows, Tenant tenant) {
        var batch = batchRepo.findById(batchId).orElseThrow();
        final UUID ctxTenant = com.alumindex.common.TenantContext.get();
        var errorLog = Collections.synchronizedList(new ArrayList<Object>());
        var llmDown  = new AtomicBoolean(false);
        var c        = new Counters();
        int workers  = Math.max(1, concurrency);
        var pool      = Executors.newFixedThreadPool(workers);
        var normCache = new java.util.concurrent.ConcurrentHashMap<String, NormalisedRow>();
        try {
            for (int i = 0; i < rows.size(); i += chunkSize) {
                var chunk = new ArrayList<>(rows.subList(i, Math.min(rows.size(), i + chunkSize)));
                processChunk(chunk, tenantId, tenant, llmDown, pool, workers, c, errorLog, ctxTenant, normCache);
                saveProgress(batch, c);
            }
        } finally {
            pool.shutdown();
        }
        finalizeBatch(batch, c, errorLog, llmDown, ImportBatch.Status.completed);
        log.info("Batch {} completed (in-memory): +{} ~{} ={} !{}", batchId,
                c.inserted.get(), c.updated.get(), c.unchanged.get(), c.failed.get());
    }

    /**
     * Processes one chunk: collapse exact-duplicate rows (counted "unchanged"), partition the
     * rest by identity so same-person rows share a bucket (prevents the duplicate-insert race),
     * then run the buckets on the shared pool and block until the chunk is done.
     */
    private void processChunk(List<Map<String, String>> chunk, UUID tenantId, Tenant tenant,
                              AtomicBoolean llmDown, ExecutorService pool, int workers,
                              Counters c, List<Object> errorLog, UUID ctxTenant,
                              Map<String, NormalisedRow> normCache) {
        var seen   = new HashSet<String>();
        var unique = new ArrayList<Map<String, String>>(chunk.size());
        for (var raw : chunk) {
            if (seen.add(rawRowKey(raw))) unique.add(raw);
        }
        int dupes = chunk.size() - unique.size();
        if (dupes > 0) {
            c.unchanged.addAndGet(dupes);
            c.processed.addAndGet(dupes);
        }
        if (unique.isEmpty()) return;

        // Step 2 — normalise the whole chunk up-front: far fewer OpenAI round-trips (batched),
        // a whole-row cache across chunks, and a per-row deterministic fallback.
        Map<String, NormalisedRow> normByKey = normaliseChunk(unique, llmDown, pool, normCache);

        int n = Math.max(1, Math.min(workers, unique.size()));
        List<List<Map<String, String>>> buckets = partitionByIdentity(unique, n);
        var latch = new CountDownLatch(buckets.size());
        for (var bucket : buckets) {
            pool.submit(() -> {
                if (ctxTenant != null) com.alumindex.common.TenantContext.set(ctxTenant);
                try {
                    for (var raw : bucket) {
                        NormalisedRow norm = normByKey.get(rawRowKey(raw));
                        if (norm == null) norm = localNormalise(raw);   // safety net
                        processOne(tenantId, raw, norm, tenant, c, errorLog);
                    }
                } finally {
                    com.alumindex.common.TenantContext.clear();
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Normalises a chunk's unique rows with a minimum of OpenAI calls: whole-row cache hits are
     * reused, misses are sent in batches of {@code llm-batch-size} (one HTTP call per batch, run
     * concurrently on the pool), and any row the LLM can't return falls back to {@link #localNormalise}.
     * Returns a map keyed by {@link #rawRowKey}. Trips {@code llmDown} when OpenAI is unavailable.
     */
    private Map<String, NormalisedRow> normaliseChunk(List<Map<String, String>> unique,
                                                      AtomicBoolean llmDown, ExecutorService pool,
                                                      Map<String, NormalisedRow> cache) {
        var result = new java.util.concurrent.ConcurrentHashMap<String, NormalisedRow>(unique.size());
        var misses = new ArrayList<Map<String, String>>();
        for (var raw : unique) {
            NormalisedRow cached = cache.get(rawRowKey(raw));
            if (cached != null) result.put(rawRowKey(raw), cached);
            else misses.add(raw);
        }
        if (misses.isEmpty()) return result;

        // OpenAI already proven down → skip calls, normalise deterministically.
        if (llmDown.get()) {
            for (var raw : misses) put(result, cache, raw, localNormalise(raw));
            return result;
        }

        int batch = Math.max(1, llmBatchSize);
        var subBatches = new ArrayList<List<Map<String, String>>>();
        for (int i = 0; i < misses.size(); i += batch) {
            subBatches.add(misses.subList(i, Math.min(misses.size(), i + batch)));
        }
        var latch = new CountDownLatch(subBatches.size());
        for (var sb : subBatches) {
            pool.submit(() -> {
                try {
                    List<NormalisedRow> norms = null;
                    try {
                        norms = llm.normaliseBatch(sb);
                    } catch (LlmUnavailableException e) {
                        llmDown.set(true);
                        log.warn("LLM unavailable — switching to deterministic normalisation: {}",
                                e.getMessage());
                    }
                    for (int i = 0; i < sb.size(); i++) {
                        var raw = sb.get(i);
                        NormalisedRow nr = (norms != null && i < norms.size() && norms.get(i) != null)
                                ? norms.get(i) : localNormalise(raw);
                        put(result, cache, raw, nr);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    private static void put(Map<String, NormalisedRow> result, Map<String, NormalisedRow> cache,
                            Map<String, String> raw, NormalisedRow nr) {
        String key = rawRowKey(raw);
        cache.put(key, nr);
        result.put(key, nr);
    }

    /** Processes a single (already-normalised) row, tallying the outcome and logging failures. */
    private void processOne(UUID tenantId, Map<String, String> raw, NormalisedRow norm,
                            Tenant tenant, Counters c, List<Object> errorLog) {
        try {
            switch (processRow(tenantId, raw, norm, tenant)) {
                case "inserted"  -> c.inserted.incrementAndGet();
                case "updated"   -> c.updated.incrementAndGet();
                case "unchanged" -> c.unchanged.incrementAndGet();
                default          -> c.failed.incrementAndGet();
            }
        } catch (AmbiguousMatchException e) {
            c.failed.incrementAndGet();
            errorLog.add(Map.of("row", raw.getOrDefault("full_name", "?"), "error", "AMBIGUOUS_NAME"));
        } catch (Exception e) {
            c.failed.incrementAndGet();
            errorLog.add(Map.of("row", raw.getOrDefault("full_name", "?"),
                    "error", String.valueOf(e.getMessage())));
            log.warn("Row processing error: {}", e.getMessage());
        } finally {
            c.processed.incrementAndGet();
        }
    }

    /** Live progress flush — runs only on the orchestrator thread (single writer). */
    private void saveProgress(ImportBatch batch, Counters c) {
        batch.setProcessedCount(c.processed.get());
        batch.setInsertedCount(c.inserted.get());
        batch.setUpdatedCount(c.updated.get());
        batch.setUnchangedCount(c.unchanged.get());
        batch.setFailedCount(c.failed.get());
        batchRepo.save(batch);
    }

    /** Writes the final counts + status; surfaces the LLM-fallback notice for operators. */
    private void finalizeBatch(ImportBatch batch, Counters c, List<Object> errorLog,
                               AtomicBoolean llmDown, ImportBatch.Status status) {
        if (llmDown.get()) {
            errorLog.add(0, Map.of("row", "*", "code", "LLM_FALLBACK",
                    "error", "OpenAI was unavailable — rows imported with basic rule-based "
                           + "normalisation (lower quality). Check your OpenAI API key/quota and re-import."));
        }
        batch.setRecordCount(c.processed.get());
        batch.setProcessedCount(c.processed.get());
        batch.setInsertedCount(c.inserted.get());
        batch.setUpdatedCount(c.updated.get());
        batch.setUnchangedCount(c.unchanged.get());
        batch.setFailedCount(c.failed.get());
        batch.setStatus(status);
        batch.setErrorLog(errorLog.isEmpty() ? null : new ArrayList<>(errorLog));
        batchRepo.save(batch);
    }

    private static void deleteQuietly(Path file) {
        try {
            if (file != null) Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete temp import file {}: {}", file, e.getMessage());
        }
    }

    /** Stable content key for a raw row — used to collapse byte-identical rows in a file. */
    private static String rawRowKey(Map<String, String> raw) {
        return new TreeMap<>(raw).toString();
    }

    /** Buckets rows so all rows for the same person share a bucket (processed in order). */
    private List<List<Map<String, String>>> partitionByIdentity(
            List<Map<String, String>> rows, int buckets) {
        var out = new ArrayList<List<Map<String, String>>>(buckets);
        for (int i = 0; i < buckets; i++) out.add(new ArrayList<>());
        for (var raw : rows) {
            out.get(Math.floorMod(identityKey(raw).hashCode(), buckets)).add(raw);
        }
        return out;
    }

    /**
     * Stable key for the person a row refers to, mirroring the matcher's keys:
     * linkedin_url first, else name (+ grad year). Rows with no identity can't
     * collide as "the same person", so they're spread for load balance.
     */
    private static String identityKey(Map<String, String> raw) {
        String li = raw.getOrDefault("linkedin_url", "").trim().toLowerCase(Locale.ROOT);
        if (!li.isBlank()) return "li:" + li;
        String name = stripTitles(firstNonBlank(raw, "full_name"));
        if (name != null) {
            return "nm:" + name.toLowerCase(Locale.ROOT)
                    + "|" + raw.getOrDefault("education_end_year", "").trim();
        }
        return "anon:" + System.identityHashCode(raw);
    }

    @Transactional
    String processRow(UUID tenantId, Map<String, String> raw, NormalisedRow norm, Tenant tenant) {
        // Normalisation (step 2) is done up-front per chunk — batched OpenAI calls with a
        // whole-row cache and deterministic fallback — so this method only matches + writes.
        String linkedinUrl  = raw.getOrDefault("linkedin_url", "").trim();
        Integer gradYear    = parseYear(raw.get("education_end_year"));

        // Step 3 — Matching (internal only). Merge only on the strong LinkedIn key:
        // name / grad-year collisions are NOT auto-merged, so distinct people who share
        // a name stay as separate records during a bulk import.
        MatchResult match = matcher.matchByLinkedin(tenantId,
                linkedinUrl.isBlank() ? null : linkedinUrl);

        return switch (match) {
            case Ambiguous a -> throw new AmbiguousMatchException();

            case NotFound __ -> {
                // Step 4a — insert
                var alumni = Alumni.builder()
                        .tenant(tenant)
                        .fullName(norm.fullName())
                        .linkedinUrl(linkedinUrl.isBlank() ? null : linkedinUrl)
                        .educationEndYear(gradYear)
                        .universityName(raw.get("university_name"))
                        .build();
                alumniRepo.save(alumni);

                profileRepo.save(AlumniProfile.builder()
                        .alumni(alumni)
                        .employer(norm.employer())
                        .jobTitle(norm.jobTitle())
                        .seniority(norm.seniority())
                        .industry(norm.industry())
                        .location(norm.location())
                        .confidenceScore(norm.confidenceScore())
                        .build());

                snapshotRepo.save(ProfileSnapshot.builder()
                        .alumni(alumni)
                        .rawSourceData(new LinkedHashMap<>(raw))
                        .extractedFields(toExtractedFields(norm))
                        .build());

                yield "inserted";
            }

            case Found f -> {
                var existing = profileRepo.findByAlumniId(f.alumni().getId()).orElse(null);
                if (existing == null) {
                    profileRepo.save(AlumniProfile.builder()
                            .alumni(f.alumni()).employer(norm.employer())
                            .jobTitle(norm.jobTitle()).seniority(norm.seniority())
                            .industry(norm.industry()).location(norm.location())
                            .confidenceScore(norm.confidenceScore()).build());
                    yield "inserted";
                }

                boolean changed = hasChanged(existing, norm);
                if (!changed) yield "unchanged";

                // Step 4b — update
                detectAndSaveCareerEvents(f.alumni(), existing, norm);

                existing.setEmployer(norm.employer());
                existing.setJobTitle(norm.jobTitle());
                existing.setSeniority(norm.seniority());
                existing.setIndustry(norm.industry());
                existing.setLocation(norm.location());
                existing.setConfidenceScore(norm.confidenceScore());
                profileRepo.save(existing);

                snapshotRepo.save(ProfileSnapshot.builder()
                        .alumni(f.alumni())
                        .rawSourceData(new LinkedHashMap<>(raw))
                        .extractedFields(toExtractedFields(norm))
                        .build());

                yield "updated";
            }
        };
    }

    private boolean hasChanged(AlumniProfile p, NormalisedRow n) {
        return !Objects.equals(p.getEmployer(),  n.employer())
            || !Objects.equals(p.getJobTitle(),  n.jobTitle())
            || !Objects.equals(p.getSeniority(), n.seniority())
            || !Objects.equals(p.getIndustry(),  n.industry())
            || !Objects.equals(p.getLocation(),  n.location());
    }

    private void detectAndSaveCareerEvents(Alumni alumni, AlumniProfile old, NormalisedRow n) {
        // Employer change → high significance
        if (!Objects.equals(old.getEmployer(), n.employer()) && n.employer() != null) {
            eventRepo.save(CareerEvent.builder()
                    .alumni(alumni)
                    .eventType(CareerEvent.EventType.employer_change)
                    .oldValue(old.getEmployer())
                    .newValue(n.employer())
                    .significanceLevel(CareerEvent.SignificanceLevel.high)
                    .build());
        }

        // Seniority increase → promotion, high
        if (!Objects.equals(old.getSeniority(), n.seniority())
                && seniorityRank(n.seniority()) > seniorityRank(old.getSeniority())) {
            eventRepo.save(CareerEvent.builder()
                    .alumni(alumni)
                    .eventType(CareerEvent.EventType.promotion)
                    .oldValue(old.getSeniority())
                    .newValue(n.seniority())
                    .significanceLevel(CareerEvent.SignificanceLevel.high)
                    .build());
        } else if (!Objects.equals(old.getJobTitle(), n.jobTitle())) {
            // Job title change without seniority increase → job_change
            eventRepo.save(CareerEvent.builder()
                    .alumni(alumni)
                    .eventType(CareerEvent.EventType.job_change)
                    .oldValue(old.getJobTitle())
                    .newValue(n.jobTitle())
                    .significanceLevel(CareerEvent.SignificanceLevel.medium)
                    .build());
        }
    }

    private int seniorityRank(String s) {
        if (s == null) return 0;
        return switch (s) {
            case "Junior"    -> 1;
            case "Mid-Level" -> 2;
            case "Senior"    -> 3;
            case "Lead"      -> 4;
            case "Manager"   -> 5;
            case "Director"  -> 6;
            case "VP"        -> 7;
            case "C-Suite"   -> 8;
            default          -> 0;
        };
    }

    private Map<String, Object> toExtractedFields(NormalisedRow n) {
        var m = new LinkedHashMap<String, Object>();
        m.put("full_name",        n.fullName());
        m.put("employer",         n.employer());
        m.put("job_title",        n.jobTitle());
        m.put("seniority",        n.seniority());
        m.put("industry",         n.industry());
        m.put("location",         n.location());
        m.put("confidence_score", n.confidenceScore());
        return m;
    }

    private Integer parseYear(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    // ── Deterministic normalisation fallback (used when OpenAI is unavailable) ──

    static NormalisedRow localNormalise(Map<String, String> raw) {
        String title = firstNonBlank(raw, "employment_title", "job_title");
        return new NormalisedRow(
                stripTitles(firstNonBlank(raw, "full_name")),
                firstNonBlank(raw, "employment_company", "company_standardized_name", "employer"),
                title,
                inferSeniority(title),
                firstNonBlank(raw, "company_industry", "industry"),
                joinLocation(raw),
                new BigDecimal("0.50"));
    }

    private static String firstNonBlank(Map<String, String> raw, String... keys) {
        for (String k : keys) {
            String v = raw.get(k);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static String stripTitles(String name) {
        if (name == null) return null;
        return name.replaceFirst(
                "(?i)^((dr|prof|professor|mr|mrs|ms|ir|hj|haji|datuk|dato|tan sri)\\.?\\s+)+", "")
                .trim();
    }

    static String inferSeniority(String title) {
        if (title == null || title.isBlank()) return null;
        String t = title.toLowerCase(Locale.ROOT);
        if (t.matches(".*\\b(chief|ceo|cto|cfo|coo|cio|founder|president)\\b.*")) return "C-Suite";
        if (t.contains("vice president") || t.matches(".*\\bvp\\b.*"))            return "VP";
        if (t.contains("director") || t.contains("head of"))                      return "Director";
        if (t.contains("manager"))                                                return "Manager";
        if (t.contains("lead") || t.contains("principal"))                        return "Lead";
        if (t.contains("senior") || t.matches(".*\\bsr\\b.*"))                    return "Senior";
        if (t.contains("junior") || t.contains("intern") || t.contains("trainee")
                || t.matches(".*\\bjr\\b.*"))                                     return "Junior";
        return "Mid-Level";
    }

    private static String joinLocation(Map<String, String> raw) {
        var parts = new ArrayList<String>();
        for (String k : new String[]{"location_city", "location_state", "location_country", "location"}) {
            String v = raw.get(k);
            if (v != null && !v.isBlank()) parts.add(v.trim());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    static class AmbiguousMatchException extends RuntimeException {
        AmbiguousMatchException() { super("AMBIGUOUS_NAME"); }
    }
}
