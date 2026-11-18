package com.nextit.service;

import com.nextit.model.*;
import com.nextit.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class AntiFakeIdentityService {

    private static final Logger log = LoggerFactory.getLogger(AntiFakeIdentityService.class);

    private static final Set<String> DISPOSABLE_EMAIL_DOMAINS = Set.of(
            "mailinator.com", "10minutemail.com", "guerrillamail.com", "tempmail.com",
            "trashmail.com", "sharklasers.com", "yopmail.com", "dispostable.com",
            "getairmail.com", "temp-mail.org", "fakeinbox.com", "throwawaymail.com"
    );

    private final InstitutionalRecordRepository institutionalRepo;
    private final JdbcStudentRepository studentRepo;
    private final JdbcInstructorRepository instructorRepo;
    private final JdbcEmployerRepository employerRepo;
    private final JdbcUserRepository userRepo;
    private final VerificationRequestRepository requestRepo;

    public record AnalysisResult(
            VerificationStatus status,
            int riskScore,
            List<String> riskFlags,
            String reviewerNotes,
            boolean autoApproved
    ) {}

    public AntiFakeIdentityService() {
        this(new JdbcInstitutionalRecordRepository(),
             new JdbcStudentRepository(),
             new JdbcInstructorRepository(),
             new JdbcEmployerRepository(),
             new JdbcUserRepository(),
             new JdbcVerificationRequestRepository());
    }

    public AntiFakeIdentityService(InstitutionalRecordRepository institutionalRepo,
                                  JdbcStudentRepository studentRepo,
                                  JdbcInstructorRepository instructorRepo,
                                  JdbcEmployerRepository employerRepo,
                                  JdbcUserRepository userRepo,
                                  VerificationRequestRepository requestRepo) {
        this.institutionalRepo = institutionalRepo;
        this.studentRepo = studentRepo;
        this.instructorRepo = instructorRepo;
        this.employerRepo = employerRepo;
        this.userRepo = userRepo;
        this.requestRepo = requestRepo;
    }

    public AnalysisResult analyzeStudent(String studentNumber, String email, String firstName, String lastName, String program) {
        int riskScore = 0;
        List<String> flags = new ArrayList<>();
        StringBuilder notes = new StringBuilder();

        String cleanEmail = (email != null) ? email.trim().toLowerCase(Locale.ROOT) : "";
        String cleanId = (studentNumber != null) ? studentNumber.trim() : "";
        String domain = cleanEmail.contains("@") ? cleanEmail.substring(cleanEmail.indexOf('@') + 1) : "";

        // 1. Disposable email check
        if (DISPOSABLE_EMAIL_DOMAINS.contains(domain)) {
            riskScore += 45;
            flags.add("DISPOSABLE_EMAIL_DOMAIN");
            notes.append("Disposable or temporary email provider detected. ");
        }

        // 2. Check for reused student ID across existing students
        boolean idReused = studentRepo.findAll().stream()
                .anyMatch(s -> cleanId.equalsIgnoreCase(s.getStudentNumber()));
        if (idReused) {
            riskScore += 65;
            flags.add("REUSED_STUDENT_ID");
            notes.append("Student ID number is already registered to another account! Potential impersonation attempt. ");
        }

        // 3. Check for similar full name impersonation
        String fullName = (firstName + " " + lastName).trim();
        boolean nameMatch = userRepo.findAll().stream()
                .filter(u -> u.getRole() == Role.STUDENT)
                .anyMatch(u -> fullName.equalsIgnoreCase(u.getFullName()) && !cleanEmail.equalsIgnoreCase(u.getEmail()));
        if (nameMatch) {
            riskScore += 25;
            flags.add("POTENTIAL_NAME_COLLISION");
            notes.append("Another student with an identical name exists in the network. ");
        }

        // 4. Institutional Registrar Database Cross-Validation
        Optional<InstitutionalRecord> registrarRecord = institutionalRepo.findByIdentifier(cleanId);
        boolean autoApproved = false;
        VerificationStatus status = VerificationStatus.PENDING;

        if (registrarRecord.isPresent()) {
            InstitutionalRecord rec = registrarRecord.get();
            if (rec.isActive() && rec.getRecordType() == Role.STUDENT) {
                boolean emailMatches = cleanEmail.equalsIgnoreCase(rec.getInstitutionalEmail());
                boolean domainMatches = domain.equalsIgnoreCase(rec.getInstitutionalEmail().substring(rec.getInstitutionalEmail().indexOf('@') + 1));

                if (emailMatches && !idReused) {
                    // Perfect automated registrar match!
                    riskScore = Math.max(0, riskScore - 10);
                    flags.add("REGISTRAR_VERIFIED_EXACT_MATCH");
                    status = VerificationStatus.VERIFIED;
                    autoApproved = true;
                    notes.append("Identity verified automatically via authorized registrar record for ").append(rec.getFullName()).append(". ");
                } else if (domainMatches && !idReused) {
                    riskScore += 10;
                    flags.add("REGISTRAR_DOMAIN_MATCH");
                    notes.append("Registrar ID matched; email is in valid institutional domain. Pending quick administrator sign-off. ");
                } else {
                    riskScore += 40;
                    flags.add("REGISTRAR_EMAIL_MISMATCH");
                    notes.append("ID exists in registrar records under '").append(rec.getFullName())
                            .append("' with official email '").append(rec.getInstitutionalEmail())
                            .append("', but registrant used '").append(cleanEmail).append("'. Possible unauthorized ID claim. ");
                }
            } else {
                riskScore += 35;
                flags.add("INACTIVE_REGISTRAR_RECORD");
                notes.append("Registrar record for this ID is inactive or marked non-student. ");
            }
        } else {
            // Not in registrar database
            boolean isEdu = domain.endsWith(".edu") || domain.endsWith(".edu.ph") || domain.endsWith(".ac.uk");
            if (isEdu) {
                riskScore += 15;
                flags.add("UNLISTED_STUDENT_ID_EDU_EMAIL");
                notes.append("Valid educational domain provided, but Student ID is not in local registrar database. Requires manual proof review. ");
            } else {
                riskScore += 30;
                flags.add("UNLISTED_STUDENT_ID_NON_EDU");
                notes.append("Non-institutional email provided and Student ID not pre-enrolled. Manual ID card review required. ");
            }
        }

        if (riskScore >= 60 && status != VerificationStatus.VERIFIED) {
            flags.add("HIGH_RISK_SUSPICIOUS_IDENTITY");
        }

        return new AnalysisResult(status, riskScore, flags, notes.toString().trim(), autoApproved);
    }

    public AnalysisResult analyzeInstructor(String employeeNumber, String email, String firstName, String lastName, String department) {
        int riskScore = 0;
        List<String> flags = new ArrayList<>();
        StringBuilder notes = new StringBuilder();

        String cleanEmail = (email != null) ? email.trim().toLowerCase(Locale.ROOT) : "";
        String cleanId = (employeeNumber != null) ? employeeNumber.trim() : "";
        String domain = cleanEmail.contains("@") ? cleanEmail.substring(cleanEmail.indexOf('@') + 1) : "";

        if (DISPOSABLE_EMAIL_DOMAINS.contains(domain)) {
            riskScore += 50;
            flags.add("DISPOSABLE_EMAIL_DOMAIN");
            notes.append("Disposable email provider detected. ");
        }

        boolean idReused = instructorRepo.findAll().stream()
                .anyMatch(inst -> cleanId.equalsIgnoreCase(inst.getEmployeeNumber()));
        if (idReused) {
            riskScore += 70;
            flags.add("REUSED_EMPLOYEE_ID");
            notes.append("Employee number is already registered to another faculty member! ");
        }

        // Instructors ALWAYS require administrator authorization
        VerificationStatus status = VerificationStatus.PENDING;

        Optional<InstitutionalRecord> registrarRecord = institutionalRepo.findByIdentifier(cleanId);
        if (registrarRecord.isPresent()) {
            InstitutionalRecord rec = registrarRecord.get();
            if (rec.isActive() && rec.getRecordType() == Role.INSTRUCTOR) {
                if (cleanEmail.equalsIgnoreCase(rec.getInstitutionalEmail())) {
                    riskScore = 5;
                    flags.add("REGISTRAR_FACULTY_CONFIRMED");
                    notes.append("Official faculty record confirmed for ").append(rec.getFullName()).append(". Awaiting administrator sign-off. ");
                } else {
                    riskScore += 35;
                    flags.add("FACULTY_EMAIL_MISMATCH");
                    notes.append("Faculty ID registered under '").append(rec.getFullName()).append("' but registrant email is different. ");
                }
            } else {
                riskScore += 40;
                flags.add("INACTIVE_FACULTY_RECORD");
                notes.append("Faculty record is inactive or invalid. ");
            }
        } else {
            riskScore += 25;
            flags.add("UNLISTED_FACULTY_ID");
            notes.append("Faculty employee ID not in pre-authorized directory. Verifier must inspect credentials. ");
        }

        if (riskScore >= 60) {
            flags.add("HIGH_RISK_FACULTY_CLAIM");
        }

        return new AnalysisResult(status, riskScore, flags, notes.toString().trim(), false);
    }

    public AnalysisResult analyzeEmployer(String companyName, String businessRegNumber, String email, String contactPerson) {
        int riskScore = 0;
        List<String> flags = new ArrayList<>();
        StringBuilder notes = new StringBuilder();

        String cleanEmail = (email != null) ? email.trim().toLowerCase(Locale.ROOT) : "";
        String cleanReg = (businessRegNumber != null) ? businessRegNumber.trim() : "";
        String domain = cleanEmail.contains("@") ? cleanEmail.substring(cleanEmail.indexOf('@') + 1) : "";

        if (DISPOSABLE_EMAIL_DOMAINS.contains(domain)) {
            riskScore += 50;
            flags.add("DISPOSABLE_EMAIL_DOMAIN");
            notes.append("Disposable email provider detected. ");
        }

        // Check for reused business registration number (TIN/SEC/DTI)
        if (!cleanReg.isBlank()) {
            if (cleanReg.length() < 5) {
                riskScore += 30;
                flags.add("INVALID_BUSINESS_REG_FORMAT");
                notes.append("Business registration number appears truncated or invalid format. ");
            }
            boolean duplicateReg = requestRepo.findAll().stream()
                    .anyMatch(r -> r.getRole() == Role.EMPLOYER && cleanReg.equalsIgnoreCase(r.getBusinessRegistrationNumber()));
            if (duplicateReg) {
                riskScore += 60;
                flags.add("DUPLICATE_BUSINESS_REGISTRATION");
                notes.append("Business Registration (TIN/SEC/DTI) is already claimed by another employer account! ");
            }
        } else {
            riskScore += 20;
            flags.add("MISSING_BUSINESS_REGISTRATION");
            notes.append("No official TIN/SEC/DTI registration number supplied. ");
        }

        // Check if generic personal email claiming corporate identity
        Set<String> consumerMail = Set.of("gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "aol.com");
        if (consumerMail.contains(domain)) {
            riskScore += 15;
            flags.add("GENERIC_CONSUMER_EMAIL");
            notes.append("Employer representative registered using a consumer email provider (").append(domain).append("). ");
        } else {
            flags.add("CORPORATE_DOMAIN_DETECTED");
            notes.append("Corporate domain (").append(domain).append(") detected. ");
        }

        VerificationStatus status = VerificationStatus.PENDING;
        if (riskScore >= 50) {
            flags.add("SUSPICIOUS_EMPLOYER_PROFILE");
        }

        return new AnalysisResult(status, riskScore, flags, notes.toString().trim(), false);
    }
}
