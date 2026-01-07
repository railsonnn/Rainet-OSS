package com.isp.platform.provisioning.radius;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents a PPPoE session from RADIUS accounting.
 */
@Data
public class RadiusSession {
    private Long radacctid;
    private String acctsessionid;
    private String acctuniqueid;
    private String username;
    private String nasipaddress;
    private LocalDateTime acctstarttime;
    private LocalDateTime acctupdatetime;
    private LocalDateTime acctstoptime;
    private Integer acctsessiontime;
    private Long acctinputoctets;
    private Long acctoutputoctets;
    private String framedipaddress;
    private String acctterminatecause;
    private boolean active;

    public long getTotalBytes() {
        long input = acctinputoctets != null ? acctinputoctets : 0L;
        long output = acctoutputoctets != null ? acctoutputoctets : 0L;
        return input + output;
    }

    public String getFormattedTraffic() {
        long totalMB = getTotalBytes() / (1024 * 1024);
        return String.format("%.2f MB", (double) totalMB);
    }
}
