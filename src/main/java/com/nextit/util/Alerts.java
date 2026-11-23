package com.nextit.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Optional;

public final class Alerts {

    private Alerts() {}

    private static void style(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(Alerts.class.getResource("/com/nextit/css/application.css").toExternalForm());
        pane.getStyleClass().add("clay-dialog-pane");
        try {
            javafx.stage.Window window = pane.getScene() != null ? pane.getScene().getWindow() : null;
            if (window instanceof javafx.stage.Stage stage) {
                WindowBoundsManager.loadStageIcons(stage);
            } else {
                dialog.setOnShowing(ev -> {
                    if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() instanceof javafx.stage.Stage stage) {
                        WindowBoundsManager.loadStageIcons(stage);
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    public static void error(String message) {
        NextItDialog.showError("Error", message);
    }

    public static void warn(String message) {
        NextItDialog.showWarning("NextIT Warning", message);
    }

    public static void info(String message) {
        NextItDialog.showInfo("NextIT", message);
    }

    public static boolean confirm(String message) {
        return NextItDialog.showConfirm("Confirm Action", message);
    }

    public static boolean confirmLogout() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Confirm Logout");
        dialog.setHeaderText(null);

        javafx.stage.Window owner = NextItDialog.getTopLevelOwner();
        if (owner != null && owner.isShowing()) {
            dialog.initOwner(owner);
        }

        Node rootNode = (owner != null && owner.getScene() != null) ? owner.getScene().getRoot() : null;
        Effect originalEffect = rootNode != null ? rootNode.getEffect() : null;
        if (rootNode != null) {
            ColorAdjust dim = new ColorAdjust();
            dim.setBrightness(-0.10);
            rootNode.setEffect(dim);
        }

        try {
            VBox content = new VBox(16);
            content.getStyleClass().add("logout-dialog-content");
            content.setPrefWidth(440);
            content.setMaxWidth(440);

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("logout-header-cushion");
            header.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 12; -fx-padding: 16 20;");

            StackPane iconPane = new StackPane();
            iconPane.getStyleClass().add("logout-icon-pane");
            iconPane.setPrefSize(42, 42);
            iconPane.setMinSize(42, 42);
            iconPane.setMaxSize(42, 42);
            iconPane.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 999; -fx-border-color: rgba(255, 255, 255, 0.25); -fx-border-radius: 999; -fx-border-width: 1;");

            Label icon = new Label("🚪");
            icon.getStyleClass().add("logout-icon");
            icon.setStyle("-fx-font-size: 20px;");
            iconPane.getChildren().add(icon);

            VBox textBox = new VBox(2);
            Label title = new Label("Log Out of NextIT?");
            title.getStyleClass().add("logout-title");
            title.setStyle("-fx-font-size: 16.5px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");

            Label subtitle = new Label("End your current session");
            subtitle.getStyleClass().add("logout-subtitle");
            subtitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: #CBD5E1;");

            textBox.getChildren().addAll(title, subtitle);
            header.getChildren().addAll(iconPane, textBox);

            Label message = new Label("Are you sure you want to log out? You will be returned to the login screen.");
            message.setWrapText(true);
            message.setPrefWidth(400);
            message.setMaxWidth(400);
            message.setMinHeight(Region.USE_PREF_SIZE);
            message.setTextOverrun(OverrunStyle.CLIP);
            message.getStyleClass().add("logout-message");
            message.setStyle("-fx-font-size: 13.5px; -fx-text-fill: #334155; -fx-line-spacing: 3px; -fx-padding: 6 2;");

            content.getChildren().addAll(header, message);

            DialogPane pane = dialog.getDialogPane();
            pane.setContent(content);
            style(dialog);
            pane.setStyle(
                    "-fx-background-color: #FAF8F5; " +
                    "-fx-background-radius: 16; " +
                    "-fx-border-color: rgba(74, 90, 106, 0.20); " +
                    "-fx-border-radius: 16; " +
                    "-fx-border-width: 1; " +
                    "-fx-padding: 18 22 14 22; " +
                    "-fx-effect: dropshadow(gaussian, rgba(26, 38, 52, 0.12), 18, 0.08, 0, 4);"
            );

            ButtonType logoutButtonType = new ButtonType("Logout", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            pane.getButtonTypes().addAll(logoutButtonType, cancelButtonType);

            Button logoutBtn = (Button) pane.lookupButton(logoutButtonType);
            if (logoutBtn != null) {
                logoutBtn.setText("Logout");
                logoutBtn.setMinWidth(110);
                logoutBtn.setDefaultButton(true);
                ButtonBar.setButtonUniformSize(logoutBtn, false);
                logoutBtn.getStyleClass().addAll("dialog-btn", "logout-confirm-btn");
                logoutBtn.setStyle("-fx-background-color: #991B1B; -fx-background-radius: 8; -fx-text-fill: #FFFFFF; -fx-font-weight: 800; -fx-font-size: 13px; -fx-padding: 8 22; -fx-cursor: hand;");
                logoutBtn.setOnMouseEntered(e -> logoutBtn.setStyle("-fx-background-color: #7F1D1D; -fx-background-radius: 8; -fx-text-fill: #FFFFFF; -fx-font-weight: 800; -fx-font-size: 13px; -fx-padding: 8 22; -fx-cursor: hand;"));
                logoutBtn.setOnMouseExited(e -> logoutBtn.setStyle("-fx-background-color: #991B1B; -fx-background-radius: 8; -fx-text-fill: #FFFFFF; -fx-font-weight: 800; -fx-font-size: 13px; -fx-padding: 8 22; -fx-cursor: hand;"));
            }

            Button cancelBtn = (Button) pane.lookupButton(cancelButtonType);
            if (cancelBtn != null) {
                cancelBtn.setText("Cancel");
                cancelBtn.setMinWidth(100);
                cancelBtn.setCancelButton(true);
                ButtonBar.setButtonUniformSize(cancelBtn, false);
                cancelBtn.getStyleClass().addAll("dialog-btn", "logout-cancel-btn");
                cancelBtn.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 8; -fx-border-color: #D1D5DB; -fx-border-radius: 8; -fx-border-width: 1; -fx-text-fill: #1E293B; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand;");
                cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 8; -fx-border-color: #94A3B8; -fx-border-radius: 8; -fx-border-width: 1; -fx-text-fill: #1E293B; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand;"));
                cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 8; -fx-border-color: #D1D5DB; -fx-border-radius: 8; -fx-border-width: 1; -fx-text-fill: #1E293B; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand;"));
            }

            pane.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == KeyCode.ESCAPE) {
                    dialog.setResult(cancelButtonType);
                    dialog.close();
                    ev.consume();
                } else if (ev.getCode() == KeyCode.ENTER) {
                    dialog.setResult(logoutButtonType);
                    dialog.close();
                    ev.consume();
                }
            });

            Optional<ButtonType> result = dialog.showAndWait();
            return result.isPresent() && result.get() == logoutButtonType;
        } finally {
            if (rootNode != null) {
                rootNode.setEffect(originalEffect);
            }
        }
    }
}
