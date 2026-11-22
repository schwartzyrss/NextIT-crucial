package com.nextit;

import com.nextit.config.AppConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;
import com.nextit.repository.UserRepository;
import com.nextit.security.Session;
import com.nextit.service.AuthenticationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QuickAccessLoginTest {

    private InMemoryUserRepository userRepository;
    private AuthenticationService authService;

    @BeforeEach
    void setUp() {
        AppConfig.ENABLE_QUICK_LOGIN = true;
        Session.logout();
        userRepository = new InMemoryUserRepository();
        authService = new AuthenticationService(userRepository);
    }

    @AfterEach
    void tearDown() {
        AppConfig.ENABLE_QUICK_LOGIN = true;
        Session.logout();
    }

    @Test
    void testAppConfigToggleFlag() {
        assertTrue(AppConfig.isQuickLoginEnabled(), "Quick login should be enabled by default");

        AppConfig.ENABLE_QUICK_LOGIN = false;
        assertFalse(AppConfig.isQuickLoginEnabled(), "Quick login should be disabled when flag is false");

        AppConfig.ENABLE_QUICK_LOGIN = true;
        assertTrue(AppConfig.isQuickLoginEnabled(), "Quick login should be re-enabled when flag is true");
    }

    @Test
    void testFindQuickAccessStudent() {
        User student = new User();
        student.setUserId(101);
        student.setUsername("alexreyes26");
        student.setEmail("alexreyes26@nextit.edu");
        student.setPasswordHash(AuthenticationService.hashPassword("password123"));
        student.setRole(Role.STUDENT);
        student.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(student);

        Optional<User> found = authService.findQuickAccessUser(Role.STUDENT);
        assertTrue(found.isPresent());
        assertEquals("alexreyes26", found.get().getUsername());
        assertEquals(Role.STUDENT, found.get().getRole());
    }

    @Test
    void testFindQuickAccessInstructor() {
        User instructor = new User();
        instructor.setUserId(201);
        instructor.setUsername("instructor1");
        instructor.setEmail("instructor@nextit.edu");
        instructor.setPasswordHash(AuthenticationService.hashPassword("password123"));
        instructor.setRole(Role.INSTRUCTOR);
        instructor.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(instructor);

        Optional<User> found = authService.findQuickAccessUser(Role.INSTRUCTOR);
        assertTrue(found.isPresent());
        assertEquals("instructor1", found.get().getUsername());
        assertEquals(Role.INSTRUCTOR, found.get().getRole());
    }

    @Test
    void testFindQuickAccessEmployer() {
        User employer = new User();
        employer.setUserId(301);
        employer.setUsername("nexusinnovation26");
        employer.setEmail("nexusinnovation26@nextit.edu");
        employer.setPasswordHash(AuthenticationService.hashPassword("password123"));
        employer.setRole(Role.EMPLOYER);
        employer.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(employer);

        Optional<User> found = authService.findQuickAccessUser(Role.EMPLOYER);
        assertTrue(found.isPresent());
        assertEquals("nexusinnovation26", found.get().getUsername());
        assertEquals(Role.EMPLOYER, found.get().getRole());
    }

    @Test
    void testFindQuickAccessAdmin() {
        User adminUser = new User();
        adminUser.setUserId(401);
        adminUser.setUsername("admin");
        adminUser.setEmail("admin@nextit.edu.ph");
        adminUser.setPasswordHash(AuthenticationService.hashPassword("password123"));
        adminUser.setRole(Role.ADMIN);
        adminUser.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(adminUser);

        Optional<User> found = authService.findQuickAccessUser(Role.ADMIN);
        assertTrue(found.isPresent());
        assertEquals("admin", found.get().getUsername());
        assertEquals(Role.ADMIN, found.get().getRole());
    }

    @Test
    void testRoleFromStringParsing() {
        assertEquals(Role.ADMIN, Role.fromString("admin"));
        assertEquals(Role.ADMIN, Role.fromString("ADMIN"));
        assertEquals(Role.ADMIN, Role.fromString(" Admin "));
        assertEquals(Role.STUDENT, Role.fromString("student"));
        assertEquals(Role.INSTRUCTOR, Role.fromString("instructor"));
        assertEquals(Role.EMPLOYER, Role.fromString("employer"));
        assertEquals(Role.STUDENT, Role.fromString("Student Scholar"));
        assertEquals(Role.INSTRUCTOR, Role.fromString("Faculty Instructor / Coordinator"));
        assertEquals(Role.EMPLOYER, Role.fromString("Industry Employer Partner"));
        assertEquals(Role.ADMIN, Role.fromString("System Administrator"));
        assertNull(Role.fromString(null));
        assertNull(Role.fromString(""));
        assertNull(Role.fromString("unknown_role"));
    }

    @Test
    void testQuickLoginAuthenticationSuccess() {
        User student = new User();
        student.setUserId(101);
        student.setUsername("student1");
        student.setEmail("student@nextit.edu");
        student.setPasswordHash(AuthenticationService.hashPassword("password123"));
        student.setRole(Role.STUDENT);
        student.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(student);

        User authed = authService.quickLogin(student, "password123");
        assertNotNull(authed);
        assertEquals("student1", authed.getUsername());
        assertEquals(student.getUserId(), Session.currentUser().getUserId());
    }

    @Test
    void testQuickLoginBlocksSuspendedAccount() {
        User suspended = new User();
        suspended.setUserId(105);
        suspended.setUsername("suspended_student");
        suspended.setEmail("suspended@nextit.edu");
        suspended.setPasswordHash(AuthenticationService.hashPassword("password123"));
        suspended.setRole(Role.STUDENT);
        suspended.setVerificationStatus(VerificationStatus.SUSPENDED);
        userRepository.save(suspended);

        AppException ex = assertThrows(AppException.class, () ->
                authService.quickLogin(suspended, "password123"));
        assertTrue(ex.getMessage().contains("suspended"));
        assertNull(Session.currentUser());
    }

    @Test
    void testAutoProvisionWhenDatabaseEmpty() {
        // With an empty repository, auto-provision should provide a default account
        Optional<User> studentOpt = authService.findQuickAccessUser(Role.STUDENT);
        assertTrue(studentOpt.isPresent());
        assertEquals("alexreyes26", studentOpt.get().getUsername());

        Optional<User> employerOpt = authService.findQuickAccessUser(Role.EMPLOYER);
        assertTrue(employerOpt.isPresent());
        assertEquals("nexusinnovation26", employerOpt.get().getUsername());
    }

    // In-memory test repository implementing UserRepository
    private static class InMemoryUserRepository implements UserRepository {
        private final List<User> users = new ArrayList<>();
        private int nextId = 1;

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
            if (user.getUserId() <= 0) {
                user.setUserId(nextId++);
            }
            users.removeIf(u -> u.getUserId() == user.getUserId() ||
                    (u.getUsername() != null && u.getUsername().equalsIgnoreCase(user.getUsername())));
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
