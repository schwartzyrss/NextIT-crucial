package com.nextit.controller;

import com.nextit.exception.AppException;
import com.nextit.model.User;
import com.nextit.service.AuthenticationService;
import com.nextit.service.OtpService;
import com.nextit.util.Alerts;
import com.nextit.util.Ui;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private Button togglePasswordBtn;
    @FXML private SVGPath eyeIcon;
    @FXML private Label errorLabel;

    private boolean passwordVisible = false;

    private static final String EYE_OPEN_PATH = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String EYE_CLOSED_PATH = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.44-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z";

    private AuthenticationService authenticationService;

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
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
                eyeIcon.setFill(javafx.scene.paint.Color.web("#4F6B66"));
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
                eyeIcon.setFill(javafx.scene.paint.Color.web("#617D77"));
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
            errorLabel.setText("");
            if (authenticationService == null) {
                authenticationService = new AuthenticationService();
            }
            User user = authenticationService.login(usernameField.getText(), enteredPassword());
            transitionToMain(user);
        } catch (AuthenticationService.MfaRequiredException mfa) {
            promptMfaDialog(mfa.getUser(), mfa.getMessage());
        } catch (AppException e) {
            com.nextit.security.Session.logout();
            errorLabel.setText(e.getMessage());
        } catch (Exception e) {
            log.error("Login failed due to an unexpected error", e);
            com.nextit.security.Session.logout();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setContentText("Could not log in. Please check the database connection.");
            alert.show();
        }
    }

    private void promptMfaDialog(User user, String instructions) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("MFA Verification Required");
        dialog.setHeaderText("Two-Factor Identity Authorization");

        VBox content = new VBox(12);
        content.setPrefWidth(380);

        Label desc = new Label(instructions);
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #4F6B66;");

        TextField mfaField = Ui.field("Enter 6-digit code");
        mfaField.setStyle("-fx-font-size: 18px; -fx-alignment: center; -fx-font-weight: bold; -fx-letter-spacing: 4px;");

        Label statusLbl = new Label("");
        statusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #b3261e;");

        Button resendBtn = new Button("Resend Code");
        resendBtn.getStyleClass().add("secondary-button");
        resendBtn.setOnAction(e -> {
            OtpService.OtpResponse r = OtpService.requestMfaOtp(user);
            statusLbl.setText(r.message());
            statusLbl.setStyle(r.sent() ? "-fx-font-size: 11px; -fx-text-fill: #2d6a4f;" : "-fx-font-size: 11px; -fx-text-fill: #b3261e;");
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
