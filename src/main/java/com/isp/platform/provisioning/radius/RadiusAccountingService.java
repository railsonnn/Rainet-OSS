package com.isp.platform.provisioning.radius;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service to query RADIUS accounting sessions.
 * Provides visibility into active PPPoE connections and usage statistics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadiusAccountingService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Get all active PPPoE sessions.
     */
    public List<RadiusSession> getActiveSessions() {
        String sql = "SELECT * FROM radacct WHERE acctstoptime IS NULL ORDER BY acctstarttime DESC";
        return jdbcTemplate.query(sql, new RadiusSessionRowMapper());
    }

    /**
     * Get active sessions for a specific user.
     */
    public List<RadiusSession> getActiveSessionsForUser(String username) {
        String sql = "SELECT * FROM radacct WHERE username = ? AND acctstoptime IS NULL ORDER BY acctstarttime DESC";
        return jdbcTemplate.query(sql, new RadiusSessionRowMapper(), username);
    }

    /**
     * Get session history for a user.
     */
    public List<RadiusSession> getSessionHistoryForUser(String username, int limit) {
        String sql = "SELECT * FROM radacct WHERE username = ? ORDER BY acctstarttime DESC LIMIT ?";
        return jdbcTemplate.query(sql, new RadiusSessionRowMapper(), username, limit);
    }

    /**
     * Get total usage statistics for a user.
     */
    public UsageStats getUserUsageStats(String username) {
        String sql = "SELECT " +
                    "COUNT(*) as total_sessions, " +
                    "SUM(acctsessiontime) as total_time, " +
                    "SUM(acctinputoctets) as total_input, " +
                    "SUM(acctoutputoctets) as total_output " +
                    "FROM radacct WHERE username = ?";
        
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            UsageStats stats = new UsageStats();
            stats.setTotalSessions(rs.getInt("total_sessions"));
            stats.setTotalTimeSeconds(rs.getLong("total_time"));
            stats.setTotalInputBytes(rs.getLong("total_input"));
            stats.setTotalOutputBytes(rs.getLong("total_output"));
            return stats;
        }, username);
    }

    /**
     * Check if user has an active session.
     */
    public boolean hasActiveSession(String username) {
        String sql = "SELECT COUNT(*) FROM radacct WHERE username = ? AND acctstoptime IS NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    /**
     * Row mapper for RadiusSession.
     */
    private static class RadiusSessionRowMapper implements RowMapper<RadiusSession> {
        @Override
        public RadiusSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            RadiusSession session = new RadiusSession();
            session.setRadacctid(rs.getLong("radacctid"));
            session.setAcctsessionid(rs.getString("acctsessionid"));
            session.setAcctuniqueid(rs.getString("acctuniqueid"));
            session.setUsername(rs.getString("username"));
            session.setNasipaddress(rs.getString("nasipaddress"));
            
            Timestamp startTime = rs.getTimestamp("acctstarttime");
            if (startTime != null) {
                session.setAcctstarttime(startTime.toLocalDateTime());
            }
            
            Timestamp updateTime = rs.getTimestamp("acctupdatetime");
            if (updateTime != null) {
                session.setAcctupdatetime(updateTime.toLocalDateTime());
            }
            
            Timestamp stopTime = rs.getTimestamp("acctstoptime");
            if (stopTime != null) {
                session.setAcctstoptime(stopTime.toLocalDateTime());
            }
            
            session.setAcctsessiontime(rs.getInt("acctsessiontime"));
            session.setAcctinputoctets(rs.getLong("acctinputoctets"));
            session.setAcctoutputoctets(rs.getLong("acctoutputoctets"));
            session.setFramedipaddress(rs.getString("framedipaddress"));
            session.setAcctterminatecause(rs.getString("acctterminatecause"));
            session.setActive(stopTime == null);
            
            return session;
        }
    }

    /**
     * Usage statistics for a user.
     */
    @lombok.Data
    public static class UsageStats {
        private int totalSessions;
        private long totalTimeSeconds;
        private long totalInputBytes;
        private long totalOutputBytes;

        public long getTotalBytes() {
            return totalInputBytes + totalOutputBytes;
        }

        public String getFormattedTotalTraffic() {
            double totalGB = getTotalBytes() / (1024.0 * 1024.0 * 1024.0);
            return String.format("%.2f GB", totalGB);
        }

        public String getFormattedTotalTime() {
            long hours = totalTimeSeconds / 3600;
            long minutes = (totalTimeSeconds % 3600) / 60;
            return String.format("%d hours, %d minutes", hours, minutes);
        }
    }
}
