package com.alumindex.controller;

import com.alumindex.dto.UserDto;
import com.alumindex.dto.LoginRequest;
import com.alumindex.dto.LoginResponse;
import com.alumindex.repository.UserRepository;
import com.alumindex.security.AlumIndexPrincipal;
import com.alumindex.service.AuditService;
import com.alumindex.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal AlumIndexPrincipal principal) {
        return userRepository.findById(principal.getUserId())
                .map(u -> ResponseEntity.ok(UserDto.from(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AlumIndexPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest body) {
        authService.changePassword(principal.getUserId(), body.currentPassword(), body.newPassword());
        auditService.log(principal, "PASSWORD_CHANGED", null);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateMe(
            @AuthenticationPrincipal AlumIndexPrincipal principal,
            @Valid @RequestBody UpdateMeRequest body) {
        UserDto updated = authService.updateDisplayName(principal.getUserId(), body.fullName());
        auditService.log(principal, "PROFILE_UPDATED", "Full name updated");
        return ResponseEntity.ok(updated);
    }

    // ── Activation (public endpoints) ─────────────────────────────────────────

    @GetMapping("/activate/{token}")
    public ResponseEntity<Map<String, String>> getActivationInfo(@PathVariable UUID token) {
        return ResponseEntity.ok(authService.getActivationInfo(token));
    }

    @PostMapping("/activate/{token}")
    public ResponseEntity<LoginResponse> activate(
            @PathVariable UUID token,
            @Valid @RequestBody ActivateRequest body) {
        return ResponseEntity.ok(authService.activateUser(token, body.password()));
    }

    // ── Records ───────────────────────────────────────────────────────────────

    record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8) String newPassword) {}

    record UpdateMeRequest(@NotBlank String fullName) {}

    record ActivateRequest(@NotBlank @Size(min = 8) String password) {}
}
