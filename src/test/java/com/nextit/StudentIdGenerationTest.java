package com.nextit;

import com.nextit.controller.OnboardingView;
import com.nextit.model.Role;
import com.nextit.model.Student;
import com.nextit.model.User;
import com.nextit.repository.JdbcStudentRepository;
import com.nextit.service.RoleIdService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Year;
import java.util.HashSet;
import java.util.Set;

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
    void standardizedStudentIdFormatAndUnambiguousCharacters() {
        int testYear = 2030;
        String id1 = studentRepo.generateNextStudentNumber(testYear);
        assertNotNull(id1);
        assertTrue(id1.startsWith("STU-2030-"), "Student ID must start with STU-2030-: " + id1);
        assertTrue(id1.matches("^STU-\\d{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$"),
                "Student ID must follow STU-YYYY-XXXXXX pattern with unambiguous characters: " + id1);

        // Ensure ambiguous characters (0, O, 1, I, L) are never present in suffix
        String suffix = id1.substring("STU-2030-".length());
        assertFalse(suffix.contains("0") || suffix.contains("O") || suffix.contains("1")
                || suffix.contains("I") || suffix.contains("L"),
                "Suffix must exclude ambiguous characters (0, O, 1, I, L): " + suffix);

        // Generate a batch of student IDs to verify collision safety and uniqueness
        Set<String> generated = new HashSet<>();
        generated.add(id1);
        for (int i = 0; i < 20; i++) {
            String nextId = studentRepo.generateNextStudentNumber(testYear);
            assertTrue(generated.add(nextId), "Each generated student ID must be unique: " + nextId);
        }
    }

    @Test
    void javaBridgeReturnsValidCurrentYearStudentIdPreview() {
        OnboardingView.JavaBridge bridge = new OnboardingView.JavaBridge(null, null);
        String previewId = bridge.getGeneratedStudentId();
        assertNotNull(previewId);
        int currentYear = Year.now().getValue();
        assertTrue(previewId.startsWith("STU-" + currentYear + "-"),
                "Preview ID must begin with STU-" + currentYear + "-: " + previewId);
        assertTrue(previewId.matches("^STU-\\d{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$"),
                "Preview ID must match standardized STU-YYYY-XXXXXX pattern: " + previewId);
    }

    @Test
    void databaseUniquenessConstraintEnforcedOnStudentNumber() {
        User u1 = data.user(Role.STUDENT);
        Student s1 = new Student();
        s1.setUserId(u1.getUserId());
        s1.setStudentNumber("STU-2035-7K8M9P");
        s1.setProgram("BS Computer Science");
        s1.setYearLevel(1);
        s1.setSection("A");
        s1.setEnrollmentStatus("Enrolled");
        studentRepo.save(s1);

        // Attempting to save another student with the exact same student number must fail
        User u2 = data.user(Role.STUDENT);
        Student duplicate = new Student();
        duplicate.setUserId(u2.getUserId());
        duplicate.setStudentNumber("STU-2035-7K8M9P");
        duplicate.setProgram("BS Information Technology");
        duplicate.setYearLevel(1);
        duplicate.setSection("A");
        duplicate.setEnrollmentStatus("Enrolled");

        assertThrows(Exception.class, () -> studentRepo.save(duplicate),
                "Database must strictly enforce UNIQUE constraint on student_number");
    }

    @Test
    void studentNumberIsImmutableOnProfileUpdate() {
        User u = data.user(Role.STUDENT);
        String originalId = studentRepo.generateNextStudentNumber(2026);
        Student s = new Student();
        s.setUserId(u.getUserId());
        s.setStudentNumber(originalId);
        s.setProgram("BS Computer Science");
        s.setYearLevel(1);
        s.setSection("A");
        s.setEnrollmentStatus("Enrolled");
        studentRepo.save(s);

        // Attempt to change student_number via update
        s.setStudentNumber("STU-2026-MUTATED");
        s.setProgram("BS Cybersecurity");
        s.setYearLevel(2);
        studentRepo.update(s);

        // Reload and verify original student number remained unchanged
        Student loaded = studentRepo.findByUserId(u.getUserId()).orElseThrow();
        assertEquals(originalId, loaded.getStudentNumber(), "student_number must be strictly immutable across updates");
        assertEquals("BS Cybersecurity", loaded.getProgram());
        assertEquals(2, loaded.getYearLevel());
    }
}
