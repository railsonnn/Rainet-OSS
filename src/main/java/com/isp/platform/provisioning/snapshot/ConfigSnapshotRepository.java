package com.isp.platform.provisioning.snapshot;

import com.isp.platform.provisioning.domain.Router;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigSnapshotRepository extends JpaRepository<ConfigSnapshot, UUID> {
    
    List<ConfigSnapshot> findByTenantId(UUID tenantId);
    
    List<ConfigSnapshot> findByRouter(Router router);
    
    List<ConfigSnapshot> findByRouterAndSnapshotTypeOrderByCreatedAtDesc(
        Router router, ConfigSnapshot.SnapshotType snapshotType);
    
    Optional<ConfigSnapshot> findTopByRouterAndSnapshotTypeOrderByCreatedAtDesc(
        Router router, ConfigSnapshot.SnapshotType snapshotType);
    
    List<ConfigSnapshot> findByRouterOrderByCreatedAtDesc(Router router);
}
