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
     * is no longer applicable and should be removed or reimplemented.
     * 
     * @deprecated This method is not supported with Basic Authentication.
     * Use HTTP Basic Auth instead of calling this endpoint.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public AuthTokens login(LoginRequest request) {
        // With Basic Auth, login endpoints are not needed
        // Authentication happens via HTTP Basic Auth header on each request
        throw new ApiException("Login endpoint not supported with Basic Auth. Please use HTTP Basic Authentication header.");
    }

    /**
     * Refresh authentication token.
     * 
     * TODO: This method is no longer applicable with Basic Auth.
     * Consider removing this endpoint or implementing alternative token refresh strategy.
     * 
     * @deprecated This method is not supported with Basic Authentication.
     * Use HTTP Basic Auth instead of token refresh.
     */
    @Deprecated
    public AuthTokens refresh(RefreshRequest request) {
        // With Basic Auth, refresh endpoints are not needed
        // Authentication happens via HTTP Basic Auth header on each request
        throw new ApiException("Token refresh not supported with Basic Auth. Please use HTTP Basic Authentication header.");
    }

    /**
     * Logout user.
     * 
     * TODO: With Basic Auth, logout is handled by the client discarding credentials.
     * This method may not be needed or should be reimplemented for session invalidation.
     * 
     * @deprecated This method is not applicable with Basic Authentication.
     * Logout is handled client-side by discarding credentials.
     */
    @Deprecated
    public void logout(String refreshToken) {
        // With Basic Auth, logout is handled client-side
        // No server-side action needed
        throw new ApiException("Logout not supported with Basic Auth. Client should discard credentials.");
    }
}
