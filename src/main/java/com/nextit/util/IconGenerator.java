package com.nextit.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Generates transparent-background NextIT emblem logos and multi-resolution
 * desktop icons (16, 32, 48, 64, 128, 256, and .ico) from the official emblem master.
 */
public class IconGenerator {

    /**
     * Extracts the NextIT circular emblem from the source image, removes the external
     * white/dark margins with alpha feathering, and centers it on a square transparent canvas.
     */
    public static BufferedImage extractTransparentEmblem(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        // 1. Flood-fill background detection
        boolean[][] isBg = new boolean[w][h];
        Queue<int[]> q = new ArrayDeque<>();

        for (int x = 0; x < w; x++) {
            q.add(new int[]{x, 0});
            q.add(new int[]{x, h - 1});
        }
        for (int y = 0; y < h; y++) {
            q.add(new int[]{0, y});
            q.add(new int[]{w - 1, y});
        }

        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1];
            if (x < 0 || x >= w || y < 0 || y >= h || isBg[x][y]) continue;

            int rgb = src.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            // Background conditions:
            // - Dark margin strip on right edge (x >= 838)
            // - Shadow gradient below bottom gold rim (y >= 780)
            // - Light/white background outside the circular emblem & banner
            boolean isArtifactStrip = (x >= 838);
            boolean isBottomShadow = (y >= 780);
            boolean isWhiteBg = (r >= 235 && g >= 235 && b >= 230);
            boolean isVeryLight = (r >= 215 && g >= 212 && b >= 208 && Math.abs(r - g) < 26 && Math.abs(g - b) < 26);

