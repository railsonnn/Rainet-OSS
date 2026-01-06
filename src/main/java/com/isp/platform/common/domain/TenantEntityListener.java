package com.isp.platform.common.domain;

import com.isp.platform.gateway.tenant.TenantContext;
import jakarta.persistence.PrePersist;
import java.util.UUID;

public class TenantEntityListener {
    @PrePersist
    public void applyTenant(TenantAware entity) {
        UUID currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant == null) {
            throw new IllegalStateException("Tenant context is not set for tenant-aware entity");
        }
        entity.setTenantId(currentTenant);
    }
}
