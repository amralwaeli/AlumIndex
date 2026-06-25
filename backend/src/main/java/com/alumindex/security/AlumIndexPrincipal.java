package com.alumindex.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.util.UUID;

/**
 * Immutable principal stored in the SecurityContext for every authenticated request.
 * Sourced from JWT claims; never from the request body.
 */
@Getter
@RequiredArgsConstructor
public class AlumIndexPrincipal implements Principal {

    private final UUID userId;
    private final String email;
    private final String role;
    private final UUID tenantId;   // null for superadmin

    @Override
    public String getName() {
        return email;
    }

    public boolean isSuperAdmin() {
        return "superadmin".equals(role);
    }
}
