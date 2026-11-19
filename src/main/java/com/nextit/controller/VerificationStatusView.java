package com.nextit.controller;

import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.model.VerificationRequest;
import com.nextit.model.VerificationStatus;
import com.nextit.repository.JdbcUserRepository;
import com.nextit.repository.JdbcVerificationRequestRepository;
import com.nextit.security.Session;
import com.nextit.service.AntiFakeIdentityService;
import com.nextit.service.SecurityAuditService;
import com.nextit.util.Alerts;
import com.nextit.util.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

public final class VerificationStatusView {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    private VerificationStatusView() {}

    public static Node create(Stage stage, User initialUser, Runnable onLogout, java.util.function.Consumer<User> onVerified) {
        VBox root = new VBox(20);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(30, 40, 40, 40));
        root.setStyle("-fx-background-color: #FDFBFA;");

        final User[] currentUser = new User[]{ initialUser };
        JdbcUserRepository userRepo = new JdbcUserRepository();
        JdbcVerificationRequestRepository requestRepo = new JdbcVerificationRequestRepository();

        // 1. Top Header
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setMaxWidth(860);

        Label brandBadge = Ui.badge("NextIT Security", "badge-pill-pink");
        brandBadge.setStyle(brandBadge.getStyle() + "; -fx-font-size: 13px; -fx-padding: 4 14;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button logoutBtn = new Button("🚪 Logout");
        logoutBtn.getStyleClass().add("secondary-button");
        logoutBtn.setOnAction(e -> {
            if (onLogout != null) onLogout.run();
        });

        topBar.getChildren().addAll(brandBadge, spacer, logoutBtn);

        // 2. Main Tray
        VBox mainTray = new VBox(24);
        mainTray.setMaxWidth(860);
        mainTray.getStyleClass().add("clay-tray");
        mainTray.setPadding(new Insets(30, 36, 36, 36));

        VBox contentHolder = new VBox(20);
        mainTray.getChildren().add(contentHolder);

        Runnable renderContent = () -> {
            contentHolder.getChildren().clear();
            User u = currentUser[0];
            VerificationStatus st = u.getVerificationStatus();
            Optional<VerificationRequest> reqOpt = requestRepo.findByUserId(u.getUserId());

            // Status Badge & Title
            HBox statusHeader = new HBox(14);
            statusHeader.setAlignment(Pos.CENTER_LEFT);

            String pillClass = switch (st) {
                case VERIFIED -> "badge-pill-mint";
                case PENDING -> "badge-pill-yellow";
                case REJECTED -> "badge-pill-pink";
                case SUSPENDED -> "badge-pill-pink";
                case UNVERIFIED -> "badge-pill-cream";
            };
            Label statusBadge = Ui.badge("● " + st.displayName(), pillClass);
            statusBadge.setStyle(statusBadge.getStyle() + "; -fx-font-size: 14px; -fx-padding: 6 16; -fx-font-weight: 800;");

            Label roleBadge = Ui.badge(String.valueOf(u.getRole()), "badge-pill-cream");

            statusHeader.getChildren().addAll(statusBadge, roleBadge);

            Label titleLabel = new Label();
            titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: #414141;");

            Label descLabel = new Label();
            descLabel.setWrapText(true);
            descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #6E6862; -fx-line-spacing: 2px;");

            VBox infoBox = new VBox(10);
            infoBox.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 20; -fx-padding: 20; -fx-border-color: #C6A75E; -fx-border-radius: 20;");

            if (st == VerificationStatus.PENDING) {
                titleLabel.setText("Identity Verification in Progress");
                descLabel.setText("Your submitted institutional credentials are being reviewed by authorized campus verifiers. In accordance with anti-impersonation and data security policies, role-specific features (portfolios, evaluations, and talent discovery) are unlocked upon official approval.");

                Label l1 = new Label("• Account: " + u.getFullName() + " (@" + u.getUsername() + ")");
                Label l2 = new Label("• Registered Email: " + u.getEmail());
                Label l3 = new Label("• Role Claimed: " + u.getRole());
                Label l4 = new Label(reqOpt.map(r -> "• Submitted Identifier / Proof: " + (r.getIdNumber() != null ? r.getIdNumber() : r.getBusinessRegistrationNumber()) + " (" + (r.getOrganizationName() != null ? r.getOrganizationName() : "Institution") + ")").orElse("• Verification Request Logged"));
                Label l5 = new Label(reqOpt.map(r -> "• Submission Date: " + (r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_FMT) : "Recent")).orElse(""));

                for (Label l : new Label[]{l1, l2, l3, l4, l5}) {
                    l.setStyle("-fx-font-size: 12px; -fx-text-fill: #414141; -fx-font-weight: 600;");
                }
                infoBox.getChildren().addAll(l1, l2, l3, l4, l5);
            } else if (st == VerificationStatus.REJECTED) {
                titleLabel.setText("Verification Requirements Not Satisfied");
                String reason = u.getRejectionReason() != null && !u.getRejectionReason().isBlank()
                        ? u.getRejectionReason()
                        : "Submitted identification credentials could not be validated against official registrar or business records.";
                descLabel.setText("Your identity verification application was not approved. Review the verifier feedback below and update your credentials to resubmit.");

                VBox reasonTray = new VBox(6);
                reasonTray.setStyle("-fx-background-color: #FAF4EE; -fx-background-radius: 14; -fx-padding: 14; -fx-border-color: #9E0B0F; -fx-border-radius: 14;");
                Label reasonH = new Label("Rejection Reason Provided by Verifier:");
                reasonH.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #9E0B0F;");
                Label reasonB = new Label(reason);
                reasonB.setWrapText(true);
                reasonB.setStyle("-fx-font-size: 12px; -fx-text-fill: #414141; -fx-font-weight: bold;");
                reasonTray.getChildren().addAll(reasonH, reasonB);
                infoBox.getChildren().add(reasonTray);
            } else if (st == VerificationStatus.SUSPENDED) {
                titleLabel.setText("Account Suspended");
                descLabel.setText("This account has been temporarily restricted due to security flags or duplicate identity detection. Access to all NextIT services is suspended pending an official inquiry.");
                Label contactNote = new Label("To resolve this restriction, please contact campus IT Security at security@nextit.edu with proof of identity.");
                contactNote.setWrapText(true);
                contactNote.setStyle("-fx-font-size: 12px; -fx-text-fill: #9E0B0F; -fx-font-weight: bold;");
                infoBox.getChildren().add(contactNote);
            } else {
                titleLabel.setText("Account Verification Required");
                descLabel.setText("Please submit your institutional proof to verify your account and access role features.");
            }

            // Timeline graphic
            HBox timeline = new HBox(12);
            timeline.setAlignment(Pos.CENTER);
            timeline.setStyle("-fx-background-color: #EFE2CA; -fx-background-radius: 18; -fx-padding: 14; -fx-border-color: #C6A75E; -fx-border-radius: 18; -fx-border-width: 1;");

            Label step1 = Ui.badge("1. Submitted ✓", "badge-pill-mint");
            Label arrow1 = new Label("➔");
            arrow1.setStyle("-fx-text-fill: #9E0B0F; -fx-font-weight: bold;");
            Label step2 = Ui.badge("2. Verifier Review", st == VerificationStatus.PENDING ? "badge-pill-yellow" : (st == VerificationStatus.VERIFIED ? "badge-pill-mint" : "badge-pill-pink"));
            Label arrow2 = new Label("➔");
            arrow2.setStyle("-fx-text-fill: #9E0B0F; -fx-font-weight: bold;");
            Label step3 = Ui.badge("3. Role Workspace", st == VerificationStatus.VERIFIED ? "badge-pill-mint" : "badge-pill-cream");

            timeline.getChildren().addAll(step1, arrow1, step2, arrow2, step3);

            // Bottom Actions
            HBox actions = new HBox(12);
            actions.setAlignment(Pos.CENTER_RIGHT);

            Button refreshBtn = new Button("🔄 Refresh Status");
            refreshBtn.getStyleClass().add("primary-button");
            refreshBtn.setOnAction(ev -> {
                Optional<User> fresh = userRepo.findById(u.getUserId());
                if (fresh.isPresent()) {
                    currentUser[0] = fresh.get();
                    Session.login(fresh.get());
                    if (fresh.get().getVerificationStatus() == VerificationStatus.VERIFIED) {
                        Alerts.info("Congratulations! Your account identity has been verified.");
                        if (onVerified != null) onVerified.accept(fresh.get());
                        return;
                    }
                }
                Alerts.info("Status updated. Current status: " + currentUser[0].getVerificationStatus().displayName());
            });

            actions.getChildren().add(refreshBtn);

            if (st == VerificationStatus.REJECTED) {
                Button resubmitBtn = new Button("📝 Edit & Resubmit Application");
                resubmitBtn.getStyleClass().add("secondary-button");
                resubmitBtn.setOnAction(ev -> openResubmitModal(stage, currentUser[0], requestRepo, () -> {
                    Optional<User> fresh = userRepo.findById(u.getUserId());
                    fresh.ifPresent(updatedUser -> currentUser[0] = updatedUser);
                }));
                actions.getChildren().add(0, resubmitBtn);
            }

            contentHolder.getChildren().addAll(statusHeader, titleLabel, descLabel, timeline, infoBox, actions);
        };

