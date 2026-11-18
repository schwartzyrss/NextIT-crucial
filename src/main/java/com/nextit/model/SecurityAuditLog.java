package com.nextit.model;

import java.time.LocalDateTime;

public class SecurityAuditLog {
    private int logId;
    private Integer userId;
    private String actorUsername;
    private String action;
    private Integer targetUserId;
    private String ipAddress;
    private String details;
    private String severity = "INFO";
    private LocalDateTime createdAt;

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Integer targetUserId) { this.targetUserId = targetUserId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity != null ? severity : "INFO"; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
