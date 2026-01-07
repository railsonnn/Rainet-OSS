package com.isp.platform.provisioning.snapshot;

import com.isp.platform.provisioning.domain.Router;
import com.isp.platform.provisioning.mikrotik.RouterOsExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Service for managing router configuration snapshots.
 * Handles BEFORE/AFTER snapshots, hashing, and rollback operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigSnapshotService {

    private final ConfigSnapshotRepository snapshotRepository;
    private final RouterOsExecutor routerOsExecutor;

    /**
     * Create a BEFORE snapshot of current router configuration.
     *
     * @param router the router to snapshot
     * @param appliedBy user performing the action
     * @return the created snapshot
     */
    public ConfigSnapshot createBeforeSnapshot(Router router, String appliedBy) {
        log.info("Creating BEFORE snapshot for router: {}", router.getHostname());
        
        try {
            String configScript = routerOsExecutor.exportCompact(router);
            String configHash = HashUtil.sha256(configScript);
            
            ConfigSnapshot snapshot = new ConfigSnapshot();
            snapshot.setRouter(router);
            snapshot.setTenantId(router.getTenantId());
            snapshot.setSnapshotType(ConfigSnapshot.SnapshotType.BEFORE);
            snapshot.setDescription(String.format("Configuration before update on %s", router.getHostname()));
            snapshot.setConfigScript(configScript);
            snapshot.setConfigHash(configHash);
            snapshot.setAppliedBy(appliedBy);
            
            ConfigSnapshot saved = snapshotRepository.save(snapshot);
            log.info("BEFORE snapshot created with hash: {} for router: {}", configHash, router.getHostname());
            return saved;
        } catch (Exception e) {
            log.error("Failed to create BEFORE snapshot for router: {}", router.getHostname(), e);
            throw new RuntimeException("Failed to create BEFORE snapshot", e);
        }
    }

    /**
     * Create an AFTER snapshot of router configuration after applying changes.
     *
     * @param router the router to snapshot
     * @param appliedBy user performing the action
     * @return the created snapshot
     */
    public ConfigSnapshot createAfterSnapshot(Router router, String appliedBy) {
        log.info("Creating AFTER snapshot for router: {}", router.getHostname());
        
        try {
            String configScript = routerOsExecutor.exportCompact(router);
            String configHash = HashUtil.sha256(configScript);
            
            ConfigSnapshot snapshot = new ConfigSnapshot();
            snapshot.setRouter(router);
            snapshot.setTenantId(router.getTenantId());
            snapshot.setSnapshotType(ConfigSnapshot.SnapshotType.AFTER);
            snapshot.setDescription(String.format("Configuration after update on %s", router.getHostname()));
            snapshot.setConfigScript(configScript);
            snapshot.setConfigHash(configHash);
            snapshot.setAppliedBy(appliedBy);
            
            ConfigSnapshot saved = snapshotRepository.save(snapshot);
            log.info("AFTER snapshot created with hash: {} for router: {}", configHash, router.getHostname());
            return saved;
        } catch (Exception e) {
            log.error("Failed to create AFTER snapshot for router: {}", router.getHostname(), e);
            throw new RuntimeException("Failed to create AFTER snapshot", e);
        }
    }

    /**
     * Get the latest BEFORE snapshot for a router.
     *
     * @param router the router
     * @return optional containing the latest BEFORE snapshot
     */
    public Optional<ConfigSnapshot> getLatestBeforeSnapshot(Router router) {
        return snapshotRepository.findTopByRouterAndSnapshotTypeOrderByCreatedAtDesc(
            router, ConfigSnapshot.SnapshotType.BEFORE);
    }

    /**
     * Perform a rollback to a previous BEFORE snapshot.
     *
     * @param snapshotId the snapshot ID to rollback to
     * @param appliedBy user performing the rollback
     * @return the AFTER snapshot of the rollback
     */
    public ConfigSnapshot performRollback(UUID snapshotId, String appliedBy) {
        log.info("Performing rollback to snapshot ID: {}", snapshotId);
        
        ConfigSnapshot beforeSnapshot = snapshotRepository.findById(snapshotId)
            .orElseThrow(() -> new RuntimeException("Snapshot not found: " + snapshotId));
        
        if (!beforeSnapshot.getSnapshotType().equals(ConfigSnapshot.SnapshotType.BEFORE)) {
            throw new RuntimeException("Can only rollback to BEFORE snapshots");
        }
        
        try {
            Router router = beforeSnapshot.getRouter();
            
            // Apply the previous configuration
            routerOsExecutor.applyScript(router, beforeSnapshot.getConfigScript());
            
            // Create AFTER snapshot of the rollback
            ConfigSnapshot afterSnapshot = new ConfigSnapshot();
            afterSnapshot.setRouter(router);
            afterSnapshot.setTenantId(router.getTenantId());
            afterSnapshot.setSnapshotType(ConfigSnapshot.SnapshotType.AFTER);
            afterSnapshot.setDescription(String.format("Rollback to snapshot %s", snapshotId));
            afterSnapshot.setConfigScript(beforeSnapshot.getConfigScript());
            afterSnapshot.setConfigHash(beforeSnapshot.getConfigHash());
            afterSnapshot.setAppliedBy(appliedBy);
            
            ConfigSnapshot saved = snapshotRepository.save(afterSnapshot);
            log.info("Rollback completed successfully for router: {}", router.getHostname());
            return saved;
        } catch (Exception e) {
            log.error("Rollback failed for snapshot ID: {}", snapshotId, e);
            throw new RuntimeException("Rollback failed", e);
        }
    }

    /**
     * Verify snapshot integrity by comparing calculated hash.
     *
     * @param snapshot the snapshot to verify
     * @return true if hash matches
     */
    public boolean verifySnapshot(ConfigSnapshot snapshot) {
        String calculatedHash = HashUtil.sha256(snapshot.getConfigScript());
        return calculatedHash.equals(snapshot.getConfigHash());
    }
}
