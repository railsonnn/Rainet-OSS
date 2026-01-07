package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

/**
 * Builds LAN interface configuration section.
 * Uses idempotent commands with existence checks.
 */
@Component
public class LanSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# LAN Configuration\n");
        sb.append("# Idempotent: removes existing before adding\n");
        
        if (config.getLanNetwork() != null && config.getBridgeInterface() != null) {
            sb.append("/ip/address\n");
            // Remove existing LAN address to avoid duplicates
            sb.append(String.format(":if ([/ip/address print count-only where interface=\"%s\" comment=\"LAN Network\"] > 0) do={\n", config.getBridgeInterface()));
            sb.append(String.format("  /ip/address remove [find interface=\"%s\" comment=\"LAN Network\"]\n", config.getBridgeInterface()));
            sb.append("}\n");
            sb.append(String.format("add address=%s interface=%s comment=\"LAN Network\"\n", 
                config.getLanNetwork(), config.getBridgeInterface()));
        }
        
        // DHCP Server configuration
        if (config.getLanNetwork() != null) {
            String[] lanParts = config.getLanNetwork().split("/");
            String lanIp = lanParts[0];
            
            sb.append("/ip/pool\n");
            sb.append(":if ([/ip/pool print count-only where name=\"lan-pool\"] > 0) do={\n");
            sb.append("  /ip/pool remove [find name=\"lan-pool\"]\n");
            sb.append("}\n");
            // Reserve IPs 1-9 for static assignments (router gateway, servers, printers, etc.)
            sb.append(String.format("add name=lan-pool ranges=%s-%s\n", 
                incrementIp(lanIp, 10), incrementIp(lanIp, 254)));
            
            sb.append("/ip/dhcp-server\n");
            sb.append(":if ([/ip/dhcp-server print count-only where name=\"lan-dhcp\"] > 0) do={\n");
            sb.append("  /ip/dhcp-server remove [find name=\"lan-dhcp\"]\n");
            sb.append("}\n");
            sb.append(String.format("add name=lan-dhcp interface=%s address-pool=lan-pool disabled=no\n", 
                config.getBridgeInterface()));
            
            sb.append("/ip/dhcp-server/network\n");
            sb.append(String.format(":if ([/ip/dhcp-server/network print count-only where address=\"%s\"] > 0) do={\n", config.getLanNetwork()));
            sb.append(String.format("  /ip/dhcp-server/network remove [find address=\"%s\"]\n", config.getLanNetwork()));
            sb.append("}\n");
            sb.append(String.format("add address=%s gateway=%s dns-server=%s comment=\"LAN DHCP Network\"\n", 
                config.getLanNetwork(), lanIp, 
                config.getLanDns1() != null ? config.getLanDns1() : lanIp));
        }
        
        return sb.toString();
    }

    /**
     * Increment an IP address by a given amount.
     */
    private String incrementIp(String ip, int increment) {
        String[] parts = ip.split("\\.");
        int lastOctet = Integer.parseInt(parts[3]) + increment;
        return String.format("%s.%s.%s.%d", parts[0], parts[1], parts[2], lastOctet);
    }
}
