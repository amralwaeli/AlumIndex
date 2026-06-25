package com.alumindex.controller;

import com.alumindex.entity.CustomerRequest;
import com.alumindex.repository.CustomerRequestRepository;
import com.alumindex.security.AlumIndexPrincipal;
import com.alumindex.service.AuditService;
import com.alumindex.service.OverviewService;
import com.alumindex.service.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/superadmin")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class SuperAdminController {

    private final TenantService tenantService;
    private final OverviewService overviewService;
    private final CustomerRequestRepository requestRepo;
    private final AuditService auditService;

    // ── Overview ─────────────────────────────────────────────────────────────

    @GetMapping("/overview")
    public ResponseEntity<OverviewService.OverviewResponse> overview() {
        return ResponseEntity.ok(overviewService.getOverview());
    }

    // ── Invite University ────────────────────────────────────────────────────

    @PostMapping("/invite")
    public ResponseEntity<java.util.Map<String, String>> invite(@Valid @RequestBody InviteRequest body) {
        String token = tenantService.invite(body.email(), body.organization());
        return ResponseEntity.ok(java.util.Map.of("token", token));
    }

    // ── Customers list (all statuses) ────────────────────────────────────────

    @GetMapping("/customers")
    public ResponseEntity<List<TenantService.CustomerSummary>> customers() {
        return ResponseEntity.ok(tenantService.listAllCustomers());
    }

    // ── Renew agreement ──────────────────────────────────────────────────────

    @PutMapping("/customers/{id}/renew")
    public ResponseEntity<Void> renew(
            @PathVariable UUID id,
            @Valid @RequestBody RenewRequest body,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        tenantService.renew(id, body.subscriptionEnd());
        auditService.log(id, principal.getUserId(), "TENANT_RENEWED",
                "New end date: " + body.subscriptionEnd());
        return ResponseEntity.ok().build();
    }

    // ── Suspend access ───────────────────────────────────────────────────────

    @PutMapping("/customers/{id}/suspend")
    public ResponseEntity<Void> suspend(
            @PathVariable UUID id,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        tenantService.suspend(id);
        auditService.log(id, principal.getUserId(), "TENANT_SUSPENDED",
                "Manual suspension by operator");
        return ResponseEntity.ok().build();
    }

    // ── Reactivate access ────────────────────────────────────────────────────

    @PutMapping("/customers/{id}/reactivate")
    public ResponseEntity<Void> reactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        tenantService.reactivate(id);
        auditService.log(id, principal.getUserId(), "TENANT_REACTIVATED",
                "Reactivated by operator");
        return ResponseEntity.ok().build();
    }

    // ── Offboard & delete ────────────────────────────────────────────────────

    @DeleteMapping("/customers/{id}")
    public ResponseEntity<Void> offboard(
            @PathVariable UUID id,
            @Valid @RequestBody OffboardRequest body,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        // audit log is written inside offboard() before the delete
        tenantService.offboard(id, body.confirmationName(), principal.getUserId());
        return ResponseEntity.ok().build();
    }

    // ── Pending requests ─────────────────────────────────────────────────────

    @GetMapping("/requests")
    public ResponseEntity<List<CustomerRequest>> requests(
            @RequestParam(defaultValue = "pending") String status) {
        var s = CustomerRequest.Status.valueOf(status);
        return ResponseEntity.ok(requestRepo.findByStatusOrderBySubmittedAtAsc(s));
    }

    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ApproveRequest body,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        tenantService.approve(id, body.subscriptionStart(), body.subscriptionEnd(),
                body.autoSuspendOnExpiry());
        auditService.log(null, principal.getUserId(), "REQUEST_APPROVED",
                "Request " + id + " approved, end: " + body.subscriptionEnd());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/requests/{id}/deny")
    public ResponseEntity<Void> deny(
            @PathVariable UUID id,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        tenantService.deny(id);
        auditService.log(null, principal.getUserId(), "REQUEST_DENIED",
                "Request " + id + " denied");
        return ResponseEntity.ok().build();
    }

    // ── Request records ──────────────────────────────────────────────────────

    record InviteRequest(@Email @NotBlank String email, @NotBlank String organization) {}

    record RenewRequest(@NotBlank String subscriptionEnd) {}

    record OffboardRequest(@NotBlank String confirmationName) {}

    record ApproveRequest(
            @NotBlank String subscriptionStart,
            @NotBlank String subscriptionEnd,
            boolean autoSuspendOnExpiry) {}
}
