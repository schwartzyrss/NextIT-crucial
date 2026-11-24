package com.nextit;

import com.nextit.config.EmbeddedDatabaseManager;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;
import com.nextit.repository.JdbcUserRepository;
import com.nextit.security.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddedDatabasePersistenceTest {

    private Path tempPersistentDataDir;

    @BeforeEach
    void setUp() throws Exception {
        Session.logout();
        tempPersistentDataDir = Files.createTempDirectory("nextit-persist-test-dir");
        System.setProperty("NEXTIT_DATA_DIR", tempPersistentDataDir.toAbsolutePath().toString());
    }

    @AfterEach
    void tearDown() {
        EmbeddedDatabaseManager.stop();
        System.clearProperty("NEXTIT_DATA_DIR");
        System.clearProperty("NEXTIT_DB_URL");
        System.clearProperty("NEXTIT_DB_USER");
        System.clearProperty("NEXTIT_DB_PASSWORD");
        Session.logout();
    }

    @Test
    void testUserDataPersistsAcrossEmbeddedDatabaseRestarts() throws Exception {
        // Step 1: Start embedded database on persistent directory
        EmbeddedDatabaseManager.ensureStarted();
        assertTrue(EmbeddedDatabaseManager.isStarted(), "Embedded DB must be started");

        JdbcUserRepository userRepo = new JdbcUserRepository();
        String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
        String username = "persist_test_" + uniqueSuffix;
        String email = "persist_" + uniqueSuffix + "@nextit.edu";

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(Role.STUDENT);
        user.setFirstName("Permanent");
        user.setLastName("Account");
        user.setPasswordHash("hashed_pass");
        user.setVerificationStatus(VerificationStatus.VERIFIED);

        userRepo.save(user);
        assertTrue(user.getUserId() > 0, "User must have a generated ID");

        // Verify account exists in this session
        Optional<User> foundFirstSession = userRepo.findByUsername(username);
        assertTrue(foundFirstSession.isPresent());
        assertEquals("Permanent", foundFirstSession.get().getFirstName());

        // Step 2: Simulate application exit/shutdown
        EmbeddedDatabaseManager.stop();
        assertFalse(EmbeddedDatabaseManager.isStarted(), "Embedded DB must be stopped");

        // Step 3: Simulate application re-launch
        EmbeddedDatabaseManager.ensureStarted();
        assertTrue(EmbeddedDatabaseManager.isStarted(), "Embedded DB must be restarted");

        // Step 4: Verify that the account SURVIVED the exit and restart!
        JdbcUserRepository reloadedRepo = new JdbcUserRepository();
        Optional<User> foundSecondSession = reloadedRepo.findByUsername(username);
        assertTrue(foundSecondSession.isPresent(), "Account must persist across application exit/restart!");
        assertEquals(email, foundSecondSession.get().getEmail());
        assertEquals(Role.STUDENT, foundSecondSession.get().getRole());
        assertEquals("Permanent", foundSecondSession.get().getFirstName());
        assertEquals("Account", foundSecondSession.get().getLastName());
    }
}
