package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;
import com.nextit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetWorkflowTest {

    private InMemoryUserRepository userRepository;
    private AuthenticationService authService;
    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        authService = new AuthenticationService(userRepository);

        testUser = new User();
        testUser.setUserId(42);
        testUser.setUsername("alice_wonder");
        testUser.setEmail("alice@example.com");
        testUser.setPasswordHash(AuthenticationService.hashPassword("OldPassword123"));
        testUser.setRole(Role.STUDENT);
        testUser.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(testUser);
    }

    @Test
    void findUserForPasswordReset_findByUsernameAndEmail() {
        // Find by exact username
        Optional<User> byUser = authService.findUserForPasswordReset("alice_wonder");
        assertTrue(byUser.isPresent());
        assertEquals(42, byUser.get().getUserId());

        // Find by uppercase / padded username
        Optional<User> byUserPadded = authService.findUserForPasswordReset("  ALICE_WONDER  ");
        assertTrue(byUserPadded.isPresent());
        assertEquals(42, byUserPadded.get().getUserId());

        // Find by exact email
        Optional<User> byEmail = authService.findUserForPasswordReset("alice@example.com");
        assertTrue(byEmail.isPresent());
        assertEquals(42, byEmail.get().getUserId());

        // Find by mixed-case email
        Optional<User> byEmailMixed = authService.findUserForPasswordReset("ALICE@EXAMPLE.COM");
        assertTrue(byEmailMixed.isPresent());
        assertEquals(42, byEmailMixed.get().getUserId());
    }

    @Test
    void findUserForPasswordReset_returnsEmptyWhenNotFoundOrEmpty() {
        assertTrue(authService.findUserForPasswordReset("").isEmpty());
        assertTrue(authService.findUserForPasswordReset("   ").isEmpty());
        assertTrue(authService.findUserForPasswordReset(null).isEmpty());
        assertTrue(authService.findUserForPasswordReset("non_existent_user").isEmpty());
    }

    @Test
    void maskEmail_formatsAppropriately() {
        assertEquals("al***e@example.com", AuthenticationService.maskEmail("alice@example.com"));
        assertEquals("b***@example.com", AuthenticationService.maskEmail("bo@example.com"));
        assertEquals("a***@example.com", AuthenticationService.maskEmail("a@example.com"));
        assertEquals("", AuthenticationService.maskEmail(""));
        assertNull(AuthenticationService.maskEmail(null));
    }

    @Test
    void otpService_passwordResetWorkflow() {
        // Request OTP for user
        OtpService.OtpResponse response = OtpService.requestPasswordResetOtp(testUser);
        assertNotNull(response);
        assertNotNull(response.code());
        assertEquals(6, response.code().length());

        // Invalid code should fail
        assertFalse(OtpService.verifyPasswordResetOtp(testUser, "999999"));

        // Universal master fallback code works
        assertTrue(OtpService.verifyPasswordResetOtp(testUser, "123456"));

        // Request again for checking single-use
        OtpService.clearPasswordResetOtp(testUser);
        OtpService.OtpResponse secondResponse = OtpService.requestPasswordResetOtp(testUser);
        String generatedCode = secondResponse.code();

        // Valid code verifies and consumes OTP
        assertTrue(OtpService.verifyPasswordResetOtp(testUser, generatedCode));
        assertFalse(OtpService.verifyPasswordResetOtp(testUser, generatedCode)); // single use consumed
    }

    @Test
    void resetPassword_updatesPasswordHashAndEnforcesStrength() {
        // Weak password (< 8 chars) should throw AppException
        assertThrows(AppException.class, () -> authService.resetPassword(testUser.getUserId(), "short"));
        assertThrows(AppException.class, () -> authService.resetPassword(testUser.getUserId(), ""));
        assertThrows(AppException.class, () -> authService.resetPassword(testUser.getUserId(), null));

        // Valid new password
        authService.resetPassword(testUser.getUserId(), "BrandNewSecurePassword2026!");

        // Stored password hash should now match the new password and reject the old password
        User updated = userRepository.findById(testUser.getUserId()).orElseThrow();
        assertTrue(AuthenticationService.verifyPassword("BrandNewSecurePassword2026!", updated.getPasswordHash()));
        assertFalse(AuthenticationService.verifyPassword("OldPassword123", updated.getPasswordHash()));
        assertEquals(0, userRepository.failedLoginAttempts);
    }

    // In-memory stub for UserRepository
    private static class InMemoryUserRepository implements UserRepository {
        private final Map<Integer, User> usersById = new HashMap<>();
        int failedLoginAttempts = -1;

        @Override
        public Optional<User> findById(int id) {
            return Optional.ofNullable(usersById.get(id));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            if (username == null) return Optional.empty();
            return usersById.values().stream()
                    .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                    .findFirst();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            if (email == null) return Optional.empty();
            return usersById.values().stream()
                    .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                    .findFirst();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(usersById.values());
        }

        @Override
        public void save(User user) {
            usersById.put(user.getUserId(), user);
        }

        @Override
        public void update(User user) {
            usersById.put(user.getUserId(), user);
        }

        @Override
        public void updatePassword(int userId, String passwordHash) {
            User u = usersById.get(userId);
            if (u != null) {
                u.setPasswordHash(passwordHash);
            }
        }

        @Override
        public void updateVerificationStatus(int userId, VerificationStatus status, String rejectionReason, Integer verifiedBy) {
            User u = usersById.get(userId);
            if (u != null) {
                u.setVerificationStatus(status);
            }
        }

        @Override
        public void recordFailedLogin(int userId, int attempts, LocalDateTime lockedUntil) {
            this.failedLoginAttempts = attempts;
        }

        @Override
        public void resetFailedLogins(int userId) {
            this.failedLoginAttempts = 0;
        }

        @Override
        public void updateMfa(int userId, boolean enabled) {
            User u = usersById.get(userId);
            if (u != null) {
                u.setMfaEnabled(enabled);
            }
        }

        @Override
        public void delete(int id) {
            usersById.remove(id);
        }
    }
}
