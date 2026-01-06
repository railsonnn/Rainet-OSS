package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

/**
 * Builds WAN interface configuration section.
 */
@Component
public class WanSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# WAN Interface Configuration\n");
        
        if (config.getWanAddress() != null && config.getWanInterface() != null) {
            sb.append("/ip/address\n");
            sb.append(String.format("add address=%s interface=%s comment=\"WAN Address\"\n", 
                config.getWanAddress(), config.getWanInterface()));
        }
        
        if (config.getWanGateway() != null) {
            sb.append("/ip/route\n");
            sb.append(String.format("add dst-address=0.0.0.0/0 gateway=%s comment=\"Default Route\"\n", 
                config.getWanGateway()));
        }
        
        if (config.getLanDns1() != null || config.getLanDns2() != null) {
            sb.append("/ip/dns\n");
            sb.append("set allow-remote-requests=yes\n");
            if (config.getLanDns1() != null) {
                sb.append(String.format("set servers=%s", config.getLanDns1()));
                if (config.getLanDns2() != null) {
                    sb.append(String.format(",%s", config.getLanDns2()));
                }
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }
}
