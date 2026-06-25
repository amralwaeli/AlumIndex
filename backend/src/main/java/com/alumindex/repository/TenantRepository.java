package com.alumindex.repository;

import com.alumindex.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByAdminEmail(String adminEmail);
    boolean existsByAdminEmail(String adminEmail);

    @Query("""
        SELECT t FROM Tenant t
        WHERE t.subscriptionEnd < :now
          AND t.autoSuspendOnExpiry = true
          AND t.subscriptionStatus = :status
        """)
    List<Tenant> findExpiredForAutoSuspend(@Param("now") Instant now,
                                           @Param("status") Tenant.SubscriptionStatus status);

    @Query("""
        SELECT COUNT(t) FROM Tenant t
        WHERE t.subscriptionEnd IS NOT NULL
          AND t.subscriptionEnd >= :start
          AND t.subscriptionEnd < :end
          AND t.subscriptionStatus = :status
        """)
    long countExpiringSoon(@Param("start") Instant start, @Param("end") Instant end,
                           @Param("status") Tenant.SubscriptionStatus status);
}
