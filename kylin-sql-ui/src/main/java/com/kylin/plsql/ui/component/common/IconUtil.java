package com.kylin.plsql.ui.component.common;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.kitfox.svg.SVGUniverse;
import com.kitfox.svg.SVGDiagram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IconUtil {

    private static final Logger log = LoggerFactory.getLogger(IconUtil.class);

    private static final SVGUniverse svgUniverse = new SVGUniverse();
    private static final int SIZE = 16;

    private static final java.util.Set<String> ORIGINAL_COLOR_ICONS = java.util.Set.of(
        "mysql", "oracle", "mariadb", "oceanbase"
    );

    public static ImageIcon menuIcon(String name) {
        return loadButtonIcon(name, getColorForIcon(name));
    }

    private static final java.util.regex.Pattern STYLE_PATTERN = java.util.regex.Pattern.compile("style=\"([^\"]*)\"");

    private static String simplifySvg(String svg) {
        svg = svg.replaceAll("<title>[^<]*</title>", "")
                 .replaceAll("<g[^>]*></g>", "")
                 .replaceAll("id=\"SVGRepo_[^\"]*\"", "")
                 .replaceAll("fill-rule=\"[^\"]*\"\\s*", "")
                 .replaceAll("clip-rule=\"[^\"]*\"\\s*", "");
        java.util.regex.Matcher m = STYLE_PATTERN.matcher(svg);
        if (!m.find()) return svg;
        StringBuffer sb = new StringBuffer();
        m.reset();
        while (m.find()) {
            StringBuilder a = new StringBuilder();
            for (String p : m.group(1).split(";")) {
                String[] kv = p.split(":", 2);
                if (kv.length == 2) a.append(kv[0].trim()).append("=\"").append(kv[1].trim()).append("\" ");
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(a.toString().trim()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static ImageIcon loadButtonIcon(String name, Color color) {
        if (color == null) color = getColorForIcon(name);
        String pngPath = "/icons/database/" + name + ".png";
        InputStream pngIn = IconUtil.class.getResourceAsStream(pngPath);
        if (pngIn != null) {
            try {
                BufferedImage img = ImageIO.read(pngIn);
                if (img != null) {
                    ImageIcon icon = new ImageIcon(img.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH));
                    return icon;
                }
                return null;
            } catch (Exception e) {
                return null;
            } finally {
                try { pngIn.close(); } catch (Exception ignored) {}
            }
        }
        String path = "/icons/" + name + ".svg";
        InputStream in = IconUtil.class.getResourceAsStream(path);
        if (in == null) {
            path = "/icons/database/" + name + ".svg";
            in = IconUtil.class.getResourceAsStream(path);
        }
        if (in == null) return null;
        try {
            String svgText = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            SVGDiagram diagram;
            if (ORIGINAL_COLOR_ICONS.contains(name)) {
                svgText = simplifySvg(svgText);
                try (ByteArrayInputStream bis = new ByteArrayInputStream(svgText.getBytes(StandardCharsets.UTF_8))) {
                    java.net.URI uri = svgUniverse.loadSVG(bis, name);
                    diagram = svgUniverse.getDiagram(uri);
                }
                if (diagram == null) return null;
                double scale = Math.min((double) SIZE / diagram.getWidth(), (double) SIZE / diagram.getHeight());
                BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = result.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.transform(AffineTransform.getScaleInstance(scale, scale));
                diagram.render(g);
                g.dispose();
                return new ImageIcon(result);
            }

            svgText = svgText.replace("currentColor", "white");
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
            log.warn("[DIAG] IconUtil failed for {}: {} - {}", name, e.getClass().getSimpleName(), e.getMessage());
            return null;
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }

    private static Color getColorForIcon(String name) {
        if (name == null) return new Color(0x5B5B5B);
        return switch (name) {
            case "new", "create", "add", "plus", "execute", "append", "commit", "arrow-big-up" -> new Color(0x2E7D32);
            case "open", "locate", "folder", "skip-forward", "forward",
                 "search", "find", "file-search", "database-search",
                 "refresh", "sync", "reload",
                 "arrow-left", "arrow-right", "arrow-left-to-line", "arrow-right-to-line" -> new Color(0x1565C0);
            case "save", "save-plus", "copy", "export", "edit",
                 "arrow-down-to-line", "arrow-up-to-line" -> new Color(0xF57F17);
            case "format", "regex", "compile" -> new Color(0x6A1B9A);
            case "x", "close", "trash", "trash-2", "delete", "remove", "rollback", "zap-off", "minus", "stop" -> new Color(0xC62828);
            case "history", "time", "clock" -> new Color(0xE65100);
            case "connect", "settings", "config", "preference", "compare", "diff" -> new Color(0x00695C);
            case "info", "help", "question", "pin", "pin-off" -> new Color(0x546E7A);
            case "database", "oracle" -> new Color(0xC74634);
            case "mysql" -> new Color(0x00758F);
            case "postgresql" -> new Color(0x336791);
            case "mariadb" -> new Color(0x003545);
            case "sqlite" -> new Color(0x003B57);
            case "oceanbase" -> new Color(0x0181FD);
            case "microsoftsqlserver" -> new Color(0xCC2927);
            default -> new Color(0x5B5B5B);
        };
    }
}
