package com.isp.platform.billing.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GenerateInvoiceRequest(
        @NotBlank String customerId,
        @NotNull BigDecimal amount,
        @NotNull LocalDate dueDate) {
}
