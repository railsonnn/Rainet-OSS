package com.isp.platform.provisioning.radius;

import com.isp.platform.billing.domain.Customer;
import com.isp.platform.billing.domain.Plan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to manage FreeRADIUS user entries in radcheck and radreply tables.
 * Synchronizes customer data with RADIUS authentication database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadiusUserService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates or updates RADIUS user for PPPoE authentication.
     * Inserts entries into radcheck (password) and radreply (rate limits).
     */
    @Transactional
    public void createOrUpdateRadiusUser(Customer customer, String plainPassword) {
        if (customer.getEmail() == null || customer.getPlan() == null) {
            log.warn("Cannot create RADIUS user: email or plan is null for customer {}", customer.getId());
            return;
        }

        String username = customer.getEmail();
        
        // Remove existing entries
        removeRadiusUser(username);

        // Insert password into radcheck (Cleartext-Password for simplicity)
        // In production, use Crypt-Password or CHAP
        jdbcTemplate.update(
            "INSERT INTO radcheck (username, attribute, op, value, created_at, updated_at) " +
            "VALUES (?, 'Cleartext-Password', ':=', ?, NOW(), NOW())",
            username, plainPassword
        );

        log.info("Created radcheck entry for user: {}", username);

        // Insert rate limit into radreply if plan exists
        if (customer.getPlan() != null) {
            Plan plan = customer.getPlan();
            
            // MikroTik rate limit format: upload/download in bits per second
            long uploadBps = plan.getUploadMbps() * 1_000_000L;
            long downloadBps = plan.getDownloadMbps() * 1_000_000L;
            String rateLimit = String.format("%d/%d", uploadBps, downloadBps);

            jdbcTemplate.update(
                "INSERT INTO radreply (username, attribute, op, value, created_at, updated_at) " +
                "VALUES (?, 'Mikrotik-Rate-Limit', ':=', ?, NOW(), NOW())",
                username, rateLimit
            );

            log.info("Created radreply entry for user: {} with rate limit: {}", username, rateLimit);
        }
    }

    /**
     * Removes RADIUS user entries from radcheck and radreply.
     */
    @Transactional
    public void removeRadiusUser(String username) {
        int deletedCheck = jdbcTemplate.update("DELETE FROM radcheck WHERE username = ?", username);
        int deletedReply = jdbcTemplate.update("DELETE FROM radreply WHERE username = ?", username);
        
        if (deletedCheck > 0 || deletedReply > 0) {
            log.info("Removed RADIUS entries for user: {} (radcheck: {}, radreply: {})", 
                username, deletedCheck, deletedReply);
        }
    }

    /**
     * Updates rate limit for existing RADIUS user based on plan change.
     */
    @Transactional
    public void updateRateLimit(Customer customer) {
        if (customer.getEmail() == null || customer.getPlan() == null) {
            log.warn("Cannot update rate limit: email or plan is null for customer {}", customer.getId());
            return;
        }

        String username = customer.getEmail();
        Plan plan = customer.getPlan();

        // Delete existing rate limit
        jdbcTemplate.update("DELETE FROM radreply WHERE username = ? AND attribute = 'Mikrotik-Rate-Limit'", 
            username);

        // Insert new rate limit
        long uploadBps = plan.getUploadMbps() * 1_000_000L;
        long downloadBps = plan.getDownloadMbps() * 1_000_000L;
        String rateLimit = String.format("%d/%d", uploadBps, downloadBps);

        jdbcTemplate.update(
            "INSERT INTO radreply (username, attribute, op, value, created_at, updated_at) " +
            "VALUES (?, 'Mikrotik-Rate-Limit', ':=', ?, NOW(), NOW())",
            username, rateLimit
        );

        log.info("Updated rate limit for user: {} to {}", username, rateLimit);
    }

    /**
     * Blocks a customer by setting minimal rate limit.
     */
    @Transactional
    public void blockCustomer(String username) {
        // Delete existing rate limit
        jdbcTemplate.update("DELETE FROM radreply WHERE username = ? AND attribute = 'Mikrotik-Rate-Limit'", 
            username);

        // Insert minimal rate limit (1kbps)
        jdbcTemplate.update(
            "INSERT INTO radreply (username, attribute, op, value, created_at, updated_at) " +
            "VALUES (?, 'Mikrotik-Rate-Limit', ':=', '1000/1000', NOW(), NOW())",
            username
        );

        log.info("Blocked customer: {} with minimal rate limit", username);
    }

    /**
     * Unblocks a customer by restoring normal rate limit.
     */
    @Transactional
    public void unblockCustomer(Customer customer) {
        updateRateLimit(customer);
        log.info("Unblocked customer: {}", customer.getEmail());
    }
}
