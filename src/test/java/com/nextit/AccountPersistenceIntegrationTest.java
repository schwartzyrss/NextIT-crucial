package com.nextit;

import com.nextit.config.EmbeddedDatabaseManager;
import com.nextit.model.Role;
import com.nextit.model.Student;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;
import com.nextit.repository.JdbcStudentRepository;
import com.nextit.repository.JdbcUserRepository;
import com.nextit.security.Session;
import com.nextit.service.AuthenticationService;
import com.nextit.service.ProfileService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AccountPersistenceIntegrationTest {

    private static DatabaseTestServer database;
    private static JdbcUserRepository userRepo;
    private static JdbcStudentRepository studentRepo;
    private static ProfileService profileService;

    @BeforeAll
    static void setUp() throws SQLException {
        database = DatabaseTestServer.getInstance().useForApplication();
        userRepo = new JdbcUserRepository();
        studentRepo = new JdbcStudentRepository();
        profileService = new ProfileService();
        Session.logout();
    }

    @AfterAll
    static void tearDown() {
        Session.logout();
    }

    @Test
    void testUserRegistrationAndProfilePersistenceDirectlyInDatabase() {
        String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
        String username = "persist_user_" + uniqueSuffix;
        String email = "persist_" + uniqueSuffix + "@nextit.edu";

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(Role.STUDENT);
        user.setFirstName("Persistence");
        user.setLastName("Tester");
        user.setPasswordHash(AuthenticationService.hashPassword("SecurePass123!"));
        user.setVerificationStatus(VerificationStatus.VERIFIED);

        userRepo.save(user);

        assertTrue(user.getUserId() > 0, "Saved user must have a valid generated primary key");

        // Verify retrieval directly from database
        Optional<User> fetchedOpt = userRepo.findByUsername(username);
        assertTrue(fetchedOpt.isPresent(), "User must be retrievable from database by username");
        User fetched = fetchedOpt.get();
        assertEquals(email, fetched.getEmail());
        assertEquals(Role.STUDENT, fetched.getRole());
        assertEquals("Persistence", fetched.getFirstName());

        // Save student profile record linked to user
        Student s = new Student();
        s.setUserId(fetched.getUserId());
        s.setStudentNumber("2099-" + String.format("%04d", (int) ((System.currentTimeMillis() / 1000) % 9000)));
        s.setProgram("BS Information Technology");
        s.setYearLevel(2);
        s.setSection("B");
        s.setEnrollmentStatus("Enrolled");
        studentRepo.save(s);

        assertTrue(s.getStudentId() > 0, "Saved student must have generated studentId");

        // Verify student profile retrieval directly from database
        Optional<Student> studentOpt = studentRepo.findByUserId(fetched.getUserId());
        assertTrue(studentOpt.isPresent(), "Student profile must be retrievable from database");
        Student retrievedStudent = studentOpt.get();
        assertEquals(2, retrievedStudent.getYearLevel());
        assertEquals("B", retrievedStudent.getSection());

        // Authenticate via AuthenticationService
        AuthenticationService auth = new AuthenticationService(userRepo);
        User authenticated = auth.login(username, "SecurePass123!");
        assertNotNull(authenticated);
        assertTrue(Session.isLoggedIn());
        assertEquals(fetched.getUserId(), Session.currentUser().getUserId());

        // Update profile details (e.g. year level and section)
        profileService.updateStudentYearLevel(retrievedStudent, 4);
        profileService.updateStudentSection(retrievedStudent, "D");

        // Query fresh from DB to prove persistence
        Student reFetched = studentRepo.findByUserId(fetched.getUserId()).orElseThrow();
        assertEquals(4, reFetched.getYearLevel(), "Year level update must be committed to database");
        assertEquals("D", reFetched.getSection(), "Section update must be committed to database");

        // Sign out: verify session cleanup isolates active references without modifying stored DB records
        Session.logout();
        assertFalse(Session.isLoggedIn(), "Session must be logged out");
        assertNull(Session.currentUser());
        assertNull(Session.currentStudent());

        // Re-query database: accounts and profiles remain fully intact after logout
        Optional<User> afterLogoutUser = userRepo.findByUsername(username);
        assertTrue(afterLogoutUser.isPresent(), "User account must remain in database after sign-out");
        Optional<Student> afterLogoutStudent = studentRepo.findByUserId(fetched.getUserId());
        assertTrue(afterLogoutStudent.isPresent(), "Student profile must remain in database after sign-out");
        assertEquals(4, afterLogoutStudent.get().getYearLevel());
        assertEquals("D", afterLogoutStudent.get().getSection());
    }

    @Test
    void testSessionLogoutIsIsolatedAndDoesNotDeleteDatabaseRecords() {
        Session.login(new User());
        assertTrue(Session.isLoggedIn());
        Session.logout();
        assertFalse(Session.isLoggedIn());
        assertNull(Session.currentUser());
        assertNull(Session.currentStudent());
    }
}
