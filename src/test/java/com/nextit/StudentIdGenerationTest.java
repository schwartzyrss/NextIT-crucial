package com.nextit;

import com.nextit.controller.OnboardingView;
import com.nextit.model.Role;
import com.nextit.model.Student;
import com.nextit.model.User;
import com.nextit.repository.JdbcStudentRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.*;

class StudentIdGenerationTest {

    private static IntegrationTestData data;
    private static JdbcStudentRepository studentRepo;

    @BeforeAll
    static void setUp() throws SQLException {
        DatabaseTestServer.getInstance().useForApplication();
        data = new IntegrationTestData();
        studentRepo = new JdbcStudentRepository();
    }

    @Test
    void standardizedIdFormatAndSequentialIncrement() {
        int testYear = 2030; // Use a future year to ensure clean sequence starting at 0001
        String id1 = studentRepo.generateNextStudentNumber(testYear);
        assertEquals("2030-0001", id1, "First student ID for an empty year must be 2030-0001");

        // Save a student with 2030-0001
        User u1 = data.user(Role.STUDENT);
        Student s1 = new Student();
        s1.setUserId(u1.getUserId());
        s1.setStudentNumber(id1);
        s1.setProgram("BS Information Technology");
        s1.setYearLevel(1);
        s1.setSection("A");
        s1.setEnrollmentStatus("Enrolled");
        studentRepo.save(s1);

        // Next ID should be 2030-0002
        String id2 = studentRepo.generateNextStudentNumber(testYear);
        assertEquals("2030-0002", id2, "Subsequent student ID must increment sequentially to 2030-0002");

        // Save another student with 2030-0091 to test multi-digit zero-padding
        User u2 = data.user(Role.STUDENT);
        Student s2 = new Student();
        s2.setUserId(u2.getUserId());
        s2.setStudentNumber("2030-0091");
        s2.setProgram("BS Information Technology");
        s2.setYearLevel(2);
        s2.setSection("B");
        s2.setEnrollmentStatus("Enrolled");
        studentRepo.save(s2);

        // Next ID should be 2030-0092
        String id3 = studentRepo.generateNextStudentNumber(testYear);
        assertEquals("2030-0092", id3, "Next student ID after 2030-0091 must be 2030-0092");
    }

    @Test
    void javaBridgeReturnsValidCurrentYearStudentIdPreview() {
        OnboardingView.JavaBridge bridge = new OnboardingView.JavaBridge(null, null);
        String previewId = bridge.getGeneratedStudentId();
        assertNotNull(previewId);
        int currentYear = Year.now().getValue();
        assertTrue(previewId.startsWith(currentYear + "-"), "Preview ID must begin with current year: " + currentYear);
        assertEquals(9, previewId.length(), "Preview ID must follow YYYY-NNNN (9 characters)");
        assertTrue(previewId.matches("\\d{4}-\\d{4}"), "Preview ID must match standardized YYYY-NNNN regex pattern");
    }

    @Test
    void databaseUniquenessConstraintEnforcedOnStudentNumber() {
        User u1 = data.user(Role.STUDENT);
        Student s1 = new Student();
        s1.setUserId(u1.getUserId());
        s1.setStudentNumber("2035-0042");
        s1.setProgram("BS Computer Science");
        s1.setYearLevel(1);
        s1.setSection("A");
        s1.setEnrollmentStatus("Enrolled");
        studentRepo.save(s1);

        // Attempting to save another student with the exact same student number must fail
        User u2 = data.user(Role.STUDENT);
        Student duplicate = new Student();
        duplicate.setUserId(u2.getUserId());
        duplicate.setStudentNumber("2035-0042");
        duplicate.setProgram("BS Information Technology");
        duplicate.setYearLevel(1);
        duplicate.setSection("A");
        duplicate.setEnrollmentStatus("Enrolled");

        assertThrows(Exception.class, () -> studentRepo.save(duplicate),
                "Database must strictly enforce UNIQUE constraint on student_number");
    }
}
