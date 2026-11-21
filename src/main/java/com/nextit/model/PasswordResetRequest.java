package com.nextit.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class PasswordResetRequest {
    private UUID requestId;
    private Integer userId;
    private String identifierSubmitted;
    private Role role;
    private String reason;
    private String status; // PENDING, APPROVED_SENT, COMPLETED, REJECTED
    private LocalDateTime requestedAt;
    private LocalDateTime resolvedAt;
    private String adminRemarks;

    // Resolved profile details
    private String username;
    private String userEmail;
    private String fullName;

    public PasswordResetRequest() {
        this.requestId = UUID.randomUUID();
        this.status = "PENDING";
        this.requestedAt = LocalDateTime.now();
    }

    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getIdentifierSubmitted() { return identifierSubmitted; }
    public void setIdentifierSubmitted(String identifierSubmitted) { this.identifierSubmitted = identifierSubmitted; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getAdminRemarks() { return adminRemarks; }
    public void setAdminRemarks(String adminRemarks) { this.adminRemarks = adminRemarks; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
