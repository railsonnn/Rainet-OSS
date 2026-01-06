package com.isp.platform.provisioning.mikrotik.builder;

import com.isp.platform.provisioning.domain.Router;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Modular RouterOS script builder that orchestrates section builders.
 * Each section is independently generated and combined into a single import-ready script.
 */
@Slf4j
@Component
public class RouterOsScriptBuilder {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InterfaceSectionBuilder interfaceBuilder;
    private final BridgeSectionBuilder bridgeBuilder;
    private final WanSectionBuilder wanBuilder;
    private final LanSectionBuilder lanBuilder;
    private final PPPoESectionBuilder pppoeBuilder;
    private final FirewallSectionBuilder firewallBuilder;
    private final QoSSectionBuilder qosBuilder;
    private final ServicesSectionBuilder servicesBuilder;

    public RouterOsScriptBuilder(
            InterfaceSectionBuilder interfaceBuilder,
            BridgeSectionBuilder bridgeBuilder,
            WanSectionBuilder wanBuilder,
            LanSectionBuilder lanBuilder,
            PPPoESectionBuilder pppoeBuilder,
            FirewallSectionBuilder firewallBuilder,
            QoSSectionBuilder qosBuilder,
            ServicesSectionBuilder servicesBuilder) {
        this.interfaceBuilder = interfaceBuilder;
        this.bridgeBuilder = bridgeBuilder;
        this.wanBuilder = wanBuilder;
        this.lanBuilder = lanBuilder;
        this.pppoeBuilder = pppoeBuilder;
        this.firewallBuilder = firewallBuilder;
        this.qosBuilder = qosBuilder;
        this.servicesBuilder = servicesBuilder;
    }

    /**
     * Build a complete RouterOS configuration script from a provisioning DTO.
     *
     * @param router the target router
     * @param config the provisioning configuration DTO
     * @return complete RouterOS import script
     */
    public String buildScript(Router router, RouterOsConfig config) {
        log.info("Building RouterOS script for router: {} (tenant: {})", router.getHostname(), router.getTenantId());

        StringBuilder script = new StringBuilder();

        // Add header with metadata
        appendHeader(script, router, config);

        // Add each section in order
        script.append(interfaceBuilder.build(config)).append("\n\n");
        script.append(bridgeBuilder.build(config)).append("\n\n");
        script.append(wanBuilder.build(config)).append("\n\n");
        script.append(lanBuilder.build(config)).append("\n\n");
        script.append(pppoeBuilder.build(config)).append("\n\n");
        script.append(servicesBuilder.build(config)).append("\n\n");
        script.append(firewallBuilder.build(config)).append("\n\n");
        script.append(qosBuilder.build(config)).append("\n\n");

        return script.toString();
    }

    /**
     * Append metadata header to the script.
     */
    private void appendHeader(StringBuilder script, Router router, RouterOsConfig config) {
        script.append("# ======================================================\n");
        script.append("# Rainet OSS/BSS - RouterOS Configuration\n");
        script.append("# ======================================================\n");
        script.append(String.format("# Router: %s\n", router.getHostname()));
        script.append(String.format("# Management IP: %s\n", router.getManagementAddress()));
        script.append(String.format("# Tenant ID: %s\n", router.getTenantId()));
        script.append(String.format("# Generated: %s\n", TIMESTAMP_FORMATTER.format(LocalDateTime.now())));
        script.append(String.format("# Config Version: %s\n", config.getVersion()));
        script.append("# ======================================================\n");
        script.append("# WARNING: This script is idempotent and safe to re-apply\n");
        script.append("# ======================================================\n\n");
    }
}
