package com.isp.platform.admin.security;

/**
 * System-wide roles for RBAC.
 * Controls access to provisioning, billing, support, and customer operations.
 */
public enum SystemRole {
    
    ADMIN("Administrator", "Full system access"),
    TECH("Technician", "Provisioning and router management"),
    FINANCE("Finance Officer", "Billing and invoice management"),
    SUPPORT("Support Agent", "Customer support and billing inquiries"),
    CUSTOMER("Customer", "Own account and service management");

    private final String displayName;
    private final String description;

    SystemRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if role has permission for action.
     */
    public boolean hasPermission(String action) {
        return switch (this) {
            case ADMIN -> true; // Admin can do everything
            case TECH -> action.startsWith("provisioning") || action.startsWith("router");
            case FINANCE -> action.startsWith("billing") || action.startsWith("invoice");
            case SUPPORT -> action.startsWith("customer") || action.startsWith("support");
            case CUSTOMER -> action.startsWith("self-service");
        };
    }
}
