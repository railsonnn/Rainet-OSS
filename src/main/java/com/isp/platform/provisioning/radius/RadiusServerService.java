package com.isp.platform.provisioning.radius;

import com.isp.platform.customer.domain.Customer;
import com.isp.platform.customer.domain.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * RADIUS server integration for PPPoE authentication.
 * Authenticates customers via RADIUS and returns MikroTik-specific rate-limit attributes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadiusServerService {

    private final CustomerRepository customerRepository;

    @Value("${radius.mikrotik-rate-limit-attribute:Mikrotik-Rate-Limit}")
    private String rateLimitAttribute;

    /**
     * Authenticate a PPPoE user via RADIUS.
     * Checks customer status and returns plan-based rate limits.
     *
     * @param request RADIUS authentication request
     * @return RADIUS authentication response with plan details
     */
    public RadiusAuthRequest.RadiusAuthResponse authenticate(RadiusAuthRequest request) {
        log.info("RADIUS authentication request for user: {}", request.getUsername());

        try {
            // Find customer by document (username is customer document)
            Optional<Customer> customerOpt = findCustomerByUsername(request.getUsername());

            if (customerOpt.isEmpty()) {
                log.warn("Customer not found: {}", request.getUsername());
                return RadiusAuthRequest.RadiusAuthResponse.builder()
                    .authenticated(false)
                    .errorMessage("Customer not found")
                    .build();
            }

            Customer customer = customerOpt.get();

            // Check if customer is blocked (due to overdue invoices)
            if (customer.isBlocked()) {
                log.warn("Customer {} is blocked due to delinquency", request.getUsername());
                return createBlockedResponse();
            }

            // Check if customer status is not ACTIVE
            if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
                log.warn("Customer {} has status: {}", request.getUsername(), customer.getStatus());
                return RadiusAuthRequest.RadiusAuthResponse.builder()
                    .authenticated(false)
                    .errorMessage("Customer account is not active")
                    .build();
            }

            // Build successful response with plan from customer
            Map<String, String> attributes = buildMikrotikAttributes(customer.getPlan());

            RadiusAuthRequest.RadiusAuthResponse response = RadiusAuthRequest.RadiusAuthResponse.builder()
                .authenticated(true)
                .profileName(customer.getPlan())
                .uploadMbps(getUploadMbps(customer.getPlan()))
                .downloadMbps(getDownloadMbps(customer.getPlan()))
                .attributes(attributes)
                .build();

            log.info("RADIUS authentication successful for customer: {} with plan: {}", 
                request.getUsername(), customer.getPlan());

            return response;
        } catch (Exception e) {
            log.error("RADIUS authentication error for user: {}", request.getUsername(), e);
            return RadiusAuthRequest.RadiusAuthResponse.builder()
                .authenticated(false)
                .errorMessage("Authentication error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Find customer by username (document or UUID).
     * 
     * Uses indexed query on document field for efficient lookups.
     */
    private Optional<Customer> findCustomerByUsername(String username) {
        // Try to find by UUID first
        try {
            UUID id = UUID.fromString(username);
            return customerRepository.findById(id);
        } catch (IllegalArgumentException e) {
            // Not a UUID, find by document using indexed query
            return customerRepository.findByDocument(username);
        }
    }

    /**
     * Build MikroTik-specific RADIUS attributes for rate limiting.
     */
    private Map<String, String> buildMikrotikAttributes(String planName) {
        Map<String, String> attributes = new HashMap<>();
        
        int uploadMbps = getUploadMbps(planName);
        int downloadMbps = getDownloadMbps(planName);
        
        // MikroTik rate limit format: upload/download in bytes per second
        long uploadBps = uploadMbps * 1_000_000L / 8; // Convert Mbps to bytes/sec
        long downloadBps = downloadMbps * 1_000_000L / 8;
        
        attributes.put(rateLimitAttribute, String.format("%d/%d", uploadBps, downloadBps));
        attributes.put("Mikrotik-Queue-Name", planName);
        attributes.put("Reply-Message", "Welcome to Rainet ISP!");
        
        return attributes;
    }

    /**
     * Create a RADIUS response for blocked customers.
     * Returns minimal bandwidth (1 Kbps) to indicate blocked status.
     */
    private RadiusAuthRequest.RadiusAuthResponse createBlockedResponse() {
        Map<String, String> attributes = new HashMap<>();
        
        // Minimal bandwidth for blocked customers (125 bytes/sec = 1 Kbps)
        attributes.put(rateLimitAttribute, "125/125");
        attributes.put("Mikrotik-Queue-Name", "BLOCKED");
        attributes.put("Reply-Message", "Your account is blocked due to overdue payment. Please contact support.");
        
        return RadiusAuthRequest.RadiusAuthResponse.builder()
            .authenticated(true)  // Authenticated but with restricted access
            .profileName("BLOCKED")
            .uploadMbps(0)
            .downloadMbps(0)
            .attributes(attributes)
            .build();
    }

    /**
     * Get upload speed in Mbps based on plan name.
     * In a real system, this would query a Plan entity.
     */
    private int getUploadMbps(String planName) {
        // Simplified plan mapping
        return switch (planName.toUpperCase()) {
            case "BASIC" -> 5;
            case "STANDARD" -> 10;
            case "PREMIUM" -> 20;
            case "ENTERPRISE" -> 50;
            default -> 10; // Default 10 Mbps
        };
    }

    /**
     * Get download speed in Mbps based on plan name.
     * In a real system, this would query a Plan entity.
     */
    private int getDownloadMbps(String planName) {
        // Simplified plan mapping
        return switch (planName.toUpperCase()) {
            case "BASIC" -> 10;
            case "STANDARD" -> 20;
            case "PREMIUM" -> 50;
            case "ENTERPRISE" -> 100;
            default -> 20; // Default 20 Mbps
        };
    }
}
