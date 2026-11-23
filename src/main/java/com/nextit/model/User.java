package com.nextit.model;

import java.time.LocalDateTime;

public class User {
    private int userId;
    private String username;
    private String passwordHash;
    private String email;
    private Role role;
    private String firstName;
    private String lastName;
    private VerificationStatus verificationStatus = VerificationStatus.VERIFIED;
    private String rejectionReason;
    private LocalDateTime verifiedAt;
    private Integer verifiedBy;
    private int failedLoginAttempts = 0;
    private LocalDateTime lockedUntil;
    private boolean mfaEnabled = false;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus != null ? verificationStatus : VerificationStatus.UNVERIFIED;
    }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public Integer getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(Integer verifiedBy) { this.verifiedBy = verifiedBy; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getFullName() { return firstName + " " + lastName; }

    public boolean isVerified() {
        return verificationStatus != VerificationStatus.SUSPENDED;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isSuspended() {
        return verificationStatus == VerificationStatus.SUSPENDED;
    }
}
