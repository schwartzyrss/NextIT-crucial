package com.nextit;

import com.nextit.controller.OnboardingView;
import com.nextit.model.Employer;
import com.nextit.model.Instructor;
import com.nextit.model.Role;
import com.nextit.model.Student;
import com.nextit.model.User;
import com.nextit.repository.JdbcEmployerRepository;
import com.nextit.repository.JdbcInstructorRepository;
import com.nextit.repository.JdbcStudentRepository;
import com.nextit.service.RoleIdService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class RoleIdGenerationTest {

    private static IntegrationTestData data;
    private static RoleIdService roleIdService;
    private static JdbcStudentRepository studentRepo;
    private static JdbcInstructorRepository instructorRepo;
    private static JdbcEmployerRepository employerRepo;

    @BeforeAll
    static void setUp() throws SQLException {
        DatabaseTestServer.getInstance().useForApplication();
        data = new IntegrationTestData();
        roleIdService = RoleIdService.getInstance();
        studentRepo = new JdbcStudentRepository();
        instructorRepo = new JdbcInstructorRepository();
        employerRepo = new JdbcEmployerRepository();
    }

    @Test
    void allThreeRoleFormatsAdhereToSpecification() {
        int year = 2026;
        String stuId = roleIdService.generateUniqueId(Role.STUDENT, year);
        String facId = roleIdService.generateUniqueId(Role.INSTRUCTOR, year);
        String empId = roleIdService.generateUniqueId(Role.EMPLOYER, year);

        assertTrue(stuId.startsWith("STU-2026-"), "Student ID must start with STU-2026-");
        assertTrue(facId.startsWith("FAC-2026-"), "Faculty ID must start with FAC-2026-");
        assertTrue(empId.startsWith("EMP-2026-"), "Employer ID must start with EMP-2026-");

        assertTrue(roleIdService.isValidRoleFormat(stuId, Role.STUDENT));
        assertTrue(roleIdService.isValidRoleFormat(facId, Role.INSTRUCTOR));
        assertTrue(roleIdService.isValidRoleFormat(empId, Role.EMPLOYER));
    }

    @Test
    void unambiguousCharactersOnlyInGeneratedSuffix() {
        for (Role role : List.of(Role.STUDENT, Role.INSTRUCTOR, Role.EMPLOYER)) {
            for (int i = 0; i < 25; i++) {
                String id = roleIdService.generateUniqueId(role, 2026);
                String suffix = id.substring(id.lastIndexOf('-') + 1);

                assertEquals(RoleIdService.SUFFIX_LENGTH, suffix.length());
                for (char c : suffix.toCharArray()) {
                    assertTrue(RoleIdService.UNAMBIGUOUS_ALPHABET.indexOf(c) >= 0,
                            "Character '" + c + "' must belong to unambiguous alphabet");
                    assertFalse("0O1IL".indexOf(c) >= 0,
                            "Suffix must not contain ambiguous character: " + c);
                }
            }
        }
    }

    @Test
    void serverSideAtomicReservationAndConflictHandling() {
        User u1 = data.user(Role.STUDENT);
        String reservedId = roleIdService.assignAndReserveId(Role.STUDENT, u1.getUserId(), null);
        assertNotNull(reservedId);
        assertTrue(roleIdService.isIdTakenOrReserved(reservedId), "ID must be marked as reserved in the registry");

        // Attempting to assign the same preferred ID to another user must detect collision and assign a new unique ID
        User u2 = data.user(Role.STUDENT);
        String secondaryId = roleIdService.assignAndReserveId(Role.STUDENT, u2.getUserId(), reservedId);
        assertNotEquals(reservedId, secondaryId, "Collision must be detected and a fresh unique ID assigned");
        assertTrue(secondaryId.startsWith("STU-"), "Secondary assigned ID must have valid format");
    }

    @Test
    void concurrentSignupGenerationAndReservationDoesNotCollide() throws Exception {
        int concurrency = 30;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Callable<String>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            tasks.add(() -> {
                User u = data.user(Role.STUDENT);
                return roleIdService.assignAndReserveId(Role.STUDENT, u.getUserId(), null);
            });
        }

        List<Future<String>> futures = executor.invokeAll(tasks);
        Set<String> assignedIds = new HashSet<>();
        for (Future<String> f : futures) {
            String id = f.get();
            assertNotNull(id);
            assertTrue(assignedIds.add(id), "Duplicate ID detected under concurrent signups: " + id);
        }
        executor.shutdown();
        assertEquals(concurrency, assignedIds.size());
    }

    @Test
    void deactivatedIdIsPermanentlyPreservedAndNeverReused() {
        User u = data.user(Role.EMPLOYER);
        String empId = roleIdService.assignAndReserveId(Role.EMPLOYER, u.getUserId(), null);
        assertTrue(roleIdService.isIdTakenOrReserved(empId));

        // Deactivate ID
        roleIdService.deactivateId(empId);

        // Verification check: deactivated ID is STILL taken and can never be recycled
        assertTrue(roleIdService.isIdTakenOrReserved(empId), "Deactivated ID must never be recycled");

        // Trying to assign it to another user must trigger collision resolution
        User u2 = data.user(Role.EMPLOYER);
        String nextEmpId = roleIdService.assignAndReserveId(Role.EMPLOYER, u2.getUserId(), empId);
        assertNotEquals(empId, nextEmpId, "Cannot reassign a deactivated ID");
    }

    @Test
    void javaBridgeGeneratesValidEmployerIdPreview() {
        OnboardingView.JavaBridge bridge = new OnboardingView.JavaBridge(null, null);
        String previewId = bridge.getGeneratedEmployerId();
        assertNotNull(previewId);
        int currentYear = Year.now().getValue();
        assertTrue(previewId.startsWith("EMP-" + currentYear + "-"), "Preview must start with EMP-YYYY-");
        assertTrue(previewId.matches("^EMP-\\d{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$"));
    }

    @Test
    void employerProfileUpdateDoesNotOverwriteAssignedId() {
        User u = data.user(Role.EMPLOYER);
        String assignedId = roleIdService.assignAndReserveId(Role.EMPLOYER, u.getUserId(), null);

        Employer emp = new Employer();
        emp.setUserId(u.getUserId());
        emp.setCompanyName("Original Corp");
        emp.setIndustry("Technology");
        emp.setContactPerson("Jane Doe");
        emp.setJobTitle(assignedId);
        employerRepo.save(emp);

        // Update company details
        emp.setCompanyName("Renamed Corp Inc.");
        emp.setIndustry("FinTech");
        emp.setJobTitle("EMP-9999-TAMPERED");
        employerRepo.update(emp);

        // Verify assigned ID persisted and was not overwritten
        Employer loaded = employerRepo.findByUserId(u.getUserId()).orElseThrow();
        assertEquals(assignedId, loaded.getJobTitle(), "Employer assigned ID must be immutable across profile updates");
        assertEquals("Renamed Corp Inc.", loaded.getCompanyName());
    }
}
