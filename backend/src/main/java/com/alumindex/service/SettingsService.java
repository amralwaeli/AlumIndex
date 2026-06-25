package com.alumindex.service;

import com.alumindex.common.TenantContext;
import com.alumindex.entity.Tenant;
import com.alumindex.exception.ResourceNotFoundException;
import com.alumindex.exception.TenantAccessException;
import com.alumindex.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final TenantRepository tenantRepo;

    public record InstitutionDto(
            String institutionName,
            String primaryContact,
            String contactEmail,
            String subscriptionStatus,
            String subscriptionStart,
            String subscriptionEnd,
            int seatLimit,
            boolean hasApiKey,
            String maskedApiKey,
            String allowedEmailDomain) {}

    @Transactional(readOnly = true)
    public InstitutionDto getInstitution() {
        Tenant t = loadTenant();
        return toDto(t);
    }

    @Transactional
    public InstitutionDto updateInstitution(String primaryContact, String contactEmail) {
        Tenant t = loadTenant();
        t.setPrimaryContact(primaryContact);
        t.setContactEmail(contactEmail);
        return toDto(tenantRepo.save(t));
    }

    @Transactional(readOnly = true)
    public String revealApiKey() {
        return loadTenant().getApiKey();
    }

    @Transactional
    public String rotateApiKey() {
        Tenant t = loadTenant();
        String newKey = "aix_" + UUID.randomUUID().toString().replace("-", "");
        t.setApiKey(newKey);
        tenantRepo.save(t);
        return newKey;
    }

    private InstitutionDto toDto(Tenant t) {
        String masked = t.getApiKey() != null
                ? "aix_" + "•".repeat(20)
                : null;
        return new InstitutionDto(
                t.getInstitutionName(),
                t.getPrimaryContact(),
                t.getContactEmail(),
                t.getSubscriptionStatus().name(),
                t.getSubscriptionStart() != null ? t.getSubscriptionStart().toString() : null,
                t.getSubscriptionEnd() != null ? t.getSubscriptionEnd().toString() : null,
                t.getSeatLimit(),
                t.getApiKey() != null,
                masked,
                t.getAllowedEmailDomain());
    }

    private Tenant loadTenant() {
        UUID id = TenantContext.get();
        if (id == null) throw new TenantAccessException("No tenant context");
        return tenantRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }
}
