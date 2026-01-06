package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

/**
 * Builds bridge configuration section.
 * Uses idempotent commands with existence checks.
 */
@Component
public class BridgeSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Bridge Configuration\n");
        sb.append("# Idempotent: checks existence before creating\n");
        
        if (config.getBridgeInterface() != null) {
            sb.append("/interface/bridge\n");
            sb.append(String.format(":if ([/interface/bridge print count-only where name=\"%s\"] = 0) do={\n", config.getBridgeInterface()));
            sb.append(String.format("  add name=%s comment=\"LAN Bridge\"\n", config.getBridgeInterface()));
            sb.append("}\n");
            
            if (config.getLanInterface() != null) {
                sb.append("/interface/bridge/port\n");
                sb.append(String.format(":if ([/interface/bridge/port print count-only where interface=\"%s\"] = 0) do={\n", config.getLanInterface()));
                sb.append(String.format("  add interface=%s bridge=%s\n", config.getLanInterface(), config.getBridgeInterface()));
                sb.append("}\n");
            }
        }
        
        return sb.toString();
    }
}
