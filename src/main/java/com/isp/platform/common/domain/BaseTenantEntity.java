package com.isp.platform.common.domain;

import com.isp.platform.gateway.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(TenantEntityListener.class)
public abstract class BaseTenantEntity extends BaseEntity implements TenantAware {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Override
    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
