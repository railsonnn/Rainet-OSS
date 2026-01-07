package com.isp.platform.billing.domain;

import com.isp.platform.common.domain.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Customer entity for billing and PPPoE authentication.
 */
@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer extends BaseTenantEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "document", nullable = false)
    private String document;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "blocked", nullable = false)
    private Boolean blocked = false;

    public boolean isActive() {
        return active != null && active;
    }

    public boolean isBlocked() {
        return blocked != null && blocked;
    }
}
