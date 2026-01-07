package com.isp.platform.billing;

import com.isp.platform.billing.domain.Invoice;
import com.isp.platform.billing.domain.InvoiceRepository;
import com.isp.platform.billing.domain.InvoiceStatus;
import com.isp.platform.billing.integration.PixGatewayService;
import com.isp.platform.billing.integration.PixPaymentRequest;
import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PIX payment workflow.
 * Validates QR code generation, webhook processing, and automatic customer unlock.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("PIX Payment Integration Tests")
public class PixBillingIntegrationTest {

    @Autowired
    private PixGatewayService pixGatewayService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Customer testCustomer;
    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        // Create test customer
        testCustomer = new Customer();
        testCustomer.setFullName("Test Customer");
        testCustomer.setDocument("12345678901");
        testCustomer.setPlan("PROFESSIONAL");
        testCustomer.setStatus("ACTIVE");
        testCustomer.setBlocked(true); // Customer starts blocked
        testCustomer = customerRepository.save(testCustomer);

        // Create test invoice
        testInvoice = new Invoice();
        testInvoice.setCustomerId(testCustomer.getId().toString());
        testInvoice.setAmount(new BigDecimal("79.90"));
        testInvoice.setDueDate(LocalDate.now().plusDays(7));
        testInvoice.setStatus(InvoiceStatus.PENDING);
        testInvoice = invoiceRepository.save(testInvoice);
    }

    @Test
    @DisplayName("Should generate PIX QR code for invoice")
    void testGeneratePixQrCode() {
        // Act
        PixPaymentRequest.PixPaymentResponse response = pixGatewayService.generatePixQrCode(testInvoice);

        // Assert
        assertNotNull(response, "PIX response should not be null");
        assertNotNull(response.getPaymentId(), "Payment ID should be generated");
        assertNotNull(response.getQrCode(), "QR code should be generated");
        assertNotNull(response.getCopyAndPasteKey(), "Copy and paste key should be generated");
        assertEquals(testInvoice.getAmount(), response.getAmount(), "Amount should match invoice");
        assertEquals("PENDING", response.getStatus(), "Initial status should be PENDING");
    }

    @Test
    @DisplayName("Should process payment webhook and mark invoice as paid")
    void testPaymentWebhookProcessing() {
        // Arrange
        PixPaymentRequest.PixWebhook webhook = PixPaymentRequest.PixWebhook.builder()
            .eventId("evt_" + UUID.randomUUID())
            .eventType("PAYMENT_CONFIRMED")
            .paymentId("pay_" + UUID.randomUUID())
            .invoiceId(testInvoice.getId().toString())
            .status("CONFIRMED")
            .amount(testInvoice.getAmount())
            .paidAt(LocalDateTime.now())
            .build();

        // Act
        pixGatewayService.handlePaymentWebhook(webhook);

        // Assert
        Invoice updatedInvoice = invoiceRepository.findById(testInvoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PAID, updatedInvoice.getStatus(), "Invoice should be marked as PAID");
    }

    @Test
    @DisplayName("Should unlock customer automatically when payment is confirmed")
    void testCustomerAutoUnlock() {
        // Arrange - Customer starts blocked
        assertTrue(testCustomer.isBlocked(), "Customer should start blocked");

        PixPaymentRequest.PixWebhook webhook = PixPaymentRequest.PixWebhook.builder()
            .eventId("evt_" + UUID.randomUUID())
            .eventType("PAYMENT_CONFIRMED")
            .paymentId("pay_" + UUID.randomUUID())
            .invoiceId(testInvoice.getId().toString())
            .status("PAID")
            .amount(testInvoice.getAmount())
            .paidAt(LocalDateTime.now())
            .build();

        // Act
        pixGatewayService.handlePaymentWebhook(webhook);

        // Assert
        Customer updatedCustomer = customerRepository.findById(testCustomer.getId()).orElseThrow();
        assertFalse(updatedCustomer.isBlocked(), "Customer should be unblocked after payment");
    }

    @Test
    @DisplayName("Should handle cancelled payment webhook")
    void testCancelledPaymentWebhook() {
        // Arrange
        PixPaymentRequest.PixWebhook webhook = PixPaymentRequest.PixWebhook.builder()
            .eventId("evt_" + UUID.randomUUID())
            .eventType("PAYMENT_CANCELLED")
            .paymentId("pay_" + UUID.randomUUID())
            .invoiceId(testInvoice.getId().toString())
            .status("CANCELLED")
            .amount(testInvoice.getAmount())
            .paidAt(null)
            .build();

        // Act
        pixGatewayService.handlePaymentWebhook(webhook);

        // Assert
        Invoice updatedInvoice = invoiceRepository.findById(testInvoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.CANCELLED, updatedInvoice.getStatus(), "Invoice should be marked as CANCELLED");

        // Customer should remain blocked
        Customer updatedCustomer = customerRepository.findById(testCustomer.getId()).orElseThrow();
        assertTrue(updatedCustomer.isBlocked(), "Customer should remain blocked");
    }

    @Test
    @DisplayName("Should handle webhook for non-existent invoice gracefully")
    void testWebhookForNonExistentInvoice() {
        // Arrange
        PixPaymentRequest.PixWebhook webhook = PixPaymentRequest.PixWebhook.builder()
            .eventId("evt_" + UUID.randomUUID())
            .eventType("PAYMENT_CONFIRMED")
            .paymentId("pay_" + UUID.randomUUID())
            .invoiceId(UUID.randomUUID().toString()) // Non-existent invoice ID
            .status("CONFIRMED")
            .amount(new BigDecimal("100.00"))
            .paidAt(LocalDateTime.now())
            .build();

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> pixGatewayService.handlePaymentWebhook(webhook),
            "Should handle non-existent invoice gracefully");
    }

    @Test
    @DisplayName("Should handle webhook for non-existent customer gracefully")
    void testWebhookForNonExistentCustomer() {
        // Arrange - Create invoice with non-existent customer
        Invoice orphanInvoice = new Invoice();
        orphanInvoice.setCustomerId(UUID.randomUUID().toString()); // Non-existent customer
        orphanInvoice.setAmount(new BigDecimal("50.00"));
        orphanInvoice.setDueDate(LocalDate.now());
        orphanInvoice.setStatus(InvoiceStatus.PENDING);
        orphanInvoice = invoiceRepository.save(orphanInvoice);

        PixPaymentRequest.PixWebhook webhook = PixPaymentRequest.PixWebhook.builder()
            .eventId("evt_" + UUID.randomUUID())
            .eventType("PAYMENT_CONFIRMED")
            .paymentId("pay_" + UUID.randomUUID())
            .invoiceId(orphanInvoice.getId().toString())
            .status("CONFIRMED")
            .amount(orphanInvoice.getAmount())
            .paidAt(LocalDateTime.now())
            .build();

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> pixGatewayService.handlePaymentWebhook(webhook),
            "Should handle non-existent customer gracefully");
    }
}
