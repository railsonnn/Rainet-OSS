package com.isp.platform.provisioning.mikrotik;

import com.isp.platform.provisioning.domain.Router;
import com.isp.platform.provisioning.mikrotik.builder.RouterOsConfig;
import com.isp.platform.provisioning.mikrotik.builder.RouterOsScriptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates complete RouterOS provisioning scripts using the modular builder.
 * Integrates with wizard to create idempotent, reapplicable configurations.
 */
@Slf4j
@Component
public class RouterOsScriptGenerator {

    private final RouterOsScriptBuilder scriptBuilder;

    public RouterOsScriptGenerator(RouterOsScriptBuilder scriptBuilder) {
        this.scriptBuilder = scriptBuilder;
    }

    /**
     * Generate a complete RouterOS provisioning script for a router.
     * The generated script is idempotent and can be reapplied without causing duplicates.
     *
     * @param router the target router entity
     * @return complete RouterOS script ready for import
     */
    public String generateProvisioningScript(Router router) {
        log.info("Generating provisioning script for router: {}", router.getHostname());
        
        // Build configuration from router entity
        RouterOsConfig config = buildConfigFromRouter(router);
        
        // Generate script using modular builder
        return scriptBuilder.buildScript(router, config);
    }

    /**
     * Build RouterOsConfig DTO from Router entity.
     * This method can be extended to pull configuration from additional sources
     * like tenant preferences, network plans, or wizard inputs.
     *
     * @param router the router entity
     * @return populated RouterOsConfig
     */
    private RouterOsConfig buildConfigFromRouter(Router router) {
        return RouterOsConfig.builder()
                .version("1.0")
                .routerName(router.getHostname())
                // Interface configuration
                .wanInterface("wan")
                .lanInterface("lan")
                .bridgeInterface("bridge-lan")
                // Network configuration - these should come from wizard/tenant config
                .wanAddress("dhcp-client") // or static IP from wizard
                .wanGateway("auto") // detected from DHCP or set from wizard
                .lanNetwork("192.168.88.1/24") // from wizard
                .lanDns1("8.8.8.8")
                .lanDns2("8.8.4.4")
                // PPPoE configuration
                .pppoeEnabled(true)
                .pppoeService("rainet-isp")
                .pppePlans(buildDefaultPPPoEPlans())
                // RADIUS server configuration - should come from application.yml or wizard
                // TODO: Make RADIUS server and secret configurable via properties
                .radiusServer("127.0.0.1") // localhost - change in production
                .radiusSecret("CHANGE_ME_IN_PRODUCTION") // placeholder secret
                // QoS configuration
                .qosEnabled(true)
                .defaultBandwidthMbps(1000)
                .qosProfiles(buildDefaultQoSProfiles())
                // Security configuration
                .firewallEnabled(true)
                .natEnabled(true)
                .customRules(new ArrayList<>())
                .build();
    }

    /**
     * Build default PPPoE plans.
     * In production, these should come from tenant's service catalog.
     */
    private List<RouterOsConfig.PPPoEPlan> buildDefaultPPPoEPlans() {
        List<RouterOsConfig.PPPoEPlan> plans = new ArrayList<>();
        
        plans.add(RouterOsConfig.PPPoEPlan.builder()
                .planName("basic-10mb")
                .poolPrefix("10.10.1")
                .uploadMbps(5)
                .downloadMbps(10)
                .poolSize(254)
                .build());
        
        plans.add(RouterOsConfig.PPPoEPlan.builder()
                .planName("standard-50mb")
                .poolPrefix("10.10.2")
                .uploadMbps(25)
                .downloadMbps(50)
                .poolSize(254)
                .build());
        
        plans.add(RouterOsConfig.PPPoEPlan.builder()
                .planName("premium-100mb")
                .poolPrefix("10.10.3")
                .uploadMbps(50)
                .downloadMbps(100)
                .poolSize(254)
                .build());
        
        return plans;
    }

    /**
     * Build default QoS profiles.
     * In production, these should come from tenant configuration.
     */
    private List<RouterOsConfig.QoSProfile> buildDefaultQoSProfiles() {
        List<RouterOsConfig.QoSProfile> profiles = new ArrayList<>();
        
        profiles.add(RouterOsConfig.QoSProfile.builder()
                .profileName("high-priority")
                .priorityLevel(1)
                .bandwidthMbps(100)
                .build());
        
        profiles.add(RouterOsConfig.QoSProfile.builder()
                .profileName("normal-priority")
                .priorityLevel(4)
                .bandwidthMbps(50)
                .build());
        
        profiles.add(RouterOsConfig.QoSProfile.builder()
                .profileName("low-priority")
                .priorityLevel(8)
                .bandwidthMbps(10)
                .build());
        
        return profiles;
    }
}
