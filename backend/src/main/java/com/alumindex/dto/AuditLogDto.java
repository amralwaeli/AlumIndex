package com.alumindex.dto;

import com.alumindex.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        UUID userId,
        String userEmail,
        UUID tenantId,
        String actionType,
        String actionDetails,
        Instant actionTime) {

    /** Must be called inside an open session (user/tenant may be lazy). */
    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(
                a.getId(),
                a.getUser() != null ? a.getUser().getId() : null,
                a.getUser() != null ? a.getUser().getEmail() : null,
                a.getTenant() != null ? a.getTenant().getId() : null,
                a.getActionType(),
                a.getActionDetails(),
                a.getActionTime());
    }
}
