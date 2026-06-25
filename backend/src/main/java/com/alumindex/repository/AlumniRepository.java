package com.alumindex.repository;

import com.alumindex.entity.Alumni;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlumniRepository extends JpaRepository<Alumni, UUID> {

    Optional<Alumni> findByLinkedinUrlAndTenantId(String linkedinUrl, UUID tenantId);

    List<Alumni> findByFullNameIgnoreCaseAndEducationEndYearAndTenantId(
            String fullName, Integer educationEndYear, UUID tenantId);

    List<Alumni> findByFullNameIgnoreCaseAndTenantId(String fullName, UUID tenantId);

    /**
     * `pattern` must be pre-lowercased and wrapped in % wildcards by the caller
     * (or null to skip). Binding the raw term inside CONCAT makes PostgreSQL
     * infer bytea for null params and fail with "lower(bytea) does not exist".
     *
     * Profile is fetch-joined because the mappedBy one-to-one cannot be
     * lazy-proxied — without it each row costs an extra SELECT (N+1).
     */
    @Query(value = """
        SELECT a FROM Alumni a
        LEFT JOIN FETCH a.profile p
        WHERE a.tenant.id = :tenantId
          AND (:pattern IS NULL OR LOWER(a.fullName) LIKE :pattern
               OR LOWER(p.employer) LIKE :pattern
               OR LOWER(p.jobTitle) LIKE :pattern)
          AND (:industry IS NULL OR p.industry = :industry)
          AND (:seniority IS NULL OR p.seniority = :seniority)
        """,
        countQuery = """
        SELECT COUNT(a) FROM Alumni a
        LEFT JOIN a.profile p
        WHERE a.tenant.id = :tenantId
          AND (:pattern IS NULL OR LOWER(a.fullName) LIKE :pattern
               OR LOWER(p.employer) LIKE :pattern
               OR LOWER(p.jobTitle) LIKE :pattern)
          AND (:industry IS NULL OR p.industry = :industry)
          AND (:seniority IS NULL OR p.seniority = :seniority)
        """)
    Page<Alumni> search(
            @Param("tenantId") UUID tenantId,
            @Param("pattern") String pattern,
            @Param("industry") String industry,
            @Param("seniority") String seniority,
            Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndEducationEndYear(UUID tenantId, Integer educationEndYear);

    @Query("""
        SELECT DISTINCT a.educationEndYear FROM Alumni a
        WHERE a.tenant.id = :tenantId AND a.educationEndYear IS NOT NULL
        ORDER BY a.educationEndYear DESC
        """)
    List<Integer> findDistinctYearsByTenantId(@Param("tenantId") UUID tenantId);
}
