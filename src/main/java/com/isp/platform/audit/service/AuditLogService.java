package com.isp.platform.audit.service;

import com.isp.platform.admin.security.TenantContext;
import com.isp.platform.audit.domain.AuditLog;
import com.isp.platform.audit.domain.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Immutable audit logging service.
 * Records all critical operations for compliance and security auditing.
 * All audit logs are append-only and cannot be modified.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final TenantContext tenantContext;
    private final ObjectMapper objectMapper;

    /**
     * Log a provisioning operation.
     */
    public void logProvisioning(String actor, AuditLog.AuditAction action, 
                               String routerId, Object details, AuditLog.AuditStatus status) {
        logAudit(actor, action, "Router", routerId, details, status, null);
    }

    /**
     * Log a billing operation.
     */
    public void logBilling(String actor, AuditLog.AuditAction action,
                          String invoiceId, Object details, AuditLog.AuditStatus status) {
        logAudit(actor, action, "Invoice", invoiceId, details, status, null);
    }

    /**
     * Log a customer operation.
     */
    public void logCustomer(String actor, AuditLog.AuditAction action,
                           String customerId, Object details, AuditLog.AuditStatus status) {
        logAudit(actor, action, "Customer", customerId, details, status, null);
    }

    /**
     * Log a rollback operation.
     */
    public void logRollback(String actor, String routerId, String snapshotId, 
                           AuditLog.AuditStatus status) {
        Object details = String.format("Rollback from snapshot: %s", snapshotId);
        logAudit(actor, AuditLog.AuditAction.PROVISIONING_ROLLBACK, "Router", 
                routerId, details, status, null);
    }

    /**
     * Log a PIX payment webhook event.
     */
    public void logPixWebhook(String invoiceId, String webhookData, AuditLog.AuditStatus status) {
        logAudit("PIX_GATEWAY", AuditLog.AuditAction.BILLING_PIX_WEBHOOK, "Invoice", 
                invoiceId, webhookData, status, null);
    }

    /**
     * Log an authentication event.
     */
    public void logAuthentication(String actor, AuditLog.AuditAction action,
                                 AuditLog.AuditStatus status, String errorMessage) {
        logAudit(actor, action, "User", actor, "User authentication", status, errorMessage);
    }

    /**
     * Generic audit logging method.
     * All parameters are recorded immutably.
     */
    private void logAudit(String actor, AuditLog.AuditAction action, String resourceType,
                         String resourceId, Object payload, AuditLog.AuditStatus status,
                         String errorMessage) {
        try {
            AuditLog log = new AuditLog();
            log.setTenantId(tenantContext.getCurrentTenantId());
            log.setActor(actor);
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setStatus(status);
            log.setErrorMessage(errorMessage);
            
            // Serialize payload to JSON
            if (payload != null) {
                log.setPayload(objectMapper.writeValueAsString(payload));
            }
            
            // Capture request context
            HttpServletRequest request = getHttpRequest();
            if (request != null) {
                log.setIpAddress(getClientIp(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }
            
            // Save to database
            auditLogRepository.save(log);
            
            log.info("Audit log recorded: actor={}, action={}, resource={}/{}, status={}",
                actor, action.name(), resourceType, resourceId, status.name());
        } catch (Exception e) {
            log.error("Failed to record audit log", e);
            // Don't fail the operation, but log the error
        }
    }

    /**
     * Query audit logs for a specific tenant and action.
     */
    public List<AuditLog> getAuditsByTenantAndAction(UUID tenantId, AuditLog.AuditAction action) {
        return auditLogRepository.findAuditsByTenantAndAction(tenantId, action);
    }

    /**
     * Query audit logs by date range.
     */
    public List<AuditLog> getAuditsByDateRange(UUID tenantId, LocalDateTime startDate, 
                                              LocalDateTime endDate) {
        return auditLogRepository.findAuditsByTenantAndDateRange(tenantId, startDate, endDate);
    }

    /**
     * Query audit logs for a specific resource.
     */
    public List<AuditLog> getAuditsByResource(String resourceType, String resourceId) {
        return auditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId);
    }

    /**
     * Get HTTP request from context.
     */
    private HttpServletRequest getHttpRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest();
            }
        } catch (Exception e) {
            // Request context not available
        }
        return null;
    }

    /**
     * Extract client IP address from request.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isEmpty()) {
            return xForwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
