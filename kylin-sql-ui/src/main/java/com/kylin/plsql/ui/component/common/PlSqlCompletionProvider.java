package com.kylin.plsql.ui.component.common;

import com.kylin.plsql.core.cache.MetadataCache;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionCellRenderer;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlSqlCompletionProvider extends DefaultCompletionProvider {
    private static final Logger log = LoggerFactory.getLogger(PlSqlCompletionProvider.class);

    private final Supplier<String> connNameSupplier;
    private final Supplier<String> schemaSupplier;
    private final MetadataCache cache = MetadataCache.getInstance();
    private BiConsumer<String, String> columnLoader; // (schema, table) → lazy-load columns

    public PlSqlCompletionProvider(Supplier<String> connNameSupplier, Supplier<String> schemaSupplier) {
        super(PlSqlTokenMaker.KEYWORDS.toArray(new String[0]));
        this.connNameSupplier = connNameSupplier;
        this.schemaSupplier = schemaSupplier;
        setAutoActivationRules(true, null);
        setListCellRenderer(new CompletionCellRenderer());
        log.info("PlSqlCompletionProvider initialized with {} keywords", getCompletionCount());
    }

    public void setColumnLoader(BiConsumer<String, String> loader) {
        this.columnLoader = loader;
    }

    @Override
    public String getAlreadyEnteredText(JTextComponent comp) {
        String text = super.getAlreadyEnteredText(comp);
        log.debug("getAlreadyEnteredText -> '{}' (caret={})", text, comp.getCaretPosition());
        return text;
    }

    @Override
    protected List<Completion> getCompletionsImpl(JTextComponent comp) {
        String entered = super.getAlreadyEnteredText(comp);
        String connName = connNameSupplier != null ? connNameSupplier.get() : null;
        String tableName = (connName != null && !connName.isEmpty()) ? getTableBeforeDot(comp) : null;

        if (tableName != null) {
            // alias dot: only show columns, no keywords / other tables
            List<Completion> result = new ArrayList<>();
            addColumnCompletions(result, connName, tableName, entered);
            log.info("getCompletionsImpl entered='{}', dot=table={}, total={}", entered, tableName, result.size());
            return result;
        }

        List<Completion> result = super.getCompletionsImpl(comp);
        if (result == null) result = new ArrayList<>();
        if (connName != null && !connName.isEmpty() && entered != null && !entered.isEmpty()) {
            addObjectCompletions(result, connName, entered);
        }

        log.info("getCompletionsImpl entered='{}', conn={}, total={}", entered, connName, result.size());
        if (!result.isEmpty()) {
            log.info("  first few: {}", result.stream().limit(5).map(c -> c.getInputText()).toList());
        }
        return result;
    }

    /** Return the current schema (may be null). */
    private String getCurrentSchema() {
        return schemaSupplier != null ? schemaSupplier.get() : null;
    }

    // ── Type-based icons (same style as ObjectBrowser) ──

    private static final Map<String, Icon> TYPE_ICONS = new HashMap<>();
    static {
        addTypeIcon("TABLE",     "T", new Color(0x337AB7));
        addTypeIcon("VIEW",      "V", new Color(0x5BC0DE));
        addTypeIcon("INDEX",     "I", new Color(0xF0AD4E));
        addTypeIcon("SEQUENCE",  "N", new Color(0x8E44AD));
        addTypeIcon("SYNONYM",   "Y", new Color(0x7B8D8E));
        addTypeIcon("FUNCTION",  "F", new Color(0xD9534F));
        addTypeIcon("PROCEDURE", "P", new Color(0xD9534F));
        addTypeIcon("PACKAGE",   "K", new Color(0xA0522D));
        addTypeIcon("COLUMN",    "C", new Color(0x059775));
    }

    private static void addTypeIcon(String type, String letter, Color bg) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(1, 1, 14, 14, 3, 3);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        int x = (16 - fm.stringWidth(letter)) / 2;
        int y = (16 + fm.getAscent()) / 2 - 1;
        g.drawString(letter, x, y);
        g.dispose();
        TYPE_ICONS.put(type, new ImageIcon(img));
    }

    private static Icon iconForType(String type) {
        Icon ic = type != null ? TYPE_ICONS.get(type) : null;
        return ic != null ? ic : TYPE_ICONS.get("TABLE");
    }

    // ── Custom completion items ──

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Truncate long text with ellipsis. */
    private static String trunc(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    /** Completion for a table/view with schema + comment (truncated display, full tooltip). */
    private static class TableCompletion extends BasicCompletion {
        private final String displayHtml;

        TableCompletion(CompletionProvider provider, String tableName, String schema, String type, String comment) {
            super(provider, tableName,
                  buildShortDesc(schema, comment),
                  buildSummary(tableName, schema, comment));
            setIcon(iconForType(type));
            displayHtml = "<html><b>" + esc(trunc(tableName, 24)) + "</b>"
                + " <span style='color:#888;font-size:10px'>" + esc(trunc(schema, 16)) + "</span>"
                + (comment != null && !comment.isEmpty()
                    ? " <span style='color:#999;font-size:10px'>\u2014 " + esc(trunc(comment, 28)) + "</span>"
                    : "")
                + "</html>";
        }

        private static String buildShortDesc(String schema, String comment) {
            StringBuilder sb = new StringBuilder(schema);
            if (comment != null && !comment.isEmpty()) sb.append(" \u2014 ").append(trunc(comment, 28));
            return sb.toString();
        }

        private static String buildSummary(String name, String schema, String comment) {
            StringBuilder sb = new StringBuilder(schema).append('.').append(name);
            if (comment != null && !comment.isEmpty()) sb.append(" \u2014 ").append(comment);
            return sb.toString();
        }

        @Override
        public String toString() {
            return displayHtml;
        }
    }

    /** Completion for a column with type + comment (truncated display, full tooltip). */
    private static class ColumnCompletion extends BasicCompletion {
        private final String colType;
        private final String colComment;

        ColumnCompletion(CompletionProvider provider, String columnName, String type, String comment) {
            super(provider, columnName,
                  buildShortDesc(type, comment),
                  buildSummary(columnName, type, comment));
            setIcon(TYPE_ICONS.get("COLUMN"));
            this.colType = type != null ? type : "";
            this.colComment = comment != null ? comment : "";
        }

        private static String buildShortDesc(String type, String comment) {
            StringBuilder sb = new StringBuilder();
            if (type != null && !type.isEmpty()) sb.append(type);
            if (comment != null && !comment.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append('\u2014').append(trunc(comment, 28));
            }
            return sb.toString();
        }

        private static String buildSummary(String name, String type, String comment) {
            StringBuilder sb = new StringBuilder(name);
            if (type != null && !type.isEmpty()) sb.append(' ').append(type);
            if (comment != null && !comment.isEmpty()) sb.append(" \u2014 ").append(comment);
            return sb.toString();
        }
    }

    // ── Completion logic ──

    private static final Pattern ALIAS_PATTERN = Pattern.compile(
        "(?:FROM|JOIN|INTO)\\s+([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)?)(?:\\s+AS)?\\s+([A-Za-z_]\\w*)",
        Pattern.CASE_INSENSITIVE);

    /** Resolve text before dot: if it's an alias, return mapped table name. */
    private String getTableBeforeDot(JTextComponent comp) {
        try {
            Document doc = comp.getDocument();
            int caret = comp.getCaretPosition();
            int lineStart = doc.getDefaultRootElement().getElement(
                doc.getDefaultRootElement().getElementIndex(caret)).getStartOffset();
            String prefix = doc.getText(lineStart, caret - lineStart);
            int dot = prefix.lastIndexOf('.');
            if (dot < 0) return null;
            String beforeDot = prefix.substring(0, dot).trim();
            String name = beforeDot.replaceAll("^.*\\s+", "");
            if (name.isEmpty()) return null;
            String upper = name.toUpperCase();

            if (isKnownTable(upper)) return upper;

            String fullText = doc.getText(0, doc.getLength());
            return resolveAlias(fullText, upper);
        } catch (BadLocationException ignored) {}
        return null;
    }

    private boolean isKnownTable(String name) {
        String connName = connNameSupplier != null ? connNameSupplier.get() : null;
        if (connName == null || connName.isEmpty()) return false;
        String schema = getCurrentSchema();
        if (schema != null && !schema.isEmpty()) {
            var byType = cache.getObjectNamesByType(connName, schema);
            if (byType != null) {
                for (var names : byType.values()) {
                    if (names.contains(name)) return true;
                }
            }
            return false;
        }
        for (String s : cache.getSchemas(connName)) {
            var byType = cache.getObjectNamesByType(connName, s);
            if (byType == null) continue;
            for (var names : byType.values()) {
                if (names.contains(name)) return true;
            }
        }
        return false;
    }

    private String resolveAlias(String sql, String alias) {
        Matcher m = ALIAS_PATTERN.matcher(sql);
        while (m.find()) {
            String tableRef = m.group(1);
            String als = m.group(2);
            if (als.equalsIgnoreCase(alias)) {
                int dot = tableRef.indexOf('.');
                return dot >= 0 ? tableRef.substring(dot + 1).toUpperCase() : tableRef.toUpperCase();
            }
        }
        return null;
    }

    /** Add table / view names matching entered text, scoped to current schema if set. */
    private void addObjectCompletions(List<Completion> result, String connName, String entered) {
        if (entered == null || entered.isEmpty()) return;
        String upper = entered.toUpperCase();
        String currentSchema = getCurrentSchema();
        if (currentSchema != null && !currentSchema.isEmpty()) {
            addObjectsForSchema(result, connName, currentSchema, upper);
        } else {
            for (String schema : cache.getSchemas(connName)) {
                addObjectsForSchema(result, connName, schema, upper);
            }
        }
    }

    private void addObjectsForSchema(List<Completion> result, String connName, String schema, String upper) {
        var byType = cache.getObjectNamesByType(connName, schema);
        if (byType == null) return;
        for (var entry : byType.entrySet()) {
            String type = entry.getKey();
            for (String name : entry.getValue()) {
                if (name.startsWith(upper)) {
                    String comment = cache.getTableComment(connName, schema, name);
                    result.add(new TableCompletion(this, name, schema, type, comment));
                }
            }
        }
    }

    /** Add column names of the given table (lazy-load from DB if not cached). */
    private void addColumnCompletions(List<Completion> result, String connName, String tableName, String entered) {
        String upper = entered == null ? "" : entered.toUpperCase();
        String currentSchema = getCurrentSchema();
        List<String> schemas;
        if (currentSchema != null && !currentSchema.isEmpty()) {
            schemas = new ArrayList<>();
            schemas.add(currentSchema);
        } else {
            schemas = cache.getSchemas(connName);
        }
        for (String schema : schemas) {
            var cols = cache.getColumns(connName, schema, tableName);
            if (cols == null && columnLoader != null) {
                columnLoader.accept(schema, tableName);
                cols = cache.getColumns(connName, schema, tableName);
            }
            if (cols != null && !cols.isEmpty()) {
                for (var col : cols) {
                    if (col.name.startsWith(upper)) {
                        result.add(new ColumnCompletion(this, col.name, col.type, col.comment));
                    }
                }
                return;
            }
        }
    }

    private int getCompletionCount() {
        return completions != null ? completions.size() : 0;
    }
}
