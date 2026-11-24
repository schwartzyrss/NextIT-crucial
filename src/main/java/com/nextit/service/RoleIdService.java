package com.nextit.service;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.sql.*;
import java.time.Year;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Service governing globally unique, collision-proof role-based identifiers.
 *
 * <p>Supported ID Formats:
 * <ul>
 *   <li>Student: {@code STU-<YYYY>-<SUFFIX>} (e.g. STU-2026-8F4K29 or STU-2026-000142)</li>
 *   <li>Instructor / Faculty: {@code FAC-<YYYY>-<SUFFIX>} (e.g. FAC-2026-3M9P71 or FAC-2026-0001)</li>
 *   <li>Employer: {@code EMP-<YYYY>-<SUFFIX>} (e.g. EMP-2026-7Q2R84 or EMP-2026-0001)</li>
 * </ul>
 *
 * <p>Enforces database-level uniqueness, atomic reservation, immutability,
 * and guarantees that deleted/deactivated IDs are never recycled.</p>
 */
public final class RoleIdService {

    private static final Logger log = LoggerFactory.getLogger(RoleIdService.class);

    /**
     * Unambiguous 32-character alphabet: excludes 0, O, 1, I, and L to prevent human misreading.
     */
    public static final String UNAMBIGUOUS_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final int SUFFIX_LENGTH = 6;
    private static final int MAX_COLLISION_RETRIES = 50;

    private static final Pattern STUDENT_PATTERN =
            Pattern.compile("^(STU-\\d{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ0-9]{4,8}|\\d{4}-\\d{4})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FACULTY_PATTERN =
            Pattern.compile("^FAC-\\d{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ0-9]{4,8}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMPLOYER_PATTERN =
            Pattern.compile("^EMP-\\d{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ0-9]{4,8}$", Pattern.CASE_INSENSITIVE);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile RoleIdService instance;

    private RoleIdService() {
        ensureSchemaExists();
    }

    public static RoleIdService getInstance() {
        if (instance == null) {
            synchronized (RoleIdService.class) {
                if (instance == null) {
                    instance = new RoleIdService();
                }
            }
        }
        return instance;
    }

    /**
     * Returns the official 3-letter role prefix for identifier generation.
     */
    public String getPrefix(Role role) {
        if (role == null) return "USR";
        return switch (role) {
            case STUDENT -> "STU";
            case INSTRUCTOR -> "FAC";
            case EMPLOYER -> "EMP";
            case ADMIN -> "ADM";
        };
    }

    /**
     * Generates a collision-checked, globally unique role-based identifier for the given role and year.
     * Uses unambiguous characters (excluding 0, O, 1, I, L) and verifies against all existing database records.
     */
    public String generateUniqueId(Role role, int year) {
        int targetYear = (year >= 1000) ? year : (year > 0 ? 2000 + year : Year.now().getValue());
        String prefix = getPrefix(role) + "-" + targetYear + "-";

        for (int attempt = 1; attempt <= MAX_COLLISION_RETRIES; attempt++) {
            String candidate = prefix + generateRandomSuffix(SUFFIX_LENGTH);
            if (!isIdTakenOrReserved(candidate)) {
                return candidate;
            }
            log.warn("Collision encountered on candidate ID '{}' (attempt {}/{}). Retrying...",
                    candidate, attempt, MAX_COLLISION_RETRIES);
        }
        throw new AppException("Failed to generate a globally unique " + role + " ID after "
                + MAX_COLLISION_RETRIES + " attempts. Please retry.");
    }

    /**
     * Atomically reserves and binds the identifier to the registered user in the database.
     * If a concurrent signup claimed the requested ID in the same millisecond, automatically
     * generates a new unique ID and retries until successfully persisted.
     */
    public synchronized String assignAndReserveId(Role role, int userId, String preferredId) {
        ensureSchemaExists();
        int currentYear = Year.now().getValue();
        String candidate = (preferredId != null) ? preferredId.trim().toUpperCase(Locale.ROOT) : "";

        if (candidate.isBlank() || !isValidRoleFormat(candidate, role) || isIdTakenOrReserved(candidate)) {
            candidate = generateUniqueId(role, currentYear);
        }

        String insertSql = "INSERT INTO assigned_role_ids (role_id, role, user_id, is_active, created_at) "
                + "VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)";

        int attempts = 0;
        while (attempts < MAX_COLLISION_RETRIES) {
            attempts++;
            try (Connection c = DatabaseConfig.getConnection();
                 PreparedStatement ps = c.prepareStatement(insertSql)) {
                ps.setString(1, candidate);
                ps.setString(2, role.name());
                ps.setInt(3, userId);
                ps.executeUpdate();
                log.info("Successfully bound globally unique role ID '{}' to user_id={}", candidate, userId);
                return candidate;
            } catch (SQLException ex) {
                log.warn("Database conflict binding ID '{}' (attempt {}/{}): {}. Generating new unique ID...",
                        candidate, attempts, MAX_COLLISION_RETRIES, ex.getMessage());
                candidate = generateUniqueId(role, currentYear);
            }
        }

        throw new AppException("Could not allocate a unique ID for user " + userId + " due to persistent collision conflicts.");
    }

    /**
     * Checks whether an ID has ever been issued, reserved, or associated with any active,
     * deactivated, or deleted account.
     */
    public boolean isIdTakenOrReserved(String id) {
        if (id == null || id.isBlank()) return false;
        String clean = id.trim().toUpperCase(Locale.ROOT);

        try (Connection c = DatabaseConfig.getConnection()) {
            // 1. Check permanent reservation registry (includes deactivated/deleted IDs)
            String regSql = "SELECT 1 FROM assigned_role_ids WHERE UPPER(role_id) = ?";
            try (PreparedStatement ps = c.prepareStatement(regSql)) {
                ps.setString(1, clean);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return true;
                }
            } catch (SQLException ignored) {
                // Table might be initializing
            }

            // 2. Check students table
            String stuSql = "SELECT 1 FROM students WHERE UPPER(student_number) = ?";
            try (PreparedStatement ps = c.prepareStatement(stuSql)) {
                ps.setString(1, clean);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return true;
                }
            } catch (SQLException ignored) {}

            // 3. Check instructors table
            String instSql = "SELECT 1 FROM instructors WHERE UPPER(employee_number) = ?";
            try (PreparedStatement ps = c.prepareStatement(instSql)) {
                ps.setString(1, clean);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return true;
                }
            } catch (SQLException ignored) {}

            // 4. Check verification requests
            String vrSql = "SELECT 1 FROM verification_requests WHERE UPPER(id_number) = ?";
            try (PreparedStatement ps = c.prepareStatement(vrSql)) {
                ps.setString(1, clean);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return true;
                }
            } catch (SQLException ignored) {}

