package com.nextit.controller;

import com.nextit.model.*;
import com.nextit.service.*;
import com.nextit.util.Ui;
import com.nextit.util.WindowBoundsManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Polished, recruiter-ready showcase portfolio view used by employers when opening
 * an authorised student portfolio and by instructors/administrators when reviewing submissions.
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

        ScrollPane scroll = createPortfolioView(p, includeAcademicSummary, false);
        scroll.setPrefViewportWidth(760);
        scroll.setPrefViewportHeight(640);

        Dialog<ButtonType> dialog = new Dialog<>();
        Window owner = AppController.getInstance() != null ? AppController.getInstance().getStage() : null;
        if (owner == null || !owner.isShowing()) {
            owner = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null);
        }
        if (owner != null && owner.isShowing()) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("Portfolio - " + studentName);
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        if (PortfolioDetailsView.class.getResource("/com/nextit/css/application.css") != null) {
            pane.getStylesheets().add(PortfolioDetailsView.class.getResource("/com/nextit/css/application.css").toExternalForm());
        }
        pane.setStyle("-fx-background-color: #FAF7F2;");
        pane.setContent(scroll);

        pane.getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.setText("Close Portfolio");
            closeBtn.getStyleClass().addAll("dialog-btn", "primary-button");
            closeBtn.setStyle("-fx-background-color: #991B1B; -fx-text-fill: #FFFFFF; -fx-font-weight: 800; -fx-padding: 8 24; -fx-background-radius: 6; -fx-cursor: hand;");
        }

        pane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                dialog.close();
                ev.consume();
            }
        });

        dialog.setResizable(true);
        dialog.setOnShowing(ev -> {
            if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                WindowBoundsManager.loadStageIcons(stage);
                stage.setMinWidth(760);
                stage.setMinHeight(640);
            }
        });

        dialog.showAndWait();
    }

    public static ScrollPane createPortfolioView(Portfolio p, boolean includeAcademicSummary, boolean includeExportButton) {
        int studentId = p.getStudentId();
        String studentName = directory.studentName(studentId);
        Student student = directory.students().stream()
                .filter(s -> s.getStudentId() == studentId)
                .findFirst()
                .orElse(null);

        VBox contentBox = new VBox(16);
        contentBox.setStyle("-fx-background-color: #FAF7F2;");
        contentBox.setPadding(new Insets(24, 28, 32, 28));
        contentBox.setPrefWidth(740);

        // 1. Hero Student Header Banner
        HBox headerCard = new HBox(16);
        headerCard.setAlignment(Pos.CENTER_LEFT);
        headerCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 18 20; -fx-effect: dropshadow(gaussian, rgba(26, 38, 52, 0.06), 6, 0.05, 0, 2);");

        StackPane avatar = new StackPane();
        avatar.setStyle("-fx-background-color: #FAF7F2; -fx-border-color: #991B1B; -fx-border-width: 2.5; -fx-border-radius: 50; -fx-background-radius: 50; -fx-min-width: 56; -fx-min-height: 56; -fx-max-width: 56; -fx-max-height: 56;");
        Label initialsLbl = new Label(getInitials(studentName));
        initialsLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #991B1B;");
        avatar.getChildren().add(initialsLbl);

        VBox titleBox = new VBox(4);
        Label nameLbl = new Label(studentName);
        nameLbl.setStyle("-fx-font-size: 21px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");

        String progText = student != null && student.getProgram() != null
                ? student.getProgram() + " • " + Ui.formatYearDisplay(student.getYearLevel())
                : "BS Information Technology • Year 3";
        Label progBadge = new Label(progText);
        progBadge.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 2 8; -fx-background-radius: 4; -fx-border-color: #E2E8F0; -fx-border-radius: 4; -fx-border-width: 1;");

        HBox progRow = new HBox(progBadge);
        progRow.setAlignment(Pos.CENTER_LEFT);

        String headStr = p.getHeadline() != null && !p.getHeadline().isBlank() ? p.getHeadline().trim() : "Aspiring Software Engineer";
        Label headLbl = new Label(headStr);
        headLbl.setStyle("-fx-font-size: 13.5px; -fx-font-weight: 600; -fx-text-fill: #5A6A7E;");
        titleBox.getChildren().addAll(nameLbl, progRow, headLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String visText = p.getVisibility() != null && !p.getVisibility().isBlank() ? p.getVisibility() : "Private";
        Label visibilityBadge = new Label(visText);
        String visStyle = switch (visText.toLowerCase(Locale.ROOT)) {
            case "approved" -> "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D; -fx-font-weight: 800; -fx-font-size: 12px; -fx-padding: 4 12; -fx-background-radius: 999; -fx-border-color: #86EFAC; -fx-border-radius: 999; -fx-border-width: 1;";
            case "public" -> "-fx-background-color: #EFF6FF; -fx-text-fill: #1D4ED8; -fx-font-weight: 800; -fx-font-size: 12px; -fx-padding: 4 12; -fx-background-radius: 999; -fx-border-color: #93C5FD; -fx-border-radius: 999; -fx-border-width: 1;";
            case "pending" -> "-fx-background-color: #FEF3C7; -fx-text-fill: #B45309; -fx-font-weight: 800; -fx-font-size: 12px; -fx-padding: 4 12; -fx-background-radius: 999; -fx-border-color: #FCD34D; -fx-border-radius: 999; -fx-border-width: 1;";
            default -> "-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-weight: 800; -fx-font-size: 12px; -fx-padding: 4 12; -fx-background-radius: 999; -fx-border-color: #CBD5E1; -fx-border-radius: 999; -fx-border-width: 1;";
        };
        visibilityBadge.setStyle(visStyle);

        headerCard.getChildren().addAll(avatar, titleBox, spacer, visibilityBadge);
        contentBox.getChildren().add(headerCard);

        // 2. Two-Column "About & Career Goal" Cards
        VBox aboutCard = new VBox(8);
        aboutCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14 16;");
        Label aboutHeader = new Label("BIOGRAPHY");
        aboutHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #991B1B; -fx-letter-spacing: 0.5px;");
        Label aboutText = new Label(p.getBio() != null && !p.getBio().isBlank() ? p.getBio().trim() : "No biography provided.");
        aboutText.setWrapText(true);
        aboutText.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1A2634; -fx-line-spacing: 2px;");
        aboutCard.getChildren().addAll(aboutHeader, aboutText);

        VBox goalCard = new VBox(8);
        goalCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14 16;");
        Label goalHeader = new Label("CAREER GOAL");
        goalHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #991B1B; -fx-letter-spacing: 0.5px;");
        Label goalText = new Label(p.getCareerGoal() != null && !p.getCareerGoal().isBlank() ? p.getCareerGoal().trim() : "No career goal specified.");
        goalText.setWrapText(true);
        goalText.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1A2634; -fx-line-spacing: 2px;");
        goalCard.getChildren().addAll(goalHeader, goalText);

        HBox aboutRow = new HBox(14, aboutCard, goalCard);
        HBox.setHgrow(aboutCard, Priority.ALWAYS);
        HBox.setHgrow(goalCard, Priority.ALWAYS);
        contentBox.getChildren().add(aboutRow);

        // Optional Academic Highlights (for Instructors/Admins)
        if (includeAcademicSummary) {
            HBox acadBox = new HBox(16);
            acadBox.setAlignment(Pos.CENTER_LEFT);
            acadBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12 16;");
            Label acadHeader = new Label("ACADEMIC HIGHLIGHTS:");
            acadHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #991B1B;");
            Label gradeBadge = new Label(String.format("Average Grade: %.2f%%", academic.averageForStudent(studentId)));
            gradeBadge.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #1E293B; -fx-font-weight: 700; -fx-font-size: 12px; -fx-padding: 4 10; -fx-background-radius: 6;");
            Label attBadge = new Label(String.format("Attendance: %.2f%%", academic.attendanceRateForStudent(studentId)));
            attBadge.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #1E293B; -fx-font-weight: 700; -fx-font-size: 12px; -fx-padding: 4 10; -fx-background-radius: 6;");
            acadBox.getChildren().addAll(acadHeader, gradeBadge, attBadge);
            contentBox.getChildren().add(acadBox);
        }

        // 3. Visual Technical Skills Section (with Progress Bars)
        VBox skillsSection = new VBox(10);
        Label skillsTitle = new Label("TECHNICAL SKILLS & COMPETENCIES");
        skillsTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #1A2634; -fx-letter-spacing: 0.5px;");
        skillsSection.getChildren().add(skillsTitle);

        List<StudentSkill> skillList = skills.forStudent(studentId);
        Map<Integer, TechnicalSkill> techMap = directory.technicalSkills().stream()
                .collect(Collectors.toMap(TechnicalSkill::getSkillId, s -> s, (a, b) -> a));

        if (skillList.isEmpty()) {
            VBox emptyCard = new VBox(new Label("No technical skills evaluated yet."));
            emptyCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14; -fx-text-fill: #7A889B;");
            skillsSection.getChildren().add(emptyCard);
        } else {
            GridPane skillsGrid = new GridPane();
            skillsGrid.setHgap(14);
            skillsGrid.setVgap(10);
            for (int i = 0; i < skillList.size(); i++) {
                StudentSkill s = skillList.get(i);
                TechnicalSkill ts = techMap.get(s.getSkillId());
                String skillName = ts != null ? ts.getSkillName() : directory.skillNames().getOrDefault(s.getSkillId(), "Skill #" + s.getSkillId());
                String category = ts != null && ts.getCategory() != null ? ts.getCategory() : "Technical";
                String proficiency = s.getProficiencyLevel() != null && !s.getProficiencyLevel().isBlank() ? s.getProficiencyLevel() : "Proficient";

                double scoreVal;
                if (s.getAssessmentScore() != null) {
                    scoreVal = s.getAssessmentScore();
                } else {
                    scoreVal = switch (proficiency.toLowerCase(Locale.ROOT)) {
                        case "expert", "master" -> 95.0;
                        case "advanced" -> 85.0;
                        case "intermediate" -> 70.0;
                        case "beginner" -> 50.0;
                        default -> 75.0;
                    };
                }

                VBox skillCard = new VBox(6);
                skillCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12;");

                HBox skillTop = new HBox(8);
                skillTop.setAlignment(Pos.CENTER_LEFT);

                Label sName = new Label(skillName);
                sName.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");

                Label catPill = new Label(category);
                catPill.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-size: 10.5px; -fx-font-weight: 600; -fx-padding: 2 6; -fx-background-radius: 4;");

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Label profBadge = Ui.badge(proficiency, badgeClassForProficiency(proficiency));

                Label scoreLbl = new Label(String.format("%.1f%%", scoreVal));
                scoreLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #991B1B;");

                skillTop.getChildren().addAll(sName, catPill, sp, profBadge, scoreLbl);

                ProgressBar bar = new ProgressBar(Math.min(1.0, Math.max(0.0, scoreVal / 100.0)));
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setPrefHeight(7);

                skillCard.getChildren().addAll(skillTop, bar);

                int col = i % 2;
                int row = i / 2;
                skillsGrid.add(skillCard, col, row);
                GridPane.setHgrow(skillCard, Priority.ALWAYS);
            }
            skillsSection.getChildren().add(skillsGrid);
        }
        contentBox.getChildren().add(skillsSection);

        // 4. Rich Project Showcase Cards
        VBox projectsSection = new VBox(12);
        Label projSectionTitle = new Label("FEATURED PROJECTS & COLLABORATIVE SHOWCASE");
        projSectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #1A2634; -fx-letter-spacing: 0.5px;");
        projectsSection.getChildren().add(projSectionTitle);

        List<Project> projectList = projects.projectsForStudent(studentId);
        if (projectList.isEmpty()) {
            VBox emptyProj = new VBox(new Label("No projects recorded yet."));
            emptyProj.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14; -fx-text-fill: #7A889B;");
            projectsSection.getChildren().add(emptyProj);
        } else {
            for (Project proj : projectList) {
                boolean isOwner = proj.getStudentId() != null && proj.getStudentId() == studentId;

                VBox card = new VBox(10);
                card.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 16 18; -fx-effect: dropshadow(gaussian, rgba(26, 38, 52, 0.05), 6, 0.05, 0, 2);");

                // Top row
                HBox top = new HBox(10);
                top.setAlignment(Pos.CENTER_LEFT);

                Label pTitle = new Label(proj.getProjectTitle() != null ? proj.getProjectTitle() : "Untitled Project");
                pTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");

                String pType = proj.getProjectType() != null ? proj.getProjectType() : "Project";
                Label typePill = new Label(pType);
                typePill.setStyle("-fx-background-color: #991B1B; -fx-text-fill: #FFFFFF; -fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 2 8; -fx-background-radius: 4;");

                String st = proj.getStatus() != null ? proj.getStatus() : "Completed";
                Label statusPill = Ui.badge(st, badgeClassForStatus(st));

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                String dateStr = proj.getProjectDate() != null ? Ui.formatDate(proj.getProjectDate()) : "";
                Label dateLbl = new Label(dateStr);
                dateLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");

                top.getChildren().addAll(pTitle, typePill, statusPill, sp, dateLbl);

                // Summary text
                Label desc = new Label(proj.getDescription() != null && !proj.getDescription().isBlank()
                        ? proj.getDescription().trim()
                        : "No detailed description provided.");
                desc.setWrapText(true);
                desc.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #4A5A6A; -fx-line-spacing: 2px;");

                // Metadata row (Role & Contributors)
                HBox metaRow = new HBox(10);
                metaRow.setAlignment(Pos.CENTER_LEFT);

                Label roleBadge;
                if (isOwner) {
                    roleBadge = new Label("Project Owner & Lead");
                    roleBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-border-color: #86EFAC; -fx-border-radius: 4;");
                } else {
                    roleBadge = new Label("Group Project - Credited Member");
                    roleBadge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #B45309; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-border-color: #FCD34D; -fx-border-radius: 4;");
                }

                String teamInfo;
                if (isOwner) {
                    List<ProjectMember> mems = projects.membersForProject(proj.getProjectId());
                    if (mems.isEmpty()) {
                        teamInfo = "Solo Project (No external group members tagged)";
                    } else {
                        teamInfo = mems.stream().map(m -> {
                            String mName = directory.studentName(m.getStudentId());
                            return (m.getRole() != null && !m.getRole().isBlank()) ? mName + " • " + m.getRole() : mName;
                        }).collect(Collectors.joining(", "));
                    }
                } else {
                    Optional<ProjectMember> memOpt = projects.memberForProjectAndStudent(proj.getProjectId(), studentId);
                    String role = memOpt.map(ProjectMember::getRole).orElse(null);
                    String ownerName = proj.getStudentId() != null ? directory.studentName(proj.getStudentId()) : "Lead";
                    teamInfo = "Owner: " + ownerName + (role != null && !role.isBlank() ? "  •  Role: " + role : "");
                }

                Label teamLbl = new Label(teamInfo);
                teamLbl.setWrapText(true);
                teamLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5A6A7E; -fx-font-weight: 500;");
                HBox.setHgrow(teamLbl, Priority.ALWAYS);

                metaRow.getChildren().addAll(roleBadge, teamLbl);

                // Bottom row: Technology tags and link buttons
                HBox bottomRow = new HBox(12);
                bottomRow.setAlignment(Pos.CENTER_LEFT);

                FlowPane techChips = new FlowPane(6, 6);
                HBox.setHgrow(techChips, Priority.ALWAYS);

                String rawTechs = proj.getTechnologiesUsed();
                if (rawTechs != null && !rawTechs.isBlank()) {
                    String[] tags = rawTechs.split("[,;•]+");
                    for (String t : tags) {
                        String clean = t.trim();
                        if (!clean.isBlank()) {
                            Label chip = new Label(clean);
                            chip.setStyle("-fx-background-color: #F1F5F9; -fx-border-color: #CBD5E1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #334155; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 3 8;");
                            techChips.getChildren().add(chip);
                        }
                    }
                }

                HBox linksBox = new HBox(8);
                linksBox.setAlignment(Pos.CENTER_RIGHT);

                if (proj.getRepositoryUrl() != null && !proj.getRepositoryUrl().isBlank()) {
                    Button repoBtn = new Button("View Repository ↗");
                    repoBtn.setStyle("-fx-background-color: #FAF7F2; -fx-border-color: #991B1B; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #991B1B; -fx-font-weight: 700; -fx-font-size: 11.5px; -fx-padding: 4 10; -fx-cursor: hand;");
                    final String repoUrl = proj.getRepositoryUrl();
                    repoBtn.setOnAction(e -> openUrl(repoUrl));
                    linksBox.getChildren().add(repoBtn);
                }

                if (proj.getDemoUrl() != null && !proj.getDemoUrl().isBlank()) {
                    Button demoBtn = new Button("Live Demo ↗");
                    demoBtn.setStyle("-fx-background-color: #991B1B; -fx-border-color: #991B1B; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #FFFFFF; -fx-font-weight: 700; -fx-font-size: 11.5px; -fx-padding: 4 10; -fx-cursor: hand;");
                    final String demoUrl = proj.getDemoUrl();
                    demoBtn.setOnAction(e -> openUrl(demoUrl));
                    linksBox.getChildren().add(demoBtn);
                }

                bottomRow.getChildren().addAll(techChips, linksBox);

                card.getChildren().addAll(top, desc, metaRow, bottomRow);
                projectsSection.getChildren().add(card);
            }
        }
        contentBox.getChildren().add(projectsSection);

        // 5. Achievements & Certifications Cards
        VBox achCard = new VBox(8);
        achCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14 16;");
        Label achHeader = new Label("🏆 ACHIEVEMENTS & HONORS");
        achHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #991B1B; -fx-letter-spacing: 0.5px;");
        achCard.getChildren().add(achHeader);

        List<Achievement> achList = students.achievementsFor(studentId);
        if (achList.isEmpty()) {
            Label noAch = new Label("No achievements recorded.");
            noAch.setStyle("-fx-font-size: 12px; -fx-text-fill: #7A889B; -fx-font-style: italic;");
            achCard.getChildren().add(noAch);
        } else {
            VBox achItems = new VBox(8);
            for (Achievement a : achList) {
                VBox item = new VBox(2);
                Label aTitle = new Label("🏆 " + a.getTitle());
                aTitle.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");
                String org = a.getOrganization() != null && !a.getOrganization().isBlank() ? a.getOrganization() : "Batangas State University";
                String dt = a.getAchievementDate() != null ? "  •  " + Ui.formatDate(a.getAchievementDate()) : "";
                Label aSub = new Label(org + dt);
                aSub.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 500;");

                String proof = a.getVerificationUrl();
                if (proof != null && !proof.isBlank()) {
                    HBox itemHeader = new HBox(8);
                    itemHeader.setAlignment(Pos.CENTER_LEFT);
                    itemHeader.getChildren().add(aTitle);

                    Region itemSp = new Region();
                    HBox.setHgrow(itemSp, Priority.ALWAYS);

                    Button proofBtn = new Button("🖼️ View Proof ↗");
                    proofBtn.setStyle("-fx-background-color: #FAF7F2; -fx-border-color: #991B1B; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #991B1B; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 2 8; -fx-cursor: hand;");
                    proofBtn.setOnAction(e -> com.nextit.util.CertificateImageViewer.show(proof, a.getTitle(), a.getOrganization()));

                    itemHeader.getChildren().addAll(itemSp, proofBtn);
                    item.getChildren().addAll(itemHeader, aSub);
                } else {
                    item.getChildren().addAll(aTitle, aSub);
                }
                achItems.getChildren().add(item);
            }
            achCard.getChildren().add(achItems);
        }

        VBox certCard = new VBox(8);
        certCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14 16;");
        Label certHeader = new Label("📜 LICENSES & CERTIFICATIONS");
        certHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #991B1B; -fx-letter-spacing: 0.5px;");
        certCard.getChildren().add(certHeader);

        List<Certification> certList = students.certificationsFor(studentId);
        if (certList.isEmpty()) {
            Label noCert = new Label("No certifications recorded.");
            noCert.setStyle("-fx-font-size: 12px; -fx-text-fill: #7A889B; -fx-font-style: italic;");
            certCard.getChildren().add(noCert);
        } else {
            VBox certItems = new VBox(8);
            for (Certification c : certList) {
                VBox item = new VBox(2);
                Label cTitle = new Label("📜 " + c.getCertificationName());
                cTitle.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");
                String issuer = c.getIssuingOrganization() != null && !c.getIssuingOrganization().isBlank() ? c.getIssuingOrganization() : "Credential Authority";
                String dt = c.getIssueDate() != null ? "  •  " + Ui.formatDate(c.getIssueDate()) : "";
                Label cSub = new Label(issuer + dt);
                cSub.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 500;");

                String proof = c.getCredentialUrl();
                if (proof != null && !proof.isBlank()) {
                    HBox itemHeader = new HBox(8);
                    itemHeader.setAlignment(Pos.CENTER_LEFT);
                    itemHeader.getChildren().add(cTitle);

                    Region itemSp = new Region();
                    HBox.setHgrow(itemSp, Priority.ALWAYS);

                    Button proofBtn = new Button("🖼️ View Proof ↗");
                    proofBtn.setStyle("-fx-background-color: #FAF7F2; -fx-border-color: #991B1B; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #991B1B; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 2 8; -fx-cursor: hand;");
                    proofBtn.setOnAction(e -> com.nextit.util.CertificateImageViewer.show(proof, c.getCertificationName(), c.getIssuingOrganization()));

                    itemHeader.getChildren().addAll(itemSp, proofBtn);
                    item.getChildren().addAll(itemHeader, cSub);
                } else {
                    item.getChildren().addAll(cTitle, cSub);
                }
                certItems.getChildren().add(item);
            }
            certCard.getChildren().add(certItems);
        }

        HBox achCertRow = new HBox(14, achCard, certCard);
        HBox.setHgrow(achCard, Priority.ALWAYS);
        HBox.setHgrow(certCard, Priority.ALWAYS);
        contentBox.getChildren().add(achCertRow);

        // 6. Optional Export Button (for Student Digital Portfolio Preview)
        if (includeExportButton) {
            Button exportBtn = Ui.button("📄 Export / Print Portfolio Report", "clay-btn-secondary");
            exportBtn.setStyle("-fx-font-weight: 700; -fx-padding: 8 18;");
            exportBtn.setOnAction(ev -> exportPortfolioHtml(studentId, studentName, student, p));
            HBox exportRow = new HBox(exportBtn);
            exportRow.setAlignment(Pos.CENTER_RIGHT);
            exportRow.setStyle("-fx-padding: 8 0 0 0;");
            contentBox.getChildren().add(exportRow);
        }

        // 7. Scrollable Layout Container
        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #FAF7F2; -fx-border-color: transparent; -fx-padding: 0;");
        return scroll;
    }

    private static void exportPortfolioHtml(int studentId, String studentName, Student student, Portfolio p) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Student Portfolio Report");
        chooser.setInitialFileName("student_portfolio_" + studentId + ".html");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Printable HTML Report (*.html)", "*.html"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );
        Window owner = AppController.getInstance() != null ? AppController.getInstance().getStage() : null;
        if (owner == null || !owner.isShowing()) {
            owner = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null);
        }
        File f = chooser.showSaveDialog(owner);
        if (f != null) {
            try (PrintWriter pw = new PrintWriter(f, StandardCharsets.UTF_8)) {
                pw.println("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>" + escapeHtml(studentName) + " - Portfolio</title>");
                pw.println("<style>body{font-family:sans-serif;margin:40px;background:#FAF7F2;color:#1A2634;} h1{color:#991B1B;} h2{border-bottom:1px solid #C6A75E;padding-bottom:4px;color:#1A2634;} .box{background:#fff;border-radius:12px;border:1px solid #EFE2CA;padding:24px;box-shadow:0 4px 12px rgba(0,0,0,0.06);margin-bottom:20px;} .meta{color:#5A6A7E;font-size:14px;margin-bottom:16px;}</style></head><body>");
                pw.println("<div class='box'><h1>" + escapeHtml(studentName) + "</h1>");
                pw.println("<p class='meta'><b>" + (student == null ? "" : escapeHtml(student.getProgram()) + " • " + Ui.formatYearDisplay(student.getYearLevel())) + "</b></p>");
                pw.println("<h2>Professional Headline</h2><p>" + escapeHtml(p.getHeadline()) + "</p>");
                pw.println("<h2>Biography</h2><p>" + escapeHtml(p.getBio()) + "</p>");
                pw.println("<h2>Career Goal</h2><p>" + escapeHtml(p.getCareerGoal()) + "</p>");
                pw.println("</div></body></html>");
                com.nextit.util.ExportService.promptOpenFile(f, "Portfolio Exported", "Portfolio report saved successfully to:\n" + f.getAbsolutePath());
            } catch (Exception ex) {
                com.nextit.util.Alerts.error("Failed to export portfolio: " + ex.getMessage());
            }
        }
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String getInitials(String name) {
        if (name == null || name.isBlank()) return "ST";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private static String badgeClassForProficiency(String prof) {
        if (prof == null) return "badge-pill-cream";
        return switch (prof.toLowerCase(Locale.ROOT)) {
            case "expert", "master" -> "badge-pill-mint";
            case "advanced" -> "badge-pill-navy";
            case "intermediate" -> "badge-pill-yellow";
            default -> "badge-pill-cream";
        };
    }

    private static String badgeClassForStatus(String status) {
        if (status == null) return "badge-pill-cream";
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "completed", "active" -> "badge-pill-mint";
            case "in progress" -> "badge-pill-navy";
            case "archived", "closed" -> "badge-pill-pink";
            default -> "badge-pill-cream";
        };
    }

    private static void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            String trimmed = url.trim();
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                trimmed = "https://" + trimmed;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(trimmed));
            }
        } catch (Exception ex) {
            com.nextit.util.Alerts.warn("Could not open link in desktop browser: " + ex.getMessage());
        }
    }
}
