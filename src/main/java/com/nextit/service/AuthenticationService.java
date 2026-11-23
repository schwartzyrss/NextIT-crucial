package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;
import com.nextit.repository.JdbcUserRepository;
import com.nextit.repository.UserRepository;
import com.nextit.security.Session;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final SecurityAuditService auditService;

    public AuthenticationService() {
        this(new JdbcUserRepository(), SecurityAuditService.getInstance());
    }

    public AuthenticationService(UserRepository userRepository) {
        this(userRepository, SecurityAuditService.getInstance());
    }

    public AuthenticationService(UserRepository userRepository, SecurityAuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public User login(String usernameOrEmail, String password) {
        User user = validateCredentials(usernameOrEmail, password);

        // Check if account is suspended
        if (user.isSuspended()) {
            auditService.log(user.getUserId(), user.getUsername(), "LOGIN_BLOCKED_SUSPENDED", user.getUserId(),
                    "127.0.0.1", "Blocked login attempt for suspended account", "SECURITY_ALERT");
            throw new AppException("This account has been suspended due to security or policy concerns. Please contact campus administration.");
        }

        // If user has MFA enabled, MFA code verification is required
        if (isMfaRequired(user)) {
            OtpService.requestMfaOtp(user);
            throw new MfaRequiredException(user, "Multi-Factor Authentication required for " + user.getRole() + " access. A 6-digit authorization code has been sent to " + maskEmail(user.getEmail()) + ".");
        }

        finalizeLogin(user);
        return user;
    }

    public User completeMfaLogin(User user, String mfaCode) {
        if (user == null) {
            throw new AppException("Invalid MFA session.");
        }
        if (mfaCode == null || mfaCode.trim().length() < 6) {
            throw new AppException("Please enter the 6-digit MFA authorization code.");
        }

        boolean valid = OtpService.verifyMfaOtp(user, mfaCode);
        if (!valid) {
            auditService.log(user.getUserId(), user.getUsername(), "MFA_VERIFICATION_FAILED", user.getUserId(),
                    "127.0.0.1", "Failed MFA code submission", "WARNING");
            throw new AppException("Invalid or expired MFA code. Please check your email or click resend.");
        }

        auditService.log(user.getUserId(), user.getUsername(), "MFA_VERIFICATION_SUCCESS", user.getUserId(),
                "127.0.0.1", "MFA validation successful", "INFO");
        finalizeLogin(user);
        return user;
    }

    public boolean isMfaRequired(User user) {
        return user != null && user.isMfaEnabled();
    }

    public User validateCredentials(String usernameOrEmail, String password) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank() || password == null || password.isBlank()) {
            throw new AppException("Username and password are required.");
        }
        String term = usernameOrEmail.trim();

        // Convenience shortcuts for demo roles:
        if ("student".equalsIgnoreCase(term)) term = "student1";
        else if ("instructor".equalsIgnoreCase(term)) term = "instructor1";
        else if ("employer".equalsIgnoreCase(term)) term = "employer1";

        Optional<User> found = userRepository.findByUsername(term);
        if (found.isEmpty()) {
            found = userRepository.findByEmail(term);
        }

        if (found.isEmpty()) {
            auditService.logLoginFailure(term, "User not found");
            throw new AppException("Invalid username or password.");
        }

        User user = found.get();

        // Check if account is temporarily locked
        if (user.isLocked()) {
            long minutesRemaining = Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            auditService.log(user.getUserId(), user.getUsername(), "LOGIN_BLOCKED_LOCKED", user.getUserId(),
                    "127.0.0.1", "Login blocked due to active account lockout", "WARNING");
            throw new AppException(String.format("Account is temporarily locked due to repeated failed login attempts. Please try again in %d minute%s or contact support.",
                    minutesRemaining, minutesRemaining == 1 ? "" : "s"));
        }

        if (!verifyPassword(password, user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            LocalDateTime lockTime = null;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                lockTime = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
                userRepository.recordFailedLogin(user.getUserId(), attempts, lockTime);
                auditService.logAccountLocked(user.getUsername());
                throw new AppException("Account locked for 15 minutes due to 5 consecutive failed login attempts.");
            }
            userRepository.recordFailedLogin(user.getUserId(), attempts, null);
            auditService.logLoginFailure(user.getUsername(), "Incorrect password (attempt " + attempts + " of " + MAX_FAILED_ATTEMPTS + ")");
            throw new AppException(String.format("Invalid credentials. %d attempt%s remaining before account lockout.",
                    (MAX_FAILED_ATTEMPTS - attempts), (MAX_FAILED_ATTEMPTS - attempts) == 1 ? "" : "s"));
        }

        // Credentials are valid; reset failed login counter
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            userRepository.resetFailedLogins(user.getUserId());
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        return user;
    }

    public void finalizeLogin(User user) {
        Session.login(user);
        auditService.logLoginSuccess(user);
    }

    public void logout() {
        if (Session.currentUser() != null) {
            auditService.log(Session.currentUser().getUserId(), Session.currentUser().getUsername(),
                    "LOGOUT", Session.currentUser().getUserId(), "127.0.0.1", "User logged out", "INFO");
        }
        Session.logout();
    }

    public static String hashPassword(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(10));
    }

    public static boolean verifyPassword(String plain, String hash) {
        if (hash == null || plain == null) return false;
        try {
            return BCrypt.checkpw(plain, hash);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public Optional<User> findUserForPasswordReset(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String term = identifier.trim();
        return userRepository.findByIdentifier(term);
    }

    public void resetPassword(int userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new AppException("Password must be at least 8 characters long.");
        }
        String hashed = hashPassword(newPassword);
        userRepository.updatePassword(userId, hashed);
        userRepository.resetFailedLogins(userId);
        userRepository.findById(userId).ifPresent(user -> {
            auditService.log(user.getUserId(), user.getUsername(), "PASSWORD_RESET_SUCCESS", user.getUserId(),
                    "127.0.0.1", "Password reset successfully via email verification", "INFO");
        });
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String domain = email.substring(at);
        if (name.isEmpty()) return "***" + domain;
        if (name.length() <= 2) return name.charAt(0) + "***" + domain;
        return name.substring(0, 2) + "***" + name.charAt(name.length() - 1) + domain;
    }

    /**
     * Resolves a demo or active user for quick login by role.
     * Searches preferred demo accounts first, then queries Supabase/DB for the most recent
     * active account matching that role, with auto-provisioning fallback for clean environments.
     */
    public Optional<User> findQuickAccessUser(Role role) {
        if (role == null) return Optional.empty();

        // 1. Check preferred demo accounts for this role
        java.util.List<String> candidates = switch (role) {
            case STUDENT -> java.util.List.of("alexreyes26", "alexreyes", "student1", "25-16522");
            case INSTRUCTOR -> java.util.List.of("instructor1", "maria.santos", "kakonggg");
            case EMPLOYER -> java.util.List.of("nexusinnovation26", "nexusinnovation", "employer1");
            case ADMIN -> java.util.List.of("admin", "admin26", "sysadmin");
        };

        for (String candidate : candidates) {
            Optional<User> u = userRepository.findByUsername(candidate);
            if (u.isEmpty()) {
                u = userRepository.findByEmail(candidate);
            }
            if (u.isPresent() && u.get().getRole() == role && !u.get().isSuspended()) {
                return u;
            }
        }

        // 2. Query repository for the most recent active/verified account with this role
        Optional<User> latestActive = userRepository.findLatestActiveByRole(role);
        if (latestActive.isPresent() && !latestActive.get().isSuspended()) {
            return latestActive;
        }

        // 3. Fallback: auto-provision default demo user if the database is clean/empty
        return autoProvisionDemoUser(role);
    }

    /**
     * Performs immediate one-click authentication for a quick-access demo or active user.
     * Bypasses manual entry while strictly verifying account suspension, lockout, and MFA requirements.
     */
    public User quickLogin(User user, String fallbackPassword) {
        if (user == null) {
            throw new AppException("Invalid user for quick access.");
        }
        if (user.isSuspended()) {
            auditService.log(user.getUserId(), user.getUsername(), "LOGIN_BLOCKED_SUSPENDED", user.getUserId(),
                    "127.0.0.1", "Blocked quick login attempt for suspended account", "SECURITY_ALERT");
            throw new AppException("This account has been suspended due to security or policy concerns.");
        }

        // Check account lockout
        if (user.isLocked()) {
            long minutesRemaining = Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            throw new AppException(String.format("Account is temporarily locked due to repeated failed login attempts. Please try again in %d minute%s or contact support.",
                    minutesRemaining, minutesRemaining == 1 ? "" : "s"));
        }

        // If fallback password matches the hash, reset failed attempts
        if (fallbackPassword != null && verifyPassword(fallbackPassword, user.getPasswordHash())) {
            if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
                userRepository.resetFailedLogins(user.getUserId());
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
            }
        }

        // MFA check
        if (isMfaRequired(user)) {
            OtpService.requestMfaOtp(user);
            throw new MfaRequiredException(user, "Multi-Factor Authentication required for " + user.getRole() + " access. A 6-digit authorization code has been sent to " + maskEmail(user.getEmail()) + ".");
        }

        finalizeLogin(user);
        return user;
    }

    private Optional<User> autoProvisionDemoUser(Role role) {
        User demo = new User();
        switch (role) {
            case STUDENT -> {
                demo.setUsername("alexreyes26");
                demo.setEmail("alexreyes26@nextit.edu");
                demo.setFirstName("Alex");
                demo.setLastName("Reyes");
                demo.setRole(Role.STUDENT);
            }
            case INSTRUCTOR -> {
                demo.setUsername("instructor1");
                demo.setEmail("instructor@nextit.edu");
                demo.setFirstName("Maria");
                demo.setLastName("Santos");
                demo.setRole(Role.INSTRUCTOR);
            }
            case EMPLOYER -> {
                demo.setUsername("nexusinnovation26");
                demo.setEmail("nexusinnovation26@nextit.edu");
                demo.setFirstName("Nexus");
                demo.setLastName("Innovation");
                demo.setRole(Role.EMPLOYER);
            }
            case ADMIN -> {
                demo.setUsername("admin");
                demo.setEmail("admin@nextit.edu.ph");
                demo.setFirstName("System");
                demo.setLastName("Administrator");
                demo.setRole(Role.ADMIN);
            }
        }
        demo.setPasswordHash(hashPassword("password123"));
        demo.setVerificationStatus(VerificationStatus.VERIFIED);
        demo.setVerifiedAt(LocalDateTime.now());
        demo.setMfaEnabled(false);
        try {
            userRepository.save(demo);
            log.info("Auto-provisioned default demo account for {}: {}", role, demo.getUsername());
            return Optional.of(demo);
        } catch (Exception ex) {
            log.debug("Auto-provision save failed for {}: {}, attempting to look up existing", role, ex.getMessage());
            return userRepository.findByUsername(demo.getUsername());
        }
    }

    public static class MfaRequiredException extends AppException {
        private static final long serialVersionUID = 1L;
        private final transient User user;

        public MfaRequiredException(User user, String message) {
            super(message);
            this.user = user;
        }

        public User getUser() {
            return user;
        }
    }
}
