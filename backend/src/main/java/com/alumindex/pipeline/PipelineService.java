package com.alumindex.pipeline;

import com.alumindex.entity.*;
import com.alumindex.pipeline.LlmNormalisationService.LlmUnavailableException;
import com.alumindex.pipeline.LlmNormalisationService.NormalisedRow;
import com.alumindex.pipeline.MatchingService.*;
import com.alumindex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

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

    @Async
    public void runAsync(UUID tenantId, UUID batchId,
                         List<Map<String, String>> rows, Tenant tenant) {
        var batch = batchRepo.findById(batchId).orElseThrow();
        var errorLog = new ArrayList<Object>();
        // circuit breaker: once OpenAI proves unavailable, remaining rows go
        // straight to the deterministic fallback instead of retrying for 7s each
        var llmDown = new java.util.concurrent.atomic.AtomicBoolean(false);
        int inserted = 0, updated = 0, unchanged = 0, failed = 0;

        int processed = 0;
        for (Map<String, String> raw : rows) {
            try {
                var result = processRow(tenantId, raw, tenant, llmDown);
                switch (result) {
                    case "inserted"  -> inserted++;
                    case "updated"   -> updated++;
                    case "unchanged" -> unchanged++;
                    default          -> failed++;
                }
            } catch (LlmUnavailableException e) {
                failed++;
                errorLog.add(Map.of("row", raw.getOrDefault("full_name", "?"),
                        "error", "OpenAI API unavailable"));
                log.warn("LLM unavailable for row {}: {}", raw.get("full_name"), e.getMessage());
            } catch (AmbiguousMatchException e) {
                failed++;
                errorLog.add(Map.of("row", raw.getOrDefault("full_name", "?"),
                        "error", "AMBIGUOUS_NAME"));
            } catch (Exception e) {
                failed++;
                errorLog.add(Map.of("row", raw.getOrDefault("full_name", "?"),
                        "error", e.getMessage()));
                log.warn("Row processing error: {}", e.getMessage());
            }

            processed++;
            // live progress for the UI — every 5 rows to limit extra round trips
            if (processed % 5 == 0) {
                batch.setProcessedCount(processed);
                batch.setInsertedCount(inserted);
                batch.setUpdatedCount(updated);
                batch.setUnchangedCount(unchanged);
                batch.setFailedCount(failed);
                batchRepo.save(batch);
            }
        }

        // non-fatal, but operators must see it (Overview + import card)
        if (llmDown.get()) {
            errorLog.add(0, Map.of("row", "*",
                    "error", "OpenAI API unavailable — fallback normalisation used (check quota)"));
        }

        batch.setRecordCount(rows.size());
        batch.setProcessedCount(processed);
        batch.setInsertedCount(inserted);
        batch.setUpdatedCount(updated);
        batch.setUnchangedCount(unchanged);
        batch.setFailedCount(failed);
        batch.setStatus(ImportBatch.Status.completed);
        batch.setErrorLog(errorLog.isEmpty() ? null : errorLog);
        batchRepo.save(batch);
        log.info("Batch {} completed: +{} ~{} ={} !{}", batchId, inserted, updated, unchanged, failed);
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

        // Step 3 — Matching (internal only)
        MatchResult match = matcher.match(tenantId,
                linkedinUrl.isBlank() ? null : linkedinUrl,
                norm.fullName(), gradYear);

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
