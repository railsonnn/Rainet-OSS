package com.isp.platform.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Plan entities.
 */
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    
    List<Plan> findByTenantIdAndActiveTrue(UUID tenantId);
    
    Optional<Plan> findByIdAndTenantId(UUID id, UUID tenantId);
}
