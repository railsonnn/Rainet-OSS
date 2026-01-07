package com.isp.platform.provisioning.controller;

import com.isp.platform.common.dto.ApiResponse;
import com.isp.platform.provisioning.service.ProvisioningRequest;
import com.isp.platform.provisioning.service.ProvisioningService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/provisioning")
public class ProvisioningController {

    private final ProvisioningService provisioningService;

    public ProvisioningController(ProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECH')")
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<Map<String, String>>> preview(@Valid @RequestBody ProvisioningRequest request) {
        String script = provisioningService.preview(request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("script", script)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECH')")
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> apply(
            @Valid @RequestBody ProvisioningRequest request,
            Principal principal) {
        UUID snapshotId = provisioningService.apply(request, principal.getName());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("snapshotId", snapshotId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECH')")
    @PostMapping("/rollback/{snapshotId}")
    public ResponseEntity<ApiResponse<String>> rollback(
            @PathVariable UUID snapshotId,
            Principal principal) {
        provisioningService.rollback(snapshotId, principal.getName());
        return ResponseEntity.ok(ApiResponse.ok("rolled back"));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TECH')")
    @GetMapping("/snapshots")
    public ResponseEntity<ApiResponse<?>> snapshots() {
        return ResponseEntity.ok(ApiResponse.ok(provisioningService.listSnapshots()));
    }
}
