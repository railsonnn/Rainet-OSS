package com.isp.platform.provisioning.domain;

import com.isp.platform.common.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "routers")
public class Router extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pop_id", nullable = false)
    private Pop pop;

    @Column(name = "hostname", nullable = false)
    private String hostname;

    @Column(name = "mgmt_address", nullable = false)
    private String managementAddress;

    @Column(name = "routeros_version", nullable = false)
    private String routerOsVersion;

    @Column(name = "api_username", nullable = false)
    private String apiUsername;

    @Column(name = "api_password", nullable = false)
    private String apiPassword;
}
