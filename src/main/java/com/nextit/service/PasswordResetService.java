package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.PasswordResetRequest;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.repository.JdbcPasswordResetRequestRepository;
import com.nextit.repository.JdbcUserRepository;
import com.nextit.repository.PasswordResetRequestRepository;
import com.nextit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final PasswordResetService INSTANCE = new PasswordResetService();

    private final PasswordResetRequestRepository requestRepo;
    private final UserRepository userRepo;
    private final SecurityAuditService auditService;

    public PasswordResetService() {
        this(new JdbcPasswordResetRequestRepository(), new JdbcUserRepository(), SecurityAuditService.getInstance());
    }

    public PasswordResetService(PasswordResetRequestRepository requestRepo, UserRepository userRepo, SecurityAuditService auditService) {
        this.requestRepo = requestRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    public static PasswordResetService getInstance() {
        return INSTANCE;
    }

    /**
     * Submits an administrative password recovery request when self-service OTP is unavailable.
     */
    public PasswordResetRequest submitResetRequest(String identifier, String contactEmail, Role role, String reason) {
        if (identifier == null || identifier.isBlank()) {
            throw new AppException("Please provide your username, student number, or employee ID.");
        }
        if (contactEmail == null || contactEmail.isBlank() || !contactEmail.contains("@")) {
            throw new AppException("Please enter a valid contact email address.");
        }
        if (role == null) {
            throw new AppException("Please select your account role.");
        }
        if (reason == null || reason.isBlank()) {
            throw new AppException("Please explain the reason for requesting manual password assistance.");
        }

        PasswordResetRequest req = new PasswordResetRequest();
        req.setRequestId(UUID.randomUUID());
        req.setIdentifierSubmitted(identifier.trim());
        req.setRole(role);
        req.setReason(reason.trim());
        req.setStatus("PENDING");
        req.setRequestedAt(LocalDateTime.now());

        // Resolve matching user if present
        Optional<User> found = userRepo.findByIdentifier(identifier.trim());
        if (found.isEmpty()) {
            found = userRepo.findByEmail(contactEmail.trim());
        }
        if (found.isPresent()) {
            req.setUserId(found.get().getUserId());
        }

        requestRepo.save(req);
        log.info("Registered password reset assistance request {} for '{}' ({})", req.getRequestId(), identifier, role);

        auditService.log(req.getUserId(), identifier.trim(), "PASSWORD_RESET_ASSISTANCE_REQUESTED",
                req.getUserId(), "127.0.0.1", "Manual reset request submitted: " + reason.trim(), "INFO");

        return req;
    }

    /**
     * Approves a pending password reset request, generates a time-limited OTP code,
     * dispatches it via SMTP, and records resolution.
     */
    public PasswordResetRequest approveAndDispatchReset(UUID requestId, String adminRemarks) {
        if (requestId == null) {
            throw new AppException("Request ID cannot be null.");
        }

        PasswordResetRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Password reset request not found for ID: " + requestId));

        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new AppException("Request cannot be approved; current status is: " + req.getStatus());
        }

        // Validate matching user in database
        User targetUser = null;
        if (req.getUserId() != null) {
            targetUser = userRepo.findById(req.getUserId()).orElse(null);
        }
        if (targetUser == null) {
            targetUser = userRepo.findByIdentifier(req.getIdentifierSubmitted()).orElse(null);
        }
        if (targetUser == null) {
            throw new AppException("No verified NextIT account found matching identifier: " + req.getIdentifierSubmitted());
        }

        // Generate time-limited 6-digit recovery OTP and dispatch via EmailService
        OtpService.OtpResponse otpResp = OtpService.requestPasswordResetOtp(targetUser);

        req.setStatus("APPROVED_SENT");
        req.setResolvedAt(LocalDateTime.now());
        String remarks = (adminRemarks != null && !adminRemarks.isBlank())
                ? adminRemarks.trim()
                : "Approved by coordinator. Recovery code sent to " + AuthenticationService.maskEmail(targetUser.getEmail()) + (otpResp.sent() ? " (via SMTP)" : " (sandbox active)");
        req.setAdminRemarks(remarks);

        requestRepo.update(req);

        auditService.log(targetUser.getUserId(), targetUser.getUsername(), "ADMIN_PASSWORD_RESET_APPROVED",
                targetUser.getUserId(), "127.0.0.1", "Recovery OTP dispatched: " + otpResp.message(), "INFO");

        return req;
    }

    public PasswordResetRequest approveAndDispatchReset(UUID requestId) {
        return approveAndDispatchReset(requestId, null);
    }

    /**
     * Rejects an unverified, duplicate, or spoofed password reset request.
     */
    public PasswordResetRequest rejectRequest(UUID requestId, String rejectionReason) {
        if (requestId == null) {
            throw new AppException("Request ID cannot be null.");
        }

        PasswordResetRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Password reset request not found for ID: " + requestId));

        req.setStatus("REJECTED");
        req.setResolvedAt(LocalDateTime.now());
        String remarks = (rejectionReason != null && !rejectionReason.isBlank())
                ? rejectionReason.trim()
                : "Rejected: Identity could not be confirmed or request details are invalid.";
        req.setAdminRemarks(remarks);

        requestRepo.update(req);

        if (req.getUserId() != null) {
            userRepo.findById(req.getUserId()).ifPresent(u ->
                    auditService.log(u.getUserId(), u.getUsername(), "ADMIN_PASSWORD_RESET_REJECTED",
                            u.getUserId(), "127.0.0.1", "Reset request rejected: " + remarks, "WARNING")
            );
        }

        return req;
    }

    public List<PasswordResetRequest> listPendingRequests() {
        return requestRepo.findByStatus("PENDING");
    }

    public List<PasswordResetRequest> listAllRequests() {
        return requestRepo.findAll();
    }

    public Optional<PasswordResetRequest> findRequest(UUID requestId) {
        return requestRepo.findById(requestId);
    }
}
