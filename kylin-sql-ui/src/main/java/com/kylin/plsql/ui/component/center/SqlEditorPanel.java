package com.kylin.plsql.ui.component.center;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.ui.component.common.PlSqlCompletionProvider;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
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
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** SQL editor panel with connection toolbar, auto-commit, segment highlighting. */
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
    private Runnable onAppendExecute;
    private Runnable onFormat;
    private Runnable onConnectionChange;
    private Consumer<String> onStatusMessage;
    private String connectionName;
    private final Map<String, ConnectionInfo> connDisplayMap = new LinkedHashMap<>();

    private final JComboBox<String> connCombo;
    private final JComboBox<String> schemaCombo;
    private final JToggleButton autoTxBtn;
    private final JButton commitBtn;
    private final JButton rollbackBtn;
    private Color hoverBg;
    private Runnable onHistoryRequest;

    private final RTextScrollPane scrollPane;
    private final SearchReplacePanel searchPanel = new SearchReplacePanel();
    private Font defaultFont;
    private final JPanel toolBar;
    private final JPanel topWrapper;
    private final List<Object> execTags = new ArrayList<>();
    private int lastExecLine = -1;
    private boolean lastExecSuccess;
    private List<int[]> cachedSegments = java.util.Collections.emptyList();

    public SqlEditorPanel(ConnectionManager cm, String tabName) {
        this.connectionManager = cm;
        this.tabName = tabName != null ? tabName : "sql";
        setLayout(new BorderLayout());

        toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        JButton execBtn = flatBtn("execute", "执行", "执行 (F8)", e -> { if (onExecute != null) onExecute.run(); });
        execBtn.setForeground(new Color(0x5CB85C));
        styleBtn(execBtn);
        toolBar.add(execBtn);

        JButton appendExecBtn = flatBtn("append", "追加", "追加执行 (F9)", e -> { if (onAppendExecute != null) onAppendExecute.run(); });
        appendExecBtn.setForeground(new Color(0x3D8B3D));
        styleBtn(appendExecBtn);
        toolBar.add(appendExecBtn);

        JButton historyBtn = flatBtn("history", "历史", "执行历史", e -> { if (onHistoryRequest != null) onHistoryRequest.run(); });
        styleBtn(historyBtn);
        toolBar.add(historyBtn);

        toolBar.add(new JLabel(" 连接:"));

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

        autoTxBtn = new JToggleButton("Tx: Auto");
        autoTxBtn.setSelected(true);
        autoTxBtn.setFocusable(false);
        autoTxBtn.setFont(autoTxBtn.getFont().deriveFont(11f));
        autoTxBtn.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        autoTxBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        styleBtn(autoTxBtn);
        autoTxBtn.addActionListener(e -> {
            boolean auto = autoTxBtn.isSelected();
            autoTxBtn.setText(auto ? "Tx: Auto" : "Tx: Manual");
            connectionManager.setAutoCommit(connectionName, auto);
            updateTxButtons();
        });
        toolBar.add(autoTxBtn);

        commitBtn = flatBtn("commit", "提交", "提交 (Commit)", e -> doCommit());
        commitBtn.setForeground(new Color(0x5CB85C));
        styleBtn(commitBtn);
        commitBtn.setEnabled(false);
        toolBar.add(commitBtn);

        rollbackBtn = flatBtn("rollback", "回滚", "回滚 (Rollback)", e -> doRollback());
        rollbackBtn.setForeground(new Color(0xD9534F));
        styleBtn(rollbackBtn);
        rollbackBtn.setEnabled(false);
        toolBar.add(rollbackBtn);

        JButton resetZoomBtn = flatBtn("search-alert", "缩放", "重置缩放 (Ctrl+0)", e -> resetZoom());
        toolBar.add(resetZoomBtn);

        topWrapper = new JPanel(new BorderLayout());
        searchPanel.setVisible(false);
        topWrapper.add(toolBar, BorderLayout.NORTH);
        topWrapper.add(searchPanel, BorderLayout.SOUTH);
        add(topWrapper, BorderLayout.NORTH);

        textArea = new RSyntaxTextArea(25, 80);
        textArea.setSyntaxEditingStyle("text/plsql");
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

        // Ctrl+滚轮缩放（监听 scrollPane 而非 textArea，避免干扰内置滚动）
        scrollPane.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                int rot = e.getWheelRotation();
                Font f = textArea.getFont();
                int newSize = Math.max(6, Math.min(120, f.getSize() - rot));
                textArea.setFont(f.deriveFont((float) newSize));
                if (scrollPane.getGutter() != null) {
                    Font gf = scrollPane.getGutter().getLineNumberFont();
                    if (gf != null) scrollPane.getGutter().setLineNumberFont(gf.deriveFont((float) newSize));
                }
                e.consume();
            }
        });

        textArea.setMargin(new Insets(3, 16, 3, 3));
        add(scrollPane, BorderLayout.CENTER);

        applyEditorTheme();
        defaultFont = textArea.getFont();

        // Block KeymapWrapper's Ctrl+P → print via "none" in WHEN_FOCUSED InputMap.
        // JDK 17 JTextComponent.setKeymap() inserts a KeymapWrapper into the InputMap chain,
        // which returns the Action directly (not a String name), bypassing ActionMap overrides.
        // "none" shadows the KeymapWrapper binding so processKeyBinding returns false,
        // allowing the menu accelerator in MainFrame to fire for global search.
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control P"), "none");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control 0"), "resetZoom");
        textArea.getActionMap().put("resetZoom", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { resetZoom(); }
        });
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control EQUALS"), "zoomIn");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control MINUS"), "zoomOut");
        textArea.getActionMap().put("zoomIn", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { zoomBy(1); }
        });
        textArea.getActionMap().put("zoomOut", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { zoomBy(-1); }
        });

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
        JMenuItem fmtItem = new JMenuItem("格式化");
        fmtItem.setIcon(com.kylin.plsql.ui.component.common.IconUtil.menuIcon("format"));
        fmtItem.addActionListener(e -> {
            if (onFormat != null) onFormat.run();
        });
        popup.add(fmtItem);
        textArea.setComponentPopupMenu(popup);

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { markModified(); rebuildSegments(); updateSegmentBar(); repaint(); }
            @Override public void removeUpdate(DocumentEvent e) { markModified(); rebuildSegments(); updateSegmentBar(); repaint(); }
            @Override public void changedUpdate(DocumentEvent e) { markModified(); rebuildSegments(); updateSegmentBar(); repaint(); }
        });

        textArea.addCaretListener(e -> {
            updateSegmentBar();
            repaint();
        });

        PlSqlCompletionProvider provider = new PlSqlCompletionProvider(this::getConnectionName, this::getSchema);
        provider.setColumnLoader((schema, table) -> loadColumns(schema, table));
        final AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoActivationEnabled(true);
        ac.setAutoCompleteEnabled(true);
        int delay = readAutocompleteDelay();
        ac.setAutoActivationDelay(delay);
        ac.install(textArea);
        log.info("AutoCompletion installed on SqlEditorPanel, delay={}ms, provider={}, autoActivation={}, autoComplete={}",
            delay, provider.getClass().getSimpleName(), ac.isAutoActivationEnabled(), ac.isAutoCompleteEnabled());

        textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                if (e.getLength() != 1) return;
                char c;
                try { c = e.getDocument().getText(e.getOffset(), 1).charAt(0); }
                catch (Exception ex) { return; }
                if (c == '.') {
                    if (isInsideComment(textArea)) return;
                    log.info("dot detected, calling doCompletion()");
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        try { ac.doCompletion(); } catch (Exception ex) { log.error("doCompletion failed", ex); }
                    });
                } else if (Character.isLetter(c)) {
                    // 手动触发自动补全（AutoActivationListener 因未知原因不触发）
                    log.info("letter '{}' inserted, calling doCompletion()", c);
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        try { ac.doCompletion(); } catch (Exception ex) { log.error("doCompletion failed", ex); }
                    });
                }
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {}
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
    }

    /** Check if the caret is inside a comment token. */
    private static boolean isInsideComment(RSyntaxTextArea textArea) {
        try {
            int caret = textArea.getCaretPosition();
            int line = textArea.getLineOfOffset(caret);
            Token t = textArea.getTokenListForLine(line);
            while (t != null && t.isPaintable()) {
                if (t.containsPosition(caret)) {
                    return t.isComment();
                }
                t = t.getNextToken();
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static int readAutocompleteDelay() {
        try { return Integer.parseInt(ConfigManager.getInstance().getPreference("autocomplete.delay", "300"));
        } catch (Exception e) { return 300; }
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

    private void doCommit() {
        if (connectionName == null) return;
        try {
            connectionManager.commit(connectionName);
            if (onStatusMessage != null) onStatusMessage.accept("提交成功");
        } catch (Exception ex) {
            log.error("commit failed", ex);
            if (onStatusMessage != null) onStatusMessage.accept("提交失败: " + ex.getMessage());
        }
    }

    private void doRollback() {
        if (connectionName == null) return;
        try {
            connectionManager.rollback(connectionName);
            if (onStatusMessage != null) onStatusMessage.accept("回滚成功");
        } catch (Exception ex) {
            log.error("rollback failed", ex);
            if (onStatusMessage != null) onStatusMessage.accept("回滚失败: " + ex.getMessage());
        }
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

    public void setAutoCommit(boolean auto) {
        autoTxBtn.setSelected(auto);
        autoTxBtn.setText(auto ? "Tx: Auto" : "Tx: Manual");
    }
    public boolean isAutoCommit() { return autoTxBtn.isSelected(); }

    public void addCaretListener(CaretListener listener) {
        textArea.addCaretListener(listener);
    }

    public String getText() { return textArea.getText(); }
    public void setText(String text) { textArea.setText(text); }

    /** Return the first fully visible line index (0-based). */
    public int getScrollLine() {
        try {
            JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, textArea);
            if (vp == null) return 0;
            Rectangle r = vp.getViewRect();
            return textArea.getLineOfOffset(textArea.viewToModel(new Point(r.x, r.y)));
        } catch (Exception e) {
            return 0;
        }
    }

    public int getCaretPosition() { return textArea.getCaretPosition(); }

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
    public void setModified(boolean m) { modified = m; }
    public void resetModified() {
        modified = false;
    }

    public void setOnModifiedChange(Runnable r) { this.onModifiedChange = r; }
    public void setOnExecute(Runnable r) { this.onExecute = r; }
    public void setOnAppendExecute(Runnable r) { this.onAppendExecute = r; }
    public void setOnFormat(Runnable r) { this.onFormat = r; }
    public void setOnConnectionChange(Runnable r) { this.onConnectionChange = r; }
    public void setOnStatusMessage(Consumer<String> c) { this.onStatusMessage = c; }
    public void setOnHistoryRequest(Runnable r) { this.onHistoryRequest = r; }

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
        commitBtn.setForeground(new Color(0x5CB85C));
        rollbackBtn.setForeground(new Color(0xD9534F));
    }

    private void styleBtn(AbstractButton b) {
        b.setContentAreaFilled(true);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (hoverBg != null) { b.setBackground(hoverBg); b.setContentAreaFilled(true); b.setOpaque(true); b.repaint(); }
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setContentAreaFilled(false); b.setOpaque(false); b.repaint();
            }
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                Color c = b.getBackground();
                b.setBackground(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, c.getAlpha() + 40)));
                b.repaint();
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                b.setBackground(hoverBg); b.repaint();
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

    /** Apply FontManager overrides on top of RSTA theme — font, color, comment styles. */
    private void afterThemeApplied() {
        FontManager fm = FontManager.getInstance();
        textArea.setFont(fm.resolve("font.editor"));
        // Apply editor color override (if user has set one)
        Color editorColor = fm.resolveColor("font.editor");
        if (editorColor != null) textArea.setForeground(editorColor);
        textArea.setMargin(new Insets(3, 16, 3, 3));
        // Set comment font and color separately
        Font cf = fm.resolve("font.editor.comment");
        Color cc = fm.resolveColor("font.editor.comment");
        SyntaxScheme scheme = textArea.getSyntaxScheme();
        for (int type : new int[]{TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE, TokenTypes.COMMENT_DOCUMENTATION}) {
            if (scheme.getStyle(type) != null) {
                scheme.getStyle(type).font = cf;
                if (cc != null) scheme.getStyle(type).foreground = cc;
            }
        }
        textArea.setSyntaxScheme(scheme);
        if (scrollPane != null && scrollPane.getGutter() != null) {
            org.fife.ui.rtextarea.Gutter gutter = scrollPane.getGutter();
            gutter.setLineNumberFont(FontManager.getInstance().resolve("font.editor"));
            gutter.setBackground(theme.resolve("bg.editor"));
            gutter.setLineNumberColor(theme.resolve("fg.secondary"));
        }
    }

    /** Load columns on demand (for auto-completion when cache miss). */
    private void loadColumns(String schema, String table) {
        if (connectionName == null) return;
        MetadataCache cache = MetadataCache.getInstance();
        try {
            String dbProduct = cache.getDbProduct(connectionName);
            String sql;
            if (dbProduct != null && (dbProduct.contains("mysql") || dbProduct.contains("mariadb"))) {
                sql = "SELECT column_name, data_type, character_maximum_length, column_comment FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
            } else if (dbProduct != null && (dbProduct.contains("postgresql") || dbProduct.contains("edb"))) {
                sql = "SELECT column_name, data_type, character_maximum_length, column_comment FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
            } else {
                sql = "SELECT c.column_name, c.data_type, c.data_length, cc.comments FROM all_tab_columns c LEFT JOIN all_col_comments cc ON cc.owner=c.owner AND cc.table_name=c.table_name AND cc.column_name=c.column_name WHERE c.owner = ? AND c.table_name = ? ORDER BY c.column_id";
            }
            if (!connectionManager.isConnected(connectionName)) {
                for (Map.Entry<String, ConnectionInfo> e : connDisplayMap.entrySet()) {
                    if (connectionName.equals(e.getValue().getName())) {
                        try {
                            connectionManager.connect(e.getValue());
                        } catch (SQLException ex) {
                            log.warn("自动补全连接失败: {}", ex.getMessage());
                        }
                        break;
                    }
                }
            }
            try (Connection conn = connectionManager.getConnection(connectionName);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                ps.setString(2, table);
                List<MetadataCache.CachedColumn> cols = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MetadataCache.CachedColumn cc = new MetadataCache.CachedColumn();
                        cc.name = rs.getString(1);
                        cc.type = rs.getString(2);
                        cc.size = rs.getInt(3);
                        cc.comment = rs.getString(4);
                        if (cc.name != null) cols.add(cc);
                    }
                }
                cache.putColumns(connectionName, schema, table, cols);
            }
        } catch (Exception e) {
            log.warn("loadColumns {} {} failed: {}", schema, table, e.getMessage());
        }
    }

    /** 重置缩放到默认字体大小 (Ctrl+0)。 */
    private void resetZoom() {
        if (defaultFont != null) {
            textArea.setFont(defaultFont);
            if (scrollPane.getGutter() != null) {
                scrollPane.getGutter().setLineNumberFont(defaultFont);
            }
        }
    }

    /** 缩放一步 (Ctrl+= / Ctrl+-)。delta=1 放大，delta=-1 缩小。 */
    private void zoomBy(int delta) {
        Font f = textArea.getFont();
        int newSize = Math.max(6, Math.min(120, f.getSize() + delta));
        textArea.setFont(f.deriveFont((float) newSize));
        if (scrollPane.getGutter() != null) {
            Font gf = scrollPane.getGutter().getLineNumberFont();
            if (gf != null) scrollPane.getGutter().setLineNumberFont(gf.deriveFont((float) newSize));
        }
    }

    private static JButton flatBtn(String iconName, String fallback, String tip, java.awt.event.ActionListener action) {
        JButton btn = new JButton();
        if (tip != null) btn.setToolTipText(tip);
        btn.setFocusable(false);
        if (action != null) btn.addActionListener(action);
        ImageIcon svgIcon = com.kylin.plsql.ui.component.common.IconUtil.loadButtonIcon(iconName, null);
        if (svgIcon != null) {
            btn.setIcon(svgIcon);
        } else {
            java.net.URL url = SqlEditorPanel.class.getResource("/icons/" + iconName + ".png");
            if (url != null) {
                btn.setIcon(new ImageIcon(url));
            } else {
                btn.setText(fallback);
            }
        }
        return btn;
    }


}