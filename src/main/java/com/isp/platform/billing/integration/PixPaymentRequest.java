package com.isp.platform.billing.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PIX QR Code and payment data for Asaas/Gerencianet integration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixPaymentRequest {
    private String customerId;
    private String invoiceId;
    private BigDecimal amount;
    private String description;
    private LocalDateTime expiresAt;
    private String webhookUrl;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PixPaymentResponse {
        private String paymentId;
        private String qrCode;
        private String qrCodeImage;
        private String copyAndPasteKey;
        private long expiresIn;
        private BigDecimal amount;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PixWebhook {
        private String eventId;
        private String eventType;
        private String paymentId;
        private String invoiceId;
        private String status;
        private BigDecimal amount;
        private LocalDateTime paidAt;
    }
}
