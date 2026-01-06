package com.isp.platform.billing.service;

import jakarta.validation.constraints.NotBlank;

public record PayRequest(@NotBlank String pixProof) {
}
