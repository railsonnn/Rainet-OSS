package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

/**
 * Builds interface configuration section.
 */
@Component
public class InterfaceSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Interface Configuration\n");
        sb.append("/interface\n");
        
        if (config.getWanInterface() != null) {
            sb.append(String.format("set ether1 name=%s\n", config.getWanInterface()));
        }
        
        if (config.getLanInterface() != null) {
            sb.append(String.format("set ether2 name=%s\n", config.getLanInterface()));
        }
        
        return sb.toString();
    }
}
