package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds firewall and NAT configuration section.
 * Includes DDoS protection, connection tracking, and masquerading.
 * Uses idempotent commands with comments for identification.
 */
@Component
public class FirewallSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        if (!config.isFirewallEnabled()) {
            return "# Firewall: Disabled\n";
        }
        
        sb.append("# Firewall Configuration\n");
        sb.append("# Idempotent: removes rules by comment before adding\n");
        
        // Connection tracking
        sb.append("/ip/firewall/connection-tracking\n");
        sb.append("set enabled=yes\n");
        sb.append("set tcp-timeout=23h\n");
        sb.append("set udp-timeout=10m\n");
        
        // Firewall filter rules - idempotent by removing with comments
        sb.append("/ip/firewall/filter\n");
        
        // Remove existing Rainet rules before adding new ones
        sb.append(":foreach rule in=[find comment~\"Rainet:\"] do={ /ip/firewall/filter remove $rule }\n");
        
        // Drop invalid connections
        sb.append("add action=drop chain=forward connection-state=invalid comment=\"Rainet: Drop invalid\"\n");
        
        // Accept established and related
        sb.append("add action=accept chain=forward connection-state=established,related comment=\"Rainet: Accept established/related\"\n");
        
        // Accept new connections from LAN
        if (config.getBridgeInterface() != null && config.getWanInterface() != null) {
            sb.append(String.format("add action=accept chain=forward in-interface=%s out-interface=%s comment=\"Rainet: Accept from LAN\"\n",
                config.getBridgeInterface(), config.getWanInterface()));
        }
        
        // Drop other forward traffic
        sb.append("add action=drop chain=forward comment=\"Rainet: Drop other forward\"\n");
        
        // INPUT chain
        sb.append("add action=accept chain=input connection-state=established,related comment=\"Rainet: Accept input established/related\"\n");
        sb.append("add action=accept chain=input protocol=icmp comment=\"Rainet: Accept ICMP\"\n");
        
        if (config.getWanInterface() != null) {
            sb.append(String.format("add action=accept chain=input in-interface=!%s comment=\"Rainet: Accept from local\"\n",
                config.getWanInterface()));
        }
        
        sb.append("add action=drop chain=input comment=\"Rainet: Drop other input\"\n");
        
        // Add custom rules if any
        if (config.getCustomRules() != null && !config.getCustomRules().isEmpty()) {
            for (RouterOsConfig.FirewallRule rule : config.getCustomRules()) {
                sb.append(formatFirewallRule(rule)).append("\n");
            }
        }
        
        // NAT Configuration
        if (config.isNatEnabled() && config.getWanInterface() != null) {
            sb.append("\n/ip/firewall/nat\n");
            
            // Remove existing Rainet NAT rules
            sb.append(":foreach rule in=[find comment~\"Rainet:\"] do={ /ip/firewall/nat remove $rule }\n");
            
            // Masquerade for WAN
            sb.append(String.format("add action=masquerade chain=srcnat out-interface=%s comment=\"Rainet: Masquerade WAN\"\n",
                config.getWanInterface()));
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
            sb.append(" comment=\"Rainet: ").append(rule.getComment()).append("\"");
        }
        
        return sb.toString();
    }
}
