package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Intelligent Student Recommendation Engine for Employers.
 * Evaluates active student profiles against employer job opportunities
 * using weighted multi-criteria scoring:
 * - Verified Skills Match (45%)
 * - Projects & Portfolio Relevance (25%)
 * - Achievements & Certifications (20%)
 * - Academic Profile & Readiness (10%)
 */
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Pattern SPLIT_DELIMITERS = Pattern.compile("[,;/|\n]+");

    private final JdbcStudentRepository studentRepo;
    private final JdbcUserRepository userRepo;
    private final JdbcStudentSkillRepository studentSkillRepo;
    private final JdbcTechnicalSkillRepository technicalSkillRepo;
    private final JdbcProjectRepository projectRepo;
    private final JdbcPortfolioRepository portfolioRepo;
    private final JdbcPortfolioAccessRepository accessRepo;
    private final JdbcJobOpportunityRepository opportunityRepo;
    private final JdbcApplicationRepository applicationRepo;
    private final StudentService studentService;

    public RecommendationService() {
        this.studentRepo = new JdbcStudentRepository();
        this.userRepo = new JdbcUserRepository();
        this.studentSkillRepo = new JdbcStudentSkillRepository();
        this.technicalSkillRepo = new JdbcTechnicalSkillRepository();
        this.projectRepo = new JdbcProjectRepository();
        this.portfolioRepo = new JdbcPortfolioRepository();
        this.accessRepo = new JdbcPortfolioAccessRepository();
        this.opportunityRepo = new JdbcJobOpportunityRepository();
        this.applicationRepo = new JdbcApplicationRepository();
        this.studentService = new StudentService();
    }

    /**
     * Recommends ranked students tailored specifically for a given JobOpportunity.
     */
    public List<StudentRecommendation> getRecommendationsForOpportunity(int opportunityId) {
        JobOpportunity opportunity = opportunityRepo.findById(opportunityId)
                .orElseThrow(() -> new AppException("Job opportunity not found."));
        return calculateRecommendations(opportunity, opportunity.getEmployerId());
    }

    /**
     * Recommends top-matching students across an employer's open opportunities or general IT competencies.
     */
    public List<StudentRecommendation> getGeneralRecommendationsForEmployer(int employerId) {
        List<JobOpportunity> employerOpps = opportunityRepo.findByEmployerId(employerId).stream()
                .filter(o -> "Open".equalsIgnoreCase(o.getStatus()))
                .toList();
        JobOpportunity primaryOpportunity = employerOpps.isEmpty() ? null : employerOpps.get(0);
        return calculateRecommendations(primaryOpportunity, employerId);
    }

    /**
     * Non-blocking asynchronous query for recommendations.
     */
    public CompletableFuture<List<StudentRecommendation>> getRecommendationsAsync(Integer opportunityId, int employerId) {
        return CompletableFuture.supplyAsync(() -> {
            if (opportunityId != null && opportunityId > 0) {
                return getRecommendationsForOpportunity(opportunityId);
            } else {
                return getGeneralRecommendationsForEmployer(employerId);
            }
        });
    }

    /**
     * Shortlists or invites a student candidate to apply for an opportunity.
     */
    public void shortlistOrInviteCandidate(int employerId, int studentId, int opportunityId, String note) {
        JobOpportunity opp = opportunityRepo.findById(opportunityId)
                .orElseThrow(() -> new AppException("Job opportunity not found."));
        if (opp.getEmployerId() != employerId) {
            throw new AppException("You can only invite or shortlist candidates for your own opportunities.");
        }

        Optional<Application> existing = applicationRepo.findByStudentAndOpportunity(studentId, opportunityId);
        String remarkText = (note == null || note.isBlank())
                ? "Candidate shortlisted via NextIT Recommendation Engine"
                : note.trim();

        if (existing.isPresent()) {
            Application app = existing.get();
            app.setStatus("Shortlisted");
            app.setRemarks(remarkText);
            applicationRepo.update(app);
            log.info("Updated existing application #{} to Shortlisted for student #{}", app.getApplicationId(), studentId);
        } else {
            Application newApp = new Application();
            newApp.setOpportunityId(opportunityId);
            newApp.setStudentId(studentId);
            newApp.setStatus("Shortlisted");
            newApp.setRemarks(remarkText);
            applicationRepo.save(newApp);
            log.info("Created new application #{} as Shortlisted/Invited for student #{}", newApp.getApplicationId(), studentId);
        }

        // Grant employer immediate portfolio access if the student has a portfolio
        portfolioRepo.findByStudentId(studentId).ifPresent(p -> {
            Optional<PortfolioAccess> existingAccess = accessRepo.findByPortfolioAndEmployer(p.getPortfolioId(), employerId);
            if (existingAccess.isPresent()) {
                PortfolioAccess pa = existingAccess.get();
                if (!"Approved".equalsIgnoreCase(pa.getAccessStatus())) {
                    pa.setAccessStatus("Approved");
                    pa.setGrantedAt(LocalDateTime.now());
                    accessRepo.update(pa);
                }
            } else {
                PortfolioAccess pa = new PortfolioAccess();
                pa.setPortfolioId(p.getPortfolioId());
                pa.setEmployerId(employerId);
                pa.setAccessStatus("Approved");
                pa.setGrantedAt(LocalDateTime.now());
                accessRepo.save(pa);
            }
        });
    }

    private List<StudentRecommendation> calculateRecommendations(JobOpportunity opportunity, int employerId) {
        // Efficient batch queries to prevent N+1 database roundtrips
        List<Student> students = studentRepo.findAll();
        Map<Integer, User> usersById = userRepo.findAll().stream()
                .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));
        Map<Integer, List<StudentSkill>> studentSkillsByStudent = studentSkillRepo.findAll().stream()
                .collect(Collectors.groupingBy(StudentSkill::getStudentId));
        Map<Integer, TechnicalSkill> technicalSkills = technicalSkillRepo.findAll().stream()
                .collect(Collectors.toMap(TechnicalSkill::getSkillId, s -> s, (a, b) -> a));
        Map<Integer, List<Project>> projectsByStudent = projectRepo.findAll().stream()
                .filter(p -> p.getStudentId() != null)
                .collect(Collectors.groupingBy(Project::getStudentId));
        Map<Integer, List<AcademicCredential>> credentialsByStudent = studentService.allCredentials().stream()
                .collect(Collectors.groupingBy(AcademicCredential::getStudentId));
        Map<Integer, Portfolio> portfoliosByStudent = portfolioRepo.findAll().stream()
                .collect(Collectors.toMap(Portfolio::getStudentId, p -> p, (a, b) -> a));

        Map<Integer, Application> applicationsByStudent = new HashMap<>();
        if (opportunity != null) {
            applicationRepo.findByOpportunityId(opportunity.getOpportunityId())
                    .forEach(a -> applicationsByStudent.put(a.getStudentId(), a));
        }

        List<String> requiredSkills = parseSkills(opportunity != null ? opportunity.getRequiredSkills() : null);

        List<StudentRecommendation> recommendations = new ArrayList<>();

        for (Student student : students) {
            User user = usersById.get(student.getUserId());
            // Only consider active student user accounts
            if (user == null || user.isSuspended()) {
                continue;
            }

            List<StudentSkill> sSkills = studentSkillsByStudent.getOrDefault(student.getStudentId(), Collections.emptyList());
            List<Project> sProjects = projectsByStudent.getOrDefault(student.getStudentId(), Collections.emptyList());
            List<AcademicCredential> sCredentials = credentialsByStudent.getOrDefault(student.getStudentId(), Collections.emptyList());
            Portfolio sPortfolio = portfoliosByStudent.get(student.getStudentId());
            Application sApp = applicationsByStudent.get(student.getStudentId());

            StudentRecommendation rec = new StudentRecommendation();
            rec.setStudent(student);
            rec.setUser(user);
            rec.setPortfolio(sPortfolio);
            rec.setOpportunity(opportunity);
            rec.setIdentityVerified(user.isVerified());
            if (sApp != null) {
                rec.setHasApplied(true);
                rec.setApplicationStatus(sApp.getStatus());
            }

            // Gather all human-readable skill names for student
            List<String> allSkillNames = new ArrayList<>();
            for (StudentSkill ss : sSkills) {
                TechnicalSkill ts = technicalSkills.get(ss.getSkillId());
                if (ts != null && ts.getSkillName() != null) {
                    allSkillNames.add(ts.getSkillName());
                }
            }
            rec.setAllStudentSkills(allSkillNames);

            // 1. Verified Skills Match (45%)
            double skillsScore = calculateSkillsScore(requiredSkills, sSkills, technicalSkills, sProjects, rec);
            rec.setSkillsScore(round(skillsScore));

            // 2. Projects & Portfolio Relevance (25%)
            double projectsScore = calculateProjectsScore(opportunity, requiredSkills, sProjects, sPortfolio, rec);
            rec.setProjectsScore(round(projectsScore));

            // 3. Achievements & Certifications (20%)
            double credentialsScore = calculateCredentialsScore(requiredSkills, sCredentials, rec);
            rec.setCredentialsScore(round(credentialsScore));

            // 4. Academic Profile & Readiness (10%)
            double readinessScore = calculateReadinessScore(student, user);
            rec.setReadinessScore(round(readinessScore));

            // Overall Match Score Normalized (0 - 100%)
            double totalRaw = skillsScore + projectsScore + credentialsScore + readinessScore;
            int finalScore = (int) Math.round(Math.min(100.0, Math.max(0.0, totalRaw)));
            rec.setMatchScore(finalScore);

            // Generate concise human-readable match reason
            rec.setMatchReason(generateMatchReason(rec, requiredSkills));

            recommendations.add(rec);
        }

        // Sort descending by highest match score by default
        recommendations.sort(Comparator.comparingInt(StudentRecommendation::getMatchScore).reversed());
        return recommendations;
    }

    /**
     * 1. Verified Skills Match (45%)
     */
    private double calculateSkillsScore(List<String> requiredSkills,
                                       List<StudentSkill> sSkills,
                                       Map<Integer, TechnicalSkill> technicalSkills,
                                       List<Project> sProjects,
                                       StudentRecommendation rec) {
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        if (!requiredSkills.isEmpty()) {
            double totalSkillScore = 0.0;

            for (String req : requiredSkills) {
                String reqLower = req.toLowerCase(Locale.ROOT);
                StudentSkill matchingSkill = null;
                TechnicalSkill matchingTech = null;

                for (StudentSkill ss : sSkills) {
                    TechnicalSkill ts = technicalSkills.get(ss.getSkillId());
                    if (ts != null && ts.getSkillName() != null) {
                        String sNameLower = ts.getSkillName().toLowerCase(Locale.ROOT);
                        if (sNameLower.equals(reqLower) || sNameLower.contains(reqLower) || reqLower.contains(sNameLower)) {
                            matchingSkill = ss;
                            matchingTech = ts;
                            break;
                        }
                    }
                }

                if (matchingSkill != null) {
                    matched.add(matchingTech.getSkillName());
                    double baseWeight = switch (String.valueOf(matchingSkill.getProficiencyLevel()).toLowerCase(Locale.ROOT)) {
                        case "expert" -> 1.10;
                        case "advanced" -> 1.00;
                        case "intermediate" -> 0.85;
                        default -> 0.70; // Beginner
                    };

                    // Instructor-assessed verification boost
                    double verificationBoost = 1.0;
                    if (matchingSkill.getAssessedBy() != null && matchingSkill.getAssessedBy() > 0) {
                        Double score = matchingSkill.getAssessmentScore();
                        if (score != null && score >= 85.0) {
                            verificationBoost = 1.15;
                        } else if (score != null && score >= 70.0) {
                            verificationBoost = 1.10;
                        } else {
                            verificationBoost = 1.05;
                        }
                    }

                    totalSkillScore += (baseWeight * verificationBoost);
                } else {
                    // Check if demonstrated in student projects
                    boolean foundInProjects = sProjects.stream().anyMatch(p -> {
                        String tech = p.getTechnologiesUsed() == null ? "" : p.getTechnologiesUsed().toLowerCase(Locale.ROOT);
                        String desc = p.getDescription() == null ? "" : p.getDescription().toLowerCase(Locale.ROOT);
                        return tech.contains(reqLower) || desc.contains(reqLower);
                    });

                    if (foundInProjects) {
                        matched.add(req + " (Project)");
                        totalSkillScore += 0.60; // partial credit for practical project use
                    } else {
                        missing.add(req);
                    }
                }
            }

            rec.setMatchedSkills(matched);
            rec.setMissingSkills(missing);
            double ratio = totalSkillScore / (double) requiredSkills.size();
            return Math.min(45.0, ratio * 45.0);
        } else {
            // General recommendation mode: evaluate breadth & assessment of recorded skills
            double generalScore = 0.0;
            for (StudentSkill ss : sSkills) {
                TechnicalSkill ts = technicalSkills.get(ss.getSkillId());
                if (ts != null) {
                    matched.add(ts.getSkillName());
                    double base = "Expert".equalsIgnoreCase(ss.getProficiencyLevel()) ? 9.0 :
                            "Advanced".equalsIgnoreCase(ss.getProficiencyLevel()) ? 7.5 :
                            "Intermediate".equalsIgnoreCase(ss.getProficiencyLevel()) ? 6.0 : 4.5;
                    if (ss.getAssessedBy() != null && ss.getAssessedBy() > 0) {
                        base *= 1.15;
                    }
                    generalScore += base;
                }
            }
            rec.setMatchedSkills(matched);
            return Math.min(45.0, generalScore);
        }
    }

    /**
     * 2. Projects & Portfolio Relevance (25%)
     */
    private double calculateProjectsScore(JobOpportunity opportunity,
                                         List<String> requiredSkills,
                                         List<Project> sProjects,
                                         Portfolio sPortfolio,
                                         StudentRecommendation rec) {
        double score = 0.0;
        List<Project> relevant = new ArrayList<>();

        for (Project p : sProjects) {
            boolean isRelevant = false;
            double pScore = 0.0;

            // Base completed project points
            pScore += 4.0;

            // Capstone / Thesis / Client project experience
            String type = p.getProjectType() == null ? "" : p.getProjectType().toLowerCase(Locale.ROOT);
            String title = p.getProjectTitle() == null ? "" : p.getProjectTitle().toLowerCase(Locale.ROOT);
            if (type.contains("capstone") || type.contains("thesis") || type.contains("client")
                    || title.contains("capstone") || title.contains("thesis")) {
                pScore += 4.0;
                isRelevant = true;
            }

            // Tech stack overlap with opportunity
            String pTech = p.getTechnologiesUsed() == null ? "" : p.getTechnologiesUsed().toLowerCase(Locale.ROOT);
            for (String req : requiredSkills) {
                if (pTech.contains(req.toLowerCase(Locale.ROOT))) {
                    pScore += 2.0;
                    isRelevant = true;
                    break;
                }
            }

            // Public code repository or live demo presence
            if ((p.getRepositoryUrl() != null && !p.getRepositoryUrl().isBlank())
                    || (p.getDemoUrl() != null && !p.getDemoUrl().isBlank())) {
                pScore += 2.0;
                isRelevant = true;
            }

            score += pScore;
            if (isRelevant || relevant.size() < 3) {
                relevant.add(p);
            }
        }

        rec.setRelevantProjects(relevant);

        // Portfolio profile completeness
        if (sPortfolio != null) {
            if (sPortfolio.getHeadline() != null && !sPortfolio.getHeadline().isBlank()) score += 2.0;
            if (sPortfolio.getBio() != null && !sPortfolio.getBio().isBlank()) score += 2.0;
            if (sPortfolio.getCareerGoal() != null && !sPortfolio.getCareerGoal().isBlank()) score += 2.0;
        }

        return Math.min(25.0, score);
    }

    /**
     * 3. Achievements & Certifications (20%)
     */
    private double calculateCredentialsScore(List<String> requiredSkills,
                                            List<AcademicCredential> sCredentials,
                                            StudentRecommendation rec) {
        double score = 0.0;
        List<AcademicCredential> relevant = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (AcademicCredential cred : sCredentials) {
            boolean isRelevant = false;
            if (cred.getType() == AcademicCredential.Type.CERTIFICATION) {
                // Non-expired check
                if (cred.getExpirationDate() == null || !cred.getExpirationDate().isBefore(today)) {
                    score += 8.0;
                    isRelevant = true;
                    // Skill-aligned certification bonus
                    String certName = cred.getTitle() == null ? "" : cred.getTitle().toLowerCase(Locale.ROOT);
                    for (String req : requiredSkills) {
                        if (certName.contains(req.toLowerCase(Locale.ROOT))) {
                            score += 2.5;
                            break;
                        }
                    }
                }
            } else if (cred.getType() == AcademicCredential.Type.ACHIEVEMENT) {
                score += 5.0; // Verified awards, hackathons, academic milestones
                isRelevant = true;
            }

            if (isRelevant) {
                relevant.add(cred);
            }
        }

        rec.setRelevantCredentials(relevant);
        return Math.min(20.0, score);
    }

    /**
     * 4. Academic Profile & Readiness (10%)
     */
    private double calculateReadinessScore(Student student, User user) {
        double score = 0.0;

        // Degree / Program alignment
        String prog = student.getProgram() == null ? "" : student.getProgram().toUpperCase(Locale.ROOT);
        if (prog.contains("IT") || prog.contains("CS") || prog.contains("IS")
                || prog.contains("COMPUTER") || prog.contains("SOFTWARE")) {
            score += 4.0;
        } else {
            score += 2.0;
        }

        // Seniority / Year level readiness for employment & internship
        int year = student.getYearLevel();
        if (year >= 4) {
            score += 4.0; // Ready for immediate graduation / employment
        } else if (year == 3) {
            score += 3.0; // Prime internship readiness
        } else if (year == 2) {
            score += 2.0;
        } else {
            score += 1.0;
        }

        // Active enrollment
        String status = student.getEnrollmentStatus() == null ? "" : student.getEnrollmentStatus().toLowerCase(Locale.ROOT);
        if (status.contains("enroll") || status.contains("active") || status.contains("regular")) {
            score += 1.0;
        }

        // Verified student account
        if (user.isVerified()) {
            score += 1.0;
        }

        return Math.min(10.0, score);
    }

    private String generateMatchReason(StudentRecommendation rec, List<String> requiredSkills) {
        List<String> parts = new ArrayList<>();

        if (!requiredSkills.isEmpty()) {
            int matchedCount = rec.getMatchedSkills().size();
            int totalReq = requiredSkills.size();
            parts.add(String.format("Matches %d/%d required skills%s",
                    matchedCount, totalReq,
                    rec.getMatchedSkills().isEmpty() ? "" : " (" + String.join(", ", rec.getMatchedSkills().stream().limit(3).toList()) + ")"));
        } else if (!rec.getMatchedSkills().isEmpty()) {
            parts.add(rec.getMatchedSkills().size() + " verified technical skills (" +
                    String.join(", ", rec.getMatchedSkills().stream().limit(3).toList()) + ")");
        }

        long nonExpiredCerts = rec.getRelevantCredentials().stream()
                .filter(c -> c.getType() == AcademicCredential.Type.CERTIFICATION).count();
        long achievements = rec.getRelevantCredentials().stream()
                .filter(c -> c.getType() == AcademicCredential.Type.ACHIEVEMENT).count();

        if (nonExpiredCerts > 0) {
            parts.add(nonExpiredCerts + (nonExpiredCerts == 1 ? " active certification" : " active certifications"));
        }
        if (achievements > 0) {
            parts.add(achievements + (achievements == 1 ? " award/achievement" : " awards/achievements"));
        }

        boolean hasCapstone = rec.getRelevantProjects().stream().anyMatch(p -> {
            String type = p.getProjectType() == null ? "" : p.getProjectType().toLowerCase(Locale.ROOT);
            String title = p.getProjectTitle() == null ? "" : p.getProjectTitle().toLowerCase(Locale.ROOT);
            return type.contains("capstone") || type.contains("thesis") || title.contains("capstone") || title.contains("thesis");
        });
        if (hasCapstone) {
            parts.add("Capstone project experience");
        } else if (!rec.getRelevantProjects().isEmpty()) {
            parts.add(rec.getRelevantProjects().size() + " portfolio " + (rec.getRelevantProjects().size() == 1 ? "project" : "projects"));
        }

        if (rec.isIdentityVerified()) {
            parts.add("Verified Student Identity");
        }

        if (parts.isEmpty()) {
            return "Active student profile in " + rec.getProgramAndYear();
        }
        return String.join(" • ", parts);
    }

    private List<String> parseSkills(String skillsStr) {
        if (skillsStr == null || skillsStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(SPLIT_DELIMITERS.split(skillsStr))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private double round(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
