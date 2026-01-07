package com.isp.platform.provisioning.mikrotik;

import com.isp.platform.provisioning.domain.Router;
import com.isp.platform.provisioning.mikrotik.builder.RouterOsConfig;
import com.isp.platform.provisioning.mikrotik.builder.RouterOsScriptBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RouterOsScriptGenerator {

    private final RouterOsScriptBuilder scriptBuilder;

    public RouterOsScriptGenerator(RouterOsScriptBuilder scriptBuilder) {
        this.scriptBuilder = scriptBuilder;
    }

    public String generateProvisioningScript(Router router) {
        // Build configuration with PPPoE enabled
        RouterOsConfig config = RouterOsConfig.builder()
            .version("1.0.0")
            .routerName(router.getHostname())
            .wanInterface("wan")
            .lanInterface("lan")
            .bridgeInterface("bridge-lan")
            // WAN configuration - use example values or fetch from router entity
            .wanAddress("dhcp-client")  // or specific IP if needed
            .wanGateway(null)  // will be provided by DHCP
            .lanNetwork("192.168.1.0/24")
            .lanDns1("8.8.8.8")
            .lanDns2("8.8.4.4")
            // Enable PPPoE with default configuration
            .pppoeEnabled(true)
            .pppoeService("rainet-isp")
            .pppePlans(createDefaultPPPoEPlans())
            // TODO: Make RADIUS configuration customizable per tenant/router
            // Default assumes co-located RADIUS server; update for distributed setups
            .radiusServer("127.0.0.1")  // localhost for co-located RADIUS
            // TODO: Retrieve RADIUS secret from secure configuration/vault in production
            .radiusSecret("rainet-radius-secret")  // Default secret - MUST be changed for production
            // QoS configuration
            .qosEnabled(true)
            .defaultBandwidthMbps(10)
            // Security configuration
            .firewallEnabled(true)
            .natEnabled(true)
            .build();

        return scriptBuilder.buildScript(router, config);
    }

    /**
     * Create default PPPoE plans for initial provisioning.
     * These can be customized per-tenant or per-router in future enhancements.
     */
    private List<RouterOsConfig.PPPoEPlan> createDefaultPPPoEPlans() {
        return List.of(
            RouterOsConfig.PPPoEPlan.builder()
                .planName("basic-10mb")
                .poolPrefix("10.10.10")
                .uploadMbps(5)
                .downloadMbps(10)
                .poolSize(254)
                .build(),
            RouterOsConfig.PPPoEPlan.builder()
                .planName("standard-50mb")
                .poolPrefix("10.10.20")
                .uploadMbps(25)
                .downloadMbps(50)
                .poolSize(254)
                .build(),
            RouterOsConfig.PPPoEPlan.builder()
                .planName("premium-100mb")
                .poolPrefix("10.10.30")
                .uploadMbps(50)
                .downloadMbps(100)
                .poolSize(254)
                .build()
        );
    }
}
