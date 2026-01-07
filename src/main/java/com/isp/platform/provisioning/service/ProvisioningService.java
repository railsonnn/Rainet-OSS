package com.isp.platform.provisioning.service;

import com.isp.platform.audit.domain.AuditLog;
import com.isp.platform.audit.service.AuditService;
import com.isp.platform.common.exception.ApiException;
import com.isp.platform.common.util.HashUtil;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ProvisioningService {

    private final RouterRepository routerRepository;
    private final RouterOsScriptGenerator scriptGenerator;
    private final RouterOsExecutor executor;
    private final ConfigSnapshotRepository snapshotRepository;
    private final ConfigSnapshotService snapshotService;
    private final AuditService auditService;

    public ProvisioningService(
            RouterRepository routerRepository,
            RouterOsScriptGenerator scriptGenerator,
            RouterOsExecutor executor,
            ConfigSnapshotRepository snapshotRepository,
            ConfigSnapshotService snapshotService,
            AuditService auditService) {
        this.routerRepository = routerRepository;
        this.scriptGenerator = scriptGenerator;
        this.executor = executor;
        this.snapshotRepository = snapshotRepository;
        this.snapshotService = snapshotService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public String preview(ProvisioningRequest request) {
        Router router = findRouterForTenant(request.routerId());
        return scriptGenerator.generateProvisioningScript(router);
    }

    @Transactional
    public UUID apply(ProvisioningRequest request, String actor) {
        Router router = findRouterForTenant(request.routerId());
        
        try {
            // Create BEFORE snapshot
            log.info("Creating BEFORE snapshot for router {} before applying changes", router.getHostname());
            ConfigSnapshot beforeSnapshot = snapshotService.createBeforeSnapshot(router, actor);
            
            // Generate and apply new configuration
            String script = scriptGenerator.generateProvisioningScript(router);
            executor.applyScript(router, script);
            
            // Create AFTER snapshot
            log.info("Creating AFTER snapshot for router {} after applying changes", router.getHostname());
            ConfigSnapshot afterSnapshot = createAfterSnapshot(router, script, request.description(), actor);
            
            // Log audit
            auditService.record(
                actor,
                AuditLog.AuditAction.PROVISIONING_APPLY,
                "Router",
                router.getId().toString(),
                String.format("Applied configuration to router %s. BEFORE snapshot: %s, AFTER snapshot: %s",
                    router.getHostname(), beforeSnapshot.getId(), afterSnapshot.getId())
            );
            
            log.info("Configuration successfully applied to router {}", router.getHostname());
            return afterSnapshot.getId();
        } catch (Exception e) {
            log.error("Failed to apply configuration to router {}", router.getHostname(), e);
            auditService.recordFailure(
                actor,
                AuditLog.AuditAction.PROVISIONING_APPLY,
                "Router",
                router.getId().toString(),
                e.getMessage()
            );
            throw new ApiException("Failed to apply configuration: " + e.getMessage());
        }
    }

    @Transactional
    public void rollback(UUID snapshotId, String actor) {
        UUID tenantId = requireTenant();
        ConfigSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .filter(s -> tenantId.equals(s.getTenantId()))
                .orElseThrow(() -> new ApiException("Snapshot not found"));
        
        // Verify we're rolling back to a BEFORE snapshot
        if (!ConfigSnapshot.SnapshotType.BEFORE.equals(snapshot.getSnapshotType())) {
            throw new ApiException("Can only rollback to BEFORE snapshots. Use snapshot ID from before configuration was applied.");
        }
        
        Router router = snapshot.getRouter();
        
        try {
            log.info("Starting rollback to snapshot {} for router {}", snapshotId, router.getHostname());
            
            // Verify snapshot integrity
            if (!snapshotService.verifySnapshot(snapshot)) {
                throw new ApiException("Snapshot integrity verification failed");
            }
            
            // Apply the BEFORE configuration directly (no diff/merge)
            executor.applyScript(router, snapshot.getConfigScript());
            
            // Create AFTER snapshot for the rollback
            ConfigSnapshot rollbackSnapshot = createAfterSnapshot(
                router,
                snapshot.getConfigScript(),
                "Rollback to snapshot " + snapshotId + " by " + actor,
                actor
            );
            
            // Log audit
            auditService.record(
                actor,
                AuditLog.AuditAction.PROVISIONING_ROLLBACK,
                "Router",
                router.getId().toString(),
                String.format("Rolled back router %s to snapshot %s (BEFORE). New AFTER snapshot: %s",
                    router.getHostname(), snapshotId, rollbackSnapshot.getId())
            );
            
            log.info("Successfully rolled back router {} to snapshot {}", router.getHostname(), snapshotId);
        } catch (Exception e) {
            log.error("Rollback failed for router {} to snapshot {}", router.getHostname(), snapshotId, e);
            auditService.recordFailure(
                actor,
                AuditLog.AuditAction.PROVISIONING_ROLLBACK,
                "Router",
                router.getId().toString(),
                e.getMessage()
            );
            throw new ApiException("Rollback failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ConfigSnapshot> listSnapshots() {
        return snapshotRepository.findByTenantId(requireTenant());
    }

    private ConfigSnapshot createAfterSnapshot(Router router, String configScript, String description, String actor) {
        String configHash = HashUtil.sha256(configScript);
        
        ConfigSnapshot snapshot = new ConfigSnapshot();
        snapshot.setRouter(router);
        snapshot.setTenantId(router.getTenantId());
        snapshot.setSnapshotType(ConfigSnapshot.SnapshotType.AFTER);
        snapshot.setConfigScript(configScript);
        snapshot.setConfigHash(configHash);
        snapshot.setDescription(description);
        snapshot.setAppliedBy(actor);
        
        return snapshotRepository.save(snapshot);
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
