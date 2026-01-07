package com.isp.platform.provisioning.controller;

import com.isp.platform.billing.domain.Customer;
import com.isp.platform.billing.repository.CustomerRepository;
import com.isp.platform.provisioning.radius.RadiusAccountingService;
import com.isp.platform.provisioning.radius.RadiusSession;
import com.isp.platform.provisioning.radius.RadiusUserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for managing RADIUS users and viewing PPPoE sessions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/radius")
@RequiredArgsConstructor
public class RadiusController {

    private final RadiusUserService radiusUserService;
    private final RadiusAccountingService radiusAccountingService;
    private final CustomerRepository customerRepository;

    /**
     * Create or update RADIUS user for PPPoE authentication.
     */
    @PostMapping("/users")
    public ResponseEntity<String> createRadiusUser(@RequestBody CreateRadiusUserRequest request) {
        log.info("Creating RADIUS user for customer: {}", request.getCustomerId());

        Customer customer = customerRepository.findById(request.getCustomerId())
            .orElseThrow(() -> new RuntimeException("Customer not found"));

        radiusUserService.createOrUpdateRadiusUser(customer, request.getPassword());

        return ResponseEntity.ok("RADIUS user created successfully");
    }

    /**
     * Remove RADIUS user.
     */
    @DeleteMapping("/users/{username}")
    public ResponseEntity<String> removeRadiusUser(@PathVariable String username) {
        log.info("Removing RADIUS user: {}", username);
        radiusUserService.removeRadiusUser(username);
        return ResponseEntity.ok("RADIUS user removed successfully");
    }

    /**
     * Update rate limit for a customer.
     */
    @PutMapping("/users/{customerId}/rate-limit")
    public ResponseEntity<String> updateRateLimit(@PathVariable UUID customerId) {
        log.info("Updating rate limit for customer: {}", customerId);

        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new RuntimeException("Customer not found"));

        radiusUserService.updateRateLimit(customer);

        return ResponseEntity.ok("Rate limit updated successfully");
    }

    /**
     * Block a customer.
     */
    @PostMapping("/users/{customerId}/block")
    public ResponseEntity<String> blockCustomer(@PathVariable UUID customerId) {
        log.info("Blocking customer: {}", customerId);

        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new RuntimeException("Customer not found"));

        customer.setBlocked(true);
        customerRepository.save(customer);

        radiusUserService.blockCustomer(customer.getEmail());

        return ResponseEntity.ok("Customer blocked successfully");
    }

    /**
     * Unblock a customer.
     */
    @PostMapping("/users/{customerId}/unblock")
    public ResponseEntity<String> unblockCustomer(@PathVariable UUID customerId) {
        log.info("Unblocking customer: {}", customerId);

        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new RuntimeException("Customer not found"));

        customer.setBlocked(false);
        customerRepository.save(customer);

        radiusUserService.unblockCustomer(customer);

        return ResponseEntity.ok("Customer unblocked successfully");
    }

    /**
     * Get all active PPPoE sessions.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<RadiusSession>> getActiveSessions() {
        log.info("Fetching all active PPPoE sessions");
        List<RadiusSession> sessions = radiusAccountingService.getActiveSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get active sessions for a specific user.
     */
    @GetMapping("/sessions/user/{username}")
    public ResponseEntity<List<RadiusSession>> getActiveSessionsForUser(@PathVariable String username) {
        log.info("Fetching active sessions for user: {}", username);
        List<RadiusSession> sessions = radiusAccountingService.getActiveSessionsForUser(username);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get session history for a user.
     */
    @GetMapping("/sessions/user/{username}/history")
    public ResponseEntity<List<RadiusSession>> getSessionHistory(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Fetching session history for user: {} with limit: {}", username, limit);
        List<RadiusSession> sessions = radiusAccountingService.getSessionHistoryForUser(username, limit);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get usage statistics for a user.
     */
    @GetMapping("/sessions/user/{username}/stats")
    public ResponseEntity<RadiusAccountingService.UsageStats> getUserStats(@PathVariable String username) {
        log.info("Fetching usage stats for user: {}", username);
        RadiusAccountingService.UsageStats stats = radiusAccountingService.getUserUsageStats(username);
        return ResponseEntity.ok(stats);
    }

    @Data
    public static class CreateRadiusUserRequest {
        private UUID customerId;
        private String password;
    }
}
