package com.nextit.controller;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.security.Session;
import com.nextit.service.*;
import com.nextit.util.Alerts;
import com.nextit.util.Ui;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class AdminViews {

    private static final Logger log = LoggerFactory.getLogger(AdminViews.class);
    private static final AdminService admin = new AdminService();
    private static final ProfileService profiles = new ProfileService();
    private static final AntiFakeIdentityService antiFake = new AntiFakeIdentityService();

    private AdminViews() {}

    public static Node dashboard() {
        return verificationWorkspace("All Pending");
    }

    public static Node verificationWorkspace() {
        return verificationWorkspace("All Pending");
    }

    public static Node verificationWorkspace(String initialFilter) {
        VBox layout = new VBox(20);
        layout.setPadding(new Insets(24));
        layout.setStyle("-fx-background-color: #FAF7F2;");

        // 1. Header Banner Cushion
        HBox hero = new HBox(16);
        hero.getStyleClass().add("page-header-cushion");
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox heroTitles = new VBox(4);
        heroTitles.getChildren().addAll(
                Ui.title("Admin Verification Workspace"),
                Ui.subtitle("Review, inspect, approve, and reject user identity and credential verification requests.")
        );

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        HBox statusPillBox = new HBox(8);
        statusPillBox.setAlignment(Pos.CENTER_RIGHT);
        StackPane statusDot = Ui.statusDot(true);
        Label statusText = new Label("Admin Security Console Active");
        statusText.setStyle("-fx-font-weight: bold; -fx-text-fill: #9E0B0F; -fx-font-size: 12px;");
        HBox liveChip = new HBox(8, statusDot, statusText);
        liveChip.setAlignment(Pos.CENTER);
        liveChip.getStyleClass().addAll("badge-pill", "badge-pill-mint");

        Label adminBadge = Ui.badge("SYSTEM ADMIN", "badge-pill-cream");
        statusPillBox.getChildren().addAll(liveChip, adminBadge);
        hero.getChildren().addAll(heroTitles, heroSpacer, statusPillBox);

        // 2. Summary Metric Cards Row
        HBox metricRow = new HBox(14);
        metricRow.setFillHeight(true);

        Label pendingValLbl = new Label("...");
        pendingValLbl.getStyleClass().add("metric-value");
        Label verifiedInstValLbl = new Label("...");
        verifiedInstValLbl.getStyleClass().add("metric-value");
        Label verifiedEmpValLbl = new Label("...");
        verifiedEmpValLbl.getStyleClass().add("metric-value");
        Label rejectedValLbl = new Label("...");
        rejectedValLbl.getStyleClass().add("metric-value");

        // Filter Pills & Search Bar state
        String[] tabLabels = new String[]{"All Pending", "Instructors", "Employers", "Approved", "Rejected", "All Records"};
        String startTab = (initialFilter != null && !initialFilter.isBlank()) ? initialFilter : "All Pending";
        String[] currentFilter = new String[]{startTab};
        List<Button> tabButtons = new ArrayList<>();
        Runnable[] updateFilteredListHolder = new Runnable[1];

        java.util.function.Consumer<String> selectFilter = tabLabel -> {
            currentFilter[0] = tabLabel;
            for (Button b : tabButtons) {
                b.setStyle("-fx-font-size: 11.5px; -fx-padding: 5 12;");
                if (b.getText().equalsIgnoreCase(tabLabel)) {
                    b.setStyle("-fx-font-size: 11.5px; -fx-padding: 5 12; -fx-background-color: #1A2634; -fx-text-fill: #FFFFFF; -fx-border-color: #1A2634;");
                }
            }
            if (AppController.getInstance() != null) {
                AppController.getInstance().navigateTo(null, "Verification Queue");
            }
            if (updateFilteredListHolder[0] != null) {
                updateFilteredListHolder[0].run();
            }
        };

        VBox c1 = buildMetricCard("Pending Verifications", pendingValLbl, "card-pink", "Awaiting administrator decision",
                () -> selectFilter.accept("All Pending"));
        VBox c2 = buildMetricCard("Verified Instructors", verifiedInstValLbl, "card-mint", "Active verified academic faculty",
                () -> selectFilter.accept("Instructors"));
        VBox c3 = buildMetricCard("Verified Employers", verifiedEmpValLbl, "card-blue", "Accredited industry partners",
                () -> selectFilter.accept("Employers"));
        VBox c5 = buildMetricCard("Rejected / Flagged", rejectedValLbl, "card-cream", "Denied or suspicious submissions",
                () -> selectFilter.accept("Rejected"));

        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);
        HBox.setHgrow(c5, Priority.ALWAYS);
        metricRow.getChildren().addAll(c1, c2, c3, c5);

        // 3. Main Verification Queue Tray
        VBox queueTray = new VBox(14);
        queueTray.getStyleClass().add("clay-tray");
        queueTray.setPadding(new Insets(20));

        Label trayTitle = new Label("Pending & Historical Verification Queue");
        trayTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");
        Label traySub = new Label("Select any record to examine authentic university registrar records, company registration numbers, and anti-fraud indicators.");
        traySub.setStyle("-fx-font-size: 12px; -fx-text-fill: #4A5A6A;");

        // Filter Pills & Search Bar
        HBox controlsRow = new HBox(12);
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search applicant name, email, ID number, company or department...");
        searchField.getStyleClass().add("text-field");
        searchField.setPrefWidth(380);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        HBox tabPills = new HBox(6);
        tabPills.setAlignment(Pos.CENTER_LEFT);

        TableView<VerificationRequest> table = Ui.table();
        table.setPlaceholder(new Label("No verification requests found matching the current filter."));
        table.setPrefHeight(380);
        VBox.setVgrow(table, Priority.ALWAYS);

        Ui.addColumn(table, "Applicant Name", r -> {
            String name = r.getUserFullName();
            if (name == null || name.isBlank()) name = "User #" + r.getUserId();
            return (r.getUsername() != null && !r.getUsername().isBlank()) ? name + " (@" + r.getUsername() + ")" : name;
        });

        table.getColumns().add(Ui.actionColumn("Role", r -> {
            Role role = r.getRole();
            String pill = role == Role.INSTRUCTOR ? "badge-pill-mint" : (role == Role.EMPLOYER ? "badge-pill-yellow" : "badge-pill-cream");
            return Ui.badge(role != null ? role.name() : "UNKNOWN", pill);
        }));

        Ui.addColumn(table, "Institutional / Work Email", r ->
                (r.getInstitutionalEmail() != null && !r.getInstitutionalEmail().isBlank())
                        ? r.getInstitutionalEmail()
                        : (r.getUserEmail() != null ? r.getUserEmail() : "—"));

        Ui.addColumn(table, "Affiliation / Company Name", r ->
                (r.getOrganizationName() != null && !r.getOrganizationName().isBlank())
                        ? r.getOrganizationName()
                        : "—");

        Ui.addColumn(table, "ID / Registration Number", r ->
                (r.getIdNumber() != null && !r.getIdNumber().isBlank())
                        ? r.getIdNumber()
                        : (r.getBusinessRegistrationNumber() != null && !r.getBusinessRegistrationNumber().isBlank()
                                ? r.getBusinessRegistrationNumber()
                                : "—"));

        Ui.addColumn(table, "Submitted Date", r ->
                r.getCreatedAt() != null ? Ui.formatDateTime(r.getCreatedAt()) : "—");

        table.getColumns().add(Ui.actionColumn("Status", r -> {
            VerificationStatus st = r.getStatus();
            String pill = switch (st != null ? st : VerificationStatus.PENDING) {
                case VERIFIED -> "badge-pill-mint";
                case PENDING -> "badge-pill-yellow";
                case REJECTED, SUSPENDED -> "badge-pill-pink";
                case UNVERIFIED -> "badge-pill-cream";
            };
            return Ui.badge(st != null ? st.displayName() : "Pending", pill);
        }));

        table.getColumns().add(Ui.actionColumn("Risk Score", r -> {
            int score = r.getRiskScore();
            String riskText = score >= 50 ? score + " (High Risk)" : (score >= 20 ? score + " (Moderate)" : score + " (Safe)");
            String pill = score >= 50 ? "badge-pill-pink" : (score >= 20 ? "badge-pill-yellow" : "badge-pill-mint");
            return Ui.badge(riskText, pill);
        }));

        List<VerificationRequest> masterCache = new ArrayList<>();

        Runnable updateFilteredList = () -> {
            String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
            String tab = currentFilter[0];

            List<VerificationRequest> filtered = masterCache.stream().filter(r -> {
                // Tab filter
                boolean tabMatches = switch (tab) {
                    case "All Pending" -> r.getStatus() == VerificationStatus.PENDING || r.getStatus() == null;
                    case "Instructors" -> r.getRole() == Role.INSTRUCTOR;
                    case "Employers" -> r.getRole() == Role.EMPLOYER;
                    case "Approved" -> r.getStatus() == VerificationStatus.VERIFIED;
                    case "Rejected" -> r.getStatus() == VerificationStatus.REJECTED || r.getStatus() == VerificationStatus.SUSPENDED;
                    default -> true;
                };
                if (!tabMatches) return false;

                // Text query filter
                if (q.isBlank()) return true;
                return (r.getUserFullName() != null && r.getUserFullName().toLowerCase(Locale.ROOT).contains(q)) ||
                       (r.getUsername() != null && r.getUsername().toLowerCase(Locale.ROOT).contains(q)) ||
                       (r.getInstitutionalEmail() != null && r.getInstitutionalEmail().toLowerCase(Locale.ROOT).contains(q)) ||
                       (r.getUserEmail() != null && r.getUserEmail().toLowerCase(Locale.ROOT).contains(q)) ||
                       (r.getOrganizationName() != null && r.getOrganizationName().toLowerCase(Locale.ROOT).contains(q)) ||
                       (r.getIdNumber() != null && r.getIdNumber().toLowerCase(Locale.ROOT).contains(q)) ||
                       (r.getBusinessRegistrationNumber() != null && r.getBusinessRegistrationNumber().toLowerCase(Locale.ROOT).contains(q)) ||
                       (r.getStatus() != null && r.getStatus().displayName().toLowerCase(Locale.ROOT).contains(q));
            }).toList();

            table.setItems(FXCollections.observableArrayList(filtered));
        };
        updateFilteredListHolder[0] = updateFilteredList;

        for (String label : tabLabels) {
            Button tb = new Button(label);
            tb.getStyleClass().add("secondary-button");
            tb.setStyle("-fx-font-size: 11.5px; -fx-padding: 5 12;");
            if (label.equalsIgnoreCase(currentFilter[0])) {
                tb.setStyle(tb.getStyle() + "; -fx-background-color: #1A2634; -fx-text-fill: #FFFFFF; -fx-border-color: #1A2634;");
            }
            tb.setOnAction(e -> {
                selectFilter.accept(label);
            });
            tabButtons.add(tb);
            tabPills.getChildren().add(tb);
        }

        searchField.textProperty().addListener((obs, old, val) -> updateFilteredList.run());

        // Refresh Data Asynchronously
        Runnable refreshAll = () -> {
            CompletableFuture.supplyAsync(() -> {
                List<VerificationRequest> reqs = admin.verificationRequests();
                List<User> allUsers = admin.users();

                long pending = reqs.stream().filter(r -> r.getStatus() == VerificationStatus.PENDING || r.getStatus() == null).count();
                long verifiedInst = allUsers.stream().filter(u -> u.getRole() == Role.INSTRUCTOR && u.isVerified()).count();
                long verifiedEmp = allUsers.stream().filter(u -> u.getRole() == Role.EMPLOYER && u.isVerified()).count();
                long rejectedOrFlagged = reqs.stream().filter(r -> r.getStatus() == VerificationStatus.REJECTED || r.getRiskScore() >= 50).count();

                return new Object[]{reqs, pending, verifiedInst, verifiedEmp, rejectedOrFlagged};
            }).thenAccept(result -> Platform.runLater(() -> {
                @SuppressWarnings("unchecked")
                List<VerificationRequest> reqs = (List<VerificationRequest>) result[0];
                long pending = (long) result[1];
                long verifiedInst = (long) result[2];
                long verifiedEmp = (long) result[3];
                long rejected = (long) result[4];

                masterCache.clear();
                masterCache.addAll(reqs);

                pendingValLbl.setText(String.valueOf(pending));
                verifiedInstValLbl.setText(String.valueOf(verifiedInst));
                verifiedEmpValLbl.setText(String.valueOf(verifiedEmp));
                rejectedValLbl.setText(String.valueOf(rejected));

                updateFilteredList.run();
            })).exceptionally(ex -> {
                Platform.runLater(() -> {
                    log.error("Failed to load verification workspace data", ex);
                    Alerts.error("Failed to refresh verification requests: " + ex.getMessage());
                });
                return null;
            });
        };

        refreshAll.run();

        // 4. Action Controls Bar
        HBox actionsBar = new HBox(10);
        actionsBar.setAlignment(Pos.CENTER_LEFT);

        Button inspectBtn = new Button("👁 View Credentials");
        inspectBtn.getStyleClass().add("secondary-button");
        inspectBtn.setOnAction(e -> {
            VerificationRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Please select a verification request from the table to inspect.");
                return;
            }
            openInspectCredentialsModal(selected, refreshAll);
        });

        Button approveBtn = new Button("✔ Approve & Verify");
        approveBtn.getStyleClass().add("primary-button");
        approveBtn.setStyle("-fx-text-fill: #FFFFFF;");
        approveBtn.setOnAction(e -> {
            VerificationRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Please select a verification request to approve.");
                return;
            }
            if (selected.getStatus() == VerificationStatus.VERIFIED) {
                Alerts.info("Account @" + selected.getUsername() + " is already verified.");
                return;
            }
            processApproval(selected, refreshAll);
        });

        Button rejectBtn = new Button("❌ Reject with Remarks");
        rejectBtn.getStyleClass().add("secondary-button");
        rejectBtn.setStyle("-fx-text-fill: #9E0B0F; -fx-border-color: #9E0B0F;");
        rejectBtn.setOnAction(e -> {
            VerificationRequest selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Alerts.error("Please select a verification request to reject.");
                return;
            }
            processRejection(selected, refreshAll);
        });

        Button refreshBtn = new Button("🔄 Refresh Queue");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> refreshAll.run());

        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);

        actionsBar.getChildren().addAll(inspectBtn, approveBtn, rejectBtn, btnSpacer, refreshBtn);

        controlsRow.getChildren().addAll(tabPills, searchField);
        queueTray.getChildren().addAll(trayTitle, traySub, controlsRow, table, actionsBar);

        layout.getChildren().addAll(hero, metricRow, queueTray);
        return layout;
    }

    private static VBox buildMetricCard(String title, Label valLbl, String cardColorClass, String note) {
        return buildMetricCard(title, valLbl, cardColorClass, note, null);
    }

    private static VBox buildMetricCard(String title, Label valLbl, String cardColorClass, String note, Runnable onClick) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("clay-card", cardColorClass);
        card.setPadding(new Insets(16, 20, 16, 20));

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label t = new Label(title);
        t.getStyleClass().add("metric-title");
        t.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #1A2634;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(t, spacer);

        Label arrowHint = null;
        if (onClick != null) {
            arrowHint = new Label("→");
            arrowHint.getStyleClass().add("card-arrow-hint");
            arrowHint.setStyle("-fx-font-size: 15px; -fx-font-weight: 900; -fx-text-fill: #9E0B0F; -fx-opacity: 0.35;");
            topRow.getChildren().add(arrowHint);
        }

        valLbl.setStyle("-fx-font-size: 26px; -fx-font-weight: 900; -fx-text-fill: #1A2634;");

        Label n = new Label(note);
        n.setStyle("-fx-font-size: 11px; -fx-text-fill: #6E6862;");

        card.getChildren().addAll(topRow, valLbl, n);

        if (onClick != null) {
            Ui.makeInteractive(card, arrowHint, onClick);
        }
        return card;
    }

    private static void openInspectCredentialsModal(VerificationRequest req, Runnable onUpdated) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Identity Credential Examination");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStylesheets().add(AdminViews.class.getResource("/com/nextit/css/application.css").toExternalForm());
        dialog.getDialogPane().setStyle("-fx-background-color: #FDFBFA;");

        VBox root = new VBox(16);
        root.setPrefWidth(580);
        root.setMaxWidth(620);
        root.setPadding(new Insets(20));

        // 1. Applicant Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #C6A75E; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14;");

        StackPane avatar = Ui.avatar(Ui.initials(req.getUserFullName()), req.getRole() == Role.INSTRUCTOR ? "avatar-mint" : "avatar-yellow");
        VBox titles = new VBox(2);
        Label nameLbl = new Label(req.getUserFullName() + " (@" + req.getUsername() + ")");
        nameLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #1A2634;");
        Label roleEmailLbl = new Label("Claimed Role: " + req.getRole() + " • Email: " + req.getUserEmail());
        roleEmailLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #4A5A6A;");
        titles.getChildren().addAll(nameLbl, roleEmailLbl);
        header.getChildren().addAll(avatar, titles);

        // 2. Credential Breakdown Grid
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c1, c2);

        if (req.getRole() == Role.INSTRUCTOR) {
            grid.add(Ui.propertyTile("Faculty Employee ID", value(req.getIdNumber())), 0, 0);
            grid.add(Ui.propertyTile("Department / Faculty", value(req.getOrganizationName())), 1, 0);
            grid.add(Ui.propertyTile("Institutional Email", value(req.getInstitutionalEmail())), 0, 1);
            grid.add(Ui.propertyTile("Specialization / Rank", value(req.getDocumentReference())), 1, 1);
        } else {
            grid.add(Ui.propertyTile("Company Name", value(req.getOrganizationName())), 0, 0);
            grid.add(Ui.propertyTile("Business Reg / TIN / SEC", value(req.getBusinessRegistrationNumber())), 1, 0);
            grid.add(Ui.propertyTile("Work / Corporate Email", value(req.getInstitutionalEmail() != null ? req.getInstitutionalEmail() : req.getUserEmail())), 0, 1);
            grid.add(Ui.propertyTile("Website / Document URL", value(req.getDocumentReference())), 1, 1);
        }

        VBox credTray = Ui.tray("Submitted Credentials", "Information provided by applicant during registration", grid);

        // 3. Anti-Fraud & Risk Assessment Box
        VBox riskBox = new VBox(6);
        int score = req.getRiskScore();
        boolean highRisk = score >= 50;
        String boxBg = highRisk ? "#FDF2F2" : (score >= 20 ? "#FEF9E7" : "#F0FDF4");
        String boxBorder = highRisk ? "#9E0B0F" : (score >= 20 ? "#D97706" : "#10B981");

        riskBox.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12;", boxBg, boxBorder));

        Label riskTitle = new Label("Anti-Fraud Risk Assessment Score: " + score + "/100");
        riskTitle.setStyle(String.format("-fx-font-weight: 800; -fx-font-size: 13px; -fx-text-fill: %s;", boxBorder));

        String flags = req.getRiskFlags() != null && !req.getRiskFlags().isBlank() ? req.getRiskFlags() : "No security flags triggered. Clean identity profile.";
        Label flagsLbl = new Label("Flags: " + flags);
        flagsLbl.setWrapText(true);
        flagsLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #1A2634;");

        riskBox.getChildren().addAll(riskTitle, flagsLbl);

        // 4. Notes & Submission Context
        VBox notesBox = new VBox(4);
        Label notesTitle = new Label("Applicant Verification Statement:");
        notesTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2634;");
        Label notesContent = new Label(value(req.getSubmissionNotes()));
        notesContent.setWrapText(true);
        notesContent.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #4A5A6A; -fx-background-color: #FFFFFF; -fx-padding: 8; -fx-background-radius: 6; -fx-border-color: #E5E7EB; -fx-border-radius: 6;");
        notesBox.getChildren().addAll(notesTitle, notesContent);

        root.getChildren().addAll(header, credTray, riskBox, notesBox);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
        scroll.setMaxHeight(600);

        dialog.getDialogPane().setContent(scroll);

        ButtonType btnApprove = new ButtonType("✔ Approve Application", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnReject = new ButtonType("❌ Reject Application", ButtonBar.ButtonData.OTHER);
        ButtonType btnClose = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(btnApprove, btnReject, btnClose);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == btnApprove) {
                processApproval(req, onUpdated);
            } else if (bt == btnReject) {
                processRejection(req, onUpdated);
            }
        });
    }

    private static void processApproval(VerificationRequest req, Runnable onUpdated) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Verification Approval");
        confirm.setHeaderText("Verify @" + req.getUsername() + " (" + req.getRole() + ")?");
        confirm.setContentText("Approving this application grants the user verified " + req.getRole() + " workspace privileges.");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) return;

        int adminId = Session.currentUser() != null ? Session.currentUser().getUserId() : 1;

        CompletableFuture.runAsync(() -> {
            admin.approveVerification(req.getRequestId(), adminId, "Approved by administrator");
            String toEmail = req.getInstitutionalEmail() != null && !req.getInstitutionalEmail().isBlank()
                    ? req.getInstitutionalEmail() : req.getUserEmail();
            if (toEmail != null && !toEmail.isBlank()) {
                EmailService.sendVerificationDecisionEmail(toEmail, req.getUserFullName(), req.getRole(), true, "Welcome to NextIT!");
            }
        }).thenRun(() -> Platform.runLater(() -> {
            Alerts.info("Application for @" + req.getUsername() + " approved successfully!");
            if (onUpdated != null) onUpdated.run();
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                log.error("Approval failed for request {}", req.getRequestId(), ex);
                Alerts.error("Approval failed: " + ex.getMessage());
            });
            return null;
        });
    }

    private static void processRejection(VerificationRequest req, Runnable onUpdated) {
        TextInputDialog prompt = new TextInputDialog("Insufficient or unverifiable credential documentation.");
        prompt.setTitle("Reject Verification Application");
        prompt.setHeaderText("Specify Rejection Reason for @" + req.getUsername());
        prompt.setContentText("Rejection remarks (shared with user):");

        prompt.showAndWait().ifPresent(remarks -> {
            String trimmed = remarks.trim();
            if (trimmed.isBlank()) {
                Alerts.error("A rejection reason is required so the applicant can correct their submission.");
                return;
            }

            int adminId = Session.currentUser() != null ? Session.currentUser().getUserId() : 1;

            CompletableFuture.runAsync(() -> {
                admin.rejectVerification(req.getRequestId(), adminId, trimmed);
                String toEmail = req.getInstitutionalEmail() != null && !req.getInstitutionalEmail().isBlank()
                        ? req.getInstitutionalEmail() : req.getUserEmail();
                if (toEmail != null && !toEmail.isBlank()) {
                    EmailService.sendVerificationDecisionEmail(toEmail, req.getUserFullName(), req.getRole(), false, trimmed);
                }
            }).thenRun(() -> Platform.runLater(() -> {
                Alerts.info("Application for @" + req.getUsername() + " rejected. Applicant has been notified.");
                if (onUpdated != null) onUpdated.run();
            })).exceptionally(ex -> {
                Platform.runLater(() -> {
                    log.error("Rejection failed for request {}", req.getRequestId(), ex);
                    Alerts.error("Rejection failed: " + ex.getMessage());
                });
                return null;
            });
        });
    }

    public static Node profile() {
        User account = profiles.account(Session.currentUser().getUserId());

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

        Region goldSeparator = new Region();
        goldSeparator.getStyleClass().add("profile-gold-separator");
        VBox.setMargin(goldSeparator, new Insets(4, 0, 0, 0));

        identityDetails.getChildren().addAll(nameLabel, badges, goldSeparator);

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        VBox heroRight = new VBox(6);
        heroRight.setAlignment(Pos.CENTER_RIGHT);
        Label roleHighlight = new Label("Administrator Access");
        roleHighlight.getStyleClass().add("profile-hero-highlight-title");
        Label statusHighlight = new Label("Full System Clearance");
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

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
