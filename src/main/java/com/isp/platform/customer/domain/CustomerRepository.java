package com.isp.platform.customer.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Customer> findByDocumentAndTenantId(String document, UUID tenantId);
    List<Customer> findByTenantId(UUID tenantId);
}
