package com.isp.platform.provisioning.mikrotik.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PPPoE Section Builder Tests")
class PPPoESectionBuilderTest {

    private PPPoESectionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PPPoESectionBuilder();
    }

    @Test
    @DisplayName("Should generate complete PPPoE configuration with all required sections")
    void testGenerateCompletePPPoEConfiguration() {
        // Given
        RouterOsConfig config = RouterOsConfig.builder()
            .pppoeEnabled(true)
            .bridgeInterface("bridge-lan")
            .lanInterface("lan")
            .radiusServer("192.168.1.100")
            .radiusSecret("test-secret")
            .pppePlans(List.of(
                RouterOsConfig.PPPoEPlan.builder()
                    .planName("basic-10mb")
                    .poolPrefix("10.10.10")
                    .uploadMbps(5)
                    .downloadMbps(10)
                    .poolSize(254)
                    .build()
            ))
            .build();

        // When
        String script = builder.build(config);

        // Then
        assertNotNull(script);
        
        // Verify IP pool section
        assertTrue(script.contains("/ip pool"), "Should contain IP pool section");
        assertTrue(script.contains("add name=\"pppoe-pool-basic-10mb\""), "Should create named pool");
        assertTrue(script.contains("ranges=10.10.10.1-10.10.10.254"), "Should define pool range");
        
        // Verify PPP profile section
        assertTrue(script.contains("/ppp profile"), "Should contain PPP profile section");
        assertTrue(script.contains("add name=\"pppoe-profile-basic-10mb\""), "Should create named profile");
        assertTrue(script.contains("local-address=10.10.10.1"), "Should set local address");
        assertTrue(script.contains("remote-address=\"pppoe-pool-basic-10mb\""), "Should reference pool");
        assertTrue(script.contains("rate-limit=\"5M/10M\""), "Should set bandwidth limits");
        
        // Verify PPPoE server section
        assertTrue(script.contains("/interface pppoe-server server"), "Should contain PPPoE server section");
        assertTrue(script.contains("service-name=\"rainet-isp\""), "Should set service name");
        assertTrue(script.contains("interface=bridge-lan"), "Should bind to interface");
        assertTrue(script.contains("one-session-per-host=yes"), "Should enforce one session per host");
        assertTrue(script.contains("max-mtu=1480"), "Should set MTU");
        assertTrue(script.contains("max-mru=1480"), "Should set MRU");
        
        // Verify AAA configuration
        assertTrue(script.contains("/ppp aaa"), "Should contain PPP AAA section");
        assertTrue(script.contains("set use-radius=yes"), "Should enable RADIUS");
        assertTrue(script.contains("set accounting=yes"), "Should enable accounting");
        
        // Verify RADIUS server configuration
        assertTrue(script.contains("/radius"), "Should contain RADIUS section");
        assertTrue(script.contains("add service=ppp"), "Should configure RADIUS for PPP");
        assertTrue(script.contains("address=192.168.1.100"), "Should set RADIUS server address");
        assertTrue(script.contains("secret=\"test-secret\""), "Should set RADIUS secret");
    }

    @Test
    @DisplayName("Should handle multiple PPPoE plans")
    void testMultiplePPPoEPlans() {
        // Given
        RouterOsConfig config = RouterOsConfig.builder()
            .pppoeEnabled(true)
            .bridgeInterface("bridge-lan")
            .radiusServer("192.168.1.100")
            .radiusSecret("test-secret")
            .pppePlans(List.of(
                RouterOsConfig.PPPoEPlan.builder()
                    .planName("basic-10mb")
                    .poolPrefix("10.10.10")
                    .uploadMbps(5)
                    .downloadMbps(10)
                    .poolSize(254)
                    .build(),
                RouterOsConfig.PPPoEPlan.builder()
                    .planName("premium-100mb")
                    .poolPrefix("10.10.20")
                    .uploadMbps(50)
                    .downloadMbps(100)
                    .poolSize(254)
                    .build()
            ))
            .build();

        // When
        String script = builder.build(config);

        // Then
        // Verify both pools are created
        assertTrue(script.contains("pppoe-pool-basic-10mb"), "Should create basic plan pool");
        assertTrue(script.contains("pppoe-pool-premium-100mb"), "Should create premium plan pool");
        
        // Verify both profiles are created with correct limits
        assertTrue(script.contains("rate-limit=\"5M/10M\""), "Should set basic plan limits");
        assertTrue(script.contains("rate-limit=\"50M/100M\""), "Should set premium plan limits");
    }

    @Test
    @DisplayName("Should return disabled message when PPPoE is disabled")
    void testPPPoEDisabled() {
        // Given
        RouterOsConfig config = RouterOsConfig.builder()
            .pppoeEnabled(false)
            .build();

        // When
        String script = builder.build(config);

        // Then
        assertEquals("# PPPoE Server: Disabled\n", script);
    }

    @Test
    @DisplayName("Should use LAN interface if bridge interface is not available")
    void testFallbackToLanInterface() {
        // Given
        RouterOsConfig config = RouterOsConfig.builder()
            .pppoeEnabled(true)
            .lanInterface("lan-interface")
            .radiusServer("192.168.1.100")
            .radiusSecret("test-secret")
            .pppePlans(List.of(
                RouterOsConfig.PPPoEPlan.builder()
                    .planName("basic-10mb")
                    .poolPrefix("10.10.10")
                    .uploadMbps(5)
                    .downloadMbps(10)
                    .poolSize(254)
                    .build()
            ))
            .build();

        // When
        String script = builder.build(config);

        // Then
        assertTrue(script.contains("interface=lan-interface"), "Should use LAN interface when bridge is not available");
    }
}
