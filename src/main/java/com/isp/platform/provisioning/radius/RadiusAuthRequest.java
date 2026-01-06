package com.isp.platform.provisioning.radius;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * RADIUS authentication request/response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadiusAuthRequest {
    private String username;
    private String password;
    private String nasIp;
    private String nasIdentifier;
    private Map<String, String> additionalAttributes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RadiusAuthResponse {
        private boolean authenticated;
        private String profileName;
        private int uploadMbps;
        private int downloadMbps;
        private String errorMessage;
        private Map<String, String> attributes;
    }
}
