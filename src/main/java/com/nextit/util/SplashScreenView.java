package com.nextit.util;

import com.nextit.config.DatabaseConfig;
import com.nextit.config.EmbeddedDatabaseManager;
import com.nextit.controller.LoginController;
import com.nextit.repository.JdbcUserRepository;
import com.nextit.service.AuthenticationService;
import com.nextit.service.SystemService;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;

/**
 * Branded NextIT desktop splash screen with background database health validation
 * and smooth transition to the main login/dashboard experience.
 */
public final class SplashScreenView {

    private static final Logger log = LoggerFactory.getLogger(SplashScreenView.class);

    private SplashScreenView() {}

    public static void showAndInitialize(Stage stage) {
        WindowBoundsManager.configureStage(stage);

        // Build Defense-Themed Splash Card (Widescreen Desktop Showcase Container)
        VBox card = new VBox(22);
        card.setAlignment(Pos.CENTER);
        card.setMinWidth(640);
        card.setPrefWidth(980);
        card.setMaxWidth(1040);
        card.setMinHeight(520);
        card.setPrefHeight(620);
        card.setMaxHeight(620);
        card.setStyle(
                "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #FFFFFF 0%, #FDFBFA 55%, #EFE2CA 100%);"
                        + "-fx-background-radius: 28;"
                        + "-fx-border-color: #C6A75E;"
                        + "-fx-border-radius: 28;"
                        + "-fx-border-width: 1.5;"
                        + "-fx-padding: 48 64;"
                        + "-fx-effect: dropshadow(gaussian, rgba(65, 65, 65, 0.12), 32, 0.14, 0, 10);"
        );

        // Official NextIT circular emblem logo centered above title and progress bar
        ImageView emblemLogo = new ImageView();
        emblemLogo.setFitWidth(124);
        emblemLogo.setFitHeight(124);
        emblemLogo.setPreserveRatio(true);
        emblemLogo.setSmooth(true);
        try (InputStream is = SplashScreenView.class.getResourceAsStream("/images/nextit-logo.png")) {
            if (is != null) {
                emblemLogo.setImage(new Image(is));
            } else {
                try (InputStream fallback = SplashScreenView.class.getResourceAsStream("/com/nextit/icons/nextit-logo.png")) {
                    if (fallback != null) emblemLogo.setImage(new Image(fallback));
                }
            }
        } catch (Exception e) {
            log.warn("Could not load splash emblem logo: {}", e.getMessage());
        }

        StackPane emblemContainer = new StackPane(emblemLogo);
        emblemContainer.setAlignment(Pos.CENTER);
        emblemContainer.setStyle("-fx-effect: dropshadow(gaussian, rgba(158, 11, 15, 0.28), 24, 0.18, 0, 6);");

        Label titleLabel = new Label("NextIT");
        titleLabel.setStyle(
                "-fx-font-size: 38px;"
                        + "-fx-font-weight: 900;"
                        + "-fx-text-fill: #414141;"
                        + "-fx-letter-spacing: -0.5px;"
                        + "-fx-font-family: 'Segoe UI', -apple-system, sans-serif;"
        );

        Label subtitleLabel = new Label("Track Your Progress, Build Your Future");
        subtitleLabel.setStyle(
                "-fx-font-size: 14px;"
                        + "-fx-font-weight: 800;"
                        + "-fx-text-fill: #9E0B0F;"
                        + "-fx-letter-spacing: 0.5px;"
                        + "-fx-font-family: 'Segoe UI', -apple-system, sans-serif;"
        );

        VBox brandBox = new VBox(6, emblemContainer, titleLabel, subtitleLabel);
        brandBox.setAlignment(Pos.CENTER);

        // Full-width scaled horizontal progress bar
        ProgressBar progressBar = new ProgressBar(0.0);
        progressBar.setPrefWidth(540);
        progressBar.setMaxWidth(620);
        progressBar.setPrefHeight(8);
        progressBar.setStyle("-fx-accent: #9E0B0F;");

        // Live status line
        Label statusLabel = new Label("Connecting to database and validating schema...");
        statusLabel.setStyle(
                "-fx-font-size: 13px;"
                        + "-fx-font-weight: 700;"
                        + "-fx-text-fill: #414141;"
                        + "-fx-font-family: 'Segoe UI', -apple-system, sans-serif;"
        );

        // Trust chip pill badge with crimson status indicator
        HBox trustChip = new HBox(8);
        trustChip.setAlignment(Pos.CENTER);
        trustChip.setStyle(
                "-fx-background-color: #FAF4EE;"
                        + "-fx-background-radius: 999;"
                        + "-fx-border-color: #C6A75E;"
                        + "-fx-border-radius: 999;"
                        + "-fx-border-width: 1.2;"
                        + "-fx-padding: 6 18;"
                        + "-fx-effect: dropshadow(gaussian, rgba(198, 167, 94, 0.20), 8, 0.08, 0, 2);"
        );
        Region trustDot = new Region();
        trustDot.setStyle(
                "-fx-background-color: #9E0B0F;"
                        + "-fx-background-radius: 999;"
                        + "-fx-min-width: 8; -fx-min-height: 8; -fx-max-width: 8; -fx-max-height: 8;"
                        + "-fx-effect: dropshadow(gaussian, rgba(158, 11, 15, 0.6), 4, 0.25, 0, 0);"
        );
        Label trustText = new Label("Desktop Client • Standalone Edition");
        trustText.setStyle(
                "-fx-font-size: 11.5px;"
                        + "-fx-font-weight: 800;"
                        + "-fx-text-fill: #414141;"
                        + "-fx-font-family: 'Segoe UI', -apple-system, sans-serif;"
        );
        trustChip.getChildren().addAll(trustDot, trustText);

        VBox loadingStack = new VBox(18);
        loadingStack.setAlignment(Pos.CENTER);
        loadingStack.getChildren().addAll(brandBox, progressBar, statusLabel, trustChip);

        card.getChildren().add(loadingStack);

        StackPane root = new StackPane(card);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(28, 28, 28, 28));
        root.setStyle("-fx-background-color: #FDFBFA;");

