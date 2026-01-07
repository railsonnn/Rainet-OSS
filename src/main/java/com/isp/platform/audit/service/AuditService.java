package com.isp.platform.audit.service;

import com.isp.platform.audit.domain.AuditLog;
import com.isp.platform.audit.domain.AuditLogRepository;
import com.isp.platform.gateway.tenant.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String actor, AuditLog.AuditAction action, String resourceType, String resourceId, String payload) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setPayload(payload);
        log.setStatus(AuditLog.AuditStatus.SUCCESS);
        log.setTenantId(requireTenant());
        repository.save(log);
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant not resolved for audit");
        }
        return tenantId;
    }
}
