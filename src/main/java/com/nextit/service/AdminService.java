package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.repository.*;
import com.nextit.util.Validator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class AdminService {

    private final JdbcUserRepository userRepo = new JdbcUserRepository();
    private final JdbcSubjectRepository subjectRepo = new JdbcSubjectRepository();
    private final JdbcStudentRepository studentRepo = new JdbcStudentRepository();
    private final JdbcInstructorRepository instructorRepo = new JdbcInstructorRepository();
    private final JdbcEmployerRepository employerRepo = new JdbcEmployerRepository();
    private final JdbcVerificationRequestRepository verificationRepo = new JdbcVerificationRequestRepository();
    private final JdbcInstitutionalRecordRepository institutionalRepo = new JdbcInstitutionalRecordRepository();
    private final SecurityAuditService auditService = SecurityAuditService.getInstance();

    public List<User> users() { return userRepo.findAll(); }
    public List<Subject> subjects() { return subjectRepo.findAll(); }
    public List<Student> students() { return studentRepo.findAll(); }
    public List<Instructor> instructors() { return instructorRepo.findAll(); }
    public List<Employer> employers() { return employerRepo.findAll(); }

    public List<VerificationRequest> verificationRequests() { return verificationRepo.findAll(); }
    public List<VerificationRequest> pendingVerificationRequests() { return verificationRepo.findPending(); }

    public void approveVerification(int requestId, int reviewerId, String notes) {
        VerificationRequest req = verificationRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Verification request not found."));
        req.setStatus(VerificationStatus.VERIFIED);
        req.setReviewerId(reviewerId);
        req.setReviewerNotes(notes != null && !notes.isBlank() ? notes.trim() : "Approved by reviewer");
        req.setReviewedAt(LocalDateTime.now());
        verificationRepo.update(req);

        userRepo.updateVerificationStatus(req.getUserId(), VerificationStatus.VERIFIED, null, reviewerId);
        auditService.logVerificationDecision(currentUserUsername(), req.getUserId(), VerificationStatus.VERIFIED, req.getReviewerNotes());
    }

    public void rejectVerification(int requestId, int reviewerId, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new AppException("A rejection reason is required.");
        }
        VerificationRequest req = verificationRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Verification request not found."));
        req.setStatus(VerificationStatus.REJECTED);
        req.setReviewerId(reviewerId);
        req.setReviewerNotes(rejectionReason.trim());
        req.setReviewedAt(LocalDateTime.now());
        verificationRepo.update(req);

        userRepo.updateVerificationStatus(req.getUserId(), VerificationStatus.REJECTED, rejectionReason.trim(), reviewerId);
        auditService.logVerificationDecision(currentUserUsername(), req.getUserId(), VerificationStatus.REJECTED, rejectionReason.trim());
    }

    public void suspendUser(int targetUserId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new AppException("A suspension reason is required.");
        }
        userRepo.findById(targetUserId)
                .orElseThrow(() -> new AppException("User not found."));
        userRepo.updateVerificationStatus(targetUserId, VerificationStatus.SUSPENDED, reason.trim(), null);
        auditService.logSuspension(currentUserUsername(), targetUserId, reason.trim());
    }

    public void reinstateUser(int targetUserId) {
        userRepo.findById(targetUserId)
                .orElseThrow(() -> new AppException("User not found."));
        userRepo.updateVerificationStatus(targetUserId, VerificationStatus.VERIFIED, null, null);
        auditService.logReinstated(currentUserUsername(), targetUserId);
    }

    public List<InstitutionalRecord> institutionalRecords() {
        return institutionalRepo.findAll();
    }

    public void saveInstitutionalRecord(InstitutionalRecord record) {
        Validator.notBlank(record.getIdentifierNumber(), "Identifier number");
        Validator.email(record.getInstitutionalEmail());
        Validator.notBlank(record.getFullName(), "Full name");
        Validator.notBlank(record.getDepartmentOrProgram(), "Department or program");
        if (record.getRecordType() == null) {
            throw new AppException("Record type (STUDENT or INSTRUCTOR) is required.");
        }
        if (record.getRecordId() > 0) {
            institutionalRepo.update(record);
        } else {
            institutionalRepo.save(record);
        }
    }

    public void deleteInstitutionalRecord(int recordId) {
        institutionalRepo.delete(recordId);
    }

    public void createUser(User user, String plainPassword) {
        validateUser(user);
        Validator.notBlank(plainPassword, "Password");
        if (plainPassword.length() < 6) throw new AppException("Password must be at least 6 characters.");
        if (plainPassword.length() > 72) throw new AppException("Password must be 72 characters or fewer.");
        if (userRepo.findByUsername(user.getUsername().trim()).isPresent()) {
            throw new AppException("That username is already taken.");
        }
        if (userRepo.findByEmail(user.getEmail().trim()).isPresent()) {
            throw new AppException("That email address is already registered.");
        }
        user.setUsername(user.getUsername().trim());
        user.setEmail(user.getEmail().trim());
        user.setPasswordHash(AuthenticationService.hashPassword(plainPassword));
        if (user.getVerificationStatus() == null) {
            user.setVerificationStatus(VerificationStatus.VERIFIED);
        }
        userRepo.save(user);
        try {
            createProfile(user);
        } catch (RuntimeException e) {
            try {
                userRepo.delete(user.getUserId());
            } catch (RuntimeException ignored) {}
            throw e;
        }
    }

    public void updateUser(User user) {
        validateUser(user);
        User existing = userRepo.findById(user.getUserId())
                .orElseThrow(() -> new AppException("User account not found."));
        if (existing.getRole() != user.getRole()) {
            auditService.logRoleChangeBlocked(currentUserUsername(), existing.getUsername(), String.valueOf(user.getRole()));
            throw new AppException("A user's role cannot be changed after the account is created.");
        }
        userRepo.findByUsername(user.getUsername().trim())
                .filter(other -> other.getUserId() != user.getUserId())
                .ifPresent(other -> { throw new AppException("That username is already taken."); });
        userRepo.findByEmail(user.getEmail().trim())
                .filter(other -> other.getUserId() != user.getUserId())
                .ifPresent(other -> { throw new AppException("That email address is already registered."); });
        user.setUsername(user.getUsername().trim());
        user.setEmail(user.getEmail().trim());
        userRepo.update(user);
    }

    public void deleteUser(int userId) { userRepo.delete(userId); }

    public void resetPassword(int userId, String plainPassword) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException("User account not found."));
        Validator.notBlank(plainPassword, "Password");
        if (plainPassword.length() < 6) throw new AppException("Password must be at least 6 characters.");
        if (plainPassword.length() > 72) throw new AppException("Password must be 72 characters or fewer.");
        user.setPasswordHash(AuthenticationService.hashPassword(plainPassword));
        userRepo.updatePassword(userId, user.getPasswordHash());
    }

    public void saveSubject(Subject subject) {
        Validator.notBlank(subject.getSubjectCode(), "Subject code");
        Validator.notBlank(subject.getSubjectName(), "Subject name");
        Validator.maxLength(subject.getSubjectCode(), 30, "Subject code");
        Validator.maxLength(subject.getSubjectName(), 150, "Subject name");
        Validator.maxLength(subject.getDescription(), 16000, "Description");
        if (!Double.isFinite(subject.getUnits()) || subject.getUnits() <= 0 || subject.getUnits() > 99.9) {
            throw new AppException("Units must be a finite number between 0.1 and 99.9.");
        }
        subjectRepo.findAll().stream()
                .filter(s -> s.getSubjectId() != subject.getSubjectId())
                .filter(s -> s.getSubjectCode().equalsIgnoreCase(subject.getSubjectCode().trim()))
                .findAny()
                .ifPresent(s -> { throw new AppException("That subject code already exists."); });
        subject.setSubjectCode(subject.getSubjectCode().trim().toUpperCase(Locale.ROOT));
        if (subject.getSubjectId() > 0) subjectRepo.update(subject);
        else subjectRepo.save(subject);
    }

    public void deleteSubject(int subjectId) { subjectRepo.delete(subjectId); }

    public void updateStudent(Student student) {
        Validator.notBlank(student.getStudentNumber(), "Student number");
        Validator.maxLength(student.getStudentNumber(), 30, "Student number");
        Validator.notBlank(student.getProgram(), "Program");
        Validator.maxLength(student.getProgram(), 100, "Program");
        Validator.maxLength(student.getSection(), 30, "Section");
        Validator.notBlank(student.getEnrollmentStatus(), "Enrollment status");
        Validator.maxLength(student.getEnrollmentStatus(), 30, "Enrollment status");
        if (student.getYearLevel() < 1 || (student.getYearLevel() > 10 && (student.getYearLevel() < 2000 || student.getYearLevel() > 2100))) {
            throw new AppException("Year level must be between 1 and 10, or a valid graduation year.");
        }
        studentRepo.findAll().stream()
                .filter(other -> other.getStudentId() != student.getStudentId())
                .filter(other -> student.getStudentNumber().trim().equalsIgnoreCase(other.getStudentNumber()))
                .findAny()
                .ifPresent(other -> { throw new AppException("That student number is already in use."); });
        student.setStudentNumber(student.getStudentNumber().trim());
        studentRepo.update(student);
    }

    public void updateInstructor(Instructor instructor) {
        Validator.notBlank(instructor.getDepartment(), "Department");
        Validator.maxLength(instructor.getDepartment(), 100, "Department");
        Validator.maxLength(instructor.getSpecialization(), 150, "Specialization");
        Validator.maxLength(instructor.getEmployeeNumber(), 30, "Employee number");
        String number = instructor.getEmployeeNumber() == null ? "" : instructor.getEmployeeNumber().trim();
        if (!number.isBlank()) {
            instructorRepo.findAll().stream()
                    .filter(other -> other.getInstructorId() != instructor.getInstructorId())
                    .filter(other -> number.equalsIgnoreCase(other.getEmployeeNumber()))
                    .findAny()
                    .ifPresent(other -> { throw new AppException("That employee number is already in use."); });
        }
        instructor.setEmployeeNumber(number.isBlank() ? null : number);
        instructorRepo.update(instructor);
    }

    public void updateEmployer(Employer employer) {
        Validator.notBlank(employer.getCompanyName(), "Company name");
        Validator.maxLength(employer.getCompanyName(), 150, "Company name");
        Validator.maxLength(employer.getIndustry(), 100, "Industry");
        Validator.maxLength(employer.getContactPerson(), 100, "Contact person");
        Validator.maxLength(employer.getContactNumber(), 30, "Contact number");
        Validator.maxLength(employer.getAddress(), 255, "Address");
        employerRepo.update(employer);
    }

    private void validateUser(User user) {
        Validator.notBlank(user.getUsername(), "Username");
        Validator.email(user.getEmail());
        Validator.notBlank(user.getFirstName(), "First name");
        Validator.notBlank(user.getLastName(), "Last name");
        Validator.maxLength(user.getUsername(), 50, "Username");
        Validator.maxLength(user.getEmail(), 100, "Email");
        Validator.maxLength(user.getFirstName(), 50, "First name");
        Validator.maxLength(user.getLastName(), 50, "Last name");
        if (user.getRole() == null) throw new AppException("A role is required.");
    }

    private void createProfile(User user) {
        if (user.getRole() == Role.STUDENT) {
            Student student = new Student();
            student.setUserId(user.getUserId());
            student.setStudentNumber("TBD-" + user.getUserId());
            student.setProgram("Not specified");
            student.setYearLevel(1);
            student.setSection("");
            student.setEnrollmentStatus("Enrolled");
            studentRepo.save(student);
        } else if (user.getRole() == Role.INSTRUCTOR) {
            Instructor instructor = new Instructor();
            instructor.setUserId(user.getUserId());
            instructor.setEmployeeNumber("TBD-" + user.getUserId());
            instructor.setDepartment("Not specified");
            instructor.setSpecialization("");
            instructorRepo.save(instructor);
        } else if (user.getRole() == Role.EMPLOYER) {
            Employer employer = new Employer();
            employer.setUserId(user.getUserId());
            String company = (user.getFirstName() + " " + user.getLastName()).trim();
            employer.setCompanyName(company.isBlank() ? user.getUsername() : company + " Company");
            employer.setIndustry("");
            employer.setContactPerson(company);
            employer.setContactNumber("");
            employer.setAddress("");
            employerRepo.save(employer);
        }
    }

    private String currentUserUsername() {
        User u = com.nextit.security.Session.currentUser();
        return u != null ? u.getUsername() : "SYSTEM";
    }
}
