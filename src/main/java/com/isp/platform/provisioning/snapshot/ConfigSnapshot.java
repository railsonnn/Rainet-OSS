package com.isp.platform.provisioning.snapshot;

import com.isp.platform.common.domain.BaseTenantEntity;
import com.isp.platform.provisioning.domain.Router;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "config_snapshots")
public class ConfigSnapshot extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "router_id", nullable = false)
    private Router router;

    @Column(name = "snapshot_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SnapshotType snapshotType;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "config_script", nullable = false, columnDefinition = "TEXT")
    private String configScript;

    @Column(name = "config_hash", nullable = false, length = 64)
    private String configHash;

    @Column(name = "applied_by", nullable = false)
    private String appliedBy;

    public enum SnapshotType {
        BEFORE("Before applying configuration"),
        AFTER("After applying configuration");

        private final String description;

        SnapshotType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
