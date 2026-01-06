package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds QoS (Quality of Service) configuration section.
 * Uses idempotent commands with comment-based identification.
 */
@Component
public class QoSSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        if (!config.isQosEnabled()) {
            return "# QoS: Disabled\n";
        }
        
        sb.append("# Quality of Service Configuration\n");
        sb.append("# Idempotent: removes rules by comment before adding\n");
        
        // Queue trees for traffic shaping
        sb.append("/queue/tree\n");
        sb.append(":foreach rule in=[find comment~\"Rainet:\"] do={ /queue/tree remove $rule }\n");
        
        if (config.getDefaultBandwidthMbps() > 0) {
            sb.append(String.format("add name=global-root parent=global-out max-limit=%dM comment=\"Rainet: Global bandwidth\"\n",
                config.getDefaultBandwidthMbps()));
        }
        
        // Add QoS profiles
        if (config.getQosProfiles() != null && !config.getQosProfiles().isEmpty()) {
            sb.append("/queue/type\n");
            sb.append(":foreach rule in=[find comment~\"Rainet:\"] do={ /queue/type remove $rule }\n");
            
            for (RouterOsConfig.QoSProfile profile : config.getQosProfiles()) {
                sb.append(String.format("add name=%s kind=pcq pcq-rate=%dM priority=%d comment=\"Rainet: QoS profile\"\n", 
                    profile.getProfileName(), profile.getBandwidthMbps(), profile.getPriorityLevel()));
            }
        }
        
        // Simple traffic marking for QoS
        sb.append("/ip/firewall/mangle\n");
        sb.append(":foreach rule in=[find comment~\"Rainet: QoS\"] do={ /ip/firewall/mangle remove $rule }\n");
        
        if (config.getWanInterface() != null && config.getBridgeInterface() != null) {
            sb.append(String.format("add action=mark-connection chain=forward in-interface=%s out-interface=%s new-connection-mark=download comment=\"Rainet: QoS Mark download\"\n",
                config.getWanInterface(), config.getBridgeInterface()));
            sb.append(String.format("add action=mark-connection chain=forward in-interface=%s out-interface=%s new-connection-mark=upload comment=\"Rainet: QoS Mark upload\"\n",
                config.getBridgeInterface(), config.getWanInterface()));
        }
        
        return sb.toString();
    }
}
