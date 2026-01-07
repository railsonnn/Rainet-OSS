package com.isp.platform.billing.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantId(UUID tenantId);
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Invoice> findByCustomerIdAndStatus(String customerId, InvoiceStatus status);
    
    @Query("SELECT i FROM Invoice i WHERE i.status = :status AND i.dueDate < :currentDate")
    List<Invoice> findOverdueInvoices(@Param("status") InvoiceStatus status, @Param("currentDate") LocalDate currentDate);
    
    @Query("SELECT i FROM Invoice i WHERE i.customerId = :customerId AND i.status IN (com.isp.platform.billing.domain.InvoiceStatus.PENDING, com.isp.platform.billing.domain.InvoiceStatus.OVERDUE) AND i.dueDate < :currentDate")
    List<Invoice> findOverdueInvoicesByCustomer(@Param("customerId") String customerId, @Param("currentDate") LocalDate currentDate);
}
