package com.nextit;

import com.nextit.model.*;
import com.nextit.repository.*;
import com.nextit.service.RecommendationService;
import com.nextit.service.StudentService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationServiceTest {

    private static IntegrationTestData data;
    private static RecommendationService recommendationService;
    private static StudentService studentService;
    private static JdbcTechnicalSkillRepository techSkillRepo;
    private static JdbcStudentSkillRepository studentSkillRepo;
    private static JdbcProjectRepository projectRepo;
    private static JdbcJobOpportunityRepository opportunityRepo;
    private static JdbcApplicationRepository applicationRepo;
    private static JdbcPortfolioAccessRepository accessRepo;
    private static JdbcPortfolioRepository portfolioRepo;
    private static JdbcUserRepository userRepo;

    @BeforeAll
    static void init() throws SQLException {
        DatabaseTestServer.getInstance().useForApplication();
        data = new IntegrationTestData();
        recommendationService = new RecommendationService();
        studentService = new StudentService();
        techSkillRepo = new JdbcTechnicalSkillRepository();
        studentSkillRepo = new JdbcStudentSkillRepository();
        projectRepo = new JdbcProjectRepository();
        opportunityRepo = new JdbcJobOpportunityRepository();
        applicationRepo = new JdbcApplicationRepository();
        accessRepo = new JdbcPortfolioAccessRepository();
        portfolioRepo = new JdbcPortfolioRepository();
        userRepo = new JdbcUserRepository();
    }

    private TechnicalSkill findOrCreateTechSkill(String name, String category) {
        return techSkillRepo.findAll().stream()
                .filter(s -> s.getSkillName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    TechnicalSkill skill = new TechnicalSkill();
                    skill.setSkillName(name);
                    skill.setCategory(category);
                    techSkillRepo.save(skill);
                    return skill;
                });
    }

    @Test
    @DisplayName("Matches and ranks candidates based on 45% skills, 25% projects, 20% credentials, 10% readiness")
    void testWeightedScoringAndRanking() {
        Employer employer = data.employer();

        // 1. Create Job Opportunity requiring Java, Spring Boot, and MySQL
        JobOpportunity opp = new JobOpportunity();
        opp.setEmployerId(employer.getEmployerId());
        opp.setTitle("Senior Java & Spring Cloud Developer");
        opp.setDescription("Leading enterprise Java microservices architecture with SQL databases.");
        opp.setOpportunityType("Full-Time");
        opp.setRequiredSkills("Java, Spring Boot, MySQL");
        opp.setLocation("Remote");
        opp.setStatus("Open");
        opportunityRepo.save(opp);

        // 2. Candidate 1 (Top Match): Has Java, Spring Boot, Capstone project, Certified, Verified Identity
        Student studentTop = data.student();
        // Verify User identity
        userRepo.findById(studentTop.getUserId()).ifPresent(u -> {
            u.setVerificationStatus(VerificationStatus.VERIFIED);
            userRepo.update(u);
        });

        TechnicalSkill javaSkill = findOrCreateTechSkill("Java", "Programming Languages");
        TechnicalSkill springSkill = findOrCreateTechSkill("Spring Boot", "Frameworks");

        StudentSkill ssJava = new StudentSkill();
        ssJava.setStudentId(studentTop.getStudentId());
        ssJava.setSkillId(javaSkill.getSkillId());
        ssJava.setProficiencyLevel("Advanced");
        ssJava.setAssessmentScore(95.0);
        ssJava.setRemarks("Mastered core Java concurrency, collections, and JVM tuning.");
        studentSkillRepo.save(ssJava);

        StudentSkill ssSpring = new StudentSkill();
        ssSpring.setStudentId(studentTop.getStudentId());
        ssSpring.setSkillId(springSkill.getSkillId());
        ssSpring.setProficiencyLevel("Intermediate");
        ssSpring.setAssessmentScore(88.0);
        ssSpring.setRemarks("Demonstrated proficiency in Spring Boot microservices.");
        studentSkillRepo.save(ssSpring);

        // Add Capstone Project
        Project projTop = new Project();
        projTop.setStudentId(studentTop.getStudentId());
        projTop.setProjectTitle("Cloud Banking Platform");
        projTop.setProjectType("Capstone Project");
        projTop.setDescription("High throughput transactional system built with Java and Spring Boot.");
        projTop.setTechnologiesUsed("Java, Spring Boot, MySQL, Docker");
        projTop.setRepositoryUrl("https://github.com/test/cloud-banking");
        projTop.setStatus("Completed");
        projectRepo.save(projTop);

        // Add Certified Credential
        AcademicCredential credTop = new AcademicCredential();
        credTop.setStudentId(studentTop.getStudentId());
        credTop.setType(AcademicCredential.Type.CERTIFICATION);
        credTop.setTitle("Oracle Certified Professional: Java SE 17 Developer");
        credTop.setOrganization("Oracle");
        credTop.setDate(LocalDate.now().minusMonths(6));
        studentService.saveCredential(credTop);

        // 3. Candidate 2 (Low / Irrelevant Match): Only has unrelated skill (e.g. Photography), no projects
        Student studentLow = data.student();
        TechnicalSkill photoSkill = findOrCreateTechSkill("Photography", "Creative");
        StudentSkill ssPhoto = new StudentSkill();
        ssPhoto.setStudentId(studentLow.getStudentId());
        ssPhoto.setSkillId(photoSkill.getSkillId());
        ssPhoto.setProficiencyLevel("Beginner");
        studentSkillRepo.save(ssPhoto);

        // 4. Calculate recommendations
        List<StudentRecommendation> recommendations = recommendationService.getRecommendationsForOpportunity(opp.getOpportunityId());

        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());

        // Find candidate 1
        Optional<StudentRecommendation> topCandOpt = recommendations.stream()
                .filter(r -> r.getStudentId() == studentTop.getStudentId())
                .findFirst();
        assertTrue(topCandOpt.isPresent(), "Top student must be in recommendation results");
        StudentRecommendation topCand = topCandOpt.get();

        // Find candidate 2
        Optional<StudentRecommendation> lowCandOpt = recommendations.stream()
                .filter(r -> r.getStudentId() == studentLow.getStudentId())
                .findFirst();
        assertTrue(lowCandOpt.isPresent(), "Low student must be in recommendation results");
        StudentRecommendation lowCand = lowCandOpt.get();

        // Candidate 1 must score significantly higher than Candidate 2
        assertTrue(topCand.getMatchScore() > lowCand.getMatchScore(),
                "Candidate with matching skills, capstone project, and verified certs must rank higher than unrelated candidate");
        assertTrue(topCand.getMatchScore() >= 60, "Top candidate should have a high match score (>=60%), got: " + topCand.getMatchScore());
        assertTrue(topCand.getSkillsScore() > 0, "Top candidate skills score must be > 0");
        assertTrue(topCand.getProjectsScore() > 0, "Top candidate projects score must be > 0");
        assertTrue(topCand.getCredentialsScore() > 0, "Top candidate credentials score must be > 0");
        assertTrue(topCand.getReadinessScore() > 0, "Top candidate readiness score must be > 0");

        // Verify match reasons
        assertNotNull(topCand.getMatchReason());
        assertTrue(topCand.getMatchReason().toLowerCase().contains("skill") || topCand.getMatchReason().toLowerCase().contains("match"),
                "Match reason should explain skills match: " + topCand.getMatchReason());
    }

    @Test
    @DisplayName("Returns general IT recommendations when employer has no posted opportunities")
    void testGeneralRecommendationsFallback() {
        Employer employer = data.employer();
        data.student();

        List<StudentRecommendation> recommendations = recommendationService.getGeneralRecommendationsForEmployer(employer.getEmployerId());

        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty(), "General recommendations fallback should return active students");

        for (StudentRecommendation r : recommendations) {
            assertTrue(r.getMatchScore() >= 0 && r.getMatchScore() <= 100,
                    "Match score must be normalized between 0% and 100%");
            assertNotNull(r.getMatchReason());
        }
    }

    @Test
    @DisplayName("Non-blocking asynchronous recommendation execution completes without error")
    void testAsyncRecommendationsExecution() {
        Employer employer = data.employer();
        data.student();

        List<StudentRecommendation> asyncResults = recommendationService
                .getRecommendationsAsync(null, employer.getEmployerId())
                .join();

        assertNotNull(asyncResults);
        assertFalse(asyncResults.isEmpty());
    }

    @Test
    @DisplayName("Shortlisting/inviting candidate records an application and grants employer portfolio access")
    void testShortlistOrInviteCandidate() {
        Employer employer = data.employer();
        Student student = data.student();

        // Create an opportunity
        JobOpportunity opp = new JobOpportunity();
        opp.setEmployerId(employer.getEmployerId());
        opp.setTitle("Full Stack Engineer");
        opp.setDescription("React and Node.js full stack engineering.");
        opp.setOpportunityType("Full-Time");
        opp.setRequiredSkills("JavaScript, Node.js");
        opp.setLocation("Manila, Philippines");
        opp.setStatus("Open");
        opportunityRepo.save(opp);

        // Create a student portfolio
        Portfolio portfolio = new Portfolio();
        portfolio.setStudentId(student.getStudentId());
        portfolio.setHeadline("Portfolio of " + student.getStudentNumber());
        portfolio.setBio("Passionate developer");
        portfolio.setVisibility("Private");
        portfolioRepo.save(portfolio);

        // Action: Shortlist / Invite candidate
        String inviteNote = "We were impressed by your academic profile and invite you to interview.";
        recommendationService.shortlistOrInviteCandidate(employer.getEmployerId(), student.getStudentId(), opp.getOpportunityId(), inviteNote);

        // Verify Application created with status 'Shortlisted'
        List<Application> apps = applicationRepo.findByOpportunityId(opp.getOpportunityId());
        Optional<Application> candApp = apps.stream()
                .filter(a -> a.getStudentId() == student.getStudentId())
                .findFirst();
        assertTrue(candApp.isPresent(), "An application record must be registered for shortlisted student");
        assertEquals("Shortlisted", candApp.get().getStatus());

        // Verify Portfolio Access was automatically approved for employer
        Optional<PortfolioAccess> empAccess = accessRepo.findByPortfolioAndEmployer(portfolio.getPortfolioId(), employer.getEmployerId());
        assertTrue(empAccess.isPresent(), "Portfolio access record must be created for employer");
        assertEquals("Approved", empAccess.get().getAccessStatus());
    }
}
