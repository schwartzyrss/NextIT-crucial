package com.nextit.controller;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.security.Session;
import com.nextit.service.*;
import com.nextit.util.Alerts;
import com.nextit.util.Ui;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class InstructorViews {

    private static final AcademicService academic = new AcademicService();
    private static final ProjectService projects = new ProjectService();
    private static final PortfolioService portfolios = new PortfolioService();
    private static final SupportService support = new SupportService();
    private static final StudentSkillService skills = new StudentSkillService();
    private static final DirectoryService directory = new DirectoryService();
    private static final ProfileService profiles = new ProfileService();
    private static final RecommendationLetterService recLetters = new RecommendationLetterService();
    private static final CareerService career = new CareerService();
    private static final PasswordResetService resetRequests = PasswordResetService.getInstance();

    private static final List<String> ACTIVITY_TYPES =
            List.of("Quiz", "Examination", "Laboratory", "Project", "Assignment", "Recitation", "Other");
    private static final List<String> PROFICIENCY_LEVELS =
            List.of("Beginner", "Intermediate", "Advanced", "Expert");

    private InstructorViews() {}

    private static int instructorId() {
        return directory.instructors().stream()
                .filter(i -> i.getUserId() == Session.currentUser().getUserId())
                .map(Instructor::getInstructorId)
                .findFirst()
                .orElseGet(() -> {
                    Instructor inst = new Instructor();
                    inst.setUserId(Session.currentUser().getUserId());
                    inst.setEmployeeNumber("EMP-" + String.format("%04d", Session.currentUser().getUserId()));
                    inst.setDepartment("Computer Science");
                    inst.setSpecialization("Software Engineering");
                    new com.nextit.repository.JdbcInstructorRepository().save(inst);
                    return inst.getInstructorId();
                });
    }

    public static Node profile() {
        User account = profiles.account(Session.currentUser().getUserId());
        Instructor instructor = profiles.instructorForUser(account.getUserId())
                .orElseThrow(() -> new AppException("Instructor profile not found."));

        HBox hero = new HBox(20);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("profile-hero-card");

        StackPane avatar = Ui.avatar(Ui.initials(account.getFullName()), "avatar-mint");

        VBox identityDetails = new VBox(6);
        Label nameLabel = new Label(value(account.getFullName()));
        nameLabel.getStyleClass().add("profile-hero-name");

        HBox badges = new HBox(8);
        badges.setAlignment(Pos.CENTER_LEFT);
        badges.getChildren().addAll(
                Ui.badge("@" + account.getUsername(), "badge-pill-cream"),
                Ui.badge(String.valueOf(account.getRole()), "badge-pill-mint"),
                Ui.badge(value(instructor.getDepartment()), "badge-pill-lavender")
        );
        identityDetails.getChildren().addAll(nameLabel, badges);

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        VBox heroRight = new VBox(6);
        heroRight.setAlignment(Pos.CENTER_RIGHT);
        Label deptHighlight = new Label(value(instructor.getDepartment()));
        deptHighlight.getStyleClass().add("profile-hero-highlight-title");
        Label specHighlight = new Label(value(instructor.getSpecialization()));
        specHighlight.getStyleClass().add("profile-hero-highlight-subtitle");
        heroRight.getChildren().addAll(deptHighlight, specHighlight);

        hero.getChildren().addAll(avatar, identityDetails, heroSpacer, heroRight);

        // Account tray
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
        accountGrid.add(Ui.propertyTile("Account Role", Ui.badge(String.valueOf(account.getRole()), "badge-pill-mint")), 1, 1);

        VBox accountTray = Ui.tray("Account Details", "Your personal login and credentials in NextIT", accountGrid);
        HBox.setHgrow(accountTray, Priority.ALWAYS);

        // Department tray
        GridPane deptGrid = new GridPane();
        deptGrid.setHgap(16);
        deptGrid.setVgap(14);
        deptGrid.getColumnConstraints().addAll(col1, col2);

        deptGrid.add(Ui.propertyTile("Employee Number", value(instructor.getEmployeeNumber())), 0, 0);
        deptGrid.add(Ui.propertyTile("Department", value(instructor.getDepartment())), 1, 0);
        deptGrid.add(Ui.propertyTile("Specialization", value(instructor.getSpecialization())), 0, 1);
        deptGrid.add(Ui.propertyTile("Status", Ui.badge("Active Faculty", "badge-pill-mint")), 1, 1);

        VBox deptTray = Ui.tray("Faculty & Department", "Academic department assignment and subject specialization", deptGrid);
        HBox.setHgrow(deptTray, Priority.ALWAYS);

        HBox traysRow = new HBox(20, accountTray, deptTray);

        HBox notice = new HBox(12);
        notice.getStyleClass().add("profile-notice-cushion");
        Label noticeIcon = new Label("ℹ");
        noticeIcon.getStyleClass().add("profile-notice-icon");
        Label noticeText = new Label("Faculty assignments and instructor records are managed by the academic administration.");
        noticeText.getStyleClass().add("profile-notice-text");
        notice.getChildren().addAll(noticeIcon, noticeText);

        return Ui.page("My Profile", "Your account and instructor profile information. Contact the department registrar if any details need correction.",
                hero, traysRow, notice);
    }

    public static Node students() {
        int iid = instructorId();
        Map<Integer, String> names = directory.studentNames();
        Map<Integer, Subject> subjectMap = directory.subjectMap();
        List<Subject> mySubjects = directory.subjects().stream()
                .filter(s -> s.getInstructorId() != null && s.getInstructorId() == iid)
                .toList();

        TableView<Student> table = Ui.table();
        Ui.addColumn(table, "Student No.", Student::getStudentNumber);
        Ui.addColumn(table, "Name", s -> names.getOrDefault(s.getStudentId(), "Student #" + s.getStudentId()));
        Ui.addColumn(table, "Program", Student::getProgram);
        Ui.addColumn(table, "Year", s -> String.valueOf(s.getYearLevel()));
        Ui.addColumn(table, "Section", Student::getSection);
        Ui.addColumn(table, "Enrolled Subjects", s -> {
            List<Enrollment> enrs = academic.enrollmentsForStudent(s.getStudentId());
            return enrs.stream()
                    .map(e -> {
                        Subject sub = subjectMap.get(e.getSubjectId());
                        return sub != null ? sub.getSubjectCode() : "SUBJ-" + e.getSubjectId();
                    })
                    .collect(Collectors.joining(", "));
        });
        Ui.addColumn(table, "Status", Student::getEnrollmentStatus);

        ComboBox<String> subjectFilter = new ComboBox<>();
        subjectFilter.getItems().add("All Enrolled Students");
        subjectFilter.getItems().add("My Enrolled Students (All My Subjects)");
        Map<String, Integer> subjectFilterMap = new HashMap<>();
        List<Subject> filterSubjects = mySubjects.isEmpty() ? directory.subjects() : mySubjects;
        for (Subject s : filterSubjects) {
            String label = s.getSubjectCode() + " - " + s.getSubjectName() + (s.getSection() != null ? " (" + s.getSection() + ")" : "");
            subjectFilter.getItems().add(label);
            subjectFilterMap.put(label, s.getSubjectId());
        }
        subjectFilter.getSelectionModel().selectFirst();

        TextField search = Ui.field("Search by name, student number, program or section...");
        Runnable filter = () -> {
            names.clear();
            names.putAll(directory.studentNames());
            subjectMap.clear();
            subjectMap.putAll(directory.subjectMap());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String selectedFilter = subjectFilter.getValue();
            Integer filterSubjectId = subjectFilterMap.get(selectedFilter);

            List<Student> baseList;
            if (filterSubjectId != null) {
                baseList = academic.studentsForInstructorAndSubject(iid, filterSubjectId);
                if (baseList.isEmpty()) {
                    Set<Integer> sids = academic.enrollmentsForSubject(filterSubjectId).stream().map(Enrollment::getStudentId).collect(Collectors.toSet());
                    baseList = directory.students().stream().filter(s -> sids.contains(s.getStudentId())).toList();
                }
            } else if ("My Enrolled Students (All My Subjects)".equals(selectedFilter)) {
                baseList = academic.studentsForInstructor(iid);
                if (baseList.isEmpty()) baseList = directory.students();
            } else {
                baseList = directory.students();
            }

            table.setItems(FXCollections.observableArrayList(baseList.stream().filter(s ->
                    names.getOrDefault(s.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getStudentNumber()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getProgram()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getSection()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        filter.run();
        search.textProperty().addListener((obs, old, value) -> filter.run());
        subjectFilter.valueProperty().addListener((obs, old, value) -> filter.run());

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> filter.run());
        Button recommendBtn = Ui.button("✍ Recommend Student", "primary-button");
        recommendBtn.setOnAction(e -> {
            Student sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                Alerts.error("Select a student to compose a recommendation letter.");
                return;
            }
            showComposeRecommendationModal(null, null, sel.getStudentId(), filter);
        });
        Button exportCsv = Ui.button("📥 Export CSV", "clay-btn-secondary");
        exportCsv.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToCsv(table, "students_roster.csv", "Enrolled Students Roster", exportCsv.getScene().getWindow()));
        Button exportReport = Ui.button("📄 Print / PDF", "clay-btn-secondary");
        exportReport.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToHtmlReport(table, "students_roster_report.html", "Enrolled Students Roster", exportReport.getScene().getWindow()));
        return Ui.page("Students & Roster", "Browse students enrolled across your subjects with section and multi-course tracking.",
                Ui.toolbar(new Label("Filter by Subject:"), subjectFilter, new Label("Search:"), search, recommendBtn, refresh, exportCsv, exportReport), table);
    }

    public static Node portfolios() {
        Map<Integer, String> studentNames = directory.studentNames();
        TableView<Portfolio> table = Ui.table();
        Ui.addColumn(table, "Student", p -> studentNames.getOrDefault(p.getStudentId(), "Student #" + p.getStudentId()));
        Ui.addColumn(table, "Headline", Portfolio::getHeadline);
        Ui.addColumn(table, "Career Goal", Portfolio::getCareerGoal);
        Ui.addColumn(table, "Visibility", Portfolio::getVisibility);
        Ui.addColumn(table, "Updated", p -> p.getUpdatedAt() == null ? "" : p.getUpdatedAt().toLocalDate().toString());

        TextField search = Ui.field("Search by student, headline or career goal...");
        Runnable filter = () -> {
            studentNames.clear();
            studentNames.putAll(directory.studentNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(portfolios.approvedPortfolios().stream()
                    .filter(p -> q.isBlank()
                            || studentNames.getOrDefault(p.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || value(p.getHeadline()).toLowerCase(Locale.ROOT).contains(q)
                            || value(p.getCareerGoal()).toLowerCase(Locale.ROOT).contains(q))
                    .toList()));
        };
        filter.run();
        search.textProperty().addListener((obs, old, value) -> filter.run());

        Button view = new Button("View Portfolio");
        view.getStyleClass().add("primary-button");
        view.setOnAction(e -> {
            Portfolio selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a portfolio to view."); return; }
            guard(() -> PortfolioDetailsView.show(selected, true));
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> filter.run());
        return Ui.page("Student Portfolios",
                "Browse the portfolios that have been approved or made public by students.",
                Ui.toolbar(new Label("Search:"), search, view, refresh), table);
    }

    public static Node subjects() {
        int iid = instructorId();
        Map<Integer, String> instructors = directory.instructorNames();
        TableView<Subject> table = Ui.table();
        Ui.addColumn(table, "Code", Subject::getSubjectCode);
        Ui.addColumn(table, "Subject", Subject::getSubjectName);
        Ui.addColumn(table, "Units", s -> String.valueOf(s.getUnits()));
        Ui.addColumn(table, "Section", s -> s.getSection() != null ? s.getSection() : "A");
        Ui.addColumn(table, "Semester", s -> s.getSemester() != null ? s.getSemester() : "1st Semester");
        Ui.addColumn(table, "Academic Year", s -> s.getAcademicYear() != null ? s.getAcademicYear() : "2025-2026");
        Ui.addColumn(table, "Assigned Instructor", s -> s.getInstructorId() != null ? instructors.getOrDefault(s.getInstructorId(), "Instructor #" + s.getInstructorId()) : "Unassigned");
        Ui.addColumn(table, "Enrolled Roster", s -> String.valueOf(academic.enrollmentsForSubject(s.getSubjectId()).size()) + " students");

        TextField search = Ui.field("Search by code, subject name, section or instructor...");
        ComboBox<String> viewScope = Ui.combo(List.of("All Subjects", "My Assigned Subjects"), s -> s);
        viewScope.getSelectionModel().select("My Assigned Subjects");

        Runnable filter = () -> {
            instructors.clear();
            instructors.putAll(directory.instructorNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String scope = viewScope.getValue();
            boolean onlyMine = "My Assigned Subjects".equals(scope);

            table.setItems(FXCollections.observableArrayList(directory.subjects().stream()
                    .filter(s -> !onlyMine || (s.getInstructorId() != null && s.getInstructorId() == iid))
                    .filter(s -> q.isBlank()
                            || String.valueOf(s.getSubjectCode()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getSubjectName()).toLowerCase(Locale.ROOT).contains(q)
                            || (s.getSection() != null && s.getSection().toLowerCase(Locale.ROOT).contains(q))
                            || (s.getInstructorId() != null && instructors.getOrDefault(s.getInstructorId(), "").toLowerCase(Locale.ROOT).contains(q)))
                    .toList()));
        };
        filter.run();
        search.textProperty().addListener((obs, old, value) -> filter.run());
        viewScope.valueProperty().addListener((obs, old, value) -> filter.run());

        Button addSubjectBtn = Ui.button("➕ Add Subject / Section", "primary-button");
        addSubjectBtn.setOnAction(e -> showSubjectDialog(null, iid, filter));

        Button editSubjectBtn = new Button("Edit Subject");
        editSubjectBtn.setOnAction(e -> {
            Subject sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) { Alerts.error("Select a subject to edit."); return; }
            showSubjectDialog(sel, sel.getInstructorId() != null ? sel.getInstructorId() : iid, filter);
        });

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> filter.run());

        return Ui.page("Subjects & Course Management", "Manage your assigned subjects, sections, academic terms, and student rosters.",
                Ui.toolbar(new Label("Scope:"), viewScope, new Label("Search:"), search, addSubjectBtn, editSubjectBtn, refresh), table);
    }

    private static void showSubjectDialog(Subject existing, int defaultInstructorId, Runnable onComplete) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Subject / Section" : "Edit Subject / Section");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField code = Ui.field("Subject code (e.g. IT301, IT-311)");
        code.setText(existing == null ? "" : existing.getSubjectCode());
        TextField name = Ui.field("Subject name / title");
        name.setText(existing == null ? "" : existing.getSubjectName());
        TextField units = Ui.field("Units (e.g. 3.0)");
        units.setText(existing == null ? "3.0" : String.valueOf(existing.getUnits()));
        TextField section = Ui.field("Section (e.g. A, B, CS-3A)");
        section.setText(existing == null ? "A" : (existing.getSection() != null ? existing.getSection() : "A"));
        TextField academicYear = Ui.field("Academic year (e.g. 2025-2026)");
        academicYear.setText(existing == null || existing.getAcademicYear() == null ? "2025-2026" : existing.getAcademicYear());
        ComboBox<String> semester = Ui.combo(List.of("1st Semester", "2nd Semester", "Summer Term"), s -> s);
        if (existing != null && existing.getSemester() != null) semester.getSelectionModel().select(existing.getSemester());
        else semester.getSelectionModel().selectFirst();

        List<Instructor> instructorList = directory.instructors();
        Map<Integer, String> instNames = directory.instructorNames();
        ComboBox<Instructor> instPicker = Ui.combo(instructorList, i -> "Prof. " + instNames.getOrDefault(i.getInstructorId(), "Instructor #" + i.getInstructorId()));
        instructorList.stream().filter(i -> i.getInstructorId() == defaultInstructorId).findFirst().ifPresent(instPicker.getSelectionModel()::select);

        TextArea description = Ui.area("Course description");
        description.setText(existing == null ? "" : existing.getDescription());
        description.setPrefRowCount(3);

        Node form = Ui.form(
                new String[]{"Subject code", "Subject title", "Units", "Section", "Academic year", "Semester", "Assigned instructor", "Description"},
                new Node[]{code, name, units, section, academicYear, semester, instPicker, description});

        dialog.getDialogPane().setContent(form);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText(existing == null ? "Create Subject" : "Update Subject");
        ok.getStyleClass().add("primary-button");

        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            String codeVal = code.getText() == null ? "" : code.getText().trim();
            String nameVal = name.getText() == null ? "" : name.getText().trim();
            if (codeVal.isBlank() || nameVal.isBlank()) {
                Alerts.error("Subject code and title are required.");
                return;
            }
            double u;
            try {
                u = Double.parseDouble(units.getText().trim());
            } catch (Exception ex) {
                Alerts.error("Invalid units value.");
                return;
            }

            Subject s = existing == null ? new Subject() : existing;
            s.setSubjectCode(codeVal);
            s.setSubjectName(nameVal);
            s.setUnits(u);
            s.setSection(section.getText() != null ? section.getText().trim() : "A");
            s.setAcademicYear(academicYear.getText() != null ? academicYear.getText().trim() : "2025-2026");
            s.setSemester(semester.getValue());
            if (instPicker.getValue() != null) s.setInstructorId(instPicker.getValue().getInstructorId());
            s.setDescription(description.getText());

            try {
                com.nextit.repository.JdbcSubjectRepository repo = new com.nextit.repository.JdbcSubjectRepository();
                if (existing == null) repo.save(s);
                else repo.update(s);
                dialog.setResult(ButtonType.OK);
                dialog.close();
                Alerts.info("Subject " + s.getSubjectCode() + " saved.");
                if (onComplete != null) onComplete.run();
            } catch (Exception ex) {
                Alerts.error("Failed to save subject: " + ex.getMessage());
            }
        });

        dialog.showAndWait();
    }

    public static Node skills() {
        return SkillCatalogView.view();
    }

    public static Node achievementsAndCertifications() {
        return DevelopmentManagementViews.achievementsAndCertifications();
    }

    public static Node achievements() {
        return achievementsAndCertifications();
    }

    public static Node certifications() {
        return achievementsAndCertifications();
    }

    public static Node opportunities() {
        Map<Integer, String> employers = directory.employerNames();
        TableView<JobOpportunity> table = Ui.table("No opportunities found.");
        Ui.addColumn(table, "Title", JobOpportunity::getTitle);
        Ui.addColumn(table, "Employer", o -> employers.getOrDefault(o.getEmployerId(), "Employer #" + o.getEmployerId()));
        Ui.addColumn(table, "Type", JobOpportunity::getOpportunityType);
        Ui.addColumn(table, "Location", o -> value(o.getLocation()));
        Ui.addColumn(table, "Required Skills", o -> value(o.getRequiredSkills()));
        Ui.addColumn(table, "Deadline", o -> Ui.formatDate(o.getDeadline()));
        Ui.addColumn(table, "Posted", o -> Ui.formatDate(o.getCreatedAt() == null ? null : o.getCreatedAt().toLocalDate()));
        Ui.addColumn(table, "Status", JobOpportunity::getStatus);

        TextField search = Ui.field("Search by title, employer, skills or location...");
        ComboBox<String> statusFilter = Ui.combo(List.of("All statuses", "Open", "Closed", "Archived"), s -> s);
        statusFilter.getSelectionModel().selectFirst();
        Runnable refresh = () -> {
            employers.clear();
            employers.putAll(directory.employerNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String status = statusFilter.getValue();
            table.setItems(FXCollections.observableArrayList(career.allOpportunities().stream()
                    .filter(o -> "All statuses".equals(status) || status.equalsIgnoreCase(o.getStatus()))
                    .filter(o -> q.isBlank()
                            || value(o.getTitle()).toLowerCase(Locale.ROOT).contains(q)
                            || value(o.getLocation()).toLowerCase(Locale.ROOT).contains(q)
                            || value(o.getRequiredSkills()).toLowerCase(Locale.ROOT).contains(q)
                            || employers.getOrDefault(o.getEmployerId(), "").toLowerCase(Locale.ROOT).contains(q))
                    .toList()));
        };
        search.textProperty().addListener((obs, old, value) -> refresh.run());
        statusFilter.valueProperty().addListener((obs, old, value) -> refresh.run());

        Button details = new Button("View Details");
        details.setOnAction(e -> {
            JobOpportunity selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an opportunity to view."); return; }
            DevelopmentManagementViews.showOpportunity(selected, employers);
        });

        Button recommendBtn = Ui.button("✍ Recommend Student", "primary-button");
        recommendBtn.setOnAction(e -> {
            JobOpportunity selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an opportunity to recommend a student for."); return; }
            showComposeRecommendationModal(null, selected.getOpportunityId(), null, refresh);
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refresh.run());
        refresh.run();

        return Ui.page("Career Opportunities",
                "Browse internship and employment listings from industry partners and recommend qualified students.",
                Ui.toolbar(new Label("Search:"), search, statusFilter, details, recommendBtn, refreshButton), table);
    }

    public static Node recommendationLetters() {
        int iid = instructorId();
        Map<Integer, String> studentNames = new java.util.HashMap<>();
        Map<Integer, JobOpportunity> opportunities = new java.util.HashMap<>();

        TableView<RecommendationLetter> table = Ui.table("No recommendation letters found.");
        Ui.addColumn(table, "Student", l -> studentNames.getOrDefault(l.getStudentId(), "Student #" + l.getStudentId()));
        Ui.addColumn(table, "Target Opportunity", l -> {
            if (l.getOpportunityId() == null) return "General Endorsement (All Opportunities)";
            JobOpportunity o = opportunities.get(l.getOpportunityId());
            return o == null ? "Opportunity #" + l.getOpportunityId() : o.getTitle();
        });
        Ui.addColumn(table, "Opportunity Type", l -> {
            if (l.getOpportunityId() == null) return "General";
            JobOpportunity o = opportunities.get(l.getOpportunityId());
            return o == null ? "-" : o.getOpportunityType();
        });
        Ui.addColumn(table, "Letter Title", RecommendationLetter::getLetterTitle);
        Ui.addColumn(table, "Status", l -> l.getStatus() == null ? "REQUESTED" : l.getStatus());
        Ui.addColumn(table, "Date", l -> {
            if (l.getIssuedAt() != null) return Ui.formatDate(l.getIssuedAt().toLocalDate());
            return Ui.formatDate(l.getCreatedAt() != null ? l.getCreatedAt().toLocalDate() : null);
        });

        TextField search = Ui.field("Search by student, opportunity or title...");
        ComboBox<String> statusFilter = Ui.combo(
                List.of("All statuses", "REQUESTED", "ISSUED", "ATTACHED"), s -> s);
        statusFilter.getSelectionModel().selectFirst();

        Runnable refresh = () -> {
            studentNames.clear();
            studentNames.putAll(directory.studentNames());
            opportunities.clear();
            career.allOpportunities().forEach(o -> opportunities.put(o.getOpportunityId(), o));

            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String st = statusFilter.getValue();

            recLetters.getLettersForInstructorAsync(iid).thenAccept(list -> Platform.runLater(() -> {
                List<RecommendationLetter> filtered = list.stream()
                        .filter(l -> "All statuses".equals(st) || st.equalsIgnoreCase(l.getStatus()))
                        .filter(l -> {
                            if (q.isBlank()) return true;
                            String sName = studentNames.getOrDefault(l.getStudentId(), "").toLowerCase(Locale.ROOT);
                            String opp = l.getOpportunityId() == null ? "general endorsement"
                                    : (opportunities.containsKey(l.getOpportunityId()) ? opportunities.get(l.getOpportunityId()).getTitle().toLowerCase(Locale.ROOT) : "");
                            String title = l.getLetterTitle() == null ? "" : l.getLetterTitle().toLowerCase(Locale.ROOT);
                            String highlights = l.getEndorsementHighlights() == null ? "" : l.getEndorsementHighlights().toLowerCase(Locale.ROOT);
                            return sName.contains(q) || opp.contains(q) || title.contains(q) || highlights.contains(q);
                        })
                        .toList();
                table.setItems(FXCollections.observableArrayList(filtered));
            }));
        };

        search.textProperty().addListener((obs, old, val) -> refresh.run());
        statusFilter.valueProperty().addListener((obs, old, val) -> refresh.run());

        Button composeBtn = Ui.button("✍ Write Recommendation", "primary-button");
        composeBtn.setOnAction(e -> showComposeRecommendationModal(null, null, null, refresh));

        Button fulfillOrView = new Button("Open Letter / Request");
        fulfillOrView.setOnAction(e -> {
            RecommendationLetter sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                Alerts.error("Select a recommendation letter or request from the list.");
                return;
            }
            if ("REQUESTED".equalsIgnoreCase(sel.getStatus())) {
                showComposeRecommendationModal(sel, sel.getOpportunityId(), sel.getStudentId(), refresh);
            } else {
                showViewLetterModal(sel);
            }
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refresh.run());

        refresh.run();

        return Ui.page("Faculty Recommendation Letters",
                "Review student recommendation requests, compose formal academic endorsements, and issue signed letters for internships and employment opportunities.",
                Ui.toolbar(new Label("Search:"), search, new Label("Filter:"), statusFilter, composeBtn, fulfillOrView, refreshBtn),
                table);
    }

    public static void showComposeRecommendationModal(
            RecommendationLetter existingRequest,
            Integer preselectedOppId,
            Integer preselectedStudentId,
            Runnable onComplete) {
        int iid = instructorId();
        Instructor instructor = directory.instructors().stream()
                .filter(i -> i.getInstructorId() == iid)
                .findFirst()
                .orElse(null);
        User instructorUser = Session.currentUser();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existingRequest != null ? "Fulfill Recommendation Request" : "Compose Faculty Recommendation Letter");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox form = new VBox(14);
        form.setPrefWidth(580);
        form.setPadding(new Insets(10));

        // Faculty credentials banner
        VBox facultyCard = new VBox(4);
        facultyCard.setStyle("-fx-background-color: #FDFBFA; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");
        Label facName = new Label("Endorsing Faculty: " + (instructorUser != null ? instructorUser.getFullName() : "Faculty Member") +
                (instructor != null ? " (" + instructor.getDepartment() + " • " + instructor.getEmployeeNumber() + ")" : ""));
        facName.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F; -fx-font-size: 13px;");
        Label facStatus = new Label("✔ Institutional Verified Faculty Credential • College of Computer Studies");
        facStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6E6862;");
        facultyCard.getChildren().addAll(facName, facStatus);
        form.getChildren().add(facultyCard);

        // Student selection
        List<Student> studentList = directory.students();
        Map<Integer, String> studentNames = directory.studentNames();
        ComboBox<Student> studentPicker = Ui.combo(studentList, s -> studentNames.getOrDefault(s.getStudentId(), s.getStudentNumber()));
        if (existingRequest != null) {
            studentList.stream().filter(s -> s.getStudentId() == existingRequest.getStudentId()).findFirst().ifPresent(studentPicker.getSelectionModel()::select);
            studentPicker.setDisable(true);
        } else if (preselectedStudentId != null) {
            studentList.stream().filter(s -> s.getStudentId() == preselectedStudentId).findFirst().ifPresent(studentPicker.getSelectionModel()::select);
        } else {
            studentPicker.getSelectionModel().selectFirst();
        }

        // Opportunity selection
        List<JobOpportunity> oppList = career.allOpportunities();
        ComboBox<String> oppPicker = new ComboBox<>();
        oppPicker.getItems().add("General Endorsement (All Internship & Employment Listings)");
        Map<String, Integer> oppMap = new java.util.HashMap<>();
        for (JobOpportunity o : oppList) {
            String label = String.format("[%s] %s (%s)", o.getOpportunityType(), o.getTitle(), o.getLocation() == null ? "Remote" : o.getLocation());
            oppPicker.getItems().add(label);
            oppMap.put(label, o.getOpportunityId());
        }
        oppPicker.getSelectionModel().selectFirst();

        Integer targetOppId = existingRequest != null ? existingRequest.getOpportunityId() : preselectedOppId;
        if (targetOppId != null) {
            oppList.stream().filter(o -> o.getOpportunityId() == targetOppId).findFirst().ifPresent(o -> {
                String label = String.format("[%s] %s (%s)", o.getOpportunityType(), o.getTitle(), o.getLocation() == null ? "Remote" : o.getLocation());
                oppPicker.getSelectionModel().select(label);
            });
            if (existingRequest != null) {
                oppPicker.setDisable(true);
            }
        }

        // Letter Title
        TextField titleField = Ui.field("Letter Title (e.g. Official Faculty Recommendation for Software Internship)");
        if (existingRequest != null && existingRequest.getLetterTitle() != null) {
            titleField.setText(existingRequest.getLetterTitle());
        } else {
            titleField.setText("Official Faculty Academic & Technical Recommendation");
        }

        // Endorsement highlights
        TextField highlightsField = Ui.field("Key Technical & Capstone Highlights (e.g. Java concurrency, Cloud capstone leader, top 5% GPA)");
        if (existingRequest != null && existingRequest.getEndorsementHighlights() != null) {
            highlightsField.setText(existingRequest.getEndorsementHighlights());
        }

        // Recommendation Body Area
        TextArea bodyArea = Ui.area("Write structured recommendation letter endorsement...");
        bodyArea.setPrefRowCount(8);
        if (existingRequest != null && existingRequest.isIssued() && existingRequest.getRecommendationBody() != null) {
            bodyArea.setText(existingRequest.getRecommendationBody());
        } else {
            String studentNameStr = studentPicker.getValue() == null ? "the student" : studentNames.getOrDefault(studentPicker.getValue().getStudentId(), "the student");
            bodyArea.setText(String.format(
                    "To the Hiring and Selection Committee:\n\n" +
                    "It is my great pleasure to formally recommend %s for this opportunity. " +
                    "Throughout their coursework in our department, they have demonstrated exceptional diligence, technical problem-solving ability, and collaborative initiative.\n\n" +
                    "Their practical project contributions and grasp of software engineering principles make them an outstanding candidate who will deliver immediate value to your organization.\n\n" +
                    "Sincerely,\n%s\n%s\nNextIT Partner Institution",
                    studentNameStr,
                    instructorUser != null ? instructorUser.getFullName() : "Faculty Member",
                    instructor != null ? instructor.getDepartment() : "Department of Computer Science"
            ));
        }

        studentPicker.valueProperty().addListener((obs, old, val) -> {
            if (val != null && (bodyArea.getText().contains("the student") || (old != null && bodyArea.getText().contains(studentNames.getOrDefault(old.getStudentId(), ""))))) {
                String chosenName = studentNames.getOrDefault(val.getStudentId(), "the student");
                String oldName = old == null ? "the student" : studentNames.getOrDefault(old.getStudentId(), "the student");
                bodyArea.setText(bodyArea.getText().replace(oldName, chosenName));
            }
        });

        if (existingRequest != null && existingRequest.isPendingRequest() && existingRequest.getRecommendationBody() != null && !existingRequest.getRecommendationBody().isBlank()) {
            VBox requestNoteBox = new VBox(4);
            requestNoteBox.setStyle("-fx-background-color: #FEF9E7; -fx-border-color: #C6A75E; -fx-border-radius: 6; -fx-padding: 8;");
            Label noteHeader = new Label("Student Request Note:");
            noteHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #7D6608; -fx-font-size: 11px;");
            Label noteBody = new Label(existingRequest.getRecommendationBody());
            noteBody.setWrapText(true);
            noteBody.setStyle("-fx-font-size: 11px; -fx-text-fill: #414141;");
            requestNoteBox.getChildren().addAll(noteHeader, noteBody);
            form.getChildren().add(requestNoteBox);
        }

        form.getChildren().addAll(
                new Label("Selected Student Candidate:"), studentPicker,
                new Label("Target Opportunity:"), oppPicker,
                new Label("Recommendation Title:"), titleField,
                new Label("Verified Endorsement Highlights:"), highlightsField,
                new Label("Recommendation Letter Body:"), bodyArea
        );

        dialog.getDialogPane().setContent(form);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("✍ Sign & Issue Letter");
        okBtn.getStyleClass().add("primary-button");

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            Student selStudent = studentPicker.getValue();
            if (selStudent == null) {
                Alerts.error("Please select a student.");
                event.consume();
                return;
            }
            String title = titleField.getText();
            String body = bodyArea.getText();
            String highlights = highlightsField.getText();
            if (title == null || title.isBlank() || body == null || body.isBlank()) {
                Alerts.error("Please provide both a title and letter body.");
                event.consume();
                return;
            }

            Integer chosenOpp = oppMap.get(oppPicker.getValue());

            try {
                if (existingRequest != null) {
                    recLetters.issueRecommendation(existingRequest.getLetterId(), iid, title, body, highlights);
                } else {
                    recLetters.createProactiveRecommendation(iid, selStudent.getStudentId(), chosenOpp, title, body, highlights);
                }
                Alerts.info("Faculty recommendation letter successfully signed and issued.");
                if (onComplete != null) onComplete.run();
            } catch (Exception ex) {
                Alerts.error("Failed to issue recommendation letter: " + ex.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    public static void showViewLetterModal(RecommendationLetter letter) {
        Instructor inst = directory.instructors().stream()
                .filter(i -> i.getInstructorId() == letter.getInstructorId())
                .findFirst().orElse(null);
        User instUser = inst != null ? directory.users().stream().filter(u -> u.getUserId() == inst.getUserId()).findFirst().orElse(null) : null;
        String instName = instUser != null ? instUser.getFullName() : "Faculty Member #" + letter.getInstructorId();

        Student stud = directory.students().stream()
                .filter(s -> s.getStudentId() == letter.getStudentId())
                .findFirst().orElse(null);
        String studentName = directory.studentName(letter.getStudentId());

        JobOpportunity opp = letter.getOpportunityId() == null ? null : career.allOpportunities().stream()
                .filter(o -> o.getOpportunityId() == letter.getOpportunityId())
                .findFirst().orElse(null);

        VBox content = new VBox(16);
        content.setPrefWidth(550);
        content.setPadding(new Insets(10));

        // Academic Header Card
        VBox cardHeader = new VBox(6);
        cardHeader.setStyle("-fx-background-color: #FDFBFA; -fx-border-color: #EFE2CA; -fx-border-radius: 10; -fx-padding: 16;");
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label seal = Ui.badge("Verified Faculty Recommendation", "badge-pill-mint");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label dateLbl = new Label(letter.getIssuedAt() != null ? "Issued: " + Ui.formatDate(letter.getIssuedAt().toLocalDate()) : "");
        dateLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6E6862;");
        topRow.getChildren().addAll(seal, sp, dateLbl);

        Label letterTitleLbl = new Label(letter.getLetterTitle());
        letterTitleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #9E0B0F;");
        Label recFor = new Label("Candidate: " + studentName + (stud != null ? " (" + stud.getProgram() + " • Year " + stud.getYearLevel() + ")" : ""));
        recFor.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 600; -fx-text-fill: #414141;");

        Label oppLabel = new Label("Opportunity: " + (opp != null ? "[" + opp.getOpportunityType() + "] " + opp.getTitle() : "General Industry Endorsement"));
        oppLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6E6862;");

        cardHeader.getChildren().addAll(topRow, letterTitleLbl, recFor, oppLabel);
        content.getChildren().add(cardHeader);

        // Highlights Card
        if (letter.getEndorsementHighlights() != null && !letter.getEndorsementHighlights().isBlank()) {
            VBox highBox = new VBox(4);
            highBox.setStyle("-fx-background-color: #FEF9E7; -fx-border-color: #C6A75E; -fx-border-radius: 8; -fx-padding: 10;");
            Label hTitle = new Label("⭐ Key Competencies & Highlights");
            hTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #7D6608; -fx-font-size: 11.5px;");
            Label hText = new Label(letter.getEndorsementHighlights());
            hText.setWrapText(true);
            hText.setStyle("-fx-font-size: 12px; -fx-text-fill: #414141;");
            highBox.getChildren().addAll(hTitle, hText);
            content.getChildren().add(highBox);
        }

        // Formal Letter Body
        VBox bodyCard = new VBox(8);
        bodyCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-padding: 16;");
        Label bodyLbl = new Label(letter.getRecommendationBody());
        bodyLbl.setWrapText(true);
        bodyLbl.setStyle("-fx-font-size: 12.5px; -fx-line-spacing: 3px; -fx-text-fill: #2C2C2C; -fx-font-family: 'Georgia', serif;");
        bodyCard.getChildren().add(bodyLbl);
        content.getChildren().add(bodyCard);

        // Faculty Signature Block
        VBox sigBox = new VBox(3);
        sigBox.setStyle("-fx-padding: 8 0 0 8;");
        Label sigName = new Label("Digitally Endorsed by: Prof. " + instName);
        sigName.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F; -fx-font-size: 12px;");
        Label sigDept = new Label((inst != null ? inst.getDepartment() + " • " : "") + "NextIT Institutional Faculty");
        sigDept.setStyle("-fx-font-size: 11px; -fx-text-fill: #6E6862;");
        sigBox.getChildren().addAll(sigName, sigDept);
        content.getChildren().add(sigBox);

        Ui.viewDialog("Faculty Recommendation Letter", content);
    }

    public static Node projects() {
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> subjects = directory.subjectNames();
        TableView<Project> table = Ui.table();
        Ui.addColumn(table, "Title", Project::getProjectTitle);
        Ui.addColumn(table, "Student", p -> p.getStudentId() == null ? ""
                : students.getOrDefault(p.getStudentId(), "Student #" + p.getStudentId()));
        Ui.addColumn(table, "Type", Project::getProjectType);
        Ui.addColumn(table, "Subject", p -> p.getSubjectId() == null ? ""
                : subjects.getOrDefault(p.getSubjectId(), "Subject #" + p.getSubjectId()));
        Ui.addColumn(table, "Status", Project::getStatus);
        Ui.addColumn(table, "Technologies", Project::getTechnologiesUsed);
        Ui.addColumn(table, "Repository", Project::getRepositoryUrl);
        List<Project> all = projects.allProjects();
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search by title, student, subject, technology or status...");
        ComboBox<String> statusFilter = Ui.combo(
                List.of("All statuses", "Draft", "In Progress", "Completed", "Archived"), s -> s);
        statusFilter.getSelectionModel().selectFirst();
        Runnable filter = () -> {
            students.clear();
            students.putAll(directory.studentNames());
            subjects.clear();
            subjects.putAll(directory.subjectNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String status = statusFilter.getValue();
            table.setItems(FXCollections.observableArrayList(projects.allProjects().stream().filter(p ->
                    ("All statuses".equals(status) || status.equalsIgnoreCase(p.getStatus()))
                            && (q.isBlank()
                            || String.valueOf(p.getProjectTitle()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(p.getProjectType()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(p.getTechnologiesUsed()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(p.getStatus()).toLowerCase(Locale.ROOT).contains(q)
                            || (p.getStudentId() != null
                                && students.getOrDefault(p.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q))
                            || (p.getSubjectId() != null
                                && subjects.getOrDefault(p.getSubjectId(), "").toLowerCase(Locale.ROOT).contains(q)))).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> filter.run());
        statusFilter.valueProperty().addListener((obs, old, value) -> filter.run());

        Button view = new Button("View Details");
        view.getStyleClass().add("primary-button");
        view.setOnAction(e -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a project to review."); return; }
            guard(() -> showProjectReview(selected));
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> filter.run());
        return Ui.page("Student Projects", "Review the projects students have recorded, including group members and instructor evaluations.",
                Ui.toolbar(new Label("Search:"), search, statusFilter, view, refresh), table);
    }

    private static void showProjectReview(Project p) {
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> subjects = directory.subjectNames();
        Map<Integer, String> instructors = directory.instructorNames();
        Label description = new Label(value(p.getDescription()));
        description.setWrapText(true);

        StringBuilder memberText = new StringBuilder();
        projects.membersForProject(p.getProjectId()).forEach(m -> memberText.append("• ")
                .append(students.getOrDefault(m.getStudentId(), "Student #" + m.getStudentId()))
                .append(m.getRole() == null ? "" : " — " + m.getRole())
                .append(m.getContribution() == null ? "" : ": " + m.getContribution())
                .append('\n'));

        StringBuilder evaluationText = new StringBuilder(64);
        projects.allEvaluations().stream()
                .filter(e -> e.getProjectId() == p.getProjectId())
                .forEach(e -> evaluationText.append("• ")
                        .append(students.getOrDefault(e.getStudentId(), "Student #" + e.getStudentId()))
                        .append(" — overall ").append(e.getOverallScore() == null ? "n/a" : e.getOverallScore())
                        .append(", by ").append(instructors.getOrDefault(e.getInstructorId(),
                                "Instructor #" + e.getInstructorId()))
                        .append(e.getFeedback() == null ? "" : ": " + e.getFeedback())
                        .append('\n'));

        Node form = Ui.form(
                new String[]{"Title", "Owner", "Subject", "Type", "Status", "Technologies", "Repository",
                        "Demo", "Project date", "Description", "Group members", "Evaluations"},
                new Node[]{new Label(value(p.getProjectTitle())),
                        new Label(p.getStudentId() == null ? ""
                                : students.getOrDefault(p.getStudentId(), "Student #" + p.getStudentId())),
                        new Label(p.getSubjectId() == null ? ""
                                : subjects.getOrDefault(p.getSubjectId(), "Subject #" + p.getSubjectId())),
                        new Label(value(p.getProjectType())), new Label(value(p.getStatus())),
                        new Label(value(p.getTechnologiesUsed())), new Label(value(p.getRepositoryUrl())),
                        new Label(value(p.getDemoUrl())), new Label(Ui.formatDate(p.getProjectDate())),
                        description,
                        new Label(memberText.isEmpty() ? "No group members recorded." : memberText.toString()),
                        new Label(evaluationText.isEmpty() ? "No evaluations recorded." : evaluationText.toString())});
        Ui.viewDialog("Project - " + p.getProjectTitle(), form);
    }

    public static Node dashboard() {
        // 1. Hero cushion with status and live badges
        HBox hero = new HBox(16);
        hero.getStyleClass().add("page-header-cushion");
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox heroTitles = new VBox(4);
        heroTitles.getChildren().addAll(
                Ui.title("Instructor Dashboard"),
                Ui.subtitle("Welcome, " + Session.currentUser().getFullName() + ". Manage grades, attendance, evaluations, and student support.")
        );

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        HBox statusPillBox = new HBox(8);
        statusPillBox.setAlignment(Pos.CENTER_RIGHT);
        StackPane statusDot = Ui.statusDot(true);
        Label statusText = new Label("Faculty Portal Active");
        statusText.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F; -fx-font-size: 12px;");
        HBox liveChip = new HBox(8, statusDot, statusText);
        liveChip.setAlignment(Pos.CENTER);
        liveChip.getStyleClass().addAll("badge-pill", "badge-pill-mint");

        Label termChip = Ui.badge("AY 2026-2027 • 1st Sem", "badge-pill-lavender");
        statusPillBox.getChildren().addAll(liveChip, termChip);

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

        // 3. Main Dashboard Body: Left Work Area + Dedicated Right Side Panel
        HBox dashboardBody = new HBox(20);
        dashboardBody.setAlignment(Pos.TOP_LEFT);

        VBox leftColumn = new VBox(20);
        HBox.setHgrow(leftColumn, Priority.ALWAYS);

        VBox rightColumnHolder = new VBox();

        // Left Component A: Dual Charts Row
        HBox chartsRow = new HBox(14);
        chartsRow.setAlignment(Pos.TOP_LEFT);

        // Left Component B: Recent Student Grades Table Tray
        VBox gradesTray = new VBox(14);
        gradesTray.getStyleClass().add("clay-tray");
        gradesTray.setFillWidth(true);

        Map<Integer, Enrollment> enrollments = enrollmentIndex();
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> subjects = directory.subjectNames();

        TableView<Grade> gradeTable = Ui.table("No grade records found matching filter.");
        gradeTable.setPrefHeight(290);
        gradeTable.setMinHeight(220);

        Ui.addColumn(gradeTable, "Student", g -> studentLabel(g.getEnrollmentId(), enrollments, students));
        Ui.addColumn(gradeTable, "Subject", g -> subjectLabel(g.getEnrollmentId(), enrollments, subjects));
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
        Ui.addColumn(gradeTable, "Recorded", g -> g.getRecordedAt() == null ? "" : g.getRecordedAt().toLocalDate().toString());

        TextField searchGrades = Ui.field("Filter by student, subject, or activity...");
        searchGrades.setPrefWidth(240);

        ComboBox<String> typeFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All Types", "Quiz", "Examination", "Laboratory", "Project", "Assignment"));
        typeFilter.setValue("All Types");

        HBox trayHeader = new HBox(12);
        trayHeader.setAlignment(Pos.CENTER_LEFT);

        VBox trayTitles = new VBox(3);
        Label trayTitle = new Label("Recent Grade Entries");
        trayTitle.getStyleClass().add("tray-title");
        Label traySub = new Label("Student performance records and activity evaluations");
        traySub.getStyleClass().add("tray-subtitle");
        trayTitles.getChildren().addAll(trayTitle, traySub);

        Region traySpacer = new Region();
        HBox.setHgrow(traySpacer, Priority.ALWAYS);

        Button exportCsvBtn = Ui.button("📥 Export CSV", "clay-btn-secondary");
        exportCsvBtn.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToCsv(gradeTable, "instructor_grades.csv", "Student Grade Records", exportCsvBtn.getScene().getWindow()));

        Button exportReportBtn = Ui.button("📄 Print / PDF", "clay-btn-secondary");
        exportReportBtn.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToHtmlReport(gradeTable, "instructor_grades_report.html", "Student Grade Records", exportReportBtn.getScene().getWindow()));

        trayHeader.getChildren().addAll(trayTitles, traySpacer, searchGrades, typeFilter, exportCsvBtn, exportReportBtn);
        gradesTray.getChildren().addAll(trayHeader, gradeTable);

        leftColumn.getChildren().addAll(chartsRow, gradesTray);
        dashboardBody.getChildren().addAll(leftColumn, rightColumnHolder);

        // Self-refreshing dashboard logic
        final Runnable[] refreshHolder = new Runnable[1];
        Runnable refreshDashboard = () -> {
            List<Grade> allGrades = academic.allGrades();
            List<Attendance> allAttendance = academic.allAttendance();

            // Refresh Map Lookups
            enrollments.clear();
            enrollments.putAll(enrollmentIndex());
            students.clear();
            students.putAll(directory.studentNames());
            subjects.clear();
            subjects.putAll(directory.subjectNames());

            // 1. Update 8 Full-Width Stat Cards
            cards1.getChildren().clear();
            cards2.getChildren().clear();

            VBox c1 = Ui.card("Students", String.valueOf(directory.students().size()),
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.students(), "Students"); });
            VBox c2 = Ui.card("Grade Records", String.valueOf(allGrades.size()),
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.grades(), "Grades"); });
            VBox c3 = Ui.card("Attendance Records", String.valueOf(allAttendance.size()),
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.attendance(), "Attendance"); });
            VBox c4 = Ui.card("Skill Evaluations", String.valueOf(skills.all().size()),
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.technicalEvaluations(), "Technical Evaluations"); });

            VBox c5 = Ui.card("Project Evaluations", String.valueOf(projects.allEvaluations().size()),
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.evaluations(), "Project Evaluations"); });
            VBox c6 = Ui.card("Academic Support", String.valueOf(support.all().size()),
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.support(), "Academic Support"); });
            VBox c7 = Ui.card("Projects", String.valueOf(projects.allProjects().size()),
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.projects(), "Projects"); });
            VBox c8 = Ui.card("Catalog Subjects", String.valueOf(directory.subjects().size()),
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.subjects(), "Subjects"); });

            for (VBox c : List.of(c1, c2, c3, c4, c5, c6, c7, c8)) {
                HBox.setHgrow(c, Priority.ALWAYS);
                c.setMaxWidth(Double.MAX_VALUE);
            }

            cards1.getChildren().addAll(c1, c2, c3, c4);
            cards2.getChildren().addAll(c5, c6, c7, c8);

            // 2. Update Dual Charts
            chartsRow.getChildren().clear();
            Node attendanceChart = DashboardCharts.attendanceSummary(allAttendance,
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.attendance(), "Attendance"); });
            Node gradesChart = DashboardCharts.averageByActivityType(allGrades,
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.grades(), "Grades"); });
            HBox.setHgrow(attendanceChart, Priority.ALWAYS);
            HBox.setHgrow(gradesChart, Priority.ALWAYS);
            chartsRow.getChildren().addAll(attendanceChart, gradesChart);

            // 3. Update Right Side Panel
            rightColumnHolder.getChildren().clear();
            VBox rightPanel = createInstructorRightSidePanel(allAttendance, allGrades, enrollments, students, () -> refreshHolder[0].run());
            rightColumnHolder.getChildren().add(rightPanel);

            // 4. Update Grades Table
            String q = searchGrades.getText() == null ? "" : searchGrades.getText().toLowerCase(Locale.ROOT);
            String selectedType = typeFilter.getValue();
            List<Grade> filtered = allGrades.stream().filter(g -> {
                String sName = studentLabel(g.getEnrollmentId(), enrollments, students).toLowerCase(Locale.ROOT);
                String subName = subjectLabel(g.getEnrollmentId(), enrollments, subjects).toLowerCase(Locale.ROOT);
                String actName = String.valueOf(g.getActivityName()).toLowerCase(Locale.ROOT);
                boolean matchesQ = q.isBlank() || sName.contains(q) || subName.contains(q) || actName.contains(q);
                boolean matchesType = selectedType == null || "All Types".equalsIgnoreCase(selectedType)
                        || String.valueOf(g.getActivityType()).equalsIgnoreCase(selectedType);
                return matchesQ && matchesType;
            }).toList();
            gradeTable.setItems(FXCollections.observableArrayList(filtered));
        };
        refreshHolder[0] = refreshDashboard;

        // Action Column: Edit Grade
        gradeTable.getColumns().add(Ui.actionColumn("Action", g -> {
            Button editBtn = new Button("Edit");
            editBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12;");
            editBtn.setOnAction(ev -> editGrade(g, refreshDashboard));
            return editBtn;
        }));

        searchGrades.textProperty().addListener((obs, old, val) -> refreshDashboard.run());
        typeFilter.valueProperty().addListener((obs, old, val) -> refreshDashboard.run());

        refreshDashboard.run();

        VBox scrollContent = new VBox(20);
        scrollContent.setFillWidth(true);
        scrollContent.setStyle("-fx-background-color: #FDFBFA;");
        scrollContent.setPadding(new Insets(10, 24, 24, 24));
        scrollContent.getChildren().addAll(cards1, cards2, dashboardBody);

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

    private static VBox createInstructorRightSidePanel(List<Attendance> allAttendance, List<Grade> allGrades,
                                                       Map<Integer, Enrollment> enrollments,
                                                       Map<Integer, String> students, Runnable refresh) {
        VBox rightSide = new VBox(16);
        rightSide.setPrefWidth(360);
        rightSide.setMinWidth(320);

        // 1. Quick Actions Launcher
        VBox quickActions = new VBox(12);
        quickActions.getStyleClass().add("clay-tray");

        Label actTitle = new Label("Quick Actions");
        actTitle.getStyleClass().add("tray-title");
        Label actSub = new Label("Faculty academic shortcuts");
        actSub.getStyleClass().add("tray-subtitle");

        Button recordGradeBtn = new Button("📝  Record Student Grade");
        recordGradeBtn.setMaxWidth(Double.MAX_VALUE);
        recordGradeBtn.getStyleClass().add("primary-button");
        recordGradeBtn.setOnAction(e -> editGrade(null, refresh));

        Button recordAttendanceBtn = new Button("📅  Log Class Attendance");
        recordAttendanceBtn.setMaxWidth(Double.MAX_VALUE);
        recordAttendanceBtn.getStyleClass().add("secondary-button");
        recordAttendanceBtn.setOnAction(e -> editAttendance(null, refresh));

        Button refreshBtn = new Button("🔄  Refresh Academic Stats");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> {
            refresh.run();
            Alerts.info("Academic stats refreshed.");
        });

        quickActions.getChildren().addAll(new VBox(3, actTitle, actSub), recordGradeBtn, recordAttendanceBtn, refreshBtn);

        // 2. Class Engagement & Health Card
        VBox engagementCard = new VBox(14);
        engagementCard.getStyleClass().add("clay-tray");

        Label engTitle = new Label("Academic Engagement");
        engTitle.getStyleClass().add("tray-title");
        Label engSub = new Label("Classroom participation & grading");
        engSub.getStyleClass().add("tray-subtitle");

        VBox queueBox = new VBox(8);
        Label queueHeader = new Label("CLASSROOM METRICS");
        queueHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #9E0B0F; -fx-letter-spacing: 0.8px;");

        long presentCount = allAttendance.stream().filter(a -> "Present".equalsIgnoreCase(a.getStatus())).count();

        HBox attRow = new HBox(8);
        attRow.setAlignment(Pos.CENTER_LEFT);
        Label aLabel = new Label("Attendance Logged:");
        aLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
        Region aSp = new Region();
        HBox.setHgrow(aSp, Priority.ALWAYS);
        Node aBadge = Ui.badge(allAttendance.size() + " (" + presentCount + " Present)", "badge-pill-mint");
        attRow.getChildren().addAll(aLabel, aSp, aBadge);
        Ui.makeInteractive(attRow, () -> {
            if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.attendance(), "Attendance");
        });

        HBox gradeRow = new HBox(8);
        gradeRow.setAlignment(Pos.CENTER_LEFT);
        Label gLabel = new Label("Graded Activities:");
        gLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
        Region gSp = new Region();
        HBox.setHgrow(gSp, Priority.ALWAYS);
        Node gBadge = Ui.badge(allGrades.size() + " Submissions", "badge-pill-blue");
        gradeRow.getChildren().addAll(gLabel, gSp, gBadge);
        Ui.makeInteractive(gradeRow, () -> {
            if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.grades(), "Grades");
        });

        HBox supRow = new HBox(8);
        supRow.setAlignment(Pos.CENTER_LEFT);
        Label sLabel = new Label("Support Requests:");
        sLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
        Region sSp = new Region();
        HBox.setHgrow(sSp, Priority.ALWAYS);
        Node sBadge = Ui.badge(support.all().size() + " Active", "badge-pill-lavender");
        supRow.getChildren().addAll(sLabel, sSp, sBadge);
        Ui.makeInteractive(supRow, () -> {
            if (AppController.getInstance() != null) AppController.getInstance().navigateTo(InstructorViews.support(), "Academic Support");
        });

        queueBox.getChildren().addAll(queueHeader, attRow, gradeRow, supRow);

        Separator sep = new Separator();

        // Faculty clearance
        VBox facultyBox = new VBox(6);
        Label fHeader = new Label("FACULTY CLEARANCE");
        fHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #9E0B0F; -fx-letter-spacing: 0.8px;");
        Label fName = new Label(Session.currentUser().getFullName());
        fName.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
        Label fRole = new Label("Instructor / Faculty Evaluator");
        fRole.setStyle("-fx-font-size: 11px; -fx-text-fill: #6E6862;");
        facultyBox.getChildren().addAll(fHeader, fName, fRole);

        engagementCard.getChildren().addAll(new VBox(3, engTitle, engSub), queueBox, sep, facultyBox);

        // 3. Recent Submissions Stream
        VBox recentGradesCard = new VBox(12);
        recentGradesCard.getStyleClass().add("clay-tray");
        Label rTitle = new Label("Latest Grade Submissions");
        rTitle.getStyleClass().add("tray-title");
        Label rSub = new Label("Recent student evaluations");
        rSub.getStyleClass().add("tray-subtitle");
        recentGradesCard.getChildren().add(new VBox(3, rTitle, rSub));

        List<Grade> latestGrades = allGrades.stream()
                .sorted((a, b) -> Integer.compare(b.getGradeId(), a.getGradeId()))
                .limit(3)
                .toList();

        if (latestGrades.isEmpty()) {
            Label empty = new Label("No graded submissions yet.");
            empty.setStyle("-fx-font-size: 11px; -fx-text-fill: #6E6862;");
            recentGradesCard.getChildren().add(empty);
        } else {
            for (Grade g : latestGrades) {
                HBox item = new HBox(10);
                item.setAlignment(Pos.CENTER_LEFT);
                item.getStyleClass().add("activity-item");

                String stName = studentLabel(g.getEnrollmentId(), enrollments, students);
                StackPane avatar = Ui.avatar(Ui.initials(stName), "avatar-pink");
                avatar.setPrefSize(32, 32);
                avatar.setMinSize(32, 32);

                VBox info = new VBox(2);
                Label name = new Label(stName);
                name.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
                Label act = new Label(Ui.value(g.getActivityName()));
                act.setStyle("-fx-font-size: 10px; -fx-text-fill: #6E6862;");
                info.getChildren().addAll(name, act);
                HBox.setHgrow(info, Priority.ALWAYS);

                String grText = g.getGrade() == null ? "" : String.format("%.1f%%", g.getGrade());
                Label scoreBadge = Ui.badge(grText, "badge-pill-yellow");
                item.getChildren().addAll(avatar, info, scoreBadge);
                recentGradesCard.getChildren().add(item);
            }
        }

        rightSide.getChildren().addAll(quickActions, engagementCard, recentGradesCard);
        return rightSide;
    }

    public static Node grades() {
        int iid = instructorId();
        Map<Integer, Enrollment> enrollments = enrollmentIndex();
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> subjects = directory.subjectNames();
        Map<Integer, String> instructors = directory.instructorNames();
        List<Grade> all = academic.allGrades();

        TableView<Grade> table = Ui.table();
        Ui.addColumn(table, "Student", g -> studentLabel(g.getEnrollmentId(), enrollments, students));
        Ui.addColumn(table, "Subject", g -> subjectLabel(g.getEnrollmentId(), enrollments, subjects));
        Ui.addColumn(table, "Activity", Grade::getActivityName);
        Ui.addColumn(table, "Type", Grade::getActivityType);
        Ui.addColumn(table, "Score", g -> g.getScore() + " / " + g.getMaxScore());
        Ui.addColumn(table, "Grade", g -> g.getGrade() == null ? "" : String.format("%.2f%%", g.getGrade()));
        Ui.addColumn(table, "Remarks", Grade::getRemarks);
        Ui.addColumn(table, "Recorded By", g -> instructors.getOrDefault(g.getInstructorId(), "Instructor #" + g.getInstructorId()));
        Ui.addColumn(table, "Recorded", g -> g.getRecordedAt() == null ? "" : g.getRecordedAt().toLocalDate().toString());
        table.setItems(FXCollections.observableArrayList(all));

        ComboBox<String> subjectFilter = new ComboBox<>();
        subjectFilter.getItems().add("All Subjects");
        Map<String, Integer> subjectFilterMap = new HashMap<>();
        for (Subject s : directory.subjects()) {
            String label = s.getSubjectCode() + " - " + s.getSubjectName() + (s.getSection() != null ? " (" + s.getSection() + ")" : "");
            subjectFilter.getItems().add(label);
            subjectFilterMap.put(label, s.getSubjectId());
        }
        subjectFilter.getSelectionModel().selectFirst();

        TextField search = Ui.field("Search by student, subject or activity...");
        Runnable applyFilter = () -> {
            enrollments.clear();
            enrollments.putAll(enrollmentIndex());
            students.clear();
            students.putAll(directory.studentNames());
            subjects.clear();
            subjects.putAll(directory.subjectNames());
            instructors.clear();
            instructors.putAll(directory.instructorNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            Integer filterSubjectId = subjectFilterMap.get(subjectFilter.getValue());

            table.setItems(FXCollections.observableArrayList(academic.allGrades().stream()
                    .filter(g -> {
                        if (filterSubjectId != null) {
                            Enrollment enr = enrollments.get(g.getEnrollmentId());
                            if (enr == null || enr.getSubjectId() != filterSubjectId) return false;
                        }
                        return true;
                    })
                    .filter(g ->
                            studentLabel(g.getEnrollmentId(), enrollments, students).toLowerCase(Locale.ROOT).contains(q)
                                    || subjectLabel(g.getEnrollmentId(), enrollments, subjects).toLowerCase(Locale.ROOT).contains(q)
                                    || String.valueOf(g.getActivityName()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());
        subjectFilter.valueProperty().addListener((obs, old, val) -> applyFilter.run());

        Button add = new Button("Record Grade");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> editGrade(null, applyFilter));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> {
            Grade selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a grade to edit."); return; }
            editGrade(selected, applyFilter);
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            Grade selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a grade to delete."); return; }
            if (Ui.confirmed("Delete the grade for \"" + selected.getActivityName() + "\"?")) {
                guard(() -> {
                    academic.deleteGrade(selected.getGradeId());
                    applyFilter.run();
                });
            }
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Grade Management", "Record activity scores for students enrolled in your subjects. Grades are calculated automatically from score and maximum score.",
                Ui.toolbar(new Label("Filter by Subject:"), subjectFilter, new Label("Search:"), search, add, edit, delete, refresh), table);
    }

    private static void editGrade(Grade existing, Runnable refresh) {
        int iid = instructorId();
        List<Enrollment> myEnrollments = academic.enrollmentsForInstructor(iid);
        List<Enrollment> enrollmentList = new ArrayList<>(myEnrollments.isEmpty() ? academic.allEnrollments() : myEnrollments);
        if (existing != null && enrollmentList.stream().noneMatch(e -> e.getEnrollmentId() == existing.getEnrollmentId())) {
            academic.allEnrollments().stream().filter(e -> e.getEnrollmentId() == existing.getEnrollmentId()).findFirst().ifPresent(enrollmentList::add);
        }
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> subjects = directory.subjectNames();
        ComboBox<Enrollment> enrollment = enrollmentCombo(enrollmentList, students, subjects);
        Enrollment current = existing == null ? null : enrollmentList.stream()
                .filter(e -> e.getEnrollmentId() == existing.getEnrollmentId())
                .findFirst().orElse(null);
        if (current != null) enrollment.getSelectionModel().select(current);
        TextField activity = Ui.field("Activity name");
        activity.setText(existing == null ? "" : existing.getActivityName());
        ComboBox<String> type = Ui.combo(ACTIVITY_TYPES, s -> s);
        if (existing == null) type.getSelectionModel().selectFirst();
        else type.getSelectionModel().select(existing.getActivityType());
        TextField score = Ui.field("Score");
        if (existing != null) score.setText(String.valueOf(existing.getScore()));
        TextField max = Ui.field("Maximum score");
        if (existing != null) max.setText(String.valueOf(existing.getMaxScore()));
        TextField remarks = Ui.field("Remarks");
        remarks.setText(existing == null ? "" : existing.getRemarks());
        Node form = Ui.form(new String[]{"Enrollment", "Activity", "Type", "Score", "Maximum score", "Remarks"},
                new Node[]{enrollment, activity, type, score, max, remarks});
        Ui.dialog(existing == null ? "Record Grade" : "Edit Grade", form).ifPresent(button -> {
            if (button != ButtonType.OK) return;
            guard(() -> {
                if (enrollment.getValue() == null) throw new AppException("Select a student enrollment.");
                Grade grade = new Grade();
                if (existing != null) {
                    grade.setGradeId(existing.getGradeId());
                    grade.setInstructorId(existing.getInstructorId());
                    grade.setRecordedAt(existing.getRecordedAt());
                } else {
                    grade.setInstructorId(instructorId());
                }
                grade.setEnrollmentId(enrollment.getValue().getEnrollmentId());
                grade.setActivityName(activity.getText());
                grade.setActivityType(type.getValue());
                grade.setScore(Ui.parseDouble(score.getText(), "Score"));
                grade.setMaxScore(Ui.parseDouble(max.getText(), "Maximum score"));
                grade.setRemarks(remarks.getText());
                if (existing == null) academic.recordGrade(grade);
                else academic.updateGrade(grade);
                refresh.run();
                Alerts.info("Grade saved.");
            });
        });
    }

    public static Node attendance() {
        int iid = instructorId();
        Map<Integer, Enrollment> enrollments = enrollmentIndex();
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> subjects = directory.subjectNames();
        Map<Integer, String> instructors = directory.instructorNames();
        List<Attendance> all = academic.allAttendance();

        TableView<Attendance> table = Ui.table();
        Ui.addColumn(table, "Student", a -> studentLabel(a.getEnrollmentId(), enrollments, students));
        Ui.addColumn(table, "Subject", a -> subjectLabel(a.getEnrollmentId(), enrollments, subjects));
        Ui.addColumn(table, "Date", a -> Ui.formatDate(a.getAttendanceDate()));
        Ui.addColumn(table, "Status", Attendance::getStatus);
        Ui.addColumn(table, "Remarks", Attendance::getRemarks);
        Ui.addColumn(table, "Recorded By", a -> instructors.getOrDefault(a.getRecordedBy(), "Instructor #" + a.getRecordedBy()));
        table.setItems(FXCollections.observableArrayList(all));

        ComboBox<String> subjectFilter = new ComboBox<>();
        subjectFilter.getItems().add("All Subjects");
        Map<String, Integer> subjectFilterMap = new HashMap<>();
        for (Subject s : directory.subjects()) {
            String label = s.getSubjectCode() + " - " + s.getSubjectName() + (s.getSection() != null ? " (" + s.getSection() + ")" : "");
            subjectFilter.getItems().add(label);
            subjectFilterMap.put(label, s.getSubjectId());
        }
        subjectFilter.getSelectionModel().selectFirst();

        TextField search = Ui.field("Search by student, subject, date, status or remarks...");
        Runnable applyFilter = () -> {
            enrollments.clear();
            enrollments.putAll(enrollmentIndex());
            students.clear();
            students.putAll(directory.studentNames());
            subjects.clear();
            subjects.putAll(directory.subjectNames());
            instructors.clear();
            instructors.putAll(directory.instructorNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            Integer filterSubjectId = subjectFilterMap.get(subjectFilter.getValue());

            table.setItems(FXCollections.observableArrayList(academic.allAttendance().stream()
                    .filter(a -> {
                        if (filterSubjectId != null) {
                            Enrollment enr = enrollments.get(a.getEnrollmentId());
                            if (enr == null || enr.getSubjectId() != filterSubjectId) return false;
                        }
                        return true;
                    })
                    .filter(a ->
                            studentLabel(a.getEnrollmentId(), enrollments, students).toLowerCase(Locale.ROOT).contains(q)
                                    || subjectLabel(a.getEnrollmentId(), enrollments, subjects).toLowerCase(Locale.ROOT).contains(q)
                                    || Ui.formatDate(a.getAttendanceDate()).toLowerCase(Locale.ROOT).contains(q)
                                    || String.valueOf(a.getStatus()).toLowerCase(Locale.ROOT).contains(q)
                                    || String.valueOf(a.getRemarks()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());
        subjectFilter.valueProperty().addListener((obs, old, val) -> applyFilter.run());

        Button add = new Button("Record Attendance");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> editAttendance(null, applyFilter));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> {
            Attendance selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an attendance record to edit."); return; }
            editAttendance(selected, applyFilter);
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            Attendance selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an attendance record to delete."); return; }
            if (Ui.confirmed("Delete the attendance record for " + selected.getAttendanceDate() + "?")) {
                guard(() -> {
                    academic.deleteAttendance(selected.getAttendanceId());
                    applyFilter.run();
                });
            }
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Attendance", "Record attendance for enrolled students and keep an auditable history of each class.",
                Ui.toolbar(new Label("Filter by Subject:"), subjectFilter, new Label("Search:"), search, add, edit, delete, refresh), table);
    }

    private static void editAttendance(Attendance existing, Runnable refresh) {
        int iid = instructorId();
        List<Enrollment> myEnrollments = academic.enrollmentsForInstructor(iid);
        List<Enrollment> enrollmentList = new ArrayList<>(myEnrollments.isEmpty() ? academic.allEnrollments() : myEnrollments);
        if (existing != null && enrollmentList.stream().noneMatch(e -> e.getEnrollmentId() == existing.getEnrollmentId())) {
            academic.allEnrollments().stream().filter(e -> e.getEnrollmentId() == existing.getEnrollmentId()).findFirst().ifPresent(enrollmentList::add);
        }
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> subjects = directory.subjectNames();
        ComboBox<Enrollment> enrollment = enrollmentCombo(enrollmentList, students, subjects);
        Enrollment current = existing == null ? null : enrollmentList.stream()
                .filter(e -> e.getEnrollmentId() == existing.getEnrollmentId())
                .findFirst().orElse(null);
        if (current != null) enrollment.getSelectionModel().select(current);
        DatePicker date = Ui.date(existing == null || existing.getAttendanceDate() == null
                ? LocalDate.now() : existing.getAttendanceDate());
        ComboBox<String> status = Ui.combo(List.of("Present", "Absent", "Late", "Excused"), s -> s);
        if (existing == null) status.getSelectionModel().selectFirst();
        else status.getSelectionModel().select(existing.getStatus());
        TextField remarks = Ui.field("Remarks");
        remarks.setText(existing == null ? "" : existing.getRemarks());
        Node form = Ui.form(new String[]{"Enrollment", "Date", "Status", "Remarks"},
                new Node[]{enrollment, date, status, remarks});
        Ui.dialog(existing == null ? "Record Attendance" : "Edit Attendance", form).ifPresent(button -> {
            if (button != ButtonType.OK) return;
            guard(() -> {
                if (enrollment.getValue() == null) throw new AppException("Select a student enrollment.");
                Attendance attendance = new Attendance();
                if (existing != null) {
                    attendance.setAttendanceId(existing.getAttendanceId());
                    attendance.setRecordedBy(existing.getRecordedBy());
                } else {
                    attendance.setRecordedBy(instructorId());
                }
                attendance.setEnrollmentId(enrollment.getValue().getEnrollmentId());
                attendance.setAttendanceDate(date.getValue());
                attendance.setStatus(status.getValue());
                attendance.setRemarks(remarks.getText());
                if (existing == null) academic.recordAttendance(attendance);
                else academic.updateAttendance(attendance);
                refresh.run();
                Alerts.info("Attendance saved.");
            });
        });
    }

    public static Node technicalEvaluations() {
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> skillNames = directory.skillNames();
        Map<Integer, String> instructors = directory.instructorNames();
        List<StudentSkill> all = skills.all();

        TableView<StudentSkill> table = Ui.table();
        Ui.addColumn(table, "Student", s -> students.getOrDefault(s.getStudentId(), "Student #" + s.getStudentId()));
        Ui.addColumn(table, "Skill", s -> skillNames.getOrDefault(s.getSkillId(), "Skill #" + s.getSkillId()));
        Ui.addColumn(table, "Proficiency", StudentSkill::getProficiencyLevel);
        Ui.addColumn(table, "Score", s -> s.getAssessmentScore() == null ? "" : String.format("%.2f%%", s.getAssessmentScore()));
        Ui.addColumn(table, "Assessed By", s -> s.getAssessedBy() == null ? "" : instructors.getOrDefault(s.getAssessedBy(), "Instructor #" + s.getAssessedBy()));
        Ui.addColumn(table, "Assessed At", s -> s.getAssessedAt() == null ? "" : s.getAssessedAt().toLocalDate().toString());
        Ui.addColumn(table, "Remarks", StudentSkill::getRemarks);
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search by student, skill, proficiency, score or remarks...");
        Runnable applyFilter = () -> {
            students.clear();
            students.putAll(directory.studentNames());
            skillNames.clear();
            skillNames.putAll(directory.skillNames());
            instructors.clear();
            instructors.putAll(directory.instructorNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(skills.all().stream().filter(s ->
                    students.getOrDefault(s.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || skillNames.getOrDefault(s.getSkillId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getProficiencyLevel()).toLowerCase(Locale.ROOT).contains(q)
                            || (s.getAssessmentScore() != null
                                && String.format("%.2f%%", s.getAssessmentScore()).toLowerCase(Locale.ROOT).contains(q))
                            || String.valueOf(s.getRemarks()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button evaluate = new Button("Assess Skill");
        evaluate.getStyleClass().add("primary-button");
        evaluate.setOnAction(e -> {
            ComboBox<Student> student = studentCombo(directory.students());
            ComboBox<TechnicalSkill> skill = Ui.combo(directory.technicalSkills(), s -> s.getSkillName() + " (" + s.getCategory() + ")");
            ComboBox<String> level = Ui.combo(PROFICIENCY_LEVELS, s -> s);
            level.getSelectionModel().selectFirst();
            TextField score = Ui.field("Assessment score (0-100)");
            TextField remarks = Ui.field("Remarks");
            Runnable prefill = () -> {
                if (student.getValue() == null || skill.getValue() == null) return;
                java.util.Optional<StudentSkill> existing =
                        skills.find(student.getValue().getStudentId(), skill.getValue().getSkillId());
                if (existing.isPresent()) {
                    level.getSelectionModel().select(existing.get().getProficiencyLevel());
                    score.setText(scoreText(existing.get().getAssessmentScore()));
                    remarks.setText(value(existing.get().getRemarks()));
                } else {
                    level.getSelectionModel().selectFirst();
                    score.clear();
                    remarks.clear();
                }
            };
            student.valueProperty().addListener((obs, old, selected) -> guard(prefill));
            skill.valueProperty().addListener((obs, old, selected) -> guard(prefill));
            Node form = Ui.form(new String[]{"Student", "Technical skill", "Proficiency", "Score", "Remarks"},
                    new Node[]{student, skill, level, score, remarks});
            Ui.dialog("Assess Technical Skill", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    if (student.getValue() == null) throw new AppException("Select a student.");
                    if (skill.getValue() == null) throw new AppException("Select a technical skill.");
                    StudentSkill evaluation = new StudentSkill();
                    evaluation.setStudentId(student.getValue().getStudentId());
                    evaluation.setSkillId(skill.getValue().getSkillId());
                    evaluation.setProficiencyLevel(level.getValue());
                    evaluation.setAssessmentScore(Ui.optionalDouble(score.getText(), "Assessment score"));
                    evaluation.setRemarks(remarks.getText());
                    skills.recordEvaluation(evaluation, instructorId());
                    applyFilter.run();
                    Alerts.info("Skill assessment saved.");
                });
            });
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            StudentSkill selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an evaluation to delete."); return; }
            if (Ui.confirmed("Delete this skill assessment?")) {
                guard(() -> { skills.delete(selected.getStudentSkillId()); applyFilter.run(); });
            }
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Technical Skill Evaluation", "Assess student proficiency against the technical skill catalogue maintained by faculty instructors.",
                Ui.toolbar(new Label("Search:"), search, evaluate, delete, refresh), table);
    }

    public static Node evaluations() {
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> projectsById = projects.allProjects().stream()
                .collect(Collectors.toMap(Project::getProjectId, Project::getProjectTitle, (a, b) -> a));
        Map<Integer, String> instructors = directory.instructorNames();
        List<ProjectEvaluation> all = projects.allEvaluations();

        TableView<ProjectEvaluation> table = Ui.table();
        Ui.addColumn(table, "Project", e -> projectsById.getOrDefault(e.getProjectId(), "Project #" + e.getProjectId()));
        Ui.addColumn(table, "Student", e -> students.getOrDefault(e.getStudentId(), "Student #" + e.getStudentId()));
        Ui.addColumn(table, "Contribution", e -> e.getContributionScore() == null ? "" : String.valueOf(e.getContributionScore()));
        Ui.addColumn(table, "Technical", e -> e.getTechnicalScore() == null ? "" : String.valueOf(e.getTechnicalScore()));
        Ui.addColumn(table, "Teamwork", e -> e.getTeamworkScore() == null ? "" : String.valueOf(e.getTeamworkScore()));
        Ui.addColumn(table, "Overall", e -> e.getOverallScore() == null ? "" : String.valueOf(e.getOverallScore()));
        Ui.addColumn(table, "Feedback", ProjectEvaluation::getFeedback);
        Ui.addColumn(table, "Instructor", e -> instructors.getOrDefault(e.getInstructorId(), "Instructor #" + e.getInstructorId()));
        Ui.addColumn(table, "Evaluated", e -> e.getEvaluatedAt() == null ? "" : e.getEvaluatedAt().toLocalDate().toString());
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search by project, student, feedback or instructor...");
        Runnable applyFilter = () -> {
            projectsById.clear();
            projects.allProjects().forEach(p -> projectsById.put(p.getProjectId(), p.getProjectTitle()));
            students.clear();
            students.putAll(directory.studentNames());
            instructors.clear();
            instructors.putAll(directory.instructorNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(projects.allEvaluations().stream().filter(e ->
                    projectsById.getOrDefault(e.getProjectId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || students.getOrDefault(e.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(e.getFeedback()).toLowerCase(Locale.ROOT).contains(q)
                            || instructors.getOrDefault(e.getInstructorId(), "").toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button evaluate = new Button("Evaluate Project");
        evaluate.getStyleClass().add("primary-button");
        evaluate.setOnAction(e -> {
            ComboBox<Project> project = Ui.combo(projects.allProjects(), Project::getProjectTitle);
            List<Student> allStudents = directory.students();
            ComboBox<Student> student = studentCombo(allStudents);
            project.valueProperty().addListener((obs, old, selectedProject) -> {
                if (selectedProject == null) return;
                java.util.Set<Integer> eligible = new java.util.HashSet<>();
                if (selectedProject.getStudentId() != null) eligible.add(selectedProject.getStudentId());
                projects.membersForProject(selectedProject.getProjectId())
                        .forEach(m -> eligible.add(m.getStudentId()));
                student.setItems(FXCollections.observableArrayList(allStudents.stream()
                        .filter(s -> eligible.contains(s.getStudentId())).toList()));
                student.getSelectionModel().clearSelection();
            });
            TextField contribution = Ui.field("Contribution score (0-100)");
            TextField technical = Ui.field("Technical score (0-100)");
            TextField teamwork = Ui.field("Teamwork score (0-100)");
            TextArea feedback = Ui.area("Feedback");
            student.valueProperty().addListener((obs, old, selectedStudent) -> {
                contribution.clear();
                technical.clear();
                teamwork.clear();
                feedback.clear();
                if (selectedStudent == null || project.getValue() == null) return;
                projects.evaluationFor(project.getValue().getProjectId(), selectedStudent.getStudentId())
                        .ifPresent(existing -> {
                            contribution.setText(scoreText(existing.getContributionScore()));
                            technical.setText(scoreText(existing.getTechnicalScore()));
                            teamwork.setText(scoreText(existing.getTeamworkScore()));
                            feedback.setText(existing.getFeedback());
                        });
            });
            Node form = Ui.form(new String[]{"Project", "Student", "Contribution", "Technical", "Teamwork", "Feedback"},
                    new Node[]{project, student, contribution, technical, teamwork, feedback});
            Ui.dialog("Evaluate Project", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    if (project.getValue() == null) throw new AppException("Select a project.");
                    if (student.getValue() == null) throw new AppException("Select a student.");
                    ProjectEvaluation evaluation = new ProjectEvaluation();
                    evaluation.setProjectId(project.getValue().getProjectId());
                    evaluation.setStudentId(student.getValue().getStudentId());
                    evaluation.setInstructorId(instructorId());
                    evaluation.setContributionScore(Ui.optionalDouble(contribution.getText(), "Contribution score"));
                    evaluation.setTechnicalScore(Ui.optionalDouble(technical.getText(), "Technical score"));
                    evaluation.setTeamworkScore(Ui.optionalDouble(teamwork.getText(), "Teamwork score"));
                    evaluation.setFeedback(feedback.getText());
                    projects.evaluate(evaluation);
                    applyFilter.run();
                    Alerts.info("Project evaluation saved.");
                });
            });
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Project Evaluations", "Evaluate individual group members on contribution, technical work and teamwork.",
                Ui.toolbar(new Label("Search:"), search, evaluate, refresh), table);
    }

    public static Node support() {
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> subjects = directory.subjectNames();
        Map<Integer, String> instructors = directory.instructorNames();
        List<AcademicSupport> all = support.all();

        TableView<AcademicSupport> table = Ui.table();
        Ui.addColumn(table, "Student", s -> students.getOrDefault(s.getStudentId(), "Student #" + s.getStudentId()));
        Ui.addColumn(table, "Subject", s -> s.getSubjectId() == null ? "" : subjects.getOrDefault(s.getSubjectId(), "Subject #" + s.getSubjectId()));
        Ui.addColumn(table, "Type", AcademicSupport::getSupportType);
        Ui.addColumn(table, "Date", s -> Ui.formatDate(s.getSupportDate()));
        Ui.addColumn(table, "Reason", AcademicSupport::getReason);
        Ui.addColumn(table, "Action Taken", AcademicSupport::getActionTaken);
        Ui.addColumn(table, "Outcome", AcademicSupport::getOutcome);
        Ui.addColumn(table, "Remarks", AcademicSupport::getRemarks);
        Ui.addColumn(table, "Instructor", s -> instructors.getOrDefault(s.getInstructorId(), "Instructor #" + s.getInstructorId()));
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search by student, subject, type, date, reason or outcome...");
        Runnable applyFilter = () -> {
            students.clear();
            students.putAll(directory.studentNames());
            subjects.clear();
            subjects.putAll(directory.subjectNames());
            instructors.clear();
            instructors.putAll(directory.instructorNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(support.all().stream().filter(s ->
                    students.getOrDefault(s.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || (s.getSubjectId() != null
                                && subjects.getOrDefault(s.getSubjectId(), "").toLowerCase(Locale.ROOT).contains(q))
                            || String.valueOf(s.getSupportType()).toLowerCase(Locale.ROOT).contains(q)
                            || Ui.formatDate(s.getSupportDate()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getReason()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getActionTaken()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getOutcome()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getRemarks()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button add = new Button("Record Support");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> {
            ComboBox<Student> student = studentCombo(directory.students());
            ComboBox<Subject> subject = subjectCombo(directory.subjects());
            ComboBox<String> type = Ui.combo(List.of("Review", "Remedial", "Consultation", "Additional Exercise"), s -> s);
            type.getSelectionModel().selectFirst();
            DatePicker date = Ui.date(LocalDate.now());
            TextField reason = Ui.field("Reason");
            TextField action = Ui.field("Action taken");
            TextField outcome = Ui.field("Outcome");
            TextField remarks = Ui.field("Remarks");
            Node form = Ui.form(new String[]{"Student", "Subject", "Support type", "Date", "Reason", "Action taken", "Outcome", "Remarks"},
                    new Node[]{student, subject, type, date, reason, action, outcome, remarks});
            Ui.dialog("Record Academic Support", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    if (student.getValue() == null) throw new AppException("Select a student.");
                    AcademicSupport record = new AcademicSupport();
                    record.setStudentId(student.getValue().getStudentId());
                    record.setInstructorId(instructorId());
                    record.setSubjectId(subject.getValue() == null ? null : subject.getValue().getSubjectId());
                    record.setSupportType(type.getValue());
                    record.setSupportDate(date.getValue());
                    record.setReason(reason.getText());
                    record.setActionTaken(action.getText());
                    record.setOutcome(outcome.getText());
                    record.setRemarks(remarks.getText());
                    support.record(record);
                    applyFilter.run();
                    Alerts.info("Academic support record saved.");
                });
            });
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            AcademicSupport selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a support record to delete."); return; }
            if (Ui.confirmed("Delete this academic support record?")) {
                guard(() -> { support.delete(selected.getSupportId()); applyFilter.run(); });
            }
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Academic Support", "Document reviews, remedial sessions, consultations and additional exercises for students who need help.",
                Ui.toolbar(new Label("Search:"), search, add, delete, refresh), table);
    }

    public static Node passwordResetRequests() {
        TableView<PasswordResetRequest> table = Ui.table();
        Ui.addColumn(table, "Identifier", PasswordResetRequest::getIdentifierSubmitted);
        Ui.addColumn(table, "Role", r -> r.getRole() != null ? r.getRole().name() : "");
        Ui.addColumn(table, "Matched User", r -> {
            if (r.getFullName() != null && !r.getFullName().isBlank()) {
                return r.getFullName() + (r.getUsername() != null ? " (" + r.getUsername() + ")" : "");
            }
            return r.getUsername() != null ? r.getUsername() : "Unmatched";
        });
        Ui.addColumn(table, "Contact / Registered Email", r -> r.getUserEmail() != null ? r.getUserEmail() : "—");
        Ui.addColumn(table, "Reason", PasswordResetRequest::getReason);
        Ui.addColumn(table, "Status", PasswordResetRequest::getStatus);
        Ui.addColumn(table, "Requested At", r -> Ui.formatDateTime(r.getRequestedAt()));
        Ui.addColumn(table, "Resolved At", r -> Ui.formatDateTime(r.getResolvedAt()));
        Ui.addColumn(table, "Admin Remarks", PasswordResetRequest::getAdminRemarks);

        Runnable refreshData = () -> {
            List<PasswordResetRequest> all = resetRequests.listAllRequests();
            table.setItems(FXCollections.observableArrayList(all));
        };
        refreshData.run();

        TextField search = Ui.field("Search by identifier, user, role, reason or status...");
        search.textProperty().addListener((obs, old, value) -> {
            String q = value == null ? "" : value.toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(resetRequests.listAllRequests().stream().filter(r ->
                    (r.getIdentifierSubmitted() != null && r.getIdentifierSubmitted().toLowerCase(Locale.ROOT).contains(q)) ||
                    (r.getUsername() != null && r.getUsername().toLowerCase(Locale.ROOT).contains(q)) ||
                    (r.getFullName() != null && r.getFullName().toLowerCase(Locale.ROOT).contains(q)) ||
                    (r.getReason() != null && r.getReason().toLowerCase(Locale.ROOT).contains(q)) ||
                    (r.getStatus() != null && r.getStatus().toLowerCase(Locale.ROOT).contains(q)) ||
                    (r.getRole() != null && r.getRole().name().toLowerCase(Locale.ROOT).contains(q))
            ).toList()));
        });

        Button approveBtn = new Button("✔ Approve & Dispatch Reset");
        approveBtn.getStyleClass().add("primary-button");
        approveBtn.setOnAction(e -> {
            PasswordResetRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Select a password reset request from the table to approve.");
                return;
            }
            if (!"PENDING".equalsIgnoreCase(selected.getStatus())) {
                Alerts.error("Only PENDING requests can be approved. Current status: " + selected.getStatus());
                return;
            }

            TextInputDialog remarksDialog = new TextInputDialog("Approved by department coordinator. Recovery OTP dispatched.");
            remarksDialog.setTitle("Approve Password Reset");
            remarksDialog.setHeaderText("Authorize password reset for: " + selected.getIdentifierSubmitted());
            remarksDialog.setContentText("Coordinator remarks:");
            remarksDialog.showAndWait().ifPresent(remarks -> {
                approveBtn.setDisable(true);
                CompletableFuture.supplyAsync(() -> resetRequests.approveAndDispatchReset(selected.getRequestId(), remarks))
                        .thenAccept(res -> Platform.runLater(() -> {
                            approveBtn.setDisable(false);
                            Alerts.info("Password reset approved. 6-digit recovery OTP has been generated and dispatched.");
                            refreshData.run();
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> {
                                approveBtn.setDisable(false);
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                Alerts.error("Failed to approve reset request: " + cause.getMessage());
                            });
                            return null;
                        });
            });
        });

        Button rejectBtn = new Button("✖ Reject Request");
        rejectBtn.getStyleClass().add("secondary-button");
        rejectBtn.setOnAction(e -> {
            PasswordResetRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Select a password reset request from the table to reject.");
                return;
            }
            if (!"PENDING".equalsIgnoreCase(selected.getStatus())) {
                Alerts.error("Only PENDING requests can be rejected. Current status: " + selected.getStatus());
                return;
            }

            TextInputDialog reasonDialog = new TextInputDialog("Identity could not be confirmed or request details are invalid.");
            reasonDialog.setTitle("Reject Password Reset");
            reasonDialog.setHeaderText("Reject reset request for: " + selected.getIdentifierSubmitted());
            reasonDialog.setContentText("Rejection reason / remarks:");
            reasonDialog.showAndWait().ifPresent(rejectionRemarks -> {
                rejectBtn.setDisable(true);
                CompletableFuture.supplyAsync(() -> resetRequests.rejectRequest(selected.getRequestId(), rejectionRemarks))
                        .thenAccept(res -> Platform.runLater(() -> {
                            rejectBtn.setDisable(false);
                            Alerts.info("Password reset request rejected.");
                            refreshData.run();
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> {
                                rejectBtn.setDisable(false);
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                Alerts.error("Failed to reject request: " + cause.getMessage());
                            });
                            return null;
                        });
            });
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshData.run());

        return Ui.page("Password Reset Requests",
                "Administrative recovery assistance queue. Review manual password reset requests submitted by students, faculty, and employers.",
                Ui.toolbar(new Label("Search:"), search, approveBtn, rejectBtn, refreshBtn), table);
    }

    /** Enrollment selector that keeps the academic term visible, preventing ambiguous grades or attendance when a student retakes a subject. */
    private static ComboBox<Enrollment> enrollmentCombo(List<Enrollment> enrollments,
                                                        Map<Integer, String> students,
                                                        Map<Integer, String> subjects) {
        return Ui.combo(enrollments, e -> students.getOrDefault(e.getStudentId(), "Student #" + e.getStudentId())
                + " · " + subjects.getOrDefault(e.getSubjectId(), "Subject #" + e.getSubjectId())
                + " · " + value(e.getAcademicYear()) + " " + value(e.getSemester()));
    }

    private static Map<Integer, Enrollment> enrollmentIndex() {
        return academic.allEnrollments().stream()
                .collect(Collectors.toMap(Enrollment::getEnrollmentId, Function.identity(), (a, b) -> a));
    }

    private static String studentLabel(int enrollmentId, Map<Integer, Enrollment> enrollments, Map<Integer, String> students) {
        Enrollment e = enrollments.get(enrollmentId);
        return e == null ? "" : students.getOrDefault(e.getStudentId(), "Student #" + e.getStudentId());
    }

    private static String subjectLabel(int enrollmentId, Map<Integer, Enrollment> enrollments, Map<Integer, String> subjects) {
        Enrollment e = enrollments.get(enrollmentId);
        return e == null ? "" : subjects.getOrDefault(e.getSubjectId(), "Subject #" + e.getSubjectId());
    }

    private static ComboBox<Student> studentCombo(List<Student> students) {
        Map<Integer, String> names = directory.studentNames();
        return Ui.combo(students, s -> names.getOrDefault(s.getStudentId(), "Student #" + s.getStudentId())
                + " (" + s.getStudentNumber() + ")");
    }

    private static ComboBox<Subject> subjectCombo(List<Subject> subjects) {
        return Ui.combo(subjects, s -> s.getSubjectCode() + " - " + s.getSubjectName());
    }

    private static String value(String text) { return text == null ? "" : text; }
    private static String scoreText(Double score) { return score == null ? "" : String.valueOf(score); }

    private static void guard(Runnable action) {
        try {
            action.run();
        } catch (AppException e) {
            Alerts.error(e.getMessage());
        } catch (Exception e) {
            Alerts.error("Could not complete the operation. Please check the database connection.");
            org.slf4j.LoggerFactory.getLogger(InstructorViews.class).error("Instructor action failed", e);
        }
    }
}