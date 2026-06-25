package com.alumindex.controller;

import com.alumindex.service.PermissionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Super Admin configures data permissions per tenant (spec §8, UC017).
 * All endpoints require SUPERADMIN. tenantId comes from the path.
 */
@RestController
@RequestMapping("/api/superadmin/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class PermissionsController {

    private final PermissionsService permissionsService;

    @GetMapping("/{tenantId}")
    public ResponseEntity<List<PermissionsService.PermissionDto>> list(
            @PathVariable UUID tenantId) {
        return ResponseEntity.ok(permissionsService.list(tenantId));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<PermissionsService.PermissionDto> toggle(
            @PathVariable UUID tenantId,
            @RequestBody Map<String, Object> body) {
        String key = (String) body.get("permissionKey");
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return ResponseEntity.ok(permissionsService.toggle(tenantId, key, enabled));
    }

    @PostMapping("/{tenantId}/reset")
    public ResponseEntity<List<PermissionsService.PermissionDto>> reset(
            @PathVariable UUID tenantId) {
        return ResponseEntity.ok(permissionsService.resetToDefaults(tenantId));
    }
}
