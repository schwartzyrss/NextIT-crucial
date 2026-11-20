package com.nextit.model;

public class ProjectMember {
    private int projectMemberId;
    private int projectId;
    private int studentId;
    private String role;
    private String contribution;
    private String contributionNotes;
    private String roleDescription;

    public ProjectMember() {}

    public ProjectMember(int projectId, int studentId, String role, String contributionNotes) {
        this.projectId = projectId;
        this.studentId = studentId;
        this.role = role;
        this.roleDescription = role;
        this.contribution = contributionNotes;
        this.contributionNotes = contributionNotes;
    }

    public int getProjectMemberId() { return projectMemberId; }
    public void setProjectMemberId(int projectMemberId) { this.projectMemberId = projectMemberId; }

    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getRole() { return role != null ? role : roleDescription; }
    public void setRole(String role) { 
        this.role = role; 
        if (this.roleDescription == null) this.roleDescription = role;
    }

    public String getRoleDescription() { return roleDescription != null ? roleDescription : role; }
    public void setRoleDescription(String roleDescription) { 
        this.roleDescription = roleDescription; 
        if (this.role == null) this.role = roleDescription;
    }

    public String getContribution() { return contribution != null ? contribution : contributionNotes; }
    public void setContribution(String contribution) { 
        this.contribution = contribution; 
        if (this.contributionNotes == null) this.contributionNotes = contribution;
    }

    public String getContributionNotes() { return contributionNotes != null ? contributionNotes : contribution; }
    public void setContributionNotes(String contributionNotes) { 
        this.contributionNotes = contributionNotes; 
        if (this.contribution == null) this.contribution = contributionNotes;
    }
}