        Scene splashScene = new Scene(root, stage.getWidth(), stage.getHeight());
        splashScene.getStylesheets().add(SplashScreenView.class.getResource("/com/nextit/css/application.css").toExternalForm());
        WindowBoundsManager.installDesktopEventFilters(splashScene);

        stage.setScene(splashScene);
        stage.show();

        // Intercept close event for clean background shutdown
        stage.setOnCloseRequest(e -> {
            e.consume();
            shutdownApplication(stage);
        });

        // Run background initialization with smooth telemetry
        Task<Boolean> initTask = new Task<>() {
            @Override
            protected Boolean call() {
                try {
                    updateProgress(0.15, 1.0);
                    updateMessage("Loading application settings and environment...");
                    Thread.sleep(400);

                    updateProgress(0.50, 1.0);
                    updateMessage("Connecting to database and validating schema...");
                    boolean dbOk = false;
                    try (Connection conn = DatabaseConfig.getConnection()) {
                        dbOk = (conn != null && conn.isValid(2));
                    } catch (Exception e) {
                        log.warn("Direct connection check failed, trying embedded manager: {}", e.getMessage());
                        try {
                            EmbeddedDatabaseManager.ensureStarted();
                            try (Connection conn = DatabaseConfig.getConnection()) {
                                dbOk = (conn != null && conn.isValid(2));
                            }
                        } catch (Exception ex) {
                            log.error("Failed to start fallback database: {}", ex.getMessage());
                        }
                    }

                    updateProgress(0.85, 1.0);
                    updateMessage(dbOk ? "Database connected! Launching NextIT..." : "Offline mode ready. Launching NextIT...");
                    Thread.sleep(350);

                    updateProgress(1.0, 1.0);
                    Thread.sleep(150);
                    return dbOk;
                } catch (Exception e) {
                    log.error("Error during splash initialization", e);
                    return false;
                }
            }
        };

        statusLabel.textProperty().bind(initTask.messageProperty());
        progressBar.progressProperty().bind(initTask.progressProperty());

        initTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                FadeTransition fade = new FadeTransition(Duration.millis(350), card);
                fade.setFromValue(1.0);
                fade.setToValue(0.0);
                fade.setOnFinished(ev -> transitionToLogin(stage));
                fade.play();
            });
        });

        initTask.setOnFailed(e -> {
            Platform.runLater(() -> transitionToLogin(stage));
        });

        Thread thread = new Thread(initTask, "nextit-splash-init");
        thread.setDaemon(true);
        thread.start();
    }

    private static void transitionToLogin(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(SplashScreenView.class.getResource("/com/nextit/view/Login.fxml"));
            Parent loginRoot = loader.load();
            LoginController controller = loader.getController();
            controller.setAuthenticationService(new AuthenticationService(new JdbcUserRepository()));

            Scene loginScene = new Scene(loginRoot, stage.getWidth(), stage.getHeight());
            loginScene.getStylesheets().add(SplashScreenView.class.getResource("/com/nextit/css/application.css").toExternalForm());
            WindowBoundsManager.installDesktopEventFilters(loginScene);

            loginRoot.setOpacity(0.0);
            stage.setScene(loginScene);

            // Ensure window close on the Login scene triggers clean application shutdown
            stage.setOnCloseRequest(e -> {
                e.consume();
                shutdownApplication(stage);
            });

            FadeTransition fadeIn = new FadeTransition(Duration.millis(350), loginRoot);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();

            log.info("Transitioned from splash screen to main Login scene.");
        } catch (Exception e) {
            log.error("Failed to load Login scene", e);
        }
    }

    public static void shutdownApplication(Stage stage) {
        log.info("Gracefully terminating NextIT background services and closing resources...");
        try {
            WindowBoundsManager.saveBounds(stage);
        } catch (Exception ignored) {}

        try {
            SystemService.getInstance().stop();
        } catch (Exception ignored) {}

        try {
            EmbeddedDatabaseManager.stop();
        } catch (Exception ignored) {}

        Platform.exit();
        System.exit(0);
    }
}
