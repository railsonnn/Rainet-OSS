package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

/**
 * Builds LAN interface configuration section.
 */
@Component
public class LanSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# LAN Configuration\n");
        
        if (config.getLanNetwork() != null && config.getBridgeInterface() != null) {
            sb.append("/ip/address\n");
            sb.append(String.format("add address=%s interface=%s comment=\"LAN Network\"\n", 
                config.getLanNetwork(), config.getBridgeInterface()));
        }
        
        // DHCP Server configuration
        if (config.getLanNetwork() != null) {
            String[] lanParts = config.getLanNetwork().split("/");
            String lanIp = lanParts[0];
            
            sb.append("/ip/pool\n");
            sb.append(String.format("add name=lan-pool ranges=%s-%s\n", 
                incrementIp(lanIp, 1), incrementIp(lanIp, 254)));
            
            sb.append("/ip/dhcp-server\n");
            sb.append(String.format("add name=lan-dhcp interface=%s address-pool=lan-pool\n", 
                config.getBridgeInterface()));
            
            sb.append("/ip/dhcp-server/network\n");
            sb.append(String.format("add address=%s comment=\"LAN DHCP Network\"\n", config.getLanNetwork()));
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
