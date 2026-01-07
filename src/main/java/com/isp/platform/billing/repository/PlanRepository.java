package com.isp.platform.billing.repository;

import com.isp.platform.billing.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Plan entities.
 */
@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    
    Optional<Plan> findByName(String name);
}
