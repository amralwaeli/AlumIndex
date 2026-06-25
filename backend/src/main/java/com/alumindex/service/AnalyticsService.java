package com.alumindex.service;

import com.alumindex.common.TenantContext;
import com.alumindex.dto.AuditLogDto;
import com.alumindex.dto.CareerEventDto;
import com.alumindex.entity.CareerEvent;
import com.alumindex.exception.TenantAccessException;
import com.alumindex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AlumniRepository alumniRepo;
    private final AlumniProfileRepository profileRepo;
    private final CareerEventRepository eventRepo;
    private final AuditLogRepository auditRepo;
    private final DataPermissionRepository permRepo;

    // ── Dashboard KPIs (UC011) ────────────────────────────────────────────────

    public record DashboardKpis(
            long totalAlumni,
            BigDecimal employmentRate,
            long careerChangeAlerts,
            long highValueProspects) {}

    @Transactional(readOnly = true)
    public DashboardKpis getKpis(Integer year) {
        UUID tid = requireTenantId();
        long total = year == null
                ? alumniRepo.countByTenantId(tid)
                : alumniRepo.countByTenantIdAndEducationEndYear(tid, year);
        long employed  = profileRepo.countEmployedByTenantId(tid, year);
        long alerts    = eventRepo.countByTenantIdAndYear(tid, year);
        long prospects = profileRepo.countHighSeniorityByTenantId(tid, year);

        BigDecimal rate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(employed)
                        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);

        return new DashboardKpis(total, rate, alerts, prospects);
    }

    // ── Seniority distribution ────────────────────────────────────────────────
    public record SeniorityCount(String seniority, long count) {}

    @Transactional(readOnly = true)
    public List<SeniorityCount> getSeniorityDistribution(Integer year) {
        return profileRepo.countBySeniorityForTenant(requireTenantId(), year).stream()
                .map(r -> new SeniorityCount((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    // ── Industry spread ───────────────────────────────────────────────────────
    public record IndustryCount(String industry, long count) {}

    @Transactional(readOnly = true)
    public List<IndustryCount> getIndustrySpread(Integer year) {
        return profileRepo.countByIndustryForTenant(requireTenantId(), year).stream()
                .map(r -> new IndustryCount((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    // ── Recent high-significance events ──────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CareerEventDto> getRecentEvents(Integer year) {
        return eventRepo.findByTenantIdAndSignificanceLevel(
                        requireTenantId(), CareerEvent.SignificanceLevel.high, year).stream()
                .limit(10)
                .map(CareerEventDto::from)
                .toList();
    }

    // ── Alerts list (Issue 9) ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CareerEventDto> getAlerts(String type) {
        return eventRepo.findHighSignificanceByTenantId(requireTenantId()).stream()
                .filter(e -> type == null || type.isBlank()
                        || e.getEventType().name().equals(type))
                .map(CareerEventDto::from)
                .toList();
    }

    // ── Cohort years (Issue 6) ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Integer> getCohortYears() {
        return alumniRepo.findDistinctYearsByTenantId(requireTenantId());
    }

    // ── Alerts: dismiss + unread badge (Issue 9) ──────────────────────────────
    @Transactional
    public void dismissAlert(UUID eventId) {
        var event = eventRepo.findByIdInTenant(eventId, requireTenantId())
                .orElseThrow(() -> new TenantAccessException("Alert not found in this tenant"));
        event.setDismissed(true);
        eventRepo.save(event);
    }

    @Transactional(readOnly = true)
    public long getUnreadAlertCount() {
        return eventRepo.countUnreadByTenantId(requireTenantId());
    }

    // ── Donor Insights (UC — gated by permissions) ────────────────────────────
    public record DonorInsight(
            UUID alumniId, String fullName, String employer,
            int givingLikelihood,
            long capacityMin, long capacityMax,
            String wealthIndicator,
            boolean employerMatchingAvailable,
            String suggestedApproach) {}

    @Transactional(readOnly = true)
    public List<DonorInsight> getDonorInsights(String sort) {
        UUID tid = requireTenantId();
        boolean salaryEnabled = isPermEnabled(tid, "salary");
        boolean predEnabled   = isPermEnabled(tid, "donation_pred");

        return profileRepo.findByTenantIdWithAlumni(tid).stream()
                .filter(p -> p.getSeniority() != null)
                .map(p -> {
                    int likelihood = computeLikelihood(p.getSeniority(), salaryEnabled);
                    long[] cap = computeCapacity(p.getSeniority());
                    return new DonorInsight(
                            p.getAlumni().getId(),
                            p.getAlumni().getFullName(),
                            p.getEmployer(),
                            salaryEnabled ? likelihood : -1,
                            predEnabled ? cap[0] : -1,
                            predEnabled ? cap[1] : -1,
                            computeWealth(p.getSeniority()),
                            isCorpMatching(p.getEmployer()),
                            suggestApproach(likelihood)
                    );
                })
                .sorted((a, b) -> switch (sort == null ? "likelihood" : sort) {
                    case "capacity" -> Long.compare(b.capacityMax(), a.capacityMax());
                    case "recent"   -> 0;
                    default         -> Integer.compare(b.givingLikelihood(), a.givingLikelihood());
                })
                .toList();
    }

    // ── Audit Log (UC013) ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLog(int page) {
        // query carries its own ORDER BY; an unsorted PageRequest avoids a duplicate sort clause
        return auditRepo.findPageByTenantIdWithUserAndTenant(
                        requireTenantId(), PageRequest.of(page, 50))
                .map(AuditLogDto::from);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int computeLikelihood(String seniority, boolean salaryEnabled) {
        if (!salaryEnabled) return -1;
        return switch (seniority) {
            case "C-Suite"   -> 90;
            case "VP"        -> 80;
            case "Director"  -> 70;
            case "Manager"   -> 60;
            case "Lead"      -> 50;
            case "Senior"    -> 40;
            default          -> 25;
        };
    }

    private long[] computeCapacity(String seniority) {
        return switch (seniority) {
            case "C-Suite"   -> new long[]{300_000, 1_000_000};
            case "VP"        -> new long[]{150_000, 500_000};
            case "Director"  -> new long[]{75_000,  300_000};
            case "Manager"   -> new long[]{30_000,  150_000};
            default          -> new long[]{5_000,   50_000};
        };
    }

    private String computeWealth(String seniority) {
        return switch (seniority) {
            case "C-Suite", "VP" -> "high";
            case "Director", "Manager" -> "medium";
            default -> "low";
        };
    }

    private boolean isCorpMatching(String employer) {
        if (employer == null) return false;
        String e = employer.toLowerCase();
        return e.contains("microsoft") || e.contains("google") || e.contains("amazon")
                || e.contains("petronas") || e.contains("shell");
    }

    private String suggestApproach(int likelihood) {
        if (likelihood >= 80) return "Personal outreach from Dean";
        if (likelihood >= 60) return "Invite to alumni leadership forum";
        if (likelihood >= 40) return "Targeted campaign email";
        return "Annual newsletter";
    }

    private boolean isPermEnabled(UUID tenantId, String key) {
        return permRepo.findByTenantIdAndPermissionKey(tenantId, key)
                .map(p -> p.isEnabled()).orElse(false);
    }

    private UUID requireTenantId() {
        UUID id = TenantContext.get();
        if (id == null) throw new TenantAccessException("No tenant context");
        return id;
    }
}
