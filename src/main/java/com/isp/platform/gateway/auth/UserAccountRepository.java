package com.isp.platform.gateway.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUsernameAndTenantId(String username, UUID tenantId);
    boolean existsByUsernameAndTenantId(String username, UUID tenantId);
}
