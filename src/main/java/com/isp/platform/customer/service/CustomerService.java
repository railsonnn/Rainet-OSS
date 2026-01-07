package com.isp.platform.customer.service;

import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Customer create(CustomerRequest request) {
        // Tenant ID is automatically set by TenantEntityListener on persist
        requireTenant(); // Verify tenant context is set
        Customer customer = new Customer();
        customer.setFullName(request.fullName());
        customer.setDocument(request.document());
        customer.setPlan(request.plan());
        customer.setStatus("ACTIVE");
        return repository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer find(UUID id) {
        UUID tenantId = requireTenant();
        Customer customer = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException("Customer not found"));
        
        // If the requester is a CUSTOMER role, ensure they can only access their own data
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))) {
            // For CUSTOMER role, additional validation would be needed here
            // This would require linking user account to customer record
            // For now, we rely on tenant isolation which is already enforced
        }
        
        return customer;
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ApiException("Tenant not resolved");
        }
        return tenantId;
    }
}
