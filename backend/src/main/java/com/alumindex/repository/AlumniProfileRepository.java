package com.alumindex.repository;

import com.alumindex.entity.AlumniProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlumniProfileRepository extends JpaRepository<AlumniProfile, UUID> {

    Optional<AlumniProfile> findByAlumniId(UUID alumniId);

    @Query("""
        SELECT COUNT(p) FROM AlumniProfile p
        WHERE p.alumni.tenant.id = :tid AND p.employer IS NOT NULL
          AND (:year IS NULL OR p.alumni.educationEndYear = :year)
        """)
    long countEmployedByTenantId(@Param("tid") UUID tenantId, @Param("year") Integer year);

    @Query("""
        SELECT COUNT(p) FROM AlumniProfile p
        WHERE p.alumni.tenant.id = :tid
          AND p.seniority IN ('Director','VP','C-Suite')
          AND (:year IS NULL OR p.alumni.educationEndYear = :year)
        """)
    long countHighSeniorityByTenantId(@Param("tid") UUID tenantId, @Param("year") Integer year);

    @Query("""
        SELECT p.seniority, COUNT(p) FROM AlumniProfile p
        WHERE p.alumni.tenant.id = :tid AND p.seniority IS NOT NULL
          AND (:year IS NULL OR p.alumni.educationEndYear = :year)
        GROUP BY p.seniority
        ORDER BY COUNT(p) DESC
        """)
    List<Object[]> countBySeniorityForTenant(@Param("tid") UUID tenantId, @Param("year") Integer year);

    @Query("""
        SELECT p.industry, COUNT(p) FROM AlumniProfile p
        WHERE p.alumni.tenant.id = :tid AND p.industry IS NOT NULL
          AND (:year IS NULL OR p.alumni.educationEndYear = :year)
        GROUP BY p.industry
        ORDER BY COUNT(p) DESC
        """)
    List<Object[]> countByIndustryForTenant(@Param("tid") UUID tenantId, @Param("year") Integer year);

    @Query("SELECT p FROM AlumniProfile p JOIN FETCH p.alumni WHERE p.alumni.tenant.id = :tid")
    List<AlumniProfile> findByTenantIdWithAlumni(@Param("tid") UUID tenantId);
}
