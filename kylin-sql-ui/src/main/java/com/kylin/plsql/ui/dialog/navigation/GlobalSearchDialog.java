package com.kylin.plsql.ui.dialog.navigation;

import com.kylin.plsql.ui.dialog.common.BaseToolDialog;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.db.SqlExecutor;
import com.kylin.plsql.ui.component.center.SourceViewerPanel;
import com.kylin.plsql.ui.component.center.SqlEditorPanel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Global search dialog for searching across editors, metadata objects, and saved files */
public class GlobalSearchDialog extends JDialog {

    private static class SearchResult {
        final SqlEditorPanel editor;
        final String filePath;
        final int line;
        final String lineText;
        final boolean isMetadata;
        final String connName;
        final String schema;
        final String objectType;
        final String objectName;
        final String owner;

        SearchResult(SqlEditorPanel e, int l, String t) {
            editor = e; filePath = null; line = l; lineText = t;
            isMetadata = false;
            connName = null; schema = null; objectType = null; objectName = null;
            owner = null;
        }

        SearchResult(String c, String s, String t, String o) {
            editor = null; filePath = null; line = 0; lineText = null;
            isMetadata = true;
            connName = c; schema = s; objectType = t; objectName = o;
            owner = s;
        }

        SearchResult(String fp, int l, String t) {
            editor = null; filePath = fp; line = l; lineText = t;
            isMetadata = false;
            connName = null; schema = null; objectType = null; objectName = null;
            owner = null;
        }
    }

    @FunctionalInterface
    interface MatchConsumer {
        void accept(int line, String lineText);
    }

    private static final Icon ICON_FILE = makeIcon("F", new Color(0x337AB7));
    private static final Icon ICON_TABLE = makeIcon("T", new Color(0x337AB7));
    private static final Icon ICON_VIEW = makeIcon("V", new Color(0x5BC0DE));
    private static final Icon ICON_PROC = makeIcon("P", new Color(0xD9534F));
    private static final Icon ICON_FUNC = makeIcon("F", new Color(0xD9534F));
    private static final Icon ICON_PKG = makeIcon("K", new Color(0xA0522D));
    private static final Icon ICON_TRIGGER = makeIcon("R", new Color(0xF0AD4E));

