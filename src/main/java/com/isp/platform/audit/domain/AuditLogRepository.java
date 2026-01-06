package com.isp.platform.audit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    List<AuditLog> findByTenantId(UUID tenantId);
    
    List<AuditLog> findByActor(String actor);
    
    List<AuditLog> findByResourceTypeAndResourceId(String resourceType, String resourceId);
    
    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditLog.AuditAction action);
    
    @Query("SELECT al FROM AuditLog al WHERE al.tenantId = :tenantId AND al.createdAt BETWEEN :startDate AND :endDate ORDER BY al.createdAt DESC")
    List<AuditLog> findAuditsByTenantAndDateRange(
        @Param("tenantId") UUID tenantId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT al FROM AuditLog al WHERE al.tenantId = :tenantId AND al.action = :action ORDER BY al.createdAt DESC")
    List<AuditLog> findAuditsByTenantAndAction(
        @Param("tenantId") UUID tenantId,
        @Param("action") AuditLog.AuditAction action);
}
