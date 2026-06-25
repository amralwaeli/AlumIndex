package com.alumindex.service;

import com.alumindex.entity.*;
import com.alumindex.exception.ConflictException;
import com.alumindex.exception.ResourceNotFoundException;
import com.alumindex.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantRepository tenantRepo;
    @Mock UserRepository userRepo;
    @Mock InviteTokenRepository tokenRepo;
    @Mock CustomerRequestRepository requestRepo;
    @Mock DataPermissionRepository permRepo;
    @Mock ImportBatchRepository batchRepo;
    @Mock ActivationTokenRepository activationTokenRepo;
    @Mock MailService mailService;
    @InjectMocks TenantService svc;

    // ── invite() ──────────────────────────────────────────────────────────────

    @Test
    void invite_saves_token_and_returns_token_string() {
        when(tokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = svc.invite("new@uni.edu", "New University");

        assertThat(result).isNotBlank();
        verify(tokenRepo).save(any(InviteToken.class));
        verify(mailService).sendInvite(eq("new@uni.edu"), eq("New University"), anyString());
    }

    @Test
    void invite_replaces_existing_token_and_resends() {
        when(tokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = svc.invite("dup@uni.edu", "Uni");

        verify(tokenRepo).deleteByEmailAndUsedFalse("dup@uni.edu");
        assertThat(result).isNotBlank();
        verify(mailService).sendInvite(eq("dup@uni.edu"), eq("Uni"), anyString());
    }

    // ── approve() ─────────────────────────────────────────────────────────────

    @Test
    void approve_creates_tenant_user_and_permissions() {
        UUID reqId = UUID.randomUUID();
        var req = CustomerRequest.builder()
                .id(reqId)
                .name("Dean Smith")
                .email("dean@uni.edu")
                .institution("Uni Malaysia")
                .jobTitle("Dean")
                .status(CustomerRequest.Status.pending)
                .build();

        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));
        when(tenantRepo.save(any())).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            // simulate id assigned
            return t;
        });
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(permRepo.saveAll(any())).thenReturn(List.of());
        when(activationTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.approve(reqId, "2025-01-01", "2026-01-01", true);

        verify(tenantRepo).save(any(Tenant.class));
        verify(userRepo).save(any(User.class));
        verify(permRepo).saveAll(any());
        verify(activationTokenRepo).save(any(ActivationToken.class));
        verify(mailService).sendActivationInvite(eq("dean@uni.edu"), eq("Uni Malaysia"), anyString());
        assertThat(req.getStatus()).isEqualTo(CustomerRequest.Status.approved);
    }

    @Test
    void approve_already_processed_throws_409() {
        UUID reqId = UUID.randomUUID();
        var req = CustomerRequest.builder()
                .id(reqId)
                .status(CustomerRequest.Status.approved)
                .build();
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> svc.approve(reqId, "2025-01-01", "2026-01-01", true))
                .isInstanceOf(ConflictException.class);
    }

    // ── deny() ────────────────────────────────────────────────────────────────

    @Test
    void deny_marks_request_denied() {
        UUID reqId = UUID.randomUUID();
        var req = CustomerRequest.builder()
                .id(reqId)
                .email("someone@uni.edu")
                .institution("Some Uni")
                .status(CustomerRequest.Status.pending)
                .build();
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));

        svc.deny(reqId);

        assertThat(req.getStatus()).isEqualTo(CustomerRequest.Status.denied);
        verify(mailService).sendDenial(eq("someone@uni.edu"), eq("Some Uni"));
    }

    @Test
    void deny_unknown_request_throws_404() {
        UUID reqId = UUID.randomUUID();
        when(requestRepo.findById(reqId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.deny(reqId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── validateToken() ───────────────────────────────────────────────────────

    @Test
    void expired_token_throws_410() {
        UUID tokenId = UUID.randomUUID();
        var token = InviteToken.builder()
                .token(tokenId)
                .email("e@uni.edu")
                .expiresAt(Instant.now().minusSeconds(60))
                .used(false)
                .build();
        when(tokenRepo.findByToken(tokenId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> svc.validateToken(tokenId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void used_token_throws_410() {
        UUID tokenId = UUID.randomUUID();
        var token = InviteToken.builder()
                .token(tokenId)
                .email("e@uni.edu")
                .expiresAt(Instant.now().plusSeconds(600))
                .used(true)
                .build();
        when(tokenRepo.findByToken(tokenId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> svc.validateToken(tokenId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
