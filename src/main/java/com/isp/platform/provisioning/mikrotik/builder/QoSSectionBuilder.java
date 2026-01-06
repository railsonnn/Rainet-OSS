package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds QoS (Quality of Service) configuration section.
 */
@Component
public class QoSSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        if (!config.isQosEnabled()) {
            return "# QoS: Disabled\n";
        }
        
        sb.append("# Quality of Service Configuration\n");
        
        // Queue trees for traffic shaping
        sb.append("/queue/tree\n");
        
        if (config.getDefaultBandwidthMbps() > 0) {
            sb.append(String.format("add name=global-root parent=global-out max-limit=%dM comment=\"Global bandwidth limit\"\n",
                config.getDefaultBandwidthMbps()));
        }
        
        // Add QoS profiles
        if (config.getQosProfiles() != null && !config.getQosProfiles().isEmpty()) {
            sb.append("/queue/type\n");
            
            for (RouterOsConfig.QoSProfile profile : config.getQosProfiles()) {
                sb.append(String.format("add name=%s priority=%d\n", 
                    profile.getProfileName(), profile.getPriorityLevel()));
            }
        }
        
        // Simple traffic marking for QoS
        sb.append("/ip/firewall/mangle\n");
        sb.append("add action=mark-connection chain=forward in-interface=ether1+ out-interface=ether2+ new-connection-mark=download comment=\"Mark download traffic\"\n");
        sb.append("add action=mark-connection chain=forward in-interface=ether2+ out-interface=ether1 new-connection-mark=upload comment=\"Mark upload traffic\"\n");
        
        return sb.toString();
    }
}
