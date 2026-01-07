package com.isp.platform.billing.controller;

import com.isp.platform.billing.integration.PixGatewayService;
import com.isp.platform.billing.integration.PixPaymentRequest;
import com.isp.platform.billing.service.BillingService;
import com.isp.platform.billing.service.GenerateInvoiceRequest;
import com.isp.platform.billing.service.PayRequest;
import com.isp.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/billing")
public class BillingController {

    private final BillingService service;
    private final PixGatewayService pixGatewayService;

    public BillingController(BillingService service, PixGatewayService pixGatewayService) {
        this.service = service;
        this.pixGatewayService = pixGatewayService;
    }

    @PostMapping("/invoices/generate")
    public ResponseEntity<ApiResponse<?>> generate(@Valid @RequestBody GenerateInvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.generate(request)));
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<?>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @PostMapping("/pay/{invoiceId}")
    public ResponseEntity<ApiResponse<?>> pay(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody PayRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.pay(invoiceId, request)));
    }

    /**
     * Generate PIX QR code for invoice payment.
     */
    @PostMapping("/invoices/{invoiceId}/pix")
    public ResponseEntity<ApiResponse<?>> generatePixQrCode(@PathVariable UUID invoiceId) {
        log.info("Generating PIX QR code for invoice: {}", invoiceId);
        var pixResponse = service.generatePixQrCode(invoiceId);
        return ResponseEntity.ok(ApiResponse.ok(pixResponse));
    }

    /**
     * PIX payment webhook endpoint.
     * Receives payment confirmation from gateway (Asaas/Gerencianet).
     */
    @PostMapping("/webhook/pix")
    public ResponseEntity<ApiResponse<?>> pixWebhook(@RequestBody PixPaymentRequest.PixWebhook webhook) {
        log.info("Received PIX webhook: eventId={}, status={}", webhook.getEventId(), webhook.getStatus());
        try {
            pixGatewayService.handlePaymentWebhook(webhook);
            return ResponseEntity.ok(ApiResponse.ok("Webhook processed successfully"));
        } catch (Exception e) {
            log.error("Error processing PIX webhook", e);
            return ResponseEntity.ok(ApiResponse.ok("Webhook received"));
        }
    }
}
