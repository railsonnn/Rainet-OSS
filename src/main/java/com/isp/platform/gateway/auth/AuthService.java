package com.isp.platform.gateway.auth;

import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.security.Role;
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
 * Authentication service - JWT support removed, now uses HTTP Basic Auth.
 * Token generation methods return placeholders for backward compatibility.
 * 
 * TODO: Re-implement token issuance if session-based or token-based authentication is needed.
 * Consider implementing:
 * - Session tokens stored in database
 * - OAuth2/OIDC integration
 * - API key authentication for programmatic access
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
        
        // Return placeholder tokens for backward compatibility
        // Authentication now uses HTTP Basic Auth
        return new AuthTokens("", "");
    }

    public AuthTokens refresh(RefreshRequest request) {
        // JWT refresh is no longer supported
        // Return placeholder tokens for backward compatibility
        throw new ApiException("Token refresh not supported - use HTTP Basic Authentication");
    }

    public void logout(String refreshToken) {
        // Stateless HTTP Basic Auth: no server-side session to invalidate
        // Client should discard credentials
    }
}
