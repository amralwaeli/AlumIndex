package com.alumindex.service;

import com.alumindex.entity.AuditLog;
import com.alumindex.entity.Tenant;
import com.alumindex.entity.User;
import com.alumindex.repository.AuditLogRepository;
import com.alumindex.repository.TenantRepository;
import com.alumindex.repository.UserRepository;
import com.alumindex.security.AlumIndexPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditRepo;
    private final UserRepository userRepo;
    private final TenantRepository tenantRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID tenantId, UUID userId, String actionType, String details) {
        User user = userId != null ? userRepo.findById(userId).orElse(null) : null;
        Tenant tenant = tenantId != null ? tenantRepo.findById(tenantId).orElse(null) : null;
        auditRepo.save(AuditLog.builder()
                .user(user)
                .tenant(tenant)
                .actionType(actionType)
                .actionDetails(details)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AlumIndexPrincipal actor, String actionType, String details) {
        User user = actor != null
                ? userRepo.findById(actor.getUserId()).orElse(null)
                : null;
        Tenant tenant = (actor != null && actor.getTenantId() != null)
                ? tenantRepo.findById(actor.getTenantId()).orElse(null)
                : null;

        auditRepo.save(AuditLog.builder()
                .user(user)
                .tenant(tenant)
                .actionType(actionType)
                .actionDetails(details)
                .build());
    }
}
