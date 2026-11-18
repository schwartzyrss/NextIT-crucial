package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.SecurityAuditLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcSecurityAuditLogRepository implements SecurityAuditLogRepository {

    @Override
    public List<SecurityAuditLog> findAll(int limit) {
        String sql = "SELECT * FROM security_audit_logs ORDER BY log_id DESC LIMIT ?";
        List<SecurityAuditLog> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new AppException("Failed to list security audit logs", e);
        }
    }

    @Override
    public List<SecurityAuditLog> findByUserId(int userId) {
        String sql = "SELECT * FROM security_audit_logs WHERE user_id = ? OR target_user_id = ? ORDER BY log_id DESC";
        List<SecurityAuditLog> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new AppException("Failed to find security audit logs for user", e);
        }
    }

    @Override
    public void save(SecurityAuditLog log) {
        String sql = "INSERT INTO security_audit_logs (user_id, actor_username, action, target_user_id, ip_address, details, severity) VALUES (?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (log.getUserId() != null) ps.setInt(1, log.getUserId()); else ps.setNull(1, Types.INTEGER);
            ps.setString(2, log.getActorUsername() != null ? log.getActorUsername() : "SYSTEM");
            ps.setString(3, log.getAction());
            if (log.getTargetUserId() != null) ps.setInt(4, log.getTargetUserId()); else ps.setNull(4, Types.INTEGER);
            ps.setString(5, log.getIpAddress());
            ps.setString(6, log.getDetails());
            ps.setString(7, log.getSeverity() != null ? log.getSeverity() : "INFO");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) log.setLogId(keys.getInt(1));
            }
        } catch (SQLException e) {
            throw new AppException("Failed to save security audit log: " + e.getMessage(), e);
        }
    }

    private SecurityAuditLog map(ResultSet rs) throws SQLException {
        SecurityAuditLog log = new SecurityAuditLog();
        log.setLogId(rs.getInt("log_id"));
        int uid = rs.getInt("user_id");
        if (!rs.wasNull()) log.setUserId(uid);
        log.setActorUsername(rs.getString("actor_username"));
        log.setAction(rs.getString("action"));
        int targetUid = rs.getInt("target_user_id");
        if (!rs.wasNull()) log.setTargetUserId(targetUid);
        log.setIpAddress(rs.getString("ip_address"));
        log.setDetails(rs.getString("details"));
        log.setSeverity(rs.getString("severity"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) log.setCreatedAt(created.toLocalDateTime());
        return log;
    }
}
