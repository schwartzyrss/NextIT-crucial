package com.nextit.model;

import java.time.LocalDateTime;

/**
 * Model representing a Faculty Recommendation Letter endorsing a student
 * for an Internship or Employment Opportunity.
 *
 * Supported Statuses:
 * - REQUESTED: Student has requested a recommendation from an instructor
 * - ISSUED: Instructor has composed, signed, and issued the recommendation letter
 * - ATTACHED: The issued recommendation letter is attached to a student's job/internship application
 */
public class RecommendationLetter {

    private int letterId;
    private int studentId;
    private int instructorId;
    private Integer opportunityId; // Nullable: targeted to a specific opportunity or general endorsement
    private Integer applicationId; // Nullable: attached to an application
    private String letterTitle;
    private String recommendationBody;
    private String endorsementHighlights; // Key verified skills, capstone traits, achievements
    private String status; // REQUESTED, ISSUED, ATTACHED
    private LocalDateTime createdAt;
    private LocalDateTime issuedAt;

    public RecommendationLetter() {
        this.status = "REQUESTED";
        this.createdAt = LocalDateTime.now();
    }

    public int getLetterId() {
        return letterId;
    }

    public void setLetterId(int letterId) {
        this.letterId = letterId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(int instructorId) {
        this.instructorId = instructorId;
    }

    public Integer getOpportunityId() {
        return opportunityId;
    }

    public void setOpportunityId(Integer opportunityId) {
        this.opportunityId = opportunityId;
    }

    public Integer getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Integer applicationId) {
        this.applicationId = applicationId;
    }

    public String getLetterTitle() {
        return letterTitle;
    }

    public void setLetterTitle(String letterTitle) {
        this.letterTitle = letterTitle;
    }

    public String getRecommendationBody() {
        return recommendationBody;
    }

    public void setRecommendationBody(String recommendationBody) {
        this.recommendationBody = recommendationBody;
    }

    public String getEndorsementHighlights() {
        return endorsementHighlights;
    }

    public void setEndorsementHighlights(String endorsementHighlights) {
        this.endorsementHighlights = endorsementHighlights;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public boolean isIssued() {
        return "ISSUED".equalsIgnoreCase(status) || "ATTACHED".equalsIgnoreCase(status);
    }

    public boolean isAttached() {
        return "ATTACHED".equalsIgnoreCase(status);
    }

    public boolean isPendingRequest() {
        return "REQUESTED".equalsIgnoreCase(status);
    }
}
