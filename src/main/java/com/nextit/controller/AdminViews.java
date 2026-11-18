package com.nextit.controller;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.service.AcademicService;
import com.nextit.service.AdminService;
import com.nextit.service.DirectoryService;
import com.nextit.service.PortfolioService;
import com.nextit.service.ProfileService;
import com.nextit.service.SkillService;
import com.nextit.service.SystemService;
import com.nextit.util.Alerts;
import com.nextit.util.Ui;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import com.nextit.repository.JdbcInstitutionalRecordRepository;
import com.nextit.repository.JdbcVerificationRequestRepository;
import com.nextit.repository.JdbcUserRepository;
import com.nextit.service.AntiFakeIdentityService;
import com.nextit.service.SecurityAuditService;

public final class AdminViews {

    private static final AdminService admin = new AdminService();
    private static final AcademicService academic = new AcademicService();
    private static final DirectoryService directory = new DirectoryService();
    private static final PortfolioService portfolios = new PortfolioService();
    private static final ProfileService profiles = new ProfileService();
    private static final SkillService skills = new SkillService();
    private static final List<String> SEMESTERS = List.of("1st Semester", "2nd Semester", "Summer");
    private static final List<String> ENROLLMENT_STATUSES = List.of("Enrolled", "Dropped", "Completed");

    private AdminViews() {}

    public static Node profile() {
        User account = profiles.account(com.nextit.security.Session.currentUser().getUserId());

        HBox hero = new HBox(20);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("profile-hero-card");

        StackPane avatar = Ui.avatar(Ui.initials(account.getFullName()), "avatar-pink");

        VBox identityDetails = new VBox(6);
        Label nameLabel = new Label(value(account.getFullName()));
        nameLabel.getStyleClass().add("profile-hero-name");

        HBox badges = new HBox(8);
        badges.setAlignment(Pos.CENTER_LEFT);
        badges.getChildren().addAll(
                Ui.badge("@" + account.getUsername(), "badge-pill-cream"),
                Ui.badge(String.valueOf(account.getRole()), "badge-pill-pink")
        );
        identityDetails.getChildren().addAll(nameLabel, badges);

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        VBox heroRight = new VBox(6);
        heroRight.setAlignment(Pos.CENTER_RIGHT);
        Label roleHighlight = new Label("Administrator Access");
        roleHighlight.getStyleClass().add("profile-hero-highlight-title");
        Label statusHighlight = new Label("Full System Permissions");
        statusHighlight.getStyleClass().add("profile-hero-highlight-subtitle");
        heroRight.getChildren().addAll(roleHighlight, statusHighlight);

        hero.getChildren().addAll(avatar, identityDetails, heroSpacer, heroRight);

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
        accountGrid.add(Ui.propertyTile("Account Role", Ui.badge(String.valueOf(account.getRole()), "badge-pill-pink")), 1, 1);

        VBox accountTray = Ui.tray("Administrator Credentials", "System authentication details and security clearance", accountGrid);
        HBox.setHgrow(accountTray, Priority.ALWAYS);

        return Ui.page("My Profile", "Your administrator account information.", hero, accountTray);
    }

    public static Node dashboard() {
        // 1. Hero cushion with status and live term badges
        HBox hero = new HBox(16);
        hero.getStyleClass().add("page-header-cushion");
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox heroTitles = new VBox(4);
        heroTitles.getChildren().addAll(
                Ui.title("Administrator Dashboard"),
                Ui.subtitle("Manage accounts, academic reference data, and student portfolios.")
        );

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        HBox statusPillBox = new HBox(8);
        statusPillBox.setAlignment(Pos.CENTER_RIGHT);
        StackPane statusDot = Ui.statusDot(true);
        Label statusText = new Label("Systems Operational");
        statusText.setStyle("-fx-font-weight: bold; -fx-text-fill: #2d6a4f; -fx-font-size: 12px;");
        HBox liveChip = new HBox(8, statusDot, statusText);
        liveChip.setAlignment(Pos.CENTER);
        liveChip.getStyleClass().addAll("badge-pill", "badge-pill-mint");

        Label termChip = Ui.badge("AY 2026-2027 • 1st Sem", "badge-pill-lavender");
        statusPillBox.getChildren().addAll(liveChip, termChip);

        hero.getChildren().addAll(heroTitles, heroSpacer, statusPillBox);

        // Fixed sticky header container
        VBox headerContainer = new VBox(hero);
        headerContainer.setStyle("-fx-background-color: #f1f2fa;");
        headerContainer.setPadding(new Insets(24, 24, 10, 24));

        // 2. Full-Width 4-Column Stat Cards
        HBox row1 = new HBox(14);
        HBox row2 = new HBox(14);
        row1.setFillHeight(true);
        row2.setFillHeight(true);

        // 3. Main Dashboard Body: Left Content + Dedicated Right Control Panel
        HBox dashboardBody = new HBox(20);
        dashboardBody.setAlignment(Pos.TOP_LEFT);

        VBox leftColumn = new VBox(20);
        HBox.setHgrow(leftColumn, Priority.ALWAYS);

        VBox rightColumnHolder = new VBox();

        // Left Column Component A: Dual Pie Charts
        HBox chartsRow = new HBox(14);
        chartsRow.setAlignment(Pos.TOP_LEFT);

        // Left Column Component B: System User Directory Table
        VBox directoryTray = new VBox(14);
        directoryTray.getStyleClass().add("clay-tray");
        directoryTray.setFillWidth(true);

        TableView<User> userTable = Ui.table("No accounts found matching filter.");
        userTable.setPrefHeight(290);
        userTable.setMinHeight(220);

        Ui.addColumn(userTable, "Username", u -> "@" + u.getUsername());
        Ui.addColumn(userTable, "Full Name", User::getFullName);
        Ui.addColumn(userTable, "Email Address", User::getEmail);
        userTable.getColumns().add(Ui.actionColumn("Role", u -> {
            Role r = u.getRole();
            String pillClass = switch (r != null ? r : Role.STUDENT) {
                case ADMIN -> "badge-pill-pink";
                case INSTRUCTOR -> "badge-pill-yellow";
                case STUDENT -> "badge-pill-blue";
                case EMPLOYER -> "badge-pill-mint";
            };
            return Ui.badge(String.valueOf(r), pillClass);
        }));

        TextField searchUsers = Ui.field("Filter by username, name, or email...");
        searchUsers.setPrefWidth(240);

        ComboBox<String> roleFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All Roles", "STUDENT", "INSTRUCTOR", "EMPLOYER", "ADMIN"));
        roleFilter.setValue("All Roles");

        // Action column for editing users directly from the dashboard
        userTable.getColumns().add(Ui.actionColumn("Action", u -> {
            Button editBtn = new Button("Edit");
            editBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12;");
            return editBtn;
        }));

        // Directory Tray Header
        HBox trayHeader = new HBox(12);
        trayHeader.setAlignment(Pos.CENTER_LEFT);

        VBox trayTitles = new VBox(3);
        Label trayTitle = new Label("System User Directory");
        trayTitle.getStyleClass().add("tray-title");
        Label traySub = new Label("Recently active accounts and role allocations across the institution");
        traySub.getStyleClass().add("tray-subtitle");
        trayTitles.getChildren().addAll(trayTitle, traySub);

        Region traySpacer = new Region();
        HBox.setHgrow(traySpacer, Priority.ALWAYS);

        trayHeader.getChildren().addAll(trayTitles, traySpacer, searchUsers, roleFilter);
        directoryTray.getChildren().addAll(trayHeader, userTable);

        leftColumn.getChildren().addAll(chartsRow, directoryTray);
        dashboardBody.getChildren().addAll(leftColumn, rightColumnHolder);

        // Define self-refreshing logic for all dynamic widgets
        final Runnable[] refreshHolder = new Runnable[1];
        Runnable refreshDashboard = () -> {
            // Update Stat Cards
            row1.getChildren().clear();
            row2.getChildren().clear();

            VBox cardUsers = Ui.card("Total Users", String.valueOf(admin.users().size()), "card-cream");
            VBox cardStudents = Ui.card("Students", String.valueOf(admin.students().size()), "card-mint");
            VBox cardInstructors = Ui.card("Instructors", String.valueOf(admin.instructors().size()), "card-lavender");
            VBox cardEmployers = Ui.card("Employers", String.valueOf(admin.employers().size()), "card-pink");

            VBox cardSubjects = Ui.card("Subjects", String.valueOf(admin.subjects().size()), "card-yellow");
            VBox cardSkills = Ui.card("Technical Skills", String.valueOf(skills.skills().size()), "card-blue");
            VBox cardPortfolios = Ui.card("Portfolios", String.valueOf(portfolios.allPortfolios().size()), "card-cream");
            long pending = portfolios.allPortfolios().stream()
                    .filter(p -> "Pending".equalsIgnoreCase(p.getVisibility())).count();
            VBox cardPending = Ui.card("Pending Portfolios", String.valueOf(pending), "card-mint");

            for (VBox c : List.of(cardUsers, cardStudents, cardInstructors, cardEmployers,
                    cardSubjects, cardSkills, cardPortfolios, cardPending)) {
                HBox.setHgrow(c, Priority.ALWAYS);
                c.setMaxWidth(Double.MAX_VALUE);
            }

            row1.getChildren().addAll(cardUsers, cardStudents, cardInstructors, cardEmployers);
            row2.getChildren().addAll(cardSubjects, cardSkills, cardPortfolios, cardPending);

            // Update Charts Row
            chartsRow.getChildren().clear();
            Map<String, Long> enrollmentStatuses = DashboardCharts.countByStatus(
                    academic.allEnrollments().stream().map(Enrollment::getStatus).toList(),
                    List.of("Enrolled", "Dropped", "Completed"));
            Map<String, Long> portfolioStatuses = DashboardCharts.countByStatus(
                    portfolios.allPortfolios().stream().map(Portfolio::getVisibility).toList(),
                    List.of("Private", "Pending", "Approved", "Public"));

            Node enrollmentChart = DashboardCharts.statusSummary("Enrollment Status", enrollmentStatuses);
            Node portfolioChart = DashboardCharts.statusSummary("Portfolio Visibility", portfolioStatuses);
            HBox.setHgrow(enrollmentChart, Priority.ALWAYS);
            HBox.setHgrow(portfolioChart, Priority.ALWAYS);
            chartsRow.getChildren().addAll(enrollmentChart, portfolioChart);

            // Update Right Side Panel
            rightColumnHolder.getChildren().clear();
            VBox rightPanel = createRightSidePanel(pending, admin.subjects().size(), academic.allEnrollments().size(), () -> refreshHolder[0].run());
            rightColumnHolder.getChildren().add(rightPanel);

            // Update User Directory Table
            String q = searchUsers.getText() == null ? "" : searchUsers.getText().toLowerCase(Locale.ROOT);
            String selectedRole = roleFilter.getValue();
            List<User> filtered = admin.users().stream().filter(u -> {
                boolean matchesQuery = q.isBlank()
                        || String.valueOf(u.getUsername()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(u.getFullName()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(u.getEmail()).toLowerCase(Locale.ROOT).contains(q);
                boolean matchesRole = selectedRole == null || "All Roles".equals(selectedRole)
                        || String.valueOf(u.getRole()).equalsIgnoreCase(selectedRole);
                return matchesQuery && matchesRole;
            }).toList();
            userTable.setItems(FXCollections.observableArrayList(filtered));
        };
        refreshHolder[0] = refreshDashboard;

        // Wire Edit button action to refresh
        if (userTable.getColumns().size() > 4) {
            userTable.getColumns().remove(4);
        }
        userTable.getColumns().add(Ui.actionColumn("Action", u -> {
            Button editBtn = new Button("Edit");
            editBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 12;");
            editBtn.setOnAction(ev -> editUser(u, refreshDashboard));
            return editBtn;
        }));

        // Hook up filters
        searchUsers.textProperty().addListener((obs, old, val) -> refreshDashboard.run());
        roleFilter.valueProperty().addListener((obs, old, val) -> refreshDashboard.run());

        // Initial populate
        refreshDashboard.run();

        VBox scrollContent = new VBox(20);
        scrollContent.setFillWidth(true);
        scrollContent.setStyle("-fx-background-color: #f1f2fa;");
        scrollContent.setPadding(new Insets(10, 24, 24, 24));
        scrollContent.getChildren().addAll(row1, row2, dashboardBody);

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
        dashboardRoot.setStyle("-fx-background-color: #f1f2fa;");
        dashboardRoot.setTop(headerContainer);
        dashboardRoot.setCenter(scrollPane);

        // Bind to real-time database connection monitoring and live data synchronization
        Consumer<SystemService.DatabaseStatus> liveDashboardListener = dbStatus -> {
            boolean connected = dbStatus.connected();
            Ui.setStatusDot(statusDot, connected);
            statusText.setText(connected ? "Systems Operational" : "Database Disconnected");
            statusText.setStyle(connected
                    ? "-fx-font-weight: bold; -fx-text-fill: #2d6a4f; -fx-font-size: 12px;"
                    : "-fx-font-weight: bold; -fx-text-fill: #b3261e; -fx-font-size: 12px;");
            liveChip.getStyleClass().setAll("badge-pill", connected ? "badge-pill-mint" : "badge-pill-pink");

            if (connected) {
                refreshHolder[0].run();
            }
        };

        SystemService.getInstance().addListener(liveDashboardListener);
        dashboardRoot.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                SystemService.getInstance().removeListener(liveDashboardListener);
            }
        });

