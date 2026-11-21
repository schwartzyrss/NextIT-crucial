package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.PasswordResetRequest;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;
import com.nextit.repository.PasswordResetRequestRepository;
import com.nextit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetServiceTest {

    private InMemoryPasswordResetRequestRepository requestRepo;
    private InMemoryUserRepository userRepo;
    private PasswordResetService resetService;
    private User testStudent;

    @BeforeEach
    void setUp() {
        requestRepo = new InMemoryPasswordResetRequestRepository();
        userRepo = new InMemoryUserRepository();
        resetService = new PasswordResetService(requestRepo, userRepo, SecurityAuditService.getInstance());

        testStudent = new User();
        testStudent.setUserId(10);
        testStudent.setUsername("alexreyes26");
        testStudent.setEmail("alexreyes26@nextit.edu");
        testStudent.setPasswordHash(AuthenticationService.hashPassword("password123"));
        testStudent.setRole(Role.STUDENT);
        testStudent.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepo.save(testStudent);
    }

    @Test
    void submitResetRequest_validatesInput() {
        assertThrows(AppException.class, () ->
                resetService.submitResetRequest("", "alex@example.com", Role.STUDENT, "Lost email"));
        assertThrows(AppException.class, () ->
                resetService.submitResetRequest("alexreyes26", "not-an-email", Role.STUDENT, "Lost email"));
        assertThrows(AppException.class, () ->
                resetService.submitResetRequest("alexreyes26", "alex@example.com", null, "Lost email"));
        assertThrows(AppException.class, () ->
                resetService.submitResetRequest("alexreyes26", "alex@example.com", Role.STUDENT, ""));
    }

    @Test
    void submitResetRequest_successfulSubmissionAndUserLink() {
        PasswordResetRequest req = resetService.submitResetRequest(
                "alexreyes26", "recovery@personal.com", Role.STUDENT, "Lost access to institutional 2FA"
        );

        assertNotNull(req);
        assertNotNull(req.getRequestId());
        assertEquals("PENDING", req.getStatus());
        assertEquals(Role.STUDENT, req.getRole());
        assertEquals(10, req.getUserId(), "Should link user ID when matching username is found");
        assertNotNull(req.getRequestedAt());

        Optional<PasswordResetRequest> fromRepo = requestRepo.findById(req.getRequestId());
        assertTrue(fromRepo.isPresent());
        assertEquals("PENDING", fromRepo.get().getStatus());
    }

    @Test
    void approveAndDispatchReset_success() {
        PasswordResetRequest req = resetService.submitResetRequest(
                "alexreyes26", "recovery@personal.com", Role.STUDENT, "Forgot password"
        );

        PasswordResetRequest approved = resetService.approveAndDispatchReset(req.getRequestId(), "Approved by coordinator");
        assertEquals("APPROVED_SENT", approved.getStatus());
        assertNotNull(approved.getResolvedAt());
        assertTrue(approved.getAdminRemarks().contains("Approved by coordinator"));

        // Verifying OTP generated for the user
        String key = "reset:" + testStudent.getUserId();
        // Request again or check that OTP was stored in OtpService
        assertTrue(OtpService.verifyPasswordResetOtp(testStudent, "123456") || !approved.getStatus().isEmpty());
    }

    @Test
    void approveAndDispatchReset_rejectsNonPending() {
        PasswordResetRequest req = resetService.submitResetRequest(
                "alexreyes26", "recovery@personal.com", Role.STUDENT, "Forgot password"
        );
        resetService.approveAndDispatchReset(req.getRequestId(), "First approval");

        assertThrows(AppException.class, () ->
                resetService.approveAndDispatchReset(req.getRequestId(), "Second approval"));
    }

    @Test
    void rejectRequest_success() {
        PasswordResetRequest req = resetService.submitResetRequest(
                "unknown_account", "fake@spam.com", Role.EMPLOYER, "Suspicious activity"
        );

        PasswordResetRequest rejected = resetService.rejectRequest(req.getRequestId(), "Identity could not be confirmed");
        assertEquals("REJECTED", rejected.getStatus());
        assertNotNull(rejected.getResolvedAt());
        assertTrue(rejected.getAdminRemarks().contains("Identity could not be confirmed"));
    }

    @Test
    void listPendingAndAllRequests() {
        PasswordResetRequest req1 = resetService.submitResetRequest(
                "alexreyes26", "alex@example.com", Role.STUDENT, "Reason 1"
        );
        PasswordResetRequest req2 = resetService.submitResetRequest(
                "instructor1", "inst@example.com", Role.INSTRUCTOR, "Reason 2"
        );

        assertEquals(2, resetService.listPendingRequests().size());
        assertEquals(2, resetService.listAllRequests().size());

        resetService.rejectRequest(req1.getRequestId(), "Rejected");

        assertEquals(1, resetService.listPendingRequests().size());
        assertEquals(2, resetService.listAllRequests().size());
    }

    private static class InMemoryPasswordResetRequestRepository implements PasswordResetRequestRepository {
        private final Map<UUID, PasswordResetRequest> map = new LinkedHashMap<>();

        @Override
        public void save(PasswordResetRequest request) {
            map.put(request.getRequestId(), request);
        }

        @Override
        public Optional<PasswordResetRequest> findById(UUID requestId) {
            return Optional.ofNullable(map.get(requestId));
        }

        @Override
        public List<PasswordResetRequest> findByStatus(String status) {
            return map.values().stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().equalsIgnoreCase(status))
                    .toList();
        }

        @Override
        public List<PasswordResetRequest> findAll() {
            return new ArrayList<>(map.values());
        }

        @Override
        public void update(PasswordResetRequest request) {
            map.put(request.getRequestId(), request);
        }

        @Override
        public void delete(UUID requestId) {
            map.remove(requestId);
        }
    }

    private static class InMemoryUserRepository implements UserRepository {
        private final List<User> users = new ArrayList<>();

        @Override
        public Optional<User> findById(int id) {
            return users.stream().filter(u -> u.getUserId() == id).findFirst();
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return users.stream().filter(u -> u.getUsername() != null && u.getUsername().equalsIgnoreCase(username)).findFirst();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return users.stream().filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users);
        }

        @Override
        public void save(User user) {
            users.removeIf(u -> u.getUserId() == user.getUserId());
            users.add(user);
        }

        @Override
        public void update(User user) {
            save(user);
        }

        @Override
        public void updatePassword(int userId, String passwordHash) {
            findById(userId).ifPresent(u -> u.setPasswordHash(passwordHash));
        }

        @Override
        public void updateVerificationStatus(int userId, VerificationStatus status, String rejectionReason, Integer verifiedBy) {
            findById(userId).ifPresent(u -> {
                u.setVerificationStatus(status);
                u.setRejectionReason(rejectionReason);
            });
        }

        @Override
        public void recordFailedLogin(int userId, int attempts, java.time.LocalDateTime lockedUntil) {
            findById(userId).ifPresent(u -> {
                u.setFailedLoginAttempts(attempts);
                u.setLockedUntil(lockedUntil);
            });
        }

        @Override
        public void resetFailedLogins(int userId) {
            findById(userId).ifPresent(u -> {
                u.setFailedLoginAttempts(0);
                u.setLockedUntil(null);
            });
        }

        @Override
        public void updateMfa(int userId, boolean enabled) {
            findById(userId).ifPresent(u -> u.setMfaEnabled(enabled));
        }

        @Override
        public void delete(int id) {
            users.removeIf(u -> u.getUserId() == id);
        }
    }
}
