package com.alumindex.controller;

import com.alumindex.common.TenantContext;
import com.alumindex.exception.BadRequestException;
import com.alumindex.exception.TenantAccessException;
import com.alumindex.repository.AlumniProfileRepository;
import com.alumindex.repository.AuditLogRepository;
import com.alumindex.repository.CareerEventRepository;
import com.alumindex.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final AlumniProfileRepository profileRepo;
    private final CareerEventRepository eventRepo;
    private final AuditLogRepository auditRepo;
    private final AnalyticsService analyticsService;

    @GetMapping("/{type}/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@PathVariable String type) {
        UUID tid = tenantId();
        String csv = switch (type) {
            case "alumni"  -> buildAlumniCsv(tid);
            case "donors"  -> buildDonorsCsv();
            case "events"  -> buildEventsCsv(tid);
            case "audit"   -> buildAuditCsv(tid);
            case "summary" -> buildSummaryCsv();
            default -> throw new BadRequestException("Unknown report type: " + type);
        };

        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        String filename = type + "-report-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    private String buildAlumniCsv(UUID tenantId) {
        var sb = new StringBuilder("full_name,employer,job_title,seniority,industry,location,graduation_year\n");
        profileRepo.findByTenantIdWithAlumni(tenantId).forEach(p ->
                sb.append(csv(p.getAlumni().getFullName())).append(',')
                  .append(csv(p.getEmployer())).append(',')
                  .append(csv(p.getJobTitle())).append(',')
                  .append(csv(p.getSeniority())).append(',')
                  .append(csv(p.getIndustry())).append(',')
                  .append(csv(p.getLocation())).append(',')
                  .append(p.getAlumni().getEducationEndYear() != null
                          ? p.getAlumni().getEducationEndYear() : "").append('\n'));
        return sb.toString();
    }

    private String buildDonorsCsv() {
        var sb = new StringBuilder(
                "full_name,employer,giving_likelihood,capacity_min,capacity_max,wealth_indicator,employer_matching,suggested_approach\n");
        analyticsService.getDonorInsights("likelihood").forEach(d ->
                sb.append(csv(d.fullName())).append(',')
                  .append(csv(d.employer())).append(',')
                  .append(d.givingLikelihood() >= 0 ? d.givingLikelihood() : "").append(',')
                  .append(d.capacityMin() >= 0 ? d.capacityMin() : "").append(',')
                  .append(d.capacityMax() >= 0 ? d.capacityMax() : "").append(',')
                  .append(csv(d.wealthIndicator())).append(',')
                  .append(d.employerMatchingAvailable()).append(',')
                  .append(csv(d.suggestedApproach())).append('\n'));
        return sb.toString();
    }

    private String buildEventsCsv(UUID tenantId) {
        var sb = new StringBuilder("alumni_name,event_type,old_value,new_value,significance,detected_at\n");
        eventRepo.findAllByTenantIdWithAlumni(tenantId).forEach(e ->
                sb.append(csv(e.getAlumni().getFullName())).append(',')
                  .append(e.getEventType().name()).append(',')
                  .append(csv(e.getOldValue())).append(',')
                  .append(csv(e.getNewValue())).append(',')
                  .append(e.getSignificanceLevel().name()).append(',')
                  .append(e.getDetectedAt()).append('\n'));
        return sb.toString();
    }

    private String buildAuditCsv(UUID tenantId) {
        var sb = new StringBuilder("action_time,action_type,action_details,user_email\n");
        auditRepo.findByTenantIdOrderByActionTimeDesc(tenantId,
                        PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "actionTime")))
                .forEach(a ->
                        sb.append(a.getActionTime()).append(',')
                          .append(csv(a.getActionType())).append(',')
                          .append(csv(a.getActionDetails())).append(',')
                          .append(csv(a.getUser() != null ? a.getUser().getEmail() : "system")).append('\n'));
        return sb.toString();
    }

    private String buildSummaryCsv() {
        var kpis = analyticsService.getKpis(null);
        var sb = new StringBuilder("metric,value\n");
        sb.append("total_alumni,").append(kpis.totalAlumni()).append('\n')
          .append("employment_rate,").append(kpis.employmentRate()).append('\n')
          .append("career_change_alerts,").append(kpis.careerChangeAlerts()).append('\n')
          .append("high_value_prospects,").append(kpis.highValueProspects()).append('\n')
          .append('\n').append("seniority,count\n");
        analyticsService.getSeniorityDistribution(null).forEach(s ->
                sb.append(csv(s.seniority())).append(',').append(s.count()).append('\n'));
        sb.append('\n').append("industry,count\n");
        analyticsService.getIndustrySpread(null).forEach(i ->
                sb.append(csv(i.industry())).append(',').append(i.count()).append('\n'));
        return sb.toString();
    }

    private String csv(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"") || s.contains("\n")
                ? "\"" + s.replace("\"", "\"\"") + "\""
                : s;
    }

    private UUID tenantId() {
        UUID id = TenantContext.get();
        if (id == null) throw new TenantAccessException("No tenant context");
        return id;
    }
}
