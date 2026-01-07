package com.isp.platform.billing.service;

import com.isp.platform.billing.domain.Invoice;
import com.isp.platform.billing.domain.InvoiceRepository;
import com.isp.platform.billing.domain.InvoiceStatus;
import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

    private final InvoiceRepository invoiceRepository;

    public BillingService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public Invoice generate(GenerateInvoiceRequest request) {
        // Tenant ID is automatically set by TenantEntityListener on persist
        requireTenant(); // Verify tenant context is set
        Invoice invoice = new Invoice();
        invoice.setCustomerId(request.customerId());
        invoice.setAmount(request.amount());
        invoice.setDueDate(request.dueDate());
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public List<Invoice> list() {
        UUID tenantId = requireTenant();
        return invoiceRepository.findByTenantId(tenantId);
    }

    @Transactional
    public Invoice pay(UUID invoiceId, PayRequest request) {
        UUID tenantId = requireTenant();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new ApiException("Invoice not found"));
        invoice.setStatus(InvoiceStatus.PAID);
        return invoice;
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ApiException("Tenant not resolved");
        }
        return tenantId;
    }
}
