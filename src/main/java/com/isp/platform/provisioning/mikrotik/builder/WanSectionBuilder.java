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
        sb.append("# Idempotent: removes existing before adding\n");
        
        if (config.getWanAddress() != null && config.getWanInterface() != null && !"dhcp-client".equals(config.getWanAddress())) {
            sb.append("/ip/address\n");
            // Remove existing WAN address if present to avoid duplicates
            sb.append(String.format(":if ([/ip/address print count-only where interface=\"%s\" comment=\"WAN Address\"] > 0) do={\n", config.getWanInterface()));
            sb.append(String.format("  /ip/address remove [find interface=\"%s\" comment=\"WAN Address\"]\n", config.getWanInterface()));
            sb.append("}\n");
            sb.append(String.format("add address=%s interface=%s comment=\"WAN Address\"\n", 
                config.getWanAddress(), config.getWanInterface()));
        }
        
        if (config.getWanGateway() != null && !"auto".equals(config.getWanGateway())) {
            sb.append("/ip/route\n");
            // Remove existing default route to avoid duplicates
            sb.append(":if ([/ip/route print count-only where dst-address=\"0.0.0.0/0\" comment=\"Default Route\"] > 0) do={\n");
            sb.append("  /ip/route remove [find dst-address=\"0.0.0.0/0\" comment=\"Default Route\"]\n");
            sb.append("}\n");
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
