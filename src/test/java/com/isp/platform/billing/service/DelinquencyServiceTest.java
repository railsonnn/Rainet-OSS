package com.isp.platform.billing.service;

import com.isp.platform.billing.domain.Invoice;
import com.isp.platform.billing.domain.InvoiceRepository;
import com.isp.platform.billing.domain.InvoiceStatus;
import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DelinquencyService.
 * Tests automatic blocking of customers with overdue invoices.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DelinquencyService Tests")
class DelinquencyServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private DelinquencyService delinquencyService;

    private Customer testCustomer;
    private Invoice testInvoice;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        
        testCustomer = new Customer();
        testCustomer.setId(customerId);
        testCustomer.setFullName("Test Customer");
        testCustomer.setDocument("12345678900");
        testCustomer.setPlan("BASIC");
        testCustomer.setStatus("ACTIVE");
        testCustomer.setBlocked(false);

        testInvoice = new Invoice();
        testInvoice.setId(UUID.randomUUID());
        testInvoice.setCustomerId(customerId.toString());
        testInvoice.setAmount(new BigDecimal("100.00"));
        testInvoice.setDueDate(LocalDate.now().minusDays(5)); // 5 days overdue
        testInvoice.setStatus(InvoiceStatus.PENDING);
    }

    @Test
    @DisplayName("Should block customer with overdue invoice")
    void shouldBlockCustomerWithOverdueInvoice() {
        // Given
        List<Invoice> overdueInvoices = Collections.singletonList(testInvoice);
        when(invoiceRepository.findOverdueInvoices(eq(InvoiceStatus.PENDING), any(LocalDate.class)))
                .thenReturn(overdueInvoices);
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(testCustomer));

        // When
        delinquencyService.checkAndBlockDelinquentCustomers();

        // Then
        verify(invoiceRepository).save(argThat(invoice -> 
            invoice.getStatus() == InvoiceStatus.OVERDUE
        ));
        verify(customerRepository).save(argThat(customer -> 
            customer.isBlocked()
        ));
    }

    @Test
    @DisplayName("Should not block already blocked customer")
    void shouldNotBlockAlreadyBlockedCustomer() {
        // Given
        testCustomer.setBlocked(true); // Already blocked
        List<Invoice> overdueInvoices = Collections.singletonList(testInvoice);
        when(invoiceRepository.findOverdueInvoices(eq(InvoiceStatus.PENDING), any(LocalDate.class)))
                .thenReturn(overdueInvoices);
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(testCustomer));

        // When
        delinquencyService.checkAndBlockDelinquentCustomers();

        // Then
        verify(invoiceRepository).save(any(Invoice.class));
        // Customer save should not be called since already blocked
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should handle multiple overdue invoices")
    void shouldHandleMultipleOverdueInvoices() {
        // Given
        Invoice invoice2 = new Invoice();
        invoice2.setId(UUID.randomUUID());
        invoice2.setCustomerId(customerId.toString());
        invoice2.setAmount(new BigDecimal("200.00"));
        invoice2.setDueDate(LocalDate.now().minusDays(10));
        invoice2.setStatus(InvoiceStatus.PENDING);

        List<Invoice> overdueInvoices = Arrays.asList(testInvoice, invoice2);
        when(invoiceRepository.findOverdueInvoices(eq(InvoiceStatus.PENDING), any(LocalDate.class)))
                .thenReturn(overdueInvoices);
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(testCustomer));

        // When
        delinquencyService.checkAndBlockDelinquentCustomers();

        // Then
        verify(invoiceRepository, times(2)).save(any(Invoice.class));
        verify(customerRepository, times(1)).save(any(Customer.class)); // Only blocked once
    }

    @Test
    @DisplayName("Should handle customer not found gracefully")
    void shouldHandleCustomerNotFoundGracefully() {
        // Given
        List<Invoice> overdueInvoices = Collections.singletonList(testInvoice);
        when(invoiceRepository.findOverdueInvoices(eq(InvoiceStatus.PENDING), any(LocalDate.class)))
                .thenReturn(overdueInvoices);
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.empty());

        // When
        delinquencyService.checkAndBlockDelinquentCustomers();

        // Then
        verify(invoiceRepository).save(any(Invoice.class));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should handle no overdue invoices")
    void shouldHandleNoOverdueInvoices() {
        // Given
        when(invoiceRepository.findOverdueInvoices(eq(InvoiceStatus.PENDING), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // When
        delinquencyService.checkAndBlockDelinquentCustomers();

        // Then
        verify(invoiceRepository, never()).save(any(Invoice.class));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should detect customer has overdue invoices")
    void shouldDetectCustomerHasOverdueInvoices() {
        // Given
        String customerIdStr = customerId.toString();
        List<Invoice> overdueInvoices = Collections.singletonList(testInvoice);
        when(invoiceRepository.findOverdueInvoicesByCustomer(eq(customerIdStr), any(LocalDate.class)))
                .thenReturn(overdueInvoices);

        // When
        boolean hasOverdue = delinquencyService.hasOverdueInvoices(customerIdStr);

        // Then
        assertTrue(hasOverdue);
    }

    @Test
    @DisplayName("Should detect customer has no overdue invoices")
    void shouldDetectCustomerHasNoOverdueInvoices() {
        // Given
        String customerIdStr = customerId.toString();
        when(invoiceRepository.findOverdueInvoicesByCustomer(eq(customerIdStr), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // When
        boolean hasOverdue = delinquencyService.hasOverdueInvoices(customerIdStr);

        // Then
        assertFalse(hasOverdue);
    }

    @Test
    @DisplayName("Should handle invalid customer ID format")
    void shouldHandleInvalidCustomerIdFormat() {
        // Given
        testInvoice.setCustomerId("invalid-uuid-format");
        List<Invoice> overdueInvoices = Collections.singletonList(testInvoice);
        when(invoiceRepository.findOverdueInvoices(eq(InvoiceStatus.PENDING), any(LocalDate.class)))
                .thenReturn(overdueInvoices);

        // When
        delinquencyService.checkAndBlockDelinquentCustomers();

        // Then
        verify(invoiceRepository).save(any(Invoice.class));
        verify(customerRepository, never()).findById(any(UUID.class));
    }
}
