package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

/**
 * Builds interface configuration section.
 * Uses idempotent commands that can be safely reapplied.
 */
@Component
public class InterfaceSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Interface Configuration\n");
        sb.append("# Idempotent: 'set' commands can be reapplied safely\n");
        sb.append("/interface\n");
        
        if (config.getWanInterface() != null) {
            sb.append(String.format(":if ([/interface print count-only where name=\"%s\"] = 0) do={\n", config.getWanInterface()));
            sb.append(String.format("  set [find name=ether1] name=%s comment=\"WAN Interface\"\n", config.getWanInterface()));
            sb.append("}\n");
        }
        
        if (config.getLanInterface() != null) {
            sb.append(String.format(":if ([/interface print count-only where name=\"%s\"] = 0) do={\n", config.getLanInterface()));
            sb.append(String.format("  set [find name=ether2] name=%s comment=\"LAN Interface\"\n", config.getLanInterface()));
            sb.append("}\n");
        }
        
        return sb.toString();
    }
}
