package com.isp.platform.provisioning.service;

import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.provisioning.domain.Router;
import com.isp.platform.provisioning.domain.RouterRepository;
import com.isp.platform.provisioning.mikrotik.RouterOsExecutor;
import com.isp.platform.provisioning.mikrotik.RouterOsScriptGenerator;
import com.isp.platform.provisioning.snapshot.ConfigSnapshot;
import com.isp.platform.provisioning.snapshot.ConfigSnapshotRepository;
import com.isp.platform.provisioning.snapshot.ConfigSnapshotService;
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
    private final ConfigSnapshotService snapshotService;

    public ProvisioningService(
            RouterRepository routerRepository,
            RouterOsScriptGenerator scriptGenerator,
            RouterOsExecutor executor,
            ConfigSnapshotRepository snapshotRepository,
            ConfigSnapshotService snapshotService) {
        this.routerRepository = routerRepository;
        this.scriptGenerator = scriptGenerator;
        this.executor = executor;
        this.snapshotRepository = snapshotRepository;
        this.snapshotService = snapshotService;
    }

    @Transactional(readOnly = true)
    public String preview(ProvisioningRequest request) {
        Router router = findRouterForTenant(request.routerId());
        return scriptGenerator.generateProvisioningScript(router);
    }

    @Transactional
    public UUID apply(ProvisioningRequest request, String actor) {
        Router router = findRouterForTenant(request.routerId());
        
        // Create BEFORE snapshot
        snapshotService.createBeforeSnapshot(router, actor);
        
        // Apply configuration
        String script = scriptGenerator.generateProvisioningScript(router);
        executor.applyScript(router, script);
        
        // Create AFTER snapshot
        ConfigSnapshot afterSnapshot = snapshotService.createAfterSnapshot(router, actor);
        return afterSnapshot.getId();
    }

    @Transactional
    public void rollback(UUID snapshotId, String actor) {
        snapshotService.performRollback(snapshotId, actor);
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
