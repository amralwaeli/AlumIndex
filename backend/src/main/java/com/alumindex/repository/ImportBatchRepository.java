package com.alumindex.repository;

import com.alumindex.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    List<ImportBatch> findByTenantIdOrderByUploadedAtDesc(UUID tenantId);

    @Query("""
        SELECT b FROM ImportBatch b
        WHERE b.tenant.id = :tenantId
        ORDER BY b.uploadedAt DESC
        LIMIT 1
        """)
    Optional<ImportBatch> findLatestByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT b FROM ImportBatch b
        JOIN FETCH b.tenant
        ORDER BY b.uploadedAt DESC
        LIMIT 5
        """)
    List<ImportBatch> findTop5AllByOrderByUploadedAtDesc();

    @Query("SELECT COUNT(b) FROM ImportBatch b WHERE b.uploadedAt >= :start AND b.uploadedAt < :end")
    long countByUploadedAtBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT SUM(b.failedCount) FROM ImportBatch b WHERE b.uploadedAt >= :start AND b.uploadedAt < :end")
    Long sumFailedRowsThisMonth(@Param("start") Instant start, @Param("end") Instant end);
}
