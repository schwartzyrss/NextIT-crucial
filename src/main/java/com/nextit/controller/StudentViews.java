package com.nextit.controller;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.security.Session;
import com.nextit.service.*;
import com.nextit.util.Alerts;
import com.nextit.util.CertificateImageViewer;
import com.nextit.util.CertificateUploaderBox;
import com.nextit.util.Ui;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class StudentViews {

    private static final AcademicService academic = new AcademicService();
    private static final ProjectService projects = new ProjectService();
    private static final CareerService career = new CareerService();
    private static final PortfolioService portfolio = new PortfolioService();
    private static final StudentSkillService skillService = new StudentSkillService();
    private static final StudentService studentService = new StudentService();
    private static final SupportService support = new SupportService();
    private static final DirectoryService directory = new DirectoryService();
    private static final ProfileService profiles = new ProfileService();
    private static final RecommendationLetterService recLetters = new RecommendationLetterService();

    private StudentViews() {}

    private static int studentId() {
        return academic.studentIdForUser(Session.currentUser().getUserId());
    }

    public static Node profile() {
        User account = profiles.account(Session.currentUser().getUserId());
        Student student = profiles.studentForUser(account.getUserId())
                .orElseThrow(() -> new AppException("Student profile not found."));
        Session.setCurrentStudent(student);

        // 1. Identity Hero Cushion
        HBox hero = new HBox(20);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("profile-hero-card");

        StackPane avatar = Ui.avatar(Ui.initials(account.getFullName()), "avatar-lavender");

        VBox identityDetails = new VBox(6);
        Label nameLabel = new Label(value(account.getFullName()));
        nameLabel.getStyleClass().add("profile-hero-name");

        HBox badges = new HBox(8);
        badges.setAlignment(Pos.CENTER_LEFT);
        badges.getChildren().addAll(
                Ui.badge("@" + account.getUsername(), "badge-pill-cream"),
                Ui.badge(String.valueOf(account.getRole()), "badge-pill-lavender"),
                Ui.badge(value(student.getEnrollmentStatus()), "badge-pill-mint")
        );

        Region goldSeparator = new Region();
        goldSeparator.getStyleClass().add("profile-gold-separator");
        VBox.setMargin(goldSeparator, new Insets(4, 0, 0, 0));

        identityDetails.getChildren().addAll(nameLabel, badges, goldSeparator);

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        VBox heroRight = new VBox(6);
        heroRight.setAlignment(Pos.CENTER_RIGHT);
        String heroProg = (student.getProgram() != null && !student.getProgram().isBlank())
                ? student.getProgram().trim()
                : "Undeclared Program";
        Label programHighlight = new Label(heroProg);
        programHighlight.getStyleClass().add("profile-hero-highlight-title");
        String currentSectionDisplay = formatSectionDisplay(student.getSection());
        Label yearSectionHighlight = new Label(Ui.formatYearDisplay(student.getYearLevel()) + " • " + currentSectionDisplay);
        yearSectionHighlight.getStyleClass().add("profile-hero-highlight-subtitle");
        heroRight.getChildren().addAll(programHighlight, yearSectionHighlight);

        hero.getChildren().addAll(avatar, identityDetails, heroSpacer, heroRight);

        // 2. Account Information Tray
        GridPane accountGrid = new GridPane();
        accountGrid.setHgap(16);
        accountGrid.setVgap(14);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        accountGrid.getColumnConstraints().addAll(col1, col2);

        accountGrid.add(Ui.propertyTile("Username", value(account.getUsername())), 0, 0);
        accountGrid.add(Ui.propertyTile("Email Address", value(account.getEmail())), 1, 0);
        accountGrid.add(Ui.propertyTile("Full Name", value(account.getFullName())), 0, 1);
        accountGrid.add(Ui.propertyTile("Account Role", Ui.badge(String.valueOf(account.getRole()), "badge-pill-lavender")), 1, 1);

        VBox accountTray = Ui.tray("Account Details", "Your personal credentials and access role in NextIT", accountGrid);
        HBox.setHgrow(accountTray, Priority.ALWAYS);

        // 3. Academic & Enrollment Information Tray
        GridPane academicGrid = new GridPane();
        academicGrid.setHgap(16);
        academicGrid.setVgap(14);
        academicGrid.getColumnConstraints().addAll(col1, col2);

        academicGrid.add(Ui.propertyTile("Student Number", value(student.getStudentNumber())), 0, 0);
        String degreeProg = (student.getProgram() != null && !student.getProgram().isBlank())
                ? student.getProgram().trim()
                : "Undeclared";
        academicGrid.add(Ui.propertyTile("Degree Program", degreeProg), 1, 0);
        academicGrid.add(createEditableYearLevelTile(student, yearSectionHighlight), 0, 1);
        academicGrid.add(createEditableSectionTile(student, yearSectionHighlight), 1, 1);
        academicGrid.add(Ui.propertyTile("Enrollment Status", Ui.badge(value(student.getEnrollmentStatus()), "badge-pill-mint")), 0, 2);

        VBox academicTray = Ui.tray("Academic Standing", "Course program placement and enrollment registration", academicGrid);
        HBox.setHgrow(academicTray, Priority.ALWAYS);

        HBox traysRow = new HBox(20, accountTray, academicTray);

        // 4. Notice Cushion
        HBox notice = new HBox(12);
        notice.getStyleClass().add("profile-notice-cushion");
        Label noticeIcon = new Label("ℹ");
        noticeIcon.getStyleClass().add("profile-notice-icon");
        Label noticeText = new Label("Student records and credentials are synchronized with the registrar. Contact the department registrar if any details need correction.");
        noticeText.getStyleClass().add("profile-notice-text");
        noticeText.setWrapText(true);
        notice.getChildren().addAll(noticeIcon, noticeText);

        return Ui.page("My Profile", "Your account and student profile information. Contact the department registrar if any details need correction.",
                hero, traysRow, notice);
    }

    private static String formatYearLevelDisplay(int yearLevel) {
        if (yearLevel == 5) return "Irregular / 5th Year";
        return Ui.formatYearDisplay(yearLevel);
    }

    private static VBox createEditableYearLevelTile(Student student, Label yearSectionHighlight) {
        VBox tile = new VBox(6);
        tile.getStyleClass().add("profile-prop-tile");

        // Header Row: Label + Change Action Button (styled identically to Class Section)
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("YEAR LEVEL");
        label.getStyleClass().add("profile-prop-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button changeBtn = new Button("✏ Change");
        changeBtn.getStyleClass().add("profile-prop-action-btn");
        changeBtn.setFocusTraversable(false);

        headerRow.getChildren().addAll(label, spacer, changeBtn);

        // View Mode: Display current year level
        VBox viewPane = new VBox(2);
        Label valueLabel = new Label(formatYearLevelDisplay(student.getYearLevel()));
        valueLabel.getStyleClass().add("profile-prop-value");
        viewPane.getChildren().add(valueLabel);

        tile.getChildren().addAll(headerRow, viewPane);

        changeBtn.setOnAction(e -> {
            Window owner = changeBtn.getScene() != null ? changeBtn.getScene().getWindow() : null;
            showChangeYearLevelModal(student, valueLabel, yearSectionHighlight, owner);
        });

        return tile;
    }

    private static void showChangeYearLevelModal(Student student, Label valueLabel, Label yearSectionHighlight, Window ownerWindow) {
        Stage modalStage = new Stage();
        modalStage.initModality(Modality.APPLICATION_MODAL);
        if (ownerWindow != null && ownerWindow.isShowing()) {
            modalStage.initOwner(ownerWindow);
        }
        modalStage.initStyle(StageStyle.TRANSPARENT);
        modalStage.setTitle("Change Year Level");

        StackPane rootPane = new StackPane();
        rootPane.setStyle("-fx-background-color: transparent; -fx-padding: 30;");

        VBox card = new VBox(16);
        card.setPrefWidth(440);
        card.setMaxWidth(440);
        card.setStyle(
                "-fx-background-color: #FAF8F5; " +
                "-fx-background-radius: 16; " +
                "-fx-border-color: #E2D9CC; " +
                "-fx-border-radius: 16; " +
                "-fx-border-width: 1.5; " +
                "-fx-padding: 24 26 22 26; " +
                "-fx-effect: dropshadow(gaussian, rgba(26, 38, 52, 0.25), 24, 0.15, 0, 8);"
        );

        // 1. Header Banner
        VBox headerBox = new VBox(4);
        Label titleLabel = new Label("Change Year Level");
        titleLabel.setStyle("-fx-font-size: 17.5px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");

        Label subLabel = new Label("Select your current academic standing year level.");
        subLabel.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #5A6A7E; -fx-font-weight: 500;");
        headerBox.getChildren().addAll(titleLabel, subLabel);

        // 2. Year Level Dropdown
        List<String> options = List.of("Year 1", "Year 2", "Year 3", "Year 4", "Irregular / 5th Year");
        ComboBox<String> yearCombo = new ComboBox<>(FXCollections.observableArrayList(options));
        yearCombo.setMaxWidth(Double.MAX_VALUE);
        yearCombo.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 10; -fx-font-size: 13px;");

        String currentOption = switch (student.getYearLevel()) {
            case 1 -> "Year 1";
            case 2 -> "Year 2";
            case 4 -> "Year 4";
            case 5 -> "Irregular / 5th Year";
            default -> "Year 3";
        };
        yearCombo.setValue(currentOption);

        VBox formField = new VBox(6, new Label("Select Year Level:"), yearCombo);

        // 3. Action Buttons
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(
                "-fx-background-color: #FAF8F5; " +
                "-fx-text-fill: #1A2634; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: 700; " +
                "-fx-border-color: rgba(74, 90, 106, 0.35); " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8; " +
                "-fx-padding: 8 20; " +
                "-fx-cursor: hand;"
        );
        cancelBtn.setOnAction(e -> modalStage.close());

        Button saveBtn = new Button("Save");
        saveBtn.setStyle(
                "-fx-background-color: #991B1B; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: 800; " +
                "-fx-padding: 8 24; " +
                "-fx-background-radius: 8; " +
                "-fx-cursor: hand;"
        );
        saveBtn.setOnMouseEntered(e -> saveBtn.setStyle(
                "-fx-background-color: #7F1D1D; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: 800; " +
                "-fx-padding: 8 24; " +
                "-fx-background-radius: 8; " +
                "-fx-cursor: hand;"
        ));
        saveBtn.setOnMouseExited(e -> saveBtn.setStyle(
                "-fx-background-color: #991B1B; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: 800; " +
                "-fx-padding: 8 24; " +
                "-fx-background-radius: 8; " +
                "-fx-cursor: hand;"
        ));

        saveBtn.setOnAction(e -> {
            String selectedVal = yearCombo.getValue();
            if (selectedVal == null || selectedVal.isBlank()) return;

            int parsedYear = switch (selectedVal) {
                case "Year 1" -> 1;
                case "Year 2" -> 2;
                case "Year 4" -> 4;
                case "Irregular / 5th Year" -> 5;
                default -> 3;
            };

            saveBtn.setDisable(true);
            cancelBtn.setDisable(true);
            yearCombo.setDisable(true);

            profiles.updateStudentYearLevelAsync(student, parsedYear)
                    .thenAccept(updatedStudent -> Platform.runLater(() -> {
                        modalStage.close();

                        // 1. Update "My Profile" -> YEAR LEVEL sub-card
                        String formattedYear = formatYearLevelDisplay(updatedStudent.getYearLevel());
                        valueLabel.setText(formattedYear);

                        // 2. Update "My Profile" -> hero banner subtitle ("Year X • Section A")
                        String currentSection = formatSectionDisplay(updatedStudent.getSection());
                        yearSectionHighlight.setText(formattedYear + " • " + currentSection);

                        // 3. Confirmation feedback
                        Alerts.info("Year level updated to " + formattedYear + " successfully.");
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            saveBtn.setDisable(false);
                            cancelBtn.setDisable(false);
                            yearCombo.setDisable(false);
                            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                            Alerts.error(msg != null ? msg : "Failed to update year level.");
                        });
                        return null;
                    });
        });

        buttonBar.getChildren().addAll(cancelBtn, saveBtn);
        card.getChildren().addAll(headerBox, formField, buttonBar);
        rootPane.getChildren().add(card);

        // Pre-layout passes to ensure scene is never blank
        rootPane.applyCss();
        rootPane.layout();
        card.applyCss();
        card.layout();

        Scene modalScene = new Scene(rootPane);
        modalScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        if (StudentViews.class.getResource("/com/nextit/css/application.css") != null) {
            modalScene.getStylesheets().add(StudentViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
        }

        modalScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                modalStage.close();
                ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ENTER) {
                saveBtn.fire();
                ev.consume();
            }
        });

        modalStage.setScene(modalScene);

        if (ownerWindow != null) {
            modalStage.setOnShown(ev -> {
                double x = ownerWindow.getX() + (ownerWindow.getWidth() - modalStage.getWidth()) / 2.0;
                double y = ownerWindow.getY() + (ownerWindow.getHeight() - modalStage.getHeight()) / 2.0;
                modalStage.setX(Math.max(0, x));
                modalStage.setY(Math.max(0, y));
            });
        } else {
            modalStage.centerOnScreen();
        }

        modalStage.showAndWait();
    }

    private static String formatSectionDisplay(String section) {
        if (section == null || section.isBlank()) return "Section Unassigned";
        String s = section.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("section")) {
            return s;
        }
        return "Section " + s;
    }

    private static String cleanSectionCode(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.matches("(?i)^section\\s+.+$")) {
            return s.replaceFirst("(?i)^section\\s+", "").trim();
        }
        return s;
    }

    private static VBox createEditableSectionTile(Student student, Label yearSectionHighlight) {
        VBox tile = new VBox(6);
        tile.getStyleClass().add("profile-prop-tile");

        // Header Row: Label + Change Action Button
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("CLASS SECTION");
        label.getStyleClass().add("profile-prop-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button changeBtn = new Button("✏ Change");
        changeBtn.getStyleClass().add("profile-prop-action-btn");
        changeBtn.setFocusTraversable(false);

        headerRow.getChildren().addAll(label, spacer, changeBtn);

        // View Mode Pane: Current section label
        VBox viewPane = new VBox(2);
        Label valueLabel = new Label(formatSectionDisplay(student.getSection()));
        valueLabel.getStyleClass().add("profile-prop-value");
        viewPane.getChildren().add(valueLabel);

        // Edit Mode Pane: Inline dropdown/text input + Save & Cancel buttons
        VBox editPane = new VBox(8);
        editPane.setVisible(false);
        editPane.setManaged(false);

        ComboBox<String> sectionCombo = new ComboBox<>();
        sectionCombo.getItems().addAll("Section A", "Section B", "Section C", "Section D");
        sectionCombo.setEditable(true);
        sectionCombo.setMaxWidth(Double.MAX_VALUE);
        sectionCombo.setPromptText("Select or enter section...");

        HBox actionRow = new HBox(8);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("profile-prop-save-btn");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("profile-prop-cancel-btn");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        spinner.setMinSize(16, 16);
        spinner.setMaxSize(16, 16);
        spinner.setVisible(false);
        spinner.setManaged(false);

        Label statusFeedback = new Label("Updating...");
        statusFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #4A5A6A; -fx-font-weight: 600;");
        statusFeedback.setVisible(false);
        statusFeedback.setManaged(false);

        actionRow.getChildren().addAll(saveBtn, cancelBtn, spinner, statusFeedback);

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9E0B0F; -fx-font-weight: 700;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        editPane.getChildren().addAll(sectionCombo, actionRow, errorLbl);

        Runnable showEditMode = () -> {
            String curDisplay = formatSectionDisplay(student.getSection());
            if (sectionCombo.getItems().contains(curDisplay)) {
                sectionCombo.setValue(curDisplay);
            } else {
                sectionCombo.setValue(curDisplay);
                sectionCombo.getEditor().setText(curDisplay);
            }
            errorLbl.setVisible(false);
            errorLbl.setManaged(false);
            viewPane.setVisible(false);
            viewPane.setManaged(false);
            changeBtn.setVisible(false);
            changeBtn.setManaged(false);
            editPane.setVisible(true);
            editPane.setManaged(true);
            sectionCombo.requestFocus();
        };

        Runnable showViewMode = () -> {
            editPane.setVisible(false);
            editPane.setManaged(false);
            errorLbl.setVisible(false);
            errorLbl.setManaged(false);
            viewPane.setVisible(true);
            viewPane.setManaged(true);
            changeBtn.setVisible(true);
            changeBtn.setManaged(true);
        };

        changeBtn.setOnAction(e -> showEditMode.run());
        cancelBtn.setOnAction(e -> showViewMode.run());

        saveBtn.setOnAction(e -> {
            String rawVal = sectionCombo.getValue();
            if (rawVal == null || rawVal.isBlank()) {
                rawVal = sectionCombo.getEditor().getText();
            }
            if (rawVal == null || rawVal.trim().isBlank()) {
                errorLbl.setText("Section cannot be empty. Please select or enter a section.");
                errorLbl.setVisible(true);
                errorLbl.setManaged(true);
                return;
            }

            String cleanCode = cleanSectionCode(rawVal);
            if (cleanCode.length() > 30) {
                errorLbl.setText("Section name must be 30 characters or fewer.");
                errorLbl.setVisible(true);
                errorLbl.setManaged(true);
                return;
            }

            saveBtn.setDisable(true);
            cancelBtn.setDisable(true);
            sectionCombo.setDisable(true);
            spinner.setVisible(true);
            spinner.setManaged(true);
            statusFeedback.setVisible(true);
            statusFeedback.setManaged(true);
            errorLbl.setVisible(false);
            errorLbl.setManaged(false);

            profiles.updateStudentSectionAsync(student, cleanCode)
                    .thenAccept(updatedStudent -> Platform.runLater(() -> {
                        saveBtn.setDisable(false);
                        cancelBtn.setDisable(false);
                        sectionCombo.setDisable(false);
                        spinner.setVisible(false);
                        spinner.setManaged(false);
                        statusFeedback.setVisible(false);
                        statusFeedback.setManaged(false);

                        String formatted = formatSectionDisplay(updatedStudent.getSection());
                        valueLabel.setText(formatted);
                        yearSectionHighlight.setText(Ui.formatYearDisplay(updatedStudent.getYearLevel()) + " • " + formatted);

                        showViewMode.run();
                        Alerts.info("Class section updated to " + formatted + " successfully.");
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            saveBtn.setDisable(false);
                            cancelBtn.setDisable(false);
                            sectionCombo.setDisable(false);
                            spinner.setVisible(false);
                            spinner.setManaged(false);
                            statusFeedback.setVisible(false);
                            statusFeedback.setManaged(false);

                            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                            errorLbl.setText(msg != null ? msg : "Failed to update section.");
                            errorLbl.setVisible(true);
                            errorLbl.setManaged(true);
                            Alerts.error("Could not update class section: " + (msg != null ? msg : "An error occurred."));
                        });
                        return null;
                    });
        });

        tile.getChildren().addAll(headerRow, viewPane, editPane);
        return tile;
    }

    public static Node dashboard() {
        int sid = studentId();
        User currentUser = Session.currentUser();
        Student student = profiles.studentForUser(currentUser.getUserId()).orElse(null);

        // 1. Hero cushion with status and live badges
        HBox hero = new HBox(16);
        hero.getStyleClass().add("page-header-cushion");
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox heroTitles = new VBox(4);
        heroTitles.getChildren().addAll(
                Ui.title("Student Dashboard"),
                Ui.subtitle("Welcome back, " + currentUser.getFullName() + ". Track your academic standing, technical milestones, and career progress.")
        );

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        HBox statusPillBox = new HBox(8);
        statusPillBox.setAlignment(Pos.CENTER_RIGHT);
        StackPane statusDot = Ui.statusDot(true);
        Label statusText = new Label("Active Student");
        statusText.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F; -fx-font-size: 12px;");
        HBox liveChip = new HBox(8, statusDot, statusText);
        liveChip.setAlignment(Pos.CENTER);
        liveChip.getStyleClass().addAll("badge-pill", "badge-pill-mint");

        String progTag = student != null && student.getProgram() != null ? student.getProgram() : "BS Information Technology";
        Label progChip = Ui.badge(progTag, "badge-pill-lavender");
        statusPillBox.getChildren().addAll(liveChip, progChip);

        hero.getChildren().addAll(heroTitles, heroSpacer, statusPillBox);

        // Fixed sticky header container
        VBox headerContainer = new VBox(hero);
        headerContainer.setStyle("-fx-background-color: #FDFBFA;");
        headerContainer.setPadding(new Insets(24, 24, 10, 24));

        // 2. Full-Width 4-Column Stat Cards (Row 1 & Row 2)
        HBox cards1 = new HBox(14);
        HBox cards2 = new HBox(14);
        cards1.setFillHeight(true);
        cards2.setFillHeight(true);

        // 3. Middle Analytics & Academic Standing Row
        HBox middleRow = new HBox(16);
        middleRow.setAlignment(Pos.TOP_LEFT);

        HBox chartsRow = new HBox(14);
        chartsRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(chartsRow, Priority.ALWAYS);

        VBox standingHolder = new VBox();
        standingHolder.setPrefWidth(320);
        standingHolder.setMinWidth(280);
        standingHolder.setMaxWidth(360);

        middleRow.getChildren().addAll(chartsRow, standingHolder);

        // 4. Lower Evaluations & Student Hub Row
        HBox lowerRow = new HBox(16);
        lowerRow.setAlignment(Pos.TOP_LEFT);

        VBox gradesTray = new VBox(14);
        gradesTray.getStyleClass().add("clay-tray");
        gradesTray.setFillWidth(true);
        HBox.setHgrow(gradesTray, Priority.ALWAYS);

        Map<Integer, String> subjects = directory.subjectNames();
        Map<Integer, Enrollment> enrollmentMap = new java.util.HashMap<>();
        for (Enrollment e : academic.enrollmentsForStudent(sid)) {
            enrollmentMap.put(e.getEnrollmentId(), e);
        }

        TableView<Grade> gradeTable = Ui.table("No grades recorded yet.");
        gradeTable.setPrefHeight(290);
        gradeTable.setMinHeight(220);

        Ui.addColumn(gradeTable, "Subject", g -> {
            Enrollment e = enrollmentMap.get(g.getEnrollmentId());
            if (e != null) {
                return subjects.getOrDefault(e.getSubjectId(), "Subject #" + e.getSubjectId());
            }
            return "Enrollment #" + g.getEnrollmentId();
        });
        Ui.addColumn(gradeTable, "Activity", Grade::getActivityName);
        gradeTable.getColumns().add(Ui.actionColumn("Type", g -> {
            String t = g.getActivityType() == null ? "Activity" : g.getActivityType();
            String pill = switch (t.toLowerCase(Locale.ROOT)) {
                case "examination" -> "badge-pill-pink";
                case "laboratory" -> "badge-pill-blue";
                case "quiz" -> "badge-pill-yellow";
                case "project" -> "badge-pill-mint";
                default -> "badge-pill-lavender";
            };
            return Ui.badge(t, pill);
        }));
        Ui.addColumn(gradeTable, "Score", g -> g.getScore() + " / " + g.getMaxScore());
        gradeTable.getColumns().add(Ui.actionColumn("Grade", g -> {
            String grText = g.getGrade() == null ? "" : String.format("%.1f%%", g.getGrade());
            return Ui.badge(grText, "badge-pill-cream");
        }));
        Ui.addColumn(gradeTable, "Remarks", Grade::getRemarks);

        TextField searchGrades = Ui.field("Filter by subject or activity...");
        searchGrades.setPrefWidth(220);

        HBox trayHeader = new HBox(12);
        trayHeader.setAlignment(Pos.CENTER_LEFT);

        VBox trayTitles = new VBox(3);
        Label trayTitle = new Label("Academic Evaluations & Grades");
        trayTitle.getStyleClass().add("tray-title");
        Label traySub = new Label("Official marks and feedback from course instructors");
        traySub.getStyleClass().add("tray-subtitle");
        trayTitles.getChildren().addAll(trayTitle, traySub);

        Region traySpacer = new Region();
        HBox.setHgrow(traySpacer, Priority.ALWAYS);

        Button exportCsvBtn = Ui.button("Export CSV", "secondary-button");
        exportCsvBtn.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToCsv(gradeTable, "student_grades.csv", "Academic Evaluations & Grades", exportCsvBtn.getScene().getWindow()));

        Button exportReportBtn = Ui.button("Print / PDF", "refresh-pill-button");
        exportReportBtn.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToHtmlReport(gradeTable, "student_grades_report.html", "Academic Evaluations & Grades", exportReportBtn.getScene().getWindow()));

        trayHeader.getChildren().addAll(trayTitles, traySpacer, searchGrades, exportCsvBtn, exportReportBtn);
        gradesTray.getChildren().addAll(trayHeader, gradeTable);

        VBox hubHolder = new VBox();
        hubHolder.setPrefWidth(320);
        hubHolder.setMinWidth(280);
        hubHolder.setMaxWidth(360);

        lowerRow.getChildren().addAll(gradesTray, hubHolder);

        // Self-refreshing dashboard logic
        final Runnable[] refreshHolder = new Runnable[1];
        Runnable refreshDashboard = () -> {
            List<Grade> grades = academic.gradesForStudent(sid);
            List<Attendance> attendance = academic.attendanceForStudent(sid);
            Portfolio p = portfolio.portfolioForStudent(sid).orElse(null);
            List<Enrollment> enrollments = academic.enrollmentsForStudent(sid);

            subjects.clear();
            subjects.putAll(directory.subjectNames());
            enrollmentMap.clear();
            for (Enrollment e : enrollments) {
                enrollmentMap.put(e.getEnrollmentId(), e);
            }

            // 1. Update 8 Full-Width Stat Cards with Pastel Clay Palette
            cards1.getChildren().clear();
            cards2.getChildren().clear();

            VBox c1 = Ui.card("Enrolled Subjects", String.valueOf(enrollments.size()), "card-cream",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.academic(), "Academic"); });
            VBox c2 = Ui.card("Average Grade", String.format("%.1f%%", GradeCalculatorFacade.average(grades)), "card-mint",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.academic(), "Academic"); });
            VBox c3 = Ui.card("Attendance Rate", String.format("%.1f%%", GradeCalculatorFacade.attendance(attendance)), "card-lavender",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.academic(), "Academic"); });
            VBox c4 = Ui.card("Technical Skills", String.valueOf(skillService.forStudent(sid).size()), "card-pink",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.skills(), "Skills"); });

            VBox c5 = Ui.card("Active Projects", String.valueOf(projects.projectsForStudent(sid).size()), "card-yellow",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.projects(), "Projects"); });
            VBox c6 = Ui.card("Achievements & Certifications",
                    String.valueOf(studentService.achievementsFor(sid).size() + studentService.certificationsFor(sid).size()), "card-blue",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.achievementsAndCertifications(), "Achievements & Certifications"); });
            VBox c7 = Ui.card("My Applications", String.valueOf(career.applicationsByStudent(sid).size()), "card-cream",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.applications(), "Applications"); });
            VBox c8 = Ui.card("Open Opportunities", String.valueOf(career.openOpportunities().size()), "card-mint",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.opportunities(), "Opportunities"); });

            for (VBox c : List.of(c1, c2, c3, c4, c5, c6, c7, c8)) {
                HBox.setHgrow(c, Priority.ALWAYS);
                c.setMaxWidth(Double.MAX_VALUE);
            }

            cards1.getChildren().addAll(c1, c2, c3, c4);
            cards2.getChildren().addAll(c5, c6, c7, c8);

            // 2. Update Dual Charts & Academic Standing
            chartsRow.getChildren().clear();
            Node gradeChart = DashboardCharts.gradeTrend(grades,
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.academic(), "Academic"); });
            Node attendanceChart = DashboardCharts.attendanceSummary(attendance,
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.academic(), "Academic"); });
            HBox.setHgrow(gradeChart, Priority.ALWAYS);
            HBox.setHgrow(attendanceChart, Priority.ALWAYS);
            chartsRow.getChildren().addAll(gradeChart, attendanceChart);

            standingHolder.getChildren().clear();
            standingHolder.getChildren().add(createStandingCard(student));

            // 3. Update Student Hub Card
            hubHolder.getChildren().clear();
            hubHolder.getChildren().add(createHubCard(sid, () -> refreshHolder[0].run()));

            // 4. Update Grades Table
            String q = searchGrades.getText() == null ? "" : searchGrades.getText().toLowerCase(Locale.ROOT);
            List<Grade> filtered = grades.stream().filter(g -> {
                Enrollment e = enrollmentMap.get(g.getEnrollmentId());
                String subName = e != null ? subjects.getOrDefault(e.getSubjectId(), "").toLowerCase(Locale.ROOT) : "";
                String actName = String.valueOf(g.getActivityName()).toLowerCase(Locale.ROOT);
                return q.isBlank() || subName.contains(q) || actName.contains(q);
            }).toList();
            gradeTable.setItems(FXCollections.observableArrayList(filtered));
        };

        refreshHolder[0] = refreshDashboard;
        searchGrades.textProperty().addListener((obs, old, val) -> refreshDashboard.run());
        refreshDashboard.run();

        VBox scrollContent = new VBox(20);
        scrollContent.setFillWidth(true);
        scrollContent.setStyle("-fx-background-color: #FDFBFA;");
        scrollContent.setPadding(new Insets(10, 24, 32, 24));
        scrollContent.getChildren().addAll(cards1, cards2, middleRow, lowerRow);

        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("content-scroll");

        // Forward mouse scroll events on the sticky header to the scrollPane
        headerContainer.addEventHandler(ScrollEvent.SCROLL, event -> {
            scrollPane.fireEvent(event.copyFor(scrollPane, scrollPane));
            event.consume();
        });

        BorderPane dashboardRoot = new BorderPane();
        dashboardRoot.getProperties().put("self-scrolling", Boolean.TRUE);
        dashboardRoot.setStyle("-fx-background-color: #FDFBFA;");
        dashboardRoot.setTop(headerContainer);
        dashboardRoot.setCenter(scrollPane);

        return dashboardRoot;
    }

    private static VBox createStandingCard(Student student) {
        VBox standingTray = new VBox(12);
        standingTray.getStyleClass().add("clay-tray");
        standingTray.setStyle("-fx-padding: 16;");

        Label standingTitle = new Label("Academic Standing");
        standingTitle.getStyleClass().add("tray-title");
        Label standingSub = new Label("Program placement & progress");
        standingSub.getStyleClass().add("tray-subtitle");

        VBox standingItems = new VBox(8);
        String sNum = student != null && student.getStudentNumber() != null ? student.getStudentNumber() : "2023-0001";
        String sYear = student != null ? Ui.formatYearDisplay(student.getYearLevel()) + " • Section " + (student.getSection() != null ? student.getSection() : "A") : "Year 3 • Section A";
        String sStatus = student != null && student.getEnrollmentStatus() != null ? student.getEnrollmentStatus() : "Enrolled";

        standingItems.getChildren().addAll(
                Ui.propertyTile("Student Number", sNum),
                Ui.propertyTile("Level & Section", sYear),
                Ui.propertyTile("Enrollment Status", Ui.badge(sStatus, "badge-pill-mint"))
        );

        standingTray.getChildren().addAll(standingTitle, standingSub, standingItems);
        Ui.makeInteractive(standingTray, () -> {
            if (AppController.getInstance() != null) AppController.getInstance().navigateTo(StudentViews.academic(), "Academic");
        });
        return standingTray;
    }

    private static VBox createHubCard(int studentId, Runnable refresh) {
        VBox actionsTray = new VBox(12);
        actionsTray.getStyleClass().add("clay-tray");
        actionsTray.setStyle("-fx-padding: 16;");

        Label actionsTitle = new Label("Student Hub");
        actionsTitle.getStyleClass().add("tray-title");
        Label actionsSub = new Label("Quick actions & career portfolio");
        actionsSub.getStyleClass().add("tray-subtitle");

        Button myProfileBtn = new Button("View Profile");
        myProfileBtn.setMaxWidth(Double.MAX_VALUE);
        myProfileBtn.getStyleClass().add("primary-button");
        myProfileBtn.setOnAction(e -> {
            if (AppController.getInstance() != null) {
                AppController.getInstance().navigateTo(StudentViews.profile(), "Profile");
            }
        });

        Button addProjectBtn = new Button("Showcase Project");
        addProjectBtn.setMaxWidth(Double.MAX_VALUE);
        addProjectBtn.getStyleClass().add("secondary-button");
        addProjectBtn.setOnAction(e -> editProject(studentId, null, p -> refresh.run()));

        Button portfolioBtn = new Button("Career Portfolio");
        portfolioBtn.setMaxWidth(Double.MAX_VALUE);
        portfolioBtn.getStyleClass().add("secondary-button");
        portfolioBtn.setOnAction(e -> {
            if (AppController.getInstance() != null) {
                AppController.getInstance().navigateTo(StudentViews.portfolio(), "Portfolio");
            }
        });

        Button refreshBtn = new Button("Refresh Dashboard");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> {
            refresh.run();
            Alerts.info("Student dashboard refreshed.");
        });

        actionsTray.getChildren().addAll(actionsTitle, actionsSub, myProfileBtn, addProjectBtn, portfolioBtn, refreshBtn);
        return actionsTray;
    }

    /** Small facade so the dashboard does not need to import calculation details. */
    private static final class GradeCalculatorFacade {
        static double average(List<Grade> grades) { return com.nextit.util.GradeCalculator.average(grades); }
        static double attendance(List<Attendance> records) { return com.nextit.util.GradeCalculator.attendanceRate(records); }
    }

    public static Node academic() {
        int sid = studentId();
        Map<Integer, String> subjects = directory.subjectNames();
        Map<Integer, String> instructors = directory.instructorNames();
        Map<Integer, String> enrollments = new java.util.HashMap<>();
        for (Enrollment e : academic.enrollmentsForStudent(sid)) {
            enrollments.put(e.getEnrollmentId(), subjects.getOrDefault(e.getSubjectId(), "Subject #" + e.getSubjectId()));
        }

        Map<Integer, Subject> subjectMap = directory.subjectMap();
        TableView<Enrollment> subjectTable = Ui.table("No subjects available.");
        Ui.addColumn(subjectTable, "Subject Code", e -> {
            Subject s = subjectMap.get(e.getSubjectId());
            return s != null ? s.getSubjectCode() : "SUBJ-" + e.getSubjectId();
        });
        Ui.addColumn(subjectTable, "Subject Title", e -> {
            Subject s = subjectMap.get(e.getSubjectId());
            return s != null ? s.getSubjectName() : "Subject #" + e.getSubjectId();
        });
        Ui.addColumn(subjectTable, "Assigned Instructor / Faculty", e -> {
            Integer instId = e.getInstructorId();
            if (instId == null) {
                Subject s = subjectMap.get(e.getSubjectId());
                if (s != null) instId = s.getInstructorId();
            }
            return instId != null ? instructors.getOrDefault(instId, "Prof. #" + instId) : "Unassigned";
        });
        Ui.addColumn(subjectTable, "Section", e -> {
            Subject s = subjectMap.get(e.getSubjectId());
            return (s != null && s.getSection() != null) ? s.getSection() : "A";
        });
        Ui.addColumn(subjectTable, "Semester", Enrollment::getSemester);
        Ui.addColumn(subjectTable, "Units", e -> {
            Subject s = subjectMap.get(e.getSubjectId());
            return s != null ? String.valueOf(s.getUnits()) : "3.0";
        });
        Ui.addColumn(subjectTable, "Status / Grade", e -> {
            if (e.getGrade() != null && !e.getGrade().isBlank()) {
                return e.getStatus() + " (" + e.getGrade() + ")";
            }
            return e.getStatus() != null ? e.getStatus() : "Enrolled";
        });

        TextField subjectSearch = Ui.field("Search subjects by code, title, instructor or section...");
        Runnable refreshSubjectTable = () -> {
            List<Enrollment> list = academic.enrollmentsForStudent(sid);
            subjectMap.clear();
            subjectMap.putAll(directory.subjectMap());
            instructors.clear();
            instructors.putAll(directory.instructorNames());
            subjects.clear();
            subjects.putAll(directory.subjectNames());
            enrollments.clear();
            for (Enrollment e : list) {
                enrollments.put(e.getEnrollmentId(), subjects.getOrDefault(e.getSubjectId(), "Subject #" + e.getSubjectId()));
            }
            String q = subjectSearch.getText() == null ? "" : subjectSearch.getText().toLowerCase(Locale.ROOT).trim();
            subjectTable.setItems(FXCollections.observableArrayList(list.stream().filter(e -> {
                if (q.isBlank()) return true;
                Subject s = subjectMap.get(e.getSubjectId());
                String code = s != null ? String.valueOf(s.getSubjectCode()).toLowerCase(Locale.ROOT) : "";
                String title = s != null ? String.valueOf(s.getSubjectName()).toLowerCase(Locale.ROOT) : "";
                Integer instId = e.getInstructorId() != null ? e.getInstructorId() : (s != null ? s.getInstructorId() : null);
                String instName = instId != null ? instructors.getOrDefault(instId, "").toLowerCase(Locale.ROOT) : "";
                String sec = (s != null && s.getSection() != null) ? s.getSection().toLowerCase(Locale.ROOT) : "";
                String sem = e.getSemester() != null ? e.getSemester().toLowerCase(Locale.ROOT) : "";
                return code.contains(q) || title.contains(q) || instName.contains(q) || sec.contains(q) || sem.contains(q);
            }).toList()));
            subjectTable.refresh();
        };
        subjectSearch.textProperty().addListener((obs, old, val) -> refreshSubjectTable.run());
        refreshSubjectTable.run();

        Button enrollBtn = Ui.button("➕ Enroll / Add Subject", "primary-button");
        enrollBtn.setOnAction(ev -> showEnrollSubjectDialog(sid, () -> {
            subjectSearch.clear();
            refreshSubjectTable.run();
        }, enrollBtn.getScene() != null ? enrollBtn.getScene().getWindow() : null));

        Button refreshSubjectBtn = new Button("Refresh");
        refreshSubjectBtn.setOnAction(ev -> refreshSubjectTable.run());

        Button exportSubjectCsv = Ui.button("📥 Export CSV", "clay-btn-secondary");
        exportSubjectCsv.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToCsv(subjectTable, "enrolled_subjects.csv", "Enrolled Subjects", exportSubjectCsv.getScene().getWindow()));
        Button exportSubjectReport = Ui.button("📄 Print / PDF", "clay-btn-secondary");
        exportSubjectReport.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToHtmlReport(subjectTable, "enrolled_subjects.html", "Enrolled Subjects", exportSubjectReport.getScene().getWindow()));

        Region subjectSpacer = new Region();
        HBox.setHgrow(subjectSpacer, Priority.ALWAYS);
        HBox subjectToolbar = new HBox(8, new Label("Search:"), subjectSearch, enrollBtn, refreshSubjectBtn, subjectSpacer, exportSubjectCsv, exportSubjectReport);
        subjectToolbar.setAlignment(Pos.CENTER_LEFT);
        subjectToolbar.setStyle("-fx-padding: 0 0 10 0;");
        VBox subjectTabContent = new VBox(8, subjectToolbar, subjectTable);
        VBox.setVgrow(subjectTable, Priority.ALWAYS);
        subjectTabContent.setStyle("-fx-padding: 12 0 0 0;");
        Tab subjectTab = new Tab("Subjects", subjectTabContent);
        subjectTab.setClosable(false);

        TableView<Grade> gradeTable = Ui.table();
        Ui.addColumn(gradeTable, "Subject", g -> enrollments.getOrDefault(g.getEnrollmentId(), "Enrollment #" + g.getEnrollmentId()));
        Ui.addColumn(gradeTable, "Activity", Grade::getActivityName);
        Ui.addColumn(gradeTable, "Type", Grade::getActivityType);
        Ui.addColumn(gradeTable, "Score", g -> g.getScore() + " / " + g.getMaxScore());
        Ui.addColumn(gradeTable, "Grade", g -> g.getGrade() == null ? "" : String.format("%.2f%%", g.getGrade()));
        Ui.addColumn(gradeTable, "Remarks", Grade::getRemarks);
        Ui.addColumn(gradeTable, "Recorded", g -> g.getRecordedAt() == null ? "" : g.getRecordedAt().toLocalDate().toString());
        gradeTable.setItems(FXCollections.observableArrayList(academic.gradesForStudent(sid)));

        TableView<Attendance> attendanceTable = Ui.table();
        Ui.addColumn(attendanceTable, "Subject", a -> enrollments.getOrDefault(a.getEnrollmentId(), "Enrollment #" + a.getEnrollmentId()));
        Ui.addColumn(attendanceTable, "Date", a -> Ui.formatDate(a.getAttendanceDate()));
        Ui.addColumn(attendanceTable, "Status", Attendance::getStatus);
        Ui.addColumn(attendanceTable, "Remarks", Attendance::getRemarks);
        Ui.addColumn(attendanceTable, "Recorded By", a -> instructors.getOrDefault(a.getRecordedBy(), "Instructor #" + a.getRecordedBy()));
        attendanceTable.setItems(FXCollections.observableArrayList(academic.attendanceForStudent(sid)));

        Map<Integer, String> supportSubjects = subjects;
        TableView<AcademicSupport> supportTable = Ui.table();
        Ui.addColumn(supportTable, "Type", AcademicSupport::getSupportType);
        Ui.addColumn(supportTable, "Subject", s -> s.getSubjectId() == null ? "" : supportSubjects.getOrDefault(s.getSubjectId(), "Subject #" + s.getSubjectId()));
        Ui.addColumn(supportTable, "Date", s -> Ui.formatDate(s.getSupportDate()));
        Ui.addColumn(supportTable, "Reason", AcademicSupport::getReason);
        Ui.addColumn(supportTable, "Action Taken", AcademicSupport::getActionTaken);
        Ui.addColumn(supportTable, "Outcome", AcademicSupport::getOutcome);
        Ui.addColumn(supportTable, "Instructor", s -> instructors.getOrDefault(s.getInstructorId(), "Instructor #" + s.getInstructorId()));
        supportTable.setItems(FXCollections.observableArrayList(support.forStudent(sid)));

        Map<Integer, String> projectTitles = projects.allProjects().stream()
                .collect(java.util.stream.Collectors.toMap(Project::getProjectId, Project::getProjectTitle, (a, b) -> a));
        TableView<ProjectEvaluation> projectTable = Ui.table();
        Ui.addColumn(projectTable, "Project", e -> projectTitles.getOrDefault(e.getProjectId(), "Project #" + e.getProjectId()));
        Ui.addColumn(projectTable, "Contribution", e -> score(e.getContributionScore()));
        Ui.addColumn(projectTable, "Technical", e -> score(e.getTechnicalScore()));
        Ui.addColumn(projectTable, "Teamwork", e -> score(e.getTeamworkScore()));
        Ui.addColumn(projectTable, "Overall", e -> score(e.getOverallScore()));
        Ui.addColumn(projectTable, "Feedback", ProjectEvaluation::getFeedback);
        Ui.addColumn(projectTable, "Evaluated", e -> e.getEvaluatedAt() == null ? "" : e.getEvaluatedAt().toLocalDate().toString());
        projectTable.setItems(FXCollections.observableArrayList(projects.evaluationsForStudent(sid)));

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                subjectTab,
                tab("Grades", gradeTable),
                tab("Attendance", attendanceTable),
                tab("Project Performance", projectTable),
                tab("Academic Support", supportTable));
        return Ui.page("Academic Progress", "Review your subjects, grades, attendance, project performance and instructor remarks. These records are maintained by your instructors.", tabs);
    }

    private static String normalizeSubjectCode(String code) {
        if (code == null) return "";
        return code.replaceAll("[\\s\\-_]+", "").toLowerCase(Locale.ROOT);
    }

    private static String formatSubjectCatalogItem(Subject s, Map<Integer, String> instNames) {
        if (s == null) return "";
        String code = s.getSubjectCode() != null ? s.getSubjectCode().trim() : "N/A";
        String name = s.getSubjectName() != null ? s.getSubjectName().trim() : "Untitled";
        String sec = (s.getSection() != null && !s.getSection().isBlank()) ? "Sec " + s.getSection().trim() : "Sec A";
        String sem = (s.getSemester() != null && !s.getSemester().isBlank()) ? s.getSemester().trim() : "1st Semester";
        String ay = (s.getAcademicYear() != null && !s.getAcademicYear().isBlank()) ? s.getAcademicYear().trim() : "2025-2026";
        String inst = "Unassigned";
        if (s.getInstructorId() != null) {
            String rawName = instNames.get(s.getInstructorId());
            if (rawName != null && !rawName.isBlank()) {
                inst = rawName.replaceAll("\\s*\\(.*?\\)", "").trim();
            } else {
                inst = "Instructor #" + s.getInstructorId();
            }
        }
        return String.format("%s — %s (%s • %s, %s • %s)", code, name, sec, sem, ay, inst);
    }

    private static void showEnrollSubjectDialog(int studentId, Runnable onComplete, Window ownerWindow) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
        }
        dialog.setTitle("Enroll / Add Subject");
        dialog.setHeaderText(null);
        if (StudentViews.class.getResource("/com/nextit/css/application.css") != null) {
            dialog.getDialogPane().getStylesheets().add(StudentViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
        }
        dialog.getDialogPane().setStyle("-fx-background-color: #FAF7F2;");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(12);
        content.setPrefWidth(560);
        content.setPadding(new Insets(10));

        VBox headerBox = new VBox(6);
        Label titleLabel = new Label("Course & Subject Enrollment");
        titleLabel.setStyle("-fx-font-size: 16.5px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");

        Label infoLabel = new Label("Select a subject from the academic catalog to enroll for this term. A student can enroll in multiple subjects under different faculty members simultaneously.");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(Double.MAX_VALUE);
        infoLabel.setPrefWidth(530);
        infoLabel.setMinHeight(Region.USE_PREF_SIZE);
        infoLabel.setTextOverrun(OverrunStyle.CLIP);
        infoLabel.setStyle("-fx-text-fill: #6E6862; -fx-font-size: 12.5px; -fx-line-spacing: 2px;");
        headerBox.getChildren().addAll(titleLabel, infoLabel);

        // 1. Fetch active enrollments for the student (excluding dropped)
        List<Enrollment> activeEnrollments = academic.enrollmentsForStudent(studentId).stream()
                .filter(e -> !"Dropped".equalsIgnoreCase(e.getStatus()))
                .toList();

        // 2. Fetch all subjects from the catalog (same table where instructors create subjects)
        List<Subject> allSubjects = new com.nextit.repository.JdbcSubjectRepository().findAll();

        // 3. Filter out only subjects the student is already actively enrolled in for that specific term/offering
        List<Subject> unenrolled = allSubjects.stream()
                .filter(s -> activeEnrollments.stream().noneMatch(e ->
                        e.getSubjectId() == s.getSubjectId()
                        && (s.getAcademicYear() == null || s.getAcademicYear().isBlank() || s.getAcademicYear().equalsIgnoreCase(e.getAcademicYear()))
                        && (s.getSemester() == null || s.getSemester().isBlank() || s.getSemester().equalsIgnoreCase(e.getSemester()))
                ))
                .toList();

        // Fallback: if all matched because of term/subject overlap, but subjects exist, allow any subject not enrolled in that exact term
        final List<Subject> availableSubjects = (unenrolled.isEmpty() && !allSubjects.isEmpty()) ? allSubjects : unenrolled;

        Map<Integer, String> instNames = directory.instructorNames();
        ComboBox<Subject> catalogPicker = Ui.combo(availableSubjects, s -> formatSubjectCatalogItem(s, instNames));
        catalogPicker.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Subject item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatSubjectCatalogItem(item, instNames));
            }
        });
        catalogPicker.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Subject item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(availableSubjects.isEmpty() ? "No unenrolled subjects available in catalog" : "Select an available subject from catalog...");
                } else {
                    setText(formatSubjectCatalogItem(item, instNames));
                }
            }
        });
        catalogPicker.setMaxWidth(Double.MAX_VALUE);

        TextField codeField = Ui.field("Or enter subject code directly (e.g. IT301, IT-311, PHYS 111)");
        TextField termField = Ui.field("Academic Year (e.g. 2025-2026)");
        ComboBox<String> semCombo = Ui.combo(List.of("1st Semester", "2nd Semester", "Summer Term"), s -> s);

        // Automatically sync Subject Code, Academic Year, and Semester when dropdown selection changes
        catalogPicker.valueProperty().addListener((obs, oldSub, newSub) -> {
            if (newSub != null) {
                codeField.setText(newSub.getSubjectCode() != null ? newSub.getSubjectCode().trim() : "");
                if (newSub.getAcademicYear() != null && !newSub.getAcademicYear().isBlank()) {
                    termField.setText(newSub.getAcademicYear().trim());
                }
                if (newSub.getSemester() != null && !newSub.getSemester().isBlank()) {
                    semCombo.setValue(newSub.getSemester().trim());
                }
            }
        });

        if (!availableSubjects.isEmpty()) {
            catalogPicker.getSelectionModel().selectFirst();
        } else {
            java.time.LocalDate today = java.time.LocalDate.now();
            int startYear = today.getMonthValue() >= 6 ? today.getYear() : today.getYear() - 1;
            termField.setText(startYear + "-" + (startYear + 1));
            semCombo.getSelectionModel().selectFirst();
        }

        content.getChildren().addAll(
                headerBox,
                new Label("Available Subject Catalog:"), catalogPicker,
                new Label("Manual Subject Code (Optional Override):"), codeField,
                new Label("Academic Year:"), termField,
                new Label("Semester:"), semCombo
        );

        dialog.getDialogPane().setContent(content);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Enroll Now");
        okButton.getStyleClass().add("primary-button");

        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            String manualCode = codeField.getText() != null ? codeField.getText().trim() : "";
            Subject chosen = null;

            if (!manualCode.isBlank()) {
                String normInput = normalizeSubjectCode(manualCode);
                List<Subject> allDbSubjects = new com.nextit.repository.JdbcSubjectRepository().findAll();

                // 1. Match by normalized subject code (ignoring spaces, dashes, case)
                List<Subject> matches = allDbSubjects.stream()
                        .filter(s -> normalizeSubjectCode(s.getSubjectCode()).equals(normInput))
                        .toList();

                // 2. Fallback: contains or matches subject name
                if (matches.isEmpty()) {
                    matches = allDbSubjects.stream()
                            .filter(s -> normalizeSubjectCode(s.getSubjectCode()).contains(normInput)
                                    || (s.getSubjectName() != null && normalizeSubjectCode(s.getSubjectName()).contains(normInput)))
                            .toList();
                }

                if (matches.isEmpty()) {
                    Alerts.error("No subject found matching code: " + manualCode);
                    return;
                }

                // 3. Resolve which offering if multiple sections/terms exist
                if (matches.size() == 1) {
                    chosen = matches.get(0);
                } else {
                    String currentAy = termField.getText() != null ? termField.getText().trim() : "";
                    String currentSem = semCombo.getValue() != null ? semCombo.getValue().trim() : "";

                    chosen = matches.stream()
                            .filter(s -> (currentAy.isBlank() || currentAy.equalsIgnoreCase(s.getAcademicYear()))
                                    && (currentSem.isBlank() || currentSem.equalsIgnoreCase(s.getSemester())))
                            .findFirst()
                            .orElse(null);

                    if (chosen == null) {
                        chosen = matches.stream()
                                .filter(s -> (!currentSem.isBlank() && currentSem.equalsIgnoreCase(s.getSemester()))
                                        || (!currentAy.isBlank() && currentAy.equalsIgnoreCase(s.getAcademicYear())))
                                .findFirst()
                                .orElse(matches.get(0));
                    }
                }

                // Sync dialog fields with matched offering
                if (chosen != null) {
                    catalogPicker.setValue(chosen);
                    if (chosen.getAcademicYear() != null && !chosen.getAcademicYear().isBlank()) {
                        termField.setText(chosen.getAcademicYear().trim());
                    }
                    if (chosen.getSemester() != null && !chosen.getSemester().isBlank()) {
                        semCombo.setValue(chosen.getSemester().trim());
                    }
                }
            } else {
                chosen = catalogPicker.getValue();
            }

            if (chosen == null) {
                Alerts.error("Please select a subject from the catalog or enter a valid subject code.");
                return;
            }

            final Subject toEnroll = chosen;
            String ay = (termField.getText() != null && !termField.getText().isBlank()) ? termField.getText().trim()
                    : (toEnroll.getAcademicYear() != null && !toEnroll.getAcademicYear().isBlank() ? toEnroll.getAcademicYear().trim() : "2025-2026");
            String sem = (semCombo.getValue() != null && !semCombo.getValue().isBlank()) ? semCombo.getValue().trim()
                    : (toEnroll.getSemester() != null && !toEnroll.getSemester().isBlank() ? toEnroll.getSemester().trim() : "1st Semester");

            okButton.setDisable(true);
            CompletableFuture.runAsync(() -> {
                academic.enrollStudentInSubject(studentId, toEnroll.getSubjectId(), ay, sem, toEnroll.getInstructorId());
            }).thenRun(() -> Platform.runLater(() -> {
                dialog.setResult(ButtonType.OK);
                dialog.close();

                // 1. Immediately refresh the table so the newly enrolled row renders without delay
                if (onComplete != null) {
                    onComplete.run();
                }

                // 2. Launch the styled NextIT confirmation modal centered over the window
                Platform.runLater(() -> {
                    String instName = toEnroll.getInstructorId() != null
                            ? directory.instructorNames().getOrDefault(toEnroll.getInstructorId(), "Department Faculty")
                            : "Department Faculty";
                    Window effectiveOwner = ownerWindow;
                    if (effectiveOwner == null) {
                        effectiveOwner = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null);
                    }
                    showEnrollmentConfirmationModal(effectiveOwner, toEnroll, ay, sem, instName);
                });
            })).exceptionally(ex -> {
                Platform.runLater(() -> {
                    okButton.setDisable(false);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    Alerts.error(cause.getMessage() != null ? cause.getMessage() : "Failed to enroll in subject.");
                });
                return null;
            });
        });

        dialog.showAndWait();
    }

    private static void showEnrollmentConfirmationModal(Window ownerWindow, Subject subject, String academicYear, String semester, String facultyName) {
        Stage modalStage = new Stage();
        modalStage.initModality(Modality.APPLICATION_MODAL);
        if (ownerWindow != null) {
            modalStage.initOwner(ownerWindow);
        }
        modalStage.initStyle(StageStyle.TRANSPARENT);

        StackPane rootPane = new StackPane();
        rootPane.setStyle("-fx-background-color: transparent; -fx-padding: 30;");

        VBox card = new VBox(16);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(460);
        card.setMaxWidth(460);
        card.setStyle(
                "-fx-background-color: #FAF7F2; " +
                "-fx-background-radius: 16; " +
                "-fx-border-color: #E2D9CC; " +
                "-fx-border-radius: 16; " +
                "-fx-border-width: 1.5; " +
                "-fx-padding: 26 28 24 28; " +
                "-fx-effect: dropshadow(gaussian, rgba(26, 38, 52, 0.25), 24, 0.15, 0, 8);"
        );

        // 1. Success Checkmark Icon (Green checkmark in circular badge)
        StackPane iconCircle = new StackPane();
        iconCircle.setStyle(
                "-fx-background-color: #DCFCE7; " +
                "-fx-background-radius: 50; " +
                "-fx-min-width: 58; -fx-min-height: 58; " +
                "-fx-max-width: 58; -fx-max-height: 58; " +
                "-fx-border-color: #86EFAC; " +
                "-fx-border-radius: 50; " +
                "-fx-border-width: 1.5;"
        );
        Label checkIcon = new Label("✔");
        checkIcon.setStyle("-fx-font-size: 28px; -fx-font-weight: 900; -fx-text-fill: #15803D;");
        iconCircle.getChildren().add(checkIcon);

        // 2. Heading & Subtitle
        Label heading = new Label("Enrollment Confirmed");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");

        Label subHeading = new Label("You have been successfully registered for this subject.");
        subHeading.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #5A6A7E; -fx-font-weight: 500;");

        // 3. Subject Metadata Card
        VBox detailsCard = new VBox(10);
        detailsCard.setStyle(
                "-fx-background-color: #FFFFFF; " +
                "-fx-border-color: #E2D9CC; " +
                "-fx-border-radius: 12; " +
                "-fx-background-radius: 12; " +
                "-fx-padding: 14 16;"
        );

        HBox codeRow = new HBox(10);
        codeRow.setAlignment(Pos.CENTER_LEFT);
        Label codeBadge = new Label(subject.getSubjectCode());
        codeBadge.setStyle(
                "-fx-background-color: #991B1B; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-weight: 800; " +
                "-fx-font-size: 12px; " +
                "-fx-padding: 3 10; " +
                "-fx-background-radius: 6;"
        );

        Label titleLbl = new Label(subject.getSubjectName());
        titleLbl.setStyle("-fx-font-size: 14.5px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");
        titleLbl.setWrapText(true);
        HBox.setHgrow(titleLbl, Priority.ALWAYS);
        codeRow.getChildren().addAll(codeBadge, titleLbl);

        Separator sep = new Separator();
        sep.setStyle("-fx-opacity: 0.5;");

        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(16);
        metaGrid.setVgap(8);

        Label semLabel = new Label("Semester:");
        semLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label semVal = new Label(semester + (academicYear != null && !academicYear.isBlank() ? " (" + academicYear + ")" : ""));
        semVal.setStyle("-fx-font-size: 12px; -fx-text-fill: #1A2634; -fx-font-weight: 700;");

        Label unitsLabel = new Label("Units:");
        unitsLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label unitsVal = new Label(String.format("%.1f Units", subject.getUnits()));
        unitsVal.setStyle("-fx-font-size: 12px; -fx-text-fill: #1A2634; -fx-font-weight: 700;");

        Label instLabel = new Label("Faculty:");
        instLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label instVal = new Label(facultyName != null && !facultyName.isBlank() ? facultyName : "Department Faculty");
        instVal.setStyle("-fx-font-size: 12px; -fx-text-fill: #1A2634; -fx-font-weight: 700;");

        Label statusLabel = new Label("Status:");
        statusLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label statusVal = new Label("Enrolled (Active)");
        statusVal.setStyle("-fx-font-size: 12px; -fx-text-fill: #15803D; -fx-font-weight: 800;");

        metaGrid.add(semLabel, 0, 0);
        metaGrid.add(semVal, 1, 0);
        metaGrid.add(unitsLabel, 0, 1);
        metaGrid.add(unitsVal, 1, 1);
        metaGrid.add(instLabel, 0, 2);
        metaGrid.add(instVal, 1, 2);
        metaGrid.add(statusLabel, 0, 3);
        metaGrid.add(statusVal, 1, 3);

        detailsCard.getChildren().addAll(codeRow, sep, metaGrid);

        // 4. Prominent "Done" Button
        Button doneBtn = new Button("Done");
        doneBtn.setStyle(
                "-fx-background-color: #991B1B; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 13.5px; " +
                "-fx-font-weight: 800; " +
                "-fx-padding: 10 36; " +
                "-fx-background-radius: 8; " +
                "-fx-cursor: hand;"
        );
        doneBtn.setMaxWidth(Double.MAX_VALUE);
        doneBtn.setOnAction(e -> modalStage.close());

        card.getChildren().addAll(iconCircle, heading, subHeading, detailsCard, doneBtn);
        rootPane.getChildren().add(card);

        Scene modalScene = new Scene(rootPane);
        modalScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        if (StudentViews.class.getResource("/com/nextit/css/application.css") != null) {
            modalScene.getStylesheets().add(StudentViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
        }
        modalScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE || ev.getCode() == javafx.scene.input.KeyCode.ENTER) {
                modalStage.close();
                ev.consume();
            }
        });

        modalStage.setScene(modalScene);

        if (ownerWindow != null) {
            modalStage.setOnShown(ev -> {
                double x = ownerWindow.getX() + (ownerWindow.getWidth() - modalStage.getWidth()) / 2.0;
                double y = ownerWindow.getY() + (ownerWindow.getHeight() - modalStage.getHeight()) / 2.0;
                modalStage.setX(Math.max(0, x));
                modalStage.setY(Math.max(0, y));
            });
        } else {
            modalStage.centerOnScreen();
        }

        modalStage.show();
    }

    private static String score(Double value) {
        return value == null ? "" : String.format("%.2f", value);
    }

    private static Tab tab(String title, Node content) {
        if (content instanceof TableView<?> table) {
            Button exportCsv = Ui.button("📥 Export CSV", "clay-btn-secondary");
            exportCsv.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToCsv(table, title.toLowerCase(Locale.ROOT).replace(" ", "_") + ".csv", title, exportCsv.getScene().getWindow()));

            Button exportReport = Ui.button("📄 Print / PDF", "clay-btn-secondary");
            exportReport.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToHtmlReport(table, title.toLowerCase(Locale.ROOT).replace(" ", "_") + ".html", title, exportReport.getScene().getWindow()));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox toolbar = new HBox(8, spacer, exportCsv, exportReport);
            toolbar.setAlignment(Pos.CENTER_RIGHT);
            toolbar.setStyle("-fx-padding: 0 0 10 0;");

            VBox box = new VBox(8, toolbar, table);
            VBox.setVgrow(table, Priority.ALWAYS);
            box.setStyle("-fx-padding: 12 0 0 0;");

            Tab tab = new Tab(title, box);
            tab.setClosable(false);
            return tab;
        }
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    public static Node skills() {
        int sid = studentId();
        Map<Integer, TechnicalSkill> skillMap = directory.skillMap();
        Map<Integer, String> instructors = directory.instructorNames();
        TableView<StudentSkill> table = Ui.table("No technical skills added yet.");
        Ui.addColumn(table, "Technical Skill", s -> {
            TechnicalSkill ts = skillMap.get(s.getSkillId());
            return ts != null ? ts.getSkillName() : "Skill #" + s.getSkillId();
        });
        Ui.addColumn(table, "Category", s -> {
            TechnicalSkill ts = skillMap.get(s.getSkillId());
            return ts != null && ts.getCategory() != null ? ts.getCategory() : "General";
        });
        Ui.addColumn(table, "Proficiency", StudentSkill::getProficiencyLevel);
        Ui.addColumn(table, "Assessment Score", s -> s.getAssessmentScore() == null ? "Pending" : String.format("%.2f%%", s.getAssessmentScore()));
        Ui.addColumn(table, "Assessed By", s -> s.getAssessedBy() == null ? "Self-Reported" : instructors.getOrDefault(s.getAssessedBy(), "Instructor #" + s.getAssessedBy()));
        Ui.addColumn(table, "Assessment Date", s -> s.getAssessedAt() == null ? "--" : s.getAssessedAt().toLocalDate().toString());
        Ui.addColumn(table, "Instructor Remarks", s -> s.getRemarks() == null || s.getRemarks().isBlank() ? "--" : s.getRemarks());

        List<StudentSkill> all = skillService.forStudent(sid);
        table.setItems(FXCollections.observableArrayList(all));
        TextField search = Ui.field("Search by skill, category, proficiency or remarks...");
        Runnable filter = () -> {
            skillMap.clear();
            skillMap.putAll(directory.skillMap());
            instructors.clear();
            instructors.putAll(directory.instructorNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(skillService.forStudent(sid).stream().filter(s -> {
                TechnicalSkill ts = skillMap.get(s.getSkillId());
                String skillName = ts != null ? ts.getSkillName() : "";
                String category = ts != null && ts.getCategory() != null ? ts.getCategory() : "";
                return skillName.toLowerCase(Locale.ROOT).contains(q)
                        || category.toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(s.getProficiencyLevel()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(s.getRemarks()).toLowerCase(Locale.ROOT).contains(q);
            }).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> filter.run());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> filter.run());
        return Ui.page("Technical Skills", "Skills assessed by your instructors, including proficiency, competency categories, and assessment scores.",
                Ui.toolbar(new Label("Search:"), search, refresh), table);
    }

    public static Node projects() {
        int sid = studentId();
        Map<Integer, String> subjects = new ConcurrentHashMap<>();
        Map<Integer, List<ProjectMember>> membersCache = new ConcurrentHashMap<>();
        List<Project> allProjects = new ArrayList<>();

        TableView<Project> table = Ui.table();
        Ui.addColumn(table, "Title", Project::getProjectTitle);
        Ui.addColumn(table, "Type", Project::getProjectType);
        Ui.addColumn(table, "Subject", p -> p.getSubjectId() == null ? "" : subjects.getOrDefault(p.getSubjectId(), "Subject #" + p.getSubjectId()));
        Ui.addColumn(table, "Status", Project::getStatus);
        Ui.addColumn(table, "Credit / Role", p -> {
            if (projects.isOwner(p, sid)) {
                List<ProjectMember> mems = membersCache.getOrDefault(p.getProjectId(), List.of());
                return mems.isEmpty() ? "Owner (Solo)" : "Owner (Project Lead)";
            }
            List<ProjectMember> mems = membersCache.getOrDefault(p.getProjectId(), List.of());
            String role = mems.stream()
                    .filter(m -> m.getStudentId() == sid)
                    .map(ProjectMember::getRole)
                    .filter(r -> r != null && !r.isBlank())
                    .findFirst()
                    .orElse(null);
            return role != null ? "Group Project - Credited (" + role + ")" : "Group Project - Credited";
        });
        Ui.addColumn(table, "Members", p -> {
            List<ProjectMember> mems = membersCache.getOrDefault(p.getProjectId(), List.of());
            if (mems.isEmpty()) {
                return "1 (Solo)";
            }
            return (mems.size() + 1) + " members (" + mems.size() + " credited)";
        });
        Ui.addColumn(table, "Technologies", Project::getTechnologiesUsed);
        Ui.addColumn(table, "Repository", Project::getRepositoryUrl);
        Ui.addColumn(table, "Demo", Project::getDemoUrl);

        TextField search = Ui.field("Search by title, type, technology, member or status...");
        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            List<Project> filtered = allProjects.stream().filter(p -> {
                if (q.isBlank()) return true;
                boolean matchMain = String.valueOf(p.getProjectTitle()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(p.getProjectType()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(p.getTechnologiesUsed()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(p.getStatus()).toLowerCase(Locale.ROOT).contains(q)
                        || (p.getSubjectId() != null && subjects.getOrDefault(p.getSubjectId(), "").toLowerCase(Locale.ROOT).contains(q));
                if (matchMain) return true;
                List<ProjectMember> mems = membersCache.getOrDefault(p.getProjectId(), List.of());
                return mems.stream().anyMatch(m ->
                        String.valueOf(m.getRole()).toLowerCase(Locale.ROOT).contains(q)
                                || String.valueOf(m.getContributionNotes()).toLowerCase(Locale.ROOT).contains(q)
                                || directory.studentName(m.getStudentId()).toLowerCase(Locale.ROOT).contains(q));
            }).toList();
            table.setItems(FXCollections.observableArrayList(filtered));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Consumer<Integer> loadDataWithHighlight = (highlightProjectId) -> {
            CompletableFuture.supplyAsync(() -> {
                List<Project> list = projects.projectsForStudent(sid);
                List<Integer> pids = list.stream().map(Project::getProjectId).toList();
                Map<Integer, List<ProjectMember>> memMap = projects.membersForProjects(pids);
                Map<Integer, String> subs = directory.subjectNames();
                return new Object[]{list, memMap, subs};
            }).thenAccept(arr -> Platform.runLater(() -> {
                @SuppressWarnings("unchecked")
                List<Project> list = (List<Project>) arr[0];
                @SuppressWarnings("unchecked")
                Map<Integer, List<ProjectMember>> memMap = (Map<Integer, List<ProjectMember>>) arr[1];
                @SuppressWarnings("unchecked")
                Map<Integer, String> subs = (Map<Integer, String>) arr[2];

                allProjects.clear();
                allProjects.addAll(list);
                membersCache.clear();
                membersCache.putAll(memMap);
                subjects.clear();
                subjects.putAll(subs);

                if (highlightProjectId != null && highlightProjectId > 0) {
                    if (search.getText() != null && !search.getText().isBlank()) {
                        search.setText("");
                    }
                }
                applyFilter.run();

                if (highlightProjectId != null && highlightProjectId > 0) {
                    for (Project p : table.getItems()) {
                        if (p.getProjectId() == highlightProjectId) {
                            table.getSelectionModel().select(p);
                            table.scrollTo(p);
                            table.requestFocus();
                            break;
                        }
                    }
                }
            })).exceptionally(ex -> {
                Platform.runLater(() -> Alerts.error("Failed to load projects: " + ex.getMessage()));
                return null;
            });
        };

        Runnable loadDataAsync = () -> loadDataWithHighlight.accept(null);
        loadDataAsync.run();

        Button add = new Button("Add Project");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> editProject(sid, null, p -> loadDataWithHighlight.accept(p != null ? p.getProjectId() : null)));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a project to edit."); return; }
            if (!projects.isOwner(selected, sid)) {
                Alerts.error("Only the project owner can edit this project.");
                return;
            }
            editProject(sid, selected, p -> loadDataWithHighlight.accept(p != null ? p.getProjectId() : null));
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a project to delete."); return; }
            if (!projects.isOwner(selected, sid)) {
                Alerts.error("Only the project owner can delete this project.");
                return;
            }
            if (Ui.confirmed("Delete project \"" + selected.getProjectTitle() + "\"?")) {
                guard(() -> {
                    projects.deleteProject(selected.getProjectId(), sid);
                    loadDataWithHighlight.accept(null);
                });
            }
        });
        Button members = new Button("Group Members");
        members.setOnAction(e -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a project to manage its members."); return; }
            if (!projects.isOwner(selected, sid)) {
                Alerts.error("Only the project owner can manage group members.");
                return;
            }
            guard(() -> manageMembers(selected, sid, () -> loadDataWithHighlight.accept(selected.getProjectId())));
        });
        Button details = new Button("View Details");
        details.getStyleClass().add("secondary-button");
        details.setOnAction(e -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a project to view."); return; }
            guard(() -> showProject(selected));
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> loadDataWithHighlight.accept(null));

        return Ui.page("My Projects", "Add projects, link repositories and demos, and record the contributions of group members.",
                Ui.toolbar(new Label("Search:"), search, add, edit, delete, members, details, refresh), table);
    }

    private static void showProject(Project p) {
        Map<Integer, String> subjects = directory.subjectNames();
        Label description = new Label(value(p.getDescription()));
        description.setWrapText(true);

        String ownerName = p.getStudentId() == null ? "Unassigned" : directory.studentName(p.getStudentId());

        List<ProjectMember> memberList = projects.membersForProject(p.getProjectId());
        StringBuilder memberText = new StringBuilder();
        if (memberList.isEmpty()) {
            memberText.append("Individual Solo Project (No group members tagged)");
        } else {
            for (ProjectMember m : memberList) {
                String sName = directory.studentName(m.getStudentId());
                memberText.append("• ").append(sName);
                if (m.getRole() != null && !m.getRole().isBlank()) {
                    memberText.append(" — ").append(m.getRole());
                }
                String notes = m.getContributionNotes();
                if (notes != null && !notes.isBlank()) {
                    memberText.append("\n  Contribution: ").append(notes);
                }
                memberText.append('\n');
            }
        }
        Label membersLabel = new Label(memberText.toString().trim());
        membersLabel.setWrapText(true);

        Node form = Ui.form(
                new String[]{"Title", "Type", "Project Owner", "Subject", "Status", "Technologies", "Repository", "Demo", "Date", "Description", "Group Members & Contributions"},
                new Node[]{
                        new Label(value(p.getProjectTitle())),
                        new Label(value(p.getProjectType())),
                        new Label(ownerName),
                        new Label(p.getSubjectId() == null ? "" : subjects.getOrDefault(p.getSubjectId(), "Subject #" + p.getSubjectId())),
                        new Label(value(p.getStatus())),
                        new Label(value(p.getTechnologiesUsed())),
                        new Label(value(p.getRepositoryUrl())),
                        new Label(value(p.getDemoUrl())),
                        new Label(Ui.formatDate(p.getProjectDate())),
                        description,
                        membersLabel
                });
        Ui.viewDialog("Project - " + p.getProjectTitle(), form);
    }

    private static void editProject(int studentId, Project existing, Consumer<Project> onSaveSuccess) {
        Project p = existing == null ? new Project() : existing;
        List<Subject> enrolledSubjects = academic.enrolledSubjectsForStudent(studentId);
        List<Subject> subjectList = new ArrayList<>(enrolledSubjects.isEmpty() ? directory.subjects() : enrolledSubjects);
        if (p.getSubjectId() != null && subjectList.stream().noneMatch(s -> s.getSubjectId() == p.getSubjectId())) {
            directory.subjects().stream().filter(s -> s.getSubjectId() == p.getSubjectId()).findFirst().ifPresent(subjectList::add);
        }
        TextField title = Ui.field("Project title");
        title.setText(p.getProjectTitle());
        ComboBox<String> type = Ui.combo(List.of("Laboratory", "Academic Project", "Programming", "Capstone", "Personal", "Research", "Other"), s -> s);
        type.getSelectionModel().select(p.getProjectType() == null ? "Academic Project" : p.getProjectType());
        ComboBox<Subject> subject = Ui.combo(subjectList, s -> {
            String sec = s.getSection() != null ? " (" + s.getSection() + ")" : "";
            return s.getSubjectCode() + " - " + s.getSubjectName() + sec;
        });
        if (p.getSubjectId() != null) {
            subjectList.stream().filter(s -> s.getSubjectId() == p.getSubjectId()).findFirst()
                    .ifPresent(subject.getSelectionModel()::select);
        }
        TextArea description = Ui.area("Description");
        description.setText(p.getDescription());
        description.setPrefRowCount(3);
        TextField technologies = Ui.field("Java, JavaFX, MySQL, Supabase");
        technologies.setText(p.getTechnologiesUsed());
        TextField repository = Ui.field("https://github.com/...");
        repository.setText(p.getRepositoryUrl());
        TextField demo = Ui.field("https://...");
        demo.setText(p.getDemoUrl());
        DatePicker projectDate = Ui.date(p.getProjectDate() == null ? LocalDate.now() : p.getProjectDate());
        ComboBox<String> status = Ui.combo(List.of("Draft", "In Progress", "Completed", "Archived"), s -> s);
        status.getSelectionModel().select(p.getStatus() == null ? "Draft" : p.getStatus());

        Node projectForm = Ui.form(
                new String[]{"Title", "Type", "Subject", "Description", "Technologies", "Repository URL", "Demo URL", "Project date", "Status"},
                new Node[]{title, type, subject, description, technologies, repository, demo, projectDate, status});

        // Group Members Section
        VBox membersSection = new VBox(10);
        membersSection.getStyleClass().add("project-member-section");

        Label membersTitle = new Label("GROUP MEMBERS & COLLABORATIVE CREDIT");
        membersTitle.getStyleClass().add("project-member-section-title");

        Label membersSubtitle = new Label("Tag fellow NextIT students to credit their contributions. Credited projects will automatically reflect on their profiles and portfolios.");
        membersSubtitle.getStyleClass().add("project-member-section-subtitle");
        membersSubtitle.setWrapText(true);

        ObservableList<ProjectMember> draftMembers = FXCollections.observableArrayList();
        if (existing != null && existing.getProjectId() > 0) {
            projects.membersForProjectAsync(existing.getProjectId())
                    .thenAccept(list -> Platform.runLater(() -> draftMembers.setAll(list)));
        }

        ComboBox<Student> studentPicker = new ComboBox<>();
        studentPicker.setMaxWidth(Double.MAX_VALUE);
        studentPicker.setPromptText("Select student to add...");

        Runnable updateAvailableStudents = () -> {
            List<Student> available = directory.students().stream()
                    .filter(s -> s.getStudentId() != studentId)
                    .filter(s -> draftMembers.stream().noneMatch(m -> m.getStudentId() == s.getStudentId()))
                    .toList();
            studentPicker.setItems(FXCollections.observableArrayList(available));
            if (!available.isEmpty()) {
                studentPicker.getSelectionModel().selectFirst();
            } else {
                studentPicker.getSelectionModel().clearSelection();
            }
        };

        studentPicker.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Student s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : directory.studentName(s.getStudentId()) + " (" + s.getStudentNumber() + ")");
            }
        });
        studentPicker.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Student s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : directory.studentName(s.getStudentId()) + " (" + s.getStudentNumber() + ")");
            }
        });

        TextField roleInput = Ui.field("Role (e.g., API Development, Frontend UI)");
        TextField notesInput = Ui.field("Contribution notes (e.g., REST API endpoints, 50% UI)");

        Button addMemberBtn = new Button("+ Add Member");
        addMemberBtn.getStyleClass().add("project-member-add-btn");
        addMemberBtn.setOnAction(e -> {
            Student sel = studentPicker.getValue();
            if (sel == null) {
                Alerts.error("Select a student to add as a group member.");
                return;
            }
            String roleVal = roleInput.getText() == null ? "" : roleInput.getText().trim();
            String notesVal = notesInput.getText() == null ? "" : notesInput.getText().trim();
            if (roleVal.isBlank()) roleVal = "Group Contributor";
            if (notesVal.isBlank()) notesVal = "Project group member";
            ProjectMember newMem = new ProjectMember(existing == null ? 0 : existing.getProjectId(), sel.getStudentId(), roleVal, notesVal);
            draftMembers.add(newMem);
            roleInput.clear();
            notesInput.clear();
            updateAvailableStudents.run();
        });

        updateAvailableStudents.run();

        HBox memberInputsRow = new HBox(8, studentPicker, roleInput, notesInput, addMemberBtn);
        memberInputsRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(studentPicker, Priority.ALWAYS);
        HBox.setHgrow(roleInput, Priority.ALWAYS);
        HBox.setHgrow(notesInput, Priority.ALWAYS);

        TableView<ProjectMember> memberTable = Ui.table();
        memberTable.setPrefHeight(150);
        Ui.addColumn(memberTable, "Student", m -> directory.studentName(m.getStudentId()) + " (ID #" + m.getStudentId() + ")");
        Ui.addColumn(memberTable, "Role", ProjectMember::getRole);
        Ui.addColumn(memberTable, "Contribution Notes", ProjectMember::getContributionNotes);

        TableColumn<ProjectMember, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("✕ Remove");
            {
                removeBtn.getStyleClass().add("project-member-remove-btn");
                removeBtn.setOnAction(evt -> {
                    ProjectMember item = getTableView().getItems().get(getIndex());
                    draftMembers.remove(item);
                    updateAvailableStudents.run();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : removeBtn);
                setAlignment(Pos.CENTER);
            }
        });
        memberTable.getColumns().add(actionCol);
        memberTable.setItems(draftMembers);
        memberTable.setPlaceholder(new Label("No group members tagged yet. (Solo Project)"));

        membersSection.getChildren().addAll(membersTitle, membersSubtitle, memberInputsRow, memberTable);

        VBox dialogContent = new VBox(16, projectForm, membersSection);
        ScrollPane scroll = new ScrollPane(dialogContent);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(760);
        scroll.setPrefViewportHeight(580);

        Dialog<ButtonType> dialog = new Dialog<>();
        if (AppController.getInstance() != null && AppController.getInstance().getStage() != null) {
            dialog.initOwner(AppController.getInstance().getStage());
        }
        dialog.setTitle(existing == null ? "Add Project" : "Edit Project — " + existing.getProjectTitle());
        if (StudentViews.class.getResource("/com/nextit/css/application.css") != null) {
            dialog.getDialogPane().getStylesheets().add(StudentViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
        }
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText(existing == null ? "Create Project" : "Save Changes");
        okButton.getStyleClass().add("primary-button");

        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try {
                if (title.getText() == null || title.getText().isBlank()) {
                    Alerts.error("Project title cannot be empty.");
                    return;
                }
                Project target = existing == null ? new Project() : existing;
                target.setStudentId(existing == null ? Integer.valueOf(studentId) : existing.getStudentId());
                target.setProjectTitle(title.getText().trim());
                target.setProjectType(type.getValue());
                target.setSubjectId(subject.getValue() == null ? null : subject.getValue().getSubjectId());
                target.setDescription(description.getText());
                target.setTechnologiesUsed(technologies.getText());
                target.setRepositoryUrl(repository.getText());
                target.setDemoUrl(demo.getText());
                target.setProjectDate(projectDate.getValue());
                target.setStatus(status.getValue());

                okButton.setDisable(true);
                okButton.setText("Saving...");

                final boolean isNew = (existing == null);
                final Subject selectedSub = subject.getValue();
                final String subDisplayName = selectedSub != null ? selectedSub.getSubjectCode() + " - " + selectedSub.getSubjectName() : "None / Independent Project";
                final List<ProjectMember> savedDraftMembers = new ArrayList<>(draftMembers);

                projects.saveProjectWithMembersAsync(target, savedDraftMembers, studentId)
                        .thenRun(() -> Platform.runLater(() -> {
                            dialog.setResult(ButtonType.OK);
                            dialog.close();

                            // 1. Immediately refresh and highlight the project row in the table
                            if (onSaveSuccess != null) {
                                onSaveSuccess.accept(target);
                            }

                            // 2. Launch styled NextIT confirmation modal
                            Platform.runLater(() -> {
                                Window topWindow = AppController.getInstance() != null ? AppController.getInstance().getStage() : null;
                                if (topWindow == null || !topWindow.isShowing()) {
                                    topWindow = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null);
                                }
                                showProjectConfirmationModal(topWindow, target, isNew, subDisplayName, savedDraftMembers);
                            });
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> {
                                okButton.setDisable(false);
                                okButton.setText(isNew ? "Create Project" : "Save Changes");
                                Alerts.error(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                            });
                            return null;
                        });
            } catch (Exception ex) {
                okButton.setDisable(false);
                okButton.setText(existing == null ? "Create Project" : "Save Changes");
                Alerts.error(ex.getMessage());
            }
        });

        dialog.showAndWait();
    }

    private static void showProjectConfirmationModal(Window ownerWindow, Project project, boolean isNew, String subjectName, List<ProjectMember> members) {
        Stage modalStage = new Stage();
        modalStage.initModality(Modality.APPLICATION_MODAL);
        if (ownerWindow != null && ownerWindow.isShowing()) {
            modalStage.initOwner(ownerWindow);
        }
        modalStage.initStyle(StageStyle.TRANSPARENT);

        StackPane rootPane = new StackPane();
        rootPane.setStyle("-fx-background-color: transparent; -fx-padding: 30;");

        VBox card = new VBox(16);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(500);
        card.setMaxWidth(500);
        card.setStyle(
                "-fx-background-color: #FAF7F2; " +
                "-fx-background-radius: 16; " +
                "-fx-border-color: #E2D9CC; " +
                "-fx-border-radius: 16; " +
                "-fx-border-width: 1.5; " +
                "-fx-padding: 26 28 24 28; " +
                "-fx-effect: dropshadow(gaussian, rgba(26, 38, 52, 0.25), 24, 0.15, 0, 8);"
        );

        // 1. Success Checkmark Icon (Green checkmark in circular badge)
        StackPane iconCircle = new StackPane();
        iconCircle.setStyle(
                "-fx-background-color: #DCFCE7; " +
                "-fx-background-radius: 50; " +
                "-fx-min-width: 58; -fx-min-height: 58; " +
                "-fx-max-width: 58; -fx-max-height: 58; " +
                "-fx-border-color: #86EFAC; " +
                "-fx-border-radius: 50; " +
                "-fx-border-width: 1.5;"
        );
        Label checkIcon = new Label("✔");
        checkIcon.setStyle("-fx-font-size: 28px; -fx-font-weight: 900; -fx-text-fill: #15803D;");
        iconCircle.getChildren().add(checkIcon);

        // 2. Heading & Subtitle
        Label heading = new Label(isNew ? "Project Added" : "Project Saved");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");

        Label subHeading = new Label(isNew
                ? "Your project and collaborative credits have been added to your portfolio."
                : "Your project details and group credits have been successfully updated.");
        subHeading.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #5A6A7E; -fx-font-weight: 500;");

        // 3. Project Metadata Summary Card
        VBox detailsCard = new VBox(10);
        detailsCard.setStyle(
                "-fx-background-color: #FFFFFF; " +
                "-fx-border-color: #E2D9CC; " +
                "-fx-border-radius: 12; " +
                "-fx-background-radius: 12; " +
                "-fx-padding: 14 16;"
        );

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label typeBadge = new Label(project.getProjectType() != null ? project.getProjectType() : "Project");
        typeBadge.setStyle(
                "-fx-background-color: #991B1B; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-weight: 800; " +
                "-fx-font-size: 11.5px; " +
                "-fx-padding: 3 10; " +
                "-fx-background-radius: 6;"
        );

        Label titleLbl = new Label(project.getProjectTitle() != null ? project.getProjectTitle() : "Untitled Project");
        titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");
        titleLbl.setWrapText(true);
        HBox.setHgrow(titleLbl, Priority.ALWAYS);
        titleRow.getChildren().addAll(typeBadge, titleLbl);

        Separator sep = new Separator();
        sep.setStyle("-fx-opacity: 0.5;");

        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(14);
        metaGrid.setVgap(7);

        int rowIdx = 0;

        Label subLabel = new Label("Subject:");
        subLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label subVal = new Label(subjectName != null && !subjectName.isBlank() ? subjectName : "None / Independent Project");
        subVal.setStyle("-fx-font-size: 12px; -fx-text-fill: #1A2634; -fx-font-weight: 700;");
        subVal.setWrapText(true);
        metaGrid.add(subLabel, 0, rowIdx);
        metaGrid.add(subVal, 1, rowIdx++);

        String creditRole = (members == null || members.isEmpty())
                ? "Owner (Solo)"
                : "Owner (Project Lead) • " + (members.size() + 1) + " members (" + members.size() + " credited)";
        Label roleLabel = new Label("Credit / Role:");
        roleLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label roleVal = new Label(creditRole);
        roleVal.setStyle("-fx-font-size: 12px; -fx-text-fill: #1A2634; -fx-font-weight: 700;");
        metaGrid.add(roleLabel, 0, rowIdx);
        metaGrid.add(roleVal, 1, rowIdx++);

        Label statLabel = new Label("Status:");
        statLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label statVal = new Label(project.getStatus() != null ? project.getStatus() : "Draft");
        statVal.setStyle("-fx-font-size: 12px; -fx-text-fill: #15803D; -fx-font-weight: 800;");
        metaGrid.add(statLabel, 0, rowIdx);
        metaGrid.add(statVal, 1, rowIdx++);

        String tech = project.getTechnologiesUsed();
        Label techLabel = new Label("Technologies:");
        techLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label techVal = new Label(tech != null && !tech.isBlank() ? tech : "None specified");
        techVal.setStyle("-fx-font-size: 12px; -fx-text-fill: #1A2634; -fx-font-weight: 600;");
        techVal.setWrapText(true);
        metaGrid.add(techLabel, 0, rowIdx);
        metaGrid.add(techVal, 1, rowIdx++);

        String repo = project.getRepositoryUrl();
        Label repoLabel = new Label("Repository:");
        repoLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label repoVal = new Label(repo != null && !repo.isBlank() ? repo : "Not provided");
        repoVal.setStyle(repo != null && !repo.isBlank()
                ? "-fx-font-size: 12px; -fx-text-fill: #1D4ED8; -fx-font-weight: 600;"
                : "-fx-font-size: 12px; -fx-text-fill: #7A889B; -fx-font-style: italic;");
        repoVal.setWrapText(true);
        metaGrid.add(repoLabel, 0, rowIdx);
        metaGrid.add(repoVal, 1, rowIdx++);

        String demo = project.getDemoUrl();
        if (demo != null && !demo.isBlank()) {
            Label demoLabel = new Label("Demo URL:");
            demoLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
            Label demoVal = new Label(demo);
            demoVal.setStyle("-fx-font-size: 12px; -fx-text-fill: #1D4ED8; -fx-font-weight: 600;");
            demoVal.setWrapText(true);
            metaGrid.add(demoLabel, 0, rowIdx);
            metaGrid.add(demoVal, 1, rowIdx++);
        }

        detailsCard.getChildren().addAll(titleRow, sep, metaGrid);

        // 4. Prominent "Done" Button
        Button doneBtn = new Button("Done");
        doneBtn.setStyle(
                "-fx-background-color: #991B1B; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 13.5px; " +
                "-fx-font-weight: 800; " +
                "-fx-padding: 10 36; " +
                "-fx-background-radius: 8; " +
                "-fx-cursor: hand;"
        );
        doneBtn.setMaxWidth(Double.MAX_VALUE);
        doneBtn.setOnAction(e -> modalStage.close());

        card.getChildren().addAll(iconCircle, heading, subHeading, detailsCard, doneBtn);
        rootPane.getChildren().add(card);

        Scene modalScene = new Scene(rootPane);
        modalScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        if (StudentViews.class.getResource("/com/nextit/css/application.css") != null) {
            modalScene.getStylesheets().add(StudentViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
        }
        modalScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE || ev.getCode() == javafx.scene.input.KeyCode.ENTER) {
                modalStage.close();
                ev.consume();
            }
        });

        modalStage.setScene(modalScene);

        if (ownerWindow != null) {
            modalStage.setOnShown(ev -> {
                double x = ownerWindow.getX() + (ownerWindow.getWidth() - modalStage.getWidth()) / 2.0;
                double y = ownerWindow.getY() + (ownerWindow.getHeight() - modalStage.getHeight()) / 2.0;
                modalStage.setX(Math.max(0, x));
                modalStage.setY(Math.max(0, y));
            });
        } else {
            modalStage.centerOnScreen();
        }

        modalStage.show();
    }

    private static void manageMembers(Project project, int studentId, Runnable refreshCallback) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (AppController.getInstance() != null && AppController.getInstance().getStage() != null) {
            dialog.initOwner(AppController.getInstance().getStage());
        }
        dialog.setTitle("Group Members - " + project.getProjectTitle());
        if (StudentViews.class.getResource("/com/nextit/css/application.css") != null) {
            dialog.getDialogPane().getStylesheets().add(StudentViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
        }
        TableView<ProjectMember> table = Ui.table();
        Map<Integer, String> students = directory.studentNames();
        Ui.addColumn(table, "Student", m -> students.getOrDefault(m.getStudentId(), "Student #" + m.getStudentId()));
        Ui.addColumn(table, "Role", ProjectMember::getRole);
        Ui.addColumn(table, "Contribution Notes", ProjectMember::getContributionNotes);
        Runnable refresh = () -> {
            table.setItems(FXCollections.observableArrayList(projects.membersForProject(project.getProjectId())));
            if (refreshCallback != null) refreshCallback.run();
        };
        refresh.run();

        Button add = new Button("Add Member");
        add.setOnAction(e -> {
            List<ProjectMember> currentMembers = projects.membersForProject(project.getProjectId());
            List<Student> studentList = directory.students().stream()
                    .filter(s -> s.getStudentId() != project.getStudentId())
                    .filter(s -> currentMembers.stream().noneMatch(m -> m.getStudentId() == s.getStudentId()))
                    .toList();
            if (studentList.isEmpty()) {
                Alerts.info("Every available student is already on this project.");
                return;
            }
            ComboBox<Student> student = Ui.combo(studentList, s -> {
                String name = students.getOrDefault(s.getStudentId(), "Student #" + s.getStudentId());
                return name + " (" + s.getStudentNumber() + ")";
            });
            TextField role = Ui.field("Role (e.g. Developer, Designer)");
            TextField contribution = Ui.field("Contribution notes / percentage / area");
            Node form = Ui.form(new String[]{"Student", "Role", "Contribution Notes"}, new Node[]{student, role, contribution});
            Ui.dialog("Add Group Member", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    if (student.getValue() == null) throw new AppException("Select a student.");
                    ProjectMember member = new ProjectMember();
                    member.setProjectId(project.getProjectId());
                    member.setStudentId(student.getValue().getStudentId());
                    member.setRole(role.getText());
                    member.setContribution(contribution.getText());
                    member.setContributionNotes(contribution.getText());
                    projects.addMember(member, studentId);
                    refresh.run();
                });
            });
        });
        Button remove = new Button("Remove Selected");
        remove.setOnAction(e -> {
            ProjectMember selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a member to remove."); return; }
            if (Ui.confirmed("Remove this member from the project?")) {
                guard(() -> { projects.removeMember(selected.getProjectMemberId(), studentId); refresh.run(); });
            }
        });
        dialog.getDialogPane().setContent(new VBox(10, Ui.toolbar(add, remove), table));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(700, 480);
        dialog.showAndWait();
    }

    public static Node achievements() {
        return achievementsAndCertifications();
    }

    public static Node certifications() {
        return achievementsAndCertifications();
    }

    public static Node achievementsAndCertifications() {
        int sid = studentId();
        TableView<AcademicCredential> table = Ui.table("No achievements or certifications recorded yet.");

        TableColumn<AcademicCredential, AcademicCredential> typeCol = Ui.actionColumn("Type", cred ->
                Ui.badge(cred.getType().getDisplay(), cred.getType().getBadgeStyle()));
        typeCol.setPrefWidth(130);
        typeCol.setMinWidth(110);
        table.getColumns().add(typeCol);

        Ui.addColumn(table, "Title / Credential", AcademicCredential::getTitle);
        Ui.addColumn(table, "Organization / Issuer", AcademicCredential::getOrganization);
        Ui.addColumn(table, "Date / Timeline", AcademicCredential::getDateDisplay);
        Ui.addColumn(table, "Description / Notes", AcademicCredential::getDescription);
        TableColumn<AcademicCredential, AcademicCredential> proofCol = Ui.actionColumn("Certificate / Proof", cred -> {
            String urlOrPath = cred.getVerificationUrl();
            if (urlOrPath == null || urlOrPath.isBlank()) {
                Label none = new Label("—");
                none.setStyle("-fx-text-fill: #94A3B8; -fx-font-weight: 600;");
                return none;
            }
            Button viewBtn = new Button("🖼️ View Picture");
            viewBtn.setStyle("-fx-background-color: #FAF7F2; -fx-border-color: #991B1B; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #991B1B; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 3 8; -fx-cursor: hand;");
            viewBtn.setOnAction(ev -> CertificateImageViewer.show(urlOrPath, cred.getTitle(), cred.getOrganization()));
            return viewBtn;
        });
        proofCol.setPrefWidth(140);
        proofCol.setMinWidth(120);
        table.getColumns().add(proofCol);

        TextField search = Ui.field("Search by title, organization, issuer, description...");
        search.setPrefWidth(280);

        final String[] activeFilter = {"ALL"};

        Button filterAll = new Button("All");
        Button filterAchievements = new Button("Achievements");
        Button filterCertifications = new Button("Certifications");

        HBox filterBox = new HBox(6, filterAll, filterAchievements, filterCertifications);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        Runnable updateFilterPillStyles = () -> {
            for (Button btn : List.of(filterAll, filterAchievements, filterCertifications)) {
                btn.setStyle("-fx-background-color: #EFE2CA; -fx-text-fill: #414141; -fx-border-color: #C6A75E; " +
                        "-fx-border-radius: 999; -fx-background-radius: 999; -fx-padding: 4 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
            }
            Button active = "ACHIEVEMENT".equals(activeFilter[0]) ? filterAchievements :
                    "CERTIFICATION".equals(activeFilter[0]) ? filterCertifications : filterAll;
            active.setStyle("-fx-background-color: #9E0B0F; -fx-text-fill: #FDFBFA; -fx-border-color: #9E0B0F; " +
                    "-fx-border-radius: 999; -fx-background-radius: 999; -fx-padding: 4 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
        };

        Runnable refresh = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            List<AcademicCredential> all = studentService.credentialsFor(sid);
            List<AcademicCredential> filtered = all.stream().filter(cred -> {
                if ("ACHIEVEMENT".equals(activeFilter[0]) && cred.getType() != AcademicCredential.Type.ACHIEVEMENT) return false;
                if ("CERTIFICATION".equals(activeFilter[0]) && cred.getType() != AcademicCredential.Type.CERTIFICATION) return false;
                if (q.isEmpty()) return true;
                return (cred.getTitle() != null && cred.getTitle().toLowerCase(Locale.ROOT).contains(q))
                        || (cred.getOrganization() != null && cred.getOrganization().toLowerCase(Locale.ROOT).contains(q))
                        || (cred.getDescription() != null && cred.getDescription().toLowerCase(Locale.ROOT).contains(q))
                        || (cred.getVerificationUrl() != null && cred.getVerificationUrl().toLowerCase(Locale.ROOT).contains(q))
                        || (cred.getDateDisplay() != null && cred.getDateDisplay().toLowerCase(Locale.ROOT).contains(q));
            }).toList();
            table.setItems(FXCollections.observableArrayList(filtered));
        };

        filterAll.setOnAction(e -> { activeFilter[0] = "ALL"; updateFilterPillStyles.run(); refresh.run(); });
        filterAchievements.setOnAction(e -> { activeFilter[0] = "ACHIEVEMENT"; updateFilterPillStyles.run(); refresh.run(); });
        filterCertifications.setOnAction(e -> { activeFilter[0] = "CERTIFICATION"; updateFilterPillStyles.run(); refresh.run(); });

        updateFilterPillStyles.run();
        search.textProperty().addListener((obs, old, value) -> refresh.run());
        refresh.run();

        Button add = new Button("Add Credential");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> editCredential(sid, null, refresh));

        Button edit = new Button("Edit");
        edit.setOnAction(e -> {
            AcademicCredential selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Select an achievement or certification to edit.");
                return;
            }
            editCredential(sid, selected, refresh);
        });

        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            AcademicCredential selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Select an achievement or certification to delete.");
                return;
            }
            String typeName = selected.getType() == AcademicCredential.Type.ACHIEVEMENT ? "achievement" : "certification";
            if (Ui.confirmed("Delete " + typeName + " \"" + selected.getTitle() + "\"?")) {
                guard(() -> {
                    studentService.deleteCredential(selected);
                    refresh.run();
                });
            }
        });

        return Ui.page("Achievements & Certifications",
                "Record awards, recognitions, milestones, and professional certifications that strengthen your portfolio.",
                Ui.toolbar(new Label("Search:"), search, new Label("Filter:"), filterBox, add, edit, delete, refreshButton(refresh)),
                table);
    }

    private static void editCredential(int studentId, AcademicCredential existing, Runnable refresh) {
        ComboBox<AcademicCredential.Type> typeCombo = Ui.combo(
                List.of(AcademicCredential.Type.ACHIEVEMENT, AcademicCredential.Type.CERTIFICATION),
                AcademicCredential.Type::getDisplay);
        if (existing != null) {
            typeCombo.setValue(existing.getType());
            typeCombo.setDisable(true);
        } else {
            typeCombo.setValue(AcademicCredential.Type.ACHIEVEMENT);
        }

        TextField title = Ui.field("Title / Certification Name");
        if (existing != null) title.setText(existing.getTitle());

        TextField organization = Ui.field("Issuing Organization / Issuer");
        if (existing != null) organization.setText(existing.getOrganization());

        DatePicker date = Ui.date(existing != null ? existing.getDate() : LocalDate.now());

        DatePicker expires = Ui.date(existing != null ? existing.getExpirationDate() : null);
        expires.setPromptText("Optional (leave empty if non-expiring)");

        TextArea description = Ui.area("Description, honors, or notes");
        if (existing != null) description.setText(existing.getDescription());

        CertificateUploaderBox uploaderBox = new CertificateUploaderBox(existing != null ? existing.getVerificationUrl() : null);

        Runnable syncFieldsForType = () -> {
            boolean isCert = typeCombo.getValue() == AcademicCredential.Type.CERTIFICATION;
            expires.setDisable(!isCert);
            if (!isCert) {
                expires.setValue(null);
            }
        };
        typeCombo.valueProperty().addListener((obs, old, val) -> syncFieldsForType.run());
        syncFieldsForType.run();

        Node form = Ui.form(new String[]{
                "Credential Type",
                "Title / Name",
                "Organization / Issuer",
                "Date / Issue Date",
                "Expiration Date (Certifications)",
                "Description / Notes",
                "Certificate / Proof Picture"
        }, new Node[]{typeCombo, title, organization, date, expires, description, uploaderBox});

        String dialogTitle = existing == null ? "Add Achievement or Certification" :
                (existing.getType() == AcademicCredential.Type.ACHIEVEMENT ? "Edit Achievement" : "Edit Certification");

        Ui.dialog(dialogTitle, form).ifPresent(button -> {
            if (button != ButtonType.OK) return;
            guard(() -> {
                AcademicCredential target = new AcademicCredential();
                target.setType(typeCombo.getValue());
                target.setStudentId(studentId);
                if (existing != null) {
                    target.setId(existing.getId());
                }
                target.setTitle(title.getText());
                target.setOrganization(organization.getText());
                LocalDate dateVal = (date.getEditor().getText() == null || date.getEditor().getText().trim().isEmpty())
                        ? null
                        : date.getValue();
                LocalDate expiryVal = (expires.getEditor().getText() == null || expires.getEditor().getText().trim().isEmpty())
                        ? null
                        : expires.getValue();
                target.setDate(dateVal);
                target.setExpirationDate(expiryVal);
                target.setDescription(description.getText());
                target.setVerificationUrl(uploaderBox.saveAndGetFinalPath());

                studentService.saveCredential(target);
                refresh.run();
                Alerts.info((target.getType() == AcademicCredential.Type.ACHIEVEMENT ? "Achievement" : "Certification") + " saved.");
            });
        });
    }

    public static Node portfolio() {
        int sid = studentId();
        Portfolio loaded = portfolio.portfolioForStudent(sid).orElse(new Portfolio());
        loaded.setStudentId(sid);

        TextField headline = Ui.field("Professional headline");
        headline.setText(loaded.getHeadline());
        TextArea bio = Ui.area("Biography");
        bio.setText(loaded.getBio());
        TextArea goal = Ui.area("Career goal");
        goal.setText(loaded.getCareerGoal());
        ComboBox<String> sharing = Ui.combo(
                List.of("Private", "Public", "Submit for approval"), s -> s);
        if ("Public".equalsIgnoreCase(loaded.getVisibility())) {
            sharing.setValue("Public");
        } else if ("Pending".equalsIgnoreCase(loaded.getVisibility())
                || "Approved".equalsIgnoreCase(loaded.getVisibility())) {
            sharing.setValue("Submit for approval");
        } else {
            sharing.setValue("Private");
        }

        Label visibility = new Label(loaded.getVisibility() == null ? "Private" : loaded.getVisibility());
        Tab previewTab = tab("Preview", portfolioPreview(sid, loaded));
        Tab accessTab = tab("Access", portfolioAccess(loaded));

        Button save = new Button("Save Portfolio");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> guard(() -> {
            loaded.setHeadline(headline.getText());
            loaded.setBio(bio.getText());
            loaded.setCareerGoal(goal.getText());
            String choice = sharing.getValue();
            if ("Public".equals(choice)) {
                loaded.setVisibility("Public");
            } else if ("Submit for approval".equals(choice)) {
                // Keep an existing approval; new or edited content is submitted
                // for review. PortfolioService downgrades changed approvals.
                if (!"Approved".equalsIgnoreCase(loaded.getVisibility())) loaded.setVisibility("Pending");
            } else {
                loaded.setVisibility("Private");
            }
            portfolio.save(loaded);
            visibility.setText(loaded.getVisibility());
            previewTab.setContent(portfolioPreview(sid, loaded));
            accessTab.setContent(portfolioAccess(loaded));
            Node contentNode = previewTab.getContent();
            if (contentNode instanceof ScrollPane sp) {
                javafx.application.Platform.runLater(() -> sp.setVvalue(0.0));
            }
            String message = "Pending".equals(loaded.getVisibility())
                    ? "It is now awaiting instructor approval."
                    : "Public".equals(loaded.getVisibility())
                            ? "It is now visible to every employer."
                            : "Current visibility: " + loaded.getVisibility() + ".";
            Alerts.info("Portfolio saved. " + message);
        }));

        Student curStudent = profiles.studentForUser(Session.currentUser().getUserId()).orElse(null);
        String progTag = curStudent != null && curStudent.getProgram() != null
                ? curStudent.getProgram() + " - " + formatYearLevelDisplay(curStudent.getYearLevel())
                : "BS Information Technology - Year 3";
        Label progBadge = Ui.badge(progTag, "badge-pill-lavender");
        progBadge.setStyle("-fx-font-weight: 700; -fx-padding: 4 10;");

        Region editHeaderSpacer = new Region();
        HBox.setHgrow(editHeaderSpacer, Priority.ALWAYS);
        HBox editorHeader = new HBox(12,
                Ui.subtitle("Private keeps the portfolio hidden, Public shares it with every employer, and Submit for approval asks an instructor to approve it for granted employer access."),
                editHeaderSpacer,
                progBadge
        );
        editorHeader.setAlignment(Pos.CENTER_LEFT);

        VBox editor = new VBox(12,
                editorHeader,
                Ui.form(new String[]{"Headline", "Biography", "Career goal", "Visibility"},
                        new Node[]{headline, bio, goal, visibility}),
                new HBox(8, new Label("Sharing:"), sharing), save);

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(tab("Editor", editor), previewTab, accessTab);

        previewTab.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                loaded.setHeadline(headline.getText());
                loaded.setBio(bio.getText());
                loaded.setCareerGoal(goal.getText());
                previewTab.setContent(portfolioPreview(sid, loaded));
                Node contentNode = previewTab.getContent();
                if (contentNode instanceof ScrollPane sp) {
                    javafx.application.Platform.runLater(() -> sp.setVvalue(0.0));
                }
            }
        });

        return Ui.page("Digital Portfolio", "Present your profile, skills, projects, achievements and certifications to industry partners.", tabs);
    }

    private static Node portfolioPreview(int studentId, Portfolio p) {
        return PortfolioDetailsView.createPortfolioView(p, true, true);
    }

    private static String value(String text) { return text == null ? "" : text; }

    private static Button refreshButton(Runnable action) {
        Button button = new Button("Refresh");
        button.setOnAction(e -> action.run());
        return button;
    }

    private static Node portfolioAccess(Portfolio p) {
        TableView<PortfolioAccess> table = Ui.table();
        Map<Integer, String> employers = directory.employerNames();
        Ui.addColumn(table, "Employer", a -> employers.getOrDefault(a.getEmployerId(), "Employer #" + a.getEmployerId()));
        Ui.addColumn(table, "Status", PortfolioAccess::getAccessStatus);
        Ui.addColumn(table, "Granted", a -> a.getGrantedAt() == null ? "" : a.getGrantedAt().toLocalDate().toString());
        if (p.getPortfolioId() > 0) {
            table.setItems(FXCollections.observableArrayList(portfolio.accessFor(p.getPortfolioId())));
        }
        return Ui.page("Portfolio Access", "These employers have been granted access to your approved portfolio.", table);
    }

    public static Node opportunities() {
        int sid = studentId();
        List<JobOpportunity> all = career.openOpportunities();
        TableView<JobOpportunity> table = Ui.table();
        Ui.addColumn(table, "Title", JobOpportunity::getTitle);
        Ui.addColumn(table, "Type", JobOpportunity::getOpportunityType);
        Ui.addColumn(table, "Location", o -> value(o.getLocation()));
        Ui.addColumn(table, "Required Skills", o -> value(o.getRequiredSkills()));
        Ui.addColumn(table, "Deadline", o -> Ui.formatDate(o.getDeadline()));
        Ui.addColumn(table, "Posted", o -> Ui.formatDate(o.getCreatedAt() == null ? null : o.getCreatedAt().toLocalDate()));
        Ui.addColumn(table, "Status", JobOpportunity::getStatus);

        Map<Integer, RecommendationLetter> lettersByOpp = new java.util.HashMap<>();
        Ui.addColumn(table, "Faculty Recommendation", o -> {
            RecommendationLetter l = lettersByOpp.get(o.getOpportunityId());
            if (l == null) return "-";
            if ("ATTACHED".equalsIgnoreCase(l.getStatus())) return "✔ Attached";
            if ("ISSUED".equalsIgnoreCase(l.getStatus())) return "✔ Ready to Attach";
            if ("REQUESTED".equalsIgnoreCase(l.getStatus())) return "⏳ Requested";
            return l.getStatus();
        });

        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search opportunities...");
        ComboBox<String> type = Ui.combo(List.of("All types", "Internship", "Employment"), s -> s);
        type.getSelectionModel().selectFirst();
        Runnable filter = () -> {
            recLetters.getLettersForStudentAsync(sid).thenAccept(letters -> Platform.runLater(() -> {
                lettersByOpp.clear();
                for (RecommendationLetter l : letters) {
                    if (l.getOpportunityId() != null) {
                        lettersByOpp.put(l.getOpportunityId(), l);
                    }
                }
                String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
                String t = type.getValue();
                table.setItems(FXCollections.observableArrayList(career.openOpportunities().stream().filter(o ->
                        (q.isBlank()
                                || String.valueOf(o.getTitle()).toLowerCase(Locale.ROOT).contains(q)
                                || String.valueOf(o.getRequiredSkills()).toLowerCase(Locale.ROOT).contains(q)
                                || String.valueOf(o.getLocation()).toLowerCase(Locale.ROOT).contains(q))
                        && ("All types".equals(t) || String.valueOf(o.getOpportunityType()).equalsIgnoreCase(t))).toList()));
                table.refresh();
            }));
        };
        search.textProperty().addListener((obs, old, value) -> filter.run());
        type.valueProperty().addListener((obs, old, value) -> filter.run());
        filter.run();

        Button apply = new Button("Apply");
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(e -> {
            JobOpportunity selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an opportunity to apply to."); return; }
            if (career.hasApplied(sid, selected.getOpportunityId())) {
                Alerts.error("You have already submitted an application for this opportunity.");
                return;
            }

            // Find student's issued recommendation letters (either matching this opportunity or general)
            List<RecommendationLetter> issuedLetters = recLetters.getLettersForStudent(sid).stream()
                    .filter(RecommendationLetter::isIssued)
                    .filter(l -> l.getOpportunityId() == null || l.getOpportunityId() == selected.getOpportunityId())
                    .toList();

            Map<Integer, String> instructorNames = directory.instructorNames();

            ComboBox<String> letterSelector = new ComboBox<>();
            letterSelector.getItems().add("None / Submit without recommendation letter");
            Map<String, RecommendationLetter> letterMap = new java.util.HashMap<>();
            for (RecommendationLetter l : issuedLetters) {
                String instName = instructorNames.getOrDefault(l.getInstructorId(), "Instructor #" + l.getInstructorId());
                String label = String.format("✔ Prof. %s - %s (%s)",
                        instName, l.getLetterTitle(),
                        l.getIssuedAt() != null ? Ui.formatDate(l.getIssuedAt().toLocalDate()) : "Issued");
                letterSelector.getItems().add(label);
                letterMap.put(label, l);
            }
            letterSelector.getSelectionModel().selectFirst();

            TextArea remarks = Ui.area("Why are you a good fit for this opportunity?");

            VBox formBox = new VBox(12);
            formBox.setPrefWidth(480);
            formBox.getChildren().addAll(
                    new Label("Applying to: " + selected.getTitle() + " at " + selected.getLocation()),
                    new Label("Your Statement / Remarks:"), remarks
            );

            if (!issuedLetters.isEmpty()) {
                VBox recSection = new VBox(6);
                recSection.setStyle("-fx-background-color: #FAF7F2; -fx-border-color: #F2E7D3; -fx-border-radius: 8; -fx-padding: 10;");
                Label recTitle = new Label("Attach Verified Faculty Recommendation Letter:");
                recTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F; -fx-font-size: 12px;");
                recSection.getChildren().addAll(recTitle, letterSelector);
                formBox.getChildren().add(recSection);
            } else {
                VBox recPrompt = new VBox(6);
                recPrompt.setStyle("-fx-background-color: #FAF5E8; -fx-border-color: rgba(74, 90, 106, 0.25); -fx-border-radius: 8; -fx-padding: 10;");
                Label pTitle = new Label("💡 Optional: Boost your application with a Faculty Recommendation");
                pTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #1A2634; -fx-font-size: 11.5px;");
                Label pDesc = new Label("You can request an official endorsement letter from your course instructors before or after applying.");
                pDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #4A5A6A;");
                Button reqQuickBtn = Ui.button("Request Recommendation from Instructor", "secondary-button");
                reqQuickBtn.setOnAction(ev -> showRequestRecommendationDialog(sid, selected.getOpportunityId(), filter));
                recPrompt.getChildren().addAll(pTitle, pDesc, reqQuickBtn);
                formBox.getChildren().add(recPrompt);
            }

            Ui.dialog("Apply to Opportunity - " + selected.getTitle(), formBox).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    Application application = new Application();
                    application.setStudentId(sid);
                    application.setOpportunityId(selected.getOpportunityId());
                    application.setRemarks(remarks.getText());
                    career.apply(application);

                    String chosenLabel = letterSelector.getValue();
                    if (chosenLabel != null && letterMap.containsKey(chosenLabel)) {
                        RecommendationLetter attachedLetter = letterMap.get(chosenLabel);
                        recLetters.attachLetterToApplication(attachedLetter.getLetterId(), application.getApplicationId(), sid);
                        Alerts.info("Application submitted with verified faculty recommendation letter attached!");
                    } else {
                        Alerts.info("Application submitted successfully.");
                    }
                    filter.run();
                });
            });
        });

        Button details = new Button("View Details");
        details.getStyleClass().add("secondary-button");
        details.setOnAction(e -> {
            JobOpportunity selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an opportunity to view."); return; }
            Label description = new Label(value(selected.getDescription()));
            description.setWrapText(true);

            Button reqFromDetails = Ui.button("✍ Request Faculty Recommendation for this Role", "clay-btn-secondary");
            reqFromDetails.setOnAction(ev -> showRequestRecommendationDialog(sid, selected.getOpportunityId(), filter));

            Node content = Ui.form(new String[]{"Title", "Type", "Location", "Required Skills", "Deadline", "Posted", "Status", "Endorsement", "Description"},
                    new Node[]{new Label(value(selected.getTitle())), new Label(value(selected.getOpportunityType())),
                            new Label(value(selected.getLocation())), new Label(value(selected.getRequiredSkills())),
                            new Label(Ui.formatDate(selected.getDeadline())),
                            new Label(Ui.formatDate(selected.getCreatedAt() == null ? null : selected.getCreatedAt().toLocalDate())),
                            new Label(value(selected.getStatus())),
                            reqFromDetails,
                            description});
            Ui.viewDialog("Opportunity Details", content);
        });

        Button requestRec = Ui.button("Request Recommendation", "secondary-button");
        requestRec.setOnAction(e -> {
            JobOpportunity selected = table.getSelectionModel().getSelectedItem();
            showRequestRecommendationDialog(sid, selected != null ? selected.getOpportunityId() : null, filter);
        });

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> filter.run());
        return Ui.page("Opportunities", "Browse internships and employment opportunities from industry partners.",
                Ui.toolbar(new Label("Search:"), search, type, apply, details, requestRec, refresh), table);
    }

    public static Node applications() {
        int sid = studentId();
        Map<Integer, String> opportunities = new java.util.HashMap<>();
        Map<Integer, String> employers = new java.util.HashMap<>();
        Map<Integer, JobOpportunity> byId = new java.util.HashMap<>();
        Map<Integer, RecommendationLetter> lettersByApp = new java.util.HashMap<>();

        TableView<Application> table = Ui.table("You have not submitted any applications yet.");
        Ui.addColumn(table, "Opportunity", a -> opportunities.getOrDefault(a.getOpportunityId(), "Opportunity #" + a.getOpportunityId()));
        Ui.addColumn(table, "Company", a -> {
            JobOpportunity o = byId.get(a.getOpportunityId());
            return o == null ? "" : employers.getOrDefault(o.getEmployerId(), "Employer #" + o.getEmployerId());
        });
        Ui.addColumn(table, "Applied", a -> a.getApplicationDate() == null ? "" : Ui.formatDate(a.getApplicationDate().toLocalDate()));

        TableColumn<Application, Application> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Application item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    String st = item.getStatus() == null ? "Pending" : item.getStatus();
                    setGraphic(Ui.badge(st, badgeClassFor(st)));
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });
        table.getColumns().add(statusCol);

        Ui.addColumn(table, "Faculty Endorsement", a -> lettersByApp.containsKey(a.getApplicationId()) ? "✔ Endorsed" : "-");

        TableColumn<Application, String> remarksCol = new TableColumn<>("Employer Remarks");
        remarksCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRemarks()));
        remarksCol.setCellFactory(col -> new TableCell<Application, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("No remarks yet");
                    setStyle("-fx-text-fill: #999086; -fx-font-style: italic; -fx-alignment: CENTER-LEFT;");
                    setTooltip(null);
                } else {
                    String trimmed = item.trim().replaceAll("\\s+", " ");
                    String preview = trimmed.length() > 50 ? trimmed.substring(0, 47) + "..." : trimmed;
                    setText(preview);
                    setStyle("-fx-text-fill: #2B2625; -fx-font-weight: 500; -fx-alignment: CENTER-LEFT;");
                    Tooltip tip = new Tooltip("Employer Feedback:\n" + item.trim());
                    tip.setWrapText(true);
                    tip.setMaxWidth(400);
                    tip.setStyle("-fx-font-size: 12px; -fx-background-color: #2B2625; -fx-text-fill: #FDFBFA; -fx-padding: 8;");
                    setTooltip(tip);
                }
            }
        });
        table.getColumns().add(remarksCol);

        ComboBox<String> status = Ui.combo(List.of("All statuses", "Pending", "Under Review", "Shortlisted", "Rejected", "Accepted"), s -> s);
        status.getSelectionModel().selectFirst();
        TextField search = Ui.field("Search by opportunity, company, status or remarks...");
        Runnable filter = () -> {
            byId.clear();
            opportunities.clear();
            career.allOpportunities().forEach(o -> {
                byId.put(o.getOpportunityId(), o);
                opportunities.put(o.getOpportunityId(), o.getTitle());
            });
            employers.clear();
            employers.putAll(directory.employerNames());

            recLetters.getLettersForStudentAsync(sid).thenAccept(letters -> Platform.runLater(() -> {
                lettersByApp.clear();
                for (RecommendationLetter l : letters) {
                    if (l.getApplicationId() != null) {
                        lettersByApp.put(l.getApplicationId(), l);
                    }
                }
                String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
                String selectedStatus = status.getValue();
                table.setItems(FXCollections.observableArrayList(career.applicationsByStudent(sid).stream().filter(a ->
                        ("All statuses".equals(selectedStatus) || selectedStatus.equalsIgnoreCase(a.getStatus()))
                                && (q.isBlank()
                                || String.valueOf(opportunities.getOrDefault(a.getOpportunityId(), "")).toLowerCase(Locale.ROOT).contains(q)
                                || companyFor(a, byId, employers).toLowerCase(Locale.ROOT).contains(q)
                                || String.valueOf(a.getRemarks()).toLowerCase(Locale.ROOT).contains(q))).toList()));
                table.refresh();
            }));
        };
        filter.run();
        search.textProperty().addListener((obs, old, value) -> filter.run());
        status.valueProperty().addListener((obs, old, value) -> filter.run());

        // Row double-click opens application & feedback details
        table.setRowFactory(tv -> {
            TableRow<Application> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Application sel = row.getItem();
                    showStudentApplicationDetailsModal(sel, byId.get(sel.getOpportunityId()),
                            companyFor(sel, byId, employers), lettersByApp.get(sel.getApplicationId()));
                }
            });
            return row;
        });

        Button viewDetailsBtn = new Button("View Details");
        viewDetailsBtn.getStyleClass().add("primary-button");
        viewDetailsBtn.setStyle("-fx-text-fill: #FDFBFA;");
        viewDetailsBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        viewDetailsBtn.setOnAction(e -> {
            Application sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                Alerts.error("Select an application to view.");
                return;
            }
            showStudentApplicationDetailsModal(sel, byId.get(sel.getOpportunityId()),
                    companyFor(sel, byId, employers), lettersByApp.get(sel.getApplicationId()));
        });

        Button viewLetterBtn = new Button("View Recommendation Letter");
        viewLetterBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        viewLetterBtn.setOnAction(e -> {
            Application sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                Alerts.error("Select an application to view.");
                return;
            }
            RecommendationLetter l = lettersByApp.get(sel.getApplicationId());
            if (l == null) {
                Alerts.info("No faculty recommendation letter was attached to this application.");
                return;
            }
            InstructorViews.showViewLetterModal(l);
        });

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> filter.run());

        return Ui.page("My Applications", "Follow the status and view employer feedback on every application you have submitted.",
                Ui.toolbar(new Label("Search:"), search, new Label("Filter:"), status, viewDetailsBtn, viewLetterBtn, refresh), table);
    }

    public static void showStudentApplicationDetailsModal(Application app, JobOpportunity opportunity, String companyName, RecommendationLetter recLetter) {
        if (app == null) {
            Alerts.error("Select an application to view.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Application Details & Employer Feedback");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStylesheets().add(Ui.class.getResource("/com/nextit/css/application.css").toExternalForm());
        dialog.getDialogPane().setStyle("-fx-background-color: #FDFBFA;");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(14);
        box.setPrefWidth(520);
        box.setPadding(new Insets(16, 20, 16, 20));

        // 1. Header with Opportunity & Company
        VBox top = new VBox(4);
        String oppTitle = opportunity != null ? opportunity.getTitle() : "Opportunity #" + app.getOpportunityId();
        Label title = new Label(oppTitle);
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 800; -fx-text-fill: #9E0B0F;");
        Label comp = new Label("Company: " + (companyName != null && !companyName.isBlank() ? companyName : "Hiring Partner"));
        comp.setStyle("-fx-font-size: 13px; -fx-text-fill: #414141; -fx-font-weight: 600;");
        top.getChildren().addAll(title, comp);

        // 2. Application status details card
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-background-radius: 8;");

        grid.add(new Label("Application Status:"), 0, 0);
        String st = app.getStatus() == null ? "Pending" : app.getStatus();
        Label stBadge = Ui.badge(st, badgeClassFor(st));
        grid.add(stBadge, 1, 0);

        grid.add(new Label("Date Submitted:"), 0, 1);
        Label dateLbl = new Label(app.getApplicationDate() != null ? Ui.formatDate(app.getApplicationDate().toLocalDate()) : "—");
        dateLbl.setStyle("-fx-font-weight: 700;");
        grid.add(dateLbl, 1, 1);

        grid.add(new Label("Faculty Endorsement:"), 0, 2);
        Label endLbl = new Label(recLetter != null ? "✔ Verified Faculty Endorsed" : "— None attached");
        endLbl.setStyle(recLetter != null ? "-fx-text-fill: #1E6B35; -fx-font-weight: 700;" : "-fx-text-fill: #8C827A;");
        grid.add(endLbl, 1, 2);

        grid.add(new Label("Employer Review Status:"), 0, 3);
        String reviewedStr;
        if (app.getReviewedAt() != null) {
            reviewedStr = "Reviewed on " + Ui.formatDate(app.getReviewedAt().toLocalDate());
        } else if ("Pending".equalsIgnoreCase(st)) {
            reviewedStr = "Awaiting employer review";
        } else {
            reviewedStr = "Under evaluation";
        }
        Label revLbl = new Label(reviewedStr);
        revLbl.setStyle("-fx-font-weight: 600; -fx-text-fill: #6E6862;");
        grid.add(revLbl, 1, 3);

        // 3. Status Guidance Pill / Explanatory Banner
        VBox statusNoteBanner = new VBox(4);
        statusNoteBanner.setPadding(new Insets(10, 12, 10, 12));
        Label statusNoteTitle = new Label();
        statusNoteTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px;");
        Label statusNoteDesc = new Label();
        statusNoteDesc.setWrapText(true);
        statusNoteDesc.setStyle("-fx-font-size: 11.5px;");

        if ("Accepted".equalsIgnoreCase(st)) {
            statusNoteBanner.setStyle("-fx-background-color: #F4FAF5; -fx-border-color: #BDE4C7; -fx-border-radius: 8; -fx-background-radius: 8;");
            statusNoteTitle.setText("🎉 Congratulations! Application Accepted");
            statusNoteTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #1E6B35;");
            statusNoteDesc.setText("The employer has accepted your application! Check the feedback section below for any onboarding or next-step instructions.");
            statusNoteDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #225930;");
        } else if ("Shortlisted".equalsIgnoreCase(st)) {
            statusNoteBanner.setStyle("-fx-background-color: #FEF9E7; -fx-border-color: #EBD39E; -fx-border-radius: 8; -fx-background-radius: 8;");
            statusNoteTitle.setText("⭐ Application Shortlisted");
            statusNoteTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #996800;");
            statusNoteDesc.setText("Great job! Your profile has been shortlisted for an interview or technical evaluation round.");
            statusNoteDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #735300;");
        } else if ("Rejected".equalsIgnoreCase(st)) {
            statusNoteBanner.setStyle("-fx-background-color: #FFF5F5; -fx-border-color: #F0B8B8; -fx-border-radius: 8; -fx-background-radius: 8;");
            statusNoteTitle.setText("Application Not Selected");
            statusNoteTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #9E0B0F;");
            statusNoteDesc.setText("This application was not selected. Read the constructive remarks from the employer below to help refine your future applications.");
            statusNoteDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #732023;");
        } else if ("Under Review".equalsIgnoreCase(st) || "Reviewed".equalsIgnoreCase(st)) {
            statusNoteBanner.setStyle("-fx-background-color: #F6F8FA; -fx-border-color: #D3DFE8; -fx-border-radius: 8; -fx-background-radius: 8;");
            statusNoteTitle.setText("🔍 Application Under Review");
            statusNoteTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #2E5677;");
            statusNoteDesc.setText("The hiring partner is actively assessing your credentials, portfolio, and project experience.");
            statusNoteDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #3E6585;");
        } else {
            statusNoteBanner.setStyle("-fx-background-color: #FAF8F5; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-background-radius: 8;");
            statusNoteTitle.setText("⏳ Application Pending");
            statusNoteTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #6E6862;");
            statusNoteDesc.setText("Your application was received and is currently in the employer queue awaiting initial screening.");
            statusNoteDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6E6862;");
        }
        statusNoteBanner.getChildren().addAll(statusNoteTitle, statusNoteDesc);

        // 4. Employer Remarks & Feedback Card
        VBox remBox = new VBox(8);
        remBox.setStyle("-fx-background-color: #FAF8F5; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");
        Label remTitle = new Label("💬 Employer Remarks & Feedback");
        remTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12.5px; -fx-text-fill: #9E0B0F;");

        VBox remContentCard = new VBox(4);
        remContentCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EFE2CA; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        Label remContent = new Label();
        remContent.setWrapText(true);
        if (app.getRemarks() != null && !app.getRemarks().isBlank()) {
            remContent.setText(app.getRemarks());
            remContent.setStyle("-fx-font-size: 13px; -fx-text-fill: #2B2625; -fx-line-spacing: 2.5px;");
            remContentCard.getChildren().add(remContent);
            if (app.getReviewedAt() != null) {
                Label timeLbl = new Label("Provided on " + Ui.formatDate(app.getReviewedAt().toLocalDate()));
                timeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #8C827A; -fx-font-style: italic;");
                remContentCard.getChildren().add(timeLbl);
            }
        } else {
            remContent.setText("No remarks yet. The employer has not provided written feedback for this application.");
            remContent.setStyle("-fx-font-size: 12px; -fx-text-fill: #8C827A; -fx-font-style: italic;");
            remContentCard.getChildren().add(remContent);
        }
        remBox.getChildren().addAll(remTitle, remContentCard);

        box.getChildren().addAll(top, grid, statusNoteBanner, remBox);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    private static String badgeClassFor(String status) {
        if (status == null) return "badge-pill-cream";
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "accepted" -> "badge-pill-mint";
            case "shortlisted" -> "badge-pill-yellow";
            case "rejected" -> "badge-pill-pink";
            case "under review", "reviewed" -> "badge-pill-blue";
            default -> "badge-pill-cream";
        };
    }

    public static void showRequestRecommendationDialog(int sid, Integer preselectedOppId, Runnable onComplete) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Request Faculty Recommendation Letter");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPrefWidth(500);
        form.setPadding(new Insets(10));

        // Header info
        VBox banner = new VBox(4);
        banner.setStyle("-fx-background-color: #FDFBFA; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-padding: 12;");
        Label bTitle = new Label("Request an Endorsement for Internships & Employment");
        bTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F; -fx-font-size: 13px;");
        Label bDesc = new Label("Your instructor will compose an official verified letter endorsing your skills, capstone project, and academic standing.");
        bDesc.setWrapText(true);
        bDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6E6862;");
        banner.getChildren().addAll(bTitle, bDesc);
        form.getChildren().add(banner);

        // Instructor ComboBox
        List<Instructor> associated = academic.instructorsForStudent(sid);
        Set<Integer> associatedIds = associated.stream().map(Instructor::getInstructorId).collect(java.util.stream.Collectors.toSet());
        List<Instructor> instructors = new ArrayList<>(directory.instructors());
        instructors.sort((a, b) -> {
            boolean aAssoc = associatedIds.contains(a.getInstructorId());
            boolean bAssoc = associatedIds.contains(b.getInstructorId());
            if (aAssoc && !bAssoc) return -1;
            if (!aAssoc && bAssoc) return 1;
            return Integer.compare(a.getInstructorId(), b.getInstructorId());
        });
        Map<Integer, String> instNames = directory.instructorNames();
        ComboBox<Instructor> instPicker = Ui.combo(instructors, i -> {
            boolean isCourseFaculty = associatedIds.contains(i.getInstructorId());
            return String.format("Prof. %s%s (%s • %s)",
                    instNames.getOrDefault(i.getInstructorId(), "Instructor #" + i.getInstructorId()),
                    isCourseFaculty ? " [Course Faculty]" : "",
                    i.getDepartment() == null ? "CS/IT" : i.getDepartment(),
                    i.getSpecialization() == null ? "Software" : i.getSpecialization());
        });
        instPicker.getSelectionModel().selectFirst();

        // Opportunity ComboBox
        List<JobOpportunity> opportunities = career.allOpportunities();
        ComboBox<String> oppPicker = new ComboBox<>();
        oppPicker.getItems().add("General Endorsement (All Opportunities)");
        Map<String, Integer> oppMap = new java.util.HashMap<>();
        for (JobOpportunity o : opportunities) {
            String label = String.format("[%s] %s (%s)", o.getOpportunityType(), o.getTitle(), o.getLocation() == null ? "Remote" : o.getLocation());
            oppPicker.getItems().add(label);
            oppMap.put(label, o.getOpportunityId());
        }
        oppPicker.getSelectionModel().selectFirst();
        if (preselectedOppId != null) {
            opportunities.stream().filter(o -> o.getOpportunityId() == preselectedOppId).findFirst().ifPresent(o -> {
                String label = String.format("[%s] %s (%s)", o.getOpportunityType(), o.getTitle(), o.getLocation() == null ? "Remote" : o.getLocation());
                oppPicker.getSelectionModel().select(label);
            });
        }

        TextField titleField = Ui.field("Letter Title (e.g. Faculty Recommendation for Java Backend Role)");
        titleField.setText("Faculty Recommendation for " + (preselectedOppId != null ? "Target Opportunity" : "Internship / Employment"));

        TextArea noteArea = Ui.area("Tell your instructor what competencies, achievements, or projects to highlight...");
        noteArea.setPrefRowCount(4);

        form.getChildren().addAll(
                new Label("Select Faculty Instructor:"), instPicker,
                new Label("Target Opportunity:"), oppPicker,
                new Label("Request Title:"), titleField,
                new Label("Student Request Notes (Optional):"), noteArea
        );

        dialog.getDialogPane().setContent(form);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Submit Request");
        okBtn.getStyleClass().add("primary-button");

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            Instructor chosenInst = instPicker.getValue();
            if (chosenInst == null) {
                Alerts.error("Please select an instructor.");
                event.consume();
                return;
            }
            String title = titleField.getText();
            if (title == null || title.isBlank()) {
                Alerts.error("Please enter a title for the recommendation request.");
                event.consume();
                return;
            }
            Integer chosenOpp = oppMap.get(oppPicker.getValue());
            try {
                recLetters.requestRecommendation(sid, chosenInst.getInstructorId(), chosenOpp, title, noteArea.getText());
                Alerts.info("Recommendation request submitted to Prof. " + instNames.getOrDefault(chosenInst.getInstructorId(), "Instructor") + "!");
                if (onComplete != null) onComplete.run();
            } catch (Exception ex) {
                Alerts.error("Failed to request recommendation: " + ex.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private static String companyFor(Application a, Map<Integer, JobOpportunity> byId, Map<Integer, String> employers) {
        JobOpportunity o = byId.get(a.getOpportunityId());
        return o == null ? "" : employers.getOrDefault(o.getEmployerId(), "");
    }

    private static void guard(Runnable action) {
        try {
            action.run();
        } catch (AppException e) {
            Alerts.error(e.getMessage());
        } catch (Exception e) {
            Alerts.error("Could not complete the operation. Please check the database connection.");
            org.slf4j.LoggerFactory.getLogger(StudentViews.class).error("Student action failed", e);
        }
    }
}