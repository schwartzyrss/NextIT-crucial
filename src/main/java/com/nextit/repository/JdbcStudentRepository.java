package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcStudentRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcStudentRepository.class);

    public Optional<Student> findById(int studentId) {
        String sql = "SELECT * FROM students WHERE student_id=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load student", e); }
    }

    public Optional<Student> findByUserId(int userId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM students WHERE user_id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load student", e); }
    }

    public Optional<Student> findByStudentNumber(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM students WHERE LOWER(TRIM(student_number))=LOWER(TRIM(?))";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, studentNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load student by student number", e); }
    }

    public String generateNextStudentNumber(int year) {
        return com.nextit.service.RoleIdService.getInstance().generateUniqueId(com.nextit.model.Role.STUDENT, year);
    }

    public List<Student> findAll() {
        List<Student> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM students ORDER BY student_number")) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) { throw new AppException("Failed to load students", e); }
    }

    public void save(Student s) {
        String sql = "INSERT INTO students (user_id, student_number, program, year_level, section, enrollment_status) VALUES (?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getUserId());
            ps.setString(2, s.getStudentNumber());
            ps.setString(3, s.getProgram());
            ps.setInt(4, s.getYearLevel());
            ps.setString(5, s.getSection());
            ps.setString(6, s.getEnrollmentStatus());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) s.setStudentId(k.getInt(1));
            }
            if (s.getStudentId() <= 0 && s.getUserId() > 0) {
                findByUserId(s.getUserId()).ifPresent(found -> s.setStudentId(found.getStudentId()));
            }
            log.info("Saved student profile [student_id={}, user_id={}, number={}]", s.getStudentId(), s.getUserId(), s.getStudentNumber());
        } catch (SQLException e) {
            log.error("Failed to save student profile [user_id={}, number={}, SQLState={}, ErrorCode={}]: {}",
                    s.getUserId(), s.getStudentNumber(), e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new AppException("Failed to save student: " + e.getMessage(), e);
        }
    }

    public void update(Student s) {
        // student_number is strictly immutable upon assignment
        String sql = "UPDATE students SET program=?, year_level=?, section=?, enrollment_status=? WHERE student_id=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getProgram());
            ps.setInt(2, s.getYearLevel());
            ps.setString(3, s.getSection());
            ps.setString(4, s.getEnrollmentStatus());
            ps.setInt(5, s.getStudentId());
            int affected = ps.executeUpdate();
            if (affected == 0 && s.getUserId() > 0) {
                String sqlByUserId = "UPDATE students SET program=?, year_level=?, section=?, enrollment_status=? WHERE user_id=?";
                try (PreparedStatement ps2 = c.prepareStatement(sqlByUserId)) {
                    ps2.setString(1, s.getProgram());
                    ps2.setInt(2, s.getYearLevel());
                    ps2.setString(3, s.getSection());
                    ps2.setString(4, s.getEnrollmentStatus());
                    ps2.setInt(5, s.getUserId());
                    affected = ps2.executeUpdate();
                }
            }
            if (affected == 0) {
                log.warn("UPDATE students affected 0 rows for student_id={}, user_id={}", s.getStudentId(), s.getUserId());
                throw new AppException("Student profile record not found in the database to update.");
            }
            log.info("Successfully committed student update [student_id={}, user_id={}, year={}, section={}]",
                    s.getStudentId(), s.getUserId(), s.getYearLevel(), s.getSection());
        } catch (SQLException e) {
            log.error("Failed to update student profile [student_id={}, user_id={}, SQLState={}, ErrorCode={}]: {}",
                    s.getStudentId(), s.getUserId(), e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new AppException("Failed to update student: " + e.getMessage(), e);
        }
    }

    public void delete(int id) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM students WHERE student_id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new AppException("Failed to delete student", e); }
    }

    private Student map(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setStudentId(rs.getInt("student_id"));
        s.setUserId(rs.getInt("user_id"));
        s.setStudentNumber(rs.getString("student_number"));
        s.setProgram(rs.getString("program"));
        s.setYearLevel(rs.getInt("year_level"));
        s.setSection(rs.getString("section"));
        s.setEnrollmentStatus(rs.getString("enrollment_status"));
        return s;
    }
}
