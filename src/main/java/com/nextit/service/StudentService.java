package com.nextit.service;

import com.nextit.exception.AppException;
import com.nextit.model.AcademicCredential;
import com.nextit.model.Achievement;
import com.nextit.model.Certification;
import com.nextit.repository.JdbcAchievementRepository;
import com.nextit.repository.JdbcCertificationRepository;
import com.nextit.repository.JdbcStudentRepository;
import com.nextit.util.Validator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StudentService {

    private final JdbcAchievementRepository achievementRepo = new JdbcAchievementRepository();
    private final JdbcCertificationRepository certificationRepo = new JdbcCertificationRepository();
    private final JdbcStudentRepository studentRepo = new JdbcStudentRepository();

    public List<Achievement> achievementsFor(int studentId) { return achievementRepo.findByStudentId(studentId); }
    public List<Certification> certificationsFor(int studentId) { return certificationRepo.findByStudentId(studentId); }
    public List<Achievement> allAchievements() { return achievementRepo.findAll(); }
    public List<Certification> allCertifications() { return certificationRepo.findAll(); }

    public List<AcademicCredential> credentialsFor(int studentId) {
        List<AcademicCredential> list = new ArrayList<>();
        for (Achievement a : achievementsFor(studentId)) {
            list.add(AcademicCredential.fromAchievement(a));
        }
        for (Certification c : certificationsFor(studentId)) {
            list.add(AcademicCredential.fromCertification(c));
        }
        list.sort((a, b) -> {
            LocalDate d1 = a.getDate();
            LocalDate d2 = b.getDate();
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            return d2.compareTo(d1);
        });
        return list;
    }

    public List<AcademicCredential> allCredentials() {
        List<AcademicCredential> list = new ArrayList<>();
        for (Achievement a : allAchievements()) {
            list.add(AcademicCredential.fromAchievement(a));
        }
        for (Certification c : allCertifications()) {
            list.add(AcademicCredential.fromCertification(c));
        }
        list.sort((a, b) -> {
            LocalDate d1 = a.getDate();
            LocalDate d2 = b.getDate();
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            return d2.compareTo(d1);
        });
        return list;
    }

    public void saveCredential(AcademicCredential cred) {
        if (cred == null || cred.getType() == null) {
            throw new AppException("Credential type is required.");
        }
        if (cred.getType() == AcademicCredential.Type.ACHIEVEMENT) {
            Achievement a = new Achievement();
            if (cred.getId() > 0) a.setAchievementId(cred.getId());
            a.setStudentId(cred.getStudentId());
            a.setTitle(cred.getTitle());
            a.setOrganization(cred.getOrganization());
            a.setAchievementDate(cred.getDate());
            a.setDescription(cred.getDescription());
            a.setVerificationUrl(cred.getVerificationUrl());
            saveAchievement(a);
            cred.setId(a.getAchievementId());
        } else {
            Certification c = new Certification();
            if (cred.getId() > 0) c.setCertificationId(cred.getId());
            c.setStudentId(cred.getStudentId());
            c.setCertificationName(cred.getTitle());
            c.setIssuingOrganization(cred.getOrganization());
            c.setIssueDate(cred.getDate());
            c.setExpirationDate(cred.getExpirationDate());
            c.setCredentialUrl(cred.getVerificationUrl());
            saveCertification(c);
            cred.setId(c.getCertificationId());
        }
    }

    public void deleteCredential(AcademicCredential cred) {
        if (cred == null || cred.getType() == null) return;
        if (cred.getType() == AcademicCredential.Type.ACHIEVEMENT) {
            deleteAchievement(cred.getId(), cred.getStudentId());
        } else {
            deleteCertification(cred.getId(), cred.getStudentId());
        }
    }

    public void saveAchievement(Achievement a) {
        Validator.notBlank(a.getTitle(), "Achievement title");
        Validator.maxLength(a.getTitle(), 200, "Achievement title");
        Validator.maxLength(a.getDescription(), 16000, "Description");
        Validator.maxLength(a.getOrganization(), 150, "Organization");
        Validator.maxLength(a.getVerificationUrl(), 255, "Verification URL");
        Validator.optionalUrlOrFilePath(a.getVerificationUrl(), "Verification URL / Proof");
        if (a.getStudentId() <= 0 || studentRepo.findById(a.getStudentId()).isEmpty()) {
            throw new AppException("A valid student is required.");
        }
        if (a.getAchievementId() > 0) {
            requireAchievementOwner(a.getAchievementId(), a.getStudentId());
            achievementRepo.update(a);
        } else {
            achievementRepo.save(a);
        }
    }

    public void deleteAchievement(int id, int studentId) {
        requireAchievementOwner(id, studentId);
        achievementRepo.delete(id);
    }

    private void requireAchievementOwner(int achievementId, int studentId) {
        Achievement stored = achievementRepo.findById(achievementId)
                .orElseThrow(() -> new AppException("Achievement not found."));
        if (stored.getStudentId() != studentId) {
            throw new AppException("You can only manage your own achievements.");
        }
    }

    public void saveCertification(Certification c) {
        Validator.notBlank(c.getCertificationName(), "Certification name");
        Validator.notBlank(c.getIssuingOrganization(), "Issuing organization");
        Validator.maxLength(c.getCertificationName(), 200, "Certification name");
        Validator.maxLength(c.getIssuingOrganization(), 150, "Issuing organization");
        Validator.maxLength(c.getCredentialUrl(), 255, "Credential URL");
        Validator.optionalUrlOrFilePath(c.getCredentialUrl(), "Credential URL / Proof");
        if (c.getStudentId() <= 0 || studentRepo.findById(c.getStudentId()).isEmpty()) {
            throw new AppException("A valid student is required.");
        }
        if (c.getIssueDate() != null && c.getExpirationDate() != null
                && c.getExpirationDate().isBefore(c.getIssueDate())) {
            throw new AppException("Expiration date cannot be before the issue date.");
        }
        if (c.getCertificationId() > 0) {
            requireCertificationOwner(c.getCertificationId(), c.getStudentId());
            certificationRepo.update(c);
        } else {
            certificationRepo.save(c);
        }
    }

    public void deleteCertification(int id, int studentId) {
        requireCertificationOwner(id, studentId);
        certificationRepo.delete(id);
    }

    private void requireCertificationOwner(int certificationId, int studentId) {
        Certification stored = certificationRepo.findById(certificationId)
                .orElseThrow(() -> new AppException("Certification not found."));
        if (stored.getStudentId() != studentId) {
            throw new AppException("You can only manage your own certifications.");
        }
    }
}
