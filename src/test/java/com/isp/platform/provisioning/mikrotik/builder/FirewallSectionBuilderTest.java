package com.isp.platform.provisioning.mikrotik.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FirewallSectionBuilder.
 * Validates that firewall rules include flood protection, NAT masquerading,
 * and no duplicate rules.
 */
@DisplayName("FirewallSectionBuilder Tests")
class FirewallSectionBuilderTest {

    private FirewallSectionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new FirewallSectionBuilder();
    }

    @Test
    @DisplayName("Should return disabled message when firewall is disabled")
    void testFirewallDisabled() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(false)
                .build();

        String result = builder.build(config);

        assertEquals("# Firewall: Disabled\n", result);
    }

    @Test
    @DisplayName("Should include connection tracking configuration")
    void testConnectionTracking() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(false)
                .build();

        String result = builder.build(config);

        assertTrue(result.contains("/ip/firewall/connection-tracking"));
        assertTrue(result.contains("set enabled=yes"));
        assertTrue(result.contains("set tcp-timeout=23h"));
        assertTrue(result.contains("set udp-timeout=10m"));
    }

    @Test
    @DisplayName("Should drop invalid connections")
    void testDropInvalid() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(false)
                .build();

        String result = builder.build(config);

        assertTrue(result.contains("connection-state=invalid"));
        assertTrue(result.contains("Drop invalid input"));
        assertTrue(result.contains("Drop invalid forward"));
    }

    @Test
    @DisplayName("Should include SYN flood protection")
    void testSynFloodProtection() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(false)
                .build();

        String result = builder.build(config);

        assertTrue(result.contains("Drop SYN flood"));
        assertTrue(result.contains("protocol=tcp tcp-flags=syn connection-limit=30,32"));
    }

    @Test
    @DisplayName("Should include connection rate limiting")
    void testConnectionRateLimiting() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(false)
                .build();

        String result = builder.build(config);

        assertTrue(result.contains("Track new connections"));
        assertTrue(result.contains("Drop connection flood"));
        assertTrue(result.contains("connection_limit"));
        assertTrue(result.contains("connection-limit=20,32"));
    }

    @Test
    @DisplayName("Should include port scan detection")
    void testPortScanDetection() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(false)
                .build();

        String result = builder.build(config);

        assertTrue(result.contains("Detect port scan"));
        assertTrue(result.contains("Drop port scanners"));
        assertTrue(result.contains("port_scanners"));
        assertTrue(result.contains("psd=21,3s,3,1"));
    }

    @Test
    @DisplayName("Should include ICMP rate limiting")
    void testIcmpRateLimiting() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(false)
                .build();

        String result = builder.build(config);

        assertTrue(result.contains("Accept limited ICMP"));
        assertTrue(result.contains("Drop ICMP flood"));
        assertTrue(result.contains("protocol=icmp limit=5,5:packet"));
    }

    @Test
    @DisplayName("Should accept established and related connections")
    void testAcceptEstablishedRelated() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(false)
                .build();

        String result = builder.build(config);

        assertTrue(result.contains("Accept established/related input"));
        assertTrue(result.contains("Accept established/related forward"));
        assertTrue(result.contains("connection-state=established,related"));
    }

    @Test
    @DisplayName("Should accept traffic from LAN")
    void testAcceptFromLan() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(false)
                .build();

        String result = builder.build(config);

        assertTrue(result.contains("Accept from LAN"));
        assertTrue(result.contains("in-interface=ether2+ out-interface=ether1"));
    }

    @Test
    @DisplayName("Should include NAT masquerade when enabled")
    void testNatMasquerade() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(true)
                .build();

        String result = builder.build(config);

        assertTrue(result.contains("/ip/firewall/nat"));
        assertTrue(result.contains("action=masquerade"));
        assertTrue(result.contains("chain=srcnat out-interface=ether1"));
        assertTrue(result.contains("Masquerade WAN"));
    }

    @Test
    @DisplayName("Should not include NAT when disabled")
    void testNatDisabled() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(false)
                .build();

        String result = builder.build(config);

        assertFalse(result.contains("/ip/firewall/nat"));
        assertFalse(result.contains("action=masquerade"));
    }

    @Test
    @DisplayName("Should not contain duplicate rules")
    void testNoDuplicateRules() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(true)
                .build();

        String result = builder.build(config);

        // Count occurrences of key rules to ensure no duplicates
        int invalidDropCount = countOccurrences(result, "Drop invalid");
        int establishedAcceptCount = countOccurrences(result, "Accept established/related");
        int masqueradeCount = countOccurrences(result, "action=masquerade");

        // Each unique rule should appear exactly the expected number of times
        assertEquals(2, invalidDropCount, "Should have 2 invalid drop rules (input and forward)");
        assertEquals(2, establishedAcceptCount, "Should have 2 established/related accept rules (input and forward)");
        assertEquals(1, masqueradeCount, "Should have exactly 1 masquerade rule");
    }

    @Test
    @DisplayName("Should maintain proper rule order")
    void testRuleOrder() {
        RouterOsConfig config = RouterOsConfig.builder()
                .firewallEnabled(true)
                .natEnabled(true)
                .build();

        String result = builder.build(config);

        // Verify that flood protection comes before accept rules
        int floodProtectionIndex = result.indexOf("Drop SYN flood");
        int acceptEstablishedIndex = result.indexOf("Accept established/related");
        int dropOtherIndex = result.indexOf("Drop other input");

        assertTrue(floodProtectionIndex > 0, "Should have flood protection");
        assertTrue(acceptEstablishedIndex > 0, "Should have accept established rule");
        assertTrue(dropOtherIndex > 0, "Should have drop other rule");

        // Flood protection should come before accept established
        assertTrue(floodProtectionIndex < acceptEstablishedIndex,
                "Flood protection should be evaluated before accept rules");

        // Drop other should be at the end
        assertTrue(dropOtherIndex > acceptEstablishedIndex,
                "Drop other should be after accept rules");
    }

    /**
     * Helper method to count occurrences of a substring.
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
