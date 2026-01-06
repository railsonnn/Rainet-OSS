package com.isp.platform.provisioning.mikrotik.builder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration DTO for RouterOS script generation.
 * Contains all necessary data for building a complete router configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouterOsConfig {

    private String version;
    private String routerName;
    private String wanInterface;
    private String lanInterface;
    private String bridgeInterface;

    // Network configuration
    private String wanAddress;
    private String wanGateway;
    private String lanNetwork;
    private String lanDns1;
    private String lanDns2;

    // PPPoE configuration
    private boolean pppoeEnabled;
    private String pppoeService;
    private List<PPPoEPlan> pppePlans;
    private String radiusServer;
    private String radiusSecret;

    // QoS configuration
    private boolean qosEnabled;
    private int defaultBandwidthMbps;
    private List<QoSProfile> qosProfiles;

    // Security configuration
    private boolean firewallEnabled;
    private boolean natEnabled;
    private List<FirewallRule> customRules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PPPoEPlan {
        private String planName;
        private String poolPrefix;
        private int uploadMbps;
        private int downloadMbps;
        private long poolSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QoSProfile {
        private String profileName;
        private int priorityLevel;
        private int bandwidthMbps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FirewallRule {
        private String chain;
        private String action;
        private String protocol;
        private String srcAddress;
        private String dstAddress;
        private Integer srcPort;
        private Integer dstPort;
        private String comment;
    }
}
