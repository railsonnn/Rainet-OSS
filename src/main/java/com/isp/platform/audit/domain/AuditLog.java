package com.isp.platform.audit.domain;

import com.isp.platform.common.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_tenant_action", columnList = "tenant_id,action"),
    @Index(name = "idx_actor_created", columnList = "actor,created_at"),
    @Index(name = "idx_resource_type", columnList = "resource_type,resource_id")
})
public class AuditLog extends BaseTenantEntity {

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "action", nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    /**
     * Audit action types - all critical operations must be logged.
     */
    public enum AuditAction {
        // Provisioning
        PROVISIONING_APPLY("Apply provisioning configuration"),
        PROVISIONING_ROLLBACK("Rollback provisioning"),
        PROVISIONING_SNAPSHOT_CREATE("Create configuration snapshot"),

        // Router Operations
        ROUTER_CREATE("Create router"),
        ROUTER_UPDATE("Update router"),
        ROUTER_DELETE("Delete router"),
        ROUTER_CONNECTION_TEST("Test router connection"),

        // Billing
        BILLING_INVOICE_CREATE("Create invoice"),
        BILLING_INVOICE_PAID("Mark invoice as paid"),
        BILLING_INVOICE_CANCEL("Cancel invoice"),
        BILLING_REFUND("Process refund"),
        BILLING_PIX_WEBHOOK("Received PIX payment webhook"),

        // Customer Management
        CUSTOMER_CREATE("Create customer"),
        CUSTOMER_UPDATE("Update customer"),
        CUSTOMER_DELETE("Delete customer"),
        CUSTOMER_BLOCK("Block customer"),
        CUSTOMER_UNBLOCK("Unblock customer"),

        // Authentication
        AUTH_LOGIN("User login"),
        AUTH_LOGOUT("User logout"),
        AUTH_PASSWORD_CHANGE("Password changed"),

        // System
        SYSTEM_CONFIGURATION_CHANGE("System configuration changed"),
        SYSTEM_BACKUP("System backup"),
        SYSTEM_RESTORE("System restore");

        private final String description;

        AuditAction(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Audit operation status.
     */
    public enum AuditStatus {
        SUCCESS("Operation succeeded"),
        FAILURE("Operation failed"),
        PARTIAL("Operation partially succeeded");

        private final String description;

        AuditStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
