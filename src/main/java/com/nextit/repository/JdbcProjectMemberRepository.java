package com.nextit.repository;

import com.nextit.config.DatabaseConfig;
import com.nextit.exception.AppException;
import com.nextit.model.ProjectMember;

import java.sql.*;
import java.util.*;

public class JdbcProjectMemberRepository {

    private static volatile Boolean hasContributionNotesColumn = null;

    private static boolean checkColumnExists(Connection c, String colName) {
        if (hasContributionNotesColumn != null) return hasContributionNotesColumn;
        try {
            DatabaseMetaData meta = c.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, "project_members", colName)) {
                if (rs.next()) {
                    hasContributionNotesColumn = true;
                    return true;
                }
            }
            try (ResultSet rs = meta.getColumns(null, null, "PROJECT_MEMBERS", colName.toUpperCase(Locale.ROOT))) {
                if (rs.next()) {
                    hasContributionNotesColumn = true;
                    return true;
                }
            }
            hasContributionNotesColumn = false;
            return false;
        } catch (Exception ignored) {
            hasContributionNotesColumn = false;
            return false;
        }
    }

    public List<ProjectMember> findByProjectId(int projectId) {
        List<ProjectMember> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM project_members WHERE project_id=? ORDER BY project_member_id ASC")) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); return list; }
        } catch (SQLException e) { throw new AppException("Failed to load project members", e); }
    }

    public Map<Integer, List<ProjectMember>> findByProjectIds(Collection<Integer> projectIds) {
        Map<Integer, List<ProjectMember>> map = new HashMap<>();
        if (projectIds == null || projectIds.isEmpty()) return map;
        String inSql = projectIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String sql = "SELECT * FROM project_members WHERE project_id IN (" + inSql + ") ORDER BY project_member_id ASC";
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ProjectMember m = map(rs);
                map.computeIfAbsent(m.getProjectId(), k -> new ArrayList<>()).add(m);
            }
            return map;
        } catch (SQLException e) { throw new AppException("Failed to load project members for projects", e); }
    }

    public java.util.Optional<ProjectMember> findById(int projectMemberId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM project_members WHERE project_member_id=?")) {
            ps.setInt(1, projectMemberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? java.util.Optional.of(map(rs)) : java.util.Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load project member", e); }
    }

    public java.util.Optional<ProjectMember> findByProjectAndStudent(int projectId, int studentId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM project_members WHERE project_id=? AND student_id=?")) {
            ps.setInt(1, projectId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? java.util.Optional.of(map(rs)) : java.util.Optional.empty();
            }
        } catch (SQLException e) { throw new AppException("Failed to load project member", e); }
    }

    public void save(ProjectMember m) {
        try (Connection c = DatabaseConfig.getConnection()) {
            boolean withNotes = checkColumnExists(c, "contribution_notes");
            String sql = withNotes
                ? "INSERT INTO project_members (project_id, student_id, role, contribution, contribution_notes) VALUES (?,?,?,?,?)"
                : "INSERT INTO project_members (project_id, student_id, role, contribution) VALUES (?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, m.getProjectId());
                ps.setInt(2, m.getStudentId());
                ps.setString(3, m.getRole());
                String notes = m.getContributionNotes() != null ? m.getContributionNotes() : m.getContribution();
                ps.setString(4, notes);
                if (withNotes) {
                    ps.setString(5, notes);
                }
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) { if (k.next()) m.setProjectMemberId(k.getInt(1)); }
            }
        } catch (SQLException e) { throw new AppException("Failed to save project member", e); }
    }

    public void update(ProjectMember m) {
        try (Connection c = DatabaseConfig.getConnection()) {
            boolean withNotes = checkColumnExists(c, "contribution_notes");
            String sql = withNotes
                ? "UPDATE project_members SET role=?, contribution=?, contribution_notes=? WHERE project_member_id=?"
                : "UPDATE project_members SET role=?, contribution=? WHERE project_member_id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, m.getRole());
                String notes = m.getContributionNotes() != null ? m.getContributionNotes() : m.getContribution();
                ps.setString(2, notes);
                if (withNotes) {
                    ps.setString(3, notes);
                    ps.setInt(4, m.getProjectMemberId());
                } else {
                    ps.setInt(3, m.getProjectMemberId());
                }
                ps.executeUpdate();
            }
        } catch (SQLException e) { throw new AppException("Failed to update project member", e); }
    }

    public void delete(int id) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM project_members WHERE project_member_id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new AppException("Failed to delete project member", e); }
    }

    public void deleteByProjectId(int projectId) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM project_members WHERE project_id=?")) {
            ps.setInt(1, projectId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new AppException("Failed to delete project members", e); }
    }

    private ProjectMember map(ResultSet rs) throws SQLException {
        ProjectMember m = new ProjectMember();
        m.setProjectMemberId(rs.getInt("project_member_id"));
        m.setProjectId(rs.getInt("project_id"));
        m.setStudentId(rs.getInt("student_id"));
        m.setRole(rs.getString("role"));
        m.setContribution(rs.getString("contribution"));
        try {
            String notes = rs.getString("contribution_notes");
            if (notes != null && !notes.isBlank()) {
                m.setContributionNotes(notes);
            }
        } catch (SQLException ignored) {
            // column not in result set
        }
        return m;
    }
}
