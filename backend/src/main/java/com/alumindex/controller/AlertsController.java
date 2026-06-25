package com.alumindex.controller;

import com.alumindex.dto.CareerEventDto;
import com.alumindex.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alerts are surfaced high-significance career events.
 * Type filter: job_change | employer_change | promotion (mapped from event_type).
 * Dismissed alerts are excluded from the list and the unread count.
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<List<CareerEventDto>> alerts(
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(analyticsService.getAlerts(type));
    }

    @PutMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable UUID id) {
        analyticsService.dismissAlert(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", analyticsService.getUnreadAlertCount()));
    }
}
