package com.isp.platform.billing.service;

import com.isp.platform.billing.domain.Invoice;
import com.isp.platform.billing.domain.InvoiceRepository;
import com.isp.platform.billing.domain.InvoiceStatus;
import com.isp.platform.common.exception.ApiException;
import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import com.isp.platform.gateway.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final DelinquencyService delinquencyService;

    public BillingService(InvoiceRepository invoiceRepository, 
                          CustomerRepository customerRepository,
                          DelinquencyService delinquencyService) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.delinquencyService = delinquencyService;
    }

    @Transactional
    public Invoice generate(GenerateInvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.setCustomerId(request.customerId());
        invoice.setAmount(request.amount());
        invoice.setDueDate(request.dueDate());
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public List<Invoice> list() {
        return invoiceRepository.findByTenantId(requireTenant());
    }

    @Transactional
    public Invoice pay(UUID invoiceId, PayRequest request) {
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, requireTenant())
                .orElseThrow(() -> new ApiException("Invoice not found"));
        
        // Mark invoice as paid
        invoice.setStatus(InvoiceStatus.PAID);
        
        log.info("Invoice {} marked as PAID", invoiceId);
        
        // Automatically unblock customer if they have no more overdue invoices
        unblockCustomerIfPaid(invoice.getCustomerId());
        
        return invoice;
    }
    
    /**
     * Unblock customer after payment if they have no more overdue invoices.
     * 
     * @param customerId the customer ID to check and potentially unblock
     */
    private void unblockCustomerIfPaid(String customerId) {
        try {
            UUID customerUuid = UUID.fromString(customerId);
            
            // Check if customer still has overdue invoices
            boolean hasOverdue = delinquencyService.hasOverdueInvoices(customerId);
            
            if (!hasOverdue) {
                // No more overdue invoices, unblock the customer
                customerRepository.findById(customerUuid).ifPresent(customer -> {
                    if (customer.isBlocked()) {
                        customer.setBlocked(false);
                        customerRepository.save(customer);
                        log.info("Customer {} automatically unblocked after payment", customerUuid);
                    }
                });
            } else {
                log.info("Customer {} still has overdue invoices, remaining blocked", customerUuid);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid customer ID format: {}", customerId);
        }
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ApiException("Tenant not resolved");
        }
        return tenantId;
    }
}
