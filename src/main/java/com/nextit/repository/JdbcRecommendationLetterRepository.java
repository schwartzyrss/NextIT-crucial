package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.RecommendationLetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcRecommendationLetterRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcRecommendationLetterRepository.class);
    private static volatile boolean tableInitialized = false;

    public JdbcRecommendationLetterRepository() {
        ensureTableExists();
    }

    private void ensureTableExists() {
        if (tableInitialized) return;
        synchronized (JdbcRecommendationLetterRepository.class) {
            if (tableInitialized) return;
            try (Connection c = DatabaseConfig.getConnection();
                 ResultSet rs = c.getMetaData().getTables(null, null, "recommendation_letters", new String[]{"TABLE"})) {
                if (rs.next()) {
                    tableInitialized = true;
                    return;
                }
            } catch (SQLException ignored) {
            }
            try (Connection c = DatabaseConfig.getConnection();
                 Statement s = c.createStatement()) {
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS recommendation_letters (
                        letter_id INT AUTO_INCREMENT PRIMARY KEY,
                        student_id INT NOT NULL,
                        instructor_id INT NOT NULL,
                        opportunity_id INT NULL,
                        application_id INT NULL,
                        letter_title VARCHAR(200) NOT NULL,
                        recommendation_body TEXT NOT NULL,
                        endorsement_highlights TEXT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        issued_at DATETIME NULL,
                        INDEX idx_rec_student (student_id),
                        INDEX idx_rec_instructor (instructor_id),
                        INDEX idx_rec_opportunity (opportunity_id),
                        INDEX idx_rec_application (application_id),
                        INDEX idx_rec_status (status)
                    )
                """);
                tableInitialized = true;
            } catch (SQLException e) {
                log.warn("Could not auto-verify recommendation_letters table: {}", e.getMessage());
            }
        }
    }

    public List<RecommendationLetter> findAll() {
        List<RecommendationLetter> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM recommendation_letters ORDER BY created_at DESC")) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            throw new AppException("Failed to load recommendation letters", e);
        }
    }

    public Optional<RecommendationLetter> findById(int letterId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM recommendation_letters WHERE letter_id=?")) {
            ps.setInt(1, letterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to load recommendation letter", e);
        }
    }

    public List<RecommendationLetter> findByStudentId(int studentId) {
        List<RecommendationLetter> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM recommendation_letters WHERE student_id=? ORDER BY created_at DESC")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new AppException("Failed to load student recommendation letters", e);
        }
    }

    public List<RecommendationLetter> findByInstructorId(int instructorId) {
        List<RecommendationLetter> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM recommendation_letters WHERE instructor_id=? ORDER BY created_at DESC")) {
            ps.setInt(1, instructorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new AppException("Failed to load instructor recommendation letters", e);
        }
    }

    public List<RecommendationLetter> findByOpportunityId(int opportunityId) {
        List<RecommendationLetter> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM recommendation_letters WHERE opportunity_id=? ORDER BY created_at DESC")) {
            ps.setInt(1, opportunityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new AppException("Failed to load opportunity recommendation letters", e);
        }
    }

    public List<RecommendationLetter> findByApplicationId(int applicationId) {
        List<RecommendationLetter> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM recommendation_letters WHERE application_id=? ORDER BY created_at DESC")) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new AppException("Failed to load application recommendation letters", e);
        }
    }

    public List<RecommendationLetter> findByStudentAndOpportunity(int studentId, int opportunityId) {
        List<RecommendationLetter> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM recommendation_letters WHERE student_id=? AND opportunity_id=? ORDER BY created_at DESC")) {
            ps.setInt(1, studentId);
            ps.setInt(2, opportunityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new AppException("Failed to load recommendation letters", e);
        }
    }

    public Optional<RecommendationLetter> findByStudentAndInstructorAndOpportunity(int studentId, int instructorId, Integer opportunityId) {
        String sql = (opportunityId == null)
                ? "SELECT * FROM recommendation_letters WHERE student_id=? AND instructor_id=? AND opportunity_id IS NULL ORDER BY created_at DESC LIMIT 1"
                : "SELECT * FROM recommendation_letters WHERE student_id=? AND instructor_id=? AND opportunity_id=? ORDER BY created_at DESC LIMIT 1";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, instructorId);
            if (opportunityId != null) {
                ps.setInt(3, opportunityId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find recommendation letter request", e);
        }
    }

    public void save(RecommendationLetter l) {
        String sql = """
            INSERT INTO recommendation_letters (
                student_id, instructor_id, opportunity_id, application_id,
                letter_title, recommendation_body, endorsement_highlights, status, created_at, issued_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, l.getStudentId());
            ps.setInt(2, l.getInstructorId());
            if (l.getOpportunityId() != null) ps.setInt(3, l.getOpportunityId()); else ps.setNull(3, Types.INTEGER);
            if (l.getApplicationId() != null) ps.setInt(4, l.getApplicationId()); else ps.setNull(4, Types.INTEGER);
            ps.setString(5, l.getLetterTitle());
            ps.setString(6, l.getRecommendationBody());
            ps.setString(7, l.getEndorsementHighlights());
            ps.setString(8, l.getStatus() == null ? "REQUESTED" : l.getStatus());
            ps.setTimestamp(9, l.getCreatedAt() != null ? Timestamp.valueOf(l.getCreatedAt()) : Timestamp.valueOf(java.time.LocalDateTime.now()));
            if (l.getIssuedAt() != null) ps.setTimestamp(10, Timestamp.valueOf(l.getIssuedAt())); else ps.setNull(10, Types.TIMESTAMP);

            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) l.setLetterId(k.getInt(1));
            }
        } catch (SQLException e) {
            throw new AppException("Failed to save recommendation letter: " + e.getMessage(), e);
        }
    }

    public void update(RecommendationLetter l) {
        String sql = """
            UPDATE recommendation_letters SET
                student_id=?, instructor_id=?, opportunity_id=?, application_id=?,
                letter_title=?, recommendation_body=?, endorsement_highlights=?, status=?, issued_at=?
            WHERE letter_id=?
        """;
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, l.getStudentId());
            ps.setInt(2, l.getInstructorId());
            if (l.getOpportunityId() != null) ps.setInt(3, l.getOpportunityId()); else ps.setNull(3, Types.INTEGER);
            if (l.getApplicationId() != null) ps.setInt(4, l.getApplicationId()); else ps.setNull(4, Types.INTEGER);
            ps.setString(5, l.getLetterTitle());
            ps.setString(6, l.getRecommendationBody());
            ps.setString(7, l.getEndorsementHighlights());
            ps.setString(8, l.getStatus());
            if (l.getIssuedAt() != null) ps.setTimestamp(9, Timestamp.valueOf(l.getIssuedAt())); else ps.setNull(9, Types.TIMESTAMP);
            ps.setInt(10, l.getLetterId());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Failed to update recommendation letter: " + e.getMessage(), e);
        }
    }

    public void delete(int letterId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM recommendation_letters WHERE letter_id=?")) {
            ps.setInt(1, letterId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Failed to delete recommendation letter", e);
        }
    }

    private RecommendationLetter map(ResultSet rs) throws SQLException {
        RecommendationLetter l = new RecommendationLetter();
        l.setLetterId(rs.getInt("letter_id"));
        l.setStudentId(rs.getInt("student_id"));
        l.setInstructorId(rs.getInt("instructor_id"));

        int oppId = rs.getInt("opportunity_id");
        if (!rs.wasNull()) l.setOpportunityId(oppId);

        int appId = rs.getInt("application_id");
        if (!rs.wasNull()) l.setApplicationId(appId);

        l.setLetterTitle(rs.getString("letter_title"));
        l.setRecommendationBody(rs.getString("recommendation_body"));
        l.setEndorsementHighlights(rs.getString("endorsement_highlights"));
        l.setStatus(rs.getString("status"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) l.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp issuedAt = rs.getTimestamp("issued_at");
        if (issuedAt != null) l.setIssuedAt(issuedAt.toLocalDateTime());

        return l;
    }
}
