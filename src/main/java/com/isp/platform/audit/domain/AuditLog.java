package com.isp.platform.audit.domain;

import com.isp.platform.common.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseTenantEntity {

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "resource", nullable = false)
    private String resource;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;
}