    private static Icon makeIcon(String text, Color bg) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(1, 1, 14, 14, 3, 3);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        int x = (16 - fm.stringWidth(text)) / 2;
        int y = (16 + fm.getAscent()) / 2 - 1;
        g.drawString(text, x, y);
        g.dispose();
        return new ImageIcon(img);
    }

    private static Icon iconForObjectType(String type) {
        return switch (type) {
            case "TABLE" -> ICON_TABLE;
            case "VIEW" -> ICON_VIEW;
            case "PROCEDURE" -> ICON_PROC;
            case "FUNCTION" -> ICON_FUNC;
            case "PACKAGE" -> ICON_PKG;
            case "TRIGGER" -> ICON_TRIGGER;
            default -> ICON_FILE;
        };
    }

    private static class ResultRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (c instanceof JLabel label && value instanceof SearchResult sr) {
                if (sr.isMetadata) {
                    label.setIcon(iconForObjectType(sr.objectType));
                    label.setText(sr.objectType + "  " + sr.owner + "." + sr.objectName);
                } else if (sr.filePath != null) {
                    label.setIcon(ICON_FILE);
                    label.setText(sr.filePath + ":" + sr.line);
                } else {
                    label.setIcon(ICON_FILE);
                    label.setText("Line " + sr.line + ": " + sr.lineText);
                }
            }
            return c;
        }
    }

    private static final String[] OBJECT_TYPES = {"TABLE", "VIEW", "PROCEDURE", "FUNCTION", "PACKAGE", "TRIGGER"};
    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();
    static {
        for (String t : OBJECT_TYPES) TYPE_LABELS.put(t, t);
    }

    private final JTabbedPane editorTabs;
    private final ConnectionManager connManager;
    private final List<SqlEditorPanel> editors = new ArrayList<>();
    private final JTextField searchField = new JTextField(30);
    private final DefaultListModel<SearchResult> listModel = new DefaultListModel<>();
    private final JList<SearchResult> resultsList = new JList<>(listModel);
    private final RSyntaxTextArea previewArea = new RSyntaxTextArea();
    private final JLabel statusLabel = new JLabel(" ");
    private final JToggleButton matchCaseBtn = new JToggleButton("Aa");
    private final JToggleButton wordsBtn = new JToggleButton("\"W\"");
    private final JToggleButton regexBtn = new JToggleButton(".*");
    private final JLabel searchIcon = new JLabel();
    private JScrollPane previewScroll;
    private boolean searchAllTabs = true;
    private final Set<String> enabledTypes = new HashSet<>(Arrays.asList(OBJECT_TYPES));
    private boolean filtersActive = false;
    private String currentQuery = "";
    private final SqlExecutor executor = new SqlExecutor();
    private final Highlighter.HighlightPainter highlightPainter =
        new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFFFF00));
    private SwingWorker<String, Void> previewWorker;
    private final ConfigManager configManager;
    private final Consumer<String> fileOpener;
    private final JToggleButton pinBtn = new JToggleButton("📍");
    private boolean pinned;
    private JPanel top;
    private JPanel topRight;
    private JScrollPane listScroll;
    private JSplitPane split;

    public GlobalSearchDialog(Frame owner, JTabbedPane editorTabs, ConnectionManager connManager,
                              ConfigManager configManager, Consumer<String> fileOpener) {
        super(owner, "全局搜索", false);
        this.editorTabs = editorTabs;
        this.connManager = connManager;
        this.configManager = configManager;
        this.fileOpener = fileOpener;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(owner);
        pinned = false;

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!pinned) dispose();
            }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                if (!pinned) dispose();
            }
        });

        setLayout(new BorderLayout());

        top = new JPanel(new BorderLayout(4, 0));
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));
        top.setOpaque(true);

        searchIcon.setText("🔍");
        searchIcon.setFont(searchIcon.getFont().deriveFont(16f));
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        searchIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));
        searchIcon.setToolTipText("点击设置搜索范围");
        searchIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showFilterPopup();
            }
        });
        top.add(searchIcon, BorderLayout.WEST);

        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateSelected();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && !pinned) {
                    dispose();
                } else {
                    scheduleSearch();
                }
            }
        });
        top.add(searchField, BorderLayout.CENTER);

        topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        topRight.setOpaque(true);

        matchCaseBtn.setFocusable(false);
        matchCaseBtn.setToolTipText("区分大小写");
        matchCaseBtn.addActionListener(e -> scheduleSearch());
        topRight.add(matchCaseBtn);

        wordsBtn.setFocusable(false);
        wordsBtn.setToolTipText("全词匹配");
        wordsBtn.addActionListener(e -> scheduleSearch());
        topRight.add(wordsBtn);

        regexBtn.setFocusable(false);
        regexBtn.setToolTipText("正则表达式");
        regexBtn.addActionListener(e -> scheduleSearch());
        topRight.add(regexBtn);

        topRight.add(pinBtn);
        pinBtn.setFocusable(false);
        pinBtn.addActionListener(e -> {
            pinned = pinBtn.isSelected();
            pinBtn.setText(pinned ? "📌" : "📍");
        });
        top.add(topRight, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);

        resultsList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultsList.setCellRenderer(new ResultRenderer());
        resultsList.setBackground(UIManager.getColor("Panel.background"));
        resultsList.getSelectionModel().addListSelectionListener(this::onSelectionChanged);
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) navigateSelected();
            }
        });
        resultsList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) navigateSelected();
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && !pinned) dispose();
            }
        });

        listScroll = new JScrollPane(resultsList);
        listScroll.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        listScroll.getViewport().setOpaque(true);
        listScroll.setOpaque(true);

        previewArea.setSyntaxEditingStyle("text/plsql");
        previewArea.setEditable(false);
        previewArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        previewArea.setCodeFoldingEnabled(false);
        previewArea.setHighlightCurrentLine(false);
        previewArea.setLineWrap(false);

        previewScroll = new JScrollPane(previewArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        previewScroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, 6, 4, 6),
            BorderFactory.createLineBorder(new Color(0xCCCCCC))));
        previewScroll.setPreferredSize(new Dimension(100, 120));

        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listScroll, previewScroll);
        split.setResizeWeight(0.7);
        add(split, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        add(statusLabel, BorderLayout.SOUTH);

        syncTheme();
        ThemeManager.getInstance().addListener(() -> {
            int divLoc = split != null ? split.getDividerLocation() : -1;
            SwingUtilities.updateComponentTreeUI(GlobalSearchDialog.this);
            syncTheme();
            if (divLoc > 0 && split != null) {
                SwingUtilities.invokeLater(() -> split.setDividerLocation(divLoc));
            }
            repaint();
        });
    }

    private void showFilterPopup() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JLabel header = new JLabel(" 搜索范围");
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        header.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        popup.add(header);

        JCheckBoxMenuItem allTabsItem = new JCheckBoxMenuItem("全部标签页", searchAllTabs);
        JCheckBoxMenuItem curTabItem = new JCheckBoxMenuItem("当前标签页", !searchAllTabs);
        allTabsItem.addActionListener(e -> {
            searchAllTabs = true;
            allTabsItem.setSelected(true);
            curTabItem.setSelected(false);
            scheduleSearch();
        });
        curTabItem.addActionListener(e -> {
            searchAllTabs = false;
            allTabsItem.setSelected(false);
            curTabItem.setSelected(true);
            scheduleSearch();
        });
        popup.add(allTabsItem);
        popup.add(curTabItem);
        popup.addSeparator();

        for (String type : OBJECT_TYPES) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(TYPE_LABELS.get(type), enabledTypes.contains(type));
            item.addActionListener(e -> {
                if (item.isSelected()) enabledTypes.add(type);
                else enabledTypes.remove(type);
                updateFilterIndicator();
                scheduleSearch();
            });
            popup.add(item);
        }

        popup.addSeparator();
        JMenuItem selectAllItem = new JMenuItem("全选");
        selectAllItem.addActionListener(e -> {
            enabledTypes.addAll(Arrays.asList(OBJECT_TYPES));
            updateFilterIndicator();
            scheduleSearch();
            popup.setVisible(false);
        });
        popup.add(selectAllItem);

        JMenuItem invertItem = new JMenuItem("反选");
        invertItem.addActionListener(e -> {
            Set<String> all = new HashSet<>(Arrays.asList(OBJECT_TYPES));
            enabledTypes.clear();
            for (String t : all) {
                if (!enabledTypes.contains(t)) enabledTypes.add(t);
            }
            updateFilterIndicator();
            scheduleSearch();
            popup.setVisible(false);
        });
        popup.add(invertItem);

        popup.show(searchIcon, 0, searchIcon.getHeight());
    }

    private void updateFilterIndicator() {
        filtersActive = enabledTypes.size() < OBJECT_TYPES.length;
        searchIcon.setForeground(filtersActive ? new Color(0xFF6600) : UIManager.getColor("Label.foreground"));
    }

    private javax.swing.Timer searchTimer;

    private void scheduleSearch() {
        if (searchTimer != null) searchTimer.stop();
        searchTimer = new javax.swing.Timer(300, e -> doSearch());
        searchTimer.setRepeats(false);
        searchTimer.start();
    }

    private void doSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty() || query.equals(currentQuery)) {
            if (!query.isEmpty() && query.equals(currentQuery)) return;
        }
        currentQuery = query;
        listModel.clear();

        if (query.isEmpty()) {
            statusLabel.setText("请输入搜索关键字");
            updateTitle(0);
            return;
        }

        boolean caseSensitive = matchCaseBtn.isSelected();
        boolean wholeWords = wordsBtn.isSelected();
        boolean regex = regexBtn.isSelected();

        int editorMatches = 0, metadataMatches = 0, fileMatches = 0;
        int editorFileCount = 0, fileCount = 0;

        MetadataCache cache = MetadataCache.getInstance();
        Component selectedComp = editorTabs.getSelectedComponent();
        String curSchema = null;
        if (selectedComp instanceof SqlEditorPanel ep) curSchema = ep.getSchema();
        else if (selectedComp instanceof SourceViewerPanel svp) curSchema = svp.getSchema();
        boolean hasSchema = curSchema != null && !curSchema.isEmpty();

        for (SqlEditorPanel ed : editors) {
            if (!searchAllTabs && ed != selectedComp) continue;
            String text = ed.getTextArea().getText();
            int matched = searchText(text, query, caseSensitive, wholeWords, regex, (line, lineText) -> {
                listModel.addElement(new SearchResult(ed, line, lineText.trim()));
            });
            if (matched > 0) {
                editorMatches += matched;
                editorFileCount++;
            }
        }

        if (!enabledTypes.isEmpty() && hasSchema) {
            String searchQuery = caseSensitive ? query : query.toLowerCase();
            for (String curConn : connManager.getActiveConnections()) {
                if (!cache.hasMetadata(curConn)) continue;
                for (String type : OBJECT_TYPES) {
                    if (!enabledTypes.contains(type)) continue;
                    List<String> objects = cache.getObjects(curConn, curSchema, type);
                    if (objects == null) continue;
                    for (String obj : objects) {
                        String matchText = caseSensitive ? obj : obj.toLowerCase();
                        if (matchText.contains(searchQuery)) {
                            if (wholeWords && !isWholeWord(obj, 0, obj.length() - 1, query)) continue;
                            listModel.addElement(new SearchResult(curConn, curSchema, type, obj));
                            metadataMatches++;
                        }
                    }
                }
            }
        }

        // ── File search runs in background ──
        int editorMatchesFinal = editorMatches;
        int editorFileCountFinal = editorFileCount;
        int metadataMatchesFinal = metadataMatches;
        new SwingWorker<int[], Void>() {
            @Override
            protected int[] doInBackground() {
                int fm = 0, fc = 0;
                if (configManager != null) {
                    for (ConfigManager.SavedFileRecord rec : configManager.loadFileRecords()) {
                        if (rec.filePath == null) continue;
                        try {
                            String content = new String(Files.readAllBytes(Paths.get(rec.filePath)), StandardCharsets.UTF_8);
                            int matched = searchText(content, query, caseSensitive, wholeWords, regex, (line, lineText) -> {});
                            if (matched > 0) {
                                fm += matched;
                                fc++;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                return new int[]{fm, fc};
            }
            @Override
            protected void done() {
                try {
                    int[] fc = get();
                    int fileMatches2 = fc[0], fileCount2 = fc[1];
                    StringBuilder sb = new StringBuilder();
                    if (editorMatchesFinal > 0) {
                        sb.append("找到 ").append(editorMatchesFinal).append(" 个匹配，分布在 ").append(editorFileCountFinal).append(" 个文件中");
                    }
                    if (metadataMatchesFinal > 0) {
                        if (sb.length() > 0) sb.append(" ｜ ");
                        sb.append("找到 ").append(metadataMatchesFinal).append(" 个对象");
                    }
                    if (fileMatches2 > 0) {
                        if (sb.length() > 0) sb.append(" ｜ ");
                        sb.append("找到 ").append(fileMatches2).append(" 个匹配，分布在 ").append(fileCount2).append(" 个存储文件中");
                    }
                    if (sb.length() == 0) {
                        sb.append("无匹配");
                    }
                    statusLabel.setText(sb.toString());

                    int total = editorMatchesFinal + metadataMatchesFinal + fileMatches2;
                    updateTitle(total);
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private int searchText(String text, String query, boolean caseSensitive,
                           boolean wholeWords, boolean regex, MatchConsumer consumer) {
        int count = 0;
        String searchText = caseSensitive ? text : text.toLowerCase();
        String searchQuery = caseSensitive ? query : query.toLowerCase();
        String[] lines = searchText.split("\n", -1);

        if (regex) {
            try {
                Pattern p = Pattern.compile(query, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
                for (int i = 0; i < lines.length; i++) {
                    if (p.matcher(lines[i]).find()) {
                        String originalLine = text.split("\n", -1)[i];
                        consumer.accept(i + 1, originalLine);
                        count++;
                    }
                }
            } catch (PatternSyntaxException ignored) {}
            return count;
        }

        for (int i = 0; i < lines.length; i++) {
            int idx = lines[i].indexOf(searchQuery);
            if (idx >= 0) {
                if (wholeWords && !isWholeWord(lines[i], idx, idx + searchQuery.length() - 1, searchQuery)) continue;
                String originalLine = text.split("\n", -1)[i];
                consumer.accept(i + 1, originalLine);
                count++;
            }
        }
        return count;
    }

    private boolean isWholeWord(String text, int start, int end, String word) {
        if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) return false;
        if (end < text.length() - 1 && Character.isLetterOrDigit(text.charAt(end + 1))) return false;
        return true;
    }

    private void onSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        showPreview();
    }

    private void showPreview() {
        SearchResult sr = resultsList.getSelectedValue();
        if (sr == null) {
            previewArea.setText("");
            return;
        }
        if (previewWorker != null && !previewWorker.isDone()) {
            previewWorker.cancel(true);
        }
        previewWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                if (sr.isMetadata) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("-- ").append(sr.objectType).append(" ").append(sr.owner).append(".").append(sr.objectName).append("\n");
                    if (connManager != null) {
                        try (var conn = connManager.getConnection(sr.connName);
                             var stmt = conn.createStatement();
                             var rs = stmt.executeQuery(
                                "SELECT DBMS_METADATA.GET_DDL('" + sr.objectType + "', '" + sr.objectName + "', '" + sr.schema + "') FROM dual")) {
                            if (rs.next()) sb.append(rs.getString(1));
                        } catch (Exception ignored) {}
                    }
                    return sb.toString();
                } else if (sr.editor != null) {
                    String[] lines = sr.editor.getTextArea().getText().split("\n", -1);
                    int from = Math.max(0, sr.line - 5);
                    int to = Math.min(lines.length, sr.line + 4);
                    StringBuilder sb = new StringBuilder();
                    for (int i = from; i < to; i++) {
                        String prefix = (i + 1 == sr.line) ? ">> " : "   ";
                        sb.append(prefix).append(String.format("%4d", i + 1)).append(": ").append(lines[i]).append("\n");
                    }
                    return sb.toString();
                } else if (sr.filePath != null) {
                    try {
                        String[] lines = new String(Files.readAllBytes(Paths.get(sr.filePath))).split("\n", -1);
                        int from = Math.max(0, sr.line - 3);
                        int to = Math.min(lines.length, sr.line + 2);
                        StringBuilder sb = new StringBuilder();
                        for (int i = from; i < to; i++) {
                            sb.append(lines[i]).append("\n");
                        }
                        return sb.toString();
                    } catch (Exception e) {
                        return "// " + sr.filePath + ":" + sr.line + "\n" + sr.lineText;
                    }
                }
                return sr.lineText != null ? sr.lineText : "";
            }
            @Override
            protected void done() {
                try {
                    previewArea.setText(get());
                    previewArea.setCaretPosition(0);
                } catch (Exception ignored) {}
            }
        };
        previewWorker.execute();
    }

    private void navigateSelected() {
        SearchResult sr = resultsList.getSelectedValue();
        if (sr == null) return;
        if (sr.editor != null) {
            sr.editor.getTextArea().requestFocusInWindow();
            try {
                int offset = sr.editor.getTextArea().getLineStartOffset(sr.line - 1);
                sr.editor.getTextArea().setCaretPosition(offset);
            } catch (Exception ignored) {}
            dispose();
        } else if (sr.isMetadata && fileOpener != null) {
            fileOpener.accept(sr.owner + "." + sr.objectName);
            dispose();
        }
    }

    public void collectEditors() {
        editors.clear();
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component c = editorTabs.getComponentAt(i);
            if (c instanceof SqlEditorPanel ep) editors.add(ep);
        }
    }

    public JTextField getSearchField() { return searchField; }

    public void showDialog() {
        collectEditors();
        searchField.setText("");
        listModel.clear();
        statusLabel.setText(" ");
        previewArea.setText("");
        currentQuery = "";
        pinned = false;
        pinBtn.setSelected(false);
        pinBtn.setText("📍");
        pinBtn.setToolTipText("固定面板");
        updateTitle(0);
        syncTheme();
        searchField.requestFocusInWindow();
        setVisible(true);
    }

    private void updateTitle(int count) {
        if (count > 0) setTitle("全局搜索 (" + count + ")");
        else setTitle("全局搜索");
    }

    private void syncTheme() {
        ThemeManager tm = ThemeManager.getInstance();
        Color panelBg = tm.resolve("bg.panel");
        Color fg = tm.resolve("fg.main");
        Color fieldBg = tm.resolve("bg.main");
        Color fieldFg = tm.resolve("fg.main");
        Color selectBg = tm.resolve("selection.bg");
        Color selectFg = tm.resolve("selection.fg");

        if (panelBg != null) {
            top.setBackground(panelBg);
            topRight.setBackground(panelBg);
        }
        if (fg != null) {
            top.setForeground(fg);
            topRight.setForeground(fg);
            searchIcon.setForeground(fg);
        }
        if (fieldBg != null) searchField.setBackground(fieldBg);
        if (fieldFg != null) {
            searchField.setForeground(fieldFg);
            searchField.setCaretColor(fieldFg);
        }
        matchCaseBtn.setBackground(panelBg);
        matchCaseBtn.setForeground(fg);
        wordsBtn.setBackground(panelBg);
        wordsBtn.setForeground(fg);
        regexBtn.setBackground(panelBg);
        regexBtn.setForeground(fg);
        pinBtn.setBackground(panelBg);
        pinBtn.setForeground(fg);

        statusLabel.setForeground(fg);

        if (listScroll != null) {
            listScroll.setBackground(panelBg);
            listScroll.getViewport().setBackground(panelBg);
        }
        resultsList.setBackground(panelBg);
        resultsList.setForeground(fg);
        resultsList.setSelectionBackground(selectBg);
        resultsList.setSelectionForeground(selectFg);

        if (split != null) split.setBackground(panelBg);

        if (previewArea != null) {
            try {
                String path = tm.getCurrentTheme().config("rsta.theme");
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                    if (in != null) Theme.load(in).apply(previewArea);
                }
                try (InputStream in = RSyntaxTextArea.class.getResourceAsStream(path)) {
                    if (in != null) Theme.load(in).apply(previewArea);
                }
            } catch (Exception ignored) {}
            previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        }
        if (previewScroll != null) {
            Color bg = previewArea.getBackground();
            previewScroll.setBackground(bg);
            previewScroll.getViewport().setBackground(bg);
            Color borderColor = tm.resolve("border.light");
            if (borderColor == null) borderColor = UIManager.getColor("Label.foreground");
            if (borderColor != null) borderColor = borderColor.darker();
            previewScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 6, 4, 6),
                BorderFactory.createLineBorder(borderColor)));
        }
    }
}
