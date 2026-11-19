package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Application;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcApplicationRepository {

    public List<Application> findByStudentId(int studentId) {
        List<Application> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM applications WHERE student_id=? ORDER BY application_date DESC")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); return list; }
        } catch (SQLException e) { throw new AppException("Failed to load applications", e); }
    }

    public java.util.Optional<Application> findById(int applicationId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM applications WHERE application_id=?")) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? java.util.Optional.of(map(rs)) : java.util.Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load application", e); }
    }

    public java.util.Optional<Application> findByStudentAndOpportunity(int studentId, int opportunityId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM applications WHERE student_id=? AND opportunity_id=?")) {
            ps.setInt(1, studentId);
            ps.setInt(2, opportunityId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? java.util.Optional.of(map(rs)) : java.util.Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load application", e); }
    }

    public List<Application> findByOpportunityId(int opportunityId) {
        List<Application> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM applications WHERE opportunity_id=?")) {
            ps.setInt(1, opportunityId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); return list; }
        } catch (SQLException e) { throw new AppException("Failed to load applications", e); }
    }

    public List<Application> findAll() {
        List<Application> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM applications")) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) { throw new AppException("Failed to load applications", e); }
    }

    public void save(Application a) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO applications (opportunity_id, student_id, status, remarks) VALUES (?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getOpportunityId());
            ps.setInt(2, a.getStudentId());
            ps.setString(3, a.getStatus() == null ? "Pending" : a.getStatus());
            ps.setString(4, a.getRemarks());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { if (k.next()) a.setApplicationId(k.getInt(1)); }
        } catch (SQLException e) { throw new AppException("Failed to save application", e); }
    }

    public void update(Application a) {
        String sqlWithReviewed = "UPDATE applications SET status=?, remarks=?, reviewed_at=? WHERE application_id=?";
        String sqlFallback = "UPDATE applications SET status=?, remarks=? WHERE application_id=?";
        try (Connection c = DatabaseConfig.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sqlWithReviewed)) {
                ps.setString(1, a.getStatus());
                ps.setString(2, a.getRemarks());
                java.time.LocalDateTime rev = a.getReviewedAt() != null ? a.getReviewedAt() : java.time.LocalDateTime.now();
                ps.setTimestamp(3, Timestamp.valueOf(rev));
                ps.setInt(4, a.getApplicationId());
                ps.executeUpdate();
                return;
            } catch (SQLException e) {
                // Fallback if reviewed_at column does not exist on target database
                try (PreparedStatement ps = c.prepareStatement(sqlFallback)) {
                    ps.setString(1, a.getStatus());
                    ps.setString(2, a.getRemarks());
                    ps.setInt(3, a.getApplicationId());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) { throw new AppException("Failed to update application", e); }
    }

    private Application map(ResultSet rs) throws SQLException {
        Application a = new Application();
        a.setApplicationId(rs.getInt("application_id"));
        a.setOpportunityId(rs.getInt("opportunity_id"));
        a.setStudentId(rs.getInt("student_id"));
        Timestamp t = rs.getTimestamp("application_date");
        if (t != null) a.setApplicationDate(t.toLocalDateTime());
        a.setStatus(rs.getString("status"));
        a.setRemarks(rs.getString("remarks"));
        try {
            Timestamp rev = rs.getTimestamp("reviewed_at");
            if (rev != null) a.setReviewedAt(rev.toLocalDateTime());
        } catch (SQLException ignored) {}
        return a;
    }
}
