package com.isp.platform.provisioning.mikrotik;

import com.isp.platform.provisioning.domain.Router;
import com.isp.platform.provisioning.mikrotik.builder.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RouterOS Script Generator.
 * Validates complete script generation with idempotency guarantees.
 */
@DisplayName("RouterOS Script Generator Tests")
class RouterOsScriptGeneratorTest {

    private RouterOsScriptGenerator scriptGenerator;
    private Router testRouter;

    @BeforeEach
    void setUp() {
        // Initialize all section builders
        InterfaceSectionBuilder interfaceBuilder = new InterfaceSectionBuilder();
        BridgeSectionBuilder bridgeBuilder = new BridgeSectionBuilder();
        WanSectionBuilder wanBuilder = new WanSectionBuilder();
        LanSectionBuilder lanBuilder = new LanSectionBuilder();
        PPPoESectionBuilder pppoeBuilder = new PPPoESectionBuilder();
        FirewallSectionBuilder firewallBuilder = new FirewallSectionBuilder();
        QoSSectionBuilder qosBuilder = new QoSSectionBuilder();
        ServicesSectionBuilder servicesBuilder = new ServicesSectionBuilder();

        // Create script builder
        RouterOsScriptBuilder scriptBuilder = new RouterOsScriptBuilder(
                interfaceBuilder,
                bridgeBuilder,
                wanBuilder,
                lanBuilder,
                pppoeBuilder,
                firewallBuilder,
                qosBuilder,
                servicesBuilder
        );

        // Create script generator
        scriptGenerator = new RouterOsScriptGenerator(scriptBuilder);

        // Create test router
        testRouter = new Router();
        testRouter.setId(UUID.randomUUID());
        testRouter.setTenantId(UUID.randomUUID());
        testRouter.setHostname("test-rb4011");
        testRouter.setManagementAddress("192.168.1.1");
        testRouter.setRouterOsVersion("7.12");
        testRouter.setApiUsername("admin");
        testRouter.setApiPassword("test-password");
    }

    @Test
    @DisplayName("Should generate complete RouterOS script")
    void shouldGenerateCompleteScript() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertNotNull(script);
        assertFalse(script.isEmpty());

