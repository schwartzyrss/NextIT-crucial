package com.nextit.util;

import com.nextit.controller.AppController;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Interactive drag-and-drop certificate image uploader box with thumbnail preview,
 * file validation, and replace/remove actions.
 */
public class CertificateUploaderBox extends VBox {

    private File selectedFile;
    private String existingPath;
    private boolean removed = false;

    private static final String STYLE_NORMAL =
            "-fx-background-color: #FAF8F5; -fx-border-color: #CBD5E1; -fx-border-style: dashed; -fx-border-width: 1.8; " +
            "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 22 18; -fx-cursor: hand;";

    private static final String STYLE_HOVER =
            "-fx-background-color: #FEF2F2; -fx-border-color: #991B1B; -fx-border-style: dashed; -fx-border-width: 2.2; " +
            "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 22 18; -fx-cursor: hand;";

    private static final String STYLE_PREVIEW =
            "-fx-background-color: #FFFFFF; -fx-border-color: #E2D9CC; -fx-border-style: solid; -fx-border-width: 1; " +
            "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12 16; -fx-effect: dropshadow(gaussian, rgba(26, 38, 52, 0.05), 6, 0.05, 0, 2);";

    public CertificateUploaderBox() {
        this(null);
    }

    public CertificateUploaderBox(String existingPath) {
        this.existingPath = existingPath;
        setAlignment(Pos.CENTER);
        setSpacing(8);
        setMaxWidth(Double.MAX_VALUE);

        setupDragAndDrop();
        render();
    }

