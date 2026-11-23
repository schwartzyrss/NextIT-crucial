package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Role;
import com.nextit.model.VerificationRequest;
import com.nextit.model.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcVerificationRequestRepository implements VerificationRequestRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcVerificationRequestRepository.class);

    private static final String BASE_SELECT =
            "SELECT vr.*, u.username, u.first_name, u.last_name, u.email AS user_email " +
            "FROM verification_requests vr " +
            "JOIN users u ON vr.user_id = u.user_id ";

    @Override
    public Optional<VerificationRequest> findById(int id) {
        String sql = BASE_SELECT + "WHERE vr.request_id = ?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find verification request by id", e);
        }
    }

    @Override
    public Optional<VerificationRequest> findByUserId(int userId) {
        String sql = BASE_SELECT + "WHERE vr.user_id = ? ORDER BY vr.request_id DESC LIMIT 1";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find verification request by user id", e);
        }
    }

    @Override
    public List<VerificationRequest> findAll() {
        String sql = BASE_SELECT + "ORDER BY vr.request_id DESC";
        List<VerificationRequest> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            throw new AppException("Failed to list verification requests", e);
        }
    }

    @Override
    public List<VerificationRequest> findPending() {
        String sql = BASE_SELECT + "WHERE vr.status = 'PENDING' ORDER BY vr.risk_score DESC, vr.request_id ASC";
        List<VerificationRequest> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            throw new AppException("Failed to list pending verification requests", e);
        }
    }

    @Override
    public void save(VerificationRequest req) {
        String sql = "INSERT INTO verification_requests (user_id, role, id_number, institutional_email, organization_name, business_registration_number, document_reference, submission_notes, status, risk_score, risk_flags) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, req.getUserId());
            ps.setString(2, req.getRole().name());
            ps.setString(3, req.getIdNumber());
            ps.setString(4, req.getInstitutionalEmail());
            ps.setString(5, req.getOrganizationName());
            ps.setString(6, req.getBusinessRegistrationNumber());
            ps.setString(7, req.getDocumentReference());
            ps.setString(8, req.getSubmissionNotes());
            ps.setString(9, req.getStatus().name());
            ps.setInt(10, req.getRiskScore());
            ps.setString(11, req.getRiskFlags());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) req.setRequestId(keys.getInt(1));
            }
            log.info("Saved verification request [request_id={}, user_id={}, role={}]", req.getRequestId(), req.getUserId(), req.getRole());
        } catch (SQLException e) {
            log.error("Failed to save verification request [user_id={}, role={}, SQLState={}, ErrorCode={}]: {}",
                    req.getUserId(), req.getRole(), e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new AppException("Failed to save verification request: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(VerificationRequest req) {
        String sql = "UPDATE verification_requests SET role=?, id_number=?, institutional_email=?, organization_name=?, business_registration_number=?, document_reference=?, submission_notes=?, status=?, risk_score=?, risk_flags=?, reviewer_id=?, reviewer_notes=?, reviewed_at=? WHERE request_id=?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, req.getRole().name());
            ps.setString(2, req.getIdNumber());
            ps.setString(3, req.getInstitutionalEmail());
            ps.setString(4, req.getOrganizationName());
            ps.setString(5, req.getBusinessRegistrationNumber());
            ps.setString(6, req.getDocumentReference());
            ps.setString(7, req.getSubmissionNotes());
            ps.setString(8, req.getStatus().name());
            ps.setInt(9, req.getRiskScore());
            ps.setString(10, req.getRiskFlags());
            if (req.getReviewerId() != null) ps.setInt(11, req.getReviewerId()); else ps.setNull(11, Types.INTEGER);
            ps.setString(12, req.getReviewerNotes());
            if (req.getReviewedAt() != null) ps.setTimestamp(13, Timestamp.valueOf(req.getReviewedAt())); else ps.setNull(13, Types.TIMESTAMP);
            ps.setInt(14, req.getRequestId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                log.warn("UPDATE verification_requests affected 0 rows for request_id={}", req.getRequestId());
            } else {
                log.info("Successfully committed verification request update [request_id={}, status={}]", req.getRequestId(), req.getStatus());
            }
        } catch (SQLException e) {
            log.error("Failed to update verification request [request_id={}, SQLState={}, ErrorCode={}]: {}",
                    req.getRequestId(), e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new AppException("Failed to update verification request: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM verification_requests WHERE request_id = ?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Failed to delete verification request", e);
        }
    }

    private VerificationRequest map(ResultSet rs) throws SQLException {
        VerificationRequest req = new VerificationRequest();
        req.setRequestId(rs.getInt("request_id"));
        req.setUserId(rs.getInt("user_id"));
        req.setRole(Role.fromString(rs.getString("role")));
        req.setIdNumber(rs.getString("id_number"));
        req.setInstitutionalEmail(rs.getString("institutional_email"));
        req.setOrganizationName(rs.getString("organization_name"));
        req.setBusinessRegistrationNumber(rs.getString("business_registration_number"));
        req.setDocumentReference(rs.getString("document_reference"));
        req.setSubmissionNotes(rs.getString("submission_notes"));
        req.setStatus(VerificationStatus.valueOf(rs.getString("status")));
        req.setRiskScore(rs.getInt("risk_score"));
        req.setRiskFlags(rs.getString("risk_flags"));
        int reviewer = rs.getInt("reviewer_id");
        if (!rs.wasNull()) req.setReviewerId(reviewer);
        req.setReviewerNotes(rs.getString("reviewer_notes"));
        Timestamp reviewed = rs.getTimestamp("reviewed_at");
        if (reviewed != null) req.setReviewedAt(reviewed.toLocalDateTime());
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) req.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) req.setUpdatedAt(updated.toLocalDateTime());

        try {
            req.setUsername(rs.getString("username"));
            req.setUserFullName(rs.getString("first_name") + " " + rs.getString("last_name"));
            req.setUserEmail(rs.getString("user_email"));
        } catch (SQLException ignored) {}

        return req;
    }
}
