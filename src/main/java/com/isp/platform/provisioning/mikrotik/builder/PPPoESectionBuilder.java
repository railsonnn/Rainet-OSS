package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds PPPoE server configuration section.
 * CRITICAL: Generates idempotent configuration for ISP customer access.
 */
@Component
public class PPPoESectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        if (!config.isPppoeEnabled() || config.getPppePlans() == null || config.getPppePlans().isEmpty()) {
            return "# PPPoE Server: Disabled\n";
        }
        
        sb.append("# PPPoE Server Configuration\n");
        
        // Create IP pools for each plan
        sb.append("/ip/pool\n");
        for (RouterOsConfig.PPPoEPlan plan : config.getPppePlans()) {
            String poolName = String.format("pppoe-pool-%s", plan.getPlanName());
            String poolPrefix = plan.getPoolPrefix();
            
            sb.append(String.format("add name=%s ranges=%s.1-%s.254 comment=\"Pool for %s\"\n",
                poolName, poolPrefix, poolPrefix, plan.getPlanName()));
        }
        
        // Create PPP profiles with rate limiting
        sb.append("/ppp/profile\n");
        for (RouterOsConfig.PPPoEPlan plan : config.getPppePlans()) {
            String profileName = String.format("pppoe-profile-%s", plan.getPlanName());
            
            sb.append(String.format("add name=%s comment=\"Profile for %s plan\"\n", 
                profileName, plan.getPlanName()));
            sb.append(String.format("set %s rate-limit=%dM/%dM\n",
                profileName, plan.getUploadMbps(), plan.getDownloadMbps()));
        }
        
        // Configure PPPoE server
        sb.append("/interface/pppoe-server/server\n");
        sb.append("set enabled=yes\n");
        sb.append("set service-name=rainet-isp\n");
        sb.append("set one-session-per-host=yes\n");
        sb.append("set mru=1480\n");
        sb.append("set mtu=1480\n");
        
        // PPP AAA configuration (authentication)
        sb.append("/ppp/aaa\n");
        sb.append("set use-radius=yes\n");
        
        if (config.getRadiusServer() != null) {
            sb.append(String.format("set interim-update=1m\n"));
            sb.append(String.format("set accounting=yes\n"));
        }
        
        // RADIUS server configuration
        if (config.getRadiusServer() != null && config.getRadiusSecret() != null) {
            sb.append("/radius\n");
            sb.append(String.format("add service=ppp address=%s secret=%s\n", 
                config.getRadiusServer(), config.getRadiusSecret()));
        }
        
        return sb.toString();
    }
}
