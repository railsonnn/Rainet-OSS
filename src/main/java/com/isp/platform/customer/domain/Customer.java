package com.isp.platform.customer.domain;

import com.isp.platform.common.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer extends BaseTenantEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "document", nullable = false)
    private String document;

    @Column(name = "plan", nullable = false)
    private String plan;

    @Column(name = "status", nullable = false)
    private String status;
}
