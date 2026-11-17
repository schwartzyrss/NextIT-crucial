package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {

    @Override
    public Optional<User> findById(int id) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find user by id", e);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find user", e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find user", e);
        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY user_id";
        List<User> users = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) users.add(map(rs));
            return users;
        } catch (SQLException e) {
            throw new AppException("Failed to list users", e);
        }
    }

    @Override
    public void save(User user) {
        String sqlWithSecurity = "INSERT INTO users (username, password_hash, email, role, first_name, last_name, verification_status, rejection_reason, verified_at, failed_login_attempts, locked_until, mfa_enabled) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        String fallbackSql = "INSERT INTO users (username, password_hash, email, role, first_name, last_name) VALUES (?,?,?,?,?,?)";

        VerificationStatus status = user.getVerificationStatus() != null ? user.getVerificationStatus() : VerificationStatus.VERIFIED;

        try (Connection c = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sqlWithSecurity, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getPasswordHash());
                ps.setString(3, user.getEmail());
                ps.setString(4, user.getRole().name());
                ps.setString(5, user.getFirstName());
                ps.setString(6, user.getLastName());
                ps.setString(7, status.name());
                ps.setString(8, user.getRejectionReason());
                if (user.getVerifiedAt() != null) ps.setTimestamp(9, Timestamp.valueOf(user.getVerifiedAt())); else ps.setNull(9, Types.TIMESTAMP);
                ps.setInt(10, user.getFailedLoginAttempts());
                if (user.getLockedUntil() != null) ps.setTimestamp(11, Timestamp.valueOf(user.getLockedUntil())); else ps.setNull(11, Types.TIMESTAMP);
                ps.setBoolean(12, user.isMfaEnabled());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) user.setUserId(keys.getInt(1));
                }
            } catch (SQLException ex) {
                // If the additional columns don't exist yet in an older DB, fallback to standard insert
                try (PreparedStatement ps = c.prepareStatement(fallbackSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, user.getUsername());
                    ps.setString(2, user.getPasswordHash());
                    ps.setString(3, user.getEmail());
                    ps.setString(4, user.getRole().name());
                    ps.setString(5, user.getFirstName());
                    ps.setString(6, user.getLastName());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) user.setUserId(keys.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            throw new AppException("Failed to save user: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(User user) {
        String sql = "UPDATE users SET username=?, email=?, role=?, first_name=?, last_name=?, verification_status=?, rejection_reason=?, mfa_enabled=? WHERE user_id=?";
        String fallbackSql = "UPDATE users SET username=?, email=?, role=?, first_name=?, last_name=? WHERE user_id=?";
        VerificationStatus status = user.getVerificationStatus() != null ? user.getVerificationStatus() : VerificationStatus.VERIFIED;

        try (Connection c = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getEmail());
                ps.setString(3, user.getRole().name());
                ps.setString(4, user.getFirstName());
                ps.setString(5, user.getLastName());
                ps.setString(6, status.name());
                ps.setString(7, user.getRejectionReason());
                ps.setBoolean(8, user.isMfaEnabled());
                ps.setInt(9, user.getUserId());
                ps.executeUpdate();
            } catch (SQLException ex) {
                try (PreparedStatement ps = c.prepareStatement(fallbackSql)) {
                    ps.setString(1, user.getUsername());
                    ps.setString(2, user.getEmail());
                    ps.setString(3, user.getRole().name());
                    ps.setString(4, user.getFirstName());
                    ps.setString(5, user.getLastName());
                    ps.setInt(6, user.getUserId());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new AppException("Failed to update user", e);
        }
    }

    @Override
    public void updatePassword(int userId, String passwordHash) {
        String sql = "UPDATE users SET password_hash=? WHERE user_id=?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Failed to update password", e);
        }
    }

    @Override
    public void updateVerificationStatus(int userId, VerificationStatus status, String rejectionReason, Integer verifiedBy) {
        String sql = "UPDATE users SET verification_status=?, rejection_reason=?, verified_at=?, updated_at=CURRENT_TIMESTAMP WHERE user_id=?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, rejectionReason);
            if (status == VerificationStatus.VERIFIED) {
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            } else {
                ps.setNull(3, Types.TIMESTAMP);
            }
            ps.setInt(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Failed to update verification status: " + e.getMessage(), e);
        }
    }

    @Override
    public void recordFailedLogin(int userId, int attempts, LocalDateTime lockedUntil) {
        String sql = "UPDATE users SET failed_login_attempts=?, locked_until=? WHERE user_id=?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, attempts);
            if (lockedUntil != null) ps.setTimestamp(2, Timestamp.valueOf(lockedUntil)); else ps.setNull(2, Types.TIMESTAMP);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Non-critical if schema not updated yet
        }
    }

    @Override
    public void resetFailedLogins(int userId) {
        String sql = "UPDATE users SET failed_login_attempts=0, locked_until=NULL WHERE user_id=?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public void updateMfa(int userId, boolean enabled) {
        String sql = "UPDATE users SET mfa_enabled=? WHERE user_id=?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AppException("This user cannot be deleted because they are referenced by grades, attendance, evaluations or academic support records.", e);
        } catch (SQLException e) {
            throw new AppException("Failed to delete user", e);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setEmail(rs.getString("email"));
        u.setRole(Role.valueOf(rs.getString("role")));
        u.setFirstName(rs.getString("first_name"));
        u.setLastName(rs.getString("last_name"));

        try {
            String vStatus = rs.getString("verification_status");
            if (vStatus != null) {
                u.setVerificationStatus(VerificationStatus.valueOf(vStatus.toUpperCase()));
            } else {
                u.setVerificationStatus(VerificationStatus.VERIFIED);
            }
        } catch (Exception ignored) {
            u.setVerificationStatus(VerificationStatus.VERIFIED);
        }

        try {
            u.setRejectionReason(rs.getString("rejection_reason"));
        } catch (Exception ignored) {}

        try {
            Timestamp vat = rs.getTimestamp("verified_at");
            if (vat != null) u.setVerifiedAt(vat.toLocalDateTime());
        } catch (Exception ignored) {}

        try {
            u.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        } catch (Exception ignored) {}

        try {
            Timestamp locked = rs.getTimestamp("locked_until");
            if (locked != null) u.setLockedUntil(locked.toLocalDateTime());
        } catch (Exception ignored) {}

        try {
            u.setMfaEnabled(rs.getBoolean("mfa_enabled"));
        } catch (Exception ignored) {}

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) u.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) u.setUpdatedAt(updated.toLocalDateTime());
        return u;
    }
}
