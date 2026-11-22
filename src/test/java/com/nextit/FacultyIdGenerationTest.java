package com.nextit;

import com.nextit.controller.OnboardingView;
import com.nextit.model.Instructor;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.model.VerificationRequest;
import com.nextit.repository.JdbcInstructorRepository;
import com.nextit.repository.JdbcVerificationRequestRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.*;

class FacultyIdGenerationTest {

    private static IntegrationTestData data;
    private static JdbcInstructorRepository instructorRepo;
    private static JdbcVerificationRequestRepository verificationRepo;

    @BeforeAll
    static void setUp() throws SQLException {
        DatabaseTestServer.getInstance().useForApplication();
        data = new IntegrationTestData();
        instructorRepo = new JdbcInstructorRepository();
        verificationRepo = new JdbcVerificationRequestRepository();
    }

    @Test
    void standardizedFacultyIdFormatAndSequentialIncrement() {
        int testYear = 2032; // Future year ensures a clean initial sequence starting at 0001
        String id1 = instructorRepo.generateNextFacultyNumber(testYear);
        assertEquals("FAC-2032-0001", id1, "First faculty ID for an empty year must be FAC-2032-0001");

        // Save an instructor with FAC-2032-0001
        User u1 = data.user(Role.INSTRUCTOR);
        Instructor i1 = new Instructor();
        i1.setUserId(u1.getUserId());
        i1.setEmployeeNumber(id1);
        i1.setDepartment("Computer Studies");
        i1.setSpecialization("Software Engineering");
        instructorRepo.save(i1);

        // Next ID should be FAC-2032-0002
        String id2 = instructorRepo.generateNextFacultyNumber(testYear);
        assertEquals("FAC-2032-0002", id2, "Subsequent faculty ID must increment sequentially to FAC-2032-0002");

        // Save another instructor with FAC-2032-0091 to test multi-digit zero-padding
        User u2 = data.user(Role.INSTRUCTOR);
        Instructor i2 = new Instructor();
        i2.setUserId(u2.getUserId());
        i2.setEmployeeNumber("FAC-2032-0091");
        i2.setDepartment("Information Technology");
        i2.setSpecialization("Cybersecurity");
        instructorRepo.save(i2);

        // Next ID should be FAC-2032-0092
        String id3 = instructorRepo.generateNextFacultyNumber(testYear);
        assertEquals("FAC-2032-0092", id3, "Next faculty ID after FAC-2032-0091 must be FAC-2032-0092");
    }

    @Test
    void sequenceConsidersPendingVerificationRequests() {
        int testYear = 2033;
        String id1 = instructorRepo.generateNextFacultyNumber(testYear);
        assertEquals("FAC-2033-0001", id1);

        // Simulate an onboarding verification request in progress with FAC-2033-0005
        User u = data.user(Role.INSTRUCTOR);
        VerificationRequest req = new VerificationRequest();
        req.setUserId(u.getUserId());
        req.setRole(Role.INSTRUCTOR);
        req.setIdNumber("FAC-2033-0005");
        req.setOrganizationName("College of Information Technology");
        req.setInstitutionalEmail("faculty.lead@univ.edu");
        verificationRepo.save(req);

        // Next generated faculty number must take the verification request into account
        String nextId = instructorRepo.generateNextFacultyNumber(testYear);
        assertEquals("FAC-2033-0006", nextId, "ID sequence must account for pending verification requests to prevent collisions");
    }

    @Test
    void javaBridgeReturnsValidCurrentYearFacultyIdPreview() {
        OnboardingView.JavaBridge bridge = new OnboardingView.JavaBridge(null, null);
        String previewId = bridge.getGeneratedFacultyId();
        assertNotNull(previewId);
        int currentYear = Year.now().getValue();
        assertTrue(previewId.startsWith("FAC-" + currentYear + "-"),
                "Preview faculty ID must begin with FAC-" + currentYear + "-: " + previewId);
        assertEquals(13, previewId.length(), "Faculty ID preview must follow FAC-YYYY-NNNN (13 characters)");
        assertTrue(previewId.matches("FAC-\\d{4}-\\d{4}"),
                "Faculty ID preview must match standardized FAC-YYYY-NNNN pattern");
    }

    @Test
    void databaseUniquenessConstraintEnforcedOnEmployeeNumber() {
        User u1 = data.user(Role.INSTRUCTOR);
        Instructor i1 = new Instructor();
        i1.setUserId(u1.getUserId());
        i1.setEmployeeNumber("FAC-2036-0055");
        i1.setDepartment("Computer Studies");
        i1.setSpecialization("Database Systems");
        instructorRepo.save(i1);

        // Attempting to save another instructor with the exact same employee number must fail
        User u2 = data.user(Role.INSTRUCTOR);
        Instructor duplicate = new Instructor();
        duplicate.setUserId(u2.getUserId());
        duplicate.setEmployeeNumber("FAC-2036-0055");
        duplicate.setDepartment("Computer Studies");
        duplicate.setSpecialization("Web Development");

        assertThrows(Exception.class, () -> instructorRepo.save(duplicate),
                "Database must strictly enforce UNIQUE constraint on employee_number");
    }
}
