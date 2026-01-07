package com.isp.platform.billing.service;

import com.isp.platform.billing.domain.Invoice;
import com.isp.platform.billing.domain.InvoiceRepository;
import com.isp.platform.billing.domain.InvoiceStatus;
import com.isp.platform.common.exception.ApiException;
import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import com.isp.platform.gateway.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BillingService automatic unblocking functionality.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BillingService Auto-Unblock Tests")
class BillingServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private DelinquencyService delinquencyService;

    @InjectMocks
    private BillingService billingService;

    private UUID tenantId;
    private UUID customerId;
    private UUID invoiceId;
    private Customer testCustomer;
    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();

        // Set tenant context
        TenantContext.setCurrentTenant(tenantId);

        testCustomer = new Customer();
        testCustomer.setId(customerId);
        testCustomer.setFullName("Test Customer");
        testCustomer.setDocument("12345678900");
        testCustomer.setPlan("BASIC");
        testCustomer.setStatus("ACTIVE");
        testCustomer.setBlocked(true); // Initially blocked

        testInvoice = new Invoice();
        testInvoice.setId(invoiceId);
        testInvoice.setCustomerId(customerId.toString());
        testInvoice.setAmount(new BigDecimal("100.00"));
        testInvoice.setDueDate(LocalDate.now().minusDays(5));
        testInvoice.setStatus(InvoiceStatus.OVERDUE);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should unblock customer after paying last overdue invoice")
    void shouldUnblockCustomerAfterPayingLastOverdueInvoice() {
        // Given
        when(invoiceRepository.findByIdAndTenantId(invoiceId, tenantId))
                .thenReturn(Optional.of(testInvoice));
        when(delinquencyService.hasOverdueInvoices(customerId.toString()))
                .thenReturn(false); // No more overdue invoices
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(testCustomer));

        // When
        Invoice result = billingService.pay(invoiceId, new PayRequest());

        // Then
        assertEquals(InvoiceStatus.PAID, result.getStatus());
        verify(customerRepository).save(argThat(customer -> 
            !customer.isBlocked()
        ));
    }

    @Test
    @DisplayName("Should not unblock customer if other overdue invoices exist")
    void shouldNotUnblockCustomerIfOtherOverdueInvoicesExist() {
        // Given
        when(invoiceRepository.findByIdAndTenantId(invoiceId, tenantId))
                .thenReturn(Optional.of(testInvoice));
        when(delinquencyService.hasOverdueInvoices(customerId.toString()))
                .thenReturn(true); // Still has overdue invoices
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(testCustomer));

        // When
        Invoice result = billingService.pay(invoiceId, new PayRequest());

        // Then
        assertEquals(InvoiceStatus.PAID, result.getStatus());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should handle customer not found gracefully during unblock")
    void shouldHandleCustomerNotFoundGracefullyDuringUnblock() {
        // Given
        when(invoiceRepository.findByIdAndTenantId(invoiceId, tenantId))
                .thenReturn(Optional.of(testInvoice));
        when(delinquencyService.hasOverdueInvoices(customerId.toString()))
                .thenReturn(false);
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.empty());

        // When
        Invoice result = billingService.pay(invoiceId, new PayRequest());

        // Then
        assertEquals(InvoiceStatus.PAID, result.getStatus());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw exception when invoice not found")
    void shouldThrowExceptionWhenInvoiceNotFound() {
        // Given
        when(invoiceRepository.findByIdAndTenantId(invoiceId, tenantId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(ApiException.class, () -> 
            billingService.pay(invoiceId, new PayRequest())
        );
    }

    @Test
    @DisplayName("Should generate invoice with PENDING status")
    void shouldGenerateInvoiceWithPendingStatus() {
        // Given
        GenerateInvoiceRequest request = new GenerateInvoiceRequest(
            customerId.toString(),
            new BigDecimal("100.00"),
            LocalDate.now().plusDays(30)
        );
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Invoice result = billingService.generate(request);

        // Then
        assertEquals(InvoiceStatus.PENDING, result.getStatus());
        assertEquals(customerId.toString(), result.getCustomerId());
        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Should list invoices by tenant")
    void shouldListInvoicesByTenant() {
        // Given
        when(invoiceRepository.findByTenantId(tenantId))
                .thenReturn(Collections.singletonList(testInvoice));

        // When
        var invoices = billingService.list();

        // Then
        assertEquals(1, invoices.size());
        verify(invoiceRepository).findByTenantId(tenantId);
    }
}
