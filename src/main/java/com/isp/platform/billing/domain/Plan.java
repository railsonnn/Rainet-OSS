package com.isp.platform.billing.domain;

import com.isp.platform.common.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Internet service plan with bandwidth and pricing.
 */
@Getter
@Setter
@Entity
@Table(name = "plans")
public class Plan extends BaseTenantEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "download_mbps", nullable = false)
    private Integer downloadMbps;

    @Column(name = "upload_mbps", nullable = false)
    private Integer uploadMbps;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
