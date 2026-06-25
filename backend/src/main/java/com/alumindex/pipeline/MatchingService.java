package com.alumindex.pipeline;

import com.alumindex.entity.Alumni;
import com.alumindex.repository.AlumniRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * L1 / L2 / L3 alumni matching — all internal, no external calls.
 *
 * Decision tree (each level skips if the preceding level matched):
 *   L1  exact linkedin_url match              → confidence ≥ 0.95
 *   L2  full_name + education_end_year match  → confidence ~0.85
 *   L3  full_name only
 *       ≥2 records → AMBIGUOUS_NAME (fail)
 *       1 record   → confidence 0.50–0.75
 *       0 records  → NO_MATCH (insert)
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final AlumniRepository alumniRepo;

    public sealed interface MatchResult permits Found, NotFound, Ambiguous {}

    public record Found(Alumni alumni, double confidence) implements MatchResult {}
    public record NotFound() implements MatchResult {}
    public record Ambiguous(String reason) implements MatchResult {}

    public MatchResult match(UUID tenantId, String linkedinUrl,
                             String fullName, Integer educationEndYear) {
        // L1 — linkedin_url exact match
        if (linkedinUrl != null && !linkedinUrl.isBlank()) {
            Optional<Alumni> l1 = alumniRepo.findByLinkedinUrlAndTenantId(linkedinUrl, tenantId);
            if (l1.isPresent()) return new Found(l1.get(), 0.97);
        }

        // L2 — full_name + education_end_year
        if (fullName != null && educationEndYear != null) {
            List<Alumni> l2 = alumniRepo
                    .findByFullNameIgnoreCaseAndEducationEndYearAndTenantId(
                            fullName, educationEndYear, tenantId);
            if (l2.size() == 1) return new Found(l2.get(0), 0.85);
            if (l2.size() > 1)  return new Ambiguous("AMBIGUOUS_NAME");
        }

        // L3 — full_name only
        if (fullName != null) {
            List<Alumni> l3 = alumniRepo.findByFullNameIgnoreCaseAndTenantId(fullName, tenantId);
            if (l3.size() == 1) return new Found(l3.get(0), 0.65);
            if (l3.size() > 1)  return new Ambiguous("AMBIGUOUS_NAME");
        }

        return new NotFound();
    }
}
