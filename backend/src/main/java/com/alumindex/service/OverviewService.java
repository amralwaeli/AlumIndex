package com.alumindex.service;

import com.alumindex.entity.CustomerRequest;
import com.alumindex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OverviewService {

    private final TenantRepository tenantRepo;
    private final AlumniRepository alumniRepo;
    private final ImportBatchRepository batchRepo;
    private final CustomerRequestRepository requestRepo;
    private final AuditLogRepository auditRepo;

    public record ActivityItem(
            String id, String actionType, String actionDetails,
            String actionTime, String tenantName, String userEmail) {}

    public record BatchSummary(
            String id, String tenantName, String filename,
            String status, int recordCount, int processedCount, int failedCount,
            String errorSummary, String uploadedAt) {}

    public record OverviewResponse(
            long totalUniversities,
            long totalAlumni,
            long importsThisMonth,
            long pendingRequests,
            long tenantsExpiringSoon,
            List<ActivityItem> recentActivity,
            List<BatchSummary> recentBatches,
            long failedRowsThisMonth) {}

    @Transactional(readOnly = true)
    public OverviewResponse getOverview() {
        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        Instant endOfMonth = LocalDate.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        Instant now = Instant.now();
        long totalUniversities = tenantRepo.count();
        long totalAlumni = alumniRepo.count();
        long importsThisMonth = batchRepo.countByUploadedAtBetween(startOfMonth, endOfMonth);
        long pendingRequests = requestRepo.countByStatus(CustomerRequest.Status.pending);
        Long failedSum = batchRepo.sumFailedRowsThisMonth(startOfMonth, endOfMonth);
        long failedRowsThisMonth = failedSum != null ? failedSum : 0L;
        long tenantsExpiringSoon = tenantRepo.countExpiringSoon(
                now, now.plus(30, ChronoUnit.DAYS), com.alumindex.entity.Tenant.SubscriptionStatus.active);

        List<ActivityItem> recentActivity = auditRepo.findTop10AllByOrderByActionTimeDesc().stream()
                .map(a -> new ActivityItem(
                        a.getId().toString(),
                        a.getActionType(),
                        a.getActionDetails(),
                        a.getActionTime().toString(),
                        a.getTenant() != null ? a.getTenant().getInstitutionName() : null,
                        a.getUser() != null ? a.getUser().getEmail() : null))
                .toList();

        List<BatchSummary> recentBatches = batchRepo.findTop5AllByOrderByUploadedAtDesc().stream()
                .map(b -> new BatchSummary(
                        b.getId().toString(),
                        b.getTenant().getInstitutionName(),
                        b.getFilename(),
                        b.getStatus().name(),
                        b.getRecordCount(),
                        b.getProcessedCount(),
                        b.getFailedCount(),
                        summariseErrors(b.getErrorLog()),
                        b.getUploadedAt().toString()))
                .toList();

        return new OverviewResponse(
                totalUniversities,
                totalAlumni,
                importsThisMonth,
                pendingRequests,
                tenantsExpiringSoon,
                recentActivity,
                recentBatches,
                failedRowsThisMonth);
    }

    /** Distinct error reasons from a batch error log, at most two, joined. */
    private static String summariseErrors(List<Object> errorLog) {
        if (errorLog == null || errorLog.isEmpty()) return null;
        List<String> reasons = errorLog.stream()
                .filter(e -> e instanceof java.util.Map)
                .map(e -> ((java.util.Map<?, ?>) e).get("error"))
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .limit(2)
                .toList();
        return reasons.isEmpty() ? null : String.join(" · ", reasons);
    }
}
