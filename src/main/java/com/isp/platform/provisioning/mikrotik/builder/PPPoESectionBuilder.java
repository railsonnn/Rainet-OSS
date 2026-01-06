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
        sb.append("/ip pool\n");
        for (RouterOsConfig.PPPoEPlan plan : config.getPppePlans()) {
            String poolName = String.format("pppoe-pool-%s", plan.getPlanName());
            String poolPrefix = plan.getPoolPrefix();
            
            sb.append(String.format("add name=\"%s\" ranges=%s.1-%s.254 comment=\"Pool for %s\"\n",
                poolName, poolPrefix, poolPrefix, plan.getPlanName()));
        }
        sb.append("\n");
        
        // Create PPP profiles with rate limiting and pool assignment
        sb.append("/ppp profile\n");
        for (RouterOsConfig.PPPoEPlan plan : config.getPppePlans()) {
            String profileName = String.format("pppoe-profile-%s", plan.getPlanName());
            String poolName = String.format("pppoe-pool-%s", plan.getPlanName());
            String localAddress = plan.getPoolPrefix() + ".1";
            
            sb.append(String.format("add name=\"%s\" local-address=%s remote-address=\"%s\" rate-limit=\"%dM/%dM\" comment=\"Profile for %s plan\"\n", 
                profileName, localAddress, poolName, plan.getUploadMbps(), plan.getDownloadMbps(), plan.getPlanName()));
        }
        sb.append("\n");
        
        // Configure PPPoE server interface
        String pppoeInterface = config.getBridgeInterface() != null ? config.getBridgeInterface() : config.getLanInterface();
        if (pppoeInterface != null) {
            sb.append("/interface pppoe-server server\n");
            sb.append(String.format("add service-name=\"rainet-isp\" interface=%s disabled=no one-session-per-host=yes max-mtu=1480 max-mru=1480\n", 
                pppoeInterface));
            sb.append("\n");
        }
        
        // PPP AAA configuration (authentication)
        sb.append("/ppp aaa\n");
        sb.append("set use-radius=yes\n");
        
        if (config.getRadiusServer() != null) {
            sb.append("set interim-update=1m\n");
            sb.append("set accounting=yes\n");
        }
        sb.append("\n");
        
        // RADIUS server configuration
        if (config.getRadiusServer() != null && config.getRadiusSecret() != null) {
            sb.append("/radius\n");
            sb.append(String.format("add service=ppp address=%s secret=\"%s\" comment=\"PPPoE RADIUS Server\"\n", 
                config.getRadiusServer(), config.getRadiusSecret()));
        }
        
        return sb.toString();
    }
}
