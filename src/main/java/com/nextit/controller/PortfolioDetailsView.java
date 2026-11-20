package com.nextit.controller;

import com.nextit.model.Portfolio;
import com.nextit.service.AcademicService;
import com.nextit.service.DirectoryService;
import com.nextit.service.ProjectService;
import com.nextit.service.StudentService;
import com.nextit.service.StudentSkillService;
import com.nextit.util.Ui;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;

import java.util.List;

/**
 * Shared read-only portfolio view used by administrators when reviewing a
 * submission and by employers when opening an authorised portfolio.
 */
public final class PortfolioDetailsView {

    private static final DirectoryService directory = new DirectoryService();
    private static final StudentSkillService skills = new StudentSkillService();
    private static final ProjectService projects = new ProjectService();
    private static final StudentService students = new StudentService();
    private static final AcademicService academic = new AcademicService();

    private PortfolioDetailsView() {}

    public static void show(Portfolio p) {
        show(p, true);
    }

    /**
     * @param includeAcademicSummary when false (employer-facing view) grade and
     *                               attendance averages are omitted, because the
     *                               role matrix does not grant employers access
     *                               to a student's academic records.
     */
    public static void show(Portfolio p, boolean includeAcademicSummary) {
        int studentId = p.getStudentId();
        String studentName = directory.studentName(studentId);

        Node content;
        if (includeAcademicSummary) {
            content = Ui.form(
                    new String[]{"Student", "Headline", "Visibility", "Biography", "Career goal", "Technical skills",
                            "Projects", "Achievements", "Certifications", "Academic highlights"},
                    new Node[]{new Label(studentName), new Label(value(p.getHeadline())),
                            new Label(value(p.getVisibility())), wrapped(p.getBio()), wrapped(p.getCareerGoal()),
                            wrapped(technicalSkills(studentId)), wrapped(projectList(studentId)),
                            wrapped(achievementList(studentId)), wrapped(certificationList(studentId)),
                            new Label(String.format("Average grade: %.2f%%   |   Attendance: %.2f%%",
                                    academic.averageForStudent(studentId), academic.attendanceRateForStudent(studentId)))});
        } else {
            content = Ui.form(
                    new String[]{"Student", "Headline", "Visibility", "Biography", "Career goal", "Technical skills",
                            "Projects", "Achievements", "Certifications"},
                    new Node[]{new Label(studentName), new Label(value(p.getHeadline())),
                            new Label(value(p.getVisibility())), wrapped(p.getBio()), wrapped(p.getCareerGoal()),
                            wrapped(technicalSkills(studentId)), wrapped(projectList(studentId)),
                            wrapped(achievementList(studentId)), wrapped(certificationList(studentId))});
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(700);
        scroll.setPrefViewportHeight(560);
        Ui.viewDialog("Portfolio - " + studentName, scroll);
    }

    private static String technicalSkills(int studentId) {
        var skillNames = directory.skillNames();
        StringBuilder text = new StringBuilder();
        skills.forStudent(studentId).forEach(s -> text.append("• ")
                .append(skillNames.getOrDefault(s.getSkillId(), "Skill #" + s.getSkillId()))
                .append(" — ").append(value(s.getProficiencyLevel()))
                .append(s.getAssessmentScore() == null ? "" : " (" + s.getAssessmentScore() + "%)")
                .append('\n'));
        return text.toString();
    }

    private static String projectList(int studentId) {
        List<com.nextit.model.Project> list = projects.projectsForStudent(studentId);
        if (list.isEmpty()) return "No projects recorded.";

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            com.nextit.model.Project p = list.get(i);
            boolean isOwner = p.getStudentId() != null && p.getStudentId() == studentId;
            text.append("• ").append(p.getProjectTitle()).append(" — ").append(p.getProjectType());
            if (p.getStatus() != null && !p.getStatus().isBlank()) {
                text.append(" · ").append(p.getStatus());
            }
            if (p.getProjectDate() != null) {
                text.append(" (").append(Ui.formatDate(p.getProjectDate())).append(")");
            }
            text.append('\n');

            if (isOwner) {
                text.append("   Credit: Project Owner & Lead\n");
                List<com.nextit.model.ProjectMember> members = projects.membersForProject(p.getProjectId());
                if (!members.isEmpty()) {
                    text.append("   Group Members: ");
                    String memStr = members.stream().map(m -> {
                        String name = directory.studentName(m.getStudentId());
                        String role = m.getRole();
                        return (role == null || role.isBlank()) ? name : name + " (" + role + ")";
                    }).collect(java.util.stream.Collectors.joining(", "));
                    text.append(memStr).append('\n');
                }
            } else {
                text.append("   Credit: [Group Project - Credited Member]\n");
                java.util.Optional<com.nextit.model.ProjectMember> memOpt = projects.memberForProjectAndStudent(p.getProjectId(), studentId);
                if (memOpt.isPresent()) {
                    com.nextit.model.ProjectMember mem = memOpt.get();
                    if (mem.getRole() != null && !mem.getRole().isBlank()) {
                        text.append("   Assigned Role: ").append(mem.getRole()).append('\n');
                    }
                    String notes = mem.getContributionNotes();
                    if (notes != null && !notes.isBlank()) {
                        text.append("   Contribution Notes: ").append(notes).append('\n');
                    }
                }
                if (p.getStudentId() != null) {
                    text.append("   Project Creator/Owner: ").append(directory.studentName(p.getStudentId())).append('\n');
                }
            }

            if (p.getTechnologiesUsed() != null && !p.getTechnologiesUsed().isBlank()) {
                text.append("   Technologies: ").append(p.getTechnologiesUsed()).append('\n');
            }
            if (p.getRepositoryUrl() != null && !p.getRepositoryUrl().isBlank()) {
                text.append("   Repository: ").append(p.getRepositoryUrl()).append('\n');
            }
            if (p.getDemoUrl() != null && !p.getDemoUrl().isBlank()) {
                text.append("   Demo: ").append(p.getDemoUrl()).append('\n');
            }
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                text.append("   Description: ").append(p.getDescription()).append('\n');
            }
            if (i < list.size() - 1) {
                text.append('\n');
            }
        }
        return text.toString();
    }

    private static String achievementList(int studentId) {
        StringBuilder text = new StringBuilder();
        students.achievementsFor(studentId).forEach(a -> text.append("• ")
                .append(a.getTitle())
                .append(a.getOrganization() == null ? "" : " — " + a.getOrganization())
                .append('\n'));
        return text.toString();
    }

    private static String certificationList(int studentId) {
        StringBuilder text = new StringBuilder();
        students.certificationsFor(studentId).forEach(c -> text.append("• ")
                .append(c.getCertificationName())
                .append(c.getIssuingOrganization() == null ? "" : " — " + c.getIssuingOrganization())
                .append('\n'));
        return text.toString();
    }

    private static Label wrapped(String text) {
        Label label = new Label(text == null || text.isBlank() ? "Not provided" : text);
        label.setWrapText(true);
        return label;
    }

    private static String value(String text) { return text == null ? "" : text; }
}
