package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.repository.*;
import com.nextit.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing Faculty Recommendation Letters for Opportunities (Internships & Employment).
 */
public class RecommendationLetterService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationLetterService.class);

    private final JdbcRecommendationLetterRepository letterRepo;
    private final JdbcStudentRepository studentRepo;
    private final JdbcInstructorRepository instructorRepo;
    private final JdbcJobOpportunityRepository oppRepo;
    private final JdbcApplicationRepository appRepo;

    public RecommendationLetterService() {
        this.letterRepo = new JdbcRecommendationLetterRepository();
        this.studentRepo = new JdbcStudentRepository();
        this.instructorRepo = new JdbcInstructorRepository();
        this.oppRepo = new JdbcJobOpportunityRepository();
        this.appRepo = new JdbcApplicationRepository();
    }

    public RecommendationLetterService(
            JdbcRecommendationLetterRepository letterRepo,
            JdbcStudentRepository studentRepo,
            JdbcInstructorRepository instructorRepo,
            JdbcJobOpportunityRepository oppRepo,
            JdbcApplicationRepository appRepo) {
        this.letterRepo = letterRepo;
        this.studentRepo = studentRepo;
        this.instructorRepo = instructorRepo;
        this.oppRepo = oppRepo;
        this.appRepo = appRepo;
    }

    /**
     * Student requests a recommendation letter from a specific instructor.
     */
    public RecommendationLetter requestRecommendation(int studentId, int instructorId, Integer opportunityId,
                                                      String letterTitle, String requestNote) {
        if (studentId <= 0 || studentRepo.findById(studentId).isEmpty()) {
            throw new AppException("Invalid student selected.");
        }
        if (instructorId <= 0 || instructorRepo.findById(instructorId).isEmpty()) {
            throw new AppException("Please select a valid faculty instructor.");
        }
        if (opportunityId != null && opportunityId > 0 && oppRepo.findById(opportunityId).isEmpty()) {
            throw new AppException("Target job or internship opportunity was not found.");
        }

        Validator.notBlank(letterTitle, "Letter Title");
        Validator.maxLength(letterTitle, 200, "Letter Title");
        Validator.maxLength(requestNote, 16000, "Request details");

        // Check for existing pending request
        Optional<RecommendationLetter> existing = letterRepo
                .findByStudentAndInstructorAndOpportunity(studentId, instructorId, opportunityId);

        if (existing.isPresent()) {
            RecommendationLetter letter = existing.get();
            if ("REQUESTED".equalsIgnoreCase(letter.getStatus())) {
                letter.setLetterTitle(letterTitle);
                letter.setRecommendationBody(requestNote == null || requestNote.isBlank() ? "Recommendation requested by student." : requestNote);
                letterRepo.update(letter);
                log.info("Updated existing pending recommendation request #{} for student #{}", letter.getLetterId(), studentId);
                return letter;
            }
        }

        RecommendationLetter letter = new RecommendationLetter();
        letter.setStudentId(studentId);
        letter.setInstructorId(instructorId);
        letter.setOpportunityId(opportunityId != null && opportunityId > 0 ? opportunityId : null);
        letter.setLetterTitle(letterTitle);
        letter.setRecommendationBody(requestNote == null || requestNote.isBlank() ? "Recommendation requested by student." : requestNote);
        letter.setStatus("REQUESTED");
        letter.setCreatedAt(LocalDateTime.now());

        letterRepo.save(letter);
        log.info("Created new recommendation letter request #{} from student #{} to instructor #{}",
                letter.getLetterId(), studentId, instructorId);
        return letter;
    }

    /**
     * Instructor fulfills a recommendation request by composing and signing the formal letter.
     */
    public RecommendationLetter issueRecommendation(int letterId, int instructorId, String letterTitle,
                                                    String recommendationBody, String endorsementHighlights) {
        RecommendationLetter letter = letterRepo.findById(letterId)
                .orElseThrow(() -> new AppException("Recommendation request not found."));

        if (letter.getInstructorId() != instructorId) {
            throw new AppException("You can only fulfill recommendation requests assigned to you.");
        }

        Validator.notBlank(letterTitle, "Recommendation Title");
        Validator.maxLength(letterTitle, 200, "Recommendation Title");
        Validator.notBlank(recommendationBody, "Recommendation Letter Body");
        Validator.maxLength(recommendationBody, 16000, "Recommendation Letter Body");
        Validator.maxLength(endorsementHighlights, 1000, "Endorsement Highlights");

        letter.setLetterTitle(letterTitle);
        letter.setRecommendationBody(recommendationBody);
        letter.setEndorsementHighlights(endorsementHighlights);
        letter.setStatus("ISSUED");
        letter.setIssuedAt(LocalDateTime.now());

        letterRepo.update(letter);
        log.info("Instructor #{} issued recommendation letter #{} for student #{}",
                instructorId, letterId, letter.getStudentId());
        return letter;
    }

    /**
     * Instructor proactively composes and signs a recommendation letter for a student.
     */
    public RecommendationLetter createProactiveRecommendation(int instructorId, int studentId, Integer opportunityId,
                                                              String letterTitle, String recommendationBody,
                                                              String endorsementHighlights) {
        if (studentId <= 0 || studentRepo.findById(studentId).isEmpty()) {
            throw new AppException("Please select an active student.");
        }
        if (instructorId <= 0 || instructorRepo.findById(instructorId).isEmpty()) {
            throw new AppException("Instructor account not found.");
        }
        if (opportunityId != null && opportunityId > 0 && oppRepo.findById(opportunityId).isEmpty()) {
            throw new AppException("Opportunity not found.");
        }

        Validator.notBlank(letterTitle, "Recommendation Title");
        Validator.maxLength(letterTitle, 200, "Recommendation Title");
        Validator.notBlank(recommendationBody, "Recommendation Letter Body");
        Validator.maxLength(recommendationBody, 16000, "Recommendation Letter Body");
        Validator.maxLength(endorsementHighlights, 1000, "Endorsement Highlights");

        RecommendationLetter letter = new RecommendationLetter();
        letter.setInstructorId(instructorId);
        letter.setStudentId(studentId);
        letter.setOpportunityId(opportunityId != null && opportunityId > 0 ? opportunityId : null);
        letter.setLetterTitle(letterTitle);
        letter.setRecommendationBody(recommendationBody);
        letter.setEndorsementHighlights(endorsementHighlights);
        letter.setStatus("ISSUED");
        letter.setCreatedAt(LocalDateTime.now());
        letter.setIssuedAt(LocalDateTime.now());

        letterRepo.save(letter);
        log.info("Instructor #{} proactively created recommendation letter #{} for student #{}",
                instructorId, letter.getLetterId(), studentId);
        return letter;
    }

    /**
     * Attaches an issued recommendation letter to an application submission.
     */
    public RecommendationLetter attachLetterToApplication(int letterId, int applicationId, int studentId) {
        RecommendationLetter letter = letterRepo.findById(letterId)
                .orElseThrow(() -> new AppException("Recommendation letter not found."));

        if (letter.getStudentId() != studentId) {
            throw new AppException("You can only attach your own recommendation letters.");
        }
        if (!letter.isIssued()) {
            throw new AppException("Only issued recommendation letters can be attached to an application.");
        }

        Application application = appRepo.findById(applicationId)
                .orElseThrow(() -> new AppException("Application record not found."));

        if (application.getStudentId() != studentId) {
            throw new AppException("You can only attach letters to your own application.");
        }

        letter.setApplicationId(applicationId);
        if (letter.getOpportunityId() == null) {
            letter.setOpportunityId(application.getOpportunityId());
        }
        letter.setStatus("ATTACHED");
        letterRepo.update(letter);
        log.info("Attached recommendation letter #{} to application #{}", letterId, applicationId);
        return letter;
    }

    public List<RecommendationLetter> findAll() {
        return letterRepo.findAll();
    }

    public List<RecommendationLetter> getLettersForStudent(int studentId) {
        return letterRepo.findByStudentId(studentId);
    }

    public List<RecommendationLetter> getLettersForInstructor(int instructorId) {
        return letterRepo.findByInstructorId(instructorId);
    }

    public List<RecommendationLetter> getLettersForOpportunity(int opportunityId) {
        return letterRepo.findByOpportunityId(opportunityId);
    }

    public List<RecommendationLetter> getLettersForApplication(int applicationId) {
        return letterRepo.findByApplicationId(applicationId);
    }

    public Optional<RecommendationLetter> findById(int letterId) {
        return letterRepo.findById(letterId);
    }

    public void deleteLetter(int letterId, int actingUserId, boolean isStudent) {
        RecommendationLetter letter = letterRepo.findById(letterId)
                .orElseThrow(() -> new AppException("Recommendation letter not found."));
        // Student can only cancel pending requests; instructor can delete requests/letters assigned to them
        if (isStudent && letter.getStudentId() != actingUserId) {
            throw new AppException("You can only cancel your own recommendation requests.");
        }
        if (!isStudent && letter.getInstructorId() != actingUserId) {
            throw new AppException("You can only delete recommendations assigned to you.");
        }
        letterRepo.delete(letterId);
        log.info("Deleted recommendation letter #{}", letterId);
    }

    // --- Asynchronous Execution Wrappers for JavaFX UI ---

    public CompletableFuture<List<RecommendationLetter>> getLettersForStudentAsync(int studentId) {
        return CompletableFuture.supplyAsync(() -> getLettersForStudent(studentId));
    }

    public CompletableFuture<List<RecommendationLetter>> getLettersForInstructorAsync(int instructorId) {
        return CompletableFuture.supplyAsync(() -> getLettersForInstructor(instructorId));
    }

    public CompletableFuture<List<RecommendationLetter>> getLettersForApplicationAsync(int applicationId) {
        return CompletableFuture.supplyAsync(() -> getLettersForApplication(applicationId));
    }

    public CompletableFuture<RecommendationLetter> requestRecommendationAsync(
            int studentId, int instructorId, Integer opportunityId, String letterTitle, String requestNote) {
        return CompletableFuture.supplyAsync(() ->
                requestRecommendation(studentId, instructorId, opportunityId, letterTitle, requestNote));
    }

    public CompletableFuture<RecommendationLetter> issueRecommendationAsync(
            int letterId, int instructorId, String letterTitle, String recommendationBody, String endorsementHighlights) {
        return CompletableFuture.supplyAsync(() ->
                issueRecommendation(letterId, instructorId, letterTitle, recommendationBody, endorsementHighlights));
    }

    public CompletableFuture<RecommendationLetter> createProactiveRecommendationAsync(
            int instructorId, int studentId, Integer opportunityId, String letterTitle, String recommendationBody, String endorsementHighlights) {
        return CompletableFuture.supplyAsync(() ->
                createProactiveRecommendation(instructorId, studentId, opportunityId, letterTitle, recommendationBody, endorsementHighlights));
    }

    public CompletableFuture<RecommendationLetter> attachLetterToApplicationAsync(
            int letterId, int applicationId, int studentId) {
        return CompletableFuture.supplyAsync(() ->
                attachLetterToApplication(letterId, applicationId, studentId));
    }
}
