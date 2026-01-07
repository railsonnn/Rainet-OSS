package com.isp.platform.customer.controller;

import com.isp.platform.common.dto.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer")
public class CustomerPortalController {

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        Map<String, Object> payload = Map.of(
                "status", "ok",
                "ticketOpen", 0,
                "pendingInvoices", 0);
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/unlock")
    public ResponseEntity<ApiResponse<String>> unlock() {
        return ResponseEntity.ok(ApiResponse.ok("unlock requested"));
    }
}
