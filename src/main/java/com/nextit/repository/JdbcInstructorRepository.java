package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Instructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcInstructorRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcInstructorRepository.class);

    public Optional<Instructor> findById(int instructorId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM instructors WHERE instructor_id=?")) {
            ps.setInt(1, instructorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load instructor", e); }
    }

    public Optional<Instructor> findByUserId(int userId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM instructors WHERE user_id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load instructor", e); }
    }

    public Optional<Instructor> findByEmployeeNumber(String employeeNumber) {
        if (employeeNumber == null || employeeNumber.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM instructors WHERE LOWER(TRIM(employee_number))=LOWER(TRIM(?))";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load instructor by employee number", e); }
    }

    public String generateNextFacultyNumber(int year) {
        return com.nextit.service.RoleIdService.getInstance().generateUniqueId(com.nextit.model.Role.INSTRUCTOR, year);
    }

    public List<Instructor> findAll() {
        List<Instructor> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM instructors")) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) { throw new AppException("Failed to load instructors", e); }
    }

    public void save(Instructor i) {
        String sql = "INSERT INTO instructors (user_id, employee_number, department, specialization) VALUES (?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, i.getUserId());
            ps.setString(2, i.getEmployeeNumber());
            ps.setString(3, i.getDepartment());
            ps.setString(4, i.getSpecialization());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) i.setInstructorId(k.getInt(1));
            }
            if (i.getInstructorId() <= 0 && i.getUserId() > 0) {
                findByUserId(i.getUserId()).ifPresent(found -> i.setInstructorId(found.getInstructorId()));
            }
            log.info("Saved instructor profile [instructor_id={}, user_id={}, employeeNumber={}]",
                    i.getInstructorId(), i.getUserId(), i.getEmployeeNumber());
        } catch (SQLException e) {
            log.error("Failed to save instructor profile [user_id={}, employeeNumber={}, SQLState={}, ErrorCode={}]: {}",
                    i.getUserId(), i.getEmployeeNumber(), e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new AppException("Failed to save instructor: " + e.getMessage(), e);
        }
    }

    public void update(Instructor i) {
        // employee_number is strictly immutable upon assignment
        String sql = "UPDATE instructors SET department=?, specialization=? WHERE instructor_id=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, i.getDepartment());
            ps.setString(2, i.getSpecialization());
            ps.setInt(3, i.getInstructorId());
            int affected = ps.executeUpdate();
            if (affected == 0 && i.getUserId() > 0) {
                String sqlByUserId = "UPDATE instructors SET department=?, specialization=? WHERE user_id=?";
                try (PreparedStatement ps2 = c.prepareStatement(sqlByUserId)) {
                    ps2.setString(1, i.getDepartment());
                    ps2.setString(2, i.getSpecialization());
                    ps2.setInt(3, i.getUserId());
                    affected = ps2.executeUpdate();
                }
            }
            if (affected == 0) {
                log.warn("UPDATE instructors affected 0 rows for instructor_id={}, user_id={}", i.getInstructorId(), i.getUserId());
                throw new AppException("Instructor profile record not found in the database to update.");
            }
            log.info("Successfully committed instructor update [instructor_id={}, user_id={}, department={}]",
                    i.getInstructorId(), i.getUserId(), i.getDepartment());
        } catch (SQLException e) {
            log.error("Failed to update instructor profile [instructor_id={}, user_id={}, SQLState={}, ErrorCode={}]: {}",
                    i.getInstructorId(), i.getUserId(), e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new AppException("Failed to update instructor: " + e.getMessage(), e);
        }
    }

    private Instructor map(ResultSet rs) throws SQLException {
        Instructor i = new Instructor();
        i.setInstructorId(rs.getInt("instructor_id"));
        i.setUserId(rs.getInt("user_id"));
        i.setEmployeeNumber(rs.getString("employee_number"));
        i.setDepartment(rs.getString("department"));
        i.setSpecialization(rs.getString("specialization"));
        return i;
    }
}
