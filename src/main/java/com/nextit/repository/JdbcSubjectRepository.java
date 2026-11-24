package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcSubjectRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcSubjectRepository.class);
    private static volatile boolean checkedSubjectColumns = false;

    private static void ensureSubjectColumns() {
        if (checkedSubjectColumns) return;
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement()) {
            st.execute("ALTER TABLE subjects ADD COLUMN IF NOT EXISTS section VARCHAR(30) NULL");
            st.execute("ALTER TABLE subjects ADD COLUMN IF NOT EXISTS semester VARCHAR(20) NULL");
            st.execute("ALTER TABLE subjects ADD COLUMN IF NOT EXISTS academic_year VARCHAR(20) NULL");
            st.execute("ALTER TABLE subjects ADD COLUMN IF NOT EXISTS instructor_id INT NULL");
            checkedSubjectColumns = true;
        } catch (Exception ignored) {
            checkedSubjectColumns = true;
        }
    }

    public Optional<Subject> findById(int subjectId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM subjects WHERE subject_id=?")) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load subject", e); }
    }

    public Optional<Subject> findBySubjectCode(String subjectCode) {
        if (subjectCode == null || subjectCode.isBlank()) return Optional.empty();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM subjects WHERE LOWER(TRIM(subject_code))=LOWER(TRIM(?))")) {
            ps.setString(1, subjectCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to lookup subject by code", e); }
    }

    public List<Subject> findAll() {
        List<Subject> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM subjects ORDER BY subject_code")) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) { throw new AppException("Failed to load subjects", e); }
    }

    public List<Subject> findByInstructorId(int instructorId) {
        List<Subject> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM subjects WHERE instructor_id=? ORDER BY subject_code")) {
            ps.setInt(1, instructorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) { throw new AppException("Failed to load subjects for instructor", e); }
    }

    public void save(Subject s) {
        ensureSubjectColumns();
        String sql = "INSERT INTO subjects (subject_code, subject_name, units, description, instructor_id, section, semester, academic_year) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getSubjectCode());
            ps.setString(2, s.getSubjectName());
            ps.setDouble(3, s.getUnits());
            ps.setString(4, s.getDescription());
            if (s.getInstructorId() != null) ps.setInt(5, s.getInstructorId()); else ps.setNull(5, Types.INTEGER);
            ps.setString(6, s.getSection());
            ps.setString(7, s.getSemester());
            ps.setString(8, s.getAcademicYear());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { if (k.next()) s.setSubjectId(k.getInt(1)); }
        } catch (SQLException e) {
            log.error("Failed to save subject '{}': {}", s.getSubjectCode(), e.getMessage(), e);
            throw new AppException("Failed to save subject: " + (e.getMessage() != null ? e.getMessage() : ""), e);
        }
    }

    public void update(Subject s) {
        ensureSubjectColumns();
        String sql = "UPDATE subjects SET subject_code=?, subject_name=?, units=?, description=?, instructor_id=?, section=?, semester=?, academic_year=? WHERE subject_id=?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getSubjectCode());
            ps.setString(2, s.getSubjectName());
            ps.setDouble(3, s.getUnits());
            ps.setString(4, s.getDescription());
            if (s.getInstructorId() != null) ps.setInt(5, s.getInstructorId()); else ps.setNull(5, Types.INTEGER);
            ps.setString(6, s.getSection());
            ps.setString(7, s.getSemester());
            ps.setString(8, s.getAcademicYear());
            ps.setInt(9, s.getSubjectId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update subject '{}': {}", s.getSubjectCode(), e.getMessage(), e);
            throw new AppException("Failed to update subject: " + (e.getMessage() != null ? e.getMessage() : ""), e);
        }
    }

    public void delete(int id) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM subjects WHERE subject_id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AppException("This subject cannot be deleted because it is used by enrollments, projects or academic support records.", e);
        } catch (SQLException e) { throw new AppException("Failed to delete subject", e); }
    }

    private Subject map(ResultSet rs) throws SQLException {
        Subject s = new Subject();
        s.setSubjectId(rs.getInt("subject_id"));
        s.setSubjectCode(rs.getString("subject_code"));
        s.setSubjectName(rs.getString("subject_name"));
        s.setUnits(rs.getDouble("units"));
        s.setDescription(rs.getString("description"));
        try {
            int instId = rs.getInt("instructor_id");
            if (!rs.wasNull()) s.setInstructorId(instId);
        } catch (SQLException ignored) {}
        try { s.setSection(rs.getString("section")); } catch (SQLException ignored) {}
        try { s.setSemester(rs.getString("semester")); } catch (SQLException ignored) {}
        try { s.setAcademicYear(rs.getString("academic_year")); } catch (SQLException ignored) {}
        return s;
    }
}
