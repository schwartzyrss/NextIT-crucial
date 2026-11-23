package com.nextit.controller;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.security.Session;
import com.nextit.service.*;
import com.nextit.util.Alerts;
import com.nextit.util.Ui;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import com.nextit.util.WindowBoundsManager;
import java.util.function.Consumer;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class EmployerViews {

    private static final CareerService career = new CareerService();
    private static final PortfolioService portfolios = new PortfolioService();
    private static final StudentService students = new StudentService();
    private static final StudentSkillService skills = new StudentSkillService();
    private static final ProjectService projects = new ProjectService();
    private static final DirectoryService directory = new DirectoryService();
    private static final ProfileService profiles = new ProfileService();
    private static final RecommendationService recommendationService = new RecommendationService();
    private static final RecommendationLetterService recLetters = new RecommendationLetterService();

    private EmployerViews() {}

    private static int employerId() { return career.employerIdForUser(Session.currentUser().getUserId()); }

    public static Node profile() {
        User account = profiles.account(Session.currentUser().getUserId());
        Employer employer = profiles.employerForUser(account.getUserId())
                .orElseThrow(() -> new AppException("Employer profile not found."));

        HBox hero = new HBox(20);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("profile-hero-card");

        StackPane avatar = Ui.avatar(Ui.initials(employer.getCompanyName()), "avatar-yellow");
        Label avatarText = (!avatar.getChildren().isEmpty() && avatar.getChildren().get(0) instanceof Label l) ? l : null;

        VBox identityDetails = new VBox(6);
        Label nameLabel = new Label(value(employer.getCompanyName()));
        nameLabel.getStyleClass().add("profile-hero-name");

        Label usernameBadge = Ui.badge("@" + account.getUsername(), "badge-pill-cream");
        Label roleBadge = Ui.badge(String.valueOf(account.getRole()), "badge-pill-yellow");
        Label industryBadge = Ui.badge(value(employer.getIndustry()), "badge-pill-blue");

        HBox badges = new HBox(8);
        badges.setAlignment(Pos.CENTER_LEFT);
        badges.getChildren().addAll(usernameBadge, roleBadge, industryBadge);
        identityDetails.getChildren().addAll(nameLabel, badges);

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        VBox heroRight = new VBox(6);
        heroRight.setAlignment(Pos.CENTER_RIGHT);
        Label contactPersonHighlight = new Label(value(employer.getContactPerson()));
        contactPersonHighlight.getStyleClass().add("profile-hero-highlight-title");
        Label jobTitleHighlight = new Label(value(employer.getJobTitle()));
        jobTitleHighlight.getStyleClass().add("profile-hero-highlight-subtitle");
        heroRight.getChildren().addAll(contactPersonHighlight, jobTitleHighlight);

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
        accountGrid.add(Ui.propertyTile("Representative", value(account.getFullName())), 0, 1);
        accountGrid.add(Ui.propertyTile("Account Role", Ui.badge(String.valueOf(account.getRole()), "badge-pill-yellow")), 1, 1);

        VBox accountTray = Ui.tray("Account Credentials", "Portal login credentials and account representative details", accountGrid);
        HBox.setHgrow(accountTray, Priority.ALWAYS);

        // Company Details tray (Editable)
        TextField company = Ui.field("Company name");
        company.setText(employer.getCompanyName());
        company.textProperty().addListener((obs, old, val) -> {
            nameLabel.setText(value(val));
            if (avatarText != null) avatarText.setText(Ui.initials(val));
        });

        TextField industry = Ui.field("Company size / industry sector");
        industry.setText(employer.getIndustry());
        industry.textProperty().addListener((obs, old, val) -> {
            industryBadge.setText(value(val));
        });

        TextField contactPerson = Ui.field("Contact person");
        contactPerson.setText(employer.getContactPerson());
        contactPerson.textProperty().addListener((obs, old, val) -> {
            contactPersonHighlight.setText(value(val));
        });

        TextField jobTitle = Ui.field("Job title / corporate position");
        jobTitle.setText(employer.getJobTitle());
        jobTitle.textProperty().addListener((obs, old, val) -> {
            jobTitleHighlight.setText(value(val));
        });

        TextField contactNumber = Ui.field("+63 9XX XXX XXXX");
        contactNumber.setText(employer.getContactNumber());
        Label contactNumberError = new Label();
        contactNumberError.getStyleClass().add("field-error-message");
        contactNumberError.setVisible(false);
        contactNumberError.setManaged(false);
        VBox contactNumberBox = new VBox(3, contactNumber, contactNumberError);

        TextField address = Ui.field("Address");
        address.setText(employer.getAddress());
        Label addressError = new Label();
        addressError.getStyleClass().add("field-error-message");
        addressError.setVisible(false);
        addressError.setManaged(false);
        VBox addressBox = new VBox(3, address, addressError);

        Button save = new Button("Save Company Profile");
        save.getStyleClass().add("primary-button");

        Runnable updateSaveButtonState = () -> {
            boolean hasError = contactNumberError.isVisible() || addressError.isVisible();
            save.setDisable(hasError);
        };

        java.util.function.Consumer<String> checkContactNumber = val -> {
            if (val != null && !val.isBlank()) {
                if (!com.nextit.util.Validator.isValidPhoneNumber(val)) {
                    contactNumberError.setText("⚠ Please enter a valid phone number (e.g., +63 9XX XXX XXXX).");
                    contactNumberError.setVisible(true);
                    contactNumberError.setManaged(true);
                    if (!contactNumber.getStyleClass().contains("input-error-field")) {
                        contactNumber.getStyleClass().add("input-error-field");
                    }
                } else if (profiles.isContactNumberTaken(employer.getEmployerId(), val)) {
                    contactNumberError.setText("⚠ This contact number is already registered to another company.");
                    contactNumberError.setVisible(true);
                    contactNumberError.setManaged(true);
                    if (!contactNumber.getStyleClass().contains("input-error-field")) {
                        contactNumber.getStyleClass().add("input-error-field");
                    }
                } else {
                    contactNumberError.setVisible(false);
                    contactNumberError.setManaged(false);
                    contactNumber.getStyleClass().remove("input-error-field");
                }
            } else {
                contactNumberError.setVisible(false);
                contactNumberError.setManaged(false);
                contactNumber.getStyleClass().remove("input-error-field");
            }
            updateSaveButtonState.run();
        };

        // 300ms Debounce checks while typing
        PauseTransition debounceContact = new PauseTransition(Duration.millis(300));
        debounceContact.setOnFinished(ev -> checkContactNumber.accept(contactNumber.getText()));

        PauseTransition debounceAddress = new PauseTransition(Duration.millis(300));
        debounceAddress.setOnFinished(ev -> {
            String val = address.getText();
            if (val != null && !val.isBlank() && profiles.isAddressTaken(employer.getEmployerId(), val)) {
                addressError.setText("⚠ This office address is already in use. Please specify a unique branch or suite number.");
                addressError.setVisible(true);
                addressError.setManaged(true);
                if (!address.getStyleClass().contains("input-error-field")) {
                    address.getStyleClass().add("input-error-field");
                }
            }
            updateSaveButtonState.run();
        });

        // Clear error state immediately when user modifies input, and schedule debounce
        contactNumber.textProperty().addListener((obs, old, cur) -> {
            debounceContact.stop();
            if (contactNumberError.isVisible()) {
                contactNumberError.setVisible(false);
                contactNumberError.setManaged(false);
                contactNumber.getStyleClass().remove("input-error-field");
                updateSaveButtonState.run();
            }
            debounceContact.playFromStart();
        });

        address.textProperty().addListener((obs, old, cur) -> {
            debounceAddress.stop();
            if (addressError.isVisible()) {
                addressError.setVisible(false);
                addressError.setManaged(false);
                address.getStyleClass().remove("input-error-field");
                updateSaveButtonState.run();
            }
            debounceAddress.playFromStart();
        });

        // Immediate On-blur duplicate check
        contactNumber.focusedProperty().addListener((obs, old, isFocused) -> {
            if (!isFocused) {
                debounceContact.stop();
                checkContactNumber.accept(contactNumber.getText());
            }
        });

        address.focusedProperty().addListener((obs, old, isFocused) -> {
            if (!isFocused && address.getText() != null && !address.getText().isBlank()) {
                debounceAddress.stop();
                if (profiles.isAddressTaken(employer.getEmployerId(), address.getText())) {
                    addressError.setText("⚠ This office address is already in use. Please specify a unique branch or suite number.");
                    addressError.setVisible(true);
                    addressError.setManaged(true);
                    if (!address.getStyleClass().contains("input-error-field")) {
                        address.getStyleClass().add("input-error-field");
                    }
                }
                updateSaveButtonState.run();
            }
        });

        GridPane editGrid = new GridPane();
        editGrid.setHgap(16);
        editGrid.setVgap(14);
        editGrid.getColumnConstraints().addAll(col1, col2);

        editGrid.add(Ui.propertyTile("Company Name", company), 0, 0);
        editGrid.add(Ui.propertyTile("Company Size / Industry Sector", industry), 1, 0);
        editGrid.add(Ui.propertyTile("Contact Person", contactPerson), 0, 1);
        editGrid.add(Ui.propertyTile("Job Title / Corporate Title", jobTitle), 1, 1);
        editGrid.add(Ui.propertyTile("Contact Number", contactNumberBox), 0, 2);
        editGrid.add(Ui.propertyTile("Office Address", addressBox), 1, 2);

        save.setOnAction(e -> {
            String phoneVal = contactNumber.getText();
            if (phoneVal != null && !phoneVal.isBlank()) {
                if (!com.nextit.util.Validator.isValidPhoneNumber(phoneVal)) {
                    contactNumberError.setText("⚠ Please enter a valid phone number (e.g., +63 9XX XXX XXXX).");
                    contactNumberError.setVisible(true);
                    contactNumberError.setManaged(true);
                    if (!contactNumber.getStyleClass().contains("input-error-field")) {
                        contactNumber.getStyleClass().add("input-error-field");
                    }
                    updateSaveButtonState.run();
                    return;
                }
                if (profiles.isContactNumberTaken(employer.getEmployerId(), phoneVal)) {
                    contactNumberError.setText("⚠ This contact number is already registered to another company.");
                    contactNumberError.setVisible(true);
                    contactNumberError.setManaged(true);
                    if (!contactNumber.getStyleClass().contains("input-error-field")) {
                        contactNumber.getStyleClass().add("input-error-field");
                    }
                    updateSaveButtonState.run();
                    return;
                }
            }
            if (profiles.isAddressTaken(employer.getEmployerId(), address.getText())) {
                addressError.setText("⚠ This office address is already in use. Please specify a unique branch or suite number.");
                addressError.setVisible(true);
                addressError.setManaged(true);
                if (!address.getStyleClass().contains("input-error-field")) {
                    address.getStyleClass().add("input-error-field");
                }
                updateSaveButtonState.run();
                return;
            }

            guard(() -> {
                save.setDisable(true);
                save.setText("Saving Changes...");
                try {
                    employer.setCompanyName(company.getText());
                    employer.setIndustry(industry.getText());
                    employer.setContactPerson(contactPerson.getText());
                    employer.setJobTitle(jobTitle.getText());
                    employer.setContactNumber(contactNumber.getText());
                    employer.setAddress(address.getText());
                    profiles.updateEmployer(employer);

                    // Update Hero card displays immediately
                    nameLabel.setText(value(employer.getCompanyName()));
                    if (avatarText != null) avatarText.setText(Ui.initials(employer.getCompanyName()));
                    industryBadge.setText(value(employer.getIndustry()));
                    contactPersonHighlight.setText(value(employer.getContactPerson()));
                    jobTitleHighlight.setText(value(employer.getJobTitle()));

                    Alerts.info("Company profile updated successfully.");
                } finally {
                    save.setText("Save Company Profile");
                    updateSaveButtonState.run();
                }
            });
        });

        VBox editTray = Ui.tray("Company Information", "Update company directory information visible to campus candidates", editGrid, save);
        HBox.setHgrow(editTray, Priority.ALWAYS);

        HBox traysRow = new HBox(20, accountTray, editTray);

        return Ui.page("Company Profile", "Keep your company information current so students know who is reviewing their applications.",
                hero, traysRow);
    }

    public static Node dashboard() {
        int eid = employerId();
        Employer emp = profiles.employerForUser(Session.currentUser().getUserId()).orElse(null);
        String compName = emp != null && emp.getCompanyName() != null ? emp.getCompanyName() : "Company";

        // 1. Hero cushion with status and live badges
        HBox hero = new HBox(16);
        hero.getStyleClass().add("page-header-cushion");
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox heroTitles = new VBox(4);
        heroTitles.getChildren().addAll(
                Ui.title("Employer Dashboard"),
                Ui.subtitle("Welcome, " + Session.currentUser().getFullName() + ". Manage your opportunities and review student applications.")
        );

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        HBox statusPillBox = new HBox(8);
        statusPillBox.setAlignment(Pos.CENTER_RIGHT);
        StackPane statusDot = Ui.statusDot(true);
        Label statusText = new Label("Recruitment Active");
        statusText.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F; -fx-font-size: 12px;");
        HBox liveChip = new HBox(8, statusDot, statusText);
        liveChip.setAlignment(Pos.CENTER);
        liveChip.getStyleClass().addAll("badge-pill", "badge-pill-mint");

        Label compChip = Ui.badge(compName, "badge-pill-yellow");
        statusPillBox.getChildren().addAll(liveChip, compChip);

        hero.getChildren().addAll(heroTitles, heroSpacer, statusPillBox);

        // Fixed sticky header container
        VBox headerContainer = new VBox(hero);
        headerContainer.setStyle("-fx-background-color: #FDFBFA;");
        headerContainer.setPadding(new Insets(24, 24, 10, 24));

        // 2. Full-Width 4-Column Stat Cards Row
        HBox cardsRow = new HBox(14);
        cardsRow.setFillHeight(true);

        // Lower Candidate Applications Table Card
        VBox appTray = new VBox(14);
        appTray.getStyleClass().add("clay-tray");
        appTray.setFillWidth(true);

        // 3. Middle Analytics & Candidate Applications Layout
        HBox middleRow = new HBox(16);
        middleRow.setAlignment(Pos.TOP_LEFT);
        middleRow.setFillHeight(false);

        VBox leftColumn = new VBox(16);
        leftColumn.setAlignment(Pos.TOP_LEFT);
        leftColumn.setFillWidth(true);
        HBox.setHgrow(leftColumn, Priority.ALWAYS);

        HBox chartsRow = new HBox(14);
        chartsRow.setAlignment(Pos.TOP_LEFT);
        chartsRow.setFillHeight(false);
        HBox.setHgrow(chartsRow, Priority.ALWAYS);

        VBox rightColumnHolder = new VBox();
        rightColumnHolder.setPrefWidth(360);
        rightColumnHolder.setMinWidth(320);
        rightColumnHolder.setMaxWidth(380);

        leftColumn.getChildren().addAll(chartsRow, appTray);
        middleRow.getChildren().addAll(leftColumn, rightColumnHolder);

        Map<Integer, String> studentNames = new java.util.HashMap<>();
        Map<Integer, String> opportunityTitles = new java.util.HashMap<>();

        TableView<Application> table = Ui.table("No applications matching filter.");
        table.setPrefHeight(290);
        table.setMinHeight(220);

        Ui.addColumn(table, "Student Applicant", a -> studentNames.getOrDefault(a.getStudentId(), "Student #" + a.getStudentId()));
        Ui.addColumn(table, "Position / Role", a -> opportunityTitles.getOrDefault(a.getOpportunityId(), "Opportunity #" + a.getOpportunityId()));
        Ui.addColumn(table, "Applied Date", a -> a.getApplicationDate() == null ? "" : a.getApplicationDate().toLocalDate().toString());
        table.getColumns().add(Ui.actionColumn("Status", a -> {
            String st = a.getStatus() == null ? "Pending" : a.getStatus();
            String pill = switch (st.toLowerCase(Locale.ROOT)) {
                case "accepted" -> "badge-pill-mint";
                case "shortlisted" -> "badge-pill-blue";
                case "reviewed" -> "badge-pill-yellow";
                case "rejected" -> "badge-pill-cream";
                default -> "badge-pill-pink";
            };
            return Ui.badge(st, pill);
        }));

        TextField search = Ui.field("Filter by student or position...");
        search.setPrefWidth(240);

        ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All Statuses", "Pending", "Under Review", "Shortlisted", "Accepted", "Rejected"));
        statusFilter.setValue("All Statuses");

        HBox trayHeader = new HBox(12);
        trayHeader.setAlignment(Pos.CENTER_LEFT);

        VBox trayTitles = new VBox(3);
        Label trayTitle = new Label("Candidate Applications");
        trayTitle.getStyleClass().add("tray-title");
        Label traySub = new Label("Student applicants who applied to your open listings");
        traySub.getStyleClass().add("tray-subtitle");
        trayTitles.getChildren().addAll(trayTitle, traySub);

        Region traySpacer = new Region();
        HBox.setHgrow(traySpacer, Priority.ALWAYS);

        trayHeader.getChildren().addAll(trayTitles, traySpacer, search, statusFilter);
        appTray.getChildren().addAll(trayHeader, table);

        // Self-refreshing dashboard state
        final Runnable[] refreshHolder = new Runnable[1];
        Runnable refreshDashboard = () -> {
            List<JobOpportunity> owned = career.opportunitiesForEmployer(eid);
            List<Application> apps = career.applicationsForEmployer(eid);
            long open = owned.stream().filter(o -> "Open".equalsIgnoreCase(o.getStatus())).count();
            int sharedPortfoliosCount = portfolios.portfoliosForEmployer(eid).size();

            // Populate Map lookups
            studentNames.clear();
            studentNames.putAll(directory.studentNames());
            opportunityTitles.clear();
            owned.forEach(o -> opportunityTitles.put(o.getOpportunityId(), o.getTitle()));

            // Update 5 Full-Width Stat Cards with Pastel Clay Accents
            cardsRow.getChildren().clear();
            long shortlistedCount = apps.stream().filter(a -> "Shortlisted".equalsIgnoreCase(a.getStatus())).count();
            int recommendedCount = directory.students().size();

            VBox c1 = Ui.card("Active Opportunities", open + " Open (" + owned.size() + " Total)", "card-yellow",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.opportunities(), "Opportunities"); });
            VBox c2 = Ui.card("Applications", String.valueOf(apps.size()), "card-cream",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.applications(), "Applications"); });
            VBox c3 = Ui.card("Shortlisted Candidates", String.valueOf(shortlistedCount), "card-blue",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.applications(), "Applications"); });
            VBox c4 = Ui.card("Recommended Students", String.valueOf(recommendedCount), "card-pink",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.recommendedStudents(), "Recommended Students"); });
            VBox c5 = Ui.card("Shared Portfolios", String.valueOf(sharedPortfoliosCount), "card-mint",
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.portfolioSearch(), "Portfolio Search"); });

            for (VBox c : List.of(c1, c2, c3, c4, c5)) {
                HBox.setHgrow(c, Priority.ALWAYS);
                c.setMaxWidth(Double.MAX_VALUE);
            }
            cardsRow.getChildren().addAll(c1, c2, c3, c4, c5);

            // Update Dual Charts Row
            chartsRow.getChildren().clear();
            Map<String, Long> appStatusCounts = DashboardCharts.countByStatus(
                    apps.stream().map(Application::getStatus).toList(),
                    List.of("Pending", "Under Review", "Shortlisted", "Accepted", "Rejected"));
            Node appStatusChart = DashboardCharts.donutSummary("Application Status", "Candidate progression breakdown", appStatusCounts,
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.applications(), "Applications"); });

            Map<String, Long> roleTypeCounts = DashboardCharts.countByStatus(
                    owned.stream().map(JobOpportunity::getOpportunityType).toList(),
                    List.of("Internship", "Employment"));
            Node roleTypeChart = DashboardCharts.donutSummary("Opportunity Types", "Active roles by category", roleTypeCounts,
                    () -> { if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.opportunities(), "Opportunities"); });

            HBox.setHgrow(appStatusChart, Priority.ALWAYS);
            HBox.setHgrow(roleTypeChart, Priority.ALWAYS);
            chartsRow.getChildren().addAll(appStatusChart, roleTypeChart);

            // Update Right Side Panel
            rightColumnHolder.getChildren().clear();
            VBox rightPanel = createEmployerRightSidePanel(eid, open, apps, owned, emp, studentNames, () -> refreshHolder[0].run());
            rightColumnHolder.getChildren().add(rightPanel);

            // Update Candidate Table
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String selectedStatus = statusFilter.getValue();
            List<Application> filtered = apps.stream().filter(a -> {
                String sName = studentNames.getOrDefault(a.getStudentId(), "").toLowerCase(Locale.ROOT);
                String oTitle = opportunityTitles.getOrDefault(a.getOpportunityId(), "").toLowerCase(Locale.ROOT);
                boolean matchesQ = q.isBlank() || sName.contains(q) || oTitle.contains(q);
                boolean matchesStatus = selectedStatus == null || "All Statuses".equalsIgnoreCase(selectedStatus)
                        || String.valueOf(a.getStatus()).equalsIgnoreCase(selectedStatus);
                return matchesQ && matchesStatus;
            }).toList();
            table.setItems(FXCollections.observableArrayList(filtered));
        };
        refreshHolder[0] = refreshDashboard;

        // Action Column: Review modal
        table.getColumns().add(Ui.actionColumn("Action", a -> {
            Button reviewBtn = new Button("Review");
            reviewBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 14; -fx-background-radius: 999; -fx-font-weight: bold;");
            reviewBtn.getStyleClass().add("badge-pill-lavender");
            reviewBtn.setOnAction(ev -> reviewApplicationModal(eid, a, studentNames, refreshDashboard));
            return reviewBtn;
        }));

        search.textProperty().addListener((obs, old, val) -> refreshDashboard.run());
        statusFilter.valueProperty().addListener((obs, old, val) -> refreshDashboard.run());

        refreshDashboard.run();

        VBox scrollContent = new VBox(20);
        scrollContent.setFillWidth(true);
        scrollContent.setStyle("-fx-background-color: #FDFBFA;");
        scrollContent.setPadding(new Insets(10, 24, 32, 24));
        scrollContent.getChildren().addAll(cardsRow, middleRow);

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

    private static VBox createEmployerRightSidePanel(int eid, long openPositions, List<Application> apps,
                                                    List<JobOpportunity> owned, Employer emp,
                                                    Map<Integer, String> studentNames, Runnable refresh) {
        VBox rightSide = new VBox(16);
        rightSide.setPrefWidth(360);
        rightSide.setMinWidth(320);

        // 1. Quick Actions Launcher
        VBox quickActions = new VBox(12);
        quickActions.getStyleClass().add("clay-tray");

        Label actTitle = new Label("Quick Actions");
        actTitle.getStyleClass().add("tray-title");
        Label actSub = new Label("Recruitment shortcuts");
        actSub.getStyleClass().add("tray-subtitle");

        Button addOppBtn = new Button("Post New Opportunity");
        addOppBtn.setMaxWidth(Double.MAX_VALUE);
        addOppBtn.getStyleClass().add("primary-button");
        addOppBtn.setOnAction(e -> editOpportunity(eid, null, refresh));

        Button recBtn = new Button("✨ Recommended Students");
        recBtn.setMaxWidth(Double.MAX_VALUE);
        recBtn.setStyle("-fx-border-color: #9E0B0F; -fx-text-fill: #9E0B0F; -fx-font-weight: bold; -fx-background-color: #F8EBE8;");
        recBtn.setOnAction(e -> {
            AppController app = AppController.getInstance();
            if (app != null) {
                app.navigateTo(recommendedStudents(), "Recommended Students");
            }
        });

        Button refreshBtn = new Button("Refresh Pipeline Data");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        StackPane refIcon = Ui.refreshIcon();
        refreshBtn.setGraphic(refIcon);
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> {
            refresh.run();
            Alerts.info("Recruitment pipeline refreshed.");
        });

        quickActions.getChildren().addAll(new VBox(3, actTitle, actSub), addOppBtn, recBtn, refreshBtn);

        // 2. Hiring Pipeline & Company Overview
        VBox pipelineCard = new VBox(14);
        pipelineCard.getStyleClass().add("clay-tray");

        Label pipeTitle = new Label("Recruitment Pipeline");
        pipeTitle.getStyleClass().add("tray-title");
        Label pipeSub = new Label("Active hiring health & volume");
        pipeSub.getStyleClass().add("tray-subtitle");

        VBox queueBox = new VBox(8);
        Label queueHeader = new Label("PIPELINE HEALTH");
        queueHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #9E0B0F; -fx-letter-spacing: 0.8px;");

        long pendingReviewCount = apps.stream().filter(a -> "Pending".equalsIgnoreCase(a.getStatus())).count();

        HBox pendingRow = new HBox(8);
        pendingRow.setAlignment(Pos.CENTER_LEFT);
        Label pLabel = new Label("Applications Pending:");
        pLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
        Region pSp = new Region();
        HBox.setHgrow(pSp, Priority.ALWAYS);
        Node pBadge = pendingReviewCount > 0
                ? Ui.badge(pendingReviewCount + " Need Review", "badge-pill-pink")
                : Ui.badge("All Reviewed (0)", "badge-pill-mint");
        pendingRow.getChildren().addAll(pLabel, pSp, pBadge);
        Ui.makeInteractive(pendingRow, () -> {
            if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.applications(), "Applications");
        });

        HBox openRow = new HBox(8);
        openRow.setAlignment(Pos.CENTER_LEFT);
        Label oLabel = new Label("Open Positions:");
        oLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
        Region oSp = new Region();
        HBox.setHgrow(oSp, Priority.ALWAYS);
        Node oBadge = Ui.badge(openPositions + " Active", "badge-pill-blue");
        openRow.getChildren().addAll(oLabel, oSp, oBadge);
        Ui.makeInteractive(openRow, () -> {
            if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.opportunities(), "Opportunities");
        });

        HBox totalRow = new HBox(8);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        Label tLabel = new Label("Total Applicant Pool:");
        tLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
        Region tSp = new Region();
        HBox.setHgrow(tSp, Priority.ALWAYS);
        Node tBadge = Ui.badge(apps.size() + " Candidates", "badge-pill-lavender");
        totalRow.getChildren().addAll(tLabel, tSp, tBadge);
        Ui.makeInteractive(totalRow, () -> {
            if (AppController.getInstance() != null) AppController.getInstance().navigateTo(EmployerViews.applications(), "Applications");
        });

        queueBox.getChildren().addAll(queueHeader, pendingRow, openRow, totalRow);

        Separator sep = new Separator();

        // Company info
        VBox compBox = new VBox(6);
        Label compHeader = new Label("COMPANY PROFILE");
        compHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #9E0B0F; -fx-letter-spacing: 0.8px;");
        Label cName = new Label(emp != null && emp.getCompanyName() != null ? emp.getCompanyName() : "NextIT Partner");
        cName.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
        Label cInd = new Label(emp != null && emp.getIndustry() != null ? emp.getIndustry() : "Technology");
        cInd.setStyle("-fx-font-size: 11px; -fx-text-fill: #6E6862;");
        compBox.getChildren().addAll(compHeader, cName, cInd);

        pipelineCard.getChildren().addAll(new VBox(3, pipeTitle, pipeSub), queueBox, sep, compBox);

        // 3. Latest Candidates Stream
        VBox candidateCard = new VBox(12);
        candidateCard.getStyleClass().add("clay-tray");
        Label cTitle = new Label("Latest Applicants");
        cTitle.getStyleClass().add("tray-title");
        Label cSub = new Label("Most recent submissions");
        cSub.getStyleClass().add("tray-subtitle");
        candidateCard.getChildren().add(new VBox(3, cTitle, cSub));

        List<Application> recentApps = apps.stream()
                .sorted((a, b) -> Integer.compare(b.getApplicationId(), a.getApplicationId()))
                .limit(3)
                .toList();

        if (recentApps.isEmpty()) {
            Label empty = new Label("No candidate submissions yet.");
            empty.setStyle("-fx-font-size: 11px; -fx-text-fill: #6E6862;");
            candidateCard.getChildren().add(empty);
        } else {
            for (Application a : recentApps) {
                HBox item = new HBox(10);
                item.setAlignment(Pos.CENTER_LEFT);
                item.getStyleClass().add("activity-item");

                String stName = studentNames.getOrDefault(a.getStudentId(), "Student #" + a.getStudentId());
                StackPane avatar = Ui.avatar(Ui.initials(stName), "avatar-yellow");
                avatar.setPrefSize(32, 32);
                avatar.setMinSize(32, 32);

                VBox info = new VBox(2);
                Label name = new Label(stName);
                name.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
                String oppTitle = owned.stream()
                        .filter(o -> o.getOpportunityId() == a.getOpportunityId())
                        .map(JobOpportunity::getTitle)
                        .findFirst()
                        .orElse("Opportunity #" + a.getOpportunityId());
                Label role = new Label(oppTitle);
                role.setStyle("-fx-font-size: 10px; -fx-text-fill: #6E6862;");
                info.getChildren().addAll(name, role);
                HBox.setHgrow(info, Priority.ALWAYS);

                String status = a.getStatus() == null ? "Pending" : a.getStatus();
                String pill = switch (status.toLowerCase(Locale.ROOT)) {
                    case "accepted" -> "badge-pill-mint";
                    case "shortlisted" -> "badge-pill-blue";
                    case "reviewed" -> "badge-pill-yellow";
                    case "rejected" -> "badge-pill-cream";
                    default -> "badge-pill-pink";
                };
                Label statusBadge = Ui.badge(status, pill);
                item.getChildren().addAll(avatar, info, statusBadge);
                candidateCard.getChildren().add(item);
            }
        }

        VBox topRecCard = createDashboardRecommendationsCard(eid);
        rightSide.getChildren().addAll(quickActions, topRecCard, pipelineCard, candidateCard);
        return rightSide;
    }

    private static void reviewApplicationModal(int eid, Application selected, Map<Integer, String> studentNames, Runnable refresh) {
        if (selected == null) return;
        showUpdateStatusAndRemarksModal(eid, selected, studentNames, Collections.emptyMap(), refresh);
    }

    public static Node opportunities() {
        int eid = employerId();
        List<JobOpportunity> owned = career.opportunitiesForEmployer(eid);
        TableView<JobOpportunity> table = Ui.table();
        Ui.addColumn(table, "Title", JobOpportunity::getTitle);
        Ui.addColumn(table, "Type", JobOpportunity::getOpportunityType);
        Ui.addColumn(table, "Location", o -> value(o.getLocation()));
        Ui.addColumn(table, "Required Skills", o -> value(o.getRequiredSkills()));
        Ui.addColumn(table, "Deadline", o -> Ui.formatDate(o.getDeadline()));
        Ui.addColumn(table, "Posted", o -> Ui.formatDate(o.getCreatedAt() == null ? null : o.getCreatedAt().toLocalDate()));
        Ui.addColumn(table, "Status", JobOpportunity::getStatus);
        table.setItems(FXCollections.observableArrayList(owned));

        TextField search = Ui.field("Search your opportunities...");
        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            table.setItems(FXCollections.observableArrayList(career.opportunitiesForEmployer(eid).stream().filter(o ->
                    String.valueOf(o.getTitle()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(o.getLocation()).toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(o.getRequiredSkills()).toLowerCase(Locale.ROOT).contains(q)).toList()));
        };
        search.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Button add = new Button("Add Opportunity");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> editOpportunity(eid, null, applyFilter));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> {
            JobOpportunity selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an opportunity to edit."); return; }
            editOpportunity(eid, selected, applyFilter);
        });
        Button close = new Button("Close");
        close.setOnAction(e -> {
            JobOpportunity selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an opportunity to close."); return; }
            if (Ui.confirmed("Close \"" + selected.getTitle() + "\"? Students will no longer be able to apply.")) {
                guard(() -> {
                    JobOpportunity stored = career.opportunityById(selected.getOpportunityId());
                    stored.setStatus("Closed");
                    career.updateOpportunity(stored, eid);
                    applyFilter.run();
                });
            }
        });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            JobOpportunity selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an opportunity to delete."); return; }
            if (Ui.confirmed("Delete \"" + selected.getTitle() + "\" and all of its applications?")) {
                guard(() -> { career.deleteOpportunity(selected.getOpportunityId(), eid); applyFilter.run(); });
            }
        });
        Button topCandidates = new Button("🎯 Top Candidates");
        topCandidates.getStyleClass().add("secondary-button");
        topCandidates.setStyle("-fx-border-color: #9E0B0F; -fx-text-fill: #9E0B0F; -fx-font-weight: bold;");
        topCandidates.setOnAction(e -> {
            JobOpportunity selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Select an opportunity to view top recommended candidates.");
                return;
            }
            showTopCandidatesModal(selected);
        });

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> applyFilter.run());
        return Ui.page("Opportunities", "Create and maintain internship and employment opportunities for NextIT students.",
                Ui.toolbar(new Label("Search:"), search, add, edit, topCandidates, close, delete, refresh), table);
    }

    private static void editOpportunity(int employerId, JobOpportunity existing, Runnable refresh) {
        JobOpportunity o = existing == null ? new JobOpportunity() : existing;
        TextField title = Ui.field("Opportunity title");
        title.setText(o.getTitle());
        ComboBox<String> type = Ui.combo(List.of("Internship", "Employment"), s -> s);
        type.getSelectionModel().select(o.getOpportunityType() == null ? "Internship" : o.getOpportunityType());
        TextArea description = Ui.area("Description");
        description.setText(o.getDescription());
        TextField requiredSkills = Ui.field("Java, SQL, Git");
        requiredSkills.setText(o.getRequiredSkills());
        TextField location = Ui.field("Location");
        location.setText(o.getLocation());
        DatePicker deadline = Ui.date(o.getDeadline() == null ? LocalDate.now().plusMonths(1) : o.getDeadline());
        ComboBox<String> status = Ui.combo(List.of("Open", "Closed", "Archived"), s -> s);
        status.getSelectionModel().select(o.getStatus() == null ? "Open" : o.getStatus());
        Node form = Ui.form(new String[]{"Title", "Type", "Description", "Required skills", "Location", "Deadline", "Status"},
                new Node[]{title, type, description, requiredSkills, location, deadline, status});
        Ui.dialog(existing == null ? "Add Opportunity" : "Edit Opportunity", form).ifPresent(button -> {
            if (button != ButtonType.OK) return;
            guard(() -> {
                JobOpportunity target = new JobOpportunity();
                if (existing != null) {
                    target.setOpportunityId(existing.getOpportunityId());
                    target.setCreatedAt(existing.getCreatedAt());
                }
                target.setEmployerId(employerId);
                target.setTitle(title.getText());
                target.setOpportunityType(type.getValue());
                target.setDescription(description.getText());
                target.setRequiredSkills(requiredSkills.getText());
                target.setLocation(location.getText());
                target.setDeadline(deadline.getValue());
                target.setStatus(status.getValue());
                if (existing == null) career.addOpportunity(target, employerId);
                else career.updateOpportunity(target, employerId);
                refresh.run();
                Alerts.info("Opportunity saved.");
            });
        });
    }

    public static Node applications() {
        int eid = employerId();
        Map<Integer, String> opportunityTitles = new java.util.HashMap<>();
        Map<Integer, String> studentsById = new java.util.HashMap<>();
        Map<Integer, RecommendationLetter> attachedLettersByApp = new java.util.HashMap<>();

        TableView<Application> table = Ui.table("No applications found matching your criteria.");
        Ui.addColumn(table, "Student", a -> studentsById.getOrDefault(a.getStudentId(), "Student #" + a.getStudentId()));
        Ui.addColumn(table, "Opportunity", a -> opportunityTitles.getOrDefault(a.getOpportunityId(), "Opportunity #" + a.getOpportunityId()));
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

        Ui.addColumn(table, "Faculty Recommendation", a -> attachedLettersByApp.containsKey(a.getApplicationId()) ? "✔ Faculty Endorsed" : "-");

        TableColumn<Application, String> remarksCol = new TableColumn<>("Remarks");
        remarksCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRemarks()));
        remarksCol.setCellFactory(col -> new TableCell<Application, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("—");
                    setTooltip(null);
                } else {
                    String trimmed = item.trim().replaceAll("\\s+", " ");
                    String preview = trimmed.length() > 55 ? trimmed.substring(0, 52) + "..." : trimmed;
                    setText(preview);
                    Tooltip tip = new Tooltip(item.trim());
                    tip.setWrapText(true);
                    tip.setMaxWidth(380);
                    tip.setStyle("-fx-font-size: 12px; -fx-background-color: #2B2625; -fx-text-fill: #FDFBFA; -fx-padding: 8;");
                    setTooltip(tip);
                }
                setAlignment(Pos.CENTER_LEFT);
            }
        });
        table.getColumns().add(remarksCol);

        ComboBox<String> statusFilter = Ui.combo(List.of("All statuses", "Pending", "Under Review", "Shortlisted", "Rejected", "Accepted"), s -> s);
        statusFilter.getSelectionModel().selectFirst();
        TextField search = Ui.field("Search by student, opportunity or remarks...");

        Consumer<Integer> loadApplicationsWithHighlight = (highlightAppId) -> {
            opportunityTitles.clear();
            career.opportunitiesForEmployer(eid)
                    .forEach(o -> opportunityTitles.put(o.getOpportunityId(), o.getTitle()));
            studentsById.clear();
            studentsById.putAll(directory.studentNames());
            attachedLettersByApp.clear();
            recLetters.findAll().stream()
                    .filter(RecommendationLetter::isAttached)
                    .filter(l -> l.getApplicationId() != null)
                    .forEach(l -> attachedLettersByApp.put(l.getApplicationId(), l));

            String value = statusFilter.getValue();
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            List<Application> list = career.applicationsForEmployer(eid).stream()
                    .filter(a -> "All statuses".equals(value) || value.equalsIgnoreCase(a.getStatus()))
                    .filter(a -> q.isBlank()
                            || studentsById.getOrDefault(a.getStudentId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || opportunityTitles.getOrDefault(a.getOpportunityId(), "").toLowerCase(Locale.ROOT).contains(q)
                            || String.valueOf(a.getRemarks()).toLowerCase(Locale.ROOT).contains(q))
                    .toList();
            table.setItems(FXCollections.observableArrayList(list));
            table.refresh();

            if (highlightAppId != null && highlightAppId > 0) {
                boolean inList = list.stream().anyMatch(a -> a.getApplicationId() == highlightAppId);
                if (!inList && !"All statuses".equals(value)) {
                    statusFilter.getSelectionModel().select("All statuses");
                    return;
                }
                for (Application a : table.getItems()) {
                    if (a.getApplicationId() == highlightAppId) {
                        table.getSelectionModel().select(a);
                        table.scrollTo(a);
                        table.requestFocus();
                        break;
                    }
                }
            }
        };

        Runnable filter = () -> loadApplicationsWithHighlight.accept(null);
        statusFilter.valueProperty().addListener((obs, old, value) -> filter.run());
        search.textProperty().addListener((obs, old, value) -> filter.run());
        filter.run();

        // Row double click opens status & remarks update
        table.setRowFactory(tv -> {
            TableRow<Application> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showUpdateStatusAndRemarksModal(eid, row.getItem(), studentsById, opportunityTitles, () -> loadApplicationsWithHighlight.accept(row.getItem().getApplicationId()));
                }
            });
            return row;
        });

        Button review = new Button("Update Status");
        review.getStyleClass().add("primary-button");
        review.setStyle("-fx-text-fill: #FDFBFA;");
        review.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        review.setOnAction(e -> {
            Application selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an application to review."); return; }
            showUpdateStatusAndRemarksModal(eid, selected, studentsById, opportunityTitles, () -> loadApplicationsWithHighlight.accept(selected.getApplicationId()));
        });

        Button viewDetails = new Button("View Details");
        viewDetails.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        viewDetails.setOnAction(e -> {
            Application selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select an application to view."); return; }
            showApplicationDetailsModal(selected, studentsById, opportunityTitles, attachedLettersByApp.get(selected.getApplicationId()));
        });

        Button viewLetter = new Button("View Recommendation Letter");
        viewLetter.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        viewLetter.setOnAction(e -> {
            Application selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Select an application to view.");
                return;
            }
            RecommendationLetter l = attachedLettersByApp.get(selected.getApplicationId());
            if (l == null) {
                Alerts.info("No faculty recommendation letter was attached to this application.");
                return;
            }
            InstructorViews.showViewLetterModal(l);
        });

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> filter.run());
        return Ui.page("Applications", "Review applications submitted to your opportunities, update candidate statuses, and record actionable feedback.",
                Ui.toolbar(new Label("Search:"), search, new Label("Filter:"), statusFilter, review, viewDetails, viewLetter, refresh), table);
    }

    public static void showUpdateStatusAndRemarksModal(int eid, Application selected, Map<Integer, String> studentsById, Map<Integer, String> opportunityTitles, Runnable onComplete) {
        if (selected == null) {
            Alerts.error("Select an application to review.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        if (AppController.getInstance() != null && AppController.getInstance().getStage() != null) {
            dialog.initOwner(AppController.getInstance().getStage());
        }
        dialog.setTitle("Update Application Status & Remarks");
        dialog.setHeaderText(null);
        if (EmployerViews.class.getResource("/com/nextit/css/application.css") != null) {
            dialog.getDialogPane().getStylesheets().add(EmployerViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
        }
        dialog.getDialogPane().setStyle("-fx-background-color: #FDFBFA;");

        VBox root = new VBox(14);
        root.setPrefWidth(540);
        root.setPadding(new Insets(16, 20, 16, 20));

        // 1. Applicant Header Card
        VBox headerCard = new VBox(6);
        headerCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EFE2CA; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14; -fx-effect: dropshadow(gaussian, rgba(198, 167, 94, 0.15), 6, 0.05, 0, 2);");

        String applicantName = studentsById != null ? studentsById.getOrDefault(selected.getStudentId(), "Student #" + selected.getStudentId()) : "Student #" + selected.getStudentId();
        String oppTitle = opportunityTitles != null ? opportunityTitles.getOrDefault(selected.getOpportunityId(), "Opportunity #" + selected.getOpportunityId()) : "Opportunity #" + selected.getOpportunityId();

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(applicantName);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #9E0B0F;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String currStatus = selected.getStatus() == null ? "Pending" : selected.getStatus();
        Label currentStatusBadge = Ui.badge("Current: " + currStatus, badgeClassFor(currStatus));
        topRow.getChildren().addAll(nameLabel, spacer, currentStatusBadge);

        Label oppLabel = new Label("Applying for: " + oppTitle);
        oppLabel.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 600; -fx-text-fill: #414141;");

        String appliedDateStr = selected.getApplicationDate() == null ? "—" : Ui.formatDate(selected.getApplicationDate().toLocalDate());
        Label metaLabel = new Label("Submitted: " + appliedDateStr + (selected.getReviewedAt() != null ? "  •  Last reviewed: " + Ui.formatDate(selected.getReviewedAt().toLocalDate()) : ""));
        metaLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8C827A;");

        headerCard.getChildren().addAll(topRow, oppLabel, metaLabel);

        // 2. Status Selector
        VBox statusBox = new VBox(6);
        Label statusLabel = new Label("APPLICATION STATUS");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #6E6862; -fx-letter-spacing: 0.5px;");

        ComboBox<String> statusCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Pending", "Under Review", "Shortlisted", "Accepted", "Rejected"
        ));
        statusCombo.setMaxWidth(Double.MAX_VALUE);
        statusCombo.getStyleClass().add("combo-box");
        String currentSt = selected.getStatus();
        if ("Reviewed".equalsIgnoreCase(currentSt)) currentSt = "Under Review";
        if (currentSt != null && statusCombo.getItems().contains(currentSt)) {
            statusCombo.getSelectionModel().select(currentSt);
        } else {
            statusCombo.getSelectionModel().select("Pending");
        }
        statusBox.getChildren().addAll(statusLabel, statusCombo);

        // 3. Dynamic Feedback Guidance Banner
        VBox guidanceBanner = new VBox(4);
        guidanceBanner.setPadding(new Insets(10, 12, 10, 12));
        Label guidanceTitle = new Label();
        guidanceTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px;");
        Label guidanceDesc = new Label();
        guidanceDesc.setWrapText(true);
        guidanceDesc.setStyle("-fx-font-size: 11.5px;");
        guidanceBanner.getChildren().addAll(guidanceTitle, guidanceDesc);

        // 4. Remarks / Feedback Text Area
        VBox remarksBox = new VBox(6);
        HBox remarksHeader = new HBox(8);
        remarksHeader.setAlignment(Pos.CENTER_LEFT);
        Label remarksHeaderLabel = new Label("EMPLOYER REMARKS / FEEDBACK");
        remarksHeaderLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #6E6862;");
        Label optionalOrEncouraged = new Label();
        remarksHeader.getChildren().addAll(remarksHeaderLabel, optionalOrEncouraged);

        TextArea remarksArea = new TextArea();
        remarksArea.setPrefRowCount(4);
        remarksArea.setWrapText(true);
        remarksArea.setPromptText("Enter constructive feedback, candidate notes, or next steps...");
        remarksArea.getStyleClass().add("text-area");
        remarksArea.setText(selected.getRemarks() == null ? "" : selected.getRemarks());

        Label remarksHint = new Label("Remarks are shared directly with the student in their Application tracker.");
        remarksHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #8C827A; -fx-font-style: italic;");
        remarksBox.getChildren().addAll(remarksHeader, remarksArea, remarksHint);

        // Reactive update for guidance banner & remarks input prompt
        Runnable updateGuidance = () -> {
            String st = statusCombo.getValue();
            if ("Rejected".equalsIgnoreCase(st)) {
                guidanceBanner.setStyle("-fx-background-color: #FFF5F5; -fx-border-color: #F0B8B8; -fx-border-radius: 8; -fx-background-radius: 8;");
                guidanceTitle.setText("💡 Constructive Feedback Highly Encouraged");
                guidanceTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #9E0B0F;");
                guidanceDesc.setText("Providing constructive reasons (such as missing technical skills, position already filled, or portfolio suggestions) helps candidates understand the decision and improve.");
                guidanceDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #732023;");
                optionalOrEncouraged.setText("(Highly Encouraged)");
                optionalOrEncouraged.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 800; -fx-text-fill: #9E0B0F;");
                remarksArea.setStyle("-fx-border-color: #C6A75E; -fx-border-radius: 8;");
            } else if ("Accepted".equalsIgnoreCase(st)) {
                guidanceBanner.setStyle("-fx-background-color: #F4FAF5; -fx-border-color: #BDE4C7; -fx-border-radius: 8; -fx-background-radius: 8;");
                guidanceTitle.setText("🎉 Candidate Accepted");
                guidanceTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #1E6B35;");
                guidanceDesc.setText("Congratulations! You can optionally include onboarding notes, contact information, or welcoming instructions.");
                guidanceDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #225930;");
                optionalOrEncouraged.setText("(Optional Welcome Notes)");
                optionalOrEncouraged.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 600; -fx-text-fill: #1E6B35;");
                remarksArea.setStyle("");
            } else if ("Shortlisted".equalsIgnoreCase(st)) {
                guidanceBanner.setStyle("-fx-background-color: #FEF9E7; -fx-border-color: #EBD39E; -fx-border-radius: 8; -fx-background-radius: 8;");
                guidanceTitle.setText("⭐ Candidate Shortlisted");
                guidanceTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #996800;");
                guidanceDesc.setText("Candidate meets your requirements. Provide details on interview scheduling, presentation dates, or next round details.");
                guidanceDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #735300;");
                optionalOrEncouraged.setText("(Optional Interview Notes)");
                optionalOrEncouraged.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 600; -fx-text-fill: #996800;");
                remarksArea.setStyle("");
            } else if ("Under Review".equalsIgnoreCase(st)) {
                guidanceBanner.setStyle("-fx-background-color: #F6F8FA; -fx-border-color: #D3DFE8; -fx-border-radius: 8; -fx-background-radius: 8;");
                guidanceTitle.setText("🔍 Application Under Review");
                guidanceTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #2E5677;");
                guidanceDesc.setText("Signal to the candidate that their portfolio and credentials are being reviewed by the hiring team.");
                guidanceDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #3E6585;");
                optionalOrEncouraged.setText("(Optional Review Notes)");
                optionalOrEncouraged.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 600; -fx-text-fill: #2E5677;");
                remarksArea.setStyle("");
            } else {
                guidanceBanner.setStyle("-fx-background-color: #FAF8F5; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-background-radius: 8;");
                guidanceTitle.setText("⏳ Application Pending");
                guidanceTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12px; -fx-text-fill: #6E6862;");
                guidanceDesc.setText("Application is awaiting initial review.");
                guidanceDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6E6862;");
                optionalOrEncouraged.setText("(Optional)");
                optionalOrEncouraged.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 600; -fx-text-fill: #8C827A;");
                remarksArea.setStyle("");
            }
        };
        statusCombo.valueProperty().addListener((obs, old, val) -> updateGuidance.run());
        updateGuidance.run();

        // 5. Quick-Select Feedback Chips
        VBox chipsBox = new VBox(6);
        Label chipsLabel = new Label("QUICK-SELECT FEEDBACK CHIPS");
        chipsLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #6E6862;");

        FlowPane chipPane = new FlowPane(8, 8);
        chipPane.setPrefWrapLength(500);

        record FeedbackChip(String chipLabel, String feedbackText, String targetStatus) {}
        List<FeedbackChip> presetChips = List.of(
                new FeedbackChip("Position filled", "Thank you for applying. We have currently filled this position with another candidate.", "Rejected"),
                new FeedbackChip("Lacks required technical stack", "Thank you for your interest. We are looking for candidates with more hands-on experience in the required technical stack for this specific opening.", "Rejected"),
                new FeedbackChip("Proceeding with other candidates", "We reviewed your background carefully, but have decided to proceed with other candidates whose experience more closely matches our immediate requirements.", "Rejected"),
                new FeedbackChip("Scheduled for interview", "Congratulations! Your profile has been shortlisted. Our recruiting team will reach out shortly to schedule an interview.", "Shortlisted"),
                new FeedbackChip("Strong portfolio — shortlisted", "Impressive project portfolio and qualifications! You have been shortlisted for our technical evaluation round.", "Shortlisted"),
                new FeedbackChip("Welcome to the team!", "Congratulations! We are pleased to accept your application. Our HR team will reach out with the formal offer and onboarding details.", "Accepted")
        );

        for (FeedbackChip c : presetChips) {
            Button chipBtn = new Button(c.chipLabel());
            chipBtn.getStyleClass().add("feedback-chip");
            chipBtn.setOnAction(ev -> {
                remarksArea.setText(c.feedbackText());
                if (c.targetStatus() != null) {
                    statusCombo.getSelectionModel().select(c.targetStatus());
                }
            });
            chipPane.getChildren().add(chipBtn);
        }
        chipsBox.getChildren().addAll(chipsLabel, chipPane);

        // Status & Progress Message
        Label statusMsg = new Label();
        statusMsg.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #9E0B0F;");

        root.getChildren().addAll(headerCard, statusBox, guidanceBanner, chipsBox, remarksBox, statusMsg);

        dialog.getDialogPane().setContent(root);
        ButtonType saveBtnType = new ButtonType("Update Status", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, cancelBtnType);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveBtnType);
        if (saveBtn != null) {
            saveBtn.getStyleClass().add("primary-button");
            saveBtn.setStyle("-fx-text-fill: #FDFBFA; -fx-font-weight: 800;");
        }
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(cancelBtnType);
        if (cancelBtn != null) {
            cancelBtn.getStyleClass().add("secondary-button");
        }

        // Asynchronous Supabase save on click without freezing JavaFX UI thread
        if (saveBtn != null) {
            saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                event.consume();

                String newStatus = statusCombo.getValue();
                String newRemarks = remarksArea.getText();

                saveBtn.setDisable(true);
                if (cancelBtn != null) cancelBtn.setDisable(true);
                statusCombo.setDisable(true);
                remarksArea.setDisable(true);
                statusMsg.setText("⏳ Updating application in Supabase...");

                career.updateApplicationStatusAsync(eid, selected, newStatus, newRemarks)
                        .thenRun(() -> Platform.runLater(() -> {
                            dialog.setResult(saveBtnType);
                            dialog.close();
                            if (onComplete != null) {
                                onComplete.run();
                            }
                            Platform.runLater(() -> {
                                Window topWindow = AppController.getInstance() != null ? AppController.getInstance().getStage() : null;
                                if (topWindow == null || !topWindow.isShowing()) {
                                    topWindow = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null);
                                }
                                showApplicationStatusUpdatedModal(topWindow, applicantName, oppTitle, newStatus, newRemarks);
                            });
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> {
                                saveBtn.setDisable(false);
                                if (cancelBtn != null) cancelBtn.setDisable(false);
                                statusCombo.setDisable(false);
                                remarksArea.setDisable(false);
                                statusMsg.setText("");
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                if (cause instanceof AppException appEx) {
                                    Alerts.error(appEx.getMessage());
                                } else {
                                    Alerts.error("Could not complete the operation: " + cause.getMessage());
                                }
                            });
                            return null;
                        });
            });
        }

        dialog.showAndWait();
    }

    private static void showApplicationStatusUpdatedModal(Window ownerWindow, String studentName, String oppTitle, String updatedStatus, String remarks) {
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
        Label heading = new Label("Application Status Updated");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");

        Label subHeading = new Label("Candidate status and evaluation feedback have been recorded.");
        subHeading.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #5A6A7E; -fx-font-weight: 500;");

        // 3. Structured Summary Card
        VBox detailsCard = new VBox(12);
        detailsCard.setStyle(
                "-fx-background-color: #FFFFFF; " +
                "-fx-border-color: #E2D9CC; " +
                "-fx-border-radius: 12; " +
                "-fx-background-radius: 12; " +
                "-fx-padding: 16 18;"
        );

        // Student and Status Header
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label studentLbl = new Label(studentName != null ? studentName : "Candidate");
        studentLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");
        studentLbl.setWrapText(true);
        HBox.setHgrow(studentLbl, Priority.ALWAYS);

        String st = updatedStatus != null ? updatedStatus : "Updated";
        Label statusBadge = Ui.badge(st, badgeClassFor(st));
        topRow.getChildren().addAll(studentLbl, statusBadge);

        Separator sep = new Separator();
        sep.setStyle("-fx-opacity: 0.5;");

        // Metadata grid
        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(14);
        metaGrid.setVgap(8);

        Label oppLabel = new Label("Opportunity:");
        oppLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label oppVal = new Label(oppTitle != null ? oppTitle : "Opportunity");
        oppVal.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1A2634; -fx-font-weight: 700;");
        oppVal.setWrapText(true);
        metaGrid.add(oppLabel, 0, 0);
        metaGrid.add(oppVal, 1, 0);

        Label stLabel = new Label("Updated Status:");
        stLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #7A889B; -fx-font-weight: 600;");
        Label stVal = new Label(st);
        stVal.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #991B1B; -fx-font-weight: 800;");
        metaGrid.add(stLabel, 0, 1);
        metaGrid.add(stVal, 1, 1);

        detailsCard.getChildren().addAll(topRow, sep, metaGrid);

        // Remarks / Feedback section if present
        VBox remarksBox = new VBox(4);
        Label remarksHeader = new Label("Recorded Remarks / Feedback:");
        remarksHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #6E6862;");

        Label remarksContent = new Label(remarks != null && !remarks.isBlank() ? remarks.trim() : "No additional feedback remarks recorded.");
        remarksContent.setWrapText(true);
        remarksContent.setMaxWidth(Double.MAX_VALUE);
        remarksContent.setStyle(remarks != null && !remarks.isBlank()
                ? "-fx-font-size: 12px; -fx-text-fill: #1A2634; -fx-background-color: #FAF8F5; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10; -fx-line-spacing: 2px;"
                : "-fx-font-size: 12px; -fx-text-fill: #8C827A; -fx-font-style: italic; -fx-background-color: #FAF8F5; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8;");

        remarksBox.getChildren().addAll(remarksHeader, remarksContent);
        detailsCard.getChildren().add(remarksBox);

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
        if (EmployerViews.class.getResource("/com/nextit/css/application.css") != null) {
            modalScene.getStylesheets().add(EmployerViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
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

    public static void showApplicationDetailsModal(Application app, Map<Integer, String> studentsById, Map<Integer, String> opportunityTitles, RecommendationLetter recLetter) {
        if (app == null) {
            Alerts.error("Select an application to view.");
            return;
        }
        String studentName = studentsById != null ? studentsById.getOrDefault(app.getStudentId(), "Student #" + app.getStudentId()) : "Student #" + app.getStudentId();
        String oppTitle = opportunityTitles != null ? opportunityTitles.getOrDefault(app.getOpportunityId(), "Opportunity #" + app.getOpportunityId()) : "Opportunity #" + app.getOpportunityId();

        Dialog<ButtonType> dialog = new Dialog<>();
        if (AppController.getInstance() != null && AppController.getInstance().getStage() != null) {
            dialog.initOwner(AppController.getInstance().getStage());
        }
        dialog.setTitle("Application Details");
        dialog.setHeaderText(null);
        if (EmployerViews.class.getResource("/com/nextit/css/application.css") != null) {
            dialog.getDialogPane().getStylesheets().add(EmployerViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
        }
        dialog.getDialogPane().setStyle("-fx-background-color: #FDFBFA;");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(14);
        box.setPrefWidth(520);
        box.setPadding(new Insets(16, 20, 16, 20));

        // Header
        VBox top = new VBox(4);
        Label title = new Label(studentName);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #9E0B0F;");
        Label sub = new Label("Candidate application for " + oppTitle);
        sub.setStyle("-fx-font-size: 13px; -fx-text-fill: #414141; -fx-font-weight: 600;");
        top.getChildren().addAll(title, sub);

        // Details grid
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-background-radius: 8;");

        grid.add(new Label("Applied Date:"), 0, 0);
        Label dateLbl = new Label(app.getApplicationDate() != null ? Ui.formatDate(app.getApplicationDate().toLocalDate()) : "—");
        dateLbl.setStyle("-fx-font-weight: 700;");
        grid.add(dateLbl, 1, 0);

        grid.add(new Label("Current Status:"), 0, 1);
        String st = app.getStatus() == null ? "Pending" : app.getStatus();
        Label stBadge = Ui.badge(st, badgeClassFor(st));
        grid.add(stBadge, 1, 1);

        grid.add(new Label("Faculty Endorsement:"), 0, 2);
        Label endLbl = new Label(recLetter != null ? "✔ Verified Faculty Endorsed" : "— None attached");
        endLbl.setStyle(recLetter != null ? "-fx-text-fill: #1E6B35; -fx-font-weight: 700;" : "-fx-text-fill: #8C827A;");
        grid.add(endLbl, 1, 2);

        grid.add(new Label("Last Reviewed:"), 0, 3);
        Label revLbl = new Label(app.getReviewedAt() != null ? Ui.formatDate(app.getReviewedAt().toLocalDate()) : "Not reviewed yet");
        revLbl.setStyle("-fx-font-weight: 600; -fx-text-fill: #6E6862;");
        grid.add(revLbl, 1, 3);

        // Remarks Section
        VBox remBox = new VBox(8);
        remBox.setStyle("-fx-background-color: #FAF8F5; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");
        Label remTitle = new Label("Employer Remarks & Feedback");
        remTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 12.5px; -fx-text-fill: #9E0B0F;");

        Label remContent = new Label();
        remContent.setWrapText(true);
        if (app.getRemarks() != null && !app.getRemarks().isBlank()) {
            remContent.setText(app.getRemarks());
            remContent.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #2B2625; -fx-line-spacing: 2px;");
        } else {
            remContent.setText("— No remarks added yet —");
            remContent.setStyle("-fx-font-size: 12px; -fx-text-fill: #8C827A; -fx-font-style: italic;");
        }
        remBox.getChildren().addAll(remTitle, remContent);

        box.getChildren().addAll(top, grid, remBox);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    private static String badgeClassFor(String status) {
        if (status == null) return "badge-pill-cream";
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "open", "accepted" -> "badge-pill-mint";
            case "shortlisted", "under review", "reviewed" -> "badge-pill-navy";
            case "rejected", "closed" -> "badge-pill-pink";
            case "pending", "draft" -> "badge-pill-yellow";
            default -> "badge-pill-cream";
        };
    }

    public static Node portfolioSearch() {
        int eid = employerId();
        Map<Integer, String> studentNames = directory.studentNames();
        List<Portfolio> authorized = portfolios.portfoliosForEmployer(eid);
        List<TechnicalSkill> allSkills = directory.technicalSkills();

        TableView<Portfolio> table = Ui.table();
        Ui.addColumn(table, "Student", p -> studentNames.getOrDefault(p.getStudentId(), "Student #" + p.getStudentId()));
        Ui.addColumn(table, "Headline", Portfolio::getHeadline);
        Ui.addColumn(table, "Career Goal", Portfolio::getCareerGoal);
        Ui.addColumn(table, "Status", Portfolio::getVisibility);
        table.setItems(FXCollections.observableArrayList(authorized));

        TextField search = Ui.field("Search by student, skill, project, certification or achievement...");
        List<String> skillOptions = new java.util.ArrayList<>();
        skillOptions.add("All technical skills");
        allSkills.stream().map(TechnicalSkill::getSkillName).distinct().sorted().forEach(skillOptions::add);
        ComboBox<String> skillFilter = Ui.combo(skillOptions, s -> s);
        skillFilter.getSelectionModel().selectFirst();

        List<String> categoryOptions = new java.util.ArrayList<>();
        categoryOptions.add("All skill categories");
        allSkills.stream().map(TechnicalSkill::getCategory).filter(c -> c != null && !c.isBlank())
                .distinct().sorted().forEach(categoryOptions::add);
        ComboBox<String> categoryFilter = Ui.combo(categoryOptions, s -> s);
        categoryFilter.getSelectionModel().selectFirst();

        Runnable filter = () -> {
            studentNames.clear();
            studentNames.putAll(directory.studentNames());
            allSkills.clear();
            allSkills.addAll(directory.technicalSkills());
            refreshChoices(skillFilter, "All technical skills",
                    allSkills.stream().map(TechnicalSkill::getSkillName).distinct().sorted().toList());
            refreshChoices(categoryFilter, "All skill categories",
                    allSkills.stream().map(TechnicalSkill::getCategory)
                            .filter(c -> c != null && !c.isBlank()).distinct().sorted().toList());
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            String skill = skillFilter.getValue();
            String category = categoryFilter.getValue();
            // Re-read access on every filter so newly granted portfolios appear
            // without leaving and reopening the screen.
            table.setItems(FXCollections.observableArrayList(portfolios.portfoliosForEmployer(eid).stream()
                    .filter(p -> q.isBlank() || searchTextFor(p.getStudentId(), studentNames).contains(q))
                    .filter(p -> "All technical skills".equals(skill) || hasSkill(p.getStudentId(), skill, allSkills))
                    .filter(p -> "All skill categories".equals(category) || hasSkillCategory(p.getStudentId(), category, allSkills))
                    .toList()));
        };
        search.textProperty().addListener((obs, old, value) -> filter.run());
        skillFilter.valueProperty().addListener((obs, old, value) -> filter.run());
        categoryFilter.valueProperty().addListener((obs, old, value) -> filter.run());
        filter.run();

        Button view = new Button("View Portfolio");
        view.getStyleClass().add("primary-button");
        view.setOnAction(e -> {
            Portfolio selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Alerts.error("Select a portfolio to view."); return; }
            if (!portfolios.isVisibleToEmployer(selected.getPortfolioId(), eid)) {
                Alerts.error("You are not authorised to view this portfolio.");
                return;
            }
            guard(() -> PortfolioDetailsView.show(selected, false));
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> filter.run());

        return Ui.page("Student Portfolios", "Public portfolios and portfolios explicitly shared with your company are shown here. Filter by technical skill or category.",
                Ui.toolbar(new Label("Search:"), search, skillFilter, categoryFilter, view, refresh), table);
    }

    private static void refreshChoices(ComboBox<String> combo, String allLabel, List<String> values) {
        List<String> options = new java.util.ArrayList<>();
        options.add(allLabel);
        options.addAll(values);
        if (options.equals(combo.getItems())) return;
        String selected = combo.getValue();
        combo.setItems(FXCollections.observableArrayList(options));
        combo.setValue(options.contains(selected) ? selected : allLabel);
    }

    private static boolean hasSkill(int studentId, String skillName, List<TechnicalSkill> allSkills) {
        Map<Integer, String> names = allSkills.stream().collect(Collectors.toMap(
                TechnicalSkill::getSkillId, TechnicalSkill::getSkillName, (a, b) -> a));
        return skills.forStudent(studentId).stream()
                .anyMatch(s -> skillName.equalsIgnoreCase(names.get(s.getSkillId())));
    }

    private static boolean hasSkillCategory(int studentId, String category, List<TechnicalSkill> allSkills) {
        Map<Integer, String> categories = allSkills.stream().collect(Collectors.toMap(
                TechnicalSkill::getSkillId, TechnicalSkill::getCategory, (a, b) -> a));
        return skills.forStudent(studentId).stream()
                .anyMatch(s -> category.equalsIgnoreCase(categories.get(s.getSkillId())));
    }

    private static String searchTextFor(int studentId, Map<Integer, String> studentNames) {
        StringBuilder text = new StringBuilder();
        text.append(studentNames.getOrDefault(studentId, "")).append(' ');
        portfolios.portfolioForStudent(studentId).ifPresent(p -> text.append(value(p.getHeadline())).append(' ')
                .append(value(p.getBio())).append(' ').append(value(p.getCareerGoal())).append(' '));
        Map<Integer, String> skillNames = directory.skillNames();
        skills.forStudent(studentId).forEach(s -> text.append(skillNames.getOrDefault(s.getSkillId(), "")).append(' ')
                .append(value(s.getProficiencyLevel())).append(' '));
        projects.projectsForStudent(studentId).forEach(p -> text.append(value(p.getProjectTitle())).append(' ')
                .append(value(p.getTechnologiesUsed())).append(' '));
        students.achievementsFor(studentId).forEach(a -> text.append(value(a.getTitle())).append(' ')
                .append(value(a.getOrganization())).append(' '));
        students.certificationsFor(studentId).forEach(c -> text.append(value(c.getCertificationName())).append(' ')
                .append(value(c.getIssuingOrganization())).append(' '));
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static String value(String text) { return text == null ? "" : text; }

    private static void guard(Runnable action) {
        try {
            action.run();
        } catch (AppException e) {
            Alerts.error(e.getMessage());
        } catch (Exception e) {
            Alerts.error("Could not complete the operation. Please check the database connection.");
            org.slf4j.LoggerFactory.getLogger(EmployerViews.class).error("Employer action failed", e);
        }
    }

    // =========================================================================
    // STUDENT RECOMMENDATION ENGINE & TALENT DISCOVERY
    // =========================================================================

    public static Node recommendedStudents() {
        return recommendedStudents(null);
    }

    public static Node recommendedStudents(Integer preselectedOpportunityId) {
        int eid = employerId();
        List<JobOpportunity> ownedOpps = career.opportunitiesForEmployer(eid);
        List<JobOpportunity> openOpps = ownedOpps.stream().filter(o -> "Open".equalsIgnoreCase(o.getStatus())).toList();

        // 1. Fallback / Notice banner if employer has NO open opportunities yet
        VBox bannerHolder = new VBox();
        if (openOpps.isEmpty()) {
            HBox fallbackBanner = new HBox(16);
            fallbackBanner.setAlignment(Pos.CENTER_LEFT);
            fallbackBanner.setStyle("-fx-background-color: #EFE2CA; -fx-border-color: #C6A75E; " +
                    "-fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 16;");

            Label noticeIcon = Ui.badge("Notice", "badge-pill-yellow");
            noticeIcon.setStyle("-fx-font-weight: bold;");

            VBox bannerTexts = new VBox(4);
            Label bTitle = new Label("No Active Opportunities Posted");
            bTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #9E0B0F;");
            Label bDesc = new Label("You currently have no open job listings. Showing top candidates across core IT competencies. Post an opportunity for tailored candidate scoring.");
            bDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #414141;");
            bannerTexts.getChildren().addAll(bTitle, bDesc);

            Region bSpacer = new Region();
            HBox.setHgrow(bSpacer, Priority.ALWAYS);

            Button postOppBtn = new Button("➕ Post New Opportunity");
            postOppBtn.getStyleClass().add("primary-button");
            postOppBtn.setOnAction(e -> editOpportunity(eid, null, () -> {
                AppController app = AppController.getInstance();
                if (app != null) app.navigateTo(recommendedStudents(), "Recommended Students");
            }));

            fallbackBanner.getChildren().addAll(noticeIcon, bannerTexts, bSpacer, postOppBtn);
            bannerHolder.getChildren().add(fallbackBanner);
        }

        // 2. Toolbar & Controls
        ComboBox<String> oppFilter = new ComboBox<>();
        oppFilter.getItems().add("All Open Opportunities");
        Map<String, Integer> oppMap = new HashMap<>();
        for (JobOpportunity o : openOpps) {
            String label = o.getTitle() + " (#" + o.getOpportunityId() + ")";
            oppFilter.getItems().add(label);
            oppMap.put(label, o.getOpportunityId());
        }

        if (preselectedOpportunityId != null) {
            for (Map.Entry<String, Integer> entry : oppMap.entrySet()) {
                if (entry.getValue().equals(preselectedOpportunityId)) {
                    oppFilter.setValue(entry.getKey());
                    break;
                }
            }
        }
        if (oppFilter.getValue() == null) {
            oppFilter.setValue("All Open Opportunities");
        }

        ComboBox<String> scoreFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All Match Scores",
                "90%+ Match (Exceptional)",
                "80%+ Match (Top Match)",
                "60%+ Match (Good Match)",
                "40%+ Match (Moderate Match)"
        ));
        scoreFilter.setValue("All Match Scores");

        List<String> skillOptions = new ArrayList<>();
        skillOptions.add("All Skills");
        directory.technicalSkills().stream()
                .map(TechnicalSkill::getSkillName)
                .distinct().sorted()
                .forEach(skillOptions::add);
        ComboBox<String> skillFilter = new ComboBox<>(FXCollections.observableArrayList(skillOptions));
        skillFilter.setValue("All Skills");

        ComboBox<String> sortSelector = new ComboBox<>(FXCollections.observableArrayList(
                "Highest Match Score",
                "Most Verified Skills",
                "Most Certifications & Awards",
                "Candidate Name (A-Z)"
        ));
        sortSelector.setValue("Highest Match Score");

        TextField searchField = Ui.field("Search candidates by name, program, skills, or projects...");
        searchField.setPrefWidth(260);

        Button refreshBtn = new Button("Refresh");
        StackPane refIcon = Ui.refreshIcon();
        refreshBtn.setGraphic(refIcon);
        refreshBtn.getStyleClass().add("secondary-button");

        HBox toolbar = Ui.toolbar(
                new Label("Opportunity:"), oppFilter,
                new Label("Min Score:"), scoreFilter,
                new Label("Skill:"), skillFilter,
                new Label("Sort:"), sortSelector,
                searchField,
                refreshBtn
        );

        // 3. Results Container & Data State
        VBox cardsContainer = new VBox(14);
        cardsContainer.setFillWidth(true);

        List<StudentRecommendation> rawRecommendations = new ArrayList<>();

        final Runnable[] filterAndRenderRef = new Runnable[1];
        filterAndRenderRef[0] = () -> {
            cardsContainer.getChildren().clear();

            String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
            String selectedScore = scoreFilter.getValue();
            int minScore = switch (selectedScore == null ? "All" : selectedScore) {
                case "90%+ Match (Exceptional)" -> 90;
                case "80%+ Match (Top Match)" -> 80;
                case "60%+ Match (Good Match)" -> 60;
                case "40%+ Match (Moderate Match)" -> 40;
                default -> 0;
            };

            String selectedSkill = skillFilter.getValue();
            boolean filterSkill = selectedSkill != null && !"All Skills".equalsIgnoreCase(selectedSkill);

            String selectedSort = sortSelector.getValue();

            List<StudentRecommendation> filtered = new ArrayList<>(rawRecommendations.stream()
                    .filter(c -> c.getMatchScore() >= minScore)
                    .filter(c -> !filterSkill || c.getAllStudentSkills().stream().anyMatch(s -> s.equalsIgnoreCase(selectedSkill)))
                    .filter(c -> {
                        if (query.isEmpty()) return true;
                        String sName = c.getStudentName().toLowerCase(Locale.ROOT);
                        String sProg = c.getProgramAndYear().toLowerCase(Locale.ROOT);
                        String sSkills = String.join(" ", c.getAllStudentSkills()).toLowerCase(Locale.ROOT);
                        String sReason = c.getMatchReason().toLowerCase(Locale.ROOT);
                        return sName.contains(query) || sProg.contains(query) || sSkills.contains(query) || sReason.contains(query);
                    })
                    .toList());

            if ("Most Verified Skills".equalsIgnoreCase(selectedSort)) {
                filtered.sort((a, b) -> Integer.compare(b.getMatchedSkills().size(), a.getMatchedSkills().size()));
            } else if ("Most Certifications & Awards".equalsIgnoreCase(selectedSort)) {
                filtered.sort((a, b) -> Integer.compare(b.getRelevantCredentials().size(), a.getRelevantCredentials().size()));
            } else if ("Candidate Name (A-Z)".equalsIgnoreCase(selectedSort)) {
                filtered.sort(Comparator.comparing(StudentRecommendation::getStudentName));
            } else {
                filtered.sort(Comparator.comparingInt(StudentRecommendation::getMatchScore).reversed());
            }

            if (filtered.isEmpty()) {
                VBox emptyCard = new VBox(12);
                emptyCard.setAlignment(Pos.CENTER);
                emptyCard.setStyle("-fx-background-color: #FDFBFA; -fx-border-color: #EFE2CA; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 36;");
                Label emptyTitle = new Label("No matching candidates found.");
                emptyTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #414141;");
                Label emptyDesc = new Label("Try lowering the minimum match score or clearing the skill search filter.");
                emptyDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: #6E6862;");
                Button resetBtn = new Button("Reset All Filters");
                resetBtn.getStyleClass().add("secondary-button");
                resetBtn.setOnAction(e -> {
                    scoreFilter.setValue("All Match Scores");
                    skillFilter.setValue("All Skills");
                    sortSelector.setValue("Highest Match Score");
                    searchField.clear();
                });
                emptyCard.getChildren().addAll(emptyTitle, emptyDesc, resetBtn);
                cardsContainer.getChildren().add(emptyCard);
                return;
            }

            HBox countRow = new HBox(8);
            countRow.setAlignment(Pos.CENTER_LEFT);
            Label countLabel = new Label("Showing " + filtered.size() + " Ranked Candidates");
            countLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #414141;");
            countRow.getChildren().add(countLabel);
            cardsContainer.getChildren().add(countRow);

            for (StudentRecommendation c : filtered) {
                VBox cCard = createCandidateCard(c, ownedOpps, filterAndRenderRef[0]);
                cardsContainer.getChildren().add(cCard);
            }
        };

        Runnable filterAndRender = () -> filterAndRenderRef[0].run();

        Runnable loadData = () -> {
            cardsContainer.getChildren().clear();
            VBox loadingCard = new VBox(14);
            loadingCard.setAlignment(Pos.CENTER);
            loadingCard.setStyle("-fx-background-color: #FDFBFA; -fx-border-color: #EFE2CA; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 40;");
            ProgressIndicator pi = new ProgressIndicator();
            pi.setPrefSize(42, 42);
            Label loadingLbl = new Label("Analyzing student profiles, verified skills, and certifications against hiring criteria...");
            loadingLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #6E6862; -fx-font-weight: 500;");
            loadingCard.getChildren().addAll(pi, loadingLbl);
            cardsContainer.getChildren().add(loadingCard);

            String selOpp = oppFilter.getValue();
            Integer chosenOppId = (selOpp == null || "All Open Opportunities".equals(selOpp))
                    ? null
                    : oppMap.get(selOpp);

            recommendationService.getRecommendationsAsync(chosenOppId, eid)
                    .thenAccept(list -> Platform.runLater(() -> {
                        rawRecommendations.clear();
                        rawRecommendations.addAll(list);
                        filterAndRender.run();
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            cardsContainer.getChildren().clear();
                            Alerts.error("Failed to load candidate recommendations: " + ex.getMessage());
                        });
                        return null;
                    });
        };

        oppFilter.valueProperty().addListener((obs, old, val) -> loadData.run());
        scoreFilter.valueProperty().addListener((obs, old, val) -> filterAndRender.run());
        skillFilter.valueProperty().addListener((obs, old, val) -> filterAndRender.run());
        sortSelector.valueProperty().addListener((obs, old, val) -> filterAndRender.run());
        searchField.textProperty().addListener((obs, old, val) -> filterAndRender.run());
        refreshBtn.setOnAction(e -> loadData.run());

        loadData.run();

        List<Node> pageElements = new ArrayList<>();
        pageElements.add(toolbar);
        if (!bannerHolder.getChildren().isEmpty()) {
            pageElements.add(bannerHolder);
        }
        pageElements.add(cardsContainer);

        return Ui.page("Recommended Students",
                "Discover, rank, and review top-matching student candidates tailored to your opportunities using multi-criteria weighted scoring.",
                pageElements.toArray(new Node[0]));
    }

    private static VBox createCandidateCard(StudentRecommendation cand, List<JobOpportunity> owned, Runnable onRefresh) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EFE2CA; -fx-border-width: 1.5; " +
                "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 4, 0, 0, 2);");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = Ui.avatar(Ui.initials(cand.getStudentName()), cand.getMatchScore() >= 80 ? "avatar-mint" : "avatar-yellow");
        avatar.setPrefSize(42, 42);
        avatar.setMinSize(42, 42);

        VBox nameBox = new VBox(3);
        HBox nameAndBadges = new HBox(8);
        nameAndBadges.setAlignment(Pos.CENTER_LEFT);

        Label nameLbl = new Label(cand.getStudentName());
        nameLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #414141;");

        Node verifiedBadge = cand.isIdentityVerified()
                ? Ui.badge("✓ Verified Identity", "badge-pill-mint")
                : Ui.badge("Unverified", "badge-pill-cream");

        nameAndBadges.getChildren().addAll(nameLbl, verifiedBadge);

        if (cand.isHasApplied()) {
            nameAndBadges.getChildren().add(Ui.badge("Applied: " + cand.getApplicationStatus(), "badge-pill-blue"));
        }

        List<RecommendationLetter> cLetters = recLetters.getLettersForStudent(cand.getStudentId());
        if (cLetters.stream().anyMatch(RecommendationLetter::isIssued)) {
            nameAndBadges.getChildren().add(Ui.badge("📜 Faculty Endorsed", "badge-pill-mint"));
        }

        Label subLbl = new Label(cand.getProgramAndYear() + "  •  #" + (cand.getStudent() != null ? cand.getStudent().getStudentNumber() : ""));
        subLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6E6862;");

        nameBox.getChildren().addAll(nameAndBadges, subLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox scoreBox = new VBox(2);
        scoreBox.setAlignment(Pos.CENTER_RIGHT);

        Label scoreBadge = Ui.badge(cand.getMatchScore() + "% MATCH", cand.getMatchScoreBadgeStyle());
        scoreBadge.setStyle(scoreBadge.getStyle() + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 12;");

        Label breakdownLbl = new Label(String.format("Skills: %.0f/45 • Projects: %.0f/25 • Certs: %.0f/20 • Academic: %.0f/10",
                cand.getSkillsScore(), cand.getProjectsScore(), cand.getCredentialsScore(), cand.getReadinessScore()));
        breakdownLbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #8E8882;");

        scoreBox.getChildren().addAll(scoreBadge, breakdownLbl);
        topRow.getChildren().addAll(avatar, nameBox, spacer, scoreBox);

        Label reasonLbl = new Label("💡 " + cand.getMatchReason());
        reasonLbl.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #9E0B0F; " +
                "-fx-background-color: #F8EBE8; -fx-padding: 5 10; -fx-background-radius: 6; -fx-border-color: #E6C2BA; -fx-border-radius: 6;");

        FlowPane skillsFlow = new FlowPane(6, 6);
        skillsFlow.setAlignment(Pos.CENTER_LEFT);

        Label skTitle = new Label("Skills: ");
        skTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #555555;");
        skillsFlow.getChildren().add(skTitle);

        for (String m : cand.getMatchedSkills()) {
            skillsFlow.getChildren().add(Ui.badge("✓ " + m, "badge-pill-mint"));
        }
        for (String miss : cand.getMissingSkills().stream().limit(2).toList()) {
            skillsFlow.getChildren().add(Ui.badge("Missing: " + miss, "badge-pill-pink"));
        }
        for (String other : cand.getAllStudentSkills().stream()
                .filter(s -> !cand.getMatchedSkills().contains(s))
                .limit(3)
                .toList()) {
            skillsFlow.getChildren().add(Ui.badge(other, "badge-pill-cream"));
        }

        HBox highlightsBox = new HBox(12);
        highlightsBox.setAlignment(Pos.CENTER_LEFT);

        if (!cand.getRelevantProjects().isEmpty()) {
            String projText = cand.getRelevantProjects().stream()
                    .map(Project::getProjectTitle)
                    .limit(2)
                    .collect(Collectors.joining(", "));
            Label prLbl = new Label("📂 Projects: " + projText);
            prLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #414141;");
            highlightsBox.getChildren().add(prLbl);
        }

        if (!cand.getRelevantCredentials().isEmpty()) {
            String credText = cand.getRelevantCredentials().stream()
                    .map(AcademicCredential::getTitle)
                    .limit(2)
                    .collect(Collectors.joining(", "));
            Label crLbl = new Label("🏆 Credentials: " + credText);
            crLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #414141;");
            highlightsBox.getChildren().add(crLbl);
        }

        HBox actionsRow = new HBox(8);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);

        Button viewPortfolioBtn = new Button("View Full Portfolio");
        viewPortfolioBtn.getStyleClass().add("secondary-button");
        viewPortfolioBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12;");
        viewPortfolioBtn.setOnAction(e -> showCandidatePortfolioOrProfile(cand));

        Button shortlistBtn = new Button("Shortlist / Invite to Apply");
        if (cand.isHasApplied() && "Shortlisted".equalsIgnoreCase(cand.getApplicationStatus())) {
            shortlistBtn.setText("✓ Shortlisted");
            shortlistBtn.setDisable(true);
            shortlistBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-background-color: #EFE2CA; -fx-text-fill: #414141;");
        } else {
            shortlistBtn.getStyleClass().add("primary-button");
            shortlistBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12;");
            shortlistBtn.setOnAction(e -> promptShortlistOrInvite(cand, owned, onRefresh));
        }

        actionsRow.getChildren().addAll(viewPortfolioBtn, shortlistBtn);

        card.getChildren().addAll(topRow, reasonLbl, skillsFlow);
        if (!highlightsBox.getChildren().isEmpty()) {
            card.getChildren().add(highlightsBox);
        }
        card.getChildren().add(actionsRow);

        return card;
    }

    public static void showTopCandidatesModal(JobOpportunity opportunity) {
        if (opportunity == null) return;
        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #FDFBFA;");

        HBox oppHeader = new HBox(12);
        oppHeader.setAlignment(Pos.CENTER_LEFT);
        oppHeader.setStyle("-fx-background-color: #EFE2CA; -fx-padding: 12 16; -fx-background-radius: 10; -fx-border-color: #C6A75E; -fx-border-radius: 10;");

        VBox oppInfo = new VBox(4);
        Label oppTitle = new Label(opportunity.getTitle());
        oppTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #9E0B0F;");
        Label oppSkills = new Label("Required Skills: " + (opportunity.getRequiredSkills() == null || opportunity.getRequiredSkills().isBlank() ? "Not specified" : opportunity.getRequiredSkills()));
        oppSkills.setStyle("-fx-font-size: 11px; -fx-text-fill: #414141;");
        oppInfo.getChildren().addAll(oppTitle, oppSkills);

        Region oppSp = new Region();
        HBox.setHgrow(oppSp, Priority.ALWAYS);

        Label typeBadge = Ui.badge(opportunity.getOpportunityType(), "badge-pill-blue");
        oppHeader.getChildren().addAll(oppInfo, oppSp, typeBadge);

        VBox listContainer = new VBox(10);
        listContainer.setAlignment(Pos.CENTER);

        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(40, 40);
        Label loadingLbl = new Label("Calculating candidate matches for " + opportunity.getTitle() + "...");
        loadingLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #6E6862;");
        listContainer.getChildren().addAll(pi, loadingLbl);

        ScrollPane scroll = new ScrollPane(listContainer);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(720);
        scroll.setPrefViewportHeight(480);
        scroll.setStyle("-fx-background-color: transparent;");

        root.getChildren().addAll(oppHeader, scroll);

        recommendationService.getRecommendationsAsync(opportunity.getOpportunityId(), opportunity.getEmployerId())
                .thenAccept(candidates -> Platform.runLater(() -> {
                    listContainer.getChildren().clear();
                    listContainer.setAlignment(Pos.TOP_LEFT);

                    List<StudentRecommendation> topCandidates = candidates.stream().limit(10).toList();
                    if (topCandidates.isEmpty()) {
                        Label empty = new Label("No matching candidates found for this opportunity.");
                        empty.setStyle("-fx-font-size: 13px; -fx-text-fill: #6E6862; -fx-padding: 20;");
                        listContainer.getChildren().add(empty);
                        return;
                    }

                    List<JobOpportunity> owned = career.opportunitiesForEmployer(opportunity.getEmployerId());

                    for (StudentRecommendation cand : topCandidates) {
                        VBox candCard = createCandidateCard(cand, owned, () -> showTopCandidatesModal(opportunity));
                        listContainer.getChildren().add(candCard);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        listContainer.getChildren().clear();
                        Label err = new Label("Failed to load recommendations: " + ex.getMessage());
                        err.setStyle("-fx-text-fill: #9E0B0F; -fx-padding: 20;");
                        listContainer.getChildren().add(err);
                    });
                    return null;
                });

        Ui.viewDialog("Top Recommended Candidates - " + opportunity.getTitle(), root);
    }

    private static VBox createDashboardRecommendationsCard(int eid) {
        VBox recCard = new VBox(12);
        recCard.getStyleClass().add("clay-tray");

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox titles = new VBox(2);
        Label title = new Label("Top Recommended Candidates");
        title.getStyleClass().add("tray-title");
        Label subtitle = new Label("AI-matched talent for your open roles");
        subtitle.getStyleClass().add("tray-subtitle");
        titles.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label badge = Ui.badge("AI Match", "badge-pill-mint");
        head.getChildren().addAll(titles, sp, badge);
        recCard.getChildren().add(head);

        VBox contentList = new VBox(8);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(24, 24);
        contentList.setAlignment(Pos.CENTER);
        contentList.getChildren().addAll(pi, new Label("Matching candidate profiles..."));
        recCard.getChildren().add(contentList);

        Button exploreBtn = new Button("Explore All Recommendations →");
        exploreBtn.setMaxWidth(Double.MAX_VALUE);
        exploreBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9E0B0F; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 11.5px; -fx-padding: 4 0;");
        exploreBtn.setOnAction(e -> {
            AppController app = AppController.getInstance();
            if (app != null) {
                app.navigateTo(recommendedStudents(), "Recommended Students");
            }
        });
        recCard.getChildren().add(exploreBtn);

        recommendationService.getRecommendationsAsync(null, eid)
                .thenAccept(list -> Platform.runLater(() -> {
                    contentList.getChildren().clear();
                    contentList.setAlignment(Pos.TOP_LEFT);

                    List<StudentRecommendation> top = list.stream().limit(3).toList();
                    if (top.isEmpty()) {
                        Label empty = new Label("No recommended candidates found yet.");
                        empty.setStyle("-fx-font-size: 11px; -fx-text-fill: #6E6862;");
                        contentList.getChildren().add(empty);
                    } else {
                        for (StudentRecommendation c : top) {
                            HBox row = new HBox(10);
                            row.setAlignment(Pos.CENTER_LEFT);
                            row.getStyleClass().add("activity-item");
                            row.setStyle("-fx-padding: 8; -fx-background-color: #FFFFFF; -fx-background-radius: 8; -fx-border-color: #EFE2CA; -fx-border-radius: 8; -fx-cursor: hand;");

                            StackPane av = Ui.avatar(Ui.initials(c.getStudentName()), c.getMatchScore() >= 80 ? "avatar-mint" : "avatar-yellow");
                            av.setPrefSize(32, 32);
                            av.setMinSize(32, 32);

                            VBox info = new VBox(2);
                            Label name = new Label(c.getStudentName());
                            name.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #414141;");
                            String skillSummary = c.getMatchedSkills().isEmpty()
                                    ? (c.getAllStudentSkills().isEmpty() ? c.getProgramAndYear() : c.getAllStudentSkills().get(0))
                                    : c.getMatchedSkills().get(0);
                            Label prog = new Label(c.getProgramAndYear() + " • " + skillSummary);
                            prog.setStyle("-fx-font-size: 10px; -fx-text-fill: #6E6862;");
                            info.getChildren().addAll(name, prog);
                            HBox.setHgrow(info, Priority.ALWAYS);

                            Label scPill = Ui.badge(c.getMatchScore() + "%", c.getMatchScoreBadgeStyle());
                            scPill.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8;");

                            row.getChildren().addAll(av, info, scPill);
                            row.setOnMouseClicked(ev -> showCandidatePortfolioOrProfile(c));
                            contentList.getChildren().add(row);
                        }
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        contentList.getChildren().clear();
                        contentList.getChildren().add(new Label("Could not load recommendations."));
                    });
                    return null;
                });

        return recCard;
    }

    private static void showCandidatePortfolioOrProfile(StudentRecommendation candidate) {
        int sid = candidate.getStudent().getStudentId();
        int eid = employerId();
        Optional<Portfolio> portOpt = portfolios.portfolioForStudent(sid);

        if (portOpt.isPresent() && portfolios.isVisibleToEmployer(portOpt.get().getPortfolioId(), eid)) {
            guard(() -> PortfolioDetailsView.show(portOpt.get(), false));
        } else {
            showCandidateProfileDialog(candidate);
        }
    }

    private static void showCandidateProfileDialog(StudentRecommendation candidate) {
        int sid = candidate.getStudent().getStudentId();
        String studentName = candidate.getStudentName();

        VBox content = new VBox(14);
        content.setPadding(new Insets(10));

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(16);
        infoGrid.setVgap(8);
        infoGrid.add(Ui.propertyTile("Student Name", studentName), 0, 0);
        infoGrid.add(Ui.propertyTile("Student Number", candidate.getStudent().getStudentNumber()), 1, 0);
        infoGrid.add(Ui.propertyTile("Degree / Program", candidate.getStudent().getProgram()), 0, 1);
        infoGrid.add(Ui.propertyTile("Year Level", Ui.formatYearDisplay(candidate.getStudent().getYearLevel())), 1, 1);
        infoGrid.add(Ui.propertyTile("Identity Status", candidate.isIdentityVerified() ? Ui.badge("Verified", "badge-pill-mint") : Ui.badge("Unverified", "badge-pill-cream")), 0, 2);
        infoGrid.add(Ui.propertyTile("Enrollment Status", candidate.getStudent().getEnrollmentStatus()), 1, 2);

        VBox skillsBox = new VBox(6);
        Label skTitle = new Label("Recorded Technical Skills");
        skTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F;");
        skillsBox.getChildren().add(skTitle);
        var sSkills = skills.forStudent(sid);
        if (sSkills.isEmpty()) {
            skillsBox.getChildren().add(new Label("No technical skills recorded yet."));
        } else {
            var skillNames = directory.skillNames();
            for (StudentSkill ss : sSkills) {
                String label = skillNames.getOrDefault(ss.getSkillId(), "Skill #" + ss.getSkillId()) +
                        " — " + ss.getProficiencyLevel() + (ss.getAssessmentScore() != null ? " (" + ss.getAssessmentScore() + "%)" : "");
                skillsBox.getChildren().add(new Label("• " + label));
            }
        }

        VBox projBox = new VBox(6);
        Label prTitle = new Label("Projects & Capstone Experience");
        prTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F;");
        projBox.getChildren().add(prTitle);
        var sProjects = projects.projectsForStudent(sid);
        if (sProjects.isEmpty()) {
            projBox.getChildren().add(new Label("No projects recorded yet."));
        } else {
            for (Project p : sProjects) {
                String item = p.getProjectTitle() + " (" + p.getProjectType() + ")" +
                        (p.getTechnologiesUsed() != null ? " — Tech: " + p.getTechnologiesUsed() : "");
                projBox.getChildren().add(new Label("• " + item));
            }
        }

        VBox credBox = new VBox(6);
        Label crTitle = new Label("Achievements & Certifications");
        crTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F;");
        credBox.getChildren().add(crTitle);
        var sCreds = students.credentialsFor(sid);
        if (sCreds.isEmpty()) {
            credBox.getChildren().add(new Label("No credentials recorded yet."));
        } else {
            for (AcademicCredential c : sCreds) {
                credBox.getChildren().add(new Label("• " + c.getType().getDisplay() + ": " + c.getTitle() + " (" + c.getOrganization() + ")"));
            }
        }

        content.getChildren().addAll(infoGrid, new Separator(), skillsBox, new Separator(), projBox, new Separator(), credBox);
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setPrefViewportWidth(650);
        sp.setPrefViewportHeight(500);

        Ui.viewDialog("Candidate Profile - " + studentName, sp);
    }

    private static void promptShortlistOrInvite(StudentRecommendation candidate,
                                                List<JobOpportunity> ownedOpportunities,
                                                Runnable onComplete) {
        if (ownedOpportunities == null || ownedOpportunities.isEmpty()) {
            Alerts.error("You need at least one open opportunity to shortlist or invite candidates. Please post an opportunity first.");
            return;
        }

        ComboBox<JobOpportunity> oppCombo = new ComboBox<>(FXCollections.observableArrayList(ownedOpportunities));
        oppCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(JobOpportunity o) {
                return o == null ? "" : o.getTitle() + " (" + o.getOpportunityType() + ")";
            }
            @Override
            public JobOpportunity fromString(String string) { return null; }
        });

        if (candidate.getOpportunity() != null) {
            ownedOpportunities.stream()
                    .filter(o -> o.getOpportunityId() == candidate.getOpportunity().getOpportunityId())
                    .findFirst()
                    .ifPresent(oppCombo.getSelectionModel()::select);
        }
        if (oppCombo.getSelectionModel().getSelectedItem() == null) {
            oppCombo.getSelectionModel().selectFirst();
        }

        TextArea noteField = Ui.area("Personalized invitation message or shortlist note...");
        noteField.setText("We evaluated your verified skills and portfolio on NextIT and invite you to interview / apply for this opportunity.");

        Label studentLabel = new Label(candidate.getStudentName() + " (" + candidate.getProgramAndYear() + ")");
        studentLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F;");

        Node form = Ui.form(
                new String[]{"Candidate", "Opportunity Position", "Invitation / Shortlist Note"},
                new Node[]{studentLabel, oppCombo, noteField}
        );

        Ui.dialog("Shortlist & Invite Candidate", form).ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            JobOpportunity chosen = oppCombo.getValue();
            if (chosen == null) {
                Alerts.error("Please select an opportunity.");
                return;
            }
            guard(() -> {
                recommendationService.shortlistOrInviteCandidate(
                        chosen.getEmployerId(),
                        candidate.getStudent().getStudentId(),
                        chosen.getOpportunityId(),
                        noteField.getText()
                );
                Alerts.info("Candidate " + candidate.getStudentName() + " has been shortlisted and invited for \"" + chosen.getTitle() + "\"!");
                if (onComplete != null) onComplete.run();
            });
        });
    }
}