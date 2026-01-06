package com.isp.platform.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantId(UUID tenantId);
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);
}
