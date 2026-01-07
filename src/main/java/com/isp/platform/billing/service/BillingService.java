package com.isp.platform.billing.service;

import com.isp.platform.audit.domain.AuditLog;
import com.isp.platform.audit.service.AuditLogService;
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
    private final AuditLogService auditLogService;

    public BillingService(InvoiceRepository invoiceRepository, AuditLogService auditLogService) {
        this.invoiceRepository = invoiceRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Invoice generate(GenerateInvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.setCustomerId(request.customerId());
        invoice.setAmount(request.amount());
        invoice.setDueDate(request.dueDate());
        invoice.setStatus(InvoiceStatus.PENDING);
        Invoice savedInvoice = invoiceRepository.save(invoice);
        
        // Log invoice generation
        auditLogService.logBilling(
            "SYSTEM",
            AuditLog.AuditAction.BILLING_INVOICE_CREATE,
            savedInvoice.getId().toString(),
            request,
            AuditLog.AuditStatus.SUCCESS
        );
        
        return savedInvoice;
    }

    @Transactional(readOnly = true)
    public List<Invoice> list() {
        return invoiceRepository.findByTenantId(requireTenant());
    }

    @Transactional
    public Invoice pay(UUID invoiceId, PayRequest request) {
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, requireTenant())
                .orElseThrow(() -> new ApiException("Invoice not found"));
        invoice.setStatus(InvoiceStatus.PAID);
        
        // Log invoice payment
        auditLogService.logBilling(
            request.paidBy() != null ? request.paidBy() : "SYSTEM",
            AuditLog.AuditAction.BILLING_INVOICE_PAID,
            invoiceId.toString(),
            request,
            AuditLog.AuditStatus.SUCCESS
        );
        
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
