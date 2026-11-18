package com.nextit.service;

import com.nextit.model.SecurityAuditLog;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;
import com.nextit.repository.JdbcSecurityAuditLogRepository;
import com.nextit.repository.SecurityAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);
    private static final SecurityAuditService INSTANCE = new SecurityAuditService();

    private final SecurityAuditLogRepository auditRepo;

    public SecurityAuditService() {
        this(new JdbcSecurityAuditLogRepository());
    }

    public SecurityAuditService(SecurityAuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    public static SecurityAuditService getInstance() {
        return INSTANCE;
    }

    public List<SecurityAuditLog> recentLogs(int limit) {
        try {
            return auditRepo.findAll(limit);
        } catch (Exception e) {
            log.warn("Could not retrieve audit logs: {}", e.getMessage());
            return List.of();
        }
    }

    public void log(Integer userId, String actorUsername, String action, Integer targetUserId,
                    String ipAddress, String details, String severity) {
        try {
            SecurityAuditLog entry = new SecurityAuditLog();
            entry.setUserId(userId);
            entry.setActorUsername(actorUsername != null && !actorUsername.isBlank() ? actorUsername : "SYSTEM");
            entry.setAction(action);
            entry.setTargetUserId(targetUserId);
            entry.setIpAddress(ipAddress != null ? ipAddress : "127.0.0.1");
            entry.setDetails(details);
            entry.setSeverity(severity != null ? severity : "INFO");
            entry.setCreatedAt(LocalDateTime.now());
            auditRepo.save(entry);
        } catch (Exception e) {
            log.error("Failed to write security audit log for action {}: {}", action, e.getMessage());
        }
    }

    public void logLoginSuccess(User user) {
        log(user.getUserId(), user.getUsername(), "LOGIN_SUCCESS", user.getUserId(),
                "127.0.0.1", "Successful authentication for role " + user.getRole(), "INFO");
    }

    public void logLoginFailure(String username, String reason) {
        log(null, username, "LOGIN_FAILED", null,
                "127.0.0.1", "Failed login attempt: " + reason, "WARNING");
    }

    public void logAccountLocked(String username) {
        log(null, username, "ACCOUNT_LOCKED", null,
                "127.0.0.1", "Account temporarily locked after 5 consecutive failed login attempts", "SECURITY_ALERT");
    }

    public void logRegistrationSubmitted(User user, VerificationStatus status, int riskScore, String flags) {
        String details = String.format("Registered as %s (Initial status: %s, Risk Score: %d, Flags: %s)",
                user.getRole(), status, riskScore, (flags == null || flags.isBlank()) ? "NONE" : flags);
        log(user.getUserId(), user.getUsername(), "REGISTRATION_SUBMITTED", user.getUserId(),
                "127.0.0.1", details, riskScore > 50 ? "WARNING" : "INFO");
    }

    public void logVerificationDecision(String actor, int targetUserId, VerificationStatus status, String notes) {
        String sev = (status == VerificationStatus.VERIFIED) ? "INFO" : "WARNING";
        log(null, actor, "VERIFICATION_" + status.name(), targetUserId,
                "127.0.0.1", "Verification status updated to " + status + ". Notes: " + notes, sev);
    }

    public void logSuspension(String actor, int targetUserId, String reason) {
        log(null, actor, "ACCOUNT_SUSPENDED", targetUserId,
                "127.0.0.1", "Account suspended: " + reason, "SECURITY_ALERT");
    }

    public void logReinstated(String actor, int targetUserId) {
        log(null, actor, "ACCOUNT_REINSTATED", targetUserId,
                "127.0.0.1", "Account reinstated to VERIFIED status", "INFO");
    }

    public void logAdminProvisioned(String actor, String newAdminUsername) {
        log(null, actor, "ADMIN_PROVISIONED", null,
                "127.0.0.1", "New administrator account provisioned: @" + newAdminUsername, "SECURITY_ALERT");
    }

    public void logRoleChangeBlocked(String actor, String targetUsername, String requestedRole) {
        log(null, actor, "ROLE_CHANGE_BLOCKED", null,
                "127.0.0.1", "Blocked unauthorized role change attempt for @" + targetUsername + " to " + requestedRole, "CRITICAL");
    }
}
