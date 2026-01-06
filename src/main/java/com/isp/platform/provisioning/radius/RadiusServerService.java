package com.isp.platform.provisioning.radius;

import com.isp.platform.billing.domain.Customer;
import com.isp.platform.billing.domain.Plan;
import com.isp.platform.billing.repository.CustomerRepository;
import com.isp.platform.billing.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RADIUS server integration for PPPoE authentication.
 * Authenticates customers via RADIUS and returns MikroTik-specific rate-limit attributes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadiusServerService {

    private final CustomerRepository customerRepository;
    private final PlanRepository planRepository;

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
            // Find customer by username (email or account ID)
            Optional<Customer> customerOpt = customerRepository.findByEmail(request.getUsername());

            if (customerOpt.isEmpty()) {
                log.warn("Customer not found: {}", request.getUsername());
                return RadiusAuthRequest.RadiusAuthResponse.builder()
                    .authenticated(false)
                    .errorMessage("Customer not found")
                    .build();
            }

            Customer customer = customerOpt.get();

            // Check if customer is active and paid
            if (!customer.isActive() || customer.isBlocked()) {
                log.warn("Customer {} is blocked or inactive", request.getUsername());
                return createBlockedResponse(customer);
            }

            // Get customer's plan
            if (customer.getPlan() == null) {
                log.warn("Customer {} has no plan assigned", request.getUsername());
                return RadiusAuthRequest.RadiusAuthResponse.builder()
                    .authenticated(false)
                    .errorMessage("No plan assigned")
                    .build();
            }

            Plan plan = customer.getPlan();

            // Verify password (in real scenario, use bcrypt)
            if (!verifyPassword(request.getPassword(), customer.getPasswordHash())) {
                log.warn("Invalid password for customer: {}", request.getUsername());
                return RadiusAuthRequest.RadiusAuthResponse.builder()
                    .authenticated(false)
                    .errorMessage("Invalid password")
                    .build();
            }

            // Build successful response with plan attributes
            Map<String, String> attributes = buildMikrotikAttributes(plan);

            RadiusAuthRequest.RadiusAuthResponse response = RadiusAuthRequest.RadiusAuthResponse.builder()
                .authenticated(true)
                .profileName(plan.getName())
                .uploadMbps(plan.getUploadMbps())
                .downloadMbps(plan.getDownloadMbps())
                .attributes(attributes)
                .build();

            log.info("RADIUS authentication successful for customer: {} with plan: {}", 
                request.getUsername(), plan.getName());

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
     * Build MikroTik-specific RADIUS attributes for rate limiting.
     */
    private Map<String, String> buildMikrotikAttributes(Plan plan) {
        Map<String, String> attributes = new HashMap<>();
        
        // MikroTik rate limit format: upload/download in bytes per second
        long uploadBps = plan.getUploadMbps() * 1_000_000L / 8; // Convert Mbps to bytes/sec
        long downloadBps = plan.getDownloadMbps() * 1_000_000L / 8;
        
        attributes.put(rateLimitAttribute, String.format("%d/%d", uploadBps, downloadBps));
        attributes.put("Mikrotik-Queue-Name", plan.getName());
        attributes.put("Reply-Message", "Welcome to Rainet ISP!");
        
        return attributes;
    }

    /**
     * Create a RADIUS response for blocked customers.
     * Returns minimal bandwidth to prevent access.
     */
    private RadiusAuthRequest.RadiusAuthResponse createBlockedResponse(Customer customer) {
        Map<String, String> attributes = new HashMap<>();
        
        // Minimal bandwidth for blocked customers (1 Kbps)
        attributes.put(rateLimitAttribute, "1/1");
        attributes.put("Reply-Message", "Your account is blocked. Please contact support.");
        
        return RadiusAuthRequest.RadiusAuthResponse.builder()
            .authenticated(true)
            .profileName("BLOCKED")
            .uploadMbps(0)
            .downloadMbps(0)
            .attributes(attributes)
            .build();
    }

    /**
     * Simple password verification (should use bcrypt in production).
     */
    private boolean verifyPassword(String plainPassword, String hashedPassword) {
        // TODO: Implement bcrypt verification
        // return BCrypt.checkpw(plainPassword, hashedPassword);
        return true; // Placeholder
    }
}
