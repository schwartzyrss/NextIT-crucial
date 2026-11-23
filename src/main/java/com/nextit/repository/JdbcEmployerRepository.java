package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Employer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcEmployerRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcEmployerRepository.class);

    public Optional<Employer> findById(int employerId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM employers WHERE employer_id=?")) {
            ps.setInt(1, employerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load employer", e); }
    }

    public Optional<Employer> findByUserId(int userId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM employers WHERE user_id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load employer", e); }
    }

    public List<Employer> findAll() {
        List<Employer> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM employers")) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) { throw new AppException("Failed to load employers", e); }
    }

    public void save(Employer e0) {
        String sql = "INSERT INTO employers (user_id, company_name, industry, contact_person, contact_number, address, job_title) VALUES (?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, e0.getUserId());
            ps.setString(2, e0.getCompanyName());
            ps.setString(3, e0.getIndustry());
            ps.setString(4, e0.getContactPerson());
            ps.setString(5, e0.getContactNumber());
            ps.setString(6, e0.getAddress());
            ps.setString(7, e0.getJobTitle());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) e0.setEmployerId(k.getInt(1));
            }
            if (e0.getEmployerId() <= 0 && e0.getUserId() > 0) {
                findByUserId(e0.getUserId()).ifPresent(found -> e0.setEmployerId(found.getEmployerId()));
            }
            log.info("Saved employer profile [employer_id={}, user_id={}, company={}]",
                    e0.getEmployerId(), e0.getUserId(), e0.getCompanyName());
        } catch (SQLException e) {
            log.error("Failed to save employer profile [user_id={}, company={}, SQLState={}, ErrorCode={}]: {}",
                    e0.getUserId(), e0.getCompanyName(), e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new AppException("Failed to save employer: " + e.getMessage(), e);
        }
    }

    public void update(Employer e0) {
        String sql = "UPDATE employers SET company_name=?, industry=?, contact_person=?, contact_number=?, address=?, job_title=? WHERE employer_id=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e0.getCompanyName());
            ps.setString(2, e0.getIndustry());
            ps.setString(3, e0.getContactPerson());
            ps.setString(4, e0.getContactNumber());
            ps.setString(5, e0.getAddress());
            ps.setString(6, e0.getJobTitle());
            ps.setInt(7, e0.getEmployerId());
            int affected = ps.executeUpdate();
            if (affected == 0 && e0.getUserId() > 0) {
                String sqlByUserId = "UPDATE employers SET company_name=?, industry=?, contact_person=?, contact_number=?, address=?, job_title=? WHERE user_id=?";
                try (PreparedStatement ps2 = c.prepareStatement(sqlByUserId)) {
                    ps2.setString(1, e0.getCompanyName());
                    ps2.setString(2, e0.getIndustry());
                    ps2.setString(3, e0.getContactPerson());
                    ps2.setString(4, e0.getContactNumber());
                    ps2.setString(5, e0.getAddress());
                    ps2.setString(6, e0.getJobTitle());
                    ps2.setInt(7, e0.getUserId());
                    affected = ps2.executeUpdate();
                }
            }
            if (affected == 0) {
                log.warn("UPDATE employers affected 0 rows for employer_id={}, user_id={}", e0.getEmployerId(), e0.getUserId());
                throw new AppException("Employer profile record not found in the database to update.");
            }
            log.info("Successfully committed employer update [employer_id={}, user_id={}, company={}]",
                    e0.getEmployerId(), e0.getUserId(), e0.getCompanyName());
        } catch (SQLException e) {
            log.error("Failed to update employer profile [employer_id={}, user_id={}, SQLState={}, ErrorCode={}]: {}",
                    e0.getEmployerId(), e0.getUserId(), e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new AppException("Failed to update employer: " + e.getMessage(), e);
        }
    }

    private Employer map(ResultSet rs) throws SQLException {
        Employer e0 = new Employer();
        e0.setEmployerId(rs.getInt("employer_id"));
        e0.setUserId(rs.getInt("user_id"));
        e0.setCompanyName(rs.getString("company_name"));
        e0.setIndustry(rs.getString("industry"));
        e0.setContactPerson(rs.getString("contact_person"));
        e0.setContactNumber(rs.getString("contact_number"));
        e0.setAddress(rs.getString("address"));
        try {
            e0.setJobTitle(rs.getString("job_title"));
        } catch (SQLException ignored) {}
        return e0;
    }
}
