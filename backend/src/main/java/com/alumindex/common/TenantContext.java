package com.alumindex.common;

import java.util.UUID;

/**
 * Thread-local holder for the current request's tenant ID.
 * Set by the JWT security filter; cleared in a servlet filter after the request.
 * Superadmin requests leave this null — their service layer handles cross-tenant access directly.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }
}
