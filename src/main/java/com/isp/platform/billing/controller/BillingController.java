package com.isp.platform.billing.controller;

import com.isp.platform.billing.service.BillingService;
import com.isp.platform.billing.service.GenerateInvoiceRequest;
import com.isp.platform.billing.service.PayRequest;
import com.isp.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/billing")
public class BillingController {

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
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
}
