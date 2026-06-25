package com.alumindex.controller;

import com.alumindex.dto.UserDto;
import com.alumindex.security.AlumIndexPrincipal;
import com.alumindex.service.AuditService;
import com.alumindex.service.UserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserManagementService userService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> list() {
        return ResponseEntity.ok(userService.listUsers());
    }

    @GetMapping("/seat-limit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> seatInfo() {
        long limit = userService.getSeatLimit();
        long used  = userService.listUsers().size();
        return ResponseEntity.ok(Map.of("used", used, "limit", limit));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> invite(
            @Valid @RequestBody InviteRequest body,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        UserDto dto = userService.inviteUser(body.email(), body.role(), principal.getUserId());
        auditService.log(principal, "USER_INVITED",
                "Invited " + body.email() + " as " + body.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleRequest body,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        UserDto dto = userService.changeRole(id, body.role(), principal.getUserId());
        auditService.log(principal, "USER_ROLE_CHANGED",
                "Changed role of user " + id + " to " + body.role());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> deactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        UserDto dto = userService.deactivate(id, principal.getUserId());
        auditService.log(principal, "USER_DEACTIVATED", "Deactivated user " + id);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> reactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        UserDto dto = userService.reactivate(id);
        auditService.log(principal, "USER_REACTIVATED", "Reactivated user " + id);
        return ResponseEntity.ok(dto);
    }

    record InviteRequest(@Email @NotBlank String email, @NotBlank String role) {}
    record RoleRequest(@NotBlank String role) {}
}
