package org.app.musannif.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.swing.filechooser.FileSystemView;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

public class FileIconCache {

    private static final int ICON_SIZE = 32;
    private static final Map<String, Image> cache = new HashMap<>();
    private static final FileSystemView fsv = FileSystemView.getFileSystemView();
    private static final Image fallback;

    static {
        BufferedImage buf = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buf.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(70, 75, 95));
        g.fillRoundRect(4, 3, 24, 26, 5, 5);
        g.setColor(new java.awt.Color(50, 55, 75));
        g.fillPolygon(new int[]{20, 20, 28}, new int[]{3, 11, 11}, 3);
        g.setColor(new java.awt.Color(90, 95, 115));
        g.drawRoundRect(4, 3, 24, 26, 5, 5);
        g.dispose();
        fallback = toFXImage(buf);
    }

    public static Image getIcon(Path path) {
        if (path == null) return fallback;
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = (dot < 0) ? "" : name.substring(dot + 1).toLowerCase();
        return cache.computeIfAbsent(ext, k -> extractIcon(path));
    }

    private static Image extractIcon(Path path) {
        File file = path.toFile();
        if (!file.isFile()) return fallback;
        try {
            javax.swing.Icon icon = fsv.getSystemIcon(file, ICON_SIZE, ICON_SIZE);
            if (icon == null) icon = fsv.getSystemIcon(file);
            if (icon == null) return fallback;
            BufferedImage buf = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = buf.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int iw = icon.getIconWidth();
            int ih = icon.getIconHeight();
            if (iw == ICON_SIZE && ih == ICON_SIZE) {
                icon.paintIcon(null, g, 0, 0);
            } else {
                double scale = Math.min((double) ICON_SIZE / iw, (double) ICON_SIZE / ih);
                int dw = (int)(iw * scale);
                int dh = (int)(ih * scale);
                BufferedImage tmp = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
                Graphics2D tg = tmp.createGraphics();
                tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                icon.paintIcon(null, tg, 0, 0);
                tg.dispose();
                g.drawImage(tmp, (ICON_SIZE - dw) / 2, (ICON_SIZE - dh) / 2, dw, dh, null);
            }
            g.dispose();
            return toFXImage(buf);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Image toFXImage(BufferedImage buf) {
        WritableImage fx = new WritableImage(ICON_SIZE, ICON_SIZE);
        PixelWriter pw = fx.getPixelWriter();
        for (int y = 0; y < ICON_SIZE; y++)
            for (int x = 0; x < ICON_SIZE; x++)
                pw.setArgb(x, y, buf.getRGB(x, y));
        return fx;
    }
}
