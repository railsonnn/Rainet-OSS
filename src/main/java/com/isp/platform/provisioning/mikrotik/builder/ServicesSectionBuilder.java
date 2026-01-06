package com.isp.platform.provisioning.mikrotik.builder;

import org.springframework.stereotype.Component;

/**
 * Builds services configuration section (NTP, DNS, SSH, etc).
 */
@Component
public class ServicesSectionBuilder {

    public String build(RouterOsConfig config) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Services Configuration\n");
        
        // NTP Client
        sb.append("/system/ntp/client\n");
        sb.append("set enabled=yes\n");
        sb.append("set server=pool.ntp.org\n");
        
        // Identity
        if (config.getRouterName() != null) {
            sb.append("/system/identity\n");
            sb.append(String.format("set name=%s\n", config.getRouterName()));
        }
        
        // Logging
        sb.append("/system/logging\n");
        sb.append("add topics=info action=memory\n");
        sb.append("add topics=warning action=memory\n");
        sb.append("add topics=error action=memory\n");
        
        // Enable required services
        sb.append("/ip/service\n");
        sb.append("set telnet disabled=yes\n");
        sb.append("set ftp disabled=yes\n");
        sb.append("set www disabled=yes\n");
        sb.append("set ssh disabled=no\n");
        sb.append("set api disabled=yes\n");
        sb.append("set api-ssl disabled=yes\n");
        
        return sb.toString();
    }
}
