package com.nextit.controller;

import com.nextit.exception.AppException;
import com.nextit.model.*;
import com.nextit.repository.*;
import com.nextit.security.Session;
import com.nextit.service.*;
import com.nextit.util.Alerts;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class OnboardingView {

    private static final Logger log = LoggerFactory.getLogger(OnboardingView.class);

    public static void open(Stage stage) {
        try {
            WebView webView = new WebView();
            WebEngine engine = webView.getEngine();
            engine.setJavaScriptEnabled(true);

            JavaBridge bridge = new JavaBridge(stage, engine);

            engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    try {
                        JSObject window = (JSObject) engine.executeScript("window");
                        window.setMember("javaBridge", bridge);
                    } catch (Exception e) {
                        log.error("Failed to inject JavaBridge into WebView", e);
                    }
                }
            });

            URL htmlUrl = OnboardingView.class.getResource("/com/nextit/view/onboarding.html");
            if (htmlUrl == null) {
                throw new AppException("Could not find onboarding.html resource");
            }
            engine.load(htmlUrl.toExternalForm());

            StackPane root = new StackPane(webView);
            webView.prefWidthProperty().bind(root.widthProperty());
            webView.prefHeightProperty().bind(root.heightProperty());

            Scene scene = new Scene(root, 1150, 750);
            stage.setTitle("NextIT: Account Onboarding & Role Selection");
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {
            log.error("Failed to open Onboarding View", e);
            Alerts.error("Could not load onboarding screen: " + e.getMessage());
        }
    }

    public static class JavaBridge {
        private final Stage stage;
        private final WebEngine engine;

        public JavaBridge(Stage stage, WebEngine engine) {
            this.stage = stage;
            this.engine = engine;
        }

        public String requestOtp(String email) {
            OtpService.OtpResponse resp = OtpService.requestOtp(email);
            String safeMsg = resp.message() != null ? resp.message().replace("\"", "\\\"").replace("\n", " ") : "";
            return String.format("{\"sent\":%b,\"configured\":%b,\"rateLimited\":%b,\"code\":\"%s\",\"message\":\"%s\"}",
                    resp.sent(), resp.configured(), resp.rateLimited(), resp.code(), safeMsg);
        }

        public boolean verifyOtp(String email, String enteredCode) {
            return OtpService.verifyOtp(email, enteredCode);
        }

        public String getSmtpConfig() {
            String host = EmailService.getSetting("SMTP_HOST", "smtp.gmail.com");
            String port = EmailService.getSetting("SMTP_PORT", "587");
            String user = EmailService.getSetting("SMTP_USER", "");
            boolean configured = EmailService.isConfigured();
            return String.format("{\"host\":\"%s\",\"port\":\"%s\",\"user\":\"%s\",\"configured\":%b}",
                    host, port, user, configured);
        }

        public String resetSmtpSettings() {
            try {
                EmailService.clearSmtpConfig();
                return "{\"success\":true,\"message\":\"Switched to Sandbox Mode. Verification code is 123456.\"}";
            } catch (Exception e) {
                return String.format("{\"success\":false,\"message\":\"%s\"}", e.getMessage());
            }
        }

        public String saveSmtpSettings(String host, String port, String user, String pass, String from, String emailToTest) {
            try {
                EmailService.updateAndPersistConfig(host, port, user, pass, from);
                String targetEmail = (emailToTest != null && !emailToTest.isBlank()) ? emailToTest.trim() : user.trim();
                OtpService.OtpResponse resp = OtpService.requestOtp(targetEmail);
                if (resp.sent()) {
                    return String.format("{\"success\":true,\"saved\":true,\"message\":\"SMTP configured & verification email sent to %s!\",\"code\":\"%s\"}",
                            targetEmail, resp.code());
                } else {
                    String safeErr = (resp.message() != null ? resp.message() : "Failed to deliver email").replace("\"", "\\\"").replace("\n", " ");
                    return String.format("{\"success\":false,\"saved\":true,\"message\":\"%s\",\"code\":\"%s\"}", safeErr, resp.code());
                }
            } catch (Exception e) {
                String safeErr = (e.getMessage() != null ? e.getMessage() : e.toString()).replace("\"", "\\\"").replace("\n", " ");
                return String.format("{\"success\":false,\"saved\":false,\"message\":\"%s\"}", safeErr);
            }
        }

        public void registerAndLaunch(String roleStr, String username, String firstName, String lastName,
                                      String email, String password, String detail1, String detail2, String detail3) {
            CompletableFuture.runAsync(() -> {
                try {
                    Role role;
                    try {
                        role = Role.valueOf(roleStr.trim().toUpperCase());
                    } catch (Exception ex) {
                        throw new AppException("Invalid registration role. Supported roles are Student, Instructor, and Employer.");
                    }

                    if (email == null || email.isBlank() || !email.contains("@")) {
                        throw new AppException("A valid email address is required.");
                    }
                    if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
                        throw new AppException("First name and last name are required.");
                    }
                    if (password == null || password.length() < 6) {
                        throw new AppException("Password must be at least 6 characters.");
                    }

                    String cleanEmail = email.trim().toLowerCase();
                    String uName = (username != null && !username.isBlank())
                            ? username.trim()
                            : cleanEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");

                    JdbcUserRepository userRepo = new JdbcUserRepository();
                    JdbcVerificationRequestRepository reqRepo = new JdbcVerificationRequestRepository();

                    // Prevent duplicate accounts with same email or username
                    Optional<User> byEmail = userRepo.findByEmail(cleanEmail);
                    if (byEmail.isPresent()) {
                        throw new AppException("An account with the email " + cleanEmail + " is already registered. Please sign in directly.");
                    }
                    Optional<User> byUsername = userRepo.findByUsername(uName);
                    if (byUsername.isPresent()) {
                        throw new AppException("The username @" + uName + " is already taken. Please choose another username.");
                    }

                    // Run Anti-Fake Identity Protection Engine
                    AntiFakeIdentityService antiFake = new AntiFakeIdentityService();
                    AntiFakeIdentityService.AnalysisResult analysis;
                    if (role == Role.STUDENT) {
                        if (detail3 == null || detail3.isBlank()) {
                            throw new AppException("Student ID number is required for institutional student verification.");
                        }
                        analysis = antiFake.analyzeStudent(detail3, cleanEmail, firstName, lastName, detail1);
                    } else if (role == Role.INSTRUCTOR) {
                        if (detail3 == null || detail3.isBlank()) {
                            throw new AppException("Faculty employee identification number is required.");
                        }
                        analysis = antiFake.analyzeInstructor(detail3, cleanEmail, firstName, lastName, detail1);
                    } else if (role == Role.EMPLOYER) {
                        if (detail1 == null || detail1.isBlank()) {
                            throw new AppException("Legitimate business or organization name is required.");
                        }
                        analysis = antiFake.analyzeEmployer(detail1, detail3, cleanEmail, firstName + " " + lastName);
                    } else {
                        throw new AppException("Unsupported role selection.");
                    }

                    User user = new User();
                    user.setUsername(uName);
                    user.setPasswordHash(AuthenticationService.hashPassword(password));
                    user.setEmail(cleanEmail);
                    user.setRole(role);
                    user.setFirstName(firstName.trim());
                    user.setLastName(lastName.trim());
                    user.setVerificationStatus(VerificationStatus.VERIFIED);
                    userRepo.save(user);

                    // Save role-specific profiles
                    if (role == Role.STUDENT) {
                        JdbcStudentRepository studentRepo = new JdbcStudentRepository();
                        Student s = new Student();
                        s.setUserId(user.getUserId());
                        s.setStudentNumber(detail3.trim());
                        s.setProgram((detail1 != null && !detail1.isBlank()) ? detail1.trim() : "BS Information Technology");
                        int yr = 1;
                        try {
                            if (detail2 != null && !detail2.isBlank()) {
                                yr = Integer.parseInt(detail2.replaceAll("\\D", ""));
                            }
                        } catch (Exception ignored) {}
                        s.setYearLevel(yr > 0 ? yr : 1);
                        s.setSection("A");
                        s.setEnrollmentStatus("Enrolled");
                        studentRepo.save(s);
                    } else if (role == Role.INSTRUCTOR) {
                        JdbcInstructorRepository instRepo = new JdbcInstructorRepository();
                        Instructor inst = new Instructor();
                        inst.setUserId(user.getUserId());
                        inst.setEmployeeNumber(detail3.trim());
                        inst.setDepartment((detail1 != null && !detail1.isBlank()) ? detail1.trim() : "Computer Studies");
                        inst.setSpecialization((detail2 != null && !detail2.isBlank()) ? detail2.trim() : "Information Technology");
                        instRepo.save(inst);
                    } else if (role == Role.EMPLOYER) {
                        JdbcEmployerRepository empRepo = new JdbcEmployerRepository();
                        Employer emp = new Employer();
                        emp.setUserId(user.getUserId());
                        emp.setCompanyName(detail1.trim());
                        emp.setIndustry((detail2 != null && !detail2.isBlank()) ? detail2.trim() : "Technology");
                        emp.setContactPerson(user.getFullName());
                        emp.setContactNumber((detail3 != null && !detail3.isBlank()) ? detail3.trim() : "");
                        emp.setAddress("Technology Campus");
                        empRepo.save(emp);
                    }

                    // Record Verification Request with Anti-Fake Identity Metrics
                    VerificationRequest req = new VerificationRequest();
                    req.setUserId(user.getUserId());
                    req.setRole(role);
                    req.setIdNumber(detail3 != null ? detail3.trim() : null);
                    req.setInstitutionalEmail(cleanEmail);
                    req.setOrganizationName(detail1 != null ? detail1.trim() : null);
                    req.setBusinessRegistrationNumber(role == Role.EMPLOYER && detail3 != null ? detail3.trim() : null);
                    req.setDocumentReference("Onboarding Self-Registration Submission");
                    req.setSubmissionNotes(analysis.reviewerNotes());
                    req.setStatus(VerificationStatus.VERIFIED);
                    req.setRiskScore(analysis.riskScore());
                    req.setRiskFlags(String.join(",", analysis.riskFlags()));
                    reqRepo.save(req);

                    // Audit Log
                    SecurityAuditService.getInstance().logRegistrationSubmitted(
                            user, analysis.status(), analysis.riskScore(), String.join(",", analysis.riskFlags()));

                    // Establish session
                    Session.login(user);

                    // Update UI on JavaFX thread
                    Platform.runLater(() -> {
                        try {
                            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nextit/view/MainLayout.fxml"));
                            BorderPane root = loader.load();
                            AppController appController = loader.getController();
                            Scene scene = new Scene(root, 1100, 700);
                            scene.getStylesheets().add(getClass().getResource("/com/nextit/css/application.css").toExternalForm());
                            stage.setTitle("NextIT: Track Your Progress, Build Your Future");
                            stage.setScene(scene);
                            stage.centerOnScreen();
                            appController.initialize(stage, user);
                        } catch (Exception e) {
                            log.error("Failed to load main layout view", e);
                            Alerts.error("Could not load dashboard: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to complete registration and launch dashboard", e);
                    Platform.runLater(() -> Alerts.error("Registration failed: " + e.getMessage()));
                }
            });
        }

        public void backToLogin() {
            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nextit/view/Login.fxml"));
                    Parent root = loader.load();
                    LoginController controller = loader.getController();
                    controller.setAuthenticationService(new AuthenticationService(new JdbcUserRepository()));
                    Scene scene = new Scene(root, 900, 560);
                    scene.getStylesheets().add(getClass().getResource("/com/nextit/css/application.css").toExternalForm());
                    stage.setTitle("NextIT: Track Your Progress, Build Your Future");
                    stage.setScene(scene);
                    stage.centerOnScreen();
                } catch (Exception e) {
                    log.error("Failed to return to login", e);
                }
            });
        }
    }
}
