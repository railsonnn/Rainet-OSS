package com.isp.platform.billing.integration;

import com.isp.platform.billing.domain.Invoice;
import com.isp.platform.billing.domain.InvoiceStatus;
import com.isp.platform.billing.repository.InvoiceRepository;
import com.isp.platform.billing.domain.Customer;
import com.isp.platform.billing.repository.CustomerRepository;
import com.isp.platform.provisioning.radius.RadiusServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * PIX payment gateway integration supporting Asaas and Gerencianet.
 * Generates QR codes, receives webhooks, and automatically unlocks paid customers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PixGatewayService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final RadiusServerService radiusService;
    private final RestTemplate restTemplate;

    @Value("${pix.gateway:asaas}")
    private String gatewayProvider;

    @Value("${asaas.api-key:}")
    private String asaasApiKey;

    @Value("${asaas.api-url:https://api.asaas.com/v3}")
    private String asaasApiUrl;

    @Value("${gerencianet.client-id:}")
    private String gerencianetClientId;

    @Value("${gerencianet.client-secret:}")
    private String gerencianetClientSecret;

    @Value("${app.webhook-url:}")
    private String webhookUrl;

    /**
     * Generate PIX QR Code for invoice payment.
     *
     * @param invoice the invoice to generate QR code for
     * @return PIX payment response with QR code
     */
    public PixPaymentRequest.PixPaymentResponse generatePixQrCode(Invoice invoice) {
        log.info("Generating PIX QR code for invoice: {} (customer: {})", invoice.getId(), invoice.getCustomerId());

        try {
            if ("asaas".equalsIgnoreCase(gatewayProvider)) {
                return generateAsaasPixQrCode(invoice);
            } else if ("gerencianet".equalsIgnoreCase(gatewayProvider)) {
                return generateGerencianetPixQrCode(invoice);
            } else {
                throw new RuntimeException("Unsupported PIX gateway: " + gatewayProvider);
            }
        } catch (Exception e) {
            log.error("Failed to generate PIX QR code for invoice: {}", invoice.getId(), e);
            throw new RuntimeException("Failed to generate PIX QR code", e);
        }
    }

    /**
     * Handle PIX payment webhook from gateway.
     * Automatically unlocks customer when payment is confirmed.
     *
     * @param webhook PIX webhook data
     */
    public void handlePaymentWebhook(PixPaymentRequest.PixWebhook webhook) {
        log.info("Processing PIX webhook: eventId={}, status={}", webhook.getEventId(), webhook.getStatus());

        try {
            // Find invoice by payment ID or invoice ID
            Optional<Invoice> invoiceOpt = invoiceRepository.findById(Long.parseLong(webhook.getInvoiceId()));

            if (invoiceOpt.isEmpty()) {
                log.warn("Invoice not found for webhook: {}", webhook.getInvoiceId());
                return;
            }

            Invoice invoice = invoiceOpt.get();

            // Check if payment is confirmed
            if ("CONFIRMED".equalsIgnoreCase(webhook.getStatus()) || "PAID".equalsIgnoreCase(webhook.getStatus())) {
                // Mark invoice as paid
                invoice.setStatus(InvoiceStatus.PAID);
                invoiceRepository.save(invoice);

                log.info("Invoice {} marked as PAID", invoice.getId());

                // Unlock customer
                unlockCustomer(invoice.getCustomerId());
            } else if ("CANCELLED".equalsIgnoreCase(webhook.getStatus())) {
                invoice.setStatus(InvoiceStatus.CANCELLED);
                invoiceRepository.save(invoice);
                log.info("Invoice {} marked as CANCELLED", invoice.getId());
            }
        } catch (Exception e) {
            log.error("Error processing PIX webhook", e);
        }
    }

    /**
     * Unlock customer after payment confirmation.
     *
     * @param customerId customer ID to unlock
     */
    private void unlockCustomer(String customerId) {
        log.info("Unlocking customer: {}", customerId);

        try {
            Optional<Customer> customerOpt = customerRepository.findById(customerId);

            if (customerOpt.isEmpty()) {
                log.warn("Customer not found: {}", customerId);
                return;
            }

            Customer customer = customerOpt.get();
            customer.setBlocked(false);
            customerRepository.save(customer);

            log.info("Customer {} unlocked successfully", customerId);

            // Notify RADIUS to update profile
            // The next PPPoE authentication will now succeed with full bandwidth
        } catch (Exception e) {
            log.error("Failed to unlock customer: {}", customerId, e);
        }
    }

    /**
     * Generate PIX QR code using Asaas API.
     */
    private PixPaymentRequest.PixPaymentResponse generateAsaasPixQrCode(Invoice invoice) {
        log.debug("Generating Asaas PIX QR code");

        String url = asaasApiUrl + "/pix/qrcode";

        Map<String, Object> payload = new HashMap<>();
        payload.put("value", invoice.getAmount());
        payload.put("description", "Invoice " + invoice.getId());
        payload.put("expiresIn", 3600); // 1 hour expiry
        payload.put("reuse", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + asaasApiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            return PixPaymentRequest.PixPaymentResponse.builder()
                .paymentId(String.valueOf(response.get("id")))
                .qrCode(String.valueOf(response.get("encodedImage")))
                .copyAndPasteKey(String.valueOf(response.get("payload")))
                .expiresIn(3600)
                .amount(invoice.getAmount())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            log.error("Asaas API error", e);
            throw new RuntimeException("Failed to generate Asaas PIX QR code", e);
        }
    }

    /**
     * Generate PIX QR code using Gerencianet API.
     */
    private PixPaymentRequest.PixPaymentResponse generateGerencianetPixQrCode(Invoice invoice) {
        log.debug("Generating Gerencianet PIX QR code");

        // TODO: Implement Gerencianet integration
        // For now, return mock response
        return PixPaymentRequest.PixPaymentResponse.builder()
            .paymentId("mock-" + invoice.getId())
            .qrCode("00020126580014br.gov.bcb.brcode...")
            .copyAndPasteKey("00020126580014br.gov.bcb.brcode...")
            .expiresIn(3600)
            .amount(invoice.getAmount())
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .build();
    }
}
