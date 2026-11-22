package com.nextit.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * NextIT standardized confirmation and information dialogs.
 * Enforces the 60-30-10 visual palette (Ivory/Beige, Navy, Crimson, Gold),
 * circular NextIT logo integration, high-contrast typography, and 100%
 * non-truncated text formatting with automated wrapping.
 */
public final class NextItDialog {

    private NextItDialog() {}

    public static boolean showConfirm(String message) {
        return showConfirm("Confirm Action", null, message);
    }

    public static boolean showConfirm(String title, String message) {
        return showConfirm(title, null, message);
    }

    public static boolean showConfirm(String title, String header, String message) {
        return runOnFx(() -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(title != null ? title : "Confirm Action");
            dialog.setHeaderText(null);

            DialogPane pane = dialog.getDialogPane();
            pane.getStylesheets().add(NextItDialog.class.getResource("/com/nextit/css/application.css").toExternalForm());
            pane.setStyle("-fx-background-color: #FAF7F2;");

            try {
                dialog.setOnShowing(ev -> {
                    if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                        WindowBoundsManager.loadStageIcons(stage);
                    }
                });
            } catch (Exception ignored) {}

            VBox root = new VBox(14);
            root.setPrefWidth(460);
            root.setMinWidth(420);
            root.setPadding(new Insets(18, 20, 18, 20));

            // Header Banner
            HBox headerBox = new HBox(12);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            headerBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EFE2CA; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 12 16; -fx-effect: dropshadow(gaussian, rgba(26, 38, 52, 0.08), 6, 0.05, 0, 2);");

            ImageView logo = Ui.logo(36);
            VBox titlesBox = new VBox(2);
            Label titleLbl = new Label(title != null ? title : "Confirm Action");
            titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: 900; -fx-text-fill: #1A2634;");
            titleLbl.setWrapText(true);
            titleLbl.setMinHeight(Region.USE_PREF_SIZE);

            String subText = header != null && !header.isBlank() ? header : "NextIT Verification & Authorization";
            Label subLbl = new Label(subText);
            subLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #4A5A6A; -fx-font-weight: 600;");
            subLbl.setWrapText(true);
            subLbl.setMinHeight(Region.USE_PREF_SIZE);

            titlesBox.getChildren().addAll(titleLbl, subLbl);
            headerBox.getChildren().addAll(logo, titlesBox);

            // Message Body (strictly wrapText, no truncation)
            VBox msgCard = new VBox(8);
            msgCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: rgba(74, 90, 106, 0.15); -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 14;");

            Label msgLbl = new Label(message != null ? message : "");
            msgLbl.setWrapText(true);
            msgLbl.setMinHeight(Region.USE_PREF_SIZE);
            msgLbl.setPrefWidth(420);
            msgLbl.setMaxWidth(Double.MAX_VALUE);
            msgLbl.setTextOverrun(OverrunStyle.CLIP);
            msgLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #1A2634; -fx-line-spacing: 2px; -fx-font-weight: 500;");

            msgCard.getChildren().add(msgLbl);
            root.getChildren().addAll(headerBox, msgCard);
            pane.setContent(root);

            ButtonType okType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            pane.getButtonTypes().addAll(okType, cancelType);

            Button okBtn = (Button) pane.lookupButton(okType);
            if (okBtn != null) {
                okBtn.getStyleClass().addAll("dialog-btn", "primary-button");
                okBtn.setStyle("-fx-background-color: #9E0B0F; -fx-text-fill: #FAF7F2; -fx-font-weight: 800; -fx-padding: 8 20;");
            }
            Button cancelBtn = (Button) pane.lookupButton(cancelType);
            if (cancelBtn != null) {
                cancelBtn.getStyleClass().addAll("dialog-btn", "secondary-button");
                cancelBtn.setStyle("-fx-background-color: #FAF7F2; -fx-text-fill: #1A2634; -fx-font-weight: 700; -fx-border-color: rgba(74, 90, 106, 0.35); -fx-padding: 8 18;");
            }

            Optional<ButtonType> res = dialog.showAndWait();
            return res.isPresent() && (res.get() == okType || res.get() == ButtonType.OK || res.get() == ButtonType.YES);
        }, false);
    }

    public static void showInfo(String title, String message) {
        showFeedbackDialog(title != null ? title : "NextIT Information", message, Alert.AlertType.INFORMATION);
    }

    public static void showError(String title, String message) {
        showFeedbackDialog(title != null ? title : "Error Encountered", message, Alert.AlertType.ERROR);
    }

    public static void showWarning(String title, String message) {
        showFeedbackDialog(title != null ? title : "NextIT Warning", message, Alert.AlertType.WARNING);
    }

    private static void showFeedbackDialog(String title, String message, Alert.AlertType alertType) {
        runOnFx(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);

            DialogPane pane = alert.getDialogPane();
            pane.getStylesheets().add(NextItDialog.class.getResource("/com/nextit/css/application.css").toExternalForm());
            pane.setStyle("-fx-background-color: #FAF7F2;");

            try {
                alert.setOnShowing(ev -> {
                    if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                        WindowBoundsManager.loadStageIcons(stage);
                    }
                });
            } catch (Exception ignored) {}

            VBox root = new VBox(12);
            root.setPrefWidth(440);
            root.setMinWidth(380);
            root.setPadding(new Insets(16, 18, 16, 18));

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);
            ImageView logo = Ui.logo(32);
            Label titleLbl = new Label(title);
            String titleColor = alertType == Alert.AlertType.ERROR ? "#9E0B0F" : "#1A2634";
            titleLbl.setStyle("-fx-font-size: 14.5px; -fx-font-weight: 800; -fx-text-fill: " + titleColor + ";");
            titleLbl.setWrapText(true);
            titleLbl.setMinHeight(Region.USE_PREF_SIZE);
            header.getChildren().addAll(logo, titleLbl);

            Label contentLbl = new Label(message != null ? message : "");
            contentLbl.setWrapText(true);
            contentLbl.setMinHeight(Region.USE_PREF_SIZE);
            contentLbl.setMaxWidth(Double.MAX_VALUE);
            contentLbl.setTextOverrun(OverrunStyle.CLIP);
            contentLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1A2634; -fx-line-spacing: 2px;");

            root.getChildren().addAll(header, contentLbl);
            pane.setContent(root);

            Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
            if (okBtn != null) {
                okBtn.getStyleClass().addAll("dialog-btn", "primary-button");
                okBtn.setStyle("-fx-background-color: #9E0B0F; -fx-text-fill: #FAF7F2; -fx-font-weight: 800;");
            }

            alert.showAndWait();
            return null;
        }, null);
    }

    private static <T> T runOnFx(Callable<T> callable, T fallback) {
        if (Platform.isFxApplicationThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                return fallback;
            }
        }
        FutureTask<T> task = new FutureTask<>(callable);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (Exception e) {
            return fallback;
        }
    }
}
