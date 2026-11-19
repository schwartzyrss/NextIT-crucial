package com.nextit;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.repository.*;
import com.nextit.service.CareerService;
import com.nextit.service.RecommendationLetterService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationLetterServiceTest {

    private static IntegrationTestData data;
    private static RecommendationLetterService recService;
    private static CareerService careerService;
    private static JdbcJobOpportunityRepository oppRepo;
    private static JdbcApplicationRepository appRepo;
    private static JdbcRecommendationLetterRepository letterRepo;

    @BeforeAll
    static void init() throws SQLException {
        DatabaseTestServer.getInstance().useForApplication();
        data = new IntegrationTestData();
        recService = new RecommendationLetterService();
        careerService = new CareerService();
        oppRepo = new JdbcJobOpportunityRepository();
        appRepo = new JdbcApplicationRepository();
        letterRepo = new JdbcRecommendationLetterRepository();
    }

    @Test
    @DisplayName("Student requests recommendation letter for an internship opportunity")
    void testStudentRequestRecommendation() {
        Student student = data.student();
        Instructor instructor = data.instructor();
        Employer employer = data.employer();

        JobOpportunity opp = new JobOpportunity();
        opp.setEmployerId(employer.getEmployerId());
        opp.setTitle("Cloud DevOps Internship");
        opp.setOpportunityType("Internship");
        opp.setDescription("Hands-on containerization and CI/CD pipelines.");
        opp.setRequiredSkills("Docker, Kubernetes, Linux");
        opp.setLocation("Taguig, Metro Manila");
        opp.setStatus("Open");
        oppRepo.save(opp);

        RecommendationLetter req = recService.requestRecommendation(
                student.getStudentId(),
                instructor.getInstructorId(),
                opp.getOpportunityId(),
                "Recommendation Request for Cloud DevOps",
                "Dear Professor, please highlight my Docker lab projects and cloud computing grades."
        );

        assertNotNull(req);
        assertTrue(req.getLetterId() > 0);
        assertEquals("REQUESTED", req.getStatus());
        assertEquals(student.getStudentId(), req.getStudentId());
        assertEquals(instructor.getInstructorId(), req.getInstructorId());
        assertEquals(opp.getOpportunityId(), req.getOpportunityId());
        assertNull(req.getApplicationId());
        assertNull(req.getIssuedAt());

        // Submitting an updated request with same instructor + opportunity updates existing request without duplicates
        RecommendationLetter updated = recService.requestRecommendation(
                student.getStudentId(),
                instructor.getInstructorId(),
                opp.getOpportunityId(),
                "Updated Title for Cloud DevOps",
                "Also highlight my recent Kubernetes certification."
        );

        assertEquals(req.getLetterId(), updated.getLetterId());
        assertEquals("Updated Title for Cloud DevOps", updated.getLetterTitle());
    }

    @Test
    @DisplayName("Instructor fulfills and signs a recommendation letter with verified highlights")
    void testInstructorFulfillAndSignRecommendation() {
        Student student = data.student();
        Instructor instructor = data.instructor();
        Employer employer = data.employer();

        JobOpportunity opp = new JobOpportunity();
        opp.setEmployerId(employer.getEmployerId());
        opp.setTitle("Full Stack Software Engineer");
        opp.setOpportunityType("Employment");
        opp.setDescription("Java, Spring Boot and modern frontend development.");
        opp.setStatus("Open");
        oppRepo.save(opp);

        RecommendationLetter req = recService.requestRecommendation(
                student.getStudentId(),
                instructor.getInstructorId(),
                opp.getOpportunityId(),
                "Recommendation Request for Full Stack Role",
                "Please endorse my capstone leadership."
        );

        // Instructor fulfills request
        String letterTitle = "Faculty Endorsement: " + student.getStudentNumber();
        String letterBody = "I unreservedly recommend this student. Their capstone architecture was rated in the top 5% of the cohort.";
        String highlights = "Java Concurrency, Spring Boot, Microservices, Capstone Team Lead";

        RecommendationLetter issued = recService.issueRecommendation(
                req.getLetterId(),
                instructor.getInstructorId(),
                letterTitle,
                letterBody,
                highlights
        );

        assertNotNull(issued);
        assertEquals("ISSUED", issued.getStatus());
        assertNotNull(issued.getIssuedAt());
        assertEquals(letterTitle, issued.getLetterTitle());
        assertEquals(letterBody, issued.getRecommendationBody());
        assertEquals(highlights, issued.getEndorsementHighlights());

        // Unauthorized instructor cannot modify or fulfill
        Instructor otherInstructor = data.instructor();
        assertThrows(AppException.class, () ->
                recService.issueRecommendation(req.getLetterId(), otherInstructor.getInstructorId(), "Title", "Body", "Highlights"));
    }

    @Test
    @DisplayName("Instructor proactively creates a general recommendation letter")
    void testProactiveRecommendationCreation() {
        Student student = data.student();
        Instructor instructor = data.instructor();

        RecommendationLetter letter = recService.createProactiveRecommendation(
                instructor.getInstructorId(),
                student.getStudentId(),
                null, // General endorsement for all opportunities
                "General Faculty Endorsement for IT Roles",
                "This student has demonstrated superior academic performance across database systems and network architecture.",
                "Database Design, SQL, Network Security"
        );

        assertNotNull(letter);
        assertTrue(letter.getLetterId() > 0);
        assertEquals("ISSUED", letter.getStatus());
        assertNull(letter.getOpportunityId());
        assertNotNull(letter.getIssuedAt());
        assertTrue(letter.isIssued());
    }

    @Test
    @DisplayName("Student attaches issued recommendation letter to an internship application")
    void testAttachLetterToApplication() {
        Student student = data.student();
        Instructor instructor = data.instructor();
        Employer employer = data.employer();

        JobOpportunity opp = new JobOpportunity();
        opp.setEmployerId(employer.getEmployerId());
        opp.setTitle("Cybersecurity Analyst Intern");
        opp.setOpportunityType("Internship");
        opp.setDescription("Network vulnerability assessment and incident response.");
        opp.setStatus("Open");
        oppRepo.save(opp);

        // 1. Issue letter
        RecommendationLetter letter = recService.createProactiveRecommendation(
                instructor.getInstructorId(),
                student.getStudentId(),
                opp.getOpportunityId(),
                "Cybersecurity Endorsement",
                "High technical aptitude in security protocols.",
                "Network Security, Vulnerability Analysis"
        );

        // 2. Submit application
        Application app = new Application();
        app.setStudentId(student.getStudentId());
        app.setOpportunityId(opp.getOpportunityId());
        app.setRemarks("Applying with attached faculty endorsement.");
        careerService.apply(app);

        // 3. Attach letter
        RecommendationLetter attached = recService.attachLetterToApplication(
                letter.getLetterId(),
                app.getApplicationId(),
                student.getStudentId()
        );

        assertNotNull(attached);
        assertEquals("ATTACHED", attached.getStatus());
        assertEquals(app.getApplicationId(), attached.getApplicationId());
        assertTrue(attached.isAttached());

        // Verify retrieval by application id
        List<RecommendationLetter> appLetters = recService.getLettersForApplication(app.getApplicationId());
        assertFalse(appLetters.isEmpty());
        assertEquals(letter.getLetterId(), appLetters.get(0).getLetterId());

        // Another student cannot attach someone else's letter
        Student anotherStudent = data.student();
        assertThrows(AppException.class, () ->
                recService.attachLetterToApplication(letter.getLetterId(), app.getApplicationId(), anotherStudent.getStudentId()));
    }

    @Test
    @DisplayName("Asynchronous query and execution helpers work non-blockingly")
    void testAsyncHelpers() {
        Student student = data.student();
        Instructor instructor = data.instructor();

        RecommendationLetter letter = recService.requestRecommendationAsync(
                student.getStudentId(),
                instructor.getInstructorId(),
                null,
                "Async Recommendation Test",
                "Notes for async test"
        ).join();

        assertNotNull(letter);
        assertEquals("REQUESTED", letter.getStatus());

        List<RecommendationLetter> studentLetters = recService.getLettersForStudentAsync(student.getStudentId()).join();
        assertFalse(studentLetters.isEmpty());

        List<RecommendationLetter> instructorLetters = recService.getLettersForInstructorAsync(instructor.getInstructorId()).join();
        assertFalse(instructorLetters.isEmpty());

        // Cancel / delete pending request
        recService.deleteLetter(letter.getLetterId(), student.getStudentId(), true);
        Optional<RecommendationLetter> deleted = recService.findById(letter.getLetterId());
        assertTrue(deleted.isEmpty());
    }
}
