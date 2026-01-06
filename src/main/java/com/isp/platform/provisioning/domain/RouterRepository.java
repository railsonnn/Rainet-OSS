package com.isp.platform.provisioning.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouterRepository extends JpaRepository<Router, UUID> {
    List<Router> findByTenantId(UUID tenantId);
    Optional<Router> findByIdAndTenantId(UUID id, UUID tenantId);
}
