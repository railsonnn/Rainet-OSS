package com.isp.platform.customer.service;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank String fullName,
        @NotBlank String document,
        @NotBlank String plan) {
}
