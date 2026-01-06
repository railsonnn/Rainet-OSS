package com.isp.platform.gateway.auth;

import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.security.JwtTokenProvider;
import com.isp.platform.gateway.security.Role;
import com.isp.platform.gateway.security.TokenType;
import com.isp.platform.gateway.tenant.Tenant;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.gateway.tenant.TenantRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserAccountRepository userRepository,
            TenantRepository tenantRepository,
            JwtTokenProvider tokenProvider,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tokenProvider = tokenProvider;
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
        return new AuthTokens(
                tokenProvider.generateAccessToken(user),
                tokenProvider.generateRefreshToken(user));
    }

    public AuthTokens refresh(RefreshRequest request) {
        String token = request.refreshToken();
        if (!tokenProvider.validate(token) || !tokenProvider.isRefreshToken(token)) {
            throw new ApiException("Invalid refresh token");
        }
        Claims claims = tokenProvider.getClaims(token);
        String username = claims.getSubject();
        UUID tenantId = UUID.fromString((String) claims.get("tenant_id"));
        TenantContext.setCurrentTenant(tenantId);
        UserAccount user = userRepository.findByUsernameAndTenantId(username, tenantId)
                .orElseThrow(() -> new ApiException("User not found"));
        return new AuthTokens(
                tokenProvider.generateAccessToken(user),
                tokenProvider.generateRefreshToken(user));
    }

    public void logout(String refreshToken) {
        // Stateless JWT: client discards tokens; implement blacklist/rotation later.
        if (!tokenProvider.validate(refreshToken)) {
            throw new ApiException("Invalid token");
        }
    }
}
