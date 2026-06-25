package com.alumindex.controller;

import com.alumindex.security.AlumIndexPrincipal;
import com.alumindex.service.AuditService;
import com.alumindex.service.SettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final AuditService auditService;

    @GetMapping("/institution")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SettingsService.InstitutionDto> getInstitution() {
        return ResponseEntity.ok(settingsService.getInstitution());
    }

    @PutMapping("/institution")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SettingsService.InstitutionDto> updateInstitution(
            @Valid @RequestBody UpdateInstitutionRequest body,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        var dto = settingsService.updateInstitution(body.primaryContact(), body.contactEmail());
        auditService.log(principal, "INSTITUTION_UPDATED", "Primary contact updated");
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/rotate-api-key")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> rotateApiKey(
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        String newKey = settingsService.rotateApiKey();
        auditService.log(principal, "API_KEY_ROTATED", null);
        return ResponseEntity.ok(Map.of("newKey", newKey));
    }

    @GetMapping("/api-key")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> revealApiKey() {
        String key = settingsService.revealApiKey();
        return key != null
                ? ResponseEntity.ok(Map.of("key", key))
                : ResponseEntity.notFound().build();
    }

    record UpdateInstitutionRequest(@NotBlank String primaryContact, @Email @NotBlank String contactEmail) {}
}
