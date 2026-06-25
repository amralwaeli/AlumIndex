package com.alumindex.service;

import com.alumindex.entity.*;
import com.alumindex.exception.BadRequestException;
import com.alumindex.exception.ConflictException;
import com.alumindex.exception.ResourceNotFoundException;
import com.alumindex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final List<String> ALL_PERMISSION_KEYS = List.of(
        "current_employment","location_linkedin","employer_type","historical_employment",
        "nonprofit_boards","corp_matching","salary","donation_pred","property","sec_stock",
        "biz_email","personal_email","seniority","news","monthly","midyear","multiyear",
        "ultra_conf","company_id","exports_users","support"
    );
    private static final Set<String> DEFAULT_ON = Set.of(
        "current_employment","seniority","monthly","exports_users","support"
    );

    private final TenantRepository tenantRepo;
    private final UserRepository userRepo;
    private final InviteTokenRepository tokenRepo;
    private final CustomerRequestRepository requestRepo;
    private final DataPermissionRepository permRepo;
    private final ImportBatchRepository batchRepo;
    private final AlumniRepository alumniRepo;
    private final ActivationTokenRepository activationTokenRepo;
    private final AuditService auditService;
    private final MailService mailService;

    // ── Invite ───────────────────────────────────────────────────────────────

    @Transactional
    public String invite(String email, String organization) {
        // Emails are globally unique — block inviting an address that already has an account
        if (userRepo.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists on the platform");
        }
        // Delete any existing unused token so the operator can always resend
        tokenRepo.deleteByEmailAndUsedFalse(email);
        var token = InviteToken.builder()
                .email(email)
                .organization(organization)
                .expiresAt(Instant.now().plusSeconds(1200))
                .build();
        tokenRepo.save(token);
        mailService.sendInvite(email, organization, token.getToken().toString());
        return token.getToken().toString();
    }

    // ── Register (public) ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InviteToken validateToken(UUID tokenId) {
        var token = tokenRepo.findByToken(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invitation token"));
        if (token.isUsed() || token.isExpired()) {
            throw new ResourceNotFoundException("Invitation has expired or already been used");
        }
        return token;
    }

    @Transactional
    public void submitRequest(UUID tokenId, String name, String jobTitle) {
        var token = validateToken(tokenId);
        var req = CustomerRequest.builder()
                .name(name)
                .email(token.getEmail())
                .institution(token.getOrganization())
                .jobTitle(jobTitle)
                .build();
        requestRepo.save(req);
        token.setUsed(true);
        tokenRepo.save(token);
    }

    // ── Approve / Deny ───────────────────────────────────────────────────────

    @Transactional
    public void approve(UUID requestId, String subscriptionStart, String subscriptionEnd,
                        boolean autoSuspendOnExpiry) {
        var req = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        if (req.getStatus() != CustomerRequest.Status.pending) {
            throw new ConflictException("Request already processed");
        }
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new ConflictException(
                    "A user with email " + req.getEmail() + " already exists on the platform");
        }

        Instant start = LocalDate.parse(subscriptionStart).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = LocalDate.parse(subscriptionEnd).atStartOfDay(ZoneOffset.UTC).toInstant();

        var tenant = Tenant.builder()
                .institutionName(req.getInstitution())
                .adminName(req.getName())
                .adminEmail(req.getEmail())
                .subscriptionStart(start)
                .subscriptionEnd(end)
                .autoSuspendOnExpiry(autoSuspendOnExpiry)
                .build();
        tenantRepo.save(tenant);

        seedDefaultPermissions(tenant);

        var adminUser = User.builder()
                .tenant(tenant)
                .fullName(req.getName())
                .email(req.getEmail())
                .passwordHash("")
                .role(User.Role.admin)
                .status(User.Status.pending_activation)
                .build();
        userRepo.save(adminUser);

        var activationToken = ActivationToken.builder()
                .user(adminUser)
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .build();
        activationTokenRepo.save(activationToken);

        req.setStatus(CustomerRequest.Status.approved);
        requestRepo.save(req);

        // Synchronous — throws and rolls back the whole transaction if email fails
        mailService.sendActivationInvite(req.getEmail(), req.getInstitution(),
                activationToken.getToken().toString());
    }

    @Transactional
    public void deny(UUID requestId) {
        var req = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        if (req.getStatus() != CustomerRequest.Status.pending) {
            throw new ConflictException("Request already processed");
        }
        req.setStatus(CustomerRequest.Status.denied);
        requestRepo.save(req);
        mailService.sendDenial(req.getEmail(), req.getInstitution());
    }

    // ── Tenant management ────────────────────────────────────────────────────

    @Transactional
    public void renew(UUID tenantId, String newEndDate) {
        Tenant t = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        t.setSubscriptionEnd(LocalDate.parse(newEndDate).atStartOfDay(ZoneOffset.UTC).toInstant());
        if (t.getSubscriptionStatus() == Tenant.SubscriptionStatus.expired) {
            t.setSubscriptionStatus(Tenant.SubscriptionStatus.active);
        }
        tenantRepo.save(t);
        mailService.sendRenewal(t.getAdminEmail(), t.getInstitutionName(), t.getSubscriptionEnd());
    }

    @Transactional
    public void suspend(UUID tenantId) {
        Tenant t = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        if (t.getSubscriptionStatus() != Tenant.SubscriptionStatus.active) {
            throw new ConflictException("Tenant is not active");
        }
        t.setSubscriptionStatus(Tenant.SubscriptionStatus.suspended);
        tenantRepo.save(t);
        mailService.sendSuspension(t.getAdminEmail(), t.getInstitutionName());
    }

    @Transactional
    public void reactivate(UUID tenantId) {
        Tenant t = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        if (t.getSubscriptionStatus() == Tenant.SubscriptionStatus.active) {
            throw new ConflictException("Tenant is already active");
        }
        t.setSubscriptionStatus(Tenant.SubscriptionStatus.active);
        tenantRepo.save(t);
        mailService.sendReactivation(t.getAdminEmail(), t.getInstitutionName());
    }

    @Transactional
    public void offboard(UUID tenantId, String confirmationName, UUID actorUserId) {
        Tenant t = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        if (!t.getInstitutionName().equalsIgnoreCase(confirmationName.trim())) {
            throw new BadRequestException("confirmation_name_mismatch");
        }
        long alumniCount = alumniRepo.countByTenantId(tenantId);
        String name = t.getInstitutionName();
        String email = t.getAdminEmail();
        // Write audit BEFORE delete — REQUIRES_NEW commits it in a separate transaction
        // so it persists even after the tenant row is deleted and FK is set NULL
        auditService.log(tenantId, actorUserId, "TENANT_OFFBOARDED",
                "Institution: " + name + ", Alumni erased: " + alumniCount + ", Admin: " + email);
        mailService.sendOffboarding(email, name);
        tenantRepo.delete(t);
    }

    // ── Auto-expire tenants (called by scheduler) ────────────────────────────

    @Transactional
    public List<Tenant> autoExpireExpired() {
        List<Tenant> toExpire = tenantRepo.findExpiredForAutoSuspend(
                Instant.now(), Tenant.SubscriptionStatus.active);
        toExpire.forEach(t -> t.setSubscriptionStatus(Tenant.SubscriptionStatus.expired));
        return tenantRepo.saveAll(toExpire);
    }

    // ── Customers list ───────────────────────────────────────────────────────

    public record CustomerSummary(
            UUID id,
            String institutionName,
            String adminEmail,
            String subscriptionStatus,
            String subscriptionStart,
            String subscriptionEnd,
            boolean autoSuspendOnExpiry,
            long alumniCount,
            long userCount,
            String lastImportAt,
            String createdAt) {}

    @Transactional(readOnly = true)
    public List<CustomerSummary> listAllCustomers() {
        return tenantRepo.findAll().stream()
                .sorted(Comparator.comparing(Tenant::getCreatedAt).reversed())
                .map(t -> new CustomerSummary(
                        t.getId(),
                        t.getInstitutionName(),
                        t.getAdminEmail(),
                        t.getSubscriptionStatus().name(),
                        t.getSubscriptionStart() != null ? t.getSubscriptionStart().toString() : null,
                        t.getSubscriptionEnd()   != null ? t.getSubscriptionEnd().toString()   : null,
                        t.isAutoSuspendOnExpiry(),
                        alumniRepo.countByTenantId(t.getId()),
                        userRepo.countByTenantId(t.getId()),
                        batchRepo.findLatestByTenantId(t.getId())
                                .map(b -> b.getUploadedAt().toString())
                                .orElse(null),
                        t.getCreatedAt().toString()
                ))
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public void seedDefaultPermissions(Tenant tenant) {
        var perms = ALL_PERMISSION_KEYS.stream()
                .map(key -> DataPermission.builder()
                        .tenant(tenant)
                        .permissionKey(key)
                        .enabled(DEFAULT_ON.contains(key))
                        .build())
                .toList();
        permRepo.saveAll(perms);
    }
}