            if (isArtifactStrip || isBottomShadow || isWhiteBg || isVeryLight) {
                isBg[x][y] = true;
                q.add(new int[]{x + 1, y});
                q.add(new int[]{x - 1, y});
                q.add(new int[]{x, y + 1});
                q.add(new int[]{x, y - 1});
            }
        }

        // 2. Measure foreground bounding box
        int minX = w, maxX = 0, minY = h, maxY = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!isBg[x][y]) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        int fgWidth = maxX - minX + 1;
        int fgHeight = maxY - minY + 1;
        int canvasSize = Math.max(fgWidth, fgHeight) + 16; // 8px breathing padding on each side
        int offsetX = (canvasSize - fgWidth) / 2 - minX;
        int offsetY = (canvasSize - fgHeight) / 2 - minY;

        // 3. Compute distance to background for 2px soft anti-aliased alpha feathering
        float[][] alpha = new float[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (isBg[x][y]) {
                    alpha[x][y] = 0f;
                } else {
                    // Check neighbors within 2 pixels
                    float minBgDist = 999f;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dy = -2; dy <= 2; dy++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h || isBg[nx][ny]) {
                                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                                if (dist < minBgDist) minBgDist = dist;
                            }
                        }
                    }
                    if (minBgDist <= 1.0f) {
                        alpha[x][y] = 0.45f * minBgDist;
                    } else if (minBgDist <= 2.0f) {
                        alpha[x][y] = 0.45f + 0.55f * (minBgDist - 1.0f);
                    } else {
                        alpha[x][y] = 1.0f;
                    }
                }
            }
        }

        // 4. Render onto high-res square transparent ARGB canvas
        BufferedImage transparentEmblem = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                float a = alpha[x][y];
                if (a > 0.02f) {
                    int rgb = src.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int aInt = Math.min(255, Math.round(a * 255f));
                    int destRgb = (aInt << 24) | (r << 16) | (g << 8) | b;
                    transparentEmblem.setRGB(x + offsetX, y + offsetY, destRgb);
                }
            }
        }

        return transparentEmblem;
    }

    /**
     * High-quality bicubic downsampler for icons.
     */
    public static BufferedImage scaleImage(BufferedImage src, int targetSize) {
        BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        g2.drawImage(src, 0, 0, targetSize, targetSize, null);
        g2.dispose();
        return scaled;
    }

    public static byte[] createIco(List<BufferedImage> images) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<byte[]> pngBytesList = new ArrayList<>();

        for (BufferedImage img : images) {
            ByteArrayOutputStream pngOs = new ByteArrayOutputStream();
            ImageIO.write(img, "png", pngOs);
            pngBytesList.add(pngOs.toByteArray());
        }

        int count = images.size();
        // Header: 6 bytes
        baos.write(new byte[]{0, 0, 1, 0, (byte) count, 0});

        int offset = 6 + (count * 16);
        for (int i = 0; i < count; i++) {
            BufferedImage img = images.get(i);
            byte[] pngData = pngBytesList.get(i);

            int width = img.getWidth() >= 256 ? 0 : img.getWidth();
            int height = img.getHeight() >= 256 ? 0 : img.getHeight();

            baos.write((byte) width);
            baos.write((byte) height);
            baos.write(0); // color count
            baos.write(0); // reserved
            baos.write(new byte[]{1, 0}); // planes
            baos.write(new byte[]{32, 0}); // bit count

            int dataSize = pngData.length;
            baos.write((byte) (dataSize & 0xFF));
            baos.write((byte) ((dataSize >> 8) & 0xFF));
            baos.write((byte) ((dataSize >> 16) & 0xFF));
            baos.write((byte) ((dataSize >> 24) & 0xFF));

            baos.write((byte) (offset & 0xFF));
            baos.write((byte) ((offset >> 8) & 0xFF));
            baos.write((byte) ((offset >> 16) & 0xFF));
            baos.write((byte) ((offset >> 24) & 0xFF));

            offset += dataSize;
        }

        for (byte[] pngData : pngBytesList) {
            baos.write(pngData);
        }

        return baos.toByteArray();
    }

    public static void generateAllAssets(File masterSource) throws Exception {
        BufferedImage src = ImageIO.read(masterSource);
        if (src == null) {
            throw new IllegalArgumentException("Could not read image from " + masterSource);
        }

        System.out.println("Processing master emblem from: " + masterSource.getAbsolutePath() + " (" + src.getWidth() + "x" + src.getHeight() + ")");
        BufferedImage transparentEmblem = extractTransparentEmblem(src);
        System.out.println("Transparent emblem extracted: " + transparentEmblem.getWidth() + "x" + transparentEmblem.getHeight());

        // Save high-resolution transparent master logo
        Path resImagesDir = Path.of("src/main/resources/images");
        Path resIconsDir = Path.of("src/main/resources/com/nextit/icons");
        Files.createDirectories(resImagesDir);
        Files.createDirectories(resIconsDir);

        File logoPng1 = resImagesDir.resolve("nextit-logo.png").toFile();
        File logoPng2 = resIconsDir.resolve("nextit-logo.png").toFile();
        ImageIO.write(transparentEmblem, "png", logoPng1);
        ImageIO.write(transparentEmblem, "png", logoPng2);
        System.out.println("Saved master transparent logo to: " + logoPng1.getPath() + " and " + logoPng2.getPath());

        int[] sizes = {16, 32, 48, 64, 128, 256};
        List<BufferedImage> icoImages = new ArrayList<>();

        for (int size : sizes) {
            BufferedImage scaled = scaleImage(transparentEmblem, size);
            File iconFile = resIconsDir.resolve("nextit-" + size + ".png").toFile();
            ImageIO.write(scaled, "png", iconFile);
            System.out.println("Saved " + size + "x" + size + " icon to: " + iconFile.getPath());
            icoImages.add(scaled);
        }

        byte[] icoData = createIco(icoImages);
        Path resIcoPath = resIconsDir.resolve("nextit.ico");
        Path rootIcoPath = Path.of("nextit.ico");
        Files.write(resIcoPath, icoData);
        Files.write(rootIcoPath, icoData);
        System.out.println("Saved Windows ICO with all resolutions to " + resIcoPath + " and " + rootIcoPath);
    }

    public static void main(String[] args) throws Exception {
        File masterSource = null;
        if (args != null && args.length > 0) {
            masterSource = new File(args[0]);
        }
        if (masterSource == null || !masterSource.exists()) {
            masterSource = new File("src/main/resources/images/nextit-emblem-source.jpg");
        }
        if (!masterSource.exists()) {
            masterSource = new File("src/main/resources/images/nextit-logo.png");
        }

        generateAllAssets(masterSource);
    }
}
