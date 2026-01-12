package com.isp.platform.gateway.auth;

import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.security.Role;
import com.isp.platform.gateway.security.TokenType;
import com.isp.platform.gateway.tenant.Tenant;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.gateway.tenant.TenantRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service.
 * 
 * TODO: This service previously used JWT tokens. With the migration to HTTP Basic Auth,
 * the login/refresh/logout endpoints need to be reimplemented or removed.
 * Consider implementing session-based authentication or a different token strategy
 * if stateless authentication is still required.
 */
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

    /**
     * Authenticate user credentials.
     * 
     * TODO: This method previously returned JWT tokens. With Basic Auth, this endpoint
     * may not be needed, or should be reimplemented to return a different response.
     * For now, it validates credentials and returns a simple success response.
     */
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
        // TODO: Return appropriate response for Basic Auth flow
        return new AuthTokens("", ""); // Placeholder - needs reimplementation
    }

    /**
     * Refresh authentication token.
     * 
     * TODO: This method is no longer applicable with Basic Auth.
     * Consider removing this endpoint or implementing alternative token refresh strategy.
     */
    public AuthTokens refresh(RefreshRequest request) {
        // TODO: Implement alternative refresh strategy or remove this method
        throw new ApiException("Token refresh not supported with Basic Auth. Please use HTTP Basic Authentication.");
    }

    /**
     * Logout user.
     * 
     * TODO: With Basic Auth, logout is handled by the client discarding credentials.
     * This method may not be needed or should be reimplemented for session invalidation.
     */
    public void logout(String refreshToken) {
        // TODO: Implement logout for Basic Auth or remove this method
        // With Basic Auth, logout is handled client-side
    }
}
