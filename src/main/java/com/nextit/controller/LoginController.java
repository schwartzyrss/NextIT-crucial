package com.nextit.controller;

import com.nextit.config.AppConfig;
import com.nextit.exception.AppException;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.service.AuthenticationService;
import com.nextit.service.OtpService;
import com.nextit.util.Alerts;
import com.nextit.util.Ui;
import com.nextit.util.WindowBoundsManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private Button togglePasswordBtn;
    @FXML private Hyperlink forgotPasswordLink;
    @FXML private SVGPath eyeIcon;
    @FXML private Label errorLabel;
    @FXML private Button signInBtn;

    private boolean passwordVisible = false;

    private static final String EYE_OPEN_PATH = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String EYE_CLOSED_PATH = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.44-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z";

    private AuthenticationService authenticationService;

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @FXML
    public void initialize() {
        // Ready for credential entry
    }

    @FXML
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void togglePassword() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            visiblePasswordField.setText(passwordField.getText());
            visiblePasswordField.setManaged(true);
            visiblePasswordField.setVisible(true);
            passwordField.setManaged(false);
            passwordField.setVisible(false);
            visiblePasswordField.requestFocus();
            visiblePasswordField.positionCaret(visiblePasswordField.getText().length());
            if (eyeIcon != null) {
                eyeIcon.setContent(EYE_CLOSED_PATH);
                eyeIcon.setFill(javafx.scene.paint.Color.web("#9E0B0F"));
            }
        } else {
            passwordField.setText(visiblePasswordField.getText());
            passwordField.setManaged(true);
            passwordField.setVisible(true);
            visiblePasswordField.setManaged(false);
            visiblePasswordField.setVisible(false);
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
            if (eyeIcon != null) {
                eyeIcon.setContent(EYE_OPEN_PATH);
                eyeIcon.setFill(javafx.scene.paint.Color.web("#414141"));
            }
        }
    }

    private String enteredPassword() {
        return passwordVisible ? visiblePasswordField.getText() : passwordField.getText();
    }

    @FXML
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void handleLogin() {
        try {
            errorLabel.setStyle("");
            errorLabel.setText("");
            if (authenticationService == null) {
                authenticationService = new AuthenticationService();
            }
            String username = usernameField.getText();
            String password = enteredPassword();
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                errorLabel.setText("Please enter your username and password.");
                return;
            }

            setLoginBusy(true, "⏳ Authenticating...");

            CompletableFuture.supplyAsync(() -> authenticationService.login(username, password))
                    .thenAccept(user -> Platform.runLater(() -> {
                        try {
                            setLoginBusy(false, null);
                            transitionToMain(user);
                        } catch (Exception ex) {
                            log.error("Failed to transition to main view after login", ex);
                            setLoginBusy(false, "Failed to load dashboard: " + ex.getMessage());
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            setLoginBusy(false, null);
                            com.nextit.security.Session.logout();
                            Throwable cause = ex;
                            while (cause.getCause() != null && cause.getCause() != cause) {
                                cause = cause.getCause();
                            }
                            if (cause instanceof AuthenticationService.MfaRequiredException mfa) {
                                promptMfaDialog(mfa.getUser(), mfa.getMessage());
                            } else if (cause instanceof AppException appEx) {
                                errorLabel.setStyle("-fx-text-fill: #9E0B0F; -fx-font-weight: 700; -fx-font-size: 11.5px; -fx-background-color: #FDF2F2; -fx-border-color: #F5C2C7; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 12;");
                                errorLabel.setText(appEx.getMessage());
                            } else {
                                log.error("Login failed due to an unexpected error", cause);
                                Alerts.error("Could not log in: " + (cause.getMessage() != null ? cause.getMessage() : "Please check database connection."));
                            }
                        });
                        return null;
                    });
        } catch (Exception e) {
            log.error("Login initiation error", e);
            setLoginBusy(false, "Login failed: " + e.getMessage());
        }
    }

    private void setLoginBusy(boolean busy, String statusMessage) {
        if (signInBtn != null) signInBtn.setDisable(busy);
        if (usernameField != null) usernameField.setDisable(busy);
        if (passwordField != null) passwordField.setDisable(busy);
        if (visiblePasswordField != null) visiblePasswordField.setDisable(busy);

        if (errorLabel != null) {
            if (busy) {
                errorLabel.setStyle("-fx-text-fill: #1A2634; -fx-font-weight: 700; -fx-font-size: 11.5px; -fx-background-color: #FAF5E8; -fx-border-color: #DBC8AA; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 12;");
                errorLabel.setText(statusMessage != null ? statusMessage : "⏳ Authenticating...");
            } else if (statusMessage != null) {
                errorLabel.setStyle("-fx-text-fill: #9E0B0F; -fx-font-weight: 700; -fx-font-size: 11.5px; -fx-background-color: #FDF2F2; -fx-border-color: #F5C2C7; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 12;");
                errorLabel.setText(statusMessage);
            } else {
                errorLabel.setStyle("");
                errorLabel.setText("");
            }
        }
    }

    @FXML
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void handleForgotPassword() {
        errorLabel.setStyle("");
        errorLabel.setText("");
        if (authenticationService == null) {
            authenticationService = new AuthenticationService();
        }
        showForgotPasswordDialog();
    }

    @SuppressWarnings("unchecked")
    private void showForgotPasswordDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Account Password Recovery");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/nextit/css/application.css").toExternalForm());
        dialog.getDialogPane().setStyle("-fx-background-color: #FDFBFA;");

        dialog.setOnShowing(ev -> {
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
                WindowBoundsManager.loadStageIcons(stage);
            }
        });

        if (errorLabel.getScene() != null && errorLabel.getScene().getWindow() instanceof Stage ownerStage) {
            dialog.initOwner(ownerStage);
        }

        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeType);
        Node sysCloseBtn = dialog.getDialogPane().lookupButton(closeType);
        if (sysCloseBtn != null) {
            sysCloseBtn.setVisible(false);
            sysCloseBtn.setManaged(false);
        }

        VBox root = new VBox(14);
        root.setPrefWidth(520);
        root.setMaxWidth(520);
        root.setPadding(new Insets(16, 20, 16, 20));

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.getStyleClass().add("dialog-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent; -fx-padding: 0;");

        // Comprehensive mouse wheel & trackpad scroll handler everywhere inside dialog
        EventHandler<ScrollEvent> globalScrollHandler = ev -> {
            double deltaY = ev.getDeltaY();
            if (deltaY == 0) return;

            double contentH = Math.max(root.getBoundsInLocal().getHeight(), root.prefHeight(520));
            double viewportH = scrollPane.getViewportBounds().getHeight();
            if (viewportH <= 0) viewportH = scrollPane.getHeight();

            double scrollableRange = contentH - viewportH;
            if (scrollableRange > 1) {
                double mult = (ev.getMultiplierY() > 0) ? ev.getMultiplierY() : 1.0;
                double scrollDelta = (Math.abs(deltaY) >= 1.0) ? deltaY : Math.signum(deltaY) * 30.0;
                double pixels = scrollDelta * mult;
                if (Math.abs(pixels) < 25) {
                    pixels = Math.signum(deltaY) * 30;
                }
                double vstep = pixels / scrollableRange;
                double nextVal = Math.max(0.0, Math.min(1.0, scrollPane.getVvalue() - vstep));
                scrollPane.setVvalue(nextVal);
                ev.consume();
            }
        };
        dialog.getDialogPane().addEventFilter(ScrollEvent.SCROLL, globalScrollHandler);
        scrollPane.addEventFilter(ScrollEvent.SCROLL, globalScrollHandler);

        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().setMinWidth(480);
        dialog.getDialogPane().setMinHeight(500);

        // Top Banner Emblem & Steps
        VBox topBanner = new VBox(10);
        topBanner.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EFE2CA; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14; -fx-effect: dropshadow(gaussian, rgba(198, 167, 94, 0.15), 6, 0.05, 0, 2);");

        HBox bannerHeader = new HBox(12);
        bannerHeader.setAlignment(Pos.CENTER_LEFT);
        ImageView modalLogo = new ImageView();
        modalLogo.setFitWidth(32);
        modalLogo.setFitHeight(32);
        modalLogo.setPreserveRatio(true);
        modalLogo.setSmooth(true);
        try (InputStream is = getClass().getResourceAsStream("/images/nextit-logo.png")) {
            if (is != null) modalLogo.setImage(new Image(is));
        } catch (Exception ignored) {}
        VBox bannerTitles = new VBox(2);
        Label title = new Label("Account Password Recovery");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #9E0B0F;");
        Label subtitle = new Label("Follow the 3 steps to verify your identity and set a new password.");
        subtitle.setWrapText(true);
        subtitle.setMinHeight(Region.USE_PREF_SIZE);
        subtitle.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6E6862;");
        bannerTitles.getChildren().addAll(title, subtitle);
        bannerHeader.getChildren().addAll(modalLogo, bannerTitles);

        HBox stepPills = new HBox(8);
        stepPills.setAlignment(Pos.CENTER_LEFT);
        Label pill1 = Ui.badge("1. Account Lookup", "badge-pill-pink");
        Label pill2 = Ui.badge("2. Verify OTP", "badge-pill-cream");
        Label pill3 = Ui.badge("3. New Password", "badge-pill-cream");
        stepPills.getChildren().addAll(pill1, pill2, pill3);

        topBanner.getChildren().addAll(bannerHeader, stepPills);

        VBox stepContent = new VBox(12);

        root.getChildren().addAll(topBanner, stepContent);
        dialog.getDialogPane().setContent(scrollPane);

        Runnable resizeDialog = () -> Platform.runLater(() -> {
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
                root.applyCss();
                root.layout();
                double contentH = Math.max(root.prefHeight(520), root.getBoundsInLocal().getHeight());
                if (contentH < 400) {
                    contentH = 540;
                }

                Screen screen = Screen.getPrimary();
                Rectangle2D bounds = (screen != null) ? screen.getVisualBounds() : new Rectangle2D(0, 0, 1920, 1080);

                // OS window trim: title bar (~32-40px) + window border padding (~16px) + DialogPane padding (~32px)
                double windowTrimH = 80;
                double desiredStageH = contentH + windowTrimH;
                double maxAllowedStageH = Math.max(520, bounds.getHeight() - 60);

                double targetStageH = Math.min(desiredStageH, maxAllowedStageH);
                double availableScrollH = targetStageH - windowTrimH;

                scrollPane.setPrefHeight(availableScrollH);
                scrollPane.setMaxHeight(availableScrollH);

                stage.setHeight(targetStageH);
                dialog.getDialogPane().setPrefHeight(targetStageH);

                dialog.getDialogPane().applyCss();
                dialog.getDialogPane().layout();
                stage.sizeToScene();

                // Re-enforce targetStageH in case sizeToScene shrank it below what is needed
                if (stage.getHeight() < targetStageH) {
                    stage.setHeight(targetStageH);
                }

                if (stage.getY() + targetStageH > bounds.getMaxY() - 15) {
                    stage.setY(Math.max(bounds.getMinY() + 15, bounds.getMaxY() - targetStageH - 25));
                }
            }
        });

        dialog.setOnShown(ev -> resizeDialog.run());

        User[] targetUser = new User[1];
        Timeline[] cooldownTimer = new Timeline[1];

        dialog.setOnCloseRequest(ev -> {
            if (cooldownTimer[0] != null) cooldownTimer[0].stop();
        });

        Runnable[] showStep1 = new Runnable[1];
        Runnable[] showStep2 = new Runnable[1];
        java.util.function.Consumer<OtpService.OtpResponse>[] showStep2WithResp = new java.util.function.Consumer[1];
        Runnable[] showStep3 = new Runnable[1];

        // Step 1: Account Lookup & Code Dispatch
        showStep1[0] = () -> {
            scrollPane.setVvalue(0.0);
            stepContent.getChildren().clear();
            title.setText("Account Password Recovery");
            subtitle.setText("Follow the 3 steps to verify your identity and set a new password.");
            pill1.setText("1. Account Lookup");
            pill2.setText("2. Verify OTP");
            pill3.setText("3. New Password");
            pill1.getStyleClass().setAll("badge-pill", "badge-pill-pink");
            pill2.getStyleClass().setAll("badge-pill", "badge-pill-cream");
            pill3.getStyleClass().setAll("badge-pill", "badge-pill-cream");

            Label stepDesc = new Label("Enter your registered username or institutional email address:");
            stepDesc.setWrapText(true);
            stepDesc.setMaxWidth(Double.MAX_VALUE);
            stepDesc.setMinHeight(Region.USE_PREF_SIZE);
            stepDesc.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #414141;");

            TextField accountField = new TextField();
            accountField.setPromptText("Username or email address");
            accountField.getStyleClass().add("text-field");
            String prefilled = (usernameField.getText() != null) ? usernameField.getText().trim() : "";
            accountField.setText(prefilled);

            Label helperNote = new Label("We will resolve your verified account and dispatch a 6-digit confirmation code to your email address.");
            helperNote.setWrapText(true);
            helperNote.setMaxWidth(Double.MAX_VALUE);
            helperNote.setMinHeight(Region.USE_PREF_SIZE);
            helperNote.setPrefWidth(470);
            helperNote.setTextOverrun(OverrunStyle.CLIP);
            helperNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #8C827A; -fx-line-spacing: 2px;");

            Label step1Msg = new Label();
            step1Msg.setWrapText(true);
            step1Msg.setMaxWidth(Double.MAX_VALUE);
            step1Msg.setMinHeight(Region.USE_PREF_SIZE);
            step1Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");

            HBox buttons = new HBox(10);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            Button cancelBtn = new Button("Cancel");
            cancelBtn.getStyleClass().add("secondary-button");
            cancelBtn.setOnAction(e -> dialog.close());

            Button sendCodeBtn = new Button("Send Verification Code");
            sendCodeBtn.getStyleClass().add("primary-button");
            sendCodeBtn.setStyle("-fx-text-fill: #FDFBFA;");

            sendCodeBtn.setOnAction(e -> {
                String input = accountField.getText() == null ? "" : accountField.getText().trim();
                if (input.isBlank()) {
                    step1Msg.setText("Please enter your username or registered email.");
                    step1Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                    return;
                }

                sendCodeBtn.setDisable(true);
                cancelBtn.setDisable(true);
                accountField.setDisable(true);
                step1Msg.setText("🔍 Searching account and dispatching verification code...");
                step1Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6E6862; -fx-font-style: italic;");

                CompletableFuture.supplyAsync(() -> authenticationService.findUserForPasswordReset(input))
                        .thenCompose(foundOpt -> {
                            if (foundOpt.isEmpty()) {
                                throw new AppException("No NextIT account found matching that username or email address.");
                            }
                            User u = foundOpt.get();
                            if (u.getEmail() == null || u.getEmail().isBlank()) {
                                throw new AppException("No registered email address is on file for this account. Please contact campus administration.");
                            }
                            targetUser[0] = u;
                            return CompletableFuture.supplyAsync(() -> OtpService.requestPasswordResetOtp(u));
                        })
                        .thenAccept(otpResp -> Platform.runLater(() -> {
                            sendCodeBtn.setDisable(false);
                            cancelBtn.setDisable(false);
                            accountField.setDisable(false);

                            if (otpResp.rateLimited()) {
                                step1Msg.setText(otpResp.message());
                                step1Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                                return;
                            }

                            showStep2WithResp[0].accept(otpResp);
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> {
                                sendCodeBtn.setDisable(false);
                                cancelBtn.setDisable(false);
                                accountField.setDisable(false);
                                Throwable cause = ex;
                                while (cause.getCause() != null && cause.getCause() != cause) {
                                    cause = cause.getCause();
                                }
                                String msg = cause.getMessage();
                                if (msg == null || msg.isBlank()) {
                                    msg = "An error occurred during account lookup. Please try again.";
                                }
                                step1Msg.setText(msg);
                                step1Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                            });
                            return null;
                        });
            });

            buttons.getChildren().addAll(cancelBtn, sendCodeBtn);

            stepContent.getChildren().addAll(stepDesc, accountField, helperNote, step1Msg, buttons);
            resizeDialog.run();
        };

        // Step 2: OTP Verification
        showStep2WithResp[0] = (otpResp) -> {
            scrollPane.setVvalue(0.0);
            stepContent.getChildren().clear();
            pill1.getStyleClass().setAll("badge-pill", "badge-pill-cream");
            pill2.getStyleClass().setAll("badge-pill", "badge-pill-pink");
            pill3.getStyleClass().setAll("badge-pill", "badge-pill-cream");

            String masked = AuthenticationService.maskEmail(targetUser[0].getEmail());
            Label stepDesc = new Label();
            Label expireNote = new Label();
            stepDesc.setWrapText(true);
            stepDesc.setMaxWidth(Double.MAX_VALUE);
            stepDesc.setMinHeight(Region.USE_PREF_SIZE);

            expireNote.setWrapText(true);
            expireNote.setMaxWidth(Double.MAX_VALUE);
            expireNote.setMinHeight(Region.USE_PREF_SIZE);

            if (otpResp != null && otpResp.sent()) {
                stepDesc.setText("Verification code dispatched to " + masked);
                stepDesc.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #1A2634;");
                expireNote.setText("The 6-digit code was dispatched securely over HTTPS (Port 443) and is valid for 10 minutes. Enter it below:");
                expireNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #4A5A6A;");
            } else {
                stepDesc.setText("Enter verification code for " + masked);
                stepDesc.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #1A2634;");
                String noteMsg = (otpResp != null && otpResp.message() != null) ? otpResp.message() : "HTTPS connection offline.";
                expireNote.setText("⚠ " + noteMsg + (com.nextit.config.AppConfig.isOfflineOtpFallbackAllowed() ? " (Testing fallback code: 123456)" : ""));
                expireNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #B45309; -fx-font-weight: 600;");
            }

            TextField otpField = new TextField();
            otpField.setPromptText("• • • • • •");
            otpField.setStyle("-fx-font-size: 22px; -fx-alignment: center; -fx-font-weight: 900; -fx-letter-spacing: 6px; -fx-background-color: #FFFFFF; -fx-border-color: #DBC8AA; -fx-border-radius: 12; -fx-background-radius: 12;");
            otpField.setTextFormatter(new TextFormatter<String>(change -> {
                String added = change.getText();
                if (added != null && (added.contains(" ") || added.contains("-"))) {
                    change.setText(added.replaceAll("[\\s-]+", ""));
                }
                String newText = change.getControlNewText();
                if (newText.length() <= 6 && newText.matches("\\d*")) {
                    return change;
                }
                return null;
            }));

            Button resendBtn = new Button("Resend Code");
            resendBtn.getStyleClass().add("secondary-button");
            resendBtn.setStyle("-fx-font-size: 11px; -fx-padding: 6 14;");

            Label resendStatus = new Label();
            resendStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #1E6B35; -fx-font-weight: 600;");

            int[] seconds = new int[]{45};
            resendBtn.setDisable(true);
            resendBtn.setText("Resend Code (" + seconds[0] + "s)");

            if (cooldownTimer[0] != null) cooldownTimer[0].stop();
            cooldownTimer[0] = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
                seconds[0]--;
                if (seconds[0] > 0) {
                    resendBtn.setText("Resend Code (" + seconds[0] + "s)");
                } else {
                    resendBtn.setText("Resend Code");
                    resendBtn.setDisable(false);
                    if (cooldownTimer[0] != null) cooldownTimer[0].stop();
                }
            }));
            cooldownTimer[0].setCycleCount(Timeline.INDEFINITE);
            cooldownTimer[0].play();

            resendBtn.setOnAction(e -> {
                resendBtn.setDisable(true);
                resendStatus.setText("Sending new code...");
                resendStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #4A5A6A; -fx-font-style: italic;");
                CompletableFuture.supplyAsync(() -> OtpService.requestPasswordResetOtp(targetUser[0]))
                        .thenAccept(resp -> Platform.runLater(() -> {
                            if (resp.rateLimited()) {
                                resendStatus.setText(resp.message());
                                resendStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                                resendBtn.setDisable(false);
                                return;
                            }
                            seconds[0] = 45;
                            resendBtn.setText("Resend Code (" + seconds[0] + "s)");
                            if (cooldownTimer[0] != null) cooldownTimer[0].playFromStart();
                            if (resp.sent()) {
                                resendStatus.setText("✔ New code sent via HTTPS (Port 443)!");
                                resendStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #1E6B35; -fx-font-weight: 600;");
                            } else {
                                resendStatus.setText("⚠ " + resp.message());
                                resendStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #B45309; -fx-font-weight: 600;");
                            }
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> {
                                resendBtn.setDisable(false);
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                resendStatus.setText(cause.getMessage());
                                resendStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #9E0B0F;");
                            });
                            return null;
                        });
            });

            HBox resendRow = new HBox(10, resendBtn, resendStatus);
            resendRow.setAlignment(Pos.CENTER_LEFT);

            Label step2Msg = new Label();
            step2Msg.setWrapText(true);
            step2Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");

            HBox buttons = new HBox(10);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            Button backBtn = new Button("Back");
            backBtn.getStyleClass().add("secondary-button");
            backBtn.setOnAction(e -> {
                if (cooldownTimer[0] != null) cooldownTimer[0].stop();
                showStep1[0].run();
            });

            Button verifyBtn = new Button("Verify Code & Continue");
            verifyBtn.getStyleClass().add("primary-button");
            verifyBtn.setStyle("-fx-text-fill: #FDFBFA;");

            verifyBtn.setOnAction(e -> {
                String raw = otpField.getText();
                String code = raw == null ? "" : raw.replaceAll("\\D", "").trim();
                if (code.length() < 6) {
                    step2Msg.setText("Please enter the complete 6-digit confirmation code.");
                    step2Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                    return;
                }

                verifyBtn.setDisable(true);
                backBtn.setDisable(true);
                resendBtn.setDisable(true);
                step2Msg.setText("⏳ Verifying confirmation code...");
                step2Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6E6862; -fx-font-style: italic;");

                CompletableFuture.supplyAsync(() -> OtpService.verifyPasswordResetOtp(targetUser[0], code))
                        .thenAccept(valid -> Platform.runLater(() -> {
                            if (valid) {
                                if (cooldownTimer[0] != null) cooldownTimer[0].stop();
                                showStep3[0].run();
                            } else {
                                verifyBtn.setDisable(false);
                                backBtn.setDisable(false);
                                if (seconds[0] <= 0) resendBtn.setDisable(false);
                                step2Msg.setText("Invalid or expired code. Please try again, click Resend" + (com.nextit.config.AppConfig.isOfflineOtpFallbackAllowed() ? ", or use testing code 123456." : "."));
                                step2Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                            }
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> {
                                verifyBtn.setDisable(false);
                                backBtn.setDisable(false);
                                if (seconds[0] <= 0) resendBtn.setDisable(false);
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                step2Msg.setText("Verification failed: " + cause.getMessage());
                                step2Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                            });
                            return null;
                        });
            });

            buttons.getChildren().addAll(backBtn, verifyBtn);
            stepContent.getChildren().addAll(stepDesc, expireNote, otpField, resendRow, step2Msg, buttons);
            resizeDialog.run();
        };

        showStep2[0] = () -> showStep2WithResp[0].accept(null);

        // Step 3: New Password Reset
        showStep3[0] = () -> {
            scrollPane.setVvalue(0.0);
            stepContent.getChildren().clear();
            pill1.getStyleClass().setAll("badge-pill", "badge-pill-cream");
            pill2.getStyleClass().setAll("badge-pill", "badge-pill-cream");
            pill3.getStyleClass().setAll("badge-pill", "badge-pill-pink");

            Label stepDesc = new Label("Set a new password for account @" + targetUser[0].getUsername() + ":");
            stepDesc.setWrapText(true);
            stepDesc.setMaxWidth(Double.MAX_VALUE);
            stepDesc.setMinHeight(Region.USE_PREF_SIZE);
            stepDesc.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #414141;");

            Label newPassLabel = new Label("NEW PASSWORD");
            newPassLabel.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 700; -fx-text-fill: #6E6862;");

            PasswordField newPassField = new PasswordField();
            newPassField.setPromptText("Enter new password (minimum 8 characters)");
            TextField visibleNewPassField = new TextField();
            visibleNewPassField.setPromptText("Enter new password (minimum 8 characters)");
            Button eyeBtn1 = new Button();
            SVGPath svg1 = new SVGPath();
            boolean[] v1 = new boolean[]{false};
            StackPane passBox1 = createEyePasswordField(newPassField, visibleNewPassField, eyeBtn1, svg1, v1);

            Label confirmPassLabel = new Label("CONFIRM NEW PASSWORD");
            confirmPassLabel.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 700; -fx-text-fill: #6E6862;");

            PasswordField confirmPassField = new PasswordField();
            confirmPassField.setPromptText("Confirm new password");
            TextField visibleConfirmPassField = new TextField();
            visibleConfirmPassField.setPromptText("Confirm new password");
            Button eyeBtn2 = new Button();
            SVGPath svg2 = new SVGPath();
            boolean[] v2 = new boolean[]{false};
            StackPane passBox2 = createEyePasswordField(confirmPassField, visibleConfirmPassField, eyeBtn2, svg2, v2);

            Label reqsLabel = new Label("• Minimum 8 characters  • Must match exactly");
            reqsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8C827A;");

            Label step3Msg = new Label();
            step3Msg.setWrapText(true);
            step3Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");

            HBox buttons = new HBox(10);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            Button cancelBtn = new Button("Cancel");
            cancelBtn.getStyleClass().add("secondary-button");
            cancelBtn.setOnAction(e -> dialog.close());

            Button resetBtn = new Button("Reset Password & Sign In");
            resetBtn.getStyleClass().add("primary-button");
            resetBtn.setStyle("-fx-text-fill: #FDFBFA;");

            resetBtn.setOnAction(e -> {
                String pass1 = v1[0] ? visibleNewPassField.getText() : newPassField.getText();
                String pass2 = v2[0] ? visibleConfirmPassField.getText() : confirmPassField.getText();

                if (pass1 == null || pass1.length() < 8) {
                    step3Msg.setText("Password must be at least 8 characters long.");
                    step3Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                    return;
                }
                if (!pass1.equals(pass2)) {
                    step3Msg.setText("Passwords do not match. Please re-enter.");
                    step3Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                    return;
                }

                resetBtn.setDisable(true);
                cancelBtn.setDisable(true);
                newPassField.setDisable(true);
                visibleNewPassField.setDisable(true);
                confirmPassField.setDisable(true);
                visibleConfirmPassField.setDisable(true);
                step3Msg.setText("⏳ Updating credentials in Supabase...");
                step3Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #6E6862; -fx-font-style: italic;");

                CompletableFuture.runAsync(() -> {
                    authenticationService.resetPassword(targetUser[0].getUserId(), pass1);
                    OtpService.clearPasswordResetOtp(targetUser[0]);
                }).thenRun(() -> Platform.runLater(() -> {
                    dialog.close();
                    handlePasswordResetSuccess(targetUser[0].getUsername());
                })).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        resetBtn.setDisable(false);
                        cancelBtn.setDisable(false);
                        newPassField.setDisable(false);
                        visibleNewPassField.setDisable(false);
                        confirmPassField.setDisable(false);
                        visibleConfirmPassField.setDisable(false);
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        step3Msg.setText("Failed to update password: " + cause.getMessage());
                        step3Msg.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #9E0B0F; -fx-font-weight: 600;");
                    });
                    return null;
                });
            });

            buttons.getChildren().addAll(cancelBtn, resetBtn);
            stepContent.getChildren().addAll(stepDesc, newPassLabel, passBox1, confirmPassLabel, passBox2, reqsLabel, step3Msg, buttons);
            resizeDialog.run();
        };

        showStep1[0].run();
        dialog.showAndWait();
    }

    private StackPane createEyePasswordField(PasswordField pf, TextField tf, Button btn, SVGPath svg, boolean[] isVisible) {
        pf.getStyleClass().add("password-input-with-eye");
        tf.getStyleClass().add("password-input-with-eye");
        tf.setManaged(false);
        tf.setVisible(false);

        svg.setContent(EYE_OPEN_PATH);
        svg.setFill(javafx.scene.paint.Color.web("#414141"));
        svg.setScaleX(0.85);
        svg.setScaleY(0.85);

        btn.setGraphic(svg);
        btn.getStyleClass().add("eye-toggle-btn");
        btn.setFocusTraversable(false);
        StackPane.setAlignment(btn, Pos.CENTER_RIGHT);
        StackPane.setMargin(btn, new Insets(0, 12, 0, 0));

        btn.setOnAction(e -> {
            isVisible[0] = !isVisible[0];
            if (isVisible[0]) {
                tf.setText(pf.getText());
                tf.setManaged(true);
                tf.setVisible(true);
                pf.setManaged(false);
                pf.setVisible(false);
                tf.requestFocus();
                tf.positionCaret(tf.getText().length());
                svg.setContent(EYE_CLOSED_PATH);
                svg.setFill(javafx.scene.paint.Color.web("#9E0B0F"));
            } else {
                pf.setText(tf.getText());
                pf.setManaged(true);
                pf.setVisible(true);
                tf.setManaged(false);
                tf.setVisible(false);
                pf.requestFocus();
                pf.positionCaret(pf.getText().length());
                svg.setContent(EYE_OPEN_PATH);
                svg.setFill(javafx.scene.paint.Color.web("#414141"));
            }
        });

        StackPane pane = new StackPane(pf, tf, btn);
        pane.setAlignment(Pos.CENTER_RIGHT);
        return pane;
    }

    private void handlePasswordResetSuccess(String username) {
        usernameField.setText(username);
        passwordField.clear();
        visiblePasswordField.clear();
        errorLabel.setStyle("-fx-text-fill: #1E6B35; -fx-font-weight: 800; -fx-background-color: #F4FAF5; -fx-border-color: #BDE4C7; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12;");
        errorLabel.setText("✔ Password reset successfully! You can now sign in with your new password.");
        Alerts.info("Your password has been updated in Supabase. Please sign in with your new credentials.");
    }

    private void promptMfaDialog(User user, String instructions) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("MFA Verification Required");
        dialog.setHeaderText("Two-Factor Identity Authorization");

        VBox content = new VBox(12);
        content.setPrefWidth(380);

        Label desc = new Label(instructions);
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #414141;");

        TextField mfaField = Ui.field("Enter 6-digit code");
        mfaField.setStyle("-fx-font-size: 18px; -fx-alignment: center; -fx-font-weight: bold; -fx-letter-spacing: 4px;");

        Label statusLbl = new Label("");
        statusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9E0B0F;");

        Button resendBtn = new Button("Resend Code");
        resendBtn.getStyleClass().add("secondary-button");
        resendBtn.setOnAction(e -> {
            OtpService.OtpResponse r = OtpService.requestMfaOtp(user);
            statusLbl.setText(r.message());
            statusLbl.setStyle(r.sent() ? "-fx-font-size: 11px; -fx-text-fill: #660508; -fx-font-weight: bold;" : "-fx-font-size: 11px; -fx-text-fill: #9E0B0F;");
        });

        HBox resendRow = new HBox(8, resendBtn);
        resendRow.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(desc, mfaField, statusLbl, resendRow);
        dialog.getDialogPane().setContent(content);

        ButtonType verifyBtnType = new ButtonType("Authorize & Sign In", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(verifyBtnType, ButtonType.CANCEL);

        dialog.setResultConverter(button -> button == verifyBtnType ? mfaField.getText() : null);

        dialog.showAndWait().ifPresent(code -> {
            try {
                User authed = authenticationService.completeMfaLogin(user, code);
                transitionToMain(authed);
            } catch (AppException ex) {
                errorLabel.setText(ex.getMessage());
            } catch (Exception ex) {
                log.error("Failed to complete MFA login", ex);
                errorLabel.setText("MFA authorization failed: " + ex.getMessage());
            }
        });
    }

    private void transitionToMain(User user) throws Exception {
        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/nextit/view/MainLayout.fxml"));
        javafx.scene.layout.BorderPane root = loader.load();
        AppController appController = loader.getController();
        Stage stage = (Stage) errorLabel.getScene().getWindow();
        javafx.scene.Scene scene = new javafx.scene.Scene(root, stage.getWidth(), stage.getHeight());
        scene.getStylesheets().add(getClass().getResource("/com/nextit/css/application.css").toExternalForm());
        com.nextit.util.WindowBoundsManager.installDesktopEventFilters(scene);
        stage.setScene(scene);
        appController.initialize(stage, user);
    }

    @FXML
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void handleOpenOnboarding() {
        try {
            Stage stage = (Stage) errorLabel.getScene().getWindow();
            OnboardingView.open(stage);
        } catch (Exception e) {
            log.error("Failed to open onboarding flow", e);
        }
    }
}
