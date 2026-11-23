package com.nextit;

import com.nextit.exception.AppException;
import com.nextit.model.Employer;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.repository.JdbcEmployerRepository;
import com.nextit.repository.JdbcUserRepository;
import com.nextit.service.ProfileService;
import com.nextit.util.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EmployerProfileMappingTest {

    private static DatabaseTestServer server;
    private static JdbcEmployerRepository employerRepo;
    private static JdbcUserRepository userRepo;
    private static ProfileService profileService;

    @BeforeAll
    static void setUp() throws SQLException {
        server = DatabaseTestServer.getInstance().useForApplication();
        employerRepo = new JdbcEmployerRepository();
        userRepo = new JdbcUserRepository();
        profileService = new ProfileService();
    }

    @Test
    @DisplayName("Phone validation strictly rejects job titles and accepts valid phone formats")
    void testPhoneValidationRules() {
        // Must reject job titles
        assertFalse(Validator.isValidPhoneNumber("IT DIRECTOR"));
        assertFalse(Validator.isValidPhoneNumber("HR Manager / SEC-1234"));
        assertFalse(Validator.isValidPhoneNumber("Chief Information Officer"));

        assertThrows(AppException.class, () -> Validator.optionalPhoneNumber("IT DIRECTOR", "Contact number"));
        assertThrows(AppException.class, () -> Validator.optionalPhoneNumber("HR Manager / SEC-1234", "Contact number"));

        // Must accept valid phone numbers and empty/null
        assertTrue(Validator.isValidPhoneNumber("+63 912 345 6789"));
        assertTrue(Validator.isValidPhoneNumber("+639123456789"));
        assertTrue(Validator.isValidPhoneNumber("09171234567"));
        assertTrue(Validator.isValidPhoneNumber("+1-555-0100"));
        assertTrue(Validator.isValidPhoneNumber(null));
        assertTrue(Validator.isValidPhoneNumber(""));
        assertTrue(Validator.isValidPhoneNumber("   "));

        assertDoesNotThrow(() -> Validator.optionalPhoneNumber("+63 912 345 6789", "Contact number"));
        assertDoesNotThrow(() -> Validator.optionalPhoneNumber(null, "Contact number"));
        assertDoesNotThrow(() -> Validator.optionalPhoneNumber("", "Contact number"));
    }

    @Test
    @DisplayName("Employer schema and repository correctly map Job Title and Contact Number separately")
    void testEmployerMappingAndPersistence() {
        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        User user = new User();
        user.setUsername("emp_map_" + suffix);
        user.setPasswordHash("hash123456");
        user.setEmail("emp_map_" + suffix + "@company.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole(Role.EMPLOYER);
        userRepo.save(user);

        // Simulate registration with Corporate Contact / Title -> Job Title, and empty Contact Number
        Employer emp = new Employer();
        emp.setUserId(user.getUserId());
        emp.setCompanyName("Acme Tech " + suffix);
        emp.setIndustry("Enterprise Software");
        emp.setContactPerson(user.getFullName());
        emp.setJobTitle("IT DIRECTOR");
        emp.setContactNumber(""); // Defaults to empty
        emp.setAddress("Innovation Hub");
        employerRepo.save(emp);

        assertTrue(emp.getEmployerId() > 0);

        // Fetch back from database
        Optional<Employer> loadedOpt = employerRepo.findById(emp.getEmployerId());
        assertTrue(loadedOpt.isPresent());
        Employer loaded = loadedOpt.get();

        assertEquals("Acme Tech " + suffix, loaded.getCompanyName());
        assertEquals("Enterprise Software", loaded.getIndustry());
        assertEquals("Jane Doe", loaded.getContactPerson());
        assertEquals("IT DIRECTOR", loaded.getJobTitle(), "Corporate title must map to jobTitle");
        assertEquals("", loaded.getContactNumber(), "Contact number must remain empty on signup");

        // Profile update with a valid phone number
        loaded.setContactNumber("+63 917 123 4567");
        loaded.setJobTitle("VP OF INFORMATION TECHNOLOGY");
        assertDoesNotThrow(() -> profileService.updateEmployer(loaded));

        Employer updated = employerRepo.findById(loaded.getEmployerId()).orElseThrow();
        assertEquals("VP OF INFORMATION TECHNOLOGY", updated.getJobTitle());
        assertEquals("+63 917 123 4567", updated.getContactNumber());

        // Profile update trying to put a job title in contact number must be rejected
        updated.setContactNumber("DIRECTOR OF OPERATIONS");
        AppException ex = assertThrows(AppException.class, () -> profileService.updateEmployer(updated));
        assertTrue(ex.getMessage().contains("valid phone number"));
    }
}
