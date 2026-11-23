package com.nextit.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Manages persisting and resolving certificate/proof image attachments locally
 * in the application's storage folder.
 */
public final class CertificateStorageService {

    private static final Path UPLOADS_DIR = Paths.get("uploads", "certificates");

    private CertificateStorageService() {}

    /**
     * Copies the source image into uploads/certificates/ with a unique filename
     * and returns the relative storage path (e.g., uploads/certificates/cert_123_abc.png).
     */
    public static String storeCertificateImage(File sourceFile) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) return null;
        if (!Files.exists(UPLOADS_DIR)) {
            Files.createDirectories(UPLOADS_DIR);
        }
        String originalName = sourceFile.getName();
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) {
            ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
        }
        String uniqueName = "cert_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path targetPath = UPLOADS_DIR.resolve(uniqueName);
        Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        // Normalize path with forward slashes for storage and DB compatibility
        return "uploads/certificates/" + uniqueName;
    }

    /**
     * Resolves a stored path or URL string into a local File if it exists.
     */
    public static File resolveImageFile(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return null;
        String trimmed = pathOrUrl.trim();

        // 1. Direct path check
        File direct = new File(trimmed);
        if (direct.exists()) return direct;

        // 2. Relative to working directory
        try {
            Path rel = Paths.get(trimmed);
            if (Files.exists(rel)) return rel.toFile();
        } catch (Exception ignored) {}

        // 3. File URI check
        if (trimmed.startsWith("file:/")) {
            try {
                File uriFile = new File(new URI(trimmed));
                if (uriFile.exists()) return uriFile;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Checks if the given path or URL points to a valid image or image file.
     */
    public static boolean hasValidProof(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return false;
        String trimmed = pathOrUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return true;
        }
        File file = resolveImageFile(trimmed);
        return file != null && file.exists() && file.length() > 0;
    }
}
