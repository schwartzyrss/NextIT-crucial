package com.nextit.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Unified model bridging Achievements and Certifications into a single cohesive entity
 * for academic milestone and credential management.
 */
public class AcademicCredential {

    public enum Type {
        ACHIEVEMENT("Achievement", "badge-pill-mint"),
        CERTIFICATION("Certification", "badge-pill-lavender");

        private final String display;
        private final String badgeStyle;

        Type(String display, String badgeStyle) {
            this.display = display;
            this.badgeStyle = badgeStyle;
        }

        public String getDisplay() {
            return display;
        }

        public String getBadgeStyle() {
            return badgeStyle;
        }
    }

    private Type type;
    private int id; // achievementId or certificationId
    private int studentId;
    private String title; // title or certificationName
    private String organization; // organization or issuingOrganization
    private LocalDate date; // achievementDate or issueDate
    private LocalDate expirationDate; // optional, only for certifications
    private String description; // description for achievements / notes
    private String verificationUrl; // verificationUrl or credentialUrl

    private Achievement originalAchievement;
    private Certification originalCertification;

    public AcademicCredential() {
        this.type = Type.ACHIEVEMENT;
    }

    public static AcademicCredential fromAchievement(Achievement a) {
        AcademicCredential c = new AcademicCredential();
        c.setType(Type.ACHIEVEMENT);
        c.setId(a.getAchievementId());
        c.setStudentId(a.getStudentId());
        c.setTitle(a.getTitle());
        c.setOrganization(a.getOrganization());
        c.setDate(a.getAchievementDate());
        c.setDescription(a.getDescription());
        c.setVerificationUrl(a.getVerificationUrl());
        c.setOriginalAchievement(a);
        return c;
    }

    public static AcademicCredential fromCertification(Certification cert) {
        AcademicCredential c = new AcademicCredential();
        c.setType(Type.CERTIFICATION);
        c.setId(cert.getCertificationId());
        c.setStudentId(cert.getStudentId());
        c.setTitle(cert.getCertificationName());
        c.setOrganization(cert.getIssuingOrganization());
        c.setDate(cert.getIssueDate());
        c.setExpirationDate(cert.getExpirationDate());
        c.setDescription(cert.getExpirationDate() != null ? "Expires: " + cert.getExpirationDate() : "No expiration date");
        c.setVerificationUrl(cert.getCredentialUrl());
        c.setOriginalCertification(cert);
        return c;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVerificationUrl() {
        return verificationUrl;
    }

    public void setVerificationUrl(String verificationUrl) {
        this.verificationUrl = verificationUrl;
    }

    public Achievement getOriginalAchievement() {
        return originalAchievement;
    }

    public void setOriginalAchievement(Achievement originalAchievement) {
        this.originalAchievement = originalAchievement;
    }

    public Certification getOriginalCertification() {
        return originalCertification;
    }

    public void setOriginalCertification(Certification originalCertification) {
        this.originalCertification = originalCertification;
    }

    public String getTypeDisplay() {
        return type == null ? "" : type.getDisplay();
    }

    public String getDateDisplay() {
        if (date == null && expirationDate == null) {
            return "—";
        }
        if (type == Type.CERTIFICATION) {
            if (date != null && expirationDate != null) {
                return date + " (Exp: " + expirationDate + ")";
            }
            if (date != null) {
                return date + " (No Expiry)";
            }
            return "Expires: " + expirationDate;
        }
        return date != null ? date.toString() : "—";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AcademicCredential that = (AcademicCredential) o;
        return id == that.id && studentId == that.studentId && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id, studentId);
    }
}
