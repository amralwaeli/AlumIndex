package com.alumindex.service;

import com.alumindex.common.TenantContext;
import com.alumindex.entity.DataPermission;
import com.alumindex.entity.Tenant;
import com.alumindex.exception.ResourceNotFoundException;
import com.alumindex.exception.TenantAccessException;
import com.alumindex.repository.DataPermissionRepository;
import com.alumindex.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PermissionsService {

    // 21 keys across 7 categories — order matches spec §8
    public static final List<String> ALL_KEYS = List.of(
        // Employment Data
        "current_employment", "location_linkedin", "employer_type", "historical_employment",
        "nonprofit_boards", "corp_matching",
        // Net Worth Data
        "salary", "donation_pred", "property", "sec_stock",
        // Contact Data
        "biz_email", "personal_email",
        // Professional Insights
        "seniority", "news",
        // Data Refresh
        "monthly", "midyear", "multiyear",
        // Verification & Matching
        "ultra_conf", "company_id",
        // Access & Support
        "exports_users", "support"
    );

    public static final Set<String> DEFAULT_ON = Set.of(
        "current_employment", "seniority", "monthly", "exports_users", "support"
    );

    private final DataPermissionRepository permRepo;
    private final TenantRepository tenantRepo;
    private final AuditService auditService;

    public record PermissionDto(String key, boolean enabled) {}

    // ── Called by the Super Admin (tenantId from path) ───────────────────────

    @Transactional(readOnly = true)
    public List<PermissionDto> list(UUID tenantId) {
        Map<String, Boolean> existing = new HashMap<>();
        permRepo.findByTenantId(tenantId).forEach(p -> existing.put(p.getPermissionKey(), p.isEnabled()));
        return ALL_KEYS.stream()
                .map(k -> new PermissionDto(k, existing.getOrDefault(k, DEFAULT_ON.contains(k))))
                .toList();
    }

    @Transactional
    public PermissionDto toggle(UUID tenantId, String key, boolean enabled) {
        if (!ALL_KEYS.contains(key)) {
            throw new ResourceNotFoundException("Unknown permission key: " + key);
        }
        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        DataPermission perm = permRepo.findByTenantIdAndPermissionKey(tenantId, key)
                .orElseGet(() -> DataPermission.builder()
                        .tenant(tenant)
                        .permissionKey(key)
                        .build());
        perm.setEnabled(enabled);
        permRepo.save(perm);

        auditService.log(tenantId, null, "PERMISSION_UPDATED", "key=" + key + " enabled=" + enabled);
        return new PermissionDto(key, enabled);
    }

    @Transactional
    public List<PermissionDto> resetToDefaults(UUID tenantId) {
        tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        permRepo.deleteByTenantId(tenantId);
        Tenant tenant = tenantRepo.getReferenceById(tenantId);

        var perms = ALL_KEYS.stream()
                .map(k -> DataPermission.builder()
                        .tenant(tenant)
                        .permissionKey(k)
                        .enabled(DEFAULT_ON.contains(k))
                        .build())
                .toList();
        permRepo.saveAll(perms);

        auditService.log(tenantId, null, "PERMISSIONS_RESET", "reset to defaults");
        return list(tenantId);
    }

    // ── Called internally (tenant from JWT context) ──────────────────────────

    @Transactional(readOnly = true)
    public List<PermissionDto> list() {
        return list(requireTenantId());
    }

    public boolean isEnabled(UUID tenantId, String key) {
        return permRepo.findByTenantIdAndPermissionKey(tenantId, key)
                .map(DataPermission::isEnabled)
                .orElse(DEFAULT_ON.contains(key));
    }

    private UUID requireTenantId() {
        UUID id = TenantContext.get();
        if (id == null) throw new TenantAccessException("No tenant context");
        return id;
    }
}
