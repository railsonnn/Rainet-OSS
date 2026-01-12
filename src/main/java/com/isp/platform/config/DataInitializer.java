package com.isp.platform.config;

import com.isp.platform.gateway.auth.UserAccount;
import com.isp.platform.gateway.auth.UserAccountRepository;
import com.isp.platform.gateway.security.Role;
import com.isp.platform.gateway.tenant.Tenant;
import com.isp.platform.gateway.tenant.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Data initializer that creates example tenant and admin user on first startup.
 * Runs only if database has no users - useful for Docker testing.
 */
@Slf4j
@Component
public class DataInitializer implements ApplicationRunner {

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
    public void run(ApplicationArguments args) {
        // Only initialize if no users exist
        if (userAccountRepository.count() > 0) {
            log.info("Database already initialized with users, skipping data initialization");
            return;
        }

        log.info("Initializing database with example tenant and admin user...");

        // Create example tenant
        Tenant tenant = new Tenant();
        tenant.setCode("example");
        tenant.setName("Example ISP");
        tenant.setActive(true);
        tenant = tenantRepository.save(tenant);
        log.info("Created example tenant: {} (id: {})", tenant.getName(), tenant.getId());

        // Create admin user
        UserAccount admin = new UserAccount();
        admin.setTenantId(tenant.getId());
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("change-me"));
        admin.setEnabled(true);
        admin.setRoles(Set.of(Role.ADMIN));
        userAccountRepository.save(admin);
        
        log.info("Created admin user: {}", admin.getUsername());
        log.warn("IMPORTANT: Default admin user created - change the password immediately!");
        log.info("Default admin credentials are documented in SETUP_AND_DEPLOYMENT_GUIDE.md");
    }
}
