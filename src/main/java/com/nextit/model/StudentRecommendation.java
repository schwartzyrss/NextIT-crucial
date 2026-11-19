package com.nextit.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a candidate scored and ranked by the NextIT Student Recommendation Engine.
 */
public class StudentRecommendation {

    private Student student;
    private User user;
    private Portfolio portfolio;
    private JobOpportunity opportunity;

    private int matchScore; // 0 - 100%
    private double skillsScore; // out of 45.0
    private double projectsScore; // out of 25.0
    private double credentialsScore; // out of 20.0
    private double readinessScore; // out of 10.0

    private List<String> matchedSkills = new ArrayList<>();
    private List<String> missingSkills = new ArrayList<>();
    private List<String> allStudentSkills = new ArrayList<>();
    private List<Project> relevantProjects = new ArrayList<>();
    private List<AcademicCredential> relevantCredentials = new ArrayList<>();

    private String matchReason;
    private boolean identityVerified;
    private boolean hasApplied;
    private String applicationStatus;

    public StudentRecommendation() {}

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public int getStudentId() { return student != null ? student.getStudentId() : 0; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }

    public JobOpportunity getOpportunity() { return opportunity; }
    public void setOpportunity(JobOpportunity opportunity) { this.opportunity = opportunity; }

    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }

    public double getSkillsScore() { return skillsScore; }
    public void setSkillsScore(double skillsScore) { this.skillsScore = skillsScore; }

    public double getProjectsScore() { return projectsScore; }
    public void setProjectsScore(double projectsScore) { this.projectsScore = projectsScore; }

    public double getCredentialsScore() { return credentialsScore; }
    public void setCredentialsScore(double credentialsScore) { this.credentialsScore = credentialsScore; }

    public double getReadinessScore() { return readinessScore; }
    public void setReadinessScore(double readinessScore) { this.readinessScore = readinessScore; }

    public List<String> getMatchedSkills() { return matchedSkills; }
    public void setMatchedSkills(List<String> matchedSkills) { this.matchedSkills = matchedSkills; }

    public List<String> getMissingSkills() { return missingSkills; }
    public void setMissingSkills(List<String> missingSkills) { this.missingSkills = missingSkills; }

    public List<String> getAllStudentSkills() { return allStudentSkills; }
    public void setAllStudentSkills(List<String> allStudentSkills) { this.allStudentSkills = allStudentSkills; }

    public List<Project> getRelevantProjects() { return relevantProjects; }
    public void setRelevantProjects(List<Project> relevantProjects) { this.relevantProjects = relevantProjects; }

    public List<AcademicCredential> getRelevantCredentials() { return relevantCredentials; }
    public void setRelevantCredentials(List<AcademicCredential> relevantCredentials) { this.relevantCredentials = relevantCredentials; }

    public String getMatchReason() { return matchReason; }
    public void setMatchReason(String matchReason) { this.matchReason = matchReason; }

    public boolean isIdentityVerified() { return identityVerified; }
    public void setIdentityVerified(boolean identityVerified) { this.identityVerified = identityVerified; }

    public boolean isHasApplied() { return hasApplied; }
    public void setHasApplied(boolean hasApplied) { this.hasApplied = hasApplied; }

    public String getApplicationStatus() { return applicationStatus; }
    public void setApplicationStatus(String applicationStatus) { this.applicationStatus = applicationStatus; }

    public String getStudentName() {
        if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return student != null ? "Student #" + student.getStudentId() : "Candidate";
    }

    public String getProgramAndYear() {
        if (student == null) return "Information Technology";
        String prog = student.getProgram() != null && !student.getProgram().isBlank() ? student.getProgram() : "BSIT";
        int year = student.getYearLevel();
        if (year > 0) {
            String suffix = switch (year) {
                case 1 -> "1st Year";
                case 2 -> "2nd Year";
                case 3 -> "3rd Year";
                case 4 -> "4th Year";
                default -> year + "th Year";
            };
            return prog + " • " + suffix;
        }
        return prog;
    }

    public String getMatchScoreBadgeStyle() {
        if (matchScore >= 80) return "badge-pill-mint";
        if (matchScore >= 60) return "badge-pill-yellow";
        if (matchScore >= 40) return "badge-pill-lavender";
        return "badge-pill-cream";
    }
}
