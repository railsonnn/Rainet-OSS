package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds firewall and NAT configuration section.
 * Includes DDoS protection, connection tracking, and masquerading.
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
        sb.append("add action=drop chain=forward protocol=tcp tcp-flags=!syn,ack in-interface=ether1 out-interface=ether2+ comment=\"Drop invalid forward\"\n");
        sb.append("add action=drop chain=forward connection-state=invalid comment=\"Drop invalid connections\"\n");
        
        // Accept established and related
        sb.append("add action=accept chain=forward connection-state=established,related comment=\"Accept established/related\"\n");
        
        // Accept new connections from LAN
        sb.append("add action=accept chain=forward in-interface=ether2+ out-interface=ether1 comment=\"Accept from LAN\"\n");
        
        // Drop other forward traffic
        sb.append("add action=drop chain=forward comment=\"Drop other forward traffic\"\n");
        
        // INPUT chain
        sb.append("add action=accept chain=input connection-state=established,related comment=\"Accept established/related input\"\n");
        sb.append("add action=accept chain=input protocol=icmp comment=\"Accept ICMP\"\n");
        sb.append("add action=accept chain=input in-interface=!ether1 comment=\"Accept from local\"\n");
        sb.append("add action=drop chain=input comment=\"Drop other input\"\n");
        
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
