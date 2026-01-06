package com.isp.platform.gateway.tenant;

import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID getCurrentTenant() {
        return TENANT.get();
    }

    public static void setCurrentTenant(UUID tenantId) {
        TENANT.set(tenantId);
    }

    public static void clear() {
        TENANT.remove();
    }
}
