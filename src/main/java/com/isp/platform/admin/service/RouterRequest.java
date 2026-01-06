package com.isp.platform.admin.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RouterRequest(
        @NotNull UUID popId,
        @NotBlank String hostname,
        @NotBlank String managementAddress,
        @NotBlank String routerOsVersion,
        @NotBlank String apiUsername,
        @NotBlank String apiPassword) {
}
