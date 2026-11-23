package com.nextit;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.repository.*;
import com.nextit.security.Session;
import com.nextit.service.AdminService;
import com.nextit.service.AntiFakeIdentityService;
import com.nextit.service.AuthenticationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityVerificationSecurityTest {

    private static JdbcUserRepository userRepo;
    private static JdbcVerificationRequestRepository verificationRepo;
    private static JdbcInstitutionalRecordRepository institutionalRepo;

    private AntiFakeIdentityService antiFakeService;
    private AuthenticationService authService;
    private AdminService adminService;

    @BeforeAll
    static void initDatabase() throws SQLException {
        DatabaseTestServer.getInstance().useForApplication();
        userRepo = new JdbcUserRepository();
        verificationRepo = new JdbcVerificationRequestRepository();
        institutionalRepo = new JdbcInstitutionalRecordRepository();
    }

    @BeforeEach
    void setUp() {
        antiFakeService = new AntiFakeIdentityService();
        authService = new AuthenticationService();
        adminService = new AdminService();
        Session.logout();
    }

    @AfterEach
    void tearDown() {
        Session.logout();
    }

    // ==========================================
    // 1. RBAC & SESSION SECURITY TESTS
    // ==========================================

    @Test
    @DisplayName("Registered accounts get immediate access to protected role features without manual verification")
    void registeredAccountsGetImmediateAccessToRoleFeatures() {
        User user = new User();
        user.setUserId(991);
        user.setEmail("student@school.edu");
        user.setRole(Role.STUDENT);
        user.setVerificationStatus(VerificationStatus.VERIFIED);

        Session.login(user);

        assertDoesNotThrow(Session::requireVerified);
        assertDoesNotThrow(() -> Session.requireRole(Role.STUDENT));
    }

    @Test
    @DisplayName("Pending verification accounts are treated as active and grant immediate access")
    void pendingAccountGrantsImmediateAccess() {
        User user = new User();
        user.setUserId(992);
        user.setEmail("pending@school.edu");
        user.setRole(Role.STUDENT);
        user.setVerificationStatus(VerificationStatus.PENDING);

        Session.login(user);

        assertDoesNotThrow(Session::requireVerified);
        assertTrue(user.isVerified());
        assertDoesNotThrow(() -> Session.requireRole(Role.STUDENT));
    }

    @Test
    @DisplayName("Suspended account must be strictly blocked")
    void suspendedAccountBlocked() {
        User suspendedUser = new User();
        suspendedUser.setUserId(993);
        suspendedUser.setRole(Role.STUDENT);
        suspendedUser.setVerificationStatus(VerificationStatus.SUSPENDED);

        Session.login(suspendedUser);
        AppException ex1 = assertThrows(AppException.class, Session::requireVerified);
        assertTrue(ex1.getMessage().contains("suspended"));
    }

    @Test
    @DisplayName("Verified account passes verification check and role authorization")
    void verifiedAccountPassesVerificationAndRoleAuthorization() {
        User verifiedUser = new User();
        verifiedUser.setUserId(995);
        verifiedUser.setRole(Role.STUDENT);
        verifiedUser.setVerificationStatus(VerificationStatus.VERIFIED);

        Session.login(verifiedUser);
        assertDoesNotThrow(Session::requireVerified);

        // Role authorization check
        assertDoesNotThrow(() -> Session.requireRole(Role.STUDENT));
        assertThrows(AppException.class, () -> Session.requireRole(Role.EMPLOYER, Role.INSTRUCTOR));
    }

    // ==========================================
    // 2. ANTI-FAKE IDENTITY & INSTITUTIONAL VERIFICATION
    // ==========================================

    @Test
    @DisplayName("AntiFakeService flags disposable / burner emails as suspicious")
    void flagsDisposableBurnerEmails() {
        AntiFakeIdentityService.AnalysisResult result =
                antiFakeService.analyzeStudent("2024-8888", "hacker@mailinator.com", "John", "Doe", "BSIT");

        assertTrue(result.riskScore() >= 40);
        assertTrue(result.riskFlags().contains("DISPOSABLE_EMAIL_DOMAIN"));
        assertFalse(result.autoApproved());
    }

    @Test
    @DisplayName("AntiFakeService matches student against institutional registrar records")
    void matchesAuthorizedInstitutionalRecord() {
        // Pre-populate school registrar record
        InstitutionalRecord reg = new InstitutionalRecord();
        reg.setRecordType(Role.STUDENT);
        reg.setIdentifierNumber("2024-REG-01");
        reg.setFullName("Juan Dela Cruz");
        reg.setInstitutionalEmail("juan.delacruz@school.edu");
        reg.setDepartmentOrProgram("BS Information Technology");
        reg.setActive(true);
        institutionalRepo.save(reg);

        AntiFakeIdentityService.AnalysisResult result =
                antiFakeService.analyzeStudent("2024-REG-01", "juan.delacruz@school.edu", "Juan", "Dela Cruz", "BS Information Technology");

        assertTrue(result.autoApproved());
        assertEquals(VerificationStatus.VERIFIED, result.status());
        assertEquals(0, result.riskScore());
    }

    @Test
    @DisplayName("AntiFakeService flags unlisted student ID for manual administrative review")
    void flagsUnlistedStudentForManualReview() {
        AntiFakeIdentityService.AnalysisResult result =
                antiFakeService.analyzeStudent("2099-UNKNOWN-99", "newstudent@gmail.com", "Alex", "Smith", "BSCS");

        assertFalse(result.autoApproved());
        assertEquals(VerificationStatus.PENDING, result.status());
        assertTrue(result.riskFlags().contains("UNLISTED_STUDENT_ID_NON_EDU"));
    }

    @Test
    @DisplayName("AntiFakeService flags invalid or short employer business registration")
    void flagsInvalidEmployerBusinessRegistration() {
        AntiFakeIdentityService.AnalysisResult result =
                antiFakeService.analyzeEmployer("Fake Corp", "123", "contact@fakecorp.com", "Bob");

        assertTrue(result.riskFlags().contains("INVALID_BUSINESS_REG_FORMAT"));
        assertTrue(result.riskScore() >= 30);
    }

    // ==========================================
    // 3. AUTHENTICATION, RATE LIMITING & ACCOUNT LOCKOUT
    // ==========================================

    @Test
    @DisplayName("Account is temporarily locked after 5 consecutive failed login attempts")
    void accountLockedAfterRepeatedFailures() {
        String uniqueUser = "locktest_" + UUID.randomUUID().toString().substring(0, 8);
        String email = uniqueUser + "@test.ph";

        User user = new User();
        user.setUsername(uniqueUser);
        user.setEmail(email);
        user.setPasswordHash(AuthenticationService.hashPassword("correctPassword123"));
        user.setRole(Role.STUDENT);
        user.setFirstName("Lock");
        user.setLastName("Test");
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepo.save(user);

        // 5 consecutive wrong password attempts
        for (int i = 1; i <= 5; i++) {
            assertThrows(AppException.class, () -> authService.login(email, "wrongPassword"));
        }

        // 6th attempt should fail with Account Locked message
        AppException lockedEx = assertThrows(AppException.class, () -> authService.login(email, "correctPassword123"));
        assertTrue(lockedEx.getMessage().toLowerCase().contains("locked"));
    }

    @Test
    @DisplayName("User with MFA enabled triggers mandatory MFA verification exception")
    void mfaEnabledLoginRequiresMfaVerification() {
        String uniqueUser = "mfatest_" + UUID.randomUUID().toString().substring(0, 8);
        String email = uniqueUser + "@nextit.ph";

        User user = new User();
        user.setUsername(uniqueUser);
        user.setEmail(email);
        user.setPasswordHash(AuthenticationService.hashPassword("SuperSecretUser123!"));
        user.setRole(Role.INSTRUCTOR);
        user.setFirstName("Maria");
        user.setLastName("Instructor");
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setMfaEnabled(true);
        userRepo.save(user);

        AuthenticationService.MfaRequiredException mfaEx =
                assertThrows(AuthenticationService.MfaRequiredException.class,
                        () -> authService.login(email, "SuperSecretUser123!"));

        assertNotNull(mfaEx.getUser());
        assertEquals(email, mfaEx.getUser().getEmail());
    }

    // ==========================================
    // 4. ADMINISTRATOR VERIFICATION WORKFLOWS
    // ==========================================

    @Test
    @DisplayName("Admin approving a verification request transitions user to VERIFIED")
    void adminApprovesVerification() {
        String uniqueUser = "appr_" + UUID.randomUUID().toString().substring(0, 8);
        User applicant = new User();
        applicant.setUsername(uniqueUser);
        applicant.setEmail(uniqueUser + "@school.edu");
        applicant.setPasswordHash("hash");
        applicant.setRole(Role.INSTRUCTOR);
        applicant.setFirstName("Jane");
        applicant.setLastName("Doe");
        applicant.setVerificationStatus(VerificationStatus.PENDING);
        userRepo.save(applicant);

        User savedUser = userRepo.findByUsername(uniqueUser).orElseThrow();

        VerificationRequest req = new VerificationRequest();
        req.setUserId(savedUser.getUserId());
        req.setRole(Role.INSTRUCTOR);
        req.setStatus(VerificationStatus.PENDING);
        req.setIdNumber("FAC-9999");
        req.setRiskScore(10);
        verificationRepo.save(req);

        VerificationRequest savedReq = verificationRepo.findByUserId(savedUser.getUserId()).orElseThrow();

        adminService.approveVerification(savedReq.getRequestId(), 1, "Credentials verified by Faculty HR");

        User updatedUser = userRepo.findById(savedUser.getUserId()).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, updatedUser.getVerificationStatus());
        assertNotNull(updatedUser.getVerifiedAt());

        VerificationRequest updatedReq = verificationRepo.findById(savedReq.getRequestId()).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, updatedReq.getStatus());
        assertEquals("Credentials verified by Faculty HR", updatedReq.getReviewerNotes());
    }

    @Test
    @DisplayName("Admin rejecting a verification request sets status to REJECTED with reason")
    void adminRejectsVerification() {
        String uniqueUser = "rej_" + UUID.randomUUID().toString().substring(0, 8);
        User applicant = new User();
        applicant.setUsername(uniqueUser);
        applicant.setEmail(uniqueUser + "@school.edu");
        applicant.setPasswordHash("hash");
        applicant.setRole(Role.EMPLOYER);
        applicant.setFirstName("Bad");
        applicant.setLastName("Actor");
        applicant.setVerificationStatus(VerificationStatus.PENDING);
        userRepo.save(applicant);

        User savedUser = userRepo.findByUsername(uniqueUser).orElseThrow();

        VerificationRequest req = new VerificationRequest();
        req.setUserId(savedUser.getUserId());
        req.setRole(Role.EMPLOYER);
        req.setStatus(VerificationStatus.PENDING);
        req.setOrganizationName("Shady Ltd");
        verificationRepo.save(req);

        VerificationRequest savedReq = verificationRepo.findByUserId(savedUser.getUserId()).orElseThrow();

        adminService.rejectVerification(savedReq.getRequestId(), 1, "Invalid SEC registration number provided.");

        User updatedUser = userRepo.findById(savedUser.getUserId()).orElseThrow();
        assertEquals(VerificationStatus.REJECTED, updatedUser.getVerificationStatus());
        assertEquals("Invalid SEC registration number provided.", updatedUser.getRejectionReason());
    }

    @Test
    @DisplayName("Admin can suspend and reinstate accounts")
    void adminSuspendAndReinstateUser() {
        String uniqueUser = "susp_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(uniqueUser);
        user.setEmail(uniqueUser + "@school.edu");
        user.setPasswordHash("hash");
        user.setRole(Role.STUDENT);
        user.setFirstName("Sus");
        user.setLastName("Pended");
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepo.save(user);

        User savedUser = userRepo.findByUsername(uniqueUser).orElseThrow();

        adminService.suspendUser(savedUser.getUserId(), "Suspected credential sharing");
        assertEquals(VerificationStatus.SUSPENDED, userRepo.findById(savedUser.getUserId()).orElseThrow().getVerificationStatus());

        adminService.reinstateUser(savedUser.getUserId());
        assertEquals(VerificationStatus.VERIFIED, userRepo.findById(savedUser.getUserId()).orElseThrow().getVerificationStatus());
    }
}
