package com.isp.platform.admin.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * Utility for accessing tenant context and user information.
 * Ensures multi-tenancy enforcement at every layer.
 */
@Component
public class TenantContext {

    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String USER_ID_HEADER = "X-User-ID";

    /**
     * Get current tenant ID from security context.
     */
    public UUID getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) auth.getPrincipal()).getTenantId();
        }
        throw new RuntimeException("Tenant ID not available in context");
    }

    /**
     * Get current user ID from security context.
     */
    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) auth.getPrincipal()).getId();
        }
        throw new RuntimeException("User ID not available in context");
    }

    /**
     * Get current user's roles.
     */
    public Collection<? extends GrantedAuthority> getCurrentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getAuthorities() : null;
    }

    /**
     * Check if current user has specific role.
     */
    public boolean hasRole(SystemRole role) {
        Collection<? extends GrantedAuthority> authorities = getCurrentRoles();
        if (authorities == null) return false;
        return authorities.stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
    }

    /**
     * Enforce tenant isolation - verify resource belongs to current tenant.
     */
    public void checkTenantAccess(UUID resourceTenantId) {
        UUID currentTenant = getCurrentTenantId();
        if (!currentTenant.equals(resourceTenantId)) {
            throw new SecurityException("Tenant access denied: " + resourceTenantId);
        }
    }
}