    private void setupDragAndDrop() {
        setOnDragOver(event -> {
            if (event.getGestureSource() != this && event.getDragboard().hasFiles()) {
                if (containsImageFile(event.getDragboard().getFiles())) {
                    event.acceptTransferModes(TransferMode.COPY);
                }
            }
            event.consume();
        });

        setOnDragEntered(event -> {
            if (event.getGestureSource() != this && event.getDragboard().hasFiles()) {
                if (containsImageFile(event.getDragboard().getFiles())) {
                    setStyle(STYLE_HOVER);
                }
            }
            event.consume();
        });

        setOnDragExited(event -> {
            if (hasActiveImage()) {
                setStyle(STYLE_PREVIEW);
            } else {
                setStyle(STYLE_NORMAL);
            }
            event.consume();
        });

        setOnDragDropped(event -> {
            boolean success = false;
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                File img = findFirstImage(db.getFiles());
                if (img != null) {
                    this.selectedFile = img;
                    this.removed = false;
                    render();
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        setOnMouseClicked(ev -> {
            if (!hasActiveImage()) {
                browseFile();
            }
        });
    }

    private void render() {
        getChildren().clear();

        if (hasActiveImage()) {
            setStyle(STYLE_PREVIEW);
            renderPreviewView();
        } else {
            setStyle(STYLE_NORMAL);
            renderEmptyDropzoneView();
        }
    }

    private void renderEmptyDropzoneView() {
        VBox dropContent = new VBox(6);
        dropContent.setAlignment(Pos.CENTER);

        Label icon = new Label("📤");
        icon.setStyle("-fx-font-size: 26px; -fx-text-fill: #991B1B;");

        Label primaryLabel = new Label("Drag & drop certificate picture here, or click to browse");
        primaryLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #1E293B;");

        Label hintLabel = new Label("Supports PNG, JPG, JPEG, WEBP");
        hintLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 500; -fx-text-fill: #64748B;");

        dropContent.getChildren().addAll(icon, primaryLabel, hintLabel);
        getChildren().add(dropContent);
    }

    private void renderPreviewView() {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);

        // Thumbnail
        StackPane thumbBox = new StackPane();
        thumbBox.setAlignment(Pos.CENTER);
        thumbBox.setStyle("-fx-background-color: #FAF8F5; -fx-border-color: #E2D9CC; -fx-border-radius: 6; -fx-background-radius: 6;");
        thumbBox.setPrefSize(90, 68);
        thumbBox.setMinSize(90, 68);
        thumbBox.setMaxSize(90, 68);

        Image img = loadActiveImageThumbnail();
        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(84);
            iv.setFitHeight(62);
            iv.setPreserveRatio(true);
            thumbBox.getChildren().add(iv);
        } else {
            Label noImg = new Label("🖼️");
            noImg.setStyle("-fx-font-size: 24px;");
            thumbBox.getChildren().add(noImg);
        }

        // Info VBox
        VBox infoBox = new VBox(4);
        String nameStr = selectedFile != null ? selectedFile.getName() : getFileNameFromPath(existingPath);
        Label nameLbl = new Label(nameStr);
        nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #1E293B;");
        nameLbl.setMaxWidth(260);
        nameLbl.setWrapText(false);

        String sizeStr = selectedFile != null ? formatFileSize(selectedFile.length()) : "Attached Certificate Image";
        Label sizeLbl = new Label(sizeStr);
        sizeLbl.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 500; -fx-text-fill: #64748B;");

        HBox badgeRow = new HBox(Ui.badge("Proof Attached", "badge-pill-mint"));
        infoBox.getChildren().addAll(nameLbl, sizeLbl, badgeRow);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        // Action Buttons
        VBox actionBox = new VBox(6);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        Button changeBtn = new Button("Change Picture");
        changeBtn.setStyle("-fx-background-color: #FAF8F5; -fx-border-color: #D1D5DB; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #1E293B; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 4 10; -fx-cursor: hand;");
        changeBtn.setOnAction(e -> browseFile());

        Button removeBtn = new Button("Remove");
        removeBtn.setStyle("-fx-background-color: #FEE2E2; -fx-border-color: #FCA5A5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #991B1B; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 4 10; -fx-cursor: hand;");
        removeBtn.setOnAction(e -> {
            this.selectedFile = null;
            this.existingPath = null;
            this.removed = true;
            render();
        });

        actionBox.getChildren().addAll(changeBtn, removeBtn);
        row.getChildren().addAll(thumbBox, infoBox, actionBox);
        getChildren().add(row);
    }

    private Image loadActiveImageThumbnail() {
        try {
            if (selectedFile != null && selectedFile.exists()) {
                return new Image(selectedFile.toURI().toString(), 180, 140, true, true);
            }
            if (existingPath != null && !existingPath.isBlank()) {
                File file = CertificateStorageService.resolveImageFile(existingPath);
                if (file != null && file.exists()) {
                    return new Image(file.toURI().toString(), 180, 140, true, true);
                }
                if (existingPath.startsWith("http://") || existingPath.startsWith("https://")) {
                    return new Image(existingPath, 180, 140, true, true, true);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void browseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Certificate Picture");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg, *.webp)",
                        "*.png", "*.jpg", "*.jpeg", "*.webp",
                        "*.PNG", "*.JPG", "*.JPEG", "*.WEBP"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );
        Window owner = AppController.getInstance() != null ? AppController.getInstance().getStage() : null;
        if (owner == null || !owner.isShowing()) {
            owner = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null);
        }
        File chosen = chooser.showOpenDialog(owner);
        if (chosen != null && isImageFile(chosen)) {
            this.selectedFile = chosen;
            this.removed = false;
            render();
        }
    }

    public boolean hasActiveImage() {
        return (selectedFile != null && selectedFile.exists()) ||
                (!removed && existingPath != null && !existingPath.isBlank());
    }

    public String saveAndGetFinalPath() {
        if (removed) {
            return null;
        }
        if (selectedFile != null && selectedFile.exists()) {
            try {
                return CertificateStorageService.storeCertificateImage(selectedFile);
            } catch (IOException ex) {
                throw new com.nextit.exception.AppException("Failed to save certificate image: " + ex.getMessage(), ex);
            }
        }
        return existingPath;
    }

    private static boolean containsImageFile(List<File> files) {
        if (files == null) return false;
        return files.stream().anyMatch(CertificateUploaderBox::isImageFile);
    }

    private static File findFirstImage(List<File> files) {
        if (files == null) return null;
        return files.stream().filter(CertificateUploaderBox::isImageFile).findFirst().orElse(null);
    }

    private static boolean isImageFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp");
    }

    private static String getFileNameFromPath(String path) {
        if (path == null || path.isBlank()) return "Certificate Image";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.ROOT, "%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }
}
