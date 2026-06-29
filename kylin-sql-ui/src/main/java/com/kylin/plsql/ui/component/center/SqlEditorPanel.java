package com.kylin.plsql.ui.component.center;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SqlEditorPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(SqlEditorPanel.class);
    private final com.kylin.plsql.core.config.ThemeManager theme = com.kylin.plsql.core.config.ThemeManager.getInstance();
    private final RSyntaxTextArea textArea;
    private final ConnectionManager connectionManager;
    private String filePath;
    private boolean modified;
    private final String tabName;
    private Runnable onModifiedChange;
    private Runnable onExecute;
    private Runnable onFormat;
    private Runnable onConnectionChange;
    private String connectionName;
    private final Map<String, ConnectionInfo> connDisplayMap = new LinkedHashMap<>();

    private final JComboBox<String> connCombo;
    private final JComboBox<String> schemaCombo;
    private final JCheckBox autoCommitCheck;
    private final JButton commitBtn;
    private final JButton rollbackBtn;
    private Color hoverBg;

    private Object segmentPainterTag;
    private final DynamicSegmentPainter segmentPainter = new DynamicSegmentPainter();
    private final RTextScrollPane scrollPane;
    private final SearchReplacePanel searchPanel = new SearchReplacePanel();
    private final JPanel toolBar;
    private final JPanel topWrapper;
    private final List<Object> execTags = new ArrayList<>();
    private int lastExecLine = -1;
    private boolean lastExecSuccess;
    private List<int[]> cachedSegments = java.util.Collections.emptyList();
    private int[] segmentBoxCacheKey;
    private java.awt.Rectangle segmentBoxCache;

    public SqlEditorPanel(ConnectionManager cm, String tabName) {
        this.connectionManager = cm;
        this.tabName = tabName != null ? tabName : "sql";
        setLayout(new BorderLayout());

        toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        JButton execBtn = flatBtn("\u25B6", "\u6267\u884C (F8)", e -> { if (onExecute != null) onExecute.run(); });
        execBtn.setForeground(new Color(0x5CB85C));
        toolBar.add(execBtn);

        JButton historyBtn = flatBtn("\uD83D\uDCCB", "\u6267\u884C\u5386\u53F2", null);
        toolBar.add(historyBtn);

        toolBar.add(new JLabel(" \u8FDE\u63A5:"));

        connCombo = new JComboBox<>();
        connCombo.setPreferredSize(new Dimension(200, 26));
        connCombo.addActionListener(e -> onConnChanged());
        toolBar.add(connCombo);

        toolBar.add(new JLabel(" Schema:"));
        schemaCombo = new JComboBox<>();
        schemaCombo.setEditable(true);
        schemaCombo.setPreferredSize(new Dimension(140, 26));
        toolBar.add(schemaCombo);

        toolBar.add(Box.createHorizontalStrut(8));

        autoCommitCheck = new JCheckBox("\u81EA\u52A8\u63D0\u4EA4", true);
        autoCommitCheck.addActionListener(e -> toggleAutoCommit());
        toolBar.add(autoCommitCheck);

        commitBtn = flatBtn("\u63D0\u4EA4", null, e -> doCommit());
        styleBtn(commitBtn);
        commitBtn.setEnabled(false);
        toolBar.add(commitBtn);

        rollbackBtn = flatBtn("\u56DE\u6EDA", null, e -> doRollback());
        styleBtn(rollbackBtn);
        rollbackBtn.setEnabled(false);
        toolBar.add(rollbackBtn);

        topWrapper = new JPanel(new BorderLayout());
        searchPanel.setVisible(false);
        topWrapper.add(toolBar, BorderLayout.NORTH);
        topWrapper.add(searchPanel, BorderLayout.SOUTH);
        add(topWrapper, BorderLayout.NORTH);

        textArea = new RSyntaxTextArea(25, 80);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setAutoIndentEnabled(true);
        textArea.setBracketMatchingEnabled(true);
        textArea.setTabsEmulated(true);
        textArea.setTabSize(4);

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.getGutter().setLineNumberFont(new Font("Monospaced", Font.PLAIN, 14));
        textArea.setMargin(new Insets(3, 16, 3, 3));
        add(scrollPane, BorderLayout.CENTER);

        applyEditorTheme();
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));

        // Block KeymapWrapper's Ctrl+P → print via "none" in WHEN_FOCUSED InputMap.
        // JDK 17 JTextComponent.setKeymap() inserts a KeymapWrapper into the InputMap chain,
        // which returns the Action directly (not a String name), bypassing ActionMap overrides.
        // "none" shadows the KeymapWrapper binding so processKeyBinding returns false,
        // allowing the menu accelerator in MainFrame to fire for global search.
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control P"), "none");

        InputMap im = textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = textArea.getActionMap();
        im.put(KeyStroke.getKeyStroke("control F"), "showSearch");
        am.put("showSearch", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { showSearch(); }
        });
        im.put(KeyStroke.getKeyStroke("control R"), "showReplace");
        am.put("showReplace", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { showReplace(); }
        });
        im.put(KeyStroke.getKeyStroke("F3"), "findNext");
        am.put("findNext", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { findNext(); }
        });
        im.put(KeyStroke.getKeyStroke("shift F3"), "findPrev");
        am.put("findPrev", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { findPrev(); }
        });

        // Right-click popup with format
        JPopupMenu popup = textArea.getPopupMenu();
        if (popup == null) {
            popup = new JPopupMenu();
        }
        popup.addSeparator();
        JMenuItem fmtItem = new JMenuItem("\u683C\u5F0F\u5316");
        fmtItem.addActionListener(e -> {
            if (onFormat != null) onFormat.run();
        });
        popup.add(fmtItem);
        textArea.setComponentPopupMenu(popup);

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { markModified(); rebuildSegments(); installSegmentPainter(); updateSegmentBar(); repaint(); }
            @Override public void removeUpdate(DocumentEvent e) { markModified(); rebuildSegments(); installSegmentPainter(); updateSegmentBar(); repaint(); }
            @Override public void changedUpdate(DocumentEvent e) { markModified(); rebuildSegments(); installSegmentPainter(); updateSegmentBar(); repaint(); }
        });

        textArea.addCaretListener(e -> {
            if (segmentPainterTag == null) installSegmentPainter();
            updateSegmentBar();
            repaint();
        });
        installSegmentPainter();
    }

    private void markModified() {
        if (!modified) {
            modified = true;
            if (onModifiedChange != null) onModifiedChange.run();
        }
    }

    private void onConnChanged() {
        String display = (String) connCombo.getSelectedItem();
        if (display != null && !display.isEmpty()) {
            ConnectionInfo ci = connDisplayMap.get(display);
            if (ci != null) {
                connectionName = ci.getName();
                updateTxButtons();
                loadSchemasFromCache();
                if (onConnectionChange != null) onConnectionChange.run();
                return;
            }
        }
        connectionName = null;
        updateTxButtons();
        if (onConnectionChange != null) onConnectionChange.run();
    }

    private void loadSchemasFromCache() {
        if (connectionName == null) return;
        MetadataCache cache = MetadataCache.getInstance();
        List<String> schemas = cache.getSchemas(connectionName);
        String current = schemaCombo.getSelectedItem() != null ? schemaCombo.getSelectedItem().toString() : null;
        schemaCombo.removeAllItems();
        if (schemas != null && !schemas.isEmpty()) {
            for (String s : schemas) schemaCombo.addItem(s);
        }
        if (current != null) schemaCombo.setSelectedItem(current);
    }

    private void toggleAutoCommit() {
        if (connectionName == null) return;
        boolean auto = autoCommitCheck.isSelected();
        connectionManager.setAutoCommit(connectionName, auto);
        updateTxButtons();
    }

    private void doCommit() {
        if (connectionName == null) return;
        connectionManager.commit(connectionName);
    }

    private void doRollback() {
        if (connectionName == null) return;
        connectionManager.rollback(connectionName);
    }

    public void updateTxButtons() {
        boolean enabled = connectionName != null && !connectionManager.isAutoCommit(connectionName);
        commitBtn.setEnabled(enabled);
        rollbackBtn.setEnabled(enabled);
    }

    private static String displayName(ConnectionInfo ci) {
        if (ci.isUseUrl() && ci.getRawJdbcUrl() != null && !ci.getRawJdbcUrl().isEmpty()) {
            return ci.getName() + " [URL]";
        }
        String host = ci.getHost() != null && !ci.getHost().isEmpty() ? ci.getHost() : "?";
        int port = ci.getPort();
        return ci.getName() + " [" + host + ":" + (port > 0 ? port : "?") + "]";
    }

    public void setConnections(List<ConnectionInfo> conns) {
        connDisplayMap.clear();
        connCombo.removeAllItems();
        connCombo.addItem("");
        for (ConnectionInfo ci : conns) {
            String display = displayName(ci);
            connDisplayMap.put(display, ci);
            connCombo.addItem(display);
        }
        // Restore selection after repopulate
        if (connectionName != null && !connectionName.isEmpty()) {
            for (Map.Entry<String, ConnectionInfo> e : connDisplayMap.entrySet()) {
                if (connectionName.equals(e.getValue().getName())) {
                    connCombo.setSelectedItem(e.getKey());
                    break;
                }
            }
        }
    }

    public void setConnectionName(String name) {
        this.connectionName = name;
        // Try to select in combo (combo might already have items or gets populated later)
        for (Map.Entry<String, ConnectionInfo> e : connDisplayMap.entrySet()) {
            if (name.equals(e.getValue().getName())) {
                connCombo.setSelectedItem(e.getKey());
                break;
            }
        }
        updateTxButtons();
        loadSchemasFromCache();
    }

    public String getConnectionName() { return connectionName; }

    public void setSchema(String schema) {
        if (schema != null && !schema.isEmpty()) {
            // Check if already in list, otherwise add it (combo is editable)
            boolean found = false;
            for (int i = 0; i < schemaCombo.getItemCount(); i++) {
                if (schema.equals(schemaCombo.getItemAt(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) schemaCombo.addItem(schema);
            schemaCombo.setSelectedItem(schema);
        }
    }

    public String getSchema() {
        Object sel = schemaCombo.getSelectedItem();
        return sel != null ? sel.toString() : null;
    }

    public void setAutoCommit(boolean auto) { autoCommitCheck.setSelected(auto); }
    public boolean isAutoCommit() { return autoCommitCheck.isSelected(); }

    public void addCaretListener(CaretListener listener) {
        textArea.addCaretListener(listener);
    }

    public String getText() { return textArea.getText(); }
    public void setText(String text) { textArea.setText(text); }

    public String getSelectedText() {
        String sel = textArea.getSelectedText();
        return (sel != null && !sel.isBlank()) ? sel : null;
    }

    public int getSelectionStartLine() {
        try { return 1 + textArea.getLineOfOffset(textArea.getSelectionStart()); } catch (BadLocationException e) { return -1; }
    }

    public int getLastExecLine() { return lastExecLine; }

    public void markExecResult(int line, boolean success) {
        lastExecLine = line;
        lastExecSuccess = success;
        if (line < 0) return;
        try {
            int start = textArea.getLineStartOffset(line - 1);
            int len = textArea.getDocument().getLength();
            int end = Math.min(start + 1, len);
            if (end <= start) return;
            Object tag = textArea.getHighlighter().addHighlight(start, end, new ExecResultPainter(line, success));
            execTags.add(tag);
            textArea.repaint();
        } catch (BadLocationException ignored) {}
    }

    public void clearExecResults() {
        for (Object tag : execTags) {
            textArea.getHighlighter().removeHighlight(tag);
        }
        execTags.clear();
        textArea.repaint();
    }

    public void replaceSelection(String text) {
        if (textArea.getSelectedText() != null) {
            textArea.replaceSelection(text);
        } else {
            textArea.setText(text);
        }
    }

    public void navigateToLine(int line) {
        if (line < 1) return;
        try {
            int offset = textArea.getLineStartOffset(line - 1);
            textArea.setCaretPosition(offset);
            textArea.requestFocusInWindow();
        } catch (javax.swing.text.BadLocationException ignored) {
        }
    }

    public int getCaretLine() {
        try {
            return 1 + textArea.getLineOfOffset(textArea.getCaretPosition());
        } catch (javax.swing.text.BadLocationException e) {
            return 1;
        }
    }

    public RSyntaxTextArea getTextArea() { return textArea; }

    public void showSearch() { searchPanel.showSearch(textArea); }
    public void showReplace() { searchPanel.showReplace(textArea); }
    public void findNext() { searchPanel.findNext(); }
    public void findPrev() { searchPanel.findPrev(); }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public boolean isModified() { return modified; }
    public void resetModified() {
        modified = false;
        if (onModifiedChange != null) onModifiedChange.run();
    }

    public void setOnModifiedChange(Runnable r) { this.onModifiedChange = r; }
    public void setOnExecute(Runnable r) { this.onExecute = r; }
    public void setOnFormat(Runnable r) { this.onFormat = r; }
    public void setOnConnectionChange(Runnable r) { this.onConnectionChange = r; }

    public String getFileName() {
        if (filePath != null) return new File(filePath).getName();
        return null;
    }

    public String getTabTitle() {
        String cn = getFileName();
        if (cn == null) cn = tabName;
        if (connectionName != null && !connectionName.isEmpty()) {
            int lastAt = cn.lastIndexOf(" @");
            if (lastAt >= 0) {
                String after = cn.substring(lastAt + 2);
                // safety: connection names are simple identifiers (no '.', ' ', '/')
                boolean looksLikeMarker = after.matches("[a-zA-Z0-9_\\-]+");
                if (looksLikeMarker) {
                    boolean multiple = cn.indexOf(" @") != lastAt;
                    if (!multiple && after.equals(connectionName)) {
                        return cn;
                    }
                    int firstAt = cn.indexOf(" @");
                    cn = cn.substring(0, firstAt);
                }
            }
            cn = cn + " @" + connectionName;
        }
        return cn;
    }

    public String getCurrentStatement() {
        int[] seg = findCurrentSegment();
        if (seg == null) return null;
        try { lastExecLine = 1 + textArea.getLineOfOffset(seg[0]); } catch (BadLocationException ignored) {}
        String t = textArea.getText();
        return t.substring(seg[0], seg[1]).trim();
    }

    private int[] findCurrentSegment() {
        if (cachedSegments.isEmpty()) return null;
        int caret = Math.min(textArea.getCaretPosition(), textArea.getDocument().getLength());
        for (int[] s : cachedSegments) { if (caret >= s[0] && caret <= s[1]) return s; }
        int[] prev = null;
        for (int[] s : cachedSegments) { if (s[1] <= caret) prev = s; }
        return prev != null ? prev : cachedSegments.get(cachedSegments.size() - 1);
    }

    private void rebuildSegments() {
        String text = textArea.getText();
        cachedSegments = new ArrayList<>();
        segmentBoxCacheKey = null;
        if (text == null || text.isEmpty()) return;
        int segStart = 0;
        boolean hasContent = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ';') {
                if (hasContent) {
                    int s = segStart;
                    while (s < i && Character.isWhitespace(text.charAt(s))) s++;
                    cachedSegments.add(new int[]{s, i});
                }
                segStart = i + 1;
                hasContent = false;
            } else if (!Character.isWhitespace(ch)) {
                hasContent = true;
            }
        }
        if (hasContent) {
            int s = segStart;
            while (s < text.length() && Character.isWhitespace(text.charAt(s))) s++;
            cachedSegments.add(new int[]{s, text.length()});
        }
    }

    private void installSegmentPainter() {
        if (segmentPainterTag != null) {
            textArea.getHighlighter().removeHighlight(segmentPainterTag);
            segmentPainterTag = null;
        }
        int len = textArea.getDocument().getLength();
        if (len > 0) {
            try {
                segmentPainterTag = textArea.getHighlighter().addHighlight(0, len, segmentPainter);
            } catch (BadLocationException ignored) {}
        }
    }

    private class DynamicSegmentPainter extends DefaultHighlighter.DefaultHighlightPainter {
        private final com.kylin.plsql.core.config.ThemeManager thm = com.kylin.plsql.core.config.ThemeManager.getInstance();
        DynamicSegmentPainter() { super(new Color(0, true)); }
        @Override
        public Shape paintLayer(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c, View view) {
            if (textArea.getSelectedText() != null && !textArea.getSelectedText().isBlank()) return bounds;
            int[] seg = findCurrentSegment();
            if (seg == null) return bounds;
            if (offs1 <= seg[0] || offs0 >= seg[1]) return bounds;
            java.awt.Rectangle lineRect = bounds instanceof java.awt.Rectangle r2 ? r2 : bounds.getBounds();
            Graphics2D g2 = (Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            java.awt.Rectangle box = getSegmentBox(seg);
            if (box == null) { g2.dispose(); return bounds; }
            int hlY = Math.max(lineRect.y, box.y);
            int hlBottom = Math.min(lineRect.y + lineRect.height, box.y + box.height);
            if (hlY >= hlBottom) { g2.dispose(); return bounds; }
            g2.setColor(thm.resolve("exec.highlight"));
            g2.fillRect(box.x, hlY, box.width, hlBottom - hlY);
            boolean isFirst = offs0 <= seg[0] && offs1 > seg[0];
            boolean isLast = offs0 < seg[1] && offs1 >= seg[1];
            g2.setColor(thm.resolve("accent.tab"));
            g2.setStroke(new java.awt.BasicStroke(0.5f));
            // Left edge
            g2.drawLine(box.x, hlY, box.x, hlBottom);
            // Right edge at widest position
            g2.drawLine(box.x + box.width, hlY, box.x + box.width, hlBottom);
            // Top edge
            if (isFirst) g2.drawLine(box.x, box.y, box.x + box.width, box.y);
            // Bottom edge
            if (isLast) g2.drawLine(box.x, box.y + box.height, box.x + box.width, box.y + box.height);
            g2.dispose();
            return bounds;
        }
    }

    private java.awt.Rectangle getSegmentBox(int[] seg) {
        if (segmentBoxCacheKey != null && segmentBoxCacheKey[0] == seg[0] && segmentBoxCacheKey[1] == seg[1]) {
            return segmentBoxCache;
        }
        segmentBoxCacheKey = seg;
        segmentBoxCache = computeSegmentBox(seg);
        return segmentBoxCache;
    }

    private java.awt.Rectangle computeSegmentBox(int[] seg) {
        try {
            int segEnd = Math.min(seg[1], textArea.getDocument().getLength());
            if (segEnd <= seg[0]) return null;
            int startLine = textArea.getLineOfOffset(seg[0]);
            int endLine = textArea.getLineOfOffset(segEnd - 1);
            java.awt.Rectangle firstRect = textArea.modelToView(textArea.getLineStartOffset(startLine));
            if (firstRect == null) return null;
            int rightX = 0;
            for (int line = startLine; line <= endLine; line++) {
                int lineStart = textArea.getLineStartOffset(line);
                int lineEnd = Math.min(textArea.getLineEndOffset(line), segEnd);
                if (lineEnd > lineStart) {
                    java.awt.Rectangle r = textArea.modelToView(lineEnd - 1);
                    if (r != null) rightX = Math.max(rightX, r.x + r.width);
                }
            }
            if (rightX <= 0) rightX = firstRect.x + 1;
            int lastPos = Math.max(segEnd - 1, textArea.getLineStartOffset(endLine));
            java.awt.Rectangle lastRect = textArea.modelToView(lastPos);
            if (lastRect == null) return null;
            return new java.awt.Rectangle(firstRect.x - 1, firstRect.y, rightX - firstRect.x + 1, (lastRect.y + lastRect.height) - firstRect.y);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateSegmentBar() {
        textArea.setActiveLineRange(-1, -1);
    }

    private class ExecResultPainter extends DefaultHighlighter.DefaultHighlightPainter {
        private final com.kylin.plsql.core.config.ThemeManager thm = com.kylin.plsql.core.config.ThemeManager.getInstance();
        private final int line;
        private final boolean success;
        ExecResultPainter(int line, boolean success) {
            super(new Color(0, true));
            this.line = line;
            this.success = success;
        }
        @Override
        public Shape paintLayer(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c, View view) {
            try {
                if (textArea.getLineOfOffset(offs0) != line - 1) return bounds;
                int lineStart = textArea.getLineStartOffset(line - 1);
                if (offs0 != lineStart) return bounds;
            } catch (BadLocationException e) { return bounds; }
            try {
                Rectangle r = textArea.modelToView(textArea.getLineStartOffset(line - 1));
                if (r == null) return bounds;
                int dot = 8;
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(success ? thm.resolve("exec.success") : thm.resolve("exec.fail"));
                int dx = Math.max(2, r.x - dot - 2);
                g2.fillOval(dx, r.y + (r.height - dot) / 2, dot, dot);
                g2.dispose();
            } catch (BadLocationException ignored) {}
            return bounds;
        }
    }

    public void applyTheme() {
        applyEditorTheme();
        Color tb = theme.resolve("bg.toolbar");
        toolBar.setBackground(tb);
        topWrapper.setBackground(tb);
        Color fg = theme.resolve("fg.main");
        hoverBg = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60);
        commitBtn.setForeground(fg);
        rollbackBtn.setForeground(fg);
    }

    private void styleBtn(JButton b) {
        b.setContentAreaFilled(true);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (hoverBg != null) {
                    b.setBackground(hoverBg);
                    b.repaint();
                }
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(new Color(0, 0, 0, 0));
                b.repaint();
            }
        });
    }

    private void applyEditorTheme() {
        String path = com.kylin.plsql.core.config.ThemeManager.getInstance().getCurrentTheme().config("rsta.theme");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in != null) { Theme.load(in).apply(textArea); afterThemeApplied(); return; }
        } catch (Exception ignored) {}
        try (InputStream in = RSyntaxTextArea.class.getResourceAsStream(path)) {
            if (in != null) { Theme.load(in).apply(textArea); afterThemeApplied(); }
        } catch (Exception e) { log.warn("Load RSTA theme failed: {}", path, e); }
    }

    private void afterThemeApplied() {
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        textArea.setMargin(new Insets(3, 16, 3, 3));
        if (scrollPane != null && scrollPane.getGutter() != null) {
            org.fife.ui.rtextarea.Gutter gutter = scrollPane.getGutter();
            gutter.setLineNumberFont(new Font("Monospaced", Font.PLAIN, 14));
            gutter.setBackground(theme.resolve("bg.editor"));
            gutter.setLineNumberColor(theme.resolve("fg.secondary"));
        }
    }

    private static JButton flatBtn(String text, String tip, java.awt.event.ActionListener action) {
        JButton btn = new JButton(text);
        if (tip != null) btn.setToolTipText(tip);
        btn.setFocusable(false);
        btn.setContentAreaFilled(false);
        if (action != null) btn.addActionListener(action);
        return btn;
    }


}