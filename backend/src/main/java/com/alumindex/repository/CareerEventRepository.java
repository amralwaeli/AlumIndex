package com.alumindex.repository;

import com.alumindex.entity.CareerEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareerEventRepository extends JpaRepository<CareerEvent, UUID> {

    List<CareerEvent> findByAlumniIdOrderByDetectedAtDesc(UUID alumniId);

    /**
     * Fetch-joins alumni AND its profile: Alumni.profile is a mappedBy one-to-one,
     * which Hibernate cannot lazy-proxy — without the fetch join every loaded
     * alumni fires an extra profile SELECT (N+1; ~23s for 200 events on a
     * remote pooler).
     */
    @Query("""
        SELECT e FROM CareerEvent e
        JOIN FETCH e.alumni a
        LEFT JOIN FETCH a.profile
        WHERE a.tenant.id = :tenantId
          AND e.significanceLevel = :level
          AND e.dismissed = false
          AND (:year IS NULL OR a.educationEndYear = :year)
        ORDER BY e.detectedAt DESC
        """)
    List<CareerEvent> findByTenantIdAndSignificanceLevel(
            @Param("tenantId") UUID tenantId,
            @Param("level") CareerEvent.SignificanceLevel level,
            @Param("year") Integer year);

    default List<CareerEvent> findHighSignificanceByTenantId(UUID tenantId) {
        return findByTenantIdAndSignificanceLevel(tenantId, CareerEvent.SignificanceLevel.high, null);
    }

    @Query("SELECT COUNT(e) FROM CareerEvent e WHERE e.alumni.tenant.id = :tenantId")
    long countByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT COUNT(e) FROM CareerEvent e
        WHERE e.alumni.tenant.id = :tenantId
          AND (:year IS NULL OR e.alumni.educationEndYear = :year)
        """)
    long countByTenantIdAndYear(@Param("tenantId") UUID tenantId, @Param("year") Integer year);

    @Query("""
        SELECT COUNT(e) FROM CareerEvent e
        WHERE e.alumni.tenant.id = :tenantId
          AND e.significanceLevel = :level
          AND e.dismissed = false
        """)
    long countUnreadByTenantIdAndLevel(
            @Param("tenantId") UUID tenantId,
            @Param("level") CareerEvent.SignificanceLevel level);

    default long countUnreadByTenantId(UUID tenantId) {
        return countUnreadByTenantIdAndLevel(tenantId, CareerEvent.SignificanceLevel.high);
    }

    @Query("SELECT e FROM CareerEvent e WHERE e.id = :id AND e.alumni.tenant.id = :tenantId")
    Optional<CareerEvent> findByIdInTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("""
        SELECT e FROM CareerEvent e
        JOIN FETCH e.alumni a
        LEFT JOIN FETCH a.profile
        WHERE a.tenant.id = :tenantId
        ORDER BY e.detectedAt DESC
        """)
    List<CareerEvent> findAllByTenantIdWithAlumni(@Param("tenantId") UUID tenantId);
}
