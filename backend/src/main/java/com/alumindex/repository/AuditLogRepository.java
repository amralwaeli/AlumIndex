package com.alumindex.repository;

import com.alumindex.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByTenantIdOrderByActionTimeDesc(UUID tenantId, Pageable pageable);

    /**
     * Fetch-joins user and tenant: rows are serialised to DTOs, and lazy
     * loading would mean 2 extra SELECTs per row on the remote pooler.
     */
    @Query(value = """
        SELECT a FROM AuditLog a
        LEFT JOIN FETCH a.user
        LEFT JOIN FETCH a.tenant
        WHERE a.tenant.id = :tenantId
        ORDER BY a.actionTime DESC
        """,
        countQuery = "SELECT COUNT(a) FROM AuditLog a WHERE a.tenant.id = :tenantId")
    Page<AuditLog> findPageByTenantIdWithUserAndTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        LEFT JOIN FETCH a.user
        LEFT JOIN FETCH a.tenant
        ORDER BY a.actionTime DESC
        LIMIT 10
        """)
    List<AuditLog> findTop10AllByOrderByActionTimeDesc();
}
