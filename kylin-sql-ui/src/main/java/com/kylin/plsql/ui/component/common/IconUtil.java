package com.kylin.plsql.ui.component.common;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.kitfox.svg.SVGUniverse;
import com.kitfox.svg.SVGDiagram;

public class IconUtil {

    private static final SVGUniverse svgUniverse = new SVGUniverse();
    private static final int SIZE = 16;
    public static ImageIcon menuIcon(String name) {
        return loadButtonIcon(name, getColorForIcon(name));
    }

    public static ImageIcon loadButtonIcon(String name, Color color) {
        if (color == null) color = getColorForIcon(name);
        String path = "/icons/" + name + ".svg";
        try (InputStream in = IconUtil.class.getResourceAsStream(path)) {
            if (in == null) return null;
            String svgText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            svgText = svgText.replace("currentColor", "white");

            SVGDiagram diagram;
            try (ByteArrayInputStream bis = new ByteArrayInputStream(svgText.getBytes(StandardCharsets.UTF_8))) {
                java.net.URI uri = svgUniverse.loadSVG(bis, name);
                diagram = svgUniverse.getDiagram(uri);
            }
            if (diagram == null) return null;

            double scale = Math.min((double) SIZE / diagram.getWidth(), (double) SIZE / diagram.getHeight());
            BufferedImage whiteImg = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = whiteImg.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.transform(AffineTransform.getScaleInstance(scale, scale));
            diagram.render(g);
            g.dispose();

            BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            g = result.createGraphics();
            g.setColor(color);
            g.fillRect(0, 0, SIZE, SIZE);
            g.dispose();
            result.getAlphaRaster().setRect(whiteImg.getAlphaRaster());
            return new ImageIcon(result);
        } catch (Exception e) {
            return null;
        }
    }

    private static Color getColorForIcon(String name) {
        if (name == null) return new Color(0x5B5B5B);
        return switch (name) {
            case "new", "create", "add", "plus", "execute", "append", "commit" -> new Color(0x2E7D32);
            case "open", "locate", "folder", "skip-forward", "forward",
                 "search", "find", "file-search", "database-search",
                 "refresh", "sync", "reload" -> new Color(0x1565C0);
            case "save", "save-plus", "copy", "export", "edit" -> new Color(0xF57F17);
            case "format", "regex", "compile" -> new Color(0x6A1B9A);
            case "x", "close", "trash", "trash-2", "delete", "remove", "rollback" -> new Color(0xC62828);
            case "history", "time", "clock" -> new Color(0xE65100);
            case "connect", "settings", "config", "preference", "compare", "diff" -> new Color(0x00695C);
            case "info", "help", "question", "pin", "pin-off" -> new Color(0x546E7A);
            default -> new Color(0x5B5B5B);
        };
    }
}