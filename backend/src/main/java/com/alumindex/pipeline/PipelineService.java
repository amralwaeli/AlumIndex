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

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    /** Concurrent normalisation workers. Keep ≤ DB pool size and within your OpenAI rate tier. */
    @Value("${alumindex.import.concurrency:8}")
    private int concurrency;

    @Async
    public void runAsync(UUID tenantId, UUID batchId,
                         List<Map<String, String>> rows, Tenant tenant) {
        var batch = batchRepo.findById(batchId).orElseThrow();
        // worker threads don't inherit this thread's ThreadLocal — capture the
        // tenant context now so each worker resolves RLS exactly as we do here.
        final UUID ctxTenant = com.alumindex.common.TenantContext.get();

        var errorLog  = Collections.synchronizedList(new ArrayList<Object>());
        // circuit breaker: once OpenAI proves unavailable, remaining rows go
        // straight to the deterministic fallback instead of retrying every row.
        var llmDown   = new AtomicBoolean(false);
        var inserted  = new AtomicInteger();
        var updated   = new AtomicInteger();
        var unchanged = new AtomicInteger();
        var failed    = new AtomicInteger();
        var processed = new AtomicInteger();

        int total = rows.size();

        // Pre-pass — collapse exact-duplicate rows within the file. The first occurrence
        // is processed; byte-identical copies are counted "unchanged" with no DB work, so
        // duplicated/re-uploaded rows never create spurious updates or extra records.
        var seenRaw    = new HashSet<String>();
        var uniqueRows = new ArrayList<Map<String, String>>(total);
        for (var raw : rows) {
            if (seenRaw.add(rawRowKey(raw))) uniqueRows.add(raw);
        }
        int exactDuplicates = total - uniqueRows.size();
        if (exactDuplicates > 0) {
            unchanged.addAndGet(exactDuplicates);
            processed.addAndGet(exactDuplicates);
        }

        int workers = Math.max(1, Math.min(concurrency, Math.max(1, uniqueRows.size())));

        // Partition the unique rows by identity so every row referring to the same person
        // lands in one bucket and is processed in order. This keeps the insert-then-update
        // semantics and prevents two threads from inserting the same person twice (the race).
        List<List<Map<String, String>>> buckets = partitionByIdentity(uniqueRows, workers);

        var pool  = Executors.newFixedThreadPool(workers);
        var latch = new CountDownLatch(buckets.size());
        try {
            for (var bucket : buckets) {
                pool.submit(() -> {
                    if (ctxTenant != null) com.alumindex.common.TenantContext.set(ctxTenant);
                    try {
                        for (Map<String, String> raw : bucket) {
                            try {
                                switch (processRow(tenantId, raw, tenant, llmDown)) {
                                    case "inserted"  -> inserted.incrementAndGet();
                                    case "updated"   -> updated.incrementAndGet();
                                    case "unchanged" -> unchanged.incrementAndGet();
                                    default          -> failed.incrementAndGet();
                                }
                            } catch (LlmUnavailableException e) {
                                failed.incrementAndGet();
                                errorLog.add(Map.of("row", raw.getOrDefault("full_name", "?"),
                                        "error", "OpenAI API unavailable"));
                                log.warn("LLM unavailable for row {}: {}",
                                        raw.get("full_name"), e.getMessage());
                            } catch (AmbiguousMatchException e) {
                                failed.incrementAndGet();
                                errorLog.add(Map.of("row", raw.getOrDefault("full_name", "?"),
                                        "error", "AMBIGUOUS_NAME"));
                            } catch (Exception e) {
                                failed.incrementAndGet();
                                errorLog.add(Map.of("row", raw.getOrDefault("full_name", "?"),
                                        "error", e.getMessage()));
                                log.warn("Row processing error: {}", e.getMessage());
                            } finally {
                                processed.incrementAndGet();
                            }
                        }
                    } finally {
                        com.alumindex.common.TenantContext.clear();
                        latch.countDown();
                    }
                });
            }

            // Single-writer progress: only this thread mutates/saves the batch
            // entity, so the shared JPA entity is never touched by two threads.
            while (!latch.await(750, TimeUnit.MILLISECONDS)) {
                saveProgress(batch, processed, inserted, updated, unchanged, failed);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdown();
        }

        // non-fatal, but operators must see it (Overview + import card)
        if (llmDown.get()) {
            errorLog.add(0, Map.of("row", "*",
                    "code", "LLM_FALLBACK",
                    "error", "OpenAI was unavailable — rows imported with basic rule-based "
                           + "normalisation (lower quality). Check your OpenAI API key/quota and re-import."));
        }

        batch.setRecordCount(total);
        batch.setProcessedCount(processed.get());
        batch.setInsertedCount(inserted.get());
        batch.setUpdatedCount(updated.get());
        batch.setUnchangedCount(unchanged.get());
        batch.setFailedCount(failed.get());
        batch.setStatus(ImportBatch.Status.completed);
        batch.setErrorLog(errorLog.isEmpty() ? null : new ArrayList<>(errorLog));
        batchRepo.save(batch);
        log.info("Batch {} completed: +{} ~{} ={} !{}", batchId,
                inserted.get(), updated.get(), unchanged.get(), failed.get());
    }

    /** Live progress flush — runs only on the orchestrator thread (single writer). */
    private void saveProgress(ImportBatch batch, AtomicInteger processed, AtomicInteger inserted,
                              AtomicInteger updated, AtomicInteger unchanged, AtomicInteger failed) {
        batch.setProcessedCount(processed.get());
        batch.setInsertedCount(inserted.get());
        batch.setUpdatedCount(updated.get());
        batch.setUnchangedCount(unchanged.get());
        batch.setFailedCount(failed.get());
        batchRepo.save(batch);
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
    String processRow(UUID tenantId, Map<String, String> raw, Tenant tenant,
                      java.util.concurrent.atomic.AtomicBoolean llmDown) {
        // Step 2 — LLM normalisation (always before matching). If OpenAI is
        // unavailable the row is normalised deterministically from the mapped
        // columns so the import still succeeds (lower confidence score).
        NormalisedRow norm;
        if (llmDown.get()) {
            norm = localNormalise(raw);
        } else {
            try {
                norm = llm.normalise(raw);
            } catch (LlmUnavailableException e) {
                llmDown.set(true);
                log.warn("LLM unavailable — switching batch to deterministic normalisation: {}",
                        e.getMessage());
                norm = localNormalise(raw);
            }
        }

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