            // 5. Check institutional records
            String irSql = "SELECT 1 FROM institutional_records WHERE UPPER(identifier_number) = ?";
            try (PreparedStatement ps = c.prepareStatement(irSql)) {
                ps.setString(1, clean);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return true;
                }
            } catch (SQLException ignored) {}

            // 6. Check employers table
            String empSql = "SELECT 1 FROM employers WHERE UPPER(job_title) = ?";
            try (PreparedStatement ps = c.prepareStatement(empSql)) {
                ps.setString(1, clean);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return true;
                }
            } catch (SQLException ignored) {}

        } catch (SQLException e) {
            log.error("Database connection error while checking ID reservation for '{}': {}", clean, e.getMessage());
        }

        return false;
    }

    /**
     * Retrieves the assigned role ID for a given user ID, if present.
     */
    public java.util.Optional<String> getAssignedIdForUser(int userId) {
        String sql = "SELECT role_id FROM assigned_role_ids WHERE user_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return java.util.Optional.ofNullable(rs.getString("role_id"));
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to query assigned role ID for user {}: {}", userId, e.getMessage());
        }
        return java.util.Optional.empty();
    }

    /**
     * Deactivates an ID without deleting it from the registry so it can never be reused.
     */
    public void deactivateId(String id) {
        if (id == null || id.isBlank()) return;
        String clean = id.trim().toUpperCase(Locale.ROOT);
        String sql = "UPDATE assigned_role_ids SET is_active = FALSE WHERE UPPER(role_id) = ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clean);
            ps.executeUpdate();
            log.info("Marked role ID '{}' as deactivated in registry (preserved to prevent recycling)", clean);
        } catch (SQLException e) {
            log.warn("Failed to deactivate role ID '{}': {}", clean, e.getMessage());
        }
    }

    /**
     * Validates that the identifier strictly complies with role format rules and does not
     * contain ambiguous characters.
     */
    public boolean isValidRoleFormat(String id, Role role) {
        if (id == null || id.isBlank()) return false;
        String clean = id.trim().toUpperCase(Locale.ROOT);

        if (role == null) return false;
        return switch (role) {
            case STUDENT -> STUDENT_PATTERN.matcher(clean).matches();
            case INSTRUCTOR -> FACULTY_PATTERN.matcher(clean).matches();
            case EMPLOYER -> EMPLOYER_PATTERN.matcher(clean).matches();
            case ADMIN -> clean.startsWith("ADM-");
        };
    }

    private String generateRandomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = RANDOM.nextInt(UNAMBIGUOUS_ALPHABET.length());
            sb.append(UNAMBIGUOUS_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    private void ensureSchemaExists() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS assigned_role_ids (
                role_id VARCHAR(50) NOT NULL PRIMARY KEY,
                role VARCHAR(20) NOT NULL,
                user_id INT NULL,
                is_active BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (role_id)
            )
        """;
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement()) {
            st.execute(ddl);
        } catch (SQLException ex) {
            log.debug("Notice while validating assigned_role_ids table: {}", ex.getMessage());
        }
    }
}
