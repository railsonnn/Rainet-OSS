package com.isp.platform.customer.service;

import com.isp.platform.audit.domain.AuditLog;
import com.isp.platform.audit.service.AuditLogService;
import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final AuditLogService auditLogService;

    public CustomerService(CustomerRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Customer create(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setFullName(request.fullName());
        customer.setDocument(request.document());
        customer.setPlan(request.plan());
        customer.setStatus("ACTIVE");
        Customer savedCustomer = repository.save(customer);
        
        // Log customer creation
        auditLogService.logCustomer(
            "SYSTEM",
            AuditLog.AuditAction.CUSTOMER_CREATE,
            savedCustomer.getId().toString(),
            request,
            AuditLog.AuditStatus.SUCCESS
        );
        
        return savedCustomer;
    }

    @Transactional(readOnly = true)
    public Customer find(UUID id) {
        UUID tenantId = requireTenant();
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException("Customer not found"));
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ApiException("Tenant not resolved");
        }
        return tenantId;
    }
}
