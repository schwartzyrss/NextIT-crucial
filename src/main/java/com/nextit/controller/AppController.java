package com.nextit.controller;

import com.nextit.model.User;
import com.nextit.security.Session;
import com.nextit.service.AuthenticationService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class AppController {

    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    @FXML private Label headerLabel;
    @FXML private Label userLabel;
    @FXML private BorderPane contentPane;
    @FXML private VBox sidebarContainer;
    @FXML private VBox sidebar;
    @FXML private ScrollPane sidebarScroll;
    @FXML private VBox sidebarFooter;
    @FXML private Button logoutButton;
    @FXML private Button toggleSidebarButton;

    private Stage stage;
    private Button activeNavButton;
    private static AppController instance;

    public static AppController getInstance() {
        return instance;
    }

    public void navigateTo(javafx.scene.Node view, String sidebarMatch) {
        if (view != null) {
            setView(view);
        }
        if (sidebarMatch != null) {
            updateWorkspaceTitle(sidebarMatch);
        }
        if (sidebar != null && sidebarMatch != null) {
            String target = sidebarMatch.replaceAll("[^a-zA-Z0-9 ]", "").trim().toLowerCase(Locale.ROOT);
            Button exactMatch = null;
            Button partialMatch = null;
            for (javafx.scene.Node n : sidebar.getChildren()) {
                if (n instanceof Button b) {
                    String clean = b.getText().replaceAll("[^a-zA-Z0-9 ]", "").trim().toLowerCase(Locale.ROOT);
                    if (clean.equals(target)) {
                        exactMatch = b;
                        break;
                    }
                    if (partialMatch == null && clean.contains(target)) {
                        partialMatch = b;
                    }
                }
            }
            Button bestMatch = exactMatch != null ? exactMatch : partialMatch;
            if (bestMatch != null) {
                setActiveButton(bestMatch);
            }
        }
    }

    public void setView(javafx.scene.Node view) {
        if (contentPane != null && view != null) {
            if (view instanceof ScrollPane || Boolean.TRUE.equals(view.getProperties().get("self-scrolling"))) {
                contentPane.setCenter(view);
            } else {
                ScrollPane sp = new ScrollPane(view);
                sp.setFitToWidth(true);
                sp.setFitToHeight(true);
                sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                sp.getStyleClass().add("content-scroll");
                contentPane.setCenter(sp);
            }
        }
    }

    public void updateWorkspaceTitle(String sectionName) {
        if (stage == null) return;
        User user = Session.currentUser();
        String roleName = (user != null && user.getRole() != null)
                ? switch (user.getRole()) {
                    case STUDENT -> "Student Portal";
                    case INSTRUCTOR -> "Instructor Workspace";
                    case EMPLOYER -> "Employer Hub";
                    case ADMIN -> "Administrator Console";
                }
                : "Portal";
        String clean = sectionName != null ? sectionName.replaceAll("[^a-zA-Z0-9 &/-]", "").trim() : null;
        com.nextit.util.WindowBoundsManager.updateTitle(stage, roleName, clean);
    }

    public void initialize(Stage stage, User user) {
        instance = this;
        this.stage = stage;
        updateWorkspaceTitle("Dashboard");
        String statusSuffix = user.isVerified() ? "" : " - " + user.getVerificationStatus().displayName();
        userLabel.setText(user.getFullName() + " (" + user.getRole() + statusSuffix + ")");
        if (toggleSidebarButton != null) {
            toggleSidebarButton.setTooltip(new Tooltip("Toggle Sidebar (Ctrl+B)"));
        }
        if (stage != null && stage.getScene() != null) {
            stage.getScene().getAccelerators().put(
                    new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN),
                    this::toggleSidebar);
        }
        if (stage != null) {
            stage.iconifiedProperty().addListener((obs, wasIconified, isIconified) -> {
                if (isIconified) {
                    com.nextit.service.SystemService.getInstance().stop();
                } else {
                    com.nextit.service.SystemService.getInstance().start();
                }
            });
            stage.setOnCloseRequest(e -> {
                e.consume();
                com.nextit.util.SplashScreenView.shutdownApplication(stage);
            });
        }
        com.nextit.service.SystemService.getInstance().start();

        // RBAC Enforcement: unverified accounts cannot use role workspaces
        if (user.isVerified()) {
            buildSidebar(user);
            showDashboard(user);
        } else {
            buildUnverifiedSidebar(user);
            showVerificationStatus(user);
        }
    }

    private void showVerificationStatus(User user) {
        setView(VerificationStatusView.create(stage, user, this::confirmAndLogout, verifiedUser -> {
            userLabel.setText(verifiedUser.getFullName() + " (" + verifiedUser.getRole() + ")");
            buildSidebar(verifiedUser);
            showDashboard(verifiedUser);
        }));
    }

    private void buildUnverifiedSidebar(User user) {
        sidebar.getChildren().clear();
        sidebar.getChildren().add(categoryHeader("Identity & Access"));
        Button statusBtn = navButton("🛡️ Verification Status", () -> showVerificationStatus(user));
        sidebar.getChildren().add(statusBtn);
        setActiveButton(statusBtn);

        if (logoutButton != null) {
            logoutButton.setOnAction(e -> runSafely(this::confirmAndLogout));
        } else if (sidebarFooter != null) {
            sidebarFooter.getChildren().clear();
            Button logout = new Button("🚪 Logout");
            logout.setMaxWidth(Double.MAX_VALUE);
            logout.getStyleClass().addAll("sidebar-button", "sidebar-logout-button");
            logout.setOnAction(e -> runSafely(this::confirmAndLogout));
            sidebarFooter.getChildren().add(logout);
        }
    }

    @FXML
    private void toggleSidebar() {
        javafx.scene.Node target = sidebarContainer != null ? sidebarContainer : sidebarScroll;
        if (target == null) return;
        boolean currentlyVisible = target.isVisible();
        target.setVisible(!currentlyVisible);
        target.setManaged(!currentlyVisible);
        if (toggleSidebarButton != null) {
            toggleSidebarButton.setText(currentlyVisible ? "☰" : "◀");
            toggleSidebarButton.setTooltip(new Tooltip(currentlyVisible ? "Show Sidebar (Ctrl+B)" : "Hide Sidebar (Ctrl+B)"));
        }
    }

    private void buildSidebar(User user) {
        sidebar.getChildren().clear();
        sidebar.getChildren().add(categoryHeader("Main Menu"));
        Button dashboard = navButton("📊 Dashboard", () -> showDashboard(user));
        sidebar.getChildren().add(dashboard);
        setActiveButton(dashboard);
        switch (user.getRole()) {
            case STUDENT -> {
                sidebar.getChildren().add(categoryHeader("Academic"));
                sidebar.getChildren().add(navButton("👤 Profile", () -> setView(StudentViews.profile())));
                sidebar.getChildren().add(navButton("📚 Academic", () -> setView(StudentViews.academic())));
                sidebar.getChildren().add(navButton("💻 Skills", () -> setView(StudentViews.skills())));
                sidebar.getChildren().add(navButton("🚀 Projects", () -> setView(StudentViews.projects())));
                sidebar.getChildren().add(navButton("🏆 Achievements & Certifications", () -> setView(StudentViews.achievementsAndCertifications())));
                sidebar.getChildren().add(navButton("📁 Portfolio", () -> setView(StudentViews.portfolio())));
                sidebar.getChildren().add(navButton("🌟 Opportunities", () -> setView(StudentViews.opportunities())));
                sidebar.getChildren().add(navButton("📝 Applications", () -> setView(StudentViews.applications())));
            }
            case INSTRUCTOR -> {
                sidebar.getChildren().add(categoryHeader("Academic Management"));
                sidebar.getChildren().add(navButton("📖 Subjects", () -> setView(InstructorViews.subjects())));
                sidebar.getChildren().add(navButton("👥 Students", () -> setView(InstructorViews.students())));
                sidebar.getChildren().add(navButton("📅 Attendance", () -> setView(InstructorViews.attendance())));
                sidebar.getChildren().add(navButton("📊 Grades", () -> setView(InstructorViews.grades())));

                sidebar.getChildren().add(categoryHeader("Evaluations & Skills"));
                sidebar.getChildren().add(navButton("💻 Technical Skills", () -> setView(InstructorViews.skills())));
                sidebar.getChildren().add(navButton("📋 Technical Evaluations", () -> setView(InstructorViews.technicalEvaluations())));
                sidebar.getChildren().add(navButton("📑 Project Evaluations", () -> setView(InstructorViews.evaluations())));

                sidebar.getChildren().add(categoryHeader("Student Showcase & Career"));
                sidebar.getChildren().add(navButton("📁 Portfolios", () -> setView(InstructorViews.portfolios())));
                sidebar.getChildren().add(navButton("🚀 Projects", () -> setView(InstructorViews.projects())));
                sidebar.getChildren().add(navButton("🏆 Achievements & Certifications", () -> setView(InstructorViews.achievementsAndCertifications())));

                sidebar.getChildren().add(categoryHeader("Support & Growth"));
                sidebar.getChildren().add(navButton("💡 Academic Support", () -> setView(InstructorViews.support())));
                sidebar.getChildren().add(navButton("🌟 Opportunities", () -> setView(InstructorViews.opportunities())));
                sidebar.getChildren().add(navButton("✍ Recommendation Letters", () -> setView(InstructorViews.recommendationLetters())));

                sidebar.getChildren().add(categoryHeader("Account"));
                sidebar.getChildren().add(navButton("👤 My Profile", () -> setView(InstructorViews.profile())));
            }
            case EMPLOYER -> {
                sidebar.getChildren().add(categoryHeader("Company"));
                sidebar.getChildren().add(navButton("🏢 Company Profile", () -> setView(EmployerViews.profile())));
                sidebar.getChildren().add(navButton("🌟 Opportunities", () -> setView(EmployerViews.opportunities())));
                sidebar.getChildren().add(navButton("📝 Applications", () -> setView(EmployerViews.applications())));
                sidebar.getChildren().add(categoryHeader("Talent Discovery"));
                sidebar.getChildren().add(navButton("✨ Recommended Students", () -> setView(EmployerViews.recommendedStudents())));
                sidebar.getChildren().add(navButton("🔍 Portfolio Search", () -> setView(EmployerViews.portfolioSearch())));
            }
            case ADMIN -> {
                sidebar.getChildren().add(categoryHeader("Verification Management"));
                sidebar.getChildren().add(navButton("🛡️ Verification Queue", () -> setView(AdminViews.verificationWorkspace())));
                sidebar.getChildren().add(categoryHeader("Administration"));
                sidebar.getChildren().add(navButton("👤 Admin Profile", () -> setView(AdminViews.profile())));
            }
        }

        // Pinned sticky logout button at the bottom of the sidebar
        if (logoutButton != null) {
            logoutButton.setOnAction(e -> runSafely(this::confirmAndLogout));
        } else if (sidebarFooter != null) {
            sidebarFooter.getChildren().clear();
            Button logout = new Button("🚪 Logout");
            logout.setMaxWidth(Double.MAX_VALUE);
            logout.getStyleClass().addAll("sidebar-button", "sidebar-logout-button");
            logout.setOnAction(e -> runSafely(this::confirmAndLogout));
            sidebarFooter.getChildren().add(logout);
        }
    }

    private Label categoryHeader(String text) {
        Label l = new Label(text.toUpperCase(Locale.ROOT));
        l.getStyleClass().add("sidebar-category-header");
        return l;
    }

    private Button navButton(String text, Runnable action) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("sidebar-button");
        b.setOnAction(e -> {
            setActiveButton(b);
            updateWorkspaceTitle(text);
            runSafely(action);
        });
        return b;
    }

    private void setActiveButton(Button b) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("sidebar-button-active");
        }
        activeNavButton = b;
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().add("sidebar-button-active");
        }
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Sidebar action failed", ex);
            String message = ex.getMessage();
            com.nextit.util.Alerts.error(message == null || message.isBlank()
                    ? "The action could not be completed." : message);
        }
    }

    private void showDashboard(User user) {
        updateWorkspaceTitle("Dashboard");
        switch (user.getRole()) {
            case STUDENT -> setView(StudentViews.dashboard());
            case INSTRUCTOR -> setView(InstructorViews.dashboard());
            case EMPLOYER -> setView(EmployerViews.dashboard());
            case ADMIN -> setView(AdminViews.dashboard());
        }
    }

    @FXML
    public void confirmAndLogout() {
        if (!com.nextit.util.Alerts.confirmLogout()) {
            return;
        }
        logout();
    }

    private void logout() {
        com.nextit.service.SystemService.getInstance().stop();
        Session.logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/nextit/view/Login.fxml"));
            double width = (stage != null && stage.getWidth() > 0) ? stage.getWidth() : com.nextit.util.WindowBoundsManager.MIN_WIDTH;
            double height = (stage != null && stage.getHeight() > 0) ? stage.getHeight() : com.nextit.util.WindowBoundsManager.MIN_HEIGHT;
            Scene scene = new Scene(loader.load(), width, height);
            scene.getStylesheets().add(getClass().getResource("/com/nextit/css/application.css").toExternalForm());
            com.nextit.util.WindowBoundsManager.installDesktopEventFilters(scene);
            LoginController controller = loader.getController();
            controller.setAuthenticationService(new AuthenticationService(new com.nextit.repository.JdbcUserRepository()));
            stage.setScene(scene);
            stage.setTitle(com.nextit.util.WindowBoundsManager.APP_TITLE);
        } catch (Exception ex) {
            log.error("Could not return to the login screen", ex);
            com.nextit.util.Alerts.error("Could not return to login.");
        }
    }
}
