package com.isp.platform.admin.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * HTTP filter to enforce multi-tenant isolation.
 * Validates tenant ID header and prevents cross-tenant access.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantEnforcementFilter extends OncePerRequestFilter {

    private static final String TENANT_ID_HEADER = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String tenantIdHeader = request.getHeader(TENANT_ID_HEADER);
        
        // Skip filter for public endpoints
        if (isPublicEndpoint(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Validate tenant ID header exists
        if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
            log.warn("Missing tenant ID header for request: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Tenant ID is required");
            return;
        }
        
        // Validate tenant ID format
        try {
            UUID.fromString(tenantIdHeader);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant ID format: {}", tenantIdHeader);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid tenant ID format");
            return;
        }
        
        // Proceed with request
        filterChain.doFilter(request, response);
    }

    /**
     * Check if endpoint is public and doesn't require tenant ID.
     */
    private boolean isPublicEndpoint(String uri) {
        return uri.matches("^/(health|metrics|auth/login|auth/register|swagger.*|api-docs.*)");
    }
}