        renderContent.run();
        root.getChildren().addAll(topBar, mainTray);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("content-scroll");
        return scroll;
    }

    private static void openResubmitModal(Stage stage, User user, JdbcVerificationRequestRepository requestRepo, Runnable onDone) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Resubmit Verification Credentials");
        dialog.setHeaderText("Update Institutional Verification Details");

        VBox box = new VBox(12);
        box.setPrefWidth(420);

        Label lbl = new Label("Please update your identification details and proof notes for reviewer re-examination:");
        lbl.setWrapText(true);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #414141;");

        TextField idField = Ui.field(user.getRole() == Role.STUDENT ? "Student ID Number (e.g. 2026-0042)" : (user.getRole() == Role.INSTRUCTOR ? "Faculty Employee Number" : "Business Registration / TIN"));
        TextField orgField = Ui.field(user.getRole() == Role.STUDENT ? "Degree Program / University" : (user.getRole() == Role.INSTRUCTOR ? "Department / Faculty" : "Company Name"));
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Additional proof details, registrar verification notes, or explanation...");
        notesArea.setPrefRowCount(3);

        box.getChildren().addAll(lbl, idField, orgField, notesArea);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                String idVal = idField.getText().trim();
                String orgVal = orgField.getText().trim();
                String noteVal = notesArea.getText().trim();

                if (idVal.isBlank()) {
                    Alerts.error("ID Number or Registration Number is required.");
                    return;
                }

                // Analyze with AntiFakeIdentityService
                AntiFakeIdentityService antiFake = new AntiFakeIdentityService();
                AntiFakeIdentityService.AnalysisResult analysis;
                if (user.getRole() == Role.STUDENT) {
                    analysis = antiFake.analyzeStudent(idVal, user.getEmail(), user.getFirstName(), user.getLastName(), orgVal);
                } else if (user.getRole() == Role.INSTRUCTOR) {
                    analysis = antiFake.analyzeInstructor(idVal, user.getEmail(), user.getFirstName(), user.getLastName(), orgVal);
                } else {
                    analysis = antiFake.analyzeEmployer(orgVal, idVal, user.getEmail(), user.getFullName());
                }

                VerificationStatus newStatus = analysis.autoApproved() ? VerificationStatus.VERIFIED : VerificationStatus.PENDING;

                // Update verification request
                Optional<VerificationRequest> existingReq = requestRepo.findByUserId(user.getUserId());
                VerificationRequest req = existingReq.orElseGet(() -> {
                    VerificationRequest r = new VerificationRequest();
                    r.setUserId(user.getUserId());
                    r.setRole(user.getRole());
                    return r;
                });
                req.setIdNumber(idVal);
                req.setOrganizationName(orgVal);
                req.setSubmissionNotes(noteVal + " (Re-evaluated: " + analysis.reviewerNotes() + ")");
                req.setStatus(newStatus);
                req.setRiskScore(analysis.riskScore());
                req.setRiskFlags(String.join(",", analysis.riskFlags()));

                if (existingReq.isPresent()) {
                    requestRepo.update(req);
                } else {
                    requestRepo.save(req);
                }

                JdbcUserRepository userRepo = new JdbcUserRepository();
                userRepo.updateVerificationStatus(user.getUserId(), newStatus, null, null);
                user.setVerificationStatus(newStatus);
                user.setRejectionReason(null);

                SecurityAuditService.getInstance().log(user.getUserId(), user.getUsername(),
                        "VERIFICATION_RESUBMITTED", user.getUserId(), "127.0.0.1",
                        "Resubmitted verification details. New status: " + newStatus, "INFO");

                Alerts.info("Verification details resubmitted successfully! Status: " + newStatus.displayName());
                if (onDone != null) onDone.run();
            }
        });
    }
}
