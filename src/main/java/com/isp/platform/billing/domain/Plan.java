package com.isp.platform.billing.domain;

import com.isp.platform.common.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Internet service plan with bandwidth specifications.
 * Used for PPPoE authentication and rate limiting.
 */
@Getter
@Setter
@Entity
@Table(name = "plans")
public class Plan extends BaseTenantEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "upload_mbps", nullable = false)
    private Integer uploadMbps;

    @Column(name = "download_mbps", nullable = false)
    private Integer downloadMbps;

    @Column(name = "monthly_price", nullable = false)
    private java.math.BigDecimal monthlyPrice;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "description")
    private String description;
}
