package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds firewall and NAT configuration section.
 * Includes flood protection (SYN flood, connection rate limiting, port scan detection),
 * connection tracking, and NAT masquerading.
 */
@Component
public class FirewallSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        if (!config.isFirewallEnabled()) {
            return "# Firewall: Disabled\n";
        }
        
        sb.append("# Firewall Configuration\n");
        
        // Connection tracking
        sb.append("/ip/firewall/connection-tracking\n");
        sb.append("set enabled=yes\n");
        sb.append("set tcp-timeout=23h\n");
        sb.append("set udp-timeout=10m\n");
        
        // Firewall filter rules
        sb.append("/ip/firewall/filter\n");
        
        // Drop invalid connections
        sb.append("add action=drop chain=input connection-state=invalid comment=\"Drop invalid input\"\n");
        sb.append("add action=drop chain=forward connection-state=invalid comment=\"Drop invalid forward\"\n");
        
        // Flood protection - SYN flood
        sb.append("add action=drop chain=input protocol=tcp tcp-flags=syn connection-limit=30,32 comment=\"Drop SYN flood\"\n");
        
        // Flood protection - Connection rate limiting per IP
        sb.append("add action=add-src-to-address-list chain=input connection-state=new src-address-list=connection_limit address-list-timeout=1m comment=\"Track new connections\"\n");
        sb.append("add action=drop chain=input src-address-list=connection_limit connection-state=new connection-limit=20,32 comment=\"Drop connection flood\"\n");
        
        // Flood protection - Port scan detection
        sb.append("add action=add-src-to-address-list chain=input protocol=tcp psd=21,3s,3,1 address-list=port_scanners address-list-timeout=1d comment=\"Detect port scan\"\n");
        sb.append("add action=drop chain=input src-address-list=port_scanners comment=\"Drop port scanners\"\n");
        
        // Flood protection - ICMP rate limiting
        sb.append("add action=accept chain=input protocol=icmp limit=5,5:packet comment=\"Accept limited ICMP\"\n");
        sb.append("add action=drop chain=input protocol=icmp comment=\"Drop ICMP flood\"\n");
        
        // Accept established and related
        sb.append("add action=accept chain=input connection-state=established,related comment=\"Accept established/related input\"\n");
        sb.append("add action=accept chain=forward connection-state=established,related comment=\"Accept established/related forward\"\n");
        
        // Accept new connections from LAN
        sb.append("add action=accept chain=forward in-interface=ether2+ out-interface=ether1 comment=\"Accept from LAN\"\n");
        
        // Accept from local interfaces (INPUT chain)
        sb.append("add action=accept chain=input in-interface=!ether1 comment=\"Accept from local\"\n");
        
        // Drop other traffic
        sb.append("add action=drop chain=input comment=\"Drop other input\"\n");
        sb.append("add action=drop chain=forward comment=\"Drop other forward\"\n");
        
        // Add custom rules if any
        if (config.getCustomRules() != null && !config.getCustomRules().isEmpty()) {
            for (RouterOsConfig.FirewallRule rule : config.getCustomRules()) {
                sb.append(formatFirewallRule(rule)).append("\n");
            }
        }
        
        // NAT Configuration
        if (config.isNatEnabled()) {
            sb.append("\n/ip/firewall/nat\n");
            
            // Masquerade for WAN
            sb.append("add action=masquerade chain=srcnat out-interface=ether1 comment=\"Masquerade WAN\"\n");
        }
        
        return sb.toString();
    }

    /**
     * Format a custom firewall rule.
     */
    private String formatFirewallRule(RouterOsConfig.FirewallRule rule) {
        StringBuilder sb = new StringBuilder("add");
        
        sb.append(" action=").append(rule.getAction());
        sb.append(" chain=").append(rule.getChain());
        
        if (rule.getProtocol() != null) {
            sb.append(" protocol=").append(rule.getProtocol());
        }
        
        if (rule.getSrcAddress() != null) {
            sb.append(" src-address=").append(rule.getSrcAddress());
        }
        
        if (rule.getDstAddress() != null) {
            sb.append(" dst-address=").append(rule.getDstAddress());
        }
        
        if (rule.getSrcPort() != null) {
            sb.append(" src-port=").append(rule.getSrcPort());
        }
        
        if (rule.getDstPort() != null) {
            sb.append(" dst-port=").append(rule.getDstPort());
        }
        
        if (rule.getComment() != null) {
            sb.append(" comment=\"").append(rule.getComment()).append("\"");
        }
        
        return sb.toString();
    }
}
