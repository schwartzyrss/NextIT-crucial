package com.nextit.util;

import com.nextit.controller.AppController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

/**
 * Clean lightbox / modal dialog for viewing high-resolution certificate proof images.
 */
public final class CertificateImageViewer {

    private CertificateImageViewer() {}

    public static void show(String imagePathOrUrl, String title, String organization) {
        if (imagePathOrUrl == null || imagePathOrUrl.isBlank()) {
            Alerts.info("No certificate image attached for this record.");
            return;
        }

        File file = CertificateStorageService.resolveImageFile(imagePathOrUrl);
        Image image = null;
        try {
            if (file != null && file.exists()) {
                image = new Image(file.toURI().toString());
            } else if (imagePathOrUrl.startsWith("http://") || imagePathOrUrl.startsWith("https://")) {
                image = new Image(imagePathOrUrl, true);
            }
        } catch (Exception ex) {
            Alerts.error("Failed to load certificate image: " + ex.getMessage());
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Certificate Proof - " + (title != null ? title : ""));
        dialog.setHeaderText(null);

        Window owner = AppController.getInstance() != null ? AppController.getInstance().getStage() : null;
        if (owner == null || !owner.isShowing()) {
            owner = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null);
        }
        if (owner != null && owner.isShowing()) {
            dialog.initOwner(owner);
        }

        VBox content = new VBox(14);
        content.setStyle("-fx-background-color: #FAF8F5; -fx-padding: 20 22;");
        content.setPrefWidth(720);

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12 16;");

        VBox headerText = new VBox(3);
        Label titleLbl = new Label(title != null ? title : "Certificate Proof");
        titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #1E293B;");
        String sub = organization != null && !organization.isBlank() ? "Issued by: " + organization : "Academic Credential Attachment";
        Label subLbl = new Label(sub);
        subLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: #64748B;");
        headerText.getChildren().addAll(titleLbl, subLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label proofBadge = Ui.badge("Verified Proof", "badge-pill-mint");
        header.getChildren().addAll(headerText, spacer, proofBadge);

        // Image container
        StackPane imageCard = new StackPane();
        imageCard.setAlignment(Pos.CENTER);
        imageCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12; -fx-effect: dropshadow(gaussian, rgba(26, 38, 52, 0.05), 8, 0.08, 0, 2);");
        imageCard.setMinHeight(380);

        if (image != null && !image.isError()) {
            ImageView imageView = new ImageView(image);
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(670);
            imageView.setFitHeight(450);
            imageCard.getChildren().add(imageView);
        } else {
            VBox errorBox = new VBox(8);
            errorBox.setAlignment(Pos.CENTER);
            Label errIcon = new Label("⚠️");
            errIcon.setStyle("-fx-font-size: 32px;");
            Label errMsg = new Label("Image preview unavailable for:\n" + imagePathOrUrl);
            errMsg.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12.5px; -fx-alignment: center;");
            errorBox.getChildren().addAll(errIcon, errMsg);
            imageCard.getChildren().add(errorBox);
        }

        // Actions toolbar
        HBox actionsRow = new HBox(10);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);

        final File finalFile = file;
        Button openExtBtn = new Button("Open in Desktop App ↗");
        openExtBtn.getStyleClass().add("secondary-button");
        openExtBtn.setStyle("-fx-font-size: 12px; -fx-padding: 7 14; -fx-cursor: hand;");
        openExtBtn.setOnAction(e -> {
            try {
                if (finalFile != null && finalFile.exists()) {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(finalFile);
                    }
                } else if (imagePathOrUrl.startsWith("http://") || imagePathOrUrl.startsWith("https://")) {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(imagePathOrUrl));
                    }
                }
            } catch (Exception ex) {
                Alerts.warn("Could not open file in desktop app: " + ex.getMessage());
            }
        });

        Region botSpacer = new Region();
        HBox.setHgrow(botSpacer, Priority.ALWAYS);
        actionsRow.getChildren().addAll(openExtBtn, botSpacer);

        content.getChildren().addAll(header, imageCard, actionsRow);

        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(CertificateImageViewer.class.getResource("/com/nextit/css/application.css").toExternalForm());
        pane.setStyle("-fx-background-color: #FAF8F5; -fx-padding: 0;");
        pane.setContent(content);

        pane.getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.setText("Done");
            closeBtn.getStyleClass().addAll("dialog-btn", "primary-button");
            closeBtn.setStyle("-fx-background-color: #991B1B; -fx-text-fill: #FFFFFF; -fx-font-weight: 800; -fx-padding: 8 22; -fx-background-radius: 6; -fx-cursor: hand;");
        }

        pane.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE || ev.getCode() == KeyCode.ENTER) {
                dialog.close();
                ev.consume();
            }
        });

        dialog.setResizable(true);
        dialog.setOnShowing(ev -> {
            if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                WindowBoundsManager.loadStageIcons(stage);
                stage.setMinWidth(730);
                stage.setMinHeight(560);
            }
        });

        dialog.showAndWait();
    }
}
