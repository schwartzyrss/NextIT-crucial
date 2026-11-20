package com.nextit;

import com.nextit.controller.OnboardingView;
import com.nextit.model.Student;
import com.nextit.service.AdminService;
import com.nextit.util.Ui;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraduationYearSelectionTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    void testGraduationYearComboBoxRangeAndPrompt() {
        ComboBox<String> combo = Ui.createGraduationYearComboBox();
        assertNotNull(combo);
        assertEquals("Select graduation year (e.g., Class of 2026)", combo.getPromptText());

        int currentYear = Year.now().getValue();
        List<String> items = combo.getItems();
        assertEquals(7, items.size(), "Should offer 7 options from current year to current year + 6");

        assertEquals("Class of " + currentYear, items.get(0));
        assertEquals("Class of " + (currentYear + 1), items.get(1));
        assertEquals("Class of " + (currentYear + 2), items.get(2));
        assertEquals("Class of " + (currentYear + 3), items.get(3));
        assertEquals("Class of " + (currentYear + 4), items.get(4));
        assertEquals("Class of " + (currentYear + 5), items.get(5));
        assertEquals("Class of " + (currentYear + 6), items.get(6));

        // OnboardingView factory delegator check
        ComboBox<String> onboardingCombo = OnboardingView.createGraduationYearComboBox();
        assertNotNull(onboardingCombo);
        assertEquals(combo.getPromptText(), onboardingCombo.getPromptText());
        assertEquals(items, onboardingCombo.getItems());
    }

    @Test
    void testFormatYearDisplay() {
        assertEquals("Year 1", Ui.formatYearDisplay(1));
        assertEquals("Year 4", Ui.formatYearDisplay(4));
        int currentYear = Year.now().getValue();
        assertEquals("Class of " + currentYear, Ui.formatYearDisplay(currentYear));
        assertEquals("Class of 2026", Ui.formatYearDisplay(2026));
    }

    @Test
    void testAdminServiceValidationSupportsGraduationYear() {
        AdminService adminService = new AdminService();
        Student s = new Student();
        s.setStudentNumber("2026-TEST");
        s.setProgram("BS Information Technology");
        s.setSection("A");
        s.setEnrollmentStatus("Enrolled");

        s.setYearLevel(Year.now().getValue());
        // Should not throw AppException for graduation year
        // updateStudent checks yearLevel between 1 and 10 OR 2000 and 2100
        assertDoesNotThrow(() -> {
            try {
                adminService.updateStudent(s);
            } catch (com.nextit.exception.AppException ex) {
                // It might fail on database lookup if not connected, but should NOT fail on "Year level must be..."
                assertFalse(ex.getMessage().contains("Year level must be"), "Should accept graduation year");
            }
        });
    }

    @Test
    void testGraduationYearsParsing() {
        String inputClass = "Class of 2026";
        int parsed = Integer.parseInt(inputClass.replaceAll("\\D", ""));
        assertEquals(2026, parsed);

        String inputYearOnly = "2026";
        int parsedYearOnly = Integer.parseInt(inputYearOnly.replaceAll("\\D", ""));
        assertEquals(2026, parsedYearOnly);
    }
}
