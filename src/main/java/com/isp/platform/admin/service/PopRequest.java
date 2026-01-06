package com.isp.platform.admin.service;

import jakarta.validation.constraints.NotBlank;

public record PopRequest(@NotBlank String name, @NotBlank String city) {
}