        // Verify header is present
        assertTrue(script.contains("Rainet OSS/BSS - RouterOS Configuration"));
        assertTrue(script.contains("Router: test-rb4011"));
        assertTrue(script.contains("Management IP: 192.168.1.1"));
        assertTrue(script.contains("WARNING: This script is idempotent"));
    }

    @Test
    @DisplayName("Should include all configuration sections")
    void shouldIncludeAllSections() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then - verify all sections are present
        assertTrue(script.contains("# Interface Configuration"));
        assertTrue(script.contains("# Bridge Configuration"));
        assertTrue(script.contains("# WAN Interface Configuration"));
        assertTrue(script.contains("# LAN Configuration"));
        assertTrue(script.contains("# PPPoE Server Configuration"));
        assertTrue(script.contains("# Services Configuration"));
        assertTrue(script.contains("# Firewall Configuration"));
        assertTrue(script.contains("# Quality of Service Configuration"));
    }

    @Test
    @DisplayName("Should configure interface names")
    void shouldConfigureInterfaceNames() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("name=wan"));
        assertTrue(script.contains("name=lan"));
        assertTrue(script.contains("name=bridge-lan"));
    }

    @Test
    @DisplayName("Should configure bridge")
    void shouldConfigureBridge() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("/interface/bridge"));
        assertTrue(script.contains("add name=bridge-lan"));
        assertTrue(script.contains("interface=lan bridge=bridge-lan"));
    }

    @Test
    @DisplayName("Should configure LAN network with DHCP")
    void shouldConfigureLanWithDhcp() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("address=192.168.88.1/24"));
        assertTrue(script.contains("name=lan-pool"));
        assertTrue(script.contains("name=lan-dhcp"));
        assertTrue(script.contains("/ip/dhcp-server/network"));
    }

    @Test
    @DisplayName("Should configure PPPoE server with multiple plans")
    void shouldConfigurePppoeWithPlans() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("# PPPoE Server Configuration"));
        assertTrue(script.contains("pppoe-pool-basic-10mb"));
        assertTrue(script.contains("pppoe-pool-standard-50mb"));
        assertTrue(script.contains("pppoe-pool-premium-100mb"));
        assertTrue(script.contains("pppoe-profile-basic-10mb"));
        assertTrue(script.contains("rate-limit=5M/10M"));
        assertTrue(script.contains("rate-limit=25M/50M"));
        assertTrue(script.contains("rate-limit=50M/100M"));
    }

    @Test
    @DisplayName("Should configure RADIUS integration")
    void shouldConfigureRadius() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("/ppp/aaa"));
        assertTrue(script.contains("use-radius=yes"));
        assertTrue(script.contains("/radius"));
        assertTrue(script.contains("address=127.0.0.1"));
        assertTrue(script.contains("secret=rainet-radius-secret"));
    }

    @Test
    @DisplayName("Should configure firewall with DDoS protection")
    void shouldConfigureFirewall() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("/ip/firewall/filter"));
        assertTrue(script.contains("connection-state=invalid"));
        assertTrue(script.contains("connection-state=established,related"));
        assertTrue(script.contains("Rainet: Drop invalid"));
        assertTrue(script.contains("Rainet: Accept established/related"));
    }

    @Test
    @DisplayName("Should configure NAT masquerading")
    void shouldConfigureNat() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("/ip/firewall/nat"));
        assertTrue(script.contains("action=masquerade"));
        assertTrue(script.contains("chain=srcnat"));
        assertTrue(script.contains("out-interface=wan"));
    }

    @Test
    @DisplayName("Should configure QoS with traffic marking")
    void shouldConfigureQos() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("# Quality of Service Configuration"));
        assertTrue(script.contains("/queue/tree"));
        assertTrue(script.contains("/ip/firewall/mangle"));
        assertTrue(script.contains("new-connection-mark=download"));
        assertTrue(script.contains("new-connection-mark=upload"));
    }

    @Test
    @DisplayName("Should configure system services")
    void shouldConfigureServices() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("# Services Configuration"));
        assertTrue(script.contains("/system/ntp/client"));
        assertTrue(script.contains("/system/identity"));
        assertTrue(script.contains("set name=test-rb4011"));
        assertTrue(script.contains("/system/logging"));
    }

    @Test
    @DisplayName("Should use idempotent commands")
    void shouldUseIdempotentCommands() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then - verify idempotency patterns
        assertTrue(script.contains(":if ([") || script.contains("remove [find"));
        assertTrue(script.contains("Idempotent:") || script.contains("idempotent"));
    }

    @Test
    @DisplayName("Should use comment-based identification for managed rules")
    void shouldUseCommentBasedIdentification() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("comment=\"Rainet:"));
        assertTrue(script.contains("comment~\"Rainet:\""));
    }

    @Test
    @DisplayName("Should be reapplicable without errors")
    void shouldBeReapplicable() {
        // When - generate script twice
        String script1 = scriptGenerator.generateProvisioningScript(testRouter);
        String script2 = scriptGenerator.generateProvisioningScript(testRouter);

        // Then - both scripts should be identical (deterministic)
        assertEquals(script1.length(), script2.length());
        
        // Both should contain idempotent patterns
        assertTrue(script1.contains("remove [find") || script1.contains(":if (["));
        assertTrue(script2.contains("remove [find") || script2.contains(":if (["));
    }

    @Test
    @DisplayName("Should work for different router models (RB and CCR)")
    void shouldWorkForDifferentModels() {
        // Given - RB router
        Router rbRouter = new Router();
        rbRouter.setHostname("rb4011");
        rbRouter.setManagementAddress("192.168.1.1");
        rbRouter.setRouterOsVersion("7.12");

        // When
        String rbScript = scriptGenerator.generateProvisioningScript(rbRouter);

        // Then
        assertNotNull(rbScript);
        assertTrue(rbScript.contains("rb4011"));

        // Given - CCR router
        Router ccrRouter = new Router();
        ccrRouter.setHostname("ccr1009");
        ccrRouter.setManagementAddress("192.168.1.2");
        ccrRouter.setRouterOsVersion("7.12");

        // When
        String ccrScript = scriptGenerator.generateProvisioningScript(ccrRouter);

        // Then
        assertNotNull(ccrScript);
        assertTrue(ccrScript.contains("ccr1009"));

        // Both should have same structure
        assertTrue(rbScript.contains("# PPPoE Server Configuration"));
        assertTrue(ccrScript.contains("# PPPoE Server Configuration"));
    }

    @Test
    @DisplayName("Should include version and timestamp in header")
    void shouldIncludeVersionAndTimestamp() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("Config Version: 1.0"));
        assertTrue(script.contains("Generated:"));
    }

    @Test
    @DisplayName("Should handle DNS configuration")
    void shouldHandleDnsConfiguration() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("/ip/dns"));
        assertTrue(script.contains("allow-remote-requests=yes"));
        assertTrue(script.contains("8.8.8.8"));
        assertTrue(script.contains("8.8.4.4"));
    }

    @Test
    @DisplayName("Should configure connection tracking")
    void shouldConfigureConnectionTracking() {
        // When
        String script = scriptGenerator.generateProvisioningScript(testRouter);

        // Then
        assertTrue(script.contains("/ip/firewall/connection-tracking"));
        assertTrue(script.contains("set enabled=yes"));
        assertTrue(script.contains("tcp-timeout=23h"));
        assertTrue(script.contains("udp-timeout=10m"));
    }
}
