package com.alumindex.controller;

import com.alumindex.dto.CareerEventDto;
import com.alumindex.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // UC011 — Dashboard KPIs (optional cohort year filter, Issue 6)
    @GetMapping("/api/dashboard/metrics")
    public ResponseEntity<AnalyticsService.DashboardKpis> kpis(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(analyticsService.getKpis(year));
    }

    @GetMapping("/api/dashboard/seniority")
    public ResponseEntity<List<AnalyticsService.SeniorityCount>> seniority(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(analyticsService.getSeniorityDistribution(year));
    }

    @GetMapping("/api/dashboard/industry")
    public ResponseEntity<List<AnalyticsService.IndustryCount>> industry(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(analyticsService.getIndustrySpread(year));
    }

    @GetMapping("/api/dashboard/events")
    public ResponseEntity<List<CareerEventDto>> recentEvents(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(analyticsService.getRecentEvents(year));
    }

    @GetMapping("/api/dashboard/years")
    public ResponseEntity<List<Integer>> cohortYears() {
        return ResponseEntity.ok(analyticsService.getCohortYears());
    }

    // Donor Insights (gated by permissions)
    @GetMapping("/api/donors")
    public ResponseEntity<List<AnalyticsService.DonorInsight>> donors(
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(analyticsService.getDonorInsights(sort));
    }

    // UC013 — Audit Log (admin only — enforced at service/entity level via tenant)
    @GetMapping("/api/audit-logs")
    public ResponseEntity<Page<?>> auditLogs(
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(analyticsService.getAuditLog(page));
    }
}
