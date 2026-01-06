package com.isp.platform.common.domain;

import java.util.UUID;

public interface TenantAware {
    UUID getTenantId();
    void setTenantId(UUID tenantId);
}
