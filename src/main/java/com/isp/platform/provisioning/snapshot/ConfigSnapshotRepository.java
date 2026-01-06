package com.isp.platform.provisioning.snapshot;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigSnapshotRepository extends JpaRepository<ConfigSnapshot, UUID> {
    List<ConfigSnapshot> findByTenantId(UUID tenantId);
}
