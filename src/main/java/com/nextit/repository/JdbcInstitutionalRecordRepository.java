package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.InstitutionalRecord;
import com.nextit.model.Role;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcInstitutionalRecordRepository implements InstitutionalRecordRepository {

    @Override
    public Optional<InstitutionalRecord> findById(int id) {
        String sql = "SELECT * FROM institutional_records WHERE record_id = ?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find institutional record by id", e);
        }
    }

    @Override
    public Optional<InstitutionalRecord> findByIdentifier(String identifierNumber) {
        if (identifierNumber == null || identifierNumber.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM institutional_records WHERE LOWER(identifier_number) = LOWER(?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, identifierNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find institutional record by identifier", e);
        }
    }

    @Override
    public Optional<InstitutionalRecord> findByEmail(String institutionalEmail) {
        if (institutionalEmail == null || institutionalEmail.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM institutional_records WHERE LOWER(institutional_email) = LOWER(?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, institutionalEmail.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AppException("Failed to find institutional record by email", e);
        }
    }

    @Override
    public List<InstitutionalRecord> findAll() {
        String sql = "SELECT * FROM institutional_records ORDER BY record_id DESC";
        List<InstitutionalRecord> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            throw new AppException("Failed to list institutional records", e);
        }
    }

    @Override
    public void save(InstitutionalRecord record) {
        String sql = "INSERT INTO institutional_records (record_type, identifier_number, institutional_email, full_name, department_or_program, is_active) VALUES (?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, record.getRecordType().name());
            ps.setString(2, record.getIdentifierNumber().trim());
            ps.setString(3, record.getInstitutionalEmail().trim().toLowerCase());
            ps.setString(4, record.getFullName().trim());
            ps.setString(5, record.getDepartmentOrProgram().trim());
            ps.setBoolean(6, record.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) record.setRecordId(keys.getInt(1));
            }
        } catch (SQLException e) {
            throw new AppException("Failed to save institutional record: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(InstitutionalRecord record) {
        String sql = "UPDATE institutional_records SET record_type=?, identifier_number=?, institutional_email=?, full_name=?, department_or_program=?, is_active=? WHERE record_id=?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, record.getRecordType().name());
            ps.setString(2, record.getIdentifierNumber().trim());
            ps.setString(3, record.getInstitutionalEmail().trim().toLowerCase());
            ps.setString(4, record.getFullName().trim());
            ps.setString(5, record.getDepartmentOrProgram().trim());
            ps.setBoolean(6, record.isActive());
            ps.setInt(7, record.getRecordId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Failed to update institutional record: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM institutional_records WHERE record_id = ?";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Failed to delete institutional record", e);
        }
    }

    private InstitutionalRecord map(ResultSet rs) throws SQLException {
        InstitutionalRecord r = new InstitutionalRecord();
        r.setRecordId(rs.getInt("record_id"));
        r.setRecordType(Role.valueOf(rs.getString("record_type")));
        r.setIdentifierNumber(rs.getString("identifier_number"));
        r.setInstitutionalEmail(rs.getString("institutional_email"));
        r.setFullName(rs.getString("full_name"));
        r.setDepartmentOrProgram(rs.getString("department_or_program"));
        r.setActive(rs.getBoolean("is_active"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) r.setCreatedAt(created.toLocalDateTime());
        return r;
    }
}
