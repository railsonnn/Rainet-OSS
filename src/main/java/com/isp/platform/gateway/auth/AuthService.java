package com.isp.platform.gateway.auth;

import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.tenant.Tenant;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.gateway.tenant.TenantRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserAccountRepository userRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AuthTokens login(LoginRequest request) {
        Tenant tenant = tenantRepository.findByCode(request.tenantCode())
                .filter(Tenant::isActive)
                .orElseThrow(() -> new ApiException("Tenant not found or inactive"));

        UUID tenantId = tenant.getId();
        UserAccount user = userRepository.findByUsernameAndTenantId(request.username(), tenantId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ApiException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException("Invalid credentials");
        }

        TenantContext.setCurrentTenant(tenantId);
        
        // TODO: Reimplement token generation if needed (e.g., JWT or session tokens)
        // Currently returning placeholder tokens for compatibility with existing clients
        // Migration to proper Basic Auth is recommended
        return new AuthTokens("", "");
    }

    public AuthTokens refresh(RefreshRequest request) {
        // TODO: Reimplement token refresh logic if needed
        // Currently JWT support has been removed
        throw new ApiException("Token refresh not supported - please use Basic Authentication");
    }

    public void logout(String refreshToken) {
        // TODO: Implement logout logic if session management is added
        // Currently stateless Basic Auth is used
    }
}
