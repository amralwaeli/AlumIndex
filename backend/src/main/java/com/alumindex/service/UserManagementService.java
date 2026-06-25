package com.alumindex.service;

import com.alumindex.common.TenantContext;
import com.alumindex.dto.UserDto;
import com.alumindex.entity.ActivationToken;
import com.alumindex.entity.User;
import com.alumindex.exception.BadRequestException;
import com.alumindex.exception.ConflictException;
import com.alumindex.exception.ResourceNotFoundException;
import com.alumindex.exception.TenantAccessException;
import com.alumindex.repository.ActivationTokenRepository;
import com.alumindex.repository.TenantRepository;
import com.alumindex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepo;
    private final TenantRepository tenantRepo;
    private final ActivationTokenRepository activationTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        return userRepo.findByTenantId(requireTenantId()).stream()
                .map(UserDto::from)
                .toList();
    }

    /**
     * Creates a team member in pending_activation status, generates an activation token,
     * and sends an activation email. The entire operation is atomic: if the email fails
     * to send, the user and token are not persisted (transaction rolls back).
     */
    @Transactional
    public UserDto inviteUser(String email, String role, UUID actorId) {
        UUID tenantId = requireTenantId();

        if (userRepo.existsByEmailAndTenantId(email, tenantId)) {
            throw new ConflictException("A user with this email already exists in this institution");
        }
        if (userRepo.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists on the platform");
        }

        var tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new TenantAccessException("Tenant not found"));

        // Domain lock: invited email must share the same domain as the inviting admin
        User actor = userRepo.findById(actorId)
                .orElseThrow(() -> new TenantAccessException("Actor not found"));
        String actorDomain = actor.getEmail().contains("@")
                ? actor.getEmail().substring(actor.getEmail().indexOf('@') + 1).toLowerCase()
                : "";
        String invitedDomain = email.contains("@")
                ? email.substring(email.indexOf('@') + 1).toLowerCase()
                : "";
        if (!actorDomain.isBlank() && !invitedDomain.equals(actorDomain)) {
            throw new BadRequestException("email_domain_mismatch");
        }

        long occupiedSeats = userRepo.countByTenantId(tenantId);
        if (occupiedSeats >= tenant.getSeatLimit()) {
            throw new BadRequestException("seat_limit_reached");
        }

        var user = User.builder()
                .tenant(tenant)
                .fullName(email.split("@")[0])
                .email(email)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(User.Role.valueOf(role))
                .status(User.Status.pending_activation)
                .build();
        user = userRepo.save(user);

        var activationToken = ActivationToken.builder()
                .user(user)
                .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                .build();
        activationTokenRepo.save(activationToken);

        // Synchronous — throws if email fails, rolling back user + token
        mailService.sendActivationInvite(
                user.getEmail(),
                tenant.getInstitutionName(),
                activationToken.getToken().toString());

        return UserDto.from(user);
    }

    @Transactional
    public UserDto changeRole(UUID targetId, String newRole, UUID actorId) {
        UUID tenantId = requireTenantId();
        User target = loadInTenant(targetId, tenantId);

        if (targetId.equals(actorId)) {
            throw new BadRequestException("cannot_change_own_role");
        }

        User.Role newRoleEnum = User.Role.valueOf(newRole);
        if (target.getRole() == User.Role.admin && newRoleEnum != User.Role.admin) {
            long adminCount = userRepo.countByTenantIdAndRoleAndStatus(
                    tenantId, User.Role.admin, User.Status.active);
            if (adminCount <= 1) throw new BadRequestException("last_admin");
        }

        target.setRole(newRoleEnum);
        return UserDto.from(userRepo.save(target));
    }

    @Transactional
    public UserDto deactivate(UUID targetId, UUID actorId) {
        UUID tenantId = requireTenantId();
        User target = loadInTenant(targetId, tenantId);

        if (targetId.equals(actorId)) {
            throw new BadRequestException("cannot_deactivate_self");
        }
        if (target.getRole() == User.Role.admin) {
            long adminCount = userRepo.countByTenantIdAndRoleAndStatus(
                    tenantId, User.Role.admin, User.Status.active);
            if (adminCount <= 1) throw new BadRequestException("last_admin");
        }

        target.setStatus(User.Status.inactive);
        return UserDto.from(userRepo.save(target));
    }

    @Transactional
    public UserDto reactivate(UUID targetId) {
        UUID tenantId = requireTenantId();
        User target = loadInTenant(targetId, tenantId);
        target.setStatus(User.Status.active);
        return UserDto.from(userRepo.save(target));
    }

    @Transactional(readOnly = true)
    public long getSeatLimit() {
        return tenantRepo.findById(requireTenantId())
                .map(t -> (long) t.getSeatLimit())
                .orElse(5L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User loadInTenant(UUID userId, UUID tenantId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (u.getTenant() == null || !u.getTenant().getId().equals(tenantId)) {
            throw new TenantAccessException("User not in this tenant");
        }
        return u;
    }

    private UUID requireTenantId() {
        UUID id = TenantContext.get();
        if (id == null) throw new TenantAccessException("No tenant context");
        return id;
    }
}
