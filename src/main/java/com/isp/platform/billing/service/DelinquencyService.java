package com.isp.platform.billing.service;

import com.isp.platform.billing.domain.Invoice;
import com.isp.platform.billing.domain.InvoiceRepository;
import com.isp.platform.billing.domain.InvoiceStatus;
import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for checking overdue invoices and automatically blocking delinquent customers.
 * Runs daily to check for invoices past their due date.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DelinquencyService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;

    /**
     * Checks for overdue invoices and blocks customers automatically.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void checkAndBlockDelinquentCustomers() {
        log.info("Starting delinquency check for overdue invoices");
        
        LocalDate currentDate = LocalDate.now();
        
        // Find all pending invoices that are past due date
        List<Invoice> overdueInvoices = invoiceRepository.findOverdueInvoices(InvoiceStatus.PENDING, currentDate);
        
        log.info("Found {} overdue invoices", overdueInvoices.size());
        
        int blockedCount = 0;
        int updatedInvoiceCount = 0;
        
        for (Invoice invoice : overdueInvoices) {
            try {
                // Update invoice status to OVERDUE
                invoice.setStatus(InvoiceStatus.OVERDUE);
                invoiceRepository.save(invoice);
                updatedInvoiceCount++;
                
                // Find and block the customer
                UUID customerId = parseCustomerId(invoice.getCustomerId());
                if (customerId != null) {
                    customerRepository.findById(customerId).ifPresent(customer -> {
                        if (!customer.isBlocked()) {
                            customer.setBlocked(true);
                            customerRepository.save(customer);
                            log.info("Blocked customer {} due to overdue invoice {}", 
                                    customer.getId(), invoice.getId());
                        }
                    });
                    blockedCount++;
                }
            } catch (Exception e) {
                log.error("Error processing overdue invoice {}", invoice.getId(), e);
            }
        }
        
        log.info("Delinquency check completed. Invoices marked as OVERDUE: {}, Customers blocked: {}", 
                updatedInvoiceCount, blockedCount);
    }

    /**
     * Manually trigger delinquency check (for testing or admin action).
     */
    @Transactional
    public void manualDelinquencyCheck() {
        log.info("Manual delinquency check triggered");
        checkAndBlockDelinquentCustomers();
    }
    
    /**
     * Check if a specific customer has overdue invoices.
     * 
     * @param customerId the customer ID to check
     * @return true if customer has overdue invoices
     */
    @Transactional(readOnly = true)
    public boolean hasOverdueInvoices(String customerId) {
        LocalDate currentDate = LocalDate.now();
        List<Invoice> overdueInvoices = invoiceRepository.findOverdueInvoicesByCustomer(customerId, currentDate);
        return !overdueInvoices.isEmpty();
    }
    
    /**
     * Parse customer ID from string to UUID.
     * Returns null if parsing fails.
     */
    private UUID parseCustomerId(String customerIdStr) {
        try {
            return UUID.fromString(customerIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid customer ID format: {}", customerIdStr);
            return null;
        }
    }
}
