package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Enrollment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcEnrollmentRepository {

    public List<Enrollment> findByStudentId(int studentId) {
        String sql = "SELECT * FROM enrollments WHERE student_id=? ORDER BY enrollment_id DESC";
        List<Enrollment> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) { throw new AppException("Failed to load enrollments", e); }
    }

    public List<Enrollment> findByInstructorId(int instructorId) {
        String sql = "SELECT e.* FROM enrollments e LEFT JOIN subjects s ON s.subject_id = e.subject_id "
                + "WHERE e.instructor_id = ? OR s.instructor_id = ? ORDER BY e.enrollment_id DESC";
        List<Enrollment> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, instructorId);
            ps.setInt(2, instructorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) { throw new AppException("Failed to load enrollments for instructor", e); }
    }

    public List<Enrollment> findBySubjectId(int subjectId) {
        String sql = "SELECT * FROM enrollments WHERE subject_id=? ORDER BY enrollment_id DESC";
        List<Enrollment> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) { throw new AppException("Failed to load enrollments for subject", e); }
    }

    public List<Enrollment> findByInstructorAndSubject(int instructorId, int subjectId) {
        String sql = "SELECT e.* FROM enrollments e LEFT JOIN subjects s ON s.subject_id = e.subject_id "
                + "WHERE (e.instructor_id = ? OR s.instructor_id = ?) AND e.subject_id = ? ORDER BY e.enrollment_id DESC";
        List<Enrollment> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, instructorId);
            ps.setInt(2, instructorId);
            ps.setInt(3, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) { throw new AppException("Failed to load enrollments for instructor and subject", e); }
    }

    public Optional<Enrollment> findById(int enrollmentId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM enrollments WHERE enrollment_id=?")) {
            ps.setInt(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load enrollment", e); }
    }

    public Optional<Enrollment> findByStudentAndSubject(int studentId, int subjectId) {
        // A student can retake a subject across terms. Prefer the in-progress record,
        // then the most recently created one, so callers get a deterministic result.
        String sql = "SELECT * FROM enrollments WHERE student_id=? AND subject_id=? "
                + "ORDER BY CASE status WHEN 'Enrolled' THEN 0 WHEN 'Completed' THEN 1 ELSE 2 END, "
                + "enrollment_id DESC";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load enrollment", e); }
    }

    public List<Enrollment> findAll() {
        List<Enrollment> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM enrollments ORDER BY student_id, subject_id")) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) { throw new AppException("Failed to load enrollments", e); }
    }

    private static volatile boolean checkedEnrollmentColumns = false;

    private static void ensureEnrollmentColumns() {
        if (checkedEnrollmentColumns) return;
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement()) {
            st.execute("ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS instructor_id INT NULL");
            st.execute("ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS grade VARCHAR(20) NULL");
            st.execute("ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
            checkedEnrollmentColumns = true;
        } catch (Exception ignored) {
            checkedEnrollmentColumns = true;
        }
    }

    public void save(Enrollment e) {
        ensureEnrollmentColumns();
        String sql = "INSERT INTO enrollments (student_id, subject_id, academic_year, semester, status, instructor_id, grade, enrolled_at) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, e.getStudentId());
            ps.setInt(2, e.getSubjectId());
            ps.setString(3, e.getAcademicYear());
            ps.setString(4, e.getSemester());
            ps.setString(5, e.getStatus());
            if (e.getInstructorId() != null) ps.setInt(6, e.getInstructorId()); else ps.setNull(6, Types.INTEGER);
            ps.setString(7, e.getGrade());
            Timestamp ts = e.getEnrolledAt() != null ? Timestamp.valueOf(e.getEnrolledAt()) : new Timestamp(System.currentTimeMillis());
            ps.setTimestamp(8, ts);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) e.setEnrollmentId(k.getInt(1));
            }
        } catch (SQLException ex) { throw new AppException("Failed to save enrollment: " + (ex.getMessage() != null ? ex.getMessage() : ""), ex); }
    }

    public void update(Enrollment e) {
        ensureEnrollmentColumns();
        String sql = "UPDATE enrollments SET student_id=?, subject_id=?, academic_year=?, semester=?, status=?, instructor_id=?, grade=? WHERE enrollment_id=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, e.getStudentId());
            ps.setInt(2, e.getSubjectId());
            ps.setString(3, e.getAcademicYear());
            ps.setString(4, e.getSemester());
            ps.setString(5, e.getStatus());
            if (e.getInstructorId() != null) ps.setInt(6, e.getInstructorId()); else ps.setNull(6, Types.INTEGER);
            ps.setString(7, e.getGrade());
            ps.setInt(8, e.getEnrollmentId());
            ps.executeUpdate();
        } catch (SQLException ex) { throw new AppException("Failed to update enrollment: " + (ex.getMessage() != null ? ex.getMessage() : ""), ex); }
    }

    public void delete(int enrollmentId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM enrollments WHERE enrollment_id=?")) {
            ps.setInt(1, enrollmentId);
            ps.executeUpdate();
        } catch (SQLException ex) { throw new AppException("Failed to delete enrollment", ex); }
    }

    private Enrollment map(ResultSet rs) throws SQLException {
        Enrollment e = new Enrollment();
        e.setEnrollmentId(rs.getInt("enrollment_id"));
        e.setStudentId(rs.getInt("student_id"));
        e.setSubjectId(rs.getInt("subject_id"));
        e.setAcademicYear(rs.getString("academic_year"));
        e.setSemester(rs.getString("semester"));
        e.setStatus(rs.getString("status"));
        try {
            int instId = rs.getInt("instructor_id");
            if (!rs.wasNull()) e.setInstructorId(instId);
        } catch (SQLException ignored) {}
        try { e.setGrade(rs.getString("grade")); } catch (SQLException ignored) {}
        try {
            Timestamp ts = rs.getTimestamp("enrolled_at");
            if (ts != null) e.setEnrolledAt(ts.toLocalDateTime());
        } catch (SQLException ignored) {}
        return e;
    }
}
