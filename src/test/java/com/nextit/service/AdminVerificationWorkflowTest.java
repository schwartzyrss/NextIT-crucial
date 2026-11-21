package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.repository.UserRepository;
import com.nextit.repository.VerificationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AdminVerificationWorkflowTest {

    private AdminService adminService;
    private VerificationRequestRepository verificationRepo;
    private UserRepository userRepo;

    @BeforeEach
    void setUp() {
        verificationRepo = new InMemoryVerificationRequestRepository();
        userRepo = new InMemoryUserRepository();
        adminService = new AdminService(userRepo, verificationRepo);
    }

    @Test
    void testApproveVerificationWorkflow() {
        User instructor = new User();
        instructor.setUserId(50);
        instructor.setUsername("prof_santos");
        instructor.setEmail("santos@univ.edu");
        instructor.setRole(Role.INSTRUCTOR);
        instructor.setVerificationStatus(VerificationStatus.PENDING);
        userRepo.save(instructor);

        VerificationRequest req = new VerificationRequest();
        req.setRequestId(1);
        req.setUserId(50);
        req.setRole(Role.INSTRUCTOR);
        req.setIdNumber("FAC-1001");
        req.setInstitutionalEmail("santos@univ.edu");
        req.setOrganizationName("College of Information Technology");
        req.setStatus(VerificationStatus.PENDING);
        verificationRepo.save(req);

        adminService.approveVerification(1, 999, "Credentials authenticated against registrar");

        VerificationRequest updatedReq = verificationRepo.findById(1).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, updatedReq.getStatus());
        assertEquals(Integer.valueOf(999), updatedReq.getReviewerId());
        assertEquals("Credentials authenticated against registrar", updatedReq.getReviewerNotes());
        assertNotNull(updatedReq.getReviewedAt());

        User updatedUser = userRepo.findById(50).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, updatedUser.getVerificationStatus());
        assertTrue(updatedUser.isVerified());
    }

    @Test
    void testRejectVerificationWorkflow() {
        User employer = new User();
        employer.setUserId(60);
        employer.setUsername("fake_recruiter");
        employer.setEmail("scam@disposable.com");
        employer.setRole(Role.EMPLOYER);
        employer.setVerificationStatus(VerificationStatus.PENDING);
        userRepo.save(employer);

        VerificationRequest req = new VerificationRequest();
        req.setRequestId(2);
        req.setUserId(60);
        req.setRole(Role.EMPLOYER);
        req.setOrganizationName("Shady LLC");
        req.setBusinessRegistrationNumber("INVALID-000");
        req.setStatus(VerificationStatus.PENDING);
        verificationRepo.save(req);

        adminService.rejectVerification(2, 999, "Business registration number not verifiable in SEC/DTI database.");

        VerificationRequest updatedReq = verificationRepo.findById(2).orElseThrow();
        assertEquals(VerificationStatus.REJECTED, updatedReq.getStatus());
        assertEquals(Integer.valueOf(999), updatedReq.getReviewerId());
        assertEquals("Business registration number not verifiable in SEC/DTI database.", updatedReq.getReviewerNotes());

        User updatedUser = userRepo.findById(60).orElseThrow();
        assertEquals(VerificationStatus.REJECTED, updatedUser.getVerificationStatus());
        assertEquals("Business registration number not verifiable in SEC/DTI database.", updatedUser.getRejectionReason());
    }

    @Test
    void testRejectRequiresNonBlankReason() {
        VerificationRequest req = new VerificationRequest();
        req.setRequestId(3);
        req.setUserId(70);
        req.setRole(Role.INSTRUCTOR);
        req.setStatus(VerificationStatus.PENDING);
        verificationRepo.save(req);

        assertThrows(AppException.class, () -> adminService.rejectVerification(3, 999, ""));
        assertThrows(AppException.class, () -> adminService.rejectVerification(3, 999, "   "));
        assertThrows(AppException.class, () -> adminService.rejectVerification(3, 999, null));
    }

    @Test
    void testRoleAdminProperties() {
        assertEquals(Role.ADMIN, Role.fromString("ADMIN"));
        assertEquals(Role.ADMIN, Role.fromString("admin"));
        assertEquals(Role.ADMIN, Role.fromString(" Admin "));

        User adminUser = new User();
        adminUser.setRole(Role.ADMIN);
        assertTrue(adminUser.isVerified());
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
        public void recordFailedLogin(int userId, int attempts, java.time.LocalDateTime lockedUntil) {}

        @Override
        public void resetFailedLogins(int userId) {}

        @Override
        public void updateMfa(int userId, boolean enabled) {}

        @Override
        public void delete(int id) {
            users.removeIf(u -> u.getUserId() == id);
        }
    }

    private static class InMemoryVerificationRequestRepository implements VerificationRequestRepository {
        private final Map<Integer, VerificationRequest> map = new LinkedHashMap<>();

        @Override
        public Optional<VerificationRequest> findById(int id) {
            return Optional.ofNullable(map.get(id));
        }

        @Override
        public Optional<VerificationRequest> findByUserId(int userId) {
            return map.values().stream().filter(r -> r.getUserId() == userId).findFirst();
        }

        @Override
        public List<VerificationRequest> findAll() {
            return new ArrayList<>(map.values());
        }

        @Override
        public List<VerificationRequest> findPending() {
            return map.values().stream().filter(r -> r.getStatus() == VerificationStatus.PENDING).toList();
        }

        @Override
        public void save(VerificationRequest request) {
            map.put(request.getRequestId(), request);
        }

        @Override
        public void update(VerificationRequest request) {
            map.put(request.getRequestId(), request);
        }

        @Override
        public void delete(int id) {
            map.remove(id);
        }
    }
}