        return dashboardRoot;
    }

    private static VBox createRightSidePanel(long pendingPortfolios, int totalSubjects, int totalEnrollments, Runnable refresh) {
        VBox rightSide = new VBox(16);
        rightSide.setPrefWidth(360);
        rightSide.setMinWidth(320);

        // 1. Quick Administrative Actions Launcher
        VBox quickActionsCard = new VBox(12);
        quickActionsCard.getStyleClass().add("clay-tray");

        Label actTitle = new Label("Quick Actions");
        actTitle.getStyleClass().add("tray-title");
        Label actSub = new Label("Instant administrative shortcuts");
        actSub.getStyleClass().add("tray-subtitle");

        Button addUserBtn = new Button("Add New User");
        addUserBtn.setMaxWidth(Double.MAX_VALUE);
        addUserBtn.getStyleClass().add("primary-button");
        addUserBtn.setOnAction(e -> editUser(null, refresh));

        Button assignProfBtn = new Button("Assign Professor");
        assignProfBtn.setMaxWidth(Double.MAX_VALUE);
        assignProfBtn.getStyleClass().add("secondary-button");
        assignProfBtn.setOnAction(e -> assignProfessorModal(null, refresh));

        Button addSubjectBtn = new Button("Create Subject");
        addSubjectBtn.setMaxWidth(Double.MAX_VALUE);
        addSubjectBtn.getStyleClass().add("secondary-button");
        addSubjectBtn.setOnAction(e -> editSubject(null, refresh));

        Button verifHubBtn = new Button("🛡️ Verification Hub");
        verifHubBtn.setMaxWidth(Double.MAX_VALUE);
        verifHubBtn.getStyleClass().add("primary-button");
        verifHubBtn.setOnAction(e -> AppController.getInstance().setView(verificationHub()));

        Button refreshBtn = new Button("Refresh System Data");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> {
            refresh.run();
            Alerts.info("System data refreshed.");
        });

        quickActionsCard.getChildren().addAll(new VBox(3, actTitle, actSub), verifHubBtn, addUserBtn, assignProfBtn, addSubjectBtn, refreshBtn);

        // 2. Action Required & Platform Diagnostics
        VBox queueTray = createHealthAndQueueTray(pendingPortfolios, totalSubjects, totalEnrollments);

        // 3. Recent Platform Activity
        VBox activityCard = new VBox(12);
        activityCard.getStyleClass().add("clay-tray");
        Label feedTitle = new Label("Recent Accounts");
        feedTitle.getStyleClass().add("tray-title");
        Label feedSub = new Label("Latest registered members");
        feedSub.getStyleClass().add("tray-subtitle");
        activityCard.getChildren().add(new VBox(3, feedTitle, feedSub));

        List<User> recentUsers = admin.users().stream()
                .sorted((a, b) -> Integer.compare(b.getUserId(), a.getUserId()))
                .limit(4)
                .toList();

        for (User u : recentUsers) {
            HBox item = new HBox(10);
            item.setAlignment(Pos.CENTER_LEFT);
            item.getStyleClass().add("activity-item");

            StackPane avatar = Ui.avatar(Ui.initials(u.getFullName()), "avatar-pink");
            avatar.setPrefSize(34, 34);
            avatar.setMinSize(34, 34);

            VBox info = new VBox(2);
            Label name = new Label(Ui.value(u.getFullName()));
            name.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #383139;");
            Label uname = new Label("@" + u.getUsername());
            uname.setStyle("-fx-font-size: 10px; -fx-text-fill: #8e8394;");
            info.getChildren().addAll(name, uname);
            HBox.setHgrow(info, Priority.ALWAYS);

            Role r = u.getRole() != null ? u.getRole() : Role.STUDENT;
            String pillClass = switch (r) {
                case ADMIN -> "badge-pill-pink";
                case INSTRUCTOR -> "badge-pill-yellow";
                case STUDENT -> "badge-pill-blue";
                case EMPLOYER -> "badge-pill-mint";
            };
            Label roleBadge = Ui.badge(String.valueOf(r), pillClass);
            item.getChildren().addAll(avatar, info, roleBadge);
            activityCard.getChildren().add(item);
        }

        rightSide.getChildren().addAll(quickActionsCard, queueTray, activityCard);
        return rightSide;
    }

    private static VBox createHealthAndQueueTray(long pendingPortfolios, int totalSubjects, int totalEnrollments) {
        VBox tray = new VBox(14);
        tray.getStyleClass().add("clay-tray");

        Label title = new Label("System Status & Queue");
        title.getStyleClass().add("tray-title");
        Label sub = new Label("Platform infrastructure & pending tasks");
        sub.getStyleClass().add("tray-subtitle");
        VBox titleBox = new VBox(3, title, sub);

        // Action Queue Section
        VBox queueBox = new VBox(8);
        Label queueHeader = new Label("ACTION REQUIRED");
        queueHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #8e8394; -fx-letter-spacing: 0.5px;");

        HBox verifRow = new HBox(8);
        verifRow.setAlignment(Pos.CENTER_LEFT);
        Label verifLabel = new Label("Pending Verifications:");
        verifLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #383139;");
        Region vSpacer = new Region();
        HBox.setHgrow(vSpacer, Priority.ALWAYS);
        long pendingVerifs = admin.pendingVerificationRequests().size();
        Node verifBadge = pendingVerifs > 0
                ? Ui.badge(pendingVerifs + " Awaiting Review", "badge-pill-pink")
                : Ui.badge("All Cleared (0)", "badge-pill-mint");
        verifRow.getChildren().addAll(verifLabel, vSpacer, verifBadge);

        HBox pendingRow = new HBox(8);
        pendingRow.setAlignment(Pos.CENTER_LEFT);
        Label pendingLabel = new Label("Pending Portfolios:");
        pendingLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #383139;");
        Region pSpacer = new Region();
        HBox.setHgrow(pSpacer, Priority.ALWAYS);
        Node pendingBadge = pendingPortfolios > 0
                ? Ui.badge(pendingPortfolios + " Awaiting Review", "badge-pill-pink")
                : Ui.badge("All Cleared (0)", "badge-pill-mint");
        pendingRow.getChildren().addAll(pendingLabel, pSpacer, pendingBadge);

        HBox subjectRow = new HBox(8);
        subjectRow.setAlignment(Pos.CENTER_LEFT);
        Label subjLabel = new Label("Active Subjects:");
        subjLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #383139;");
        Region sSpacer = new Region();
        HBox.setHgrow(sSpacer, Priority.ALWAYS);
        Node subjBadge = Ui.badge(totalSubjects + " Registered", "badge-pill-cream");
        subjectRow.getChildren().addAll(subjLabel, sSpacer, subjBadge);

        HBox enrollRow = new HBox(8);
        enrollRow.setAlignment(Pos.CENTER_LEFT);
        Label enrollLabel = new Label("Total Enrollments:");
        enrollLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #383139;");
        Region eSpacer = new Region();
        HBox.setHgrow(eSpacer, Priority.ALWAYS);
        Node enrollBadge = Ui.badge(totalEnrollments + " Enrolled", "badge-pill-lavender");
        enrollRow.getChildren().addAll(enrollLabel, eSpacer, enrollBadge);

        queueBox.getChildren().addAll(queueHeader, verifRow, pendingRow, subjectRow, enrollRow);

        Separator sep = new Separator();

        // Infrastructure Section
        VBox infraBox = new VBox(8);
        Label infraHeader = new Label("SYSTEM ENVIRONMENT");
        infraHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #8e8394; -fx-letter-spacing: 0.5px;");

        HBox dbRow = new HBox(8);
        dbRow.setAlignment(Pos.CENTER_LEFT);
        Label dbLabel = new Label("Database Storage:");
        dbLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #554d56;");
        Region dSpacer = new Region();
        HBox.setHgrow(dSpacer, Priority.ALWAYS);
        Node dbBadge = Ui.badge("MariaDB / Supabase", "badge-pill-blue");
        dbRow.getChildren().addAll(dbLabel, dSpacer, dbBadge);

        HBox secRow = new HBox(8);
        secRow.setAlignment(Pos.CENTER_LEFT);
        Label secLabel = new Label("Security Clearance:");
        secLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #554d56;");
        Region secSpacer = new Region();
        HBox.setHgrow(secSpacer, Priority.ALWAYS);
        Node secBadge = Ui.badge("Superuser Admin", "badge-pill-pink");
        secRow.getChildren().addAll(secLabel, secSpacer, secBadge);

        infraBox.getChildren().addAll(infraHeader, dbRow, secRow);

        tray.getChildren().addAll(titleBox, queueBox, sep, infraBox);
        return tray;
    }

    public static Node users() {
        List<User> all = admin.users();
        TableView<User> table = Ui.table();
        Ui.addColumn(table, "Username", User::getUsername);
        Ui.addColumn(table, "Full Name", User::getFullName);
        Ui.addColumn(table, "Email", User::getEmail);
        Ui.addColumn(table, "Role", u -> String.valueOf(u.getRole()));
        Ui.addColumn(table, "Verification", u -> u.getVerificationStatus() != null ? u.getVerificationStatus().displayName() : "Verified");
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search users...");
        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(admin.users().stream().filter(u ->
                    String.valueOf(u.getUsername()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(u.getFullName()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(u.getEmail()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(u.getRole()).toLowerCase(Locale.ROOT).contains(q)
                            || (u.getVerificationStatus() != null && u.getVerificationStatus().displayName().toLowerCase(Locale.ROOT).contains(q))).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button add = new Button("Add User");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> editUser(null, applyFilter));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a user to edit."); return; }
            editUser(selected, applyFilter);
        });
        Button suspend = new Button("Suspend");
        suspend.setOnAction(e -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a user to suspend."); return; }
            if (selected.getUserId() == com.nextit.security.Session.currentUser().getUserId()) {
                Alerts.error("You cannot suspend your own account.");
                return;
            }
            TextInputDialog tid = new TextInputDialog("Security concern / suspicious activity");
            tid.setTitle("Suspend Account");
            tid.setHeaderText("Suspend User @" + selected.getUsername());
            tid.setContentText("Enter suspension reason:");
            tid.showAndWait().ifPresent(reason -> guard(() -> {
                admin.suspendUser(selected.getUserId(), reason);
                applyFilter.run();
                Alerts.info("User @" + selected.getUsername() + " has been suspended.");
            }));
        });
        Button reinstate = new Button("Reinstate");
        reinstate.setOnAction(e -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a user to reinstate."); return; }
            guard(() -> {
                admin.reinstateUser(selected.getUserId());
                applyFilter.run();
                Alerts.info("User @" + selected.getUsername() + " has been reinstated.");
            });
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a user to delete."); return; }
            if (selected.getUserId() == com.nextit.security.Session.currentUser().getUserId()) {
                Alerts.error("You cannot delete the account you are signed in with.");
                return;
            }
            if (Ui.confirmed("Delete user \"" + selected.getUsername() + "\"? This also removes their profile and related records.")) {
                guard(() -> { admin.deleteUser(selected.getUserId()); applyFilter.run(); });
            }
        });
        Button resetPassword = new Button("Reset Password");
        resetPassword.setOnAction(e -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a user whose password should be reset."); return; }
            PasswordField password = Ui.password("At least 6 characters");
            Node form = Ui.form(new String[]{"User", "New password"},
                    new Node[]{new Label(selected.getFullName() + " (" + selected.getUsername() + ")"), password});
            Ui.dialog("Reset Password", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    admin.resetPassword(selected.getUserId(), password.getText());
                    Alerts.info("Password reset. Share the new password with the user securely.");
                });
            });
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        Button exportCsv = Ui.button("📥 Export CSV", "clay-btn-secondary");
        exportCsv.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToCsv(table, "user_accounts.csv", "System User Accounts", exportCsv.getScene().getWindow()));
        Button exportReport = Ui.button("📄 Print / PDF", "clay-btn-secondary");
        exportReport.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToHtmlReport(table, "user_accounts_report.html", "System User Accounts", exportReport.getScene().getWindow()));
        return Ui.page("User Accounts", "Create and maintain accounts for students, instructors, employers and administrators.",
                Ui.toolbar(new Label("Search:"), search, add, edit, resetPassword, suspend, reinstate, delete, refresh, exportCsv, exportReport), table);
    }

    private static void editUser(User existing, Runnable refresh) {
        User u = existing == null ? new User() : existing;
        TextField username = Ui.field("Username");
        username.setText(u.getUsername());
        TextField email = Ui.field("name@example.com");
        email.setText(u.getEmail());
        TextField first = Ui.field("First name");
        first.setText(u.getFirstName());
        TextField last = Ui.field("Last name");
        last.setText(u.getLastName());
        ComboBox<Role> role = Ui.combo(List.of(Role.values()), Enum::name);
        role.getSelectionModel().select(u.getRole() == null ? Role.STUDENT : u.getRole());
        role.setDisable(existing != null);
        PasswordField password = Ui.password("At least 6 characters");
        Node form = existing == null
                ? Ui.form(new String[]{"Username", "Email", "First name", "Last name", "Role", "Password"},
                    new Node[]{username, email, first, last, role, password})
                : Ui.form(new String[]{"Username", "Email", "First name", "Last name", "Role"},
                    new Node[]{username, email, first, last, role});
        Ui.dialog(existing == null ? "Add User" : "Edit User", form).ifPresent(button -> {
            if (button != ButtonType.OK) return;
            guard(() -> {
                User target = new User();
                if (existing != null) {
                    target.setUserId(existing.getUserId());
                    target.setPasswordHash(existing.getPasswordHash());
                    target.setCreatedAt(existing.getCreatedAt());
                    target.setUpdatedAt(existing.getUpdatedAt());
                }
                target.setUsername(username.getText());
                target.setEmail(email.getText());
                target.setFirstName(first.getText());
                target.setLastName(last.getText());
                target.setRole(existing == null ? role.getValue() : existing.getRole());
                if (existing == null) admin.createUser(target, password.getText());
                else admin.updateUser(target);
                refresh.run();
                Alerts.info("User saved.");
            });
        });
    }

    public static Node subjects() {
        List<Subject> all = admin.subjects();
        TableView<Subject> table = Ui.table();
        Ui.addColumn(table, "Code", Subject::getSubjectCode);
        Ui.addColumn(table, "Name", Subject::getSubjectName);
        Ui.addColumn(table, "Units", s -> String.valueOf(s.getUnits()));
        Ui.addColumn(table, "Description", Subject::getDescription);
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search by code, name or description...");
        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(admin.subjects().stream().filter(s ->
                    String.valueOf(s.getSubjectCode()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getSubjectName()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getDescription()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button assignBtn = new Button("Assign Professor");
        assignBtn.getStyleClass().add("primary-button");
        assignBtn.setOnAction(e -> assignProfessorModal(table.getSelectionModel().getSelectedItem(), applyFilter));

        Button add = new Button("Add Subject");
        add.setOnAction(e -> editSubject(null, applyFilter));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> {
            Subject selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a subject to edit."); return; }
            editSubject(selected, applyFilter);
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            Subject selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a subject to delete."); return; }
            if (Ui.confirmed("Delete subject \"" + selected.getSubjectCode() + "\"?")) {
                guard(() -> { admin.deleteSubject(selected.getSubjectId()); applyFilter.run(); });
            }
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Subjects", "Maintain the academic subject catalogue used by enrollments, grades and projects.",
                Ui.toolbar(new Label("Search:"), search, assignBtn, add, edit, delete, refresh), table);
    }

    private static void assignProfessorModal(Subject preselected, Runnable refresh) {
        List<Subject> subjects = admin.subjects();
        if (subjects.isEmpty()) {
            Alerts.error("No subjects available in catalog.");
            return;
        }

        ComboBox<Subject> subjectCombo = Ui.combo(subjects, s -> s.getSubjectCode() + " - " + s.getSubjectName());
        if (preselected != null) {
            subjectCombo.setValue(preselected);
        } else {
            subjectCombo.getSelectionModel().selectFirst();
        }

        List<Instructor> instructors = directory.instructors();
        Map<Integer, String> instructorNames = directory.instructorNames();
        if (instructors.isEmpty()) {
            Alerts.error("No professors or instructors found in directory.");
            return;
        }

        ComboBox<Instructor> instructorCombo = Ui.combo(instructors, inst -> {
            String name = instructorNames.getOrDefault(inst.getInstructorId(), "Instructor #" + inst.getInstructorId());
            String dept = inst.getDepartment() != null && !inst.getDepartment().isBlank() ? " (" + inst.getDepartment() + ")" : "";
            return name + dept;
        });
        instructorCombo.getSelectionModel().selectFirst();

        ComboBox<String> termCombo = Ui.combo(SEMESTERS, s -> s);
        termCombo.setValue("1st Semester");

        TextField sectionField = Ui.field("e.g. BSIT-3A • Room 402");
        sectionField.setText("Section A • Room 402");

        Node form = Ui.form(
                new String[]{"Desired Subject", "Professor / Instructor", "Academic Term", "Section / Room"},
                new Node[]{subjectCombo, instructorCombo, termCombo, sectionField}
        );

        Ui.dialog("Assign Professor to Subject", form).ifPresent(button -> {
            if (button != ButtonType.OK) return;
            Subject s = subjectCombo.getValue();
            Instructor i = instructorCombo.getValue();
            if (s == null || i == null) {
                Alerts.error("Please select both a subject and a professor.");
                return;
            }
            String profName = instructorNames.getOrDefault(i.getInstructorId(), "Instructor #" + i.getInstructorId());
            Alerts.info("Successfully assigned " + profName + " to "
                    + s.getSubjectCode() + " (" + s.getSubjectName() + ") for " + termCombo.getValue() + ".");
            if (refresh != null) refresh.run();
        });
    }

    private static void editSubject(Subject existing, Runnable refresh) {
        Subject s = existing == null ? new Subject() : existing;
        TextField code = Ui.field("e.g. IT301");
        code.setText(s.getSubjectCode());
        TextField name = Ui.field("Subject name");
        name.setText(s.getSubjectName());
        TextField units = Ui.field("Units");
        units.setText(existing == null ? "3.0" : String.valueOf(s.getUnits()));
        TextArea description = Ui.area("Description");
        description.setText(s.getDescription());
        Node form = Ui.form(new String[]{"Code", "Name", "Units", "Description"},
                new Node[]{code, name, units, description});
        Ui.dialog(existing == null ? "Add Subject" : "Edit Subject", form).ifPresent(button -> {
            if (button != ButtonType.OK) return;
            guard(() -> {
                Subject target = new Subject();
                if (existing != null) target.setSubjectId(existing.getSubjectId());
                target.setSubjectCode(code.getText());
                target.setSubjectName(name.getText());
                target.setUnits(Ui.parseDouble(units.getText(), "Units"));
                target.setDescription(description.getText());
                admin.saveSubject(target);
                refresh.run();
                Alerts.info("Subject saved.");
            });
        });
    }

    public static Node enrollments() {
        Map<Integer, String> students = directory.studentNames();
        Map<Integer, String> subjects = directory.subjectNames();
        TableView<Enrollment> table = Ui.table();
        Ui.addColumn(table, "Student", e -> students.getOrDefault(e.getStudentId(), "Student #" + e.getStudentId()));
        Ui.addColumn(table, "Subject", e -> subjects.getOrDefault(e.getSubjectId(), "Subject #" + e.getSubjectId()));
        Ui.addColumn(table, "Academic Year", Enrollment::getAcademicYear);
        Ui.addColumn(table, "Semester", Enrollment::getSemester);
        Ui.addColumn(table, "Status", Enrollment::getStatus);
        List<Enrollment> all = academic.allEnrollments();
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search by student, subject, term or status...");
        Runnable applyFilter = () -> {
            students.clear();
            students.putAll(directory.studentNames());
            subjects.clear();
            subjects.putAll(directory.subjectNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(academic.allEnrollments().stream().filter(e ->
                    students.getOrDefault(e.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || subjects.getOrDefault(e.getSubjectId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(e.getAcademicYear()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(e.getSemester()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(e.getStatus()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button add = new Button("Enroll Student");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> editEnrollment(null, applyFilter));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> {
            Enrollment selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an enrollment to edit."); return; }
            editEnrollment(selected, applyFilter);
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            Enrollment selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an enrollment to delete."); return; }
            if (Ui.confirmed("Delete this enrollment? Its grades and attendance records will also be removed.")) {
                guard(() -> {
                    academic.deleteEnrollment(selected.getEnrollmentId());
                    applyFilter.run();
                });
            }
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Enrollments", "Associate students with the subjects they are taking so grades and attendance can be recorded.",
                Ui.toolbar(new Label("Search:"), search, add, edit, delete, refresh), table);
    }

    private static void editEnrollment(Enrollment existing, Runnable refresh) {
        List<Student> studentList = directory.students();
        List<Subject> subjectList = directory.subjects();
        Map<Integer, String> names = directory.studentNames();
        ComboBox<Student> student = Ui.combo(studentList,
                s -> names.getOrDefault(s.getStudentId(), "Student #" + s.getStudentId()) + " (" + value(s.getStudentNumber()) + ")");
        ComboBox<Subject> subject = Ui.combo(subjectList, s -> s.getSubjectCode() + " - " + s.getSubjectName());
        TextField year = Ui.field("Academic year");
        ComboBox<String> semester = Ui.combo(SEMESTERS, s -> s);
        ComboBox<String> status = Ui.combo(ENROLLMENT_STATUSES, s -> s);

        if (existing == null) {
            int current = LocalDate.now().getYear();
            year.setText(LocalDate.now().getMonthValue() >= 6
                    ? current + "-" + (current + 1) : (current - 1) + "-" + current);
            semester.getSelectionModel().selectFirst();
            status.getSelectionModel().selectFirst();
        } else {
            studentList.stream().filter(s -> s.getStudentId() == existing.getStudentId())
                    .findFirst().ifPresent(student.getSelectionModel()::select);
            subjectList.stream().filter(s -> s.getSubjectId() == existing.getSubjectId())
                    .findFirst().ifPresent(subject.getSelectionModel()::select);
            year.setText(existing.getAcademicYear());
            semester.getSelectionModel().select(existing.getSemester());
            status.getSelectionModel().select(existing.getStatus());
        }

        if (existing != null) {
            student.setDisable(true);
            subject.setDisable(true);
        }
        Node form = Ui.form(new String[]{"Student", "Subject", "Academic year", "Semester", "Status"},
                new Node[]{student, subject, year, semester, status});
        Ui.dialog(existing == null ? "Enroll Student" : "Edit Enrollment", form).ifPresent(button -> {
            if (button != ButtonType.OK) return;
            guard(() -> {
                if (student.getValue() == null) throw new AppException("Select a student.");
                if (subject.getValue() == null) throw new AppException("Select a subject.");
                Enrollment target = new Enrollment();
                if (existing != null) target.setEnrollmentId(existing.getEnrollmentId());
                target.setStudentId(student.getValue().getStudentId());
                target.setSubjectId(subject.getValue().getSubjectId());
                target.setAcademicYear(year.getText());
                target.setSemester(semester.getValue());
                target.setStatus(status.getValue());
                if (existing == null) academic.saveEnrollment(target);
                else academic.updateEnrollment(target);
                refresh.run();
                Alerts.info("Enrollment saved.");
            });
        });
    }

    public static Node skills() {
        return SkillCatalogView.view();
    }

    public static Node grades() {
        ComboBox<Instructor> acting = AcademicManagementViews.instructorSelector();
        return AcademicManagementViews.grades(
                () -> AcademicManagementViews.selectedInstructor(acting),
                AcademicManagementViews.instructorControl(acting));
    }

    public static Node attendance() {
        ComboBox<Instructor> acting = AcademicManagementViews.instructorSelector();
        return AcademicManagementViews.attendance(
                () -> AcademicManagementViews.selectedInstructor(acting),
                AcademicManagementViews.instructorControl(acting));
    }

    public static Node technicalEvaluations() {
        ComboBox<Instructor> acting = AcademicManagementViews.instructorSelector();
        return AcademicManagementViews.technicalEvaluations(
                () -> AcademicManagementViews.selectedInstructor(acting),
                AcademicManagementViews.instructorControl(acting));
    }

    public static Node projectEvaluations() {
        ComboBox<Instructor> acting = AcademicManagementViews.instructorSelector();
        return AcademicManagementViews.evaluations(
                () -> AcademicManagementViews.selectedInstructor(acting),
                AcademicManagementViews.instructorControl(acting));
    }

    public static Node academicSupport() {
        ComboBox<Instructor> acting = AcademicManagementViews.instructorSelector();
        return AcademicManagementViews.support(
                () -> AcademicManagementViews.selectedInstructor(acting),
                AcademicManagementViews.instructorControl(acting));
    }

    public static Node achievements() {
        return DevelopmentManagementViews.achievements();
    }

    public static Node certifications() {
        return DevelopmentManagementViews.certifications();
    }

    public static Node projects() {
        return DevelopmentManagementViews.projects();
    }

    public static Node opportunities() {
        return DevelopmentManagementViews.opportunities(true);
    }

    public static Node applications() {
        return DevelopmentManagementViews.applications(true);
    }

    public static Node students() {
        Map<Integer, String> names = directory.studentNames();
        TableView<Student> table = Ui.table();
        Ui.addColumn(table, "Student No.", Student::getStudentNumber);
        Ui.addColumn(table, "Name", s -> names.getOrDefault(s.getStudentId(), "Student #" + s.getStudentId()));
        Ui.addColumn(table, "Program", Student::getProgram);
        Ui.addColumn(table, "Year", s -> String.valueOf(s.getYearLevel()));
        Ui.addColumn(table, "Section", Student::getSection);
        Ui.addColumn(table, "Status", Student::getEnrollmentStatus);
        List<Student> all = admin.students();
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search by name, student number, program or section...");
        Runnable applyFilter = () -> {
            names.clear();
            names.putAll(directory.studentNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(admin.students().stream().filter(s ->
                    names.getOrDefault(s.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getStudentNumber()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getProgram()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(s.getSection()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button edit = new Button("Edit Profile");
        edit.setOnAction(e -> {
            Student selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a student to edit."); return; }
            TextField number = Ui.field("Student number");
            number.setText(selected.getStudentNumber());
            TextField program = Ui.field("Program");
            program.setText(selected.getProgram());
            TextField year = Ui.field("Year level");
            year.setText(String.valueOf(selected.getYearLevel()));
            TextField section = Ui.field("Section");
            section.setText(selected.getSection());
            ComboBox<String> status = Ui.combo(List.of("Enrolled", "On Leave", "Graduated", "Dropped"), s -> s);
            status.getSelectionModel().select(selected.getEnrollmentStatus());
            Node form = Ui.form(new String[]{"Student number", "Program", "Year level", "Section", "Enrollment status"},
                    new Node[]{number, program, year, section, status});
            Ui.dialog("Edit Student Profile", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    Student target = new Student();
                    target.setStudentId(selected.getStudentId());
                    target.setUserId(selected.getUserId());
                    target.setStudentNumber(number.getText());
                    target.setProgram(program.getText());
                    target.setYearLevel(Ui.parseInt(year.getText(), "Year level"));
                    target.setSection(section.getText());
                    target.setEnrollmentStatus(status.getValue());
                    admin.updateStudent(target);
                    applyFilter.run();
                    Alerts.info("Student profile updated.");
                });
            });
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Student Profiles", "Review and update student academic profile information.",
                Ui.toolbar(new Label("Search:"), search, edit, refresh), table);
    }

    public static Node instructors() {
        Map<Integer, String> names = directory.instructorNames();
        TableView<Instructor> table = Ui.table();
        Ui.addColumn(table, "Employee No.", Instructor::getEmployeeNumber);
        Ui.addColumn(table, "Name", i -> names.getOrDefault(i.getInstructorId(), "Instructor #" + i.getInstructorId()));
        Ui.addColumn(table, "Department", Instructor::getDepartment);
        Ui.addColumn(table, "Specialization", Instructor::getSpecialization);
        List<Instructor> all = admin.instructors();
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search by name, employee number, department or specialization...");
        Runnable applyFilter = () -> {
            names.clear();
            names.putAll(directory.instructorNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(admin.instructors().stream().filter(i ->
                    names.getOrDefault(i.getInstructorId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(i.getEmployeeNumber()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(i.getDepartment()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(i.getSpecialization()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button edit = new Button("Edit Profile");
        edit.setOnAction(e -> {
            Instructor selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an instructor to edit."); return; }
            TextField number = Ui.field("Employee number");
            number.setText(selected.getEmployeeNumber());
            TextField department = Ui.field("Department");
            department.setText(selected.getDepartment());
            TextField specialization = Ui.field("Specialization");
            specialization.setText(selected.getSpecialization());
            Node form = Ui.form(new String[]{"Employee number", "Department", "Specialization"},
                    new Node[]{number, department, specialization});
            Ui.dialog("Edit Instructor Profile", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    Instructor target = new Instructor();
                    target.setInstructorId(selected.getInstructorId());
                    target.setUserId(selected.getUserId());
                    target.setEmployeeNumber(number.getText());
                    target.setDepartment(department.getText());
                    target.setSpecialization(specialization.getText());
                    admin.updateInstructor(target);
                    applyFilter.run();
                    Alerts.info("Instructor profile updated.");
                });
            });
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Instructor Profiles", "Review instructor departments and specializations.",
                Ui.toolbar(new Label("Search:"), search, edit, refresh), table);
    }

    public static Node employers() {
        TableView<Employer> table = Ui.table();
        Ui.addColumn(table, "Company", Employer::getCompanyName);
        Ui.addColumn(table, "Industry", Employer::getIndustry);
        Ui.addColumn(table, "Contact Person", Employer::getContactPerson);
        Ui.addColumn(table, "Contact Number", Employer::getContactNumber);
        Ui.addColumn(table, "Address", Employer::getAddress);
        List<Employer> all = admin.employers();
        table.setItems(FXCollections.observableArrayList(all));

        TextField search = Ui.field("Search by company, industry, contact or address...");
        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(admin.employers().stream().filter(e ->
                    String.valueOf(e.getCompanyName()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(e.getIndustry()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(e.getContactPerson()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(e.getAddress()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button edit = new Button("Edit Profile");
        edit.setOnAction(e -> {
            Employer selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an employer to edit."); return; }
            TextField company = Ui.field("Company name");
            company.setText(selected.getCompanyName());
            TextField industry = Ui.field("Industry");
            industry.setText(selected.getIndustry());
            TextField contact = Ui.field("Contact person");
            contact.setText(selected.getContactPerson());
            TextField number = Ui.field("Contact number");
            number.setText(selected.getContactNumber());
            TextField address = Ui.field("Address");
            address.setText(selected.getAddress());
            Node form = Ui.form(new String[]{"Company", "Industry", "Contact person", "Contact number", "Address"},
                    new Node[]{company, industry, contact, number, address});
            Ui.dialog("Edit Employer Profile", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    Employer target = new Employer();
                    target.setEmployerId(selected.getEmployerId());
                    target.setUserId(selected.getUserId());
                    target.setCompanyName(company.getText());
                    target.setIndustry(industry.getText());
                    target.setContactPerson(contact.getText());
                    target.setContactNumber(number.getText());
                    target.setAddress(address.getText());
                    admin.updateEmployer(target);
                    applyFilter.run();
                    Alerts.info("Employer profile updated.");
                });
            });
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Employer Profiles", "Review the companies and industry partners connected to NextIT.",
                Ui.toolbar(new Label("Search:"), search, edit, refresh), table);
    }

    public static Node portfolios() {
        Map<Integer, String> students = directory.studentNames();
        TableView<Portfolio> table = Ui.table();
        Ui.addColumn(table, "Student", p -> students.getOrDefault(p.getStudentId(), "Student #" + p.getStudentId()));
        Ui.addColumn(table, "Headline", Portfolio::getHeadline);
        Ui.addColumn(table, "Visibility", Portfolio::getVisibility);
        Ui.addColumn(table, "Updated", p -> p.getUpdatedAt() == null ? "" : p.getUpdatedAt().toLocalDate().toString());

        ComboBox<String> filter = Ui.combo(List.of("All portfolios", "Pending", "Approved", "Public", "Private"), s -> s);
        filter.getSelectionModel().selectFirst();
        TextField search = Ui.field("Search by student or headline...");
        Runnable applyFilter = () -> {
            students.clear();
            students.putAll(directory.studentNames());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String visibility = filter.getValue();
            table.setItems(FXCollections.observableArrayList(portfolios.allPortfolios().stream()
                    .filter(p -> "All portfolios".equals(visibility) || visibility.equalsIgnoreCase(p.getVisibility()))
                    .filter(p -> q.isBlank()
                            || students.getOrDefault(p.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(p.getHeadline()).toLowerCase(Locale.ROOT).contains(q))
                    .toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());
        filter.valueProperty().addListener((obs, old, value) -> applyFilter.run());
        applyFilter.run();

        Button view = new Button("View Portfolio");
        view.setOnAction(e -> {
            Portfolio selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a portfolio to view."); return; }
            guard(() -> PortfolioDetailsView.show(selected));
        });
        Button approve = new Button("Approve Selected");
        approve.getStyleClass().add("primary-button");
        approve.setOnAction(e -> {
            Portfolio selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a portfolio to approve."); return; }
            if ("Approved".equalsIgnoreCase(selected.getVisibility())) {
                Alerts.info("This portfolio is already approved.");
                return;
            }
            if (!"Pending".equalsIgnoreCase(selected.getVisibility())) {
                Alerts.error("This portfolio has not been submitted for approval.");
                return;
            }
            guard(() -> {
                portfolios.approve(selected.getPortfolioId());
                applyFilter.run();
                Alerts.info("Portfolio approved.");
            });
        });
        Button grant = new Button("Grant Employer Access");
        grant.setOnAction(e -> {
            Portfolio selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a portfolio first."); return; }
            ComboBox<Employer> employer = Ui.combo(directory.employers(), Employer::getCompanyName);
            Node form = Ui.form(new String[]{"Portfolio", "Employer"},
                    new Node[]{new Label(selected.getHeadline()), employer});
            Ui.dialog("Grant Portfolio Access", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    if (employer.getValue() == null) throw new AppException("Select an employer.");
                    portfolios.grantAccess(selected.getPortfolioId(), employer.getValue().getEmployerId());
                    Alerts.info("Portfolio access granted.");
                });
            });
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        Button revoke = new Button("Revoke Access");
        revoke.setOnAction(e -> {
            Portfolio selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a portfolio first."); return; }
            Map<Integer, String> employers = directory.employerNames();
            List<PortfolioAccess> active = portfolios.accessFor(selected.getPortfolioId()).stream()
                    .filter(a -> "Approved".equalsIgnoreCase(a.getAccessStatus()))
                    .toList();
            if (active.isEmpty()) {
                Alerts.info("This portfolio has no active employer access to revoke.");
                return;
            }
            ComboBox<PortfolioAccess> access = Ui.combo(active, a ->
                    employers.getOrDefault(a.getEmployerId(), "Employer #" + a.getEmployerId())
                            + (a.getGrantedAt() == null ? "" : " (granted " + a.getGrantedAt().toLocalDate() + ")"));
            Node form = Ui.form(new String[]{"Portfolio", "Employer access"},
                    new Node[]{new Label(selected.getHeadline()), access});
            Ui.dialog("Revoke Portfolio Access", form).ifPresent(button -> {
                if (button != ButtonType.OK) return;
                guard(() -> {
                    if (access.getValue() == null) throw new AppException("Select an employer access record.");
                    portfolios.revokeAccess(access.getValue().getAccessId());
                    Alerts.info("Portfolio access revoked.");
                });
            });
        });
        return Ui.page("Portfolio Approval", "Approve student portfolios, grant access to authorised employers, and revoke access when it is no longer needed.",
                Ui.toolbar(new Label("Search:"), search, new Label("Filter:"), filter, view, approve, grant, revoke, refresh), table);
    }

    public static Node system() {
        SystemService system = SystemService.getInstance();

        // 1. Live Status Row components (pure text, flush-left aligned, zero unicode emoji prefix)
        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label("Connected");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #10B981; -fx-font-size: 13px; -fx-padding: 0;");

        Label retryBadge = new Label("Retrying automatically...");
        retryBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.15); -fx-text-fill: #b3261e; -fx-background-radius: 999; -fx-padding: 2 10; -fx-font-size: 11px; -fx-font-weight: bold;");
        retryBadge.setVisible(false);
        retryBadge.setManaged(false);

        Label timestampLabel = new Label("Live • Just now");
        timestampLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8e8394;");

        statusRow.getChildren().addAll(statusLabel, retryBadge, timestampLabel);

        // 2. Interactive manual controls (instantiated early for background sync pulse)
        Button test = new Button("Test Connection");
        test.getStyleClass().add("primary-button");

        Button refreshButton = new Button("Refresh Counts");
        StackPane refreshIcon = Ui.refreshIcon();
        refreshButton.setGraphic(refreshIcon);
        refreshButton.getStyleClass().add("refresh-pill-button");
        Ui.RefreshIconController refreshController = new Ui.RefreshIconController(refreshButton, refreshIcon);

        test.setOnAction(e -> {
            test.setDisable(true);
            refreshButton.setDisable(true);
            test.setText("Testing...");
            system.checkNow().whenComplete((res, err) -> Platform.runLater(() -> {
                test.setDisable(false);
                refreshButton.setDisable(false);
                test.setText("Test Connection");
            }));
        });

        refreshButton.setOnAction(e -> {
            test.setDisable(true);
            refreshButton.setDisable(true);
            refreshController.startSpin();
            system.checkNow().whenComplete((res, err) -> Platform.runLater(() -> {
                test.setDisable(false);
                refreshButton.setDisable(false);
                refreshController.stopSpin();
            }));
        });

        // 3. Details, Configuration, and Account
        Label detail = new Label();
        detail.setWrapText(true);
        detail.getStyleClass().add("page-subtitle");

        Label url = new Label();
        url.setWrapText(true);
        url.getStyleClass().add("page-subtitle");

        Label user = new Label();
        user.getStyleClass().add("page-subtitle");

        // 4. Row Counts Table Header & Table
        Label staleBadge = Ui.badge("Stale (Offline)", "badge-pill-yellow");
        staleBadge.setVisible(false);
        staleBadge.setManaged(false);

        HBox tableHeaderBox = new HBox(10, Ui.section("Database Row Counts"), staleBadge);
        tableHeaderBox.setAlignment(Pos.CENTER_LEFT);

        TableView<SystemService.TableCount> table = Ui.table();
        table.setPrefHeight(380);
        Ui.addColumn(table, "Table", SystemService.TableCount::table);
        Ui.addColumn(table, "Rows", t -> t.rows() < 0 ? "Unavailable" : String.valueOf(t.rows()));

        // Keep last known counts to trigger a soft highlight flash on changes
        final Map<String, Integer> lastSeenCounts = new HashMap<>();

        // Live updater callback
        Consumer<SystemService.DatabaseStatus> onUpdate = current -> {
            boolean isConn = current.connected();
            statusLabel.setText(isConn ? "Connected" : "Disconnected");
            statusLabel.setStyle(isConn
                    ? "-fx-font-weight: bold; -fx-text-fill: #10B981; -fx-font-size: 13px; -fx-padding: 0;"
                    : "-fx-font-weight: bold; -fx-text-fill: #EF4444; -fx-font-size: 13px; -fx-padding: 0;");

            retryBadge.setVisible(!isConn);
            retryBadge.setManaged(!isConn);

            if (isConn) {
                if (current.lastCheckedAt() != null) {
                    long sec = Duration.between(current.lastCheckedAt(), Instant.now()).getSeconds();
                    timestampLabel.setText(sec <= 1 ? "Live • Updated just now" : "Live • Updated " + sec + "s ago");
                }
                detail.setText(current.details() != null ? current.details() : "NextIT can reach the configured database and read its schema.");
                staleBadge.setVisible(false);
                staleBadge.setManaged(false);
                table.setOpacity(1.0);
            } else {
                timestampLabel.setText("Offline");
                detail.setText(current.details() != null ? current.details() : "Connection refused or timed out. Check that MySQL is running.");
                staleBadge.setVisible(true);
                staleBadge.setManaged(true);
                table.setOpacity(0.65);
            }

            url.setText("JDBC URL: " + current.url());
            user.setText("Database user: " + current.user());

            // Check if counts changed
            boolean countsChanged = false;
            for (SystemService.TableCount tc : current.tables()) {
                Integer prev = lastSeenCounts.get(tc.table());
                if (prev == null || !prev.equals(tc.rows())) {
                    countsChanged = true;
                    lastSeenCounts.put(tc.table(), tc.rows());
                }
            }

            table.setItems(FXCollections.observableArrayList(current.tables()));

            if (countsChanged && isConn) {
                refreshController.pulseOnce();
                FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(300), table);
                ft.setFromValue(0.75);
                ft.setToValue(1.0);
                ft.play();
            }
        };

        // Register live listener with SystemService
        system.addListener(onUpdate);

        // Immediate fetch if current cached status available
        SystemService.DatabaseStatus initial = system.status();
        if (initial != null) {
            onUpdate.accept(initial);
        }

        // Ticker to keep "Live • Updated Xs ago" fresh
        Timeline ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), ev -> {
            SystemService.DatabaseStatus cur = system.getLatestStatus();
            if (cur != null && cur.connected() && cur.lastCheckedAt() != null) {
                long sec = Duration.between(cur.lastCheckedAt(), Instant.now()).getSeconds();
                timestampLabel.setText(sec <= 1 ? "Live • Updated just now" : "Live • Updated " + sec + "s ago");
            }
        }));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();

        Node summary = Ui.form(new String[]{"Status", "Details", "Configuration", "Account"},
                new Node[]{statusRow, detail, url, user});

        VBox page = Ui.page("System Configuration",
                "Read-only diagnostics for the local database configuration. Credentials are loaded from the environment, JVM properties, or the optional .env file.",
                Ui.toolbar(test, refreshButton), summary, tableHeaderBox, table);

        page.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                system.removeListener(onUpdate);
                ticker.stop();
            }
        });

        return page;
    }

    public static Node verificationHub() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: transparent;");

        Tab tabRequests = new Tab("📋 Verification Requests", createVerificationRequestsView());
        tabRequests.setClosable(false);

        Tab tabRegistrar = new Tab("🏛️ Institutional Database", createInstitutionalDatabaseView());
        tabRegistrar.setClosable(false);

        Tab tabAudit = new Tab("📜 Security Audit Trail", createSecurityAuditView());
        tabAudit.setClosable(false);

        Tab tabProvisionAdmin = new Tab("🔑 Provision Administrator", createProvisionAdminView());
        tabProvisionAdmin.setClosable(false);

        tabPane.getTabs().addAll(tabRequests, tabRegistrar, tabAudit, tabProvisionAdmin);

        return Ui.page("Identity Verification & Security Hub",
                "Review pending identity verification requests, cross-reference official university registrar records, manage audit logs, and provision administrators.",
                tabPane);
    }

    private static Node createVerificationRequestsView() {
        TableView<VerificationRequest> table = Ui.table();
        Ui.addColumn(table, "ID", r -> String.valueOf(r.getRequestId()));
        Ui.addColumn(table, "Applicant", r -> r.getUserFullName() + " (@" + r.getUsername() + ")");
        Ui.addColumn(table, "Role", r -> String.valueOf(r.getRole()));
        Ui.addColumn(table, "Claimed ID / TIN", r -> r.getIdNumber() != null && !r.getIdNumber().isBlank()
                ? r.getIdNumber() : (r.getBusinessRegistrationNumber() != null ? r.getBusinessRegistrationNumber() : "-"));
        Ui.addColumn(table, "Email", VerificationRequest::getInstitutionalEmail);
        Ui.addColumn(table, "Risk Score", r -> r.getRiskScore() + (r.getRiskScore() >= 50 ? " (HIGH RISK)" : (r.getRiskScore() >= 20 ? " (Moderate)" : " (Safe)")));
        Ui.addColumn(table, "Status", r -> r.getStatus() != null ? r.getStatus().displayName() : "Pending");
        Ui.addColumn(table, "Submitted", r -> r.getCreatedAt() != null ? r.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");

        TextField search = Ui.field("Search by applicant, username, email, ID number...");
        ComboBox<String> roleFilter = Ui.combo(List.of("All Roles", "STUDENT", "INSTRUCTOR", "EMPLOYER"), s -> s);
        roleFilter.getSelectionModel().selectFirst();
        ComboBox<String> statusFilter = Ui.combo(List.of("All Statuses", "PENDING", "VERIFIED", "REJECTED", "SUSPENDED"), s -> s);
        statusFilter.getSelectionModel().selectFirst();
        ComboBox<String> riskFilter = Ui.combo(List.of("All Risk Levels", "High Risk (Score >= 50)", "Moderate (Score >= 20)", "Low/Safe (Score < 20)"), s -> s);
        riskFilter.getSelectionModel().selectFirst();

        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String rf = roleFilter.getValue();
            String sf = statusFilter.getValue();
            String rkf = riskFilter.getValue();

            List<VerificationRequest> filtered = admin.verificationRequests().stream().filter(r -> {
                boolean matchesQ = q.isBlank()
                        || String.valueOf(r.getUsername()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(r.getUserFullName()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(r.getInstitutionalEmail()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(r.getIdNumber()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(r.getBusinessRegistrationNumber()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(r.getRiskFlags()).toLowerCase(Locale.ROOT).contains(q);
                boolean matchesRole = rf == null || "All Roles".equals(rf) || rf.equalsIgnoreCase(String.valueOf(r.getRole()));
                boolean matchesStatus = sf == null || "All Statuses".equals(sf) || sf.equalsIgnoreCase(String.valueOf(r.getStatus()));
                boolean matchesRisk;
                if ("High Risk (Score >= 50)".equals(rkf)) {
                    matchesRisk = r.getRiskScore() >= 50;
                } else if ("Moderate (Score >= 20)".equals(rkf)) {
                    matchesRisk = r.getRiskScore() >= 20 && r.getRiskScore() < 50;
                } else if ("Low/Safe (Score < 20)".equals(rkf)) {
                    matchesRisk = r.getRiskScore() < 20;
                } else {
                    matchesRisk = true;
                }
                return matchesQ && matchesRole && matchesStatus && matchesRisk;
            }).toList();
            table.setItems(FXCollections.observableArrayList(filtered));
        };

        search.textProperty().addListener((obs, old, val) -> applyFilter.run());
        roleFilter.valueProperty().addListener((obs, old, val) -> applyFilter.run());
        statusFilter.valueProperty().addListener((obs, old, val) -> applyFilter.run());
        riskFilter.valueProperty().addListener((obs, old, val) -> applyFilter.run());
        applyFilter.run();

        Button inspectBtn = new Button("🔍 Inspect & Compare");
        inspectBtn.getStyleClass().add("primary-button");
        inspectBtn.setOnAction(e -> {
            VerificationRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Select an identity verification request to inspect.");
                return;
            }
            inspectVerificationDialog(selected, applyFilter);
        });

        Button approveBtn = new Button("✅ Quick Approve");
        approveBtn.setOnAction(e -> {
            VerificationRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a request to approve."); return; }
            if (selected.getStatus() == VerificationStatus.VERIFIED) {
                Alerts.info("This application has already been verified.");
                return;
            }
            if (Ui.confirmed("Approve identity verification for @" + selected.getUsername() + " (" + selected.getUserFullName() + ")?")) {
                int reviewerId = com.nextit.security.Session.currentUser().getUserId();
                guard(() -> {
                    admin.approveVerification(selected.getRequestId(), reviewerId, "Quick approved by admin");
                    applyFilter.run();
                    Alerts.info("Identity verified successfully! Account is now active.");
                });
            }
        });

        Button rejectBtn = new Button("❌ Reject");
        rejectBtn.setOnAction(e -> {
            VerificationRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a request to reject."); return; }
            TextInputDialog tid = new TextInputDialog("Unable to verify institutional records");
            tid.setTitle("Reject Application");
            tid.setHeaderText("Reject Verification for @" + selected.getUsername());
            tid.setContentText("Enter rejection reason for applicant:");
            tid.showAndWait().ifPresent(reason -> guard(() -> {
                int reviewerId = com.nextit.security.Session.currentUser().getUserId();
                admin.rejectVerification(selected.getRequestId(), reviewerId, reason);
                applyFilter.run();
                Alerts.info("Application rejected. Reason recorded.");
            }));
        });

        Button suspendBtn = new Button("⛔ Suspend Account");
        suspendBtn.setOnAction(e -> {
            VerificationRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a request to suspend."); return; }
            TextInputDialog tid = new TextInputDialog("Suspicious identity submission / impersonation concern");
            tid.setTitle("Suspend Account");
            tid.setHeaderText("Suspend User @" + selected.getUsername());
            tid.setContentText("Enter suspension reason:");
            tid.showAndWait().ifPresent(reason -> guard(() -> {
                admin.suspendUser(selected.getUserId(), reason);
                applyFilter.run();
                Alerts.info("Account @" + selected.getUsername() + " has been suspended.");
            }));
        });

        Button reinstateBtn = new Button("🟢 Reinstate");
        reinstateBtn.setOnAction(e -> {
            VerificationRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an account to reinstate."); return; }
            guard(() -> {
                admin.reinstateUser(selected.getUserId());
                applyFilter.run();
                Alerts.info("Account @" + selected.getUsername() + " has been reinstated.");
            });
        });

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> applyFilter.run());

        Button exportCsv = Ui.button("📥 Export CSV", "clay-btn-secondary");
        exportCsv.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToCsv(table, "verification_requests.csv", "Identity Verification Requests", exportCsv.getScene().getWindow()));

        HBox filterToolbar = Ui.toolbar(new Label("Search:"), search,
                new Label("Role:"), roleFilter,
                new Label("Status:"), statusFilter,
                new Label("Risk:"), riskFilter);

        HBox actionToolbar = Ui.toolbar(inspectBtn, approveBtn, rejectBtn, suspendBtn, reinstateBtn, refreshBtn, exportCsv);

        VBox layout = new VBox(12, filterToolbar, actionToolbar, table);
        layout.setPadding(new Insets(14, 0, 0, 0));
        VBox.setVgrow(table, Priority.ALWAYS);
        return layout;
    }

    private static void inspectVerificationDialog(VerificationRequest req, Runnable onUpdated) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Identity Verification Inspector & Registrar Comparison");
        dialog.setHeaderText("Identity Authenticity Examination — Request #" + req.getRequestId());

        VBox root = new VBox(16);
        root.setPrefWidth(600);
        root.setStyle("-fx-padding: 10;");

        // 1. Applicant Submitted Information
        GridPane applicantGrid = new GridPane();
        applicantGrid.setHgap(16);
        applicantGrid.setVgap(10);
        applicantGrid.add(new Label("Applicant Full Name:"), 0, 0);
        Label nameLbl = new Label(req.getUserFullName() + " (@" + req.getUsername() + ")");
        nameLbl.setStyle("-fx-font-weight: bold;");
        applicantGrid.add(nameLbl, 1, 0);

        applicantGrid.add(new Label("Registered Email:"), 0, 1);
        Label emailLbl = new Label(req.getInstitutionalEmail());
        emailLbl.setStyle("-fx-font-weight: bold;");
        applicantGrid.add(emailLbl, 1, 1);

        applicantGrid.add(new Label("Role Claimed:"), 0, 2);
        applicantGrid.add(Ui.badge(String.valueOf(req.getRole()), "badge-pill-pink"), 1, 2);

        applicantGrid.add(new Label("ID / TIN Claimed:"), 0, 3);
        Label idLbl = new Label(req.getIdNumber() != null ? req.getIdNumber() : (req.getBusinessRegistrationNumber() != null ? req.getBusinessRegistrationNumber() : "None"));
        idLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        applicantGrid.add(idLbl, 1, 3);

        applicantGrid.add(new Label("Organization / School:"), 0, 4);
        applicantGrid.add(new Label(req.getOrganizationName() != null ? req.getOrganizationName() : "-"), 1, 4);

        applicantGrid.add(new Label("Submission Notes:"), 0, 5);
        Label notesLbl = new Label(req.getSubmissionNotes() != null ? req.getSubmissionNotes() : "-");
        notesLbl.setWrapText(true);
        applicantGrid.add(notesLbl, 1, 5);

        VBox applicantTray = Ui.tray("Submitted Applicant Information", "Submitted by user during account registration", applicantGrid);

        // 2. Cross-reference with Official Registrar Records
        JdbcInstitutionalRecordRepository instRepo = new JdbcInstitutionalRecordRepository();
        String searchId = req.getIdNumber() != null ? req.getIdNumber().trim() : "";
        Optional<InstitutionalRecord> registrarMatch = instRepo.findByIdentifier(searchId);

        VBox registrarTray;
        if (registrarMatch.isPresent()) {
            InstitutionalRecord rec = registrarMatch.get();
            GridPane regGrid = new GridPane();
            regGrid.setHgap(16);
            regGrid.setVgap(10);

            regGrid.add(new Label("Registrar Official Name:"), 0, 0);
            Label rName = new Label(rec.getFullName());
            rName.setStyle("-fx-font-weight: bold; -fx-text-fill: #10B981;");
            regGrid.add(rName, 1, 0);

            regGrid.add(new Label("Registrar Record Type:"), 0, 1);
            regGrid.add(Ui.badge(String.valueOf(rec.getRecordType()), "badge-pill-mint"), 1, 1);

            regGrid.add(new Label("Official Identifier:"), 0, 2);
            regGrid.add(new Label(rec.getIdentifierNumber()), 1, 2);

            regGrid.add(new Label("Official University Email:"), 0, 3);
            regGrid.add(new Label(rec.getInstitutionalEmail()), 1, 3);

            regGrid.add(new Label("Program / Department:"), 0, 4);
            regGrid.add(new Label(rec.getDepartmentOrProgram()), 1, 4);

            regGrid.add(new Label("Registrar Status:"), 0, 5);
            regGrid.add(Ui.badge(rec.isActive() ? "Active Enrolment" : "Inactive", rec.isActive() ? "badge-pill-mint" : "badge-pill-pink"), 1, 5);

            boolean exactEmail = req.getInstitutionalEmail() != null && req.getInstitutionalEmail().equalsIgnoreCase(rec.getInstitutionalEmail());
            Label matchBanner = new Label(exactEmail
                    ? "✓ Verified Match: Applicant matches authorized registrar record and institutional email."
                    : "⚠️ Warning: ID matched registrar record for '" + rec.getFullName() + "' but registered email differs ('" + req.getInstitutionalEmail() + "').");
            matchBanner.setStyle(exactEmail
                    ? "-fx-background-color: #E6F4EA; -fx-text-fill: #137333; -fx-padding: 8; -fx-background-radius: 8; -fx-font-weight: bold;"
                    : "-fx-background-color: #FEF7E0; -fx-text-fill: #B06000; -fx-padding: 8; -fx-background-radius: 8; -fx-font-weight: bold;" +
                      "; -fx-font-size: 11px;");

            registrarTray = Ui.tray("Authorized Registrar Cross-Reference", "Database comparison against school registrar pre-enrolled records",
                    new VBox(10, matchBanner, regGrid));
        } else {
            Label noMatchLbl = new Label("⚠️ No matching pre-enrolled registrar record found for identifier '" + searchId + "'. Manual verification of physical student card, appointment letter, or SEC/DTI certificate is required.");
            noMatchLbl.setWrapText(true);
            noMatchLbl.setStyle("-fx-background-color: #FCE8E6; -fx-text-fill: #C5221F; -fx-padding: 10; -fx-background-radius: 8; -fx-font-weight: bold; -fx-font-size: 11px;");
            registrarTray = Ui.tray("Authorized Registrar Cross-Reference", "Database comparison against school registrar records", noMatchLbl);
        }

        // 3. Anti-Fake Risk Indicators
        VBox riskTray = new VBox(8);
        riskTray.setStyle("-fx-background-color: #FFFDF7; -fx-border-color: #E2DFD2; -fx-border-radius: 12; -fx-padding: 12;");
        Label riskTitle = new Label("Risk Score: " + req.getRiskScore() + "/100 — " + (req.getRiskScore() >= 50 ? "HIGH RISK" : (req.getRiskScore() >= 20 ? "MODERATE RISK" : "LOW RISK / SAFE")));
        riskTitle.setStyle(req.getRiskScore() >= 50
                ? "-fx-font-weight: bold; -fx-text-fill: #C5221F; -fx-font-size: 13px;"
                : "-fx-font-weight: bold; -fx-text-fill: #137333; -fx-font-size: 13px;");

        Label flagsLbl = new Label("Detected Security Flags: " + (req.getRiskFlags() != null && !req.getRiskFlags().isBlank() ? req.getRiskFlags() : "None"));
        flagsLbl.setWrapText(true);
        flagsLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #5F6368;");

        riskTray.getChildren().addAll(riskTitle, flagsLbl);

        // 4. Decision Input
        TextField reviewNotesField = Ui.field("Enter verification notes or rejection reason...");
        VBox decisionBox = new VBox(6, new Label("Verifier Assessment Notes:"), reviewNotesField);

        root.getChildren().addAll(applicantTray, registrarTray, riskTray, decisionBox);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(520);
        dialog.getDialogPane().setContent(scroll);

        ButtonType btnApprove = new ButtonType("Approve Identity", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnReject = new ButtonType("Reject Application", ButtonBar.ButtonData.NO);
        ButtonType btnSuspend = new ButtonType("Suspend Account", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(btnApprove, btnReject, btnSuspend, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            int reviewerId = com.nextit.security.Session.currentUser().getUserId();
            String notes = reviewNotesField.getText().trim();
            if (btn == btnApprove) {
                guard(() -> {
                    admin.approveVerification(req.getRequestId(), reviewerId, notes.isBlank() ? "Approved by administrator" : notes);
                    onUpdated.run();
                    Alerts.info("Identity verification approved for @" + req.getUsername() + ".");
                });
            } else if (btn == btnReject) {
                if (notes.isBlank()) {
                    Alerts.error("Please provide a rejection reason in the verifier assessment notes field.");
                    return;
                }
                guard(() -> {
                    admin.rejectVerification(req.getRequestId(), reviewerId, notes);
                    onUpdated.run();
                    Alerts.info("Application rejected for @" + req.getUsername() + ".");
                });
            } else if (btn == btnSuspend) {
                guard(() -> {
                    admin.suspendUser(req.getUserId(), notes.isBlank() ? "Suspended by verifier during identity inspection" : notes);
                    onUpdated.run();
                    Alerts.info("Account @" + req.getUsername() + " has been suspended.");
                });
            }
        });
    }

    private static Node createInstitutionalDatabaseView() {
        TableView<InstitutionalRecord> table = Ui.table();
        Ui.addColumn(table, "ID", r -> String.valueOf(r.getRecordId()));
        Ui.addColumn(table, "Record Type", r -> String.valueOf(r.getRecordType()));
        Ui.addColumn(table, "Identifier Number", InstitutionalRecord::getIdentifierNumber);
        Ui.addColumn(table, "Institutional Email", InstitutionalRecord::getInstitutionalEmail);
        Ui.addColumn(table, "Full Name", InstitutionalRecord::getFullName);
        Ui.addColumn(table, "Department / Program", InstitutionalRecord::getDepartmentOrProgram);
        Ui.addColumn(table, "Status", r -> r.isActive() ? "Active" : "Inactive");

        TextField search = Ui.field("Search by identifier, email, name, program...");
        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            List<InstitutionalRecord> filtered = admin.institutionalRecords().stream().filter(r ->
                    q.isBlank()
                            || String.valueOf(r.getIdentifierNumber()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(r.getInstitutionalEmail()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(r.getFullName()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(r.getDepartmentOrProgram()).toLowerCase(Locale.ROOT).contains(q)
            ).toList();
            table.setItems(FXCollections.observableArrayList(filtered));
        };

        search.textProperty().addListener((obs, old, val) -> applyFilter.run());
        applyFilter.run();

        Button addBtn = new Button("➕ Add Record");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> editInstitutionalRecordDialog(null, applyFilter));

        Button editBtn = new Button("✏️ Edit Record");
        editBtn.setOnAction(e -> {
            InstitutionalRecord selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a record to edit."); return; }
            editInstitutionalRecordDialog(selected, applyFilter);
        });

        Button deleteBtn = new Button("🗑️ Delete");
        deleteBtn.setOnAction(e -> {
            InstitutionalRecord selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a record to delete."); return; }
            if (Ui.confirmed("Delete institutional record for " + selected.getFullName() + " (" + selected.getIdentifierNumber() + ")?")) {
                guard(() -> {
                    admin.deleteInstitutionalRecord(selected.getRecordId());
                    applyFilter.run();
                    Alerts.info("Institutional record deleted.");
                });
            }
        });

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> applyFilter.run());

        Button exportCsv = Ui.button("📥 Export CSV", "clay-btn-secondary");
        exportCsv.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToCsv(table, "institutional_records.csv", "Authorized Institutional Records", exportCsv.getScene().getWindow()));

        HBox toolbar = Ui.toolbar(new Label("Search:"), search, addBtn, editBtn, deleteBtn, refreshBtn, exportCsv);
        VBox layout = new VBox(12, toolbar, table);
        layout.setPadding(new Insets(14, 0, 0, 0));
        VBox.setVgrow(table, Priority.ALWAYS);
        return layout;
    }

    private static void editInstitutionalRecordDialog(InstitutionalRecord existing, Runnable onDone) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Institutional Registrar Record" : "Edit Institutional Registrar Record");
        dialog.setHeaderText("Authorized University Identity Whitelist");

        ComboBox<Role> typeCombo = Ui.combo(List.of(Role.STUDENT, Role.INSTRUCTOR), Role::name);
        typeCombo.getSelectionModel().select(existing != null ? existing.getRecordType() : Role.STUDENT);

        TextField idField = Ui.field("Student Number (e.g. 2026-0042) or Faculty ID (EMP-1001)");
        if (existing != null) idField.setText(existing.getIdentifierNumber());

        TextField emailField = Ui.field("Institutional Email (e.g. name@nextit.edu)");
        if (existing != null) emailField.setText(existing.getInstitutionalEmail());

        TextField nameField = Ui.field("Full Official Name");
        if (existing != null) nameField.setText(existing.getFullName());

        TextField deptField = Ui.field("Degree Program or Faculty Department");
        if (existing != null) deptField.setText(existing.getDepartmentOrProgram());

        CheckBox activeCheck = new CheckBox("Active Enrollment / Employment Status");
        activeCheck.setSelected(existing == null || existing.isActive());

        Node form = Ui.form(new String[]{"Record Type", "Identifier Number", "Institutional Email", "Full Official Name", "Department / Program", "Status"},
                new Node[]{typeCombo, idField, emailField, nameField, deptField, activeCheck});

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                guard(() -> {
                    InstitutionalRecord rec = existing != null ? existing : new InstitutionalRecord();
                    rec.setRecordType(typeCombo.getValue());
                    rec.setIdentifierNumber(idField.getText().trim());
                    rec.setInstitutionalEmail(emailField.getText().trim());
                    rec.setFullName(nameField.getText().trim());
                    rec.setDepartmentOrProgram(deptField.getText().trim());
                    rec.setActive(activeCheck.isSelected());

                    admin.saveInstitutionalRecord(rec);
                    onDone.run();
                    Alerts.info("Institutional record saved successfully.");
                });
            }
        });
    }

    private static Node createSecurityAuditView() {
        TableView<SecurityAuditLog> table = Ui.table();
        Ui.addColumn(table, "ID", l -> String.valueOf(l.getLogId()));
        Ui.addColumn(table, "Timestamp", l -> l.getCreatedAt() != null ? l.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        Ui.addColumn(table, "Actor", l -> "@" + l.getActorUsername());
        Ui.addColumn(table, "Action", SecurityAuditLog::getAction);
        Ui.addColumn(table, "Target", l -> l.getTargetUserId() != null ? "#" + l.getTargetUserId() : "-");
        Ui.addColumn(table, "Severity", SecurityAuditLog::getSeverity);
        Ui.addColumn(table, "IP Address", SecurityAuditLog::getIpAddress);
        Ui.addColumn(table, "Details", SecurityAuditLog::getDetails);

        TextField search = Ui.field("Search audit logs...");
        ComboBox<String> severityFilter = Ui.combo(List.of("All Severities", "INFO", "WARNING", "SECURITY_ALERT", "CRITICAL"), s -> s);
        severityFilter.getSelectionModel().selectFirst();

        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String sf = severityFilter.getValue();
            List<SecurityAuditLog> filtered = SecurityAuditService.getInstance().recentLogs(200).stream().filter(l -> {
                boolean matchesQ = q.isBlank()
                        || String.valueOf(l.getActorUsername()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(l.getAction()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(l.getDetails()).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(l.getIpAddress()).toLowerCase(Locale.ROOT).contains(q);
                boolean matchesSev = sf == null || "All Severities".equals(sf) || sf.equalsIgnoreCase(l.getSeverity());
                return matchesQ && matchesSev;
            }).toList();
            table.setItems(FXCollections.observableArrayList(filtered));
        };

        search.textProperty().addListener((obs, old, val) -> applyFilter.run());
        severityFilter.valueProperty().addListener((obs, old, val) -> applyFilter.run());
        applyFilter.run();

        Button refreshBtn = new Button("🔄 Refresh Logs");
        refreshBtn.setOnAction(e -> applyFilter.run());

        Button exportCsv = Ui.button("📥 Export CSV", "clay-btn-secondary");
        exportCsv.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToCsv(table, "security_audit_logs.csv", "System Security Audit Logs", exportCsv.getScene().getWindow()));

        Button exportReport = Ui.button("📄 Print / PDF Report", "clay-btn-secondary");
        exportReport.setOnAction(e -> com.nextit.util.ExportService.exportTableViewToHtmlReport(table, "security_audit_report.html", "System Security Audit Report", exportReport.getScene().getWindow()));

        HBox toolbar = Ui.toolbar(new Label("Search:"), search, new Label("Severity:"), severityFilter, refreshBtn, exportCsv, exportReport);
        VBox layout = new VBox(12, toolbar, table);
        layout.setPadding(new Insets(14, 0, 0, 0));
        VBox.setVgrow(table, Priority.ALWAYS);
        return layout;
    }

    private static Node createProvisionAdminView() {
        VBox card = new VBox(20);
        card.setMaxWidth(680);
        card.getStyleClass().add("clay-tray");
        card.setPadding(new Insets(30));

        Label title = new Label("Provision System Administrator Account");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #383139;");

        Label subtitle = new Label("Public administrator registration is permanently disabled. Only currently authenticated administrators can provision administrative access. In accordance with university cybersecurity policy, all administrator accounts require mandatory Multi-Factor Authentication (MFA).");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #48404B; -fx-line-spacing: 2px;");

        TextField username = Ui.field("Username (e.g. jcruz_admin)");
        TextField email = Ui.field("Official University Administrator Email (e.g. jcruz@nextit.edu)");
        TextField firstName = Ui.field("First Name");
        TextField lastName = Ui.field("Last Name");
        PasswordField password = Ui.password("Temporary Password (min 8 characters)");
        PasswordField confirmPass = Ui.password("Confirm Temporary Password");

        CheckBox mfaConfirmation = new CheckBox("I confirm that this person is an authorized system administrator and that Multi-Factor Authentication (MFA) will be enforced upon their initial sign-in.");
        mfaConfirmation.setSelected(true);
        mfaConfirmation.setWrapText(true);
        mfaConfirmation.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #383139;");

        Button submitBtn = new Button("🔑 Provision Administrator Account");
        submitBtn.getStyleClass().add("primary-button");
        submitBtn.setOnAction(e -> {
            String u = username.getText().trim();
            String em = email.getText().trim();
            String fn = firstName.getText().trim();
            String ln = lastName.getText().trim();
            String pw = password.getText();
            String cp = confirmPass.getText();

            if (u.isBlank() || em.isBlank() || fn.isBlank() || ln.isBlank() || pw.isBlank()) {
                Alerts.error("All administrator account fields are required.");
                return;
            }
            if (pw.length() < 8) {
                Alerts.error("Administrator passwords must be at least 8 characters.");
                return;
            }
            if (!pw.equals(cp)) {
                Alerts.error("Passwords do not match.");
                return;
            }
            if (!mfaConfirmation.isSelected()) {
                Alerts.error("You must confirm MFA compliance before provisioning an administrator.");
                return;
            }

            guard(() -> {
                User newAdmin = new User();
                newAdmin.setUsername(u);
                newAdmin.setEmail(em);
                newAdmin.setFirstName(fn);
                newAdmin.setLastName(ln);

                admin.provisionAdmin(newAdmin, pw);

                username.clear();
                email.clear();
                firstName.clear();
                lastName.clear();
                password.clear();
                confirmPass.clear();

                Alerts.info("Administrator account @" + u + " provisioned successfully!\nMulti-Factor Authentication will be enforced on sign-in.");
            });
        });

        Node form = Ui.form(new String[]{"Username", "Official Email", "First Name", "Last Name", "Temporary Password", "Confirm Password"},
                new Node[]{username, email, firstName, lastName, password, confirmPass});

        card.getChildren().addAll(title, subtitle, form, mfaConfirmation, submitBtn);

        ScrollPane sp = new ScrollPane(card);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("content-scroll");
        sp.setPadding(new Insets(20));
        return sp;
    }

    private static String value(String text) { return text == null ? "" : text; }

    private static void guard(Runnable action) {
        try {
            action.run();
        } catch (AppException e) {
            Alerts.error(e.getMessage());
            org.slf4j.LoggerFactory.getLogger(AdminViews.class).error("Admin action failed", e);
        } catch (Exception e) {
            Alerts.error("Could not complete the operation. The record may be referenced elsewhere, or the database is unavailable.");
            org.slf4j.LoggerFactory.getLogger(AdminViews.class).error("Admin action failed", e);
        }
    }
}