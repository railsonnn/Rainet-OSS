package com.isp.platform.config;

import com.isp.platform.gateway.auth.UserAccount;
import com.isp.platform.gateway.auth.UserAccountRepository;
import com.isp.platform.gateway.security.Role;
import com.isp.platform.gateway.tenant.Tenant;
import com.isp.platform.gateway.tenant.TenantRepository;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data initializer that creates a default admin user if the database is empty.
 * This ensures the application can be accessed after initial startup.
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            UserAccountRepository userAccountRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Check if any users exist
        long userCount = userAccountRepository.count();
        if (userCount > 0) {
            log.info("Users already exist in database, skipping initialization");
            return;
        }

        log.info("No users found in database, creating default admin user");

        // Create default tenant if none exists
        Tenant tenant;
        if (tenantRepository.count() == 0) {
            tenant = new Tenant();
            tenant.setCode("default");
            tenant.setName("Default Tenant");
            tenant.setActive(true);
            tenant = tenantRepository.save(tenant);
            log.info("Created default tenant: {}", tenant.getCode());
        } else {
            tenant = tenantRepository.findAll().get(0);
            log.info("Using existing tenant: {}", tenant.getCode());
        }

        // Create admin user
        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setEmail("admin@rainet.local");
        admin.setPasswordHash(passwordEncoder.encode("change-me"));
        admin.setEnabled(true);
        admin.setRoles(Set.of(Role.ADMIN));
        admin.setTenantId(tenant.getId());
        
        userAccountRepository.save(admin);
        
        log.info("=".repeat(80));
        log.info("DEFAULT ADMIN USER CREATED");
        log.info("Username: admin");
        log.info("Password: change-me");
        log.info("Tenant: {}", tenant.getCode());
        log.info("PLEASE CHANGE THE DEFAULT PASSWORD IMMEDIATELY!");
        log.info("=".repeat(80));
    }
}
