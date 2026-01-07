package com.isp.platform.provisioning.service;

import com.isp.platform.audit.domain.AuditLog;
import com.isp.platform.audit.service.AuditLogService;
import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.provisioning.domain.Router;
import com.isp.platform.provisioning.domain.RouterRepository;
import com.isp.platform.provisioning.mikrotik.RouterOsExecutor;
import com.isp.platform.provisioning.mikrotik.RouterOsScriptGenerator;
import com.isp.platform.provisioning.snapshot.ConfigSnapshot;
import com.isp.platform.provisioning.snapshot.ConfigSnapshotRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProvisioningService {

    private final RouterRepository routerRepository;
    private final RouterOsScriptGenerator scriptGenerator;
    private final RouterOsExecutor executor;
    private final ConfigSnapshotRepository snapshotRepository;
    private final AuditLogService auditLogService;

    public ProvisioningService(
            RouterRepository routerRepository,
            RouterOsScriptGenerator scriptGenerator,
            RouterOsExecutor executor,
            ConfigSnapshotRepository snapshotRepository,
            AuditLogService auditLogService) {
        this.routerRepository = routerRepository;
        this.scriptGenerator = scriptGenerator;
        this.executor = executor;
        this.snapshotRepository = snapshotRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public String preview(ProvisioningRequest request) {
        Router router = findRouterForTenant(request.routerId());
        return scriptGenerator.generateProvisioningScript(router);
    }

    @Transactional
    public UUID apply(ProvisioningRequest request, String actor) {
        Router router = findRouterForTenant(request.routerId());
        String script = scriptGenerator.generateProvisioningScript(router);
        
        try {
            executor.apply(script);

            ConfigSnapshot snapshot = new ConfigSnapshot();
            snapshot.setRouter(router);
            snapshot.setConfigScript(script);
            snapshot.setDescription(request.description());
            snapshot.setAppliedBy(actor);
            snapshotRepository.save(snapshot);
            
            // Log successful provisioning
            auditLogService.logProvisioning(
                actor,
                AuditLog.AuditAction.PROVISIONING_APPLY,
                router.getId().toString(),
                request.description(),
                AuditLog.AuditStatus.SUCCESS
            );
            
            return snapshot.getId();
        } catch (Exception e) {
            // Log failed provisioning
            auditLogService.logProvisioning(
                actor,
                AuditLog.AuditAction.PROVISIONING_APPLY,
                router.getId().toString(),
                request.description(),
                AuditLog.AuditStatus.FAILURE
            );
            throw e;
        }
    }

    @Transactional
    public void rollback(UUID snapshotId, String actor) {
        UUID tenantId = requireTenant();
        ConfigSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .filter(s -> tenantId.equals(s.getTenantId()))
                .orElseThrow(() -> new ApiException("Snapshot not found"));
        
        try {
            executor.apply(snapshot.getConfigScript());

            ConfigSnapshot rollbackLog = new ConfigSnapshot();
            rollbackLog.setRouter(snapshot.getRouter());
            rollbackLog.setConfigScript(snapshot.getConfigScript());
            rollbackLog.setDescription("Rollback by " + actor);
            rollbackLog.setAppliedBy(actor);
            snapshotRepository.save(rollbackLog);
            
            // Log successful rollback
            auditLogService.logRollback(
                actor,
                snapshot.getRouter().getId().toString(),
                snapshotId.toString(),
                AuditLog.AuditStatus.SUCCESS
            );
        } catch (Exception e) {
            // Log failed rollback
            auditLogService.logRollback(
                actor,
                snapshot.getRouter().getId().toString(),
                snapshotId.toString(),
                AuditLog.AuditStatus.FAILURE
            );
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<ConfigSnapshot> listSnapshots() {
        return snapshotRepository.findByTenantId(requireTenant());
    }

    private Router findRouterForTenant(UUID routerId) {
        return routerRepository.findByIdAndTenantId(routerId, requireTenant())
                .orElseThrow(() -> new ApiException("Router not found"));
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ApiException("Tenant not resolved");
        }
        return tenantId;
    }
}
