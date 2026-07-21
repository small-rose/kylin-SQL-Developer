package com.kylin.plsql.ui.component.center;

import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.db.SqlExecutor;
import com.kylin.plsql.ui.component.common.PlSqlCompletionProvider;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Database source code viewer with method navigation and spec/body tabs. */
public class SourceViewerPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(SourceViewerPanel.class);
    private final ThemeManager theme = ThemeManager.getInstance();
    private final RSyntaxTextArea textArea;
    private final ConnectionManager connectionManager;
    private final String connName;
    private final String schema;
    private final String objectName;
    private final String objectType;
    private final SqlExecutor executor = new SqlExecutor();
    private final JLabel statusLabel = new JLabel(" ");

    private Font defaultFont;
    private boolean editable;
    private boolean fileMode;
    private String specSource;
    private String bodySource;
    private boolean showingBody;

    private List<MethodInfo> specMethods = new ArrayList<>();
    private List<MethodInfo> bodyMethods = new ArrayList<>();

    private final JToggleButton specBtn;
    private final JToggleButton bodyBtn;
    private final ButtonGroup tabGroup = new ButtonGroup();
    private final JButton compileBtn;
    private final JButton editBtn;
    private final JPanel outputPanel;
    private final JTextArea outputArea;

    private Runnable onSourceChanged;
    public void setOnSourceChanged(Runnable r) { this.onSourceChanged = r; }

    /** 重置缩放到默认字体大小 (Ctrl+0)。 */
    private void resetZoom() {
        if (defaultFont != null) {
            textArea.setFont(defaultFont);
            if (scrollPane != null && scrollPane.getGutter() != null) {
                scrollPane.getGutter().setLineNumberFont(defaultFont);
            }
        }
    }

    private RTextScrollPane scrollPane;

    /** 缩放一步 (Ctrl+= / Ctrl+-)。delta=1 放大，delta=-1 缩小。 */
    private void zoomBy(int delta) {
        Font f = textArea.getFont();
        int newSize = Math.max(6, Math.min(120, f.getSize() + delta));
        textArea.setFont(f.deriveFont((float) newSize));
        if (scrollPane != null && scrollPane.getGutter() != null) {
            Font gf = scrollPane.getGutter().getLineNumberFont();
            if (gf != null) scrollPane.getGutter().setLineNumberFont(gf.deriveFont((float) newSize));
        }
    }

    private JList<String> methodList;
    private DefaultListModel<String> methodListModel;
    private JLabel header;
    private JPanel leftPanel;
    private JPanel outHeader;
    private JLabel outTitle;
    private final JPanel toolBar;
    private final JPanel leftBar;
    private final JPanel rightBar;
    private JScrollPane methodScroll;
    private JSplitPane split;

    private static class MethodInfo {
        final String name;
        final String signature;
        final int line;
        MethodInfo(String name, String signature, int line) {
            this.name = name;
            this.signature = signature;
            this.line = line;
        }
    }

    private static final Pattern METHOD_PATTERN =
            Pattern.compile("^\\s*(FUNCTION|PROCEDURE)\\s+(\\w+)\\b", Pattern.CASE_INSENSITIVE);

    private List<MethodInfo> parseMethods(String source) {
        List<MethodInfo> list = new ArrayList<>();
        if (source == null || source.isBlank()) return list;
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = METHOD_PATTERN.matcher(lines[i]);
            if (m.find()) {
                String name = m.group(2);
                String sig = lines[i].trim();
                list.add(new MethodInfo(name, sig, i + 1));
            }
        }
        return list;
    }

    private void refreshMethodList(String source, List<MethodInfo> methods) {
        if (methodListModel == null) return;
        methodListModel.clear();
        for (MethodInfo mi : methods) {
            methodListModel.addElement(mi.name);
        }
    }

    public SourceViewerPanel(ConnectionManager cm, String connName, String schema, String objectName, String objectType) {
        this(cm, connName, schema, objectName, objectType, null);
    }

    public SourceViewerPanel(ConnectionManager cm, String connName, String schema, String objectName, String objectType, String preloadedContent) {
        this.connectionManager = cm;
        this.connName = connName;
        this.schema = schema;
        this.objectName = objectName;
        this.objectType = "PACKAGE".equals(objectType) ? "PACKAGE" : objectType;
        this.showingBody = "PACKAGE_BODY".equals(objectType);
        this.fileMode = (preloadedContent != null);

        setLayout(new BorderLayout());

        // ── Top toolbar ──
        toolBar = new JPanel(new BorderLayout(0, 0));
        toolBar.setBackground(theme.resolve("bg.main"));
        leftBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        leftBar.setBackground(theme.resolve("bg.main"));
        String typeLabel = "PACKAGE".equals(this.objectType) ? "PACKAGE" :
                           "PACKAGE_BODY".equals(this.objectType) ? "PACKAGE BODY" : this.objectType;
        JLabel title = new JLabel(typeLabel + " " + schema + "." + objectName);
        title.setFont(FontManager.getInstance().resolve("font.top"));
        leftBar.add(title);
        leftBar.add(Box.createHorizontalStrut(12));

        if ("PACKAGE".equals(this.objectType)) {
            specBtn = new JToggleButton("Spec");
            bodyBtn = new JToggleButton("Body");
            for (var b : new JToggleButton[]{specBtn, bodyBtn}) {
                b.setFont(FontManager.getInstance().resolve("font.top"));
                b.setFocusable(false);
                b.setContentAreaFilled(false);
                b.setForeground(theme.resolve("fg.tab.inactive"));
                b.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                tabGroup.add(b);
            }
            specBtn.setSelected(!showingBody);
            bodyBtn.setSelected(showingBody);
            specBtn.addActionListener(e -> switchTab(false));
            bodyBtn.addActionListener(e -> switchTab(true));
            leftBar.add(specBtn);
            leftBar.add(bodyBtn);
            updateTabStyle();
        } else {
            specBtn = null;
            bodyBtn = null;
        }

        toolBar.add(leftBar, BorderLayout.WEST);

        rightBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        rightBar.setBackground(theme.resolve("bg.main"));
        editBtn = flatBtn("edit", "编辑", e -> toggleEdit());
        editBtn.setToolTipText("编辑");
        rightBar.add(editBtn);
        compileBtn = flatBtn("compile", "编译", e -> compile());
        compileBtn.setToolTipText("编译");
        rightBar.add(compileBtn);
        JButton resetZoomBtn = flatBtn("search-alert", "缩放", e -> resetZoom());
        resetZoomBtn.setToolTipText("重置缩放 (Ctrl+0)");
        rightBar.add(resetZoomBtn);
        toolBar.add(rightBar, BorderLayout.EAST);

        add(toolBar, BorderLayout.NORTH);

        // ── Editor ──
        textArea = new RSyntaxTextArea(25, 80);
        textArea.setSyntaxEditingStyle("text/plsql");
        textArea.setCodeFoldingEnabled(true);
        textArea.setTabsEmulated(true);
        textArea.setTabSize(4);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFractionalFontMetricsEnabled(true);
        textArea.setFont(FontManager.getInstance().resolve("font.editor"));
        textArea.setEditable(false);
        textArea.setMargin(new Insets(3, 16, 3, 3));
        textArea.setBackground(theme.resolve("bg.editor"));
        textArea.setForeground(theme.resolve("fg.main"));
        textArea.setCaretColor(theme.resolve("editor.caret"));
        textArea.setSelectionColor(theme.resolve("selection.bg"));
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

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.getGutter().setLineNumberFont(FontManager.getInstance().resolve("font.editor.lineNum"));

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

        String rstaPath = theme.getCurrentTheme().config("rsta.theme");
        try (var in = getClass().getClassLoader().getResourceAsStream(rstaPath)) {
            if (in != null) Theme.load(in).apply(textArea);
            else {
                try (var in2 = RSyntaxTextArea.class.getResourceAsStream(rstaPath)) {
                    if (in2 != null) Theme.load(in2).apply(textArea);
                }
            }
        } catch (Exception ignored) {}
        this.defaultFont = textArea.getFont();

        // ── Method navigation panel (left) ──
        methodListModel = new DefaultListModel<>();
        methodList = new JList<>(methodListModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() { return true; }
        };
        methodList.setFont(FontManager.getInstance().resolve("font.left"));
        methodList.setBackground(theme.resolve("bg.main"));
        methodList.setForeground(theme.resolve("list.fg"));
        methodList.setSelectionBackground(theme.resolve("selection.listBg"));
        methodList.setSelectionForeground(theme.resolve("selection.listFg"));
        methodList.setFixedCellHeight(22);
        methodList.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        methodList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = methodList.getSelectedIndex();
            if (idx < 0) return;
            List<MethodInfo> methods = showingBody ? bodyMethods : specMethods;
            if (idx >= 0 && idx < methods.size()) {
                try {
                    int off = textArea.getLineStartOffset(methods.get(idx).line - 1);
                    textArea.setCaretPosition(off);
                    Rectangle r = textArea.modelToView(off);
                    if (r != null) {
                        Rectangle visible = textArea.getVisibleRect();
                        int targetY = Math.max(0, r.y - visible.height / 3);
                        textArea.scrollRectToVisible(new Rectangle(r.x, targetY, r.width, visible.height));
                    }
                    textArea.requestFocusInWindow();
                } catch (Exception ignored) {}
            }
        });

        methodList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int idx,
                                                          boolean sel, boolean foc) {
                JLabel c = (JLabel) super.getListCellRendererComponent(list, value, idx, sel, foc);
                c.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 4));
                List<MethodInfo> methods = showingBody ? bodyMethods : specMethods;
                c.setToolTipText(idx >= 0 && idx < methods.size() ? methods.get(idx).signature : null);
                return c;
            }
        });
        ToolTipManager.sharedInstance().setInitialDelay(200);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(theme.resolve("bg.main"));
        JLabel header = new JLabel(" Methods");
        header.setFont(FontManager.getInstance().resolve("font.left.title"));
        header.setForeground(theme.resolve("fg.muted"));
        header.setOpaque(true);
        header.setBackground(theme.resolve("bg.main"));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.resolve("border.default")));
        header.setPreferredSize(new Dimension(0, 24));
        leftPanel.add(header, BorderLayout.NORTH);
        this.header = header;
        this.leftPanel = leftPanel;

        methodScroll = new JScrollPane(methodList);
        methodScroll.setBorder(BorderFactory.createEmptyBorder());
        methodScroll.setBackground(theme.resolve("bg.main"));
        leftPanel.add(methodScroll, BorderLayout.CENTER);

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, scrollPane);
        split.setResizeWeight(0.20);
        split.setDividerSize(3);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(theme.resolve("bg.main"));
        // 等 SplitPane 首次完成布局后再设比例位置，避免 Linux 上因初始 width=0 导致左面板折叠
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (split.getWidth() > 0) {
                    split.setDividerLocation(0.20);
                    split.removeComponentListener(this);
                }
            }
        });
        add(split, BorderLayout.CENTER);

        // ── Bottom output panel ──
        outputArea = new JTextArea(5, 0);
        outputArea.setFont(FontManager.getInstance().resolve("font.bottom.result"));
        outputArea.setBackground(theme.resolve("bg.output"));
        outputArea.setForeground(theme.resolve("fg.secondary"));
        outputArea.setEditable(false);
        JScrollPane outScroll = new JScrollPane(outputArea);
        outScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.resolve("border.light")));

        JPanel outHeader = new JPanel(new BorderLayout());
        outHeader.setBackground(theme.resolve("bg.toolbar"));
        JLabel outTitle = new JLabel("  Compile Output");
        outTitle.setFont(FontManager.getInstance().resolve("font.bottom.title"));
        outTitle.setForeground(theme.resolve("fg.muted"));
        outHeader.add(outTitle, BorderLayout.WEST);
        this.outHeader = outHeader;
        this.outTitle = outTitle;

        outputPanel = new JPanel(new BorderLayout());
        outputPanel.setVisible(false);
        outputPanel.add(outHeader, BorderLayout.NORTH);
        outputPanel.add(outScroll, BorderLayout.CENTER);

        add(outputPanel, BorderLayout.SOUTH);

        // ── Status bar ──
        statusLabel.setFont(FontManager.getInstance().resolve("font.status"));
        statusLabel.setForeground(theme.resolve("fg.muted"));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(statusLabel, BorderLayout.PAGE_END);

        applyTheme();
        if (preloadedContent != null) {
            fileMode = true;
            if (compileBtn != null) compileBtn.setVisible(false);
            if ("PACKAGE".equals(this.objectType) && splitPackageContent(preloadedContent)) {
            } else {
                specSource = preloadedContent;
                specMethods = parseMethods(preloadedContent);
                applyCurrentSource();
            }
            statusLabel.setText("Done");
        } else {
            loadSource();
        }

        PlSqlCompletionProvider provider = new PlSqlCompletionProvider(this::getConnName, this::getSchema);
        provider.setColumnLoader((schemaName, table) -> loadColumns(schemaName, table));
        AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoActivationEnabled(true);
        ac.setAutoCompleteEnabled(true);
        int delay = readAutocompleteDelay();
        ac.setAutoActivationDelay(delay);
        ac.install(textArea);
        log.info("AutoCompletion installed on SourceViewerPanel, delay={}ms, provider={}, editable={}", delay, provider.getClass().getSimpleName(), textArea.isEditable());

        installLinkNavigation();
    }

    private void installLinkNavigation() {
        textArea.setLinkGenerator((source, offset) -> {
            RSyntaxTextArea rsta = (RSyntaxTextArea) source;
            int line;
            try {
                line = rsta.getLineOfOffset(offset);
            } catch (BadLocationException e) {
                return null;
            }
            Token tokenList = rsta.getTokenListForLine(line);
            for (Token t = tokenList; t != null; t = t.getNextToken()) {
                if (t.isWhitespace() || t.isComment()) continue;
                if (offset < t.getOffset() || offset >= t.getEndOffset()) continue;
                String word = t.getLexeme();
                if (word == null || word.isBlank()) return null;
                List<MethodInfo> methods = showingBody ? bodyMethods : specMethods;
                for (MethodInfo m : methods) {
                    if (m.name.equalsIgnoreCase(word)) {
                        int destLine = m.line - 1;
                        int linkStart = t.getOffset();
                        try {
                            int destOffset = rsta.getLineStartOffset(destLine);
                            return new LinkGeneratorResult() {
                                @Override
                                public int getSourceOffset() {
                                    return linkStart;
                                }
                                @Override
                                public HyperlinkEvent execute() {
                                    rsta.setCaretPosition(destOffset);
                                    return null;
                                }
                            };
                        } catch (BadLocationException e) {
                            return null;
                        }
                    }
                }
                return null;
            }
            return null;
        });
        textArea.setHyperlinksEnabled(true);
    }

    private static int readAutocompleteDelay() {
        try { return Integer.parseInt(ConfigManager.getInstance().getPreference("autocomplete.delay", "300"));
        } catch (Exception e) { return 300; }
    }

    public void applyTheme() {
        Color mainBg = theme.resolve("bg.main");
        setBackground(mainBg);
        toolBar.setBackground(mainBg);
        leftBar.setBackground(mainBg);
        rightBar.setBackground(mainBg);
        if (leftPanel != null) leftPanel.setBackground(mainBg);
        if (header != null) {
            header.setOpaque(true);
            header.setBackground(mainBg);
            header.setForeground(theme.resolve("fg.muted"));
            header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.resolve("border.default")));
        }
        if (methodScroll != null) {
            methodScroll.getViewport().setBackground(mainBg);
            methodScroll.setBackground(mainBg);
        }
        if (split != null) split.setBackground(mainBg);
        methodList.setBackground(mainBg);
        methodList.setForeground(theme.resolve("list.fg"));
        methodList.setSelectionBackground(theme.resolve("selection.listBg"));
        methodList.setSelectionForeground(theme.resolve("selection.listFg"));
        textArea.setBackground(theme.resolve("bg.editor"));
        textArea.setForeground(theme.resolve("fg.main"));
        textArea.setCaretColor(theme.resolve("editor.caret"));
        textArea.setSelectionColor(theme.resolve("selection.bg"));
        statusLabel.setForeground(theme.resolve("fg.muted"));
        if (outHeader != null) outHeader.setBackground(theme.resolve("bg.toolbar"));
        if (outTitle != null) outTitle.setForeground(theme.resolve("fg.muted"));
        if (outputArea != null) {
            outputArea.setBackground(theme.resolve("bg.output"));
            outputArea.setForeground(theme.resolve("fg.secondary"));
        }
        // Re-apply RSTA theme to textArea
        String rstaPath = theme.getCurrentTheme().config("rsta.theme");
        try (var in = getClass().getClassLoader().getResourceAsStream(rstaPath)) {
            if (in != null) { org.fife.ui.rsyntaxtextarea.Theme.load(in).apply(textArea); }
            else {
                try (var in2 = org.fife.ui.rsyntaxtextarea.RSyntaxTextArea.class.getResourceAsStream(rstaPath)) {
                    if (in2 != null) org.fife.ui.rsyntaxtextarea.Theme.load(in2).apply(textArea);
                }
            }
        } catch (Exception ignored) {}
        updateTabStyle();
    }

    private void switchTab(boolean body) {
        if (body == showingBody) return;
        showingBody = body;
        if (editable) {
            if (body) specSource = textArea.getText();
            else bodySource = textArea.getText();
        }
        applyCurrentSource();
        if (methodListModel != null) methodList.clearSelection();
    }

    /** 切换到 Body（包体）显示。（供恢复工作空间时使用） */
    public void showBody() {
        if (bodyBtn != null) bodyBtn.doClick();
    }

    private void updateTabStyle() {
        Color active = theme.resolve("accent.tab");
        Color inactive = theme.resolve("fg.muted");
        Border activeBorder = BorderFactory.createMatteBorder(0, 0, 2, 0, active);
        Border inactiveBorder = BorderFactory.createEmptyBorder(2, 8, 2, 8);
        if (specBtn != null) {
            specBtn.setForeground(showingBody ? inactive : active);
            specBtn.setBorder(showingBody ? inactiveBorder : activeBorder);
        }
        if (bodyBtn != null) {
            bodyBtn.setForeground(showingBody ? active : inactive);
            bodyBtn.setBorder(showingBody ? activeBorder : inactiveBorder);
        }
    }

    private void toggleEdit() {
        editable = !editable;
        textArea.setEditable(editable);
        String iconName = editable ? "save-plus" : "edit";
        ImageIcon svgIcon = com.kylin.plsql.ui.component.common.IconUtil.loadButtonIcon(iconName, null);
        if (svgIcon != null) {
            editBtn.setIcon(svgIcon);
            editBtn.setText(null);
        } else {
            java.net.URL url = SourceViewerPanel.class.getResource("/icons/" + iconName + ".png");
            if (url != null) {
                editBtn.setIcon(new ImageIcon(url));
                editBtn.setText(null);
            } else {
                editBtn.setIcon(null);
                editBtn.setText(editable ? "S" : "E");
            }
        }
        editBtn.setToolTipText(editable ? "保存更改" : "切换编辑模式");
    }

    private void compile() {
        if (fileMode || connName == null) return;
        String source = textArea.getText();
        if (source == null || source.isBlank()) return;
        outputPanel.setVisible(true);
        outputArea.setText("");
        outputArea.append("Compiling " + schema + "." + objectName + "...\n");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try (Connection conn = connectionManager.getConnection(connName)) {
                    String fullType = showingBody ? "PACKAGE BODY" :
                                      "PACKAGE".equals(objectType) ? "PACKAGE" : objectType;
                    String compileSql = "ALTER " + fullType + " " + schema + "." + objectName + " COMPILE";
                    appendOutput("> " + compileSql + "\n");
                    SqlExecutor.SqlResult r = executor.execute(conn, compileSql);
                    if (r.isSuccess()) {
                        appendOutput("Compile succeeded\n");
                        reloadSource();
                    } else {
                        appendOutput("Compile failed: " + r.error + "\n");
                        fetchErrors(conn);
                    }
                } catch (Exception e) {
                    appendOutput("Connection failed: " + e.getMessage() + "\n");
                }
                return null;
            }
        }.execute();
    }

    private void fetchErrors(Connection conn) {
        try {
            String fullType = showingBody ? "PACKAGE BODY" :
                              "PACKAGE".equals(objectType) ? "PACKAGE" : objectType;
            String sql = "SELECT line, position, text FROM user_errors WHERE name = UPPER('"
                       + objectName.replace("'", "''") + "') AND type = '"
                       + fullType.replace("'", "''") + "' ORDER BY line, position";
            SqlExecutor.SqlResult er = executor.execute(conn, sql);
            if (er.isQuery && er.rows != null && !er.rows.isEmpty()) {
                appendOutput("\nError details:\n");
                for (var row : er.rows) {
                    String line = row.size() > 0 && row.get(0) != null ? row.get(0).toString() : "?";
                    String pos = row.size() > 1 && row.get(1) != null ? row.get(1).toString() : "?";
                    String msg = row.size() > 2 && row.get(2) != null ? row.get(2).toString() : "";
                    appendOutput("  LINE " + line + ":" + pos + "  " + msg + "\n");
                }
            } else {
                appendOutput("No error details\n");
            }
        } catch (Exception ignored) {}
    }

    private void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(text);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    private void applyCurrentSource() {
        if (showingBody && bodySource != null) {
            textArea.setText(bodySource);
            refreshMethodList(bodySource, bodyMethods);
        } else if (specSource != null) {
            textArea.setText(specSource);
            refreshMethodList(specSource, specMethods);
        }
        updateTabStyle();
        if (methodList != null) {
            methodList.revalidate();
            methodList.repaint();
        }
        if (onSourceChanged != null) onSourceChanged.run();
    }

    private void reloadSource() {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try (Connection conn = connectionManager.getConnection(connName)) {
                    if ("PACKAGE".equals(objectType)) {
                        String spec = executor.getSource(conn, schema, objectName, "PACKAGE");
                        String body = executor.getSource(conn, schema, objectName, "PACKAGE_BODY");
                        SwingUtilities.invokeLater(() -> {
                            if (spec != null && !spec.isBlank()) { specSource = spec; specMethods = parseMethods(spec); }
                            if (body != null && !body.isBlank()) { bodySource = body; bodyMethods = parseMethods(body); }
                            applyCurrentSource();
                        });
                    } else {
                        String src = executor.getSource(conn, schema, objectName, objectType);
                        if (src != null && !src.isBlank()) {
                            String finalSrc = src;
                            SwingUtilities.invokeLater(() -> {
                                specSource = finalSrc;
                                specMethods = parseMethods(finalSrc);
                                applyCurrentSource();
                            });
                        }
                    }
                } catch (Exception ignored) {}
                return null;
            }
        }.execute();
    }

    private void loadSource() {
        statusLabel.setText("Loading...");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try (Connection conn = connectionManager.getConnection(connName)) {
                    if ("PACKAGE".equals(objectType)) {
                        String spec = executor.getSource(conn, schema, objectName, "PACKAGE");
                        String body = executor.getSource(conn, schema, objectName, "PACKAGE_BODY");
                        SwingUtilities.invokeLater(() -> {
                            if (spec != null && !spec.isBlank()) { specSource = spec; specMethods = parseMethods(spec); }
                            if (body != null && !body.isBlank()) { bodySource = body; bodyMethods = parseMethods(body); }
                            applyCurrentSource();
                            statusLabel.setText("Done");
                        });
                    } else {
                        String src = executor.getSource(conn, schema, objectName, objectType);
                        if (src != null && !src.isBlank()) {
                            String finalSrc = src;
                            SwingUtilities.invokeLater(() -> {
                                specSource = finalSrc;
                                specMethods = parseMethods(finalSrc);
                                applyCurrentSource();
                                statusLabel.setText("Done");
                            });
                        }
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        textArea.setText("-- " + e.getMessage());
                        statusLabel.setText("Failed");
                    });
                }
                return null;
            }
        }.execute();
    }

    public RSyntaxTextArea getTextArea() { return textArea; }
    public String getTabTitle() { return fileMode ? objectName : schema + "." + objectName; }
    public String getConnName() { return connName; }
    public String getSchema() { return schema; }
    public String getObjectName() { return objectName; }
    public String getObjectType() { return objectType; }
    public boolean isShowingBody() { return showingBody; }
    public boolean isEditable() { return editable; }

    /** Load columns on demand (for auto-completion when cache miss). */
    private void loadColumns(String schemaName, String table) {
        if (connName == null) return;
        MetadataCache cache = MetadataCache.getInstance();
        try {
            String dbProduct = cache.getDbProduct(connName);
            String sql;
            if (dbProduct != null && (dbProduct.contains("mysql") || dbProduct.contains("mariadb"))) {
                sql = "SELECT column_name, data_type, character_maximum_length, column_comment FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
            } else if (dbProduct != null && (dbProduct.contains("postgresql") || dbProduct.contains("edb"))) {
                sql = "SELECT column_name, data_type, character_maximum_length, column_comment FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
            } else {
                sql = "SELECT c.column_name, c.data_type, c.data_length, cc.comments FROM all_tab_columns c LEFT JOIN all_col_comments cc ON cc.owner=c.owner AND cc.table_name=c.table_name AND cc.column_name=c.column_name WHERE c.owner = ? AND c.table_name = ? ORDER BY c.column_id";
            }
            if (!connectionManager.isConnected(connName)) return;
            try (Connection conn = connectionManager.getConnection(connName);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schemaName);
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
                cache.putColumns(connName, schemaName, table, cols);
            }
        } catch (Exception e) {
            log.warn("loadColumns {} {} failed: {}", schemaName, table, e.getMessage());
        }
    }

    private static JButton flatBtn(String iconName, String fallback, java.awt.event.ActionListener action) {
        JButton btn = new JButton();
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusable(false);
        btn.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
        btn.addActionListener(action);
        ImageIcon svgIcon = com.kylin.plsql.ui.component.common.IconUtil.loadButtonIcon(iconName, null);
        if (svgIcon != null) {
            btn.setIcon(svgIcon);
        } else {
            java.net.URL url = SourceViewerPanel.class.getResource("/icons/" + iconName + ".png");
            if (url != null) {
                btn.setIcon(new ImageIcon(url));
            } else {
                btn.setText(fallback);
            }
        }
        return btn;
    }

    /** 匹配 PACKAGE BODY 起始位置（忽略大小写，支持 CREATE [OR REPLACE] [EDITIONABLE|NONEDITIONABLE]） */
    private static final Pattern BODY_MATCH = Pattern.compile(
        "(?i)(?:CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:EDITIONABLE\\s+|NONEDITIONABLE\\s+)?)?PACKAGE\\s+BODY\\b",
        Pattern.MULTILINE);

    /**
     * 将合并的包头+包体文件拆分为 specSource / bodySource。
     * 拆分点取文件最后出现的 PACKAGE BODY，并将之前的全部内容作为包头。
     */
    private boolean splitPackageContent(String content) {
        Matcher m = BODY_MATCH.matcher(content);
        int split = -1;
        while (m.find()) split = m.start();
        if (split < 0) return false;
        String spec = content.substring(0, split).trim();
        String body = content.substring(split).trim();
        if (spec.isEmpty() || body.isEmpty()) return false;
        specSource = spec;
        bodySource = body;
        specMethods = parseMethods(spec);
        bodyMethods = parseMethods(body);
        applyCurrentSource();
        return true;
    }
}
