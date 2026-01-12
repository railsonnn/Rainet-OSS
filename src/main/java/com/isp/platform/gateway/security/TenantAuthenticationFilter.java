package com.isp.platform.gateway.security;

import com.isp.platform.gateway.auth.UserAccount;
import com.isp.platform.gateway.auth.UserAccountRepository;
import com.isp.platform.gateway.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that sets tenant context based on authenticated user.
 * Runs after authentication to ensure tenant is set for the request.
 */
@Component
public class TenantAuthenticationFilter extends OncePerRequestFilter {

    private final UserAccountRepository userAccountRepository;

    public TenantAuthenticationFilter(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // If authenticated and tenant not yet set, resolve tenant from user
        if (authentication != null && authentication.isAuthenticated() 
                && TenantContext.getCurrentTenant() == null) {
            String username = authentication.getName();
            userAccountRepository.findByUsername(username)
                    .ifPresent(user -> TenantContext.setCurrentTenant(user.getTenantId()));
        }
        
        filterChain.doFilter(request, response);
    }
}
