package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

/**
 * Builds bridge configuration section.
 */
@Component
public class BridgeSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Bridge Configuration\n");
        
        if (config.getBridgeInterface() != null) {
            sb.append("/interface/bridge\n");
            sb.append(String.format("add name=%s comment=\"LAN Bridge\"\n", config.getBridgeInterface()));
            sb.append("/interface/bridge/port\n");
            sb.append(String.format("add interface=%s bridge=%s\n", config.getLanInterface(), config.getBridgeInterface()));
        }
        
        return sb.toString();
    }
}
