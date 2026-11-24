package com.nextit;

import com.nextit.controller.OnboardingView;
import com.nextit.model.Instructor;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.repository.JdbcInstructorRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Year;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FacultyIdGenerationTest {

    private static IntegrationTestData data;
    private static JdbcInstructorRepository instructorRepo;

    @BeforeAll
    static void setUp() throws SQLException {
        DatabaseTestServer.getInstance().useForApplication();
        data = new IntegrationTestData();
        instructorRepo = new JdbcInstructorRepository();
    }

    @Test
    void standardizedFacultyIdFormatAndUnambiguousCharacters() {
        int testYear = 2032;
        String id1 = instructorRepo.generateNextFacultyNumber(testYear);
        assertNotNull(id1);
        assertTrue(id1.startsWith("FAC-2032-"), "Faculty ID must begin with FAC-2032-: " + id1);
        assertTrue(id1.matches("^FAC-\\d{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$"),
                "Faculty ID must follow FAC-YYYY-XXXXXX pattern with unambiguous characters: " + id1);

        String suffix = id1.substring("FAC-2032-".length());
        assertFalse(suffix.contains("0") || suffix.contains("O") || suffix.contains("1")
                || suffix.contains("I") || suffix.contains("L"),
                "Suffix must exclude ambiguous characters (0, O, 1, I, L): " + suffix);

        // Verify uniqueness across multiple generations
        Set<String> generated = new HashSet<>();
        generated.add(id1);
        for (int i = 0; i < 20; i++) {
            String nextId = instructorRepo.generateNextFacultyNumber(testYear);
            assertTrue(generated.add(nextId), "Each generated faculty ID must be unique: " + nextId);
        }
    }

    @Test
    void javaBridgeReturnsValidCurrentYearFacultyIdPreview() {
        OnboardingView.JavaBridge bridge = new OnboardingView.JavaBridge(null, null);
        String previewId = bridge.getGeneratedFacultyId();
        assertNotNull(previewId);
        int currentYear = Year.now().getValue();
        assertTrue(previewId.startsWith("FAC-" + currentYear + "-"),
                "Preview faculty ID must begin with FAC-" + currentYear + "-: " + previewId);
        assertTrue(previewId.matches("^FAC-\\d{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$"),
                "Faculty ID preview must match standardized FAC-YYYY-XXXXXX pattern: " + previewId);
    }

    @Test
    void databaseUniquenessConstraintEnforcedOnEmployeeNumber() {
        User u1 = data.user(Role.INSTRUCTOR);
        Instructor i1 = new Instructor();
        i1.setUserId(u1.getUserId());
        i1.setEmployeeNumber("FAC-2036-7K8M9P");
        i1.setDepartment("Computer Studies");
        i1.setSpecialization("Database Systems");
        instructorRepo.save(i1);

        // Attempting to save another instructor with the exact same employee number must fail
        User u2 = data.user(Role.INSTRUCTOR);
        Instructor duplicate = new Instructor();
        duplicate.setUserId(u2.getUserId());
        duplicate.setEmployeeNumber("FAC-2036-7K8M9P");
        duplicate.setDepartment("Computer Studies");
        duplicate.setSpecialization("Web Development");

        assertThrows(Exception.class, () -> instructorRepo.save(duplicate),
                "Database must strictly enforce UNIQUE constraint on employee_number");
    }

    @Test
    void employeeNumberIsImmutableOnProfileUpdate() {
        User u = data.user(Role.INSTRUCTOR);
        String originalId = instructorRepo.generateNextFacultyNumber(2026);
        Instructor inst = new Instructor();
        inst.setUserId(u.getUserId());
        inst.setEmployeeNumber(originalId);
        inst.setDepartment("College of Engineering");
        inst.setSpecialization("Algorithms");
        instructorRepo.save(inst);

        // Attempt to change employee_number via update
        inst.setEmployeeNumber("FAC-2026-TAMPERED");
        inst.setDepartment("College of Computer Studies");
        inst.setSpecialization("Distributed Systems");
        instructorRepo.update(inst);

        // Reload and verify original employee number remained unchanged
        Instructor loaded = instructorRepo.findByUserId(u.getUserId()).orElseThrow();
        assertEquals(originalId, loaded.getEmployeeNumber(), "employee_number must be strictly immutable across updates");
        assertEquals("College of Computer Studies", loaded.getDepartment());
        assertEquals("Distributed Systems", loaded.getSpecialization());
    }
}
