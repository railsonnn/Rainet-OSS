package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds PPPoE server configuration section.
 * CRITICAL: Generates idempotent configuration for ISP customer access.
 * Uses existence checks to prevent duplicate pool and profile creation.
 */
@Component
public class PPPoESectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        if (!config.isPppoeEnabled() || config.getPppePlans() == null || config.getPppePlans().isEmpty()) {
            return "# PPPoE Server: Disabled\n";
        }
        
        sb.append("# PPPoE Server Configuration\n");
        sb.append("# Idempotent: removes existing before adding\n");
        
        // Create IP pools for each plan
        sb.append("/ip/pool\n");
        for (RouterOsConfig.PPPoEPlan plan : config.getPppePlans()) {
            String poolName = String.format("pppoe-pool-%s", plan.getPlanName());
            String poolPrefix = plan.getPoolPrefix();
            
            sb.append(String.format(":if ([/ip/pool print count-only where name=\"%s\"] > 0) do={\n", poolName));
            sb.append(String.format("  /ip/pool remove [find name=\"%s\"]\n", poolName));
            sb.append("}\n");
            sb.append(String.format("add name=%s ranges=%s.1-%s.254 comment=\"Pool for %s\"\n",
                poolName, poolPrefix, poolPrefix, plan.getPlanName()));
        }
        
        // Create PPP profiles with rate limiting
        sb.append("/ppp/profile\n");
        for (RouterOsConfig.PPPoEPlan plan : config.getPppePlans()) {
            String profileName = String.format("pppoe-profile-%s", plan.getPlanName());
            
            sb.append(String.format(":if ([/ppp/profile print count-only where name=\"%s\"] > 0) do={\n", profileName));
            sb.append(String.format("  /ppp/profile remove [find name=\"%s\"]\n", profileName));
            sb.append("}\n");
            sb.append(String.format("add name=%s local-address=%s.1 remote-address=pppoe-pool-%s rate-limit=%dM/%dM comment=\"Profile for %s plan\"\n", 
                profileName, plan.getPoolPrefix(), plan.getPlanName(),
                plan.getUploadMbps(), plan.getDownloadMbps(), plan.getPlanName()));
        }
        
        // Configure PPPoE server
        if (config.getLanInterface() != null) {
            sb.append("/interface/pppoe-server/server\n");
            sb.append(":if ([/interface/pppoe-server/server print count-only where interface=\"").append(config.getLanInterface()).append("\"] > 0) do={\n");
            sb.append("  /interface/pppoe-server/server remove [find interface=\"").append(config.getLanInterface()).append("\"]\n");
            sb.append("}\n");
            sb.append(String.format("add service-name=%s interface=%s disabled=no one-session-per-host=yes max-mru=1480 max-mtu=1480\n",
                config.getPppoeService(), config.getLanInterface()));
        }
        
        // PPP AAA configuration (authentication)
        sb.append("/ppp/aaa\n");
        sb.append("set use-radius=yes\n");
        
        if (config.getRadiusServer() != null) {
            sb.append("set interim-update=1m\n");
            sb.append("set accounting=yes\n");
        }
        
        // RADIUS server configuration
        if (config.getRadiusServer() != null && config.getRadiusSecret() != null) {
            sb.append("/radius\n");
            sb.append(String.format(":if ([/radius print count-only where address=\"%s\" service=ppp] > 0) do={\n", config.getRadiusServer()));
            sb.append(String.format("  /radius remove [find address=\"%s\" service=ppp]\n", config.getRadiusServer()));
            sb.append("}\n");
            // NOTE: RADIUS secret is included in script. For production, consider:
            // 1. Encrypting the generated script
            // 2. Using environment-specific secrets
            // 3. Manual configuration after script application
            sb.append(String.format("add service=ppp address=%s secret=%s timeout=3s\n", 
                config.getRadiusServer(), config.getRadiusSecret()));
        }
        
        return sb.toString();
    }
}
