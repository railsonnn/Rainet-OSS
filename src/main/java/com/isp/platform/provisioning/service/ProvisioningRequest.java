package com.isp.platform.provisioning.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProvisioningRequest(
        @NotNull UUID routerId,
        @NotBlank String description) {
}
