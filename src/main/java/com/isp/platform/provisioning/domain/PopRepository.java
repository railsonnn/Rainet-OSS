package com.isp.platform.provisioning.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PopRepository extends JpaRepository<Pop, UUID> {
    List<Pop> findByTenantId(UUID tenantId);
}
