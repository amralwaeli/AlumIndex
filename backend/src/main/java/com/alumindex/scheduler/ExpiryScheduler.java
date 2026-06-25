package com.alumindex.scheduler;

import com.alumindex.entity.Tenant;
import com.alumindex.service.AuditService;
import com.alumindex.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryScheduler {

    private final TenantService tenantService;
    private final AuditService auditService;

    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    public void expireExpiredTenants() {
        List<Tenant> expired = tenantService.autoExpireExpired();
        if (!expired.isEmpty()) {
            log.info("Expiry scheduler: auto-expired {} tenant(s)", expired.size());
        }
        expired.forEach(t -> auditService.log(t.getId(), null, "TENANT_AUTO_EXPIRED",
                "Subscription ended on " + t.getSubscriptionEnd() + "; auto-expired by scheduler"));
    }
}
