package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.PasswordResetRequest;
import com.nextit.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class JdbcPasswordResetRequestRepository implements PasswordResetRequestRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcPasswordResetRequestRepository.class);

    private static final String BASE_SELECT = """
        SELECT prr.*, u.username, u.email AS user_email, u.first_name, u.last_name
        FROM password_reset_requests prr
        LEFT JOIN users u ON prr.user_id = u.user_id
    """;

    @Override
    public void save(PasswordResetRequest request) {
        if (request.getRequestId() == null) {
            request.setRequestId(UUID.randomUUID());
        }
        String sql = """
            INSERT INTO password_reset_requests (
                request_id, user_id, identifier_submitted, role, reason, status, requested_at, resolved_at, admin_remarks
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            setUuidParam(c, ps, 1, request.getRequestId());
            if (request.getUserId() != null) ps.setInt(2, request.getUserId()); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, request.getIdentifierSubmitted());
            ps.setString(4, request.getRole() != null ? request.getRole().name() : "STUDENT");
            ps.setString(5, request.getReason());
            ps.setString(6, request.getStatus() != null ? request.getStatus() : "PENDING");
            ps.setTimestamp(7, request.getRequestedAt() != null ? Timestamp.valueOf(request.getRequestedAt()) : new Timestamp(System.currentTimeMillis()));
            if (request.getResolvedAt() != null) ps.setTimestamp(8, Timestamp.valueOf(request.getResolvedAt())); else ps.setNull(8, Types.TIMESTAMP);
            ps.setString(9, request.getAdminRemarks());

            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save password reset request {}", request.getRequestId(), e);
            throw new AppException("Failed to submit password reset request: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PasswordResetRequest> findById(UUID requestId) {
        String sql = BASE_SELECT + " WHERE prr.request_id = ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            setUuidParam(c, ps, 1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find password reset request by id", e);
        }
    }

    @Override
    public List<PasswordResetRequest> findByStatus(String status) {
        String sql = BASE_SELECT + " WHERE LOWER(prr.status) = LOWER(?) ORDER BY prr.requested_at DESC";
        List<PasswordResetRequest> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new AppException("Failed to list password reset requests by status", e);
        }
    }

    @Override
    public List<PasswordResetRequest> findAll() {
        String sql = BASE_SELECT + " ORDER BY prr.requested_at DESC";
        List<PasswordResetRequest> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            throw new AppException("Failed to list all password reset requests", e);
        }
    }

    @Override
    public void update(PasswordResetRequest request) {
        String sql = """
            UPDATE password_reset_requests
            SET status = ?, resolved_at = ?, admin_remarks = ?
            WHERE request_id = ?
        """;
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, request.getStatus());
            if (request.getResolvedAt() != null) ps.setTimestamp(2, Timestamp.valueOf(request.getResolvedAt())); else ps.setNull(2, Types.TIMESTAMP);
            ps.setString(3, request.getAdminRemarks());
            setUuidParam(c, ps, 4, request.getRequestId());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Failed to update password reset request: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(UUID requestId) {
        String sql = "DELETE FROM password_reset_requests WHERE request_id = ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            setUuidParam(c, ps, 1, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Failed to delete password reset request", e);
        }
    }

    private void setUuidParam(Connection c, PreparedStatement ps, int paramIndex, UUID uuid) throws SQLException {
        boolean isPg = false;
        try {
            isPg = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (Exception ignored) {}

        if (isPg) {
            ps.setObject(paramIndex, uuid);
        } else {
            ps.setString(paramIndex, uuid != null ? uuid.toString() : null);
        }
    }

    private PasswordResetRequest map(ResultSet rs) throws SQLException {
        PasswordResetRequest r = new PasswordResetRequest();
        Object rawId = rs.getObject("request_id");
        if (rawId instanceof UUID u) {
            r.setRequestId(u);
        } else if (rawId != null) {
            r.setRequestId(UUID.fromString(rawId.toString()));
        }

        int uid = rs.getInt("user_id");
        if (!rs.wasNull()) {
            r.setUserId(uid);
        }

        r.setIdentifierSubmitted(rs.getString("identifier_submitted"));
        String roleStr = rs.getString("role");
        if (roleStr != null) {
            try {
                r.setRole(Role.valueOf(roleStr.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {}
        }

        r.setReason(rs.getString("reason"));
        r.setStatus(rs.getString("status"));

        Timestamp reqAt = rs.getTimestamp("requested_at");
        if (reqAt != null) r.setRequestedAt(reqAt.toLocalDateTime());

        Timestamp resAt = rs.getTimestamp("resolved_at");
        if (resAt != null) r.setResolvedAt(resAt.toLocalDateTime());

        r.setAdminRemarks(rs.getString("admin_remarks"));

        // Join columns
        try {
            r.setUsername(rs.getString("username"));
            r.setUserEmail(rs.getString("user_email"));
            String fn = rs.getString("first_name");
            String ln = rs.getString("last_name");
            if (fn != null || ln != null) {
                r.setFullName(((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim());
            }
        } catch (SQLException ignored) {}

        return r;
    }
}
