package com.kylin.plsql.ui;

import com.kylin.plsql.core.cache.MetadataCache;
import com.kylin.plsql.core.config.AppTheme;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ConfigManager.TabState;
import com.kylin.plsql.core.config.ConfigManager.WorkspaceState;
import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;
import com.kylin.plsql.core.db.SqlExecutor;
import com.kylin.plsql.core.db.SqlHistory;
import com.kylin.plsql.core.service.ServiceFactory;
import com.kylin.plsql.core.format.EngineManager;
import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.SqlFormatterEngine;
import com.kylin.plsql.core.format.dialect.DialectManager;
import com.kylin.plsql.core.format.dialect.SqlDialect;
import com.kylin.plsql.core.parser.PlSqlCallHierarchy;
import com.kylin.plsql.core.parser.PlSqlNavigator;
import com.kylin.plsql.core.parser.PlSqlSymbolIndex;
import com.kylin.plsql.ui.component.bottom.BottomPanel;
import com.kylin.plsql.ui.component.bottom.BottomPanel.TabInfo;
import com.kylin.plsql.ui.component.bottom.StatusBar;
import com.kylin.plsql.ui.component.center.SourceViewerPanel;
import com.kylin.plsql.ui.component.center.SqlEditorPanel;
import com.kylin.plsql.ui.component.center.WelcomePanel;
import com.kylin.plsql.ui.component.common.IconUtil;
import com.kylin.plsql.ui.component.common.ToastManager;
import com.kylin.plsql.ui.component.left.LeftPanel;
import com.kylin.plsql.ui.component.left.LocalFileBrowser;
import com.kylin.plsql.ui.component.left.ObjectBrowser;
import com.kylin.plsql.ui.component.right.RightPanel;
import com.kylin.plsql.ui.dialog.common.AboutDialog;
import com.kylin.plsql.ui.dialog.connection.ConnectionDialog;
import com.kylin.plsql.ui.dialog.navigation.CallHierarchyDialog;
import com.kylin.plsql.ui.dialog.navigation.GlobalSearchDialog;
import com.kylin.plsql.ui.dialog.settings.SettingsDialog;
import com.kylin.plsql.ui.dialog.tools.AdvancedExportDialog;
import com.kylin.plsql.ui.dialog.tools.DataGeneratorDialog;
import com.kylin.plsql.ui.dialog.tools.ObjectSearchDialog;
import com.kylin.plsql.ui.dialog.tools.RegexTesterDialog;
import com.kylin.plsql.ui.dialog.tools.SqlHistoryDialog;
import com.kylin.plsql.ui.dialog.tools.SqlToolsDialog;
import com.kylin.plsql.ui.dialog.tools.TextDiffDialog;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Main application window with menu bar, toolbars, editor tabs, and status bar. */
public class MainFrame extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

    private final ConfigManager configManager;
    private final ConnectionManager connectionManager;
    private ServiceFactory serviceFactory;

    private LeftPanel leftPanel;
    private ObjectBrowser objectBrowser;
    private RightPanel rightPanel;
    private BottomPanel bottomPanel;
    private LocalFileBrowser fileBrowser;
    private final JSplitPane[] leftSplitRef = new JSplitPane[1];
    private final JSplitPane[] mainSplitRef = new JSplitPane[1];
    private JPanel bottomWrapper;
    private JSplitPane verticalSplit;
    private JPanel editorPanel;
    private JTabbedPane editorTabs;
    private WelcomePanel welcomePanel;
    private StatusBar statusBar;

    private GlobalSearchDialog globalSearchDialog;

    private final FormatOptions formatOptions = new FormatOptions();
    private final SqlHistory sqlHistory = new SqlHistory();
    private final PlSqlSymbolIndex symbolIndex = new PlSqlSymbolIndex();
    /** 启动完成后延迟恢复 tab 连接/schema 的暂存数据（索引与 editorTabs 对齐）。 */
    private java.util.List<ConfigManager.TabState> pendingTabStates;
    private int consoleCounter;
    private Timer autoSaveTimer;
    private JToolBar toolbar;
    private JMenuBar menuBar;
    private boolean caseModeIsUpper = true;
    private JButton caseBtn;
    private JComboBox<SqlFormatterEngine> engineCombo;

    // ── tab context menu support ──
    private JPanel tabContainer;
    private JTabbedPane secondaryTabs;
    private JSplitPane editorSplit;
    private static final java.util.List<ClosedTabInfo> recentlyClosed = new java.util.ArrayList<>();
    private static final int MAX_RECENTLY_CLOSED = 15;
    private final java.util.Set<Component> pinnedTabs = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final java.util.List<TabInfo> closedTabs = new java.util.ArrayList<>();
    private final java.util.Map<Component, Boolean> editorLockStates = new java.util.IdentityHashMap<>();

    private static class ClosedTabInfo {
        String title, content, connName, schema, filePath;
        ClosedTabInfo(String title, String content, String connName, String schema, String filePath) {
            this.title = title; this.content = content;
            this.connName = connName; this.schema = schema; this.filePath = filePath;
        }
    }

    public MainFrame(ConfigManager configManager) {
        this.configManager = configManager;
        this.connectionManager = new ConnectionManager();
        this.serviceFactory = new ServiceFactory(connectionManager);
        consoleCounter = 1;
        EngineManager.initEngines(formatOptions);
        initComponents();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveWorkspace();
                System.exit(0);
            }
        });
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        java.util.List<Image> icons = new java.util.ArrayList<>();
        // title bar: kylin_32x32.png directly
        java.net.URL icon32 = MainFrame.class.getResource("/logo/kylin_32x32.png");
        if (icon32 != null) {
            try {
                BufferedImage img = javax.imageio.ImageIO.read(icon32);
                if (img != null) icons.add(img);
            } catch (java.io.IOException ignored) {}
        }
        // taskbar/alt-tab: kylin_512x512.png at multiple sizes
        java.net.URL icon512 = MainFrame.class.getResource("/logo/kylin_512x512.png");
        if (icon512 != null) {
            try {
                BufferedImage src = javax.imageio.ImageIO.read(icon512);
                if (src != null) {
                    int[] sizes = {48, 64, 128, 256};
                    for (int s : sizes) {
                        BufferedImage scaled = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = scaled.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(src, 0, 0, s, s, null);
                        g.dispose();
                        icons.add(scaled);
                    }
                }
            } catch (java.io.IOException ignored) {}
        }
        if (!icons.isEmpty()) setIconImages(icons);
    }

    /** kylin_512x512.png 裁边 + 放大 2x，去除内边距使 logo 更饱满。 */
    public static MainFrame buildUI(ConfigManager configManager, com.kylin.plsql.ui.component.common.SplashScreen splash) {
        MainFrame frame = new MainFrame(configManager);
        long startTime = System.currentTimeMillis();
        int minDuration = configManager.getSplashMinDuration();
        int maxDuration = configManager.getSplashMaxDuration();

        javax.swing.Timer watchdog = new javax.swing.Timer(maxDuration, e -> {
            if (splash.isVisible()) {
                splash.setProgress(95, "启动超时，强制完成...");
                try {
                    SwingUtilities.invokeAndWait(frame::finishInit);
                } catch (Exception ignored) {}
                splash.setProgress(100, "启动完成");
                splash.close();
                frame.setVisible(true);
                frame.finishLayout();
            }
        });
        watchdog.setRepeats(false);
        watchdog.start();

        new Thread(() -> {
            try {
                splash.setProgress(5, "正在初始化界面...");
                SwingUtilities.invokeAndWait(() -> { frame.setupMenu(); frame.buildToolbar(); });

                splash.setProgress(25, "正在构建编辑区...");
                SwingUtilities.invokeAndWait(frame::buildPanels);

                splash.setProgress(50, "正在构建输出面板...");
                SwingUtilities.invokeAndWait(frame::buildBottomPanel);

                splash.setProgress(75, "正在组装界面...");
                SwingUtilities.invokeAndWait(frame::buildAssembly);

                splash.setProgress(90, "正在启动...");

                watchdog.stop();
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < minDuration) {
                    Thread.sleep(minDuration - elapsed);
                }

                splash.setProgress(95, "正在完成初始化...");
                SwingUtilities.invokeAndWait(frame::finishInit);

                splash.setProgress(100, "启动完成");
                SwingUtilities.invokeAndWait(() -> {
                    splash.close();
                    frame.setVisible(true);
                });

                SwingUtilities.invokeAndWait(frame::finishLayout);
            } catch (Exception e) {
                splash.setStatus("启动失败: " + e.getMessage());
                log.error("启动失败", e);
            }
        }, "splash-builder").start();
        return frame;
    }

    private void finishInit() {
        loadSavedConnections();
        boolean restored = tryRestoreWorkspace();
        if (!restored) showWelcome();
        var cur = EngineManager.getCurrent();
        if (cur != null && engineCombo != null) engineCombo.setSelectedItem(cur);
        bottomPanel.refreshConnTree();
        restartAutoSaveTimer();
        statusBar.startMemoryMonitor();
        startConnectionMonitor();
    }

    private void finishLayout() {
        reapplyTheme();
        if (leftSplitRef[0] != null) leftSplitRef[0].setDividerLocation(250);
        if (mainSplitRef[0] != null) {
            int w = mainSplitRef[0].getWidth();
            int rightW = rightPanel.getPreferredSize().width;
            mainSplitRef[0].setDividerLocation(Math.max(w - rightW, w / 2));
        }
        // 延迟到所有组件就绪后重新恢复连接和 schema（此时元数据缓存应已同步完成）
        SwingUtilities.invokeLater(this::deferredRestoreTabConnections);
    }

    /** 在启动后期延迟恢复各 tab 的连接和 schema（等待元数据同步完成）。 */
    private void deferredRestoreTabConnections() {
        if (pendingTabStates == null) return;
        for (ConfigManager.TabState ts : pendingTabStates) {
            for (int i = 0; i < editorTabs.getTabCount(); i++) {
                Component c = editorTabs.getComponentAt(i);
                if (c instanceof SqlEditorPanel ep && ts.tabName != null
                        && ts.tabName.equals(editorTabs.getTitleAt(i))) {
                    if (ts.connName != null) ep.setConnectionName(ts.connName);
                    if (ts.schema != null) ep.setSchema(ts.schema);
                    break;
                }
                if (c instanceof SourceViewerPanel sv && ts.tabName != null
                        && ts.tabName.equals(editorTabs.getTitleAt(i))) {
                    if (ts.showingBody && "PACKAGE".equals(ts.objectType)) {
                        sv.showBody();
                    }
                    break;
                }
            }
        }
        pendingTabStates = null;
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setTitle("Kylin SQL Developer");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));

        statusBar = new StatusBar();
        ((JComponent) getContentPane()).setBorder(null);
    }

    private void layoutComponents() {
        buildToolbar();
        buildPanels();
        buildBottomPanel();
        buildAssembly();
    }

    private void buildToolbar() {
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(ThemeManager.getInstance().resolve("bg.toolbar"));
        toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0,
            ThemeManager.getInstance().resolve("border.light")));

        JButton newBtn = tb("new", "新建", "新建 SQL 文件 (Ctrl+N)", e -> newFile(null, null));
        toolbar.add(newBtn);

        JButton openBtn = tb("open", "打开", "打开 (Ctrl+O)", e -> openFile());
        toolbar.add(openBtn);

        JButton saveBtn = tb("save", "保存", "保存 (Ctrl+S)", e -> saveActiveFile());
        toolbar.add(saveBtn);

        toolbar.addSeparator();

        JButton execBtn = tb("execute", "执行", "执行 (F8)", e -> executeActiveEditor());
        execBtn.setForeground(new Color(0x5CB85C));
        toolbar.add(execBtn);

        engineCombo = new JComboBox<>();
        for (var e : EngineManager.getEngines()) {
            engineCombo.addItem(e);
        }
        engineCombo.setSelectedItem(EngineManager.getCurrent());
        engineCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SqlFormatterEngine e) setText(e.getDisplayName());
                return c;
            }
        });
        engineCombo.addActionListener(e -> {
            EngineManager.setCurrent(engineCombo.getSelectedIndex());
        });
        engineCombo.setToolTipText("选择格式化引擎");
        engineCombo.setPreferredSize(new java.awt.Dimension(120, engineCombo.getPreferredSize().height));
        engineCombo.setMaximumSize(new java.awt.Dimension(120, engineCombo.getPreferredSize().height));
        toolbar.add(engineCombo);

        JButton formatBtn = tb("format", "格式化", "格式化 (Ctrl+Shift+F)", e -> formatSql());
        toolbar.add(formatBtn);

        caseBtn = tb("case-sensitive", "Aa", "大小写转换 (大写)", e -> toggleCaseSql());
        toolbar.add(caseBtn);

        toolbar.addSeparator();

        JButton connBtn = tb("connect", "连接", "管理连接", e -> showConnectionDialog());
        toolbar.add(connBtn);

        toolbar.addSeparator();

        // 定位文件
        JButton locateBtn = tb("locate", "定位", "定位文件", e -> {
            if (fileBrowser != null) {
                Runnable r = fileBrowser.getOnLocateFile();
                if (r != null) r.run();
            }
        });
        locateBtn.setForeground(new Color(0x4A90D9));
        toolbar.add(locateBtn);

        add(toolbar, BorderLayout.NORTH);
    }

    private void buildPanels() {
        objectBrowser = new ObjectBrowser(new ObjectBrowser.Callback() {
            @Override
            public void onObjectAction(String connName, String schema, String objectType, String objectName, String action) {
                MainFrame.this.onObjectAction(connName, schema, objectType, objectName, action);
            }
            @Override
            public void onNewSqlEditor(String connName) {
                newFile(connName, null);
            }
            @Override
            public void onNewSqlEditor(String connName, String schema) {
                newFile(connName, schema);
            }
            @Override
            public void onOpenConnections() { showConnectionDialog(); }
            @Override
            public void onConnectionProperties(String connName) {
                showConnectionDialog(connName);
            }
            @Override
            public void onOpenSourceObject(String connName, String schema, String objectType, String objectName) {
                openSourceObject(connName, schema, objectType, objectName);
            }
            @Override
            public void onSyncProgress(String connName, int percent) {
                statusBar.setSyncProgress("加载元数据: " + connName, percent);
                if (java.awt.Taskbar.isTaskbarSupported()) {
                    try {
                        java.awt.Taskbar.getTaskbar().setProgressValue(percent);
                    } catch (Exception ignored) {}
                }
            }
            @Override
            public void onSyncComplete(String connName) {
                statusBar.setMessage(connName + " 加载完成");
                Timer t = new Timer(4000, ev -> statusBar.hideSyncProgress());
                t.setRepeats(false);
                t.start();
                if (java.awt.Taskbar.isTaskbarSupported()) {
                    try { java.awt.Taskbar.getTaskbar().setProgressValue(0); } catch (Exception ignored) {}
                }
            }
            @Override
            public void onSyncError(String connName, String message) {
                statusBar.hideSyncProgress();
                if (java.awt.Taskbar.isTaskbarSupported()) {
                    try { java.awt.Taskbar.getTaskbar().setProgressValue(0); } catch (Exception ignored) {}
                }
                statusBar.setMessage(connName + " 刷新失败: " + message);
                JOptionPane.showMessageDialog(MainFrame.this,
                    "连接 '" + connName + "' 失败:\n" + message,
                    "连接失败", JOptionPane.ERROR_MESSAGE);
            }
            @Override
            public void onExecuteScript(String connName, String schema) {
                executeSqlScript(connName, schema);
            }
        });
        objectBrowser.setConfigManager(configManager);

        fileBrowser = new LocalFileBrowser(path -> {
            fileBrowser.markOpened(new File(path).getName());
            openOrSwitchToFile(path);
        });
        fileBrowser.addRootFoldersSilently(configManager.getOpenFolders());
        fileBrowser.setOnFolderAdded(() -> {
            leftPanel.selectFilesTab();
            configManager.setOpenFolders(fileBrowser.getOpenFolderPaths());
        });
        fileBrowser.setOnLocateFile(() -> {
            Component active = editorTabs.getSelectedComponent();
            if (active instanceof SqlEditorPanel ep) {
                String fp = ep.getFilePath();
                if (fp == null) {
                    showToast("当前标签页没有关联文件");
                    return;
                }
                // Try left panel LocalFileBrowser first
                DefaultMutableTreeNode found = fileBrowser.findNodeByPath(fp);
                if (found != null) {
                    leftPanel.selectFilesTab();
                    fileBrowser.selectAndScrollToNode(found);
                    return;
                }
                // Try right panel FilesContent
                if (rightPanel.locateFileInFiles(fp)) {
                    rightPanel.selectFilesTab();
                    return;
                }
                showToast("文件未在文件浏览器中找到");
            } else if (active instanceof SourceViewerPanel sv) {
                String cn = sv.getConnName();
                if (cn == null || cn.isEmpty()) {
                    showToast("当前标签页没有关联连接");
                    return;
                }
                leftPanel.ensureDatabaseTab();
                objectBrowser.locateObject(sv.getConnName(), sv.getSchema(), sv.getObjectType(), sv.getObjectName());
            } else {
                showToast("当前标签页没有可定位的对象");
            }
        });
        leftPanel = new LeftPanel(objectBrowser, fileBrowser, () -> {
            if (leftSplitRef[0] != null) {
                leftSplitRef[0].setDividerLocation(leftPanel.isExpanded() ? 250 : 28);
            }
        });
        leftPanel.setMinimumSize(new Dimension(32, 0));

        welcomePanel = new WelcomePanel(
                () -> newFile(null, null),
                this::openFile
        );

        editorTabs = new JTabbedPane();
        editorTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        editorTabs.addMouseWheelListener(e -> {
            int dir = e.getWheelRotation();
            int idx = editorTabs.getSelectedIndex();
            int next = idx + dir;
            if (next >= 0 && next < editorTabs.getTabCount()) {
                editorTabs.setSelectedIndex(next);
            }
        });
        installTabContextMenu(editorTabs);

        editorPanel = new JPanel(new CardLayout());
        editorPanel.setBorder(null);
        editorPanel.add(welcomePanel, "welcome");
        tabContainer = new JPanel(new BorderLayout());
        tabContainer.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1,
            ThemeManager.getInstance().resolve("border.light")));
        tabContainer.add(editorTabs, BorderLayout.CENTER);
        editorPanel.add(tabContainer, "tabs");
        showWelcome();

        rightPanel = new RightPanel(configManager, () -> {
            if (mainSplitRef[0] != null) {
                int w = mainSplitRef[0].getWidth();
                mainSplitRef[0].setDividerLocation(rightPanel.isExpanded() ? w - 140 : w - 28);
            }
        }, filePath -> {
            openOrSwitchToFile(filePath);
        });
    }

    private void buildBottomPanel() {
        bottomPanel = new BottomPanel();
        bottomPanel.setOnReopenClosedTab(this::reopenClosedTab);
        bottomPanel.setOnRefresh(() -> bottomPanel.refreshConnTree());
        bottomPanel.setOnNewQuery(connName -> newFile(connName, null));
        bottomPanel.setOnCloseAllTabs(connName -> {
            for (int i = editorTabs.getTabCount() - 1; i >= 0; i--) {
                Component c = editorTabs.getComponentAt(i);
                if (c instanceof SqlEditorPanel ep && connName.equals(ep.getConnectionName())) {
                    closeTab(editorTabs, i);
                }
            }
        });
        bottomPanel.setOnSaveTab(ti -> {
            SqlEditorPanel ep = findEditorByTabInfo(ti);
            if (ep != null) saveFile(ep);
        });
        bottomPanel.setOnCloseTab(ti -> {
            SqlEditorPanel ep = findEditorByTabInfo(ti);
            if (ep != null) {
                int idx = editorTabs.indexOfComponent(ep);
                if (idx >= 0) closeTab(editorTabs, idx);
            }
        });
        bottomPanel.setOnOpenInNewTab(ti -> {
            if (ti.filePath != null) {
                openOrSwitchToFile(ti.filePath);
            } else {
                openInNewEditor("", ti.connName);
            }
        });
        bottomPanel.setOnDeleteRecord(ti -> {
            int ret = JOptionPane.showConfirmDialog(this,
                "确定要删除记录“" + ti.tabTitle + "”吗？",
                "删除记录", JOptionPane.YES_NO_OPTION);
            if (ret != JOptionPane.YES_OPTION) return;
            removeFromClosedTabs(ti);
            bottomPanel.refreshConnTree();
        });
        bottomPanel.setOnOpenClosedTab(ti -> {
            if (ti.filePath != null) {
                openOrSwitchToFile(ti.filePath);
            } else {
                openInNewEditor("", ti.connName);
            }
            removeFromClosedTabs(ti);
        });
        bottomPanel.setDataProvider(() -> {
            java.util.Map<String, TabInfo> merged = new java.util.LinkedHashMap<>();
            for (TabInfo ct : closedTabs) {
                String key = (ct.connName != null ? ct.connName : "") + "|" + ct.tabTitle;
                merged.put(key, ct);
            }
            for (int i = 0; i < editorTabs.getTabCount(); i++) {
                Component comp = editorTabs.getComponentAt(i);
                if (comp instanceof SqlEditorPanel ep) {
                    TabInfo ti = new TabInfo();
                    ti.connName = ep.getConnectionName();
                    ti.filePath = ep.getFilePath();
                    ti.tabTitle = editorTabs.getTitleAt(i);
                    ti.open = true;
                    String key = (ti.connName != null ? ti.connName : "") + "|" + ti.tabTitle;
                    merged.put(key, ti);
                }
            }
            return new java.util.ArrayList<>(merged.values());
        });
        bottomPanel.setRefreshExecutor((connName, sql) -> {
            if (connName == null || connName.isEmpty()) return;
            boolean closeConn = connectionManager.isAutoCommit(connName);
            new SwingWorker<com.kylin.plsql.core.db.SqlExecutor.SqlResult, Void>() {
                @Override protected com.kylin.plsql.core.db.SqlExecutor.SqlResult doInBackground() {
                    try (java.sql.Connection conn = connectionManager.getConnection(connName)) {
                        int qto = connectionManager.getQueryTimeout(connName);
                        var executor = new com.kylin.plsql.core.db.SqlExecutor();
                        return executor.execute(conn, sql, qto);
                    } catch (Exception ex) {
                        return null;
                    }
                }
                @Override protected void done() {
                    try {
                        var result = get();
                        if (result != null) {
                            bottomPanel.showResult(sql, result, connName);
                        }
                    } catch (Exception ex) {
                        bottomPanel.showError(ex.getMessage());
                    }
                }
            }.execute();
        });

        bottomWrapper = new JPanel(new BorderLayout());
        bottomWrapper.setBorder(null);
        bottomWrapper.add(bottomPanel, BorderLayout.CENTER);
        bottomWrapper.add(statusBar, BorderLayout.SOUTH);
        // 底部面板折叠/展开时同步 JSplitPane 分隔线
        bottomPanel.setOnToggle(() -> {
            if (verticalSplit == null || verticalSplit.getHeight() <= 0) return;
            if (bottomPanel.isExpanded()) {
                verticalSplit.setDividerLocation((int)(verticalSplit.getHeight() * 0.65));
            } else {
                int barH = statusBar.getPreferredSize().height;
                verticalSplit.setDividerLocation(verticalSplit.getHeight() - 28 - barH);
            }
        });

        // 执行 SQL 后确保底部面板合理展开（<5% 时强制到 35%）
        bottomPanel.setOnShowResult(() -> {
            if (verticalSplit == null || verticalSplit.getHeight() <= 0) return;
            int totalH = verticalSplit.getHeight();
            int divLoc = verticalSplit.getDividerLocation();
            if ((double)(totalH - divLoc) / totalH < 0.05) {
                verticalSplit.setDividerLocation((int)(totalH * 0.65));
            }
        });

        // ── StatusBar callbacks ──
        statusBar.setOnLockToggle(locked -> {
            Component comp = editorTabs.getSelectedComponent();
            if (comp instanceof SqlEditorPanel ep) {
                ep.getTextArea().setEditable(!locked);
                editorLockStates.put(comp, locked);
            } else if (comp instanceof SourceViewerPanel sv) {
                sv.getTextArea().setEditable(!locked);
                editorLockStates.put(comp, locked);
            }
        });
        statusBar.setOnEncodingChange(cs -> {
            statusBar.setMessage("编码已更改: " + cs);
        });
    }

    private void buildAssembly() {
        leftSplitRef[0] = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, editorPanel);
        leftSplitRef[0].setResizeWeight(0);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplitRef[0], rightPanel);
        mainSplitRef[0] = mainSplit;
        mainSplit.setResizeWeight(1.0);

        // Ctrl+Shift+F10 to execute active editor
        ((JComponent) getContentPane()).getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke("control shift F10"), "executeActive");
        ((JComponent) getContentPane()).getActionMap().put("executeActive", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { executeActiveEditor(); }
        });

        editorTabs.addChangeListener(e -> {
            Component comp = editorTabs.getSelectedComponent();
            for (int i = 0; i < editorTabs.getTabCount(); i++) {
                updateEditorTabComponent(i, editorTabs);
            }
            if (comp instanceof SqlEditorPanel ae) {
                rightPanel.setActiveEditor(ae);
                installCaretListener(ae);
                String text = ae.getText();
                symbolIndex.indexLocal(text, PlSqlNavigator.parse(text));
            } else if (comp instanceof SourceViewerPanel sv) {
                rightPanel.setActiveSourceViewer(sv);
                sv.setOnSourceChanged(() -> rightPanel.setActiveSourceViewer(sv));
                symbolIndex.indexLocal("", java.util.Collections.emptyList());
                var ta = sv.getTextArea();
                boolean found = false;
                for (var l : ta.getCaretListeners()) {
                    if (l instanceof SourceViewerCaretSync) { found = true; break; }
                }
                if (!found) ta.addCaretListener(new SourceViewerCaretSync(sv));
            }
            updateStatusBar();
            bottomPanel.refreshConnTree();
        });
        verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainSplit, bottomWrapper);
        verticalSplit.setBorder(null);
        verticalSplit.setResizeWeight(1.0);
        verticalSplit.setDividerSize(4);
        // 等 SplitPane 首次完成布局后设分隔线为 65%/35%，避免 resizeWeight(1.0) 把底部压到最小
        verticalSplit.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (verticalSplit.getHeight() > 0) {
                    verticalSplit.setDividerLocation((int)(verticalSplit.getHeight() * 0.65));
                    verticalSplit.removeComponentListener(this);
                }
            }
        });
        add(verticalSplit, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            if (leftSplitRef[0] != null) leftSplitRef[0].setDividerLocation(250);
            if (mainSplitRef[0] != null) {
                int w = mainSplitRef[0].getWidth();
                int rightW = rightPanel.getPreferredSize().width;
                mainSplitRef[0].setDividerLocation(Math.max(w - rightW, w / 2));
            }
        });
    }

    private void updateStatusBar() {
        Component comp = editorTabs.getSelectedComponent();
        if (comp instanceof SqlEditorPanel ae) {
            statusBar.setConnection(ae.getConnectionName(), connectionManager.isConnected(ae.getConnectionName()));
            String title = ae.getTabTitle();
            int idx = title.lastIndexOf(" @");
            if (idx > 0) title = title.substring(0, idx);
            statusBar.setFileType(title.contains(".") ? title.substring(title.lastIndexOf('.') + 1).toUpperCase() : "SQL");
            var ta = ae.getTextArea();
            try {
                int dot = ta.getCaretPosition();
                int line = ta.getLineOfOffset(dot);
                int col = dot - ta.getLineStartOffset(line) + 1;
                statusBar.setPosition(line + 1, col);
            } catch (Exception ignored) {
                statusBar.setPosition(1, 1);
            }
            statusBar.setLocked(editorLockStates.getOrDefault(comp, false));
        } else if (comp instanceof SourceViewerPanel) {
            statusBar.setConnection("");
            statusBar.setFileType("OBJ");
            statusBar.setPosition(1, 1);
            statusBar.setLocked(editorLockStates.getOrDefault(comp, false));
        } else {
            statusBar.setConnection("");
            statusBar.setFileType("");
            statusBar.setPosition(1, 1);
            statusBar.setLocked(false);
        }
    }

    private void installCaretListener(SqlEditorPanel editor) {
        if (editor == null) return;
        for (var l : editor.getTextArea().getCaretListeners()) {
            if (l instanceof OutlineSyncListener) return;
        }
        editor.addCaretListener(new OutlineSyncListener());
    }

    private class OutlineSyncListener implements CaretListener {
        @Override
        public void caretUpdate(CaretEvent e) {
            SqlEditorPanel editor = getActiveEditor();
            if (editor != null) {
                rightPanel.caretUpdated(editor.getCaretLine(), editor.getTextArea().getLineCount());
                var ta = editor.getTextArea();
                try {
                    int dot = ta.getCaretPosition();
                    int line = ta.getLineOfOffset(dot);
                    int col = dot - ta.getLineStartOffset(line) + 1;
                    statusBar.setPosition(line + 1, col);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private class SourceViewerCaretSync implements CaretListener {
        private final SourceViewerPanel viewer;
        SourceViewerCaretSync(SourceViewerPanel sv) { this.viewer = sv; }
        @Override
        public void caretUpdate(CaretEvent e) {
            try {
                int line = viewer.getTextArea().getLineOfOffset(e.getDot());
                rightPanel.caretUpdated(line + 1, viewer.getTextArea().getLineCount());
            } catch (Exception ignored) {}
        }
    }



    private void setupMenu() {
        Color mc = ThemeManager.getInstance().resolve("bg.toolbar");
        UIManager.put("MenuBar.background", mc);
        UIManager.put("MenuBar.borderColor", mc);
        UIManager.put("Menu.background", mc);
        UIManager.put("MenuItem.background", mc);
        UIManager.put("TitlePane.background", mc);
        UIManager.put("TitlePane.unifiedBackground", false);
        menuBar = new JMenuBar() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(ThemeManager.getInstance().resolve("bg.toolbar"));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        menuBar.setBackground(mc);
        menuBar.setOpaque(true);

        JMenu fileMenu = new JMenu("文件");
        JMenuItem newItem = new JMenuItem("新建 SQL 文件");
        newItem.setIcon(IconUtil.menuIcon("new"));
        newItem.setAccelerator(KeyStroke.getKeyStroke("control N"));
        newItem.addActionListener(e -> newFile(null, null));
        fileMenu.add(newItem);

        JMenuItem openItem = new JMenuItem("打开 SQL 文件");
        openItem.setIcon(IconUtil.menuIcon("open"));
        openItem.setAccelerator(KeyStroke.getKeyStroke("control O"));
        openItem.addActionListener(e -> openFile());
        fileMenu.add(openItem);

        fileMenu.addSeparator();

        JMenuItem saveItem = new JMenuItem("保存");
        saveItem.setIcon(IconUtil.menuIcon("save"));
        saveItem.setAccelerator(KeyStroke.getKeyStroke("control S"));
        saveItem.addActionListener(e -> saveActiveFile());
        fileMenu.add(saveItem);

        JMenuItem saveAsItem = new JMenuItem("另存为");
        saveAsItem.setIcon(IconUtil.menuIcon("save-plus"));
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke("control shift S"));
        saveAsItem.addActionListener(e -> saveActiveFileAs());
        fileMenu.add(saveAsItem);

        fileMenu.addSeparator();
        JMenuItem closeItem = new JMenuItem("关闭标签");
        closeItem.setIcon(IconUtil.menuIcon("x"));
        closeItem.setAccelerator(KeyStroke.getKeyStroke("control W"));
        closeItem.addActionListener(e -> closeCurrentTab());
        fileMenu.add(closeItem);

        fileMenu.addSeparator();
        JMenuItem prefItem = new JMenuItem("设置");
        prefItem.setIcon(IconUtil.menuIcon("settings"));
        prefItem.setAccelerator(KeyStroke.getKeyStroke("control alt S"));
        prefItem.addActionListener(e -> showSettingsDialog());
        fileMenu.add(prefItem);

        fileMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        JMenu editMenu = new JMenu("编辑");
        JMenuItem undoItem = new JMenuItem("撤销");
        undoItem.setAccelerator(KeyStroke.getKeyStroke("control Z"));
        undoItem.addActionListener(e -> action("undo"));
        editMenu.add(undoItem);

        JMenuItem redoItem = new JMenuItem("重做");
        redoItem.setAccelerator(KeyStroke.getKeyStroke("control Y"));
        redoItem.addActionListener(e -> action("redo"));
        editMenu.add(redoItem);

        editMenu.addSeparator();
        JMenuItem findItem = new JMenuItem("查找");
        findItem.setIcon(IconUtil.menuIcon("search"));
        findItem.setAccelerator(KeyStroke.getKeyStroke("control F"));
        findItem.addActionListener(e -> action("find"));
        editMenu.add(findItem);

        JMenuItem replaceItem = new JMenuItem("替换");
        replaceItem.setIcon(IconUtil.menuIcon("search"));
        replaceItem.setAccelerator(KeyStroke.getKeyStroke("control R"));
        replaceItem.addActionListener(e -> action("replace"));
        editMenu.add(replaceItem);
        editMenu.addSeparator();
        JMenuItem globalSearchItem = new JMenuItem("全局搜索");
        globalSearchItem.setIcon(IconUtil.menuIcon("file-search"));
        globalSearchItem.setAccelerator(KeyStroke.getKeyStroke("control P"));
        globalSearchItem.addActionListener(e -> showGlobalSearch());
        editMenu.add(globalSearchItem);
        menuBar.add(editMenu);

        // KeyEventPostProcessor fires AFTER all component InputMap/ActionMap processing,
        // even if the event was consumed. This ensures Ctrl+P always triggers global search
        // regardless of whether the textarea's JDK 17 KeymapWrapper consumed the event.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED
                    && e.getKeyCode() == KeyEvent.VK_P
                    && e.isControlDown()
                    && e.isConsumed()) {
                EventQueue.invokeLater(MainFrame.this::showGlobalSearch);
                return true;
            }
            return false;
        });

        JMenu sqlMenu = new JMenu("SQL");
        JMenuItem execItem = new JMenuItem("执行 (F8)");
        execItem.setIcon(IconUtil.menuIcon("execute"));
        execItem.setAccelerator(KeyStroke.getKeyStroke("F8"));
        execItem.addActionListener(e -> executeActiveEditor());
        sqlMenu.add(execItem);

        JMenuItem appendExecItem = new JMenuItem("追加执行 (F9)");
        appendExecItem.setIcon(IconUtil.menuIcon("append"));
        appendExecItem.setAccelerator(KeyStroke.getKeyStroke("F9"));
        appendExecItem.addActionListener(e -> executeAppendEditor());
        sqlMenu.add(appendExecItem);

        JMenuItem fmtItem = new JMenuItem("格式化 (Ctrl+Shift+F)");
        fmtItem.setIcon(IconUtil.menuIcon("format"));
        fmtItem.setAccelerator(KeyStroke.getKeyStroke("control shift F"));
        fmtItem.addActionListener(e -> formatSql());
        sqlMenu.add(fmtItem);

        JMenuItem planItem = new JMenuItem("执行计划");
        planItem.setIcon(IconUtil.menuIcon("skip-forward"));
        planItem.setAccelerator(KeyStroke.getKeyStroke("control E"));
        planItem.addActionListener(e -> explainPlan());
        sqlMenu.add(planItem);

        JMenuItem callItem = new JMenuItem("调用层级 (Ctrl+Alt+H)");
        callItem.addActionListener(e -> showCallHierarchy());
        sqlMenu.add(callItem);

        JMenuItem histItem = new JMenuItem("SQL 历史记录");
        histItem.setIcon(IconUtil.menuIcon("history"));
        histItem.setAccelerator(KeyStroke.getKeyStroke("control shift H"));
        histItem.addActionListener(e -> showSqlHistoryDialog());
        sqlMenu.add(histItem);

        menuBar.add(sqlMenu);

        JMenu viewMenu = new JMenu("视图");
        JMenu themeMenu = new JMenu("主题");
        ButtonGroup themeGroup = new ButtonGroup();
        AppTheme[] themes = {AppTheme.DARK, AppTheme.LIGHT, AppTheme.GREEN};
        String[] themeLabels = {"Darcula", "Light", "豆沙绿"};
        for (int i = 0; i < themes.length; i++) {
            JRadioButtonMenuItem mi = new JRadioButtonMenuItem(themeLabels[i]);
            int idx = i;
            mi.addActionListener(e -> switchTheme(themes[idx]));
            themeGroup.add(mi);
            themeMenu.add(mi);
            if (themes[i] == ThemeManager.getInstance().getCurrentTheme()) mi.setSelected(true);
        }
        viewMenu.add(themeMenu);
        menuBar.add(viewMenu);

        // ── Tools Menu ──
        JMenu toolsMenu = new JMenu("工具");

        JMenuItem sqlToolsItem = new JMenuItem("SQL 工具");
        sqlToolsItem.setIcon(IconUtil.menuIcon("toolbox"));
        sqlToolsItem.addActionListener(e -> new SqlToolsDialog(MainFrame.this, formatOptions, 0).setVisible(true));
        toolsMenu.add(sqlToolsItem);

        JMenuItem sqlFmtItem = new JMenuItem("SQL 格式化");
        sqlFmtItem.setIcon(IconUtil.menuIcon("format"));
        sqlFmtItem.addActionListener(e -> new SqlToolsDialog(MainFrame.this, formatOptions, 1).setVisible(true));
        toolsMenu.add(sqlFmtItem);

        JMenuItem dataGenItem = new JMenuItem("数据生成器");
        dataGenItem.addActionListener(e -> showDataGeneratorDialog());
        toolsMenu.add(dataGenItem);

        JMenuItem sqlHistItem = new JMenuItem("SQL 历史");
        sqlHistItem.setIcon(IconUtil.menuIcon("history"));
        sqlHistItem.addActionListener(e -> showSqlHistoryDialog());
        toolsMenu.add(sqlHistItem);

        JMenuItem diffItem = new JMenuItem("文本比较");
        diffItem.setIcon(IconUtil.menuIcon("compare"));
        diffItem.addActionListener(e -> new TextDiffDialog(MainFrame.this).setVisible(true));
        toolsMenu.add(diffItem);

        JMenuItem regexItem = new JMenuItem("正则测试器");
        regexItem.setIcon(IconUtil.menuIcon("regex"));
        regexItem.addActionListener(e -> new RegexTesterDialog(MainFrame.this).setVisible(true));
        toolsMenu.add(regexItem);

        JMenuItem objSearchItem = new JMenuItem("对象搜索");
        objSearchItem.setIcon(IconUtil.menuIcon("database-search"));
        objSearchItem.addActionListener(e -> showObjectSearchDialog());
        toolsMenu.add(objSearchItem);

        toolsMenu.addSeparator();

        JMenuItem advExportItem = new JMenuItem("高级导出");
        advExportItem.addActionListener(e -> showAdvancedExportDialog());
        toolsMenu.add(advExportItem);

        menuBar.add(toolsMenu);

        JMenu helpMenu = new JMenu("帮助");
        JMenuItem logItem = new JMenuItem("应用日志");
        logItem.setIcon(IconUtil.menuIcon("info"));
        logItem.addActionListener(e -> new com.kylin.plsql.ui.dialog.common.LogViewerDialog(MainFrame.this).setVisible(true));
        helpMenu.add(logItem);
        helpMenu.addSeparator();
        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.setIcon(IconUtil.menuIcon("help"));
        aboutItem.addActionListener(e -> new AboutDialog(MainFrame.this).setVisible(true));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void action(String cmd) {
        SqlEditorPanel editor = getActiveEditor();
        if (editor == null) return;
        if ("find".equals(cmd)) {
            editor.showSearch();
        } else if ("replace".equals(cmd)) {
            editor.showReplace();
        } else {
            java.awt.event.ActionEvent ae = new java.awt.event.ActionEvent(
                editor.getTextArea(), java.awt.event.ActionEvent.ACTION_PERFORMED, cmd);
            editor.getTextArea().getActionMap().get(cmd).actionPerformed(ae);
        }
    }

    private void newFile(String connName, String schema) {
        SqlEditorPanel editor = new SqlEditorPanel(connectionManager, getNextConsoleName());
        editor.getTextArea().setSyntaxEditingStyle("text/plsql");
        editor.getTextArea().setTabSize(4);
        var connections = configManager.loadConnections();
        editor.setConnections(connections);
        if (connName != null) editor.setConnectionName(connName);
        if (schema != null) editor.setSchema(schema);
        editor.setOnExecute(() -> executeActiveEditor());
        editor.setOnAppendExecute(() -> executeAppendEditor());
        editor.setOnFormat(this::formatSql);
        editor.setOnStatusMessage(msg -> {
    bottomPanel.showToast(msg);
    statusBar.setStatusText(msg);
});
editor.setOnHistoryRequest(() -> rightPanel.selectHistoryTab());
        showEditorTabs();
        editorTabs.addTab(editor.getTabTitle(), editor);
        int idx = editorTabs.indexOfComponent(editor);
        initTabComponent(idx, editor);
        editorTabs.setSelectedComponent(editor);
        editor.getTextArea().requestFocusInWindow();
        statusBar.setMessage("新建: " + editor.getTabTitle());
        installCaretListener(editor);
        saveWorkspace();
    }

    private String getNextConsoleName() {
        return consoleCounter++ == 1 ? "sql" : "sql_" + (consoleCounter - 1);
    }

    private void initTabComponent(int index, Component panel) {
        editorTabs.setTabComponentAt(index, buildTabComponent(index, panel, editorTabs));
    }

    private JPanel buildTabComponent(int index, Component panel, JTabbedPane tabs) {
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);
        String iconLetter;
        Color iconColor;
        if (panel instanceof SourceViewerPanel sv) {
            iconLetter = switch (sv.getObjectType()) {
                case "PACKAGE" -> "K";
                case "PROCEDURE" -> "P";
                case "FUNCTION" -> "F";
                default -> "V";
            };
            iconColor = switch (sv.getObjectType()) {
                case "PACKAGE" -> new Color(0xA0522D);
                case "PROCEDURE", "FUNCTION" -> new Color(0xD9534F);
                default -> new Color(0x5BC0DE);
            };
        } else {
            iconLetter = "S";
            iconColor = (panel instanceof SqlEditorPanel ep && ep.getFilePath() == null)
                ? new Color(0xE74C3C) : new Color(0x27AE60);
        }
        JLabel iconLbl = new JLabel(makeTabIcon(iconLetter, iconColor));
        tabPanel.add(iconLbl);
        tabPanel.add(Box.createHorizontalStrut(2));

        String title = panel instanceof SqlEditorPanel ep ? ep.getTabTitle()
                       : panel instanceof SourceViewerPanel sv ? sv.getTabTitle()
                       : tabs.getTitleAt(index);
        JLabel label = new JLabel(title);
        boolean isSelected = tabs.getSelectedIndex() == index;
        label.setForeground(isSelected
            ? ThemeManager.getInstance().resolve("fg.tab.active")
            : ThemeManager.getInstance().resolve("fg.tab.inactive"));
        if (panel instanceof SqlEditorPanel editor) {
            editor.setOnModifiedChange(() -> label.setText(editor.getTabTitle()));
        }

        JButton closeBtn = createCloseButton();
        closeBtn.setForeground(ThemeManager.getInstance().resolve("fg.muted"));
        closeBtn.addActionListener(e -> {
            int i = tabs.indexOfComponent(panel);
            if (i >= 0) closeTab(tabs, i);
        });
        tabPanel.add(label);
        tabPanel.add(Box.createHorizontalStrut(2));
        tabPanel.add(Box.createHorizontalGlue());
        boolean pinned = isPinned(tabs, index);
        if (pinned) {
            tabPanel.add(new JLabel(new PinIcon(new Color(0x3D8B3D))));
        } else {
            tabPanel.add(closeBtn);
        }
        tabPanel.add(Box.createHorizontalStrut(2));
        return tabPanel;
    }

    private void updateEditorTabComponent(int index, JTabbedPane tabs) {
        if (index < 0 || index >= tabs.getTabCount()) return;
        tabs.setTabComponentAt(index, buildTabComponent(index, tabs.getComponentAt(index), tabs));
    }

    private static Icon makeTabIcon(String text, Color bg) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(1, 1, 14, 14, 3, 3);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        int x = (16 - fm.stringWidth(text)) / 2;
        int y = (16 + fm.getAscent()) / 2 - 1;
        g.drawString(text, x, y);
        g.dispose();
        return new ImageIcon(img);
    }

    private static class PinIcon implements Icon {
        private final Color color;
        PinIcon(Color c) { this.color = c; }
        @Override public int getIconWidth() { return 10; }
        @Override public int getIconHeight() { return 10; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillOval(x + 1, y + 1, 8, 8);
        }
    }

    private static JButton createCloseButton() {
        JButton btn = new JButton("×") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = getModel().isRollover();
                if (hover) {
                    g2.setColor(new Color(0xE81123));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.setColor(Color.WHITE);
                } else {
                    g2.setColor(getForeground());
                }
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 1;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        btn.setFont(FontManager.getInstance().resolve("font.top"));
        btn.setPreferredSize(new Dimension(18, 18));
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setContentAreaFilled(false);
        btn.setFocusable(false);
        btn.setRolloverEnabled(true);
        btn.setForeground(new Color(0x888888));
        return btn;
    }

    /** Matches: CREATE [OR REPLACE] [EDITIONABLE|NONEDITIONABLE] (FUNCTION|PROCEDURE|PACKAGE [BODY]|...) */
    private static final Pattern OBJ_CREATE_PATTERN = Pattern.compile(
        "CREATE\\s+(OR\\s+REPLACE\\s+)?(EDITIONABLE\\s+|NONEDITIONABLE\\s+)?(FUNCTION|PROCEDURE|PACKAGE\\s+BODY|PACKAGE|TYPE\\s+BODY|TYPE|TRIGGER)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    /** Matches standalone declaration at line start: e.g. "PACKAGE BODY foo IS" */
    private static final Pattern OBJ_DECL_PATTERN = Pattern.compile(
        "^(PACKAGE\\s+BODY|PACKAGE|FUNCTION|PROCEDURE)\\s+\\w+",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static String detectObjectType(String content) {
        if (content == null || content.isBlank()) {
            log.debug("detectObjectType: content is null/blank");
            return null;
        }
        var m = OBJ_DECL_PATTERN.matcher(content);
        if (m.find()) {
            String type = m.group(1).toUpperCase().replace(' ', '_');
            log.debug("detectObjectType: matched via line-start decl, type={}, raw='{}'", type, m.group());
            return type;
        }
        m = OBJ_CREATE_PATTERN.matcher(content);
        if (m.find()) {
            String type = m.group(3).toUpperCase().replace(' ', '_');
            log.debug("detectObjectType: matched via CREATE, type={}, raw='{}'", type, m.group());
            return type;
        }
        log.debug("detectObjectType: no match, first 200 chars: {}",
            content.substring(0, Math.min(200, content.length())).replace('\n', ' '));
        return null;
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
            "SQL 文件 (*.sql, *.pck, *.pks, *.pkb, *.prc, *.fnc, *.vw, *.trg)",
            "sql", "pck", "pks", "pkb", "prc", "fnc", "vw", "trg"));
        chooser.setAcceptAllFileFilterUsed(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.exists()) return;

        String fileName = file.getName();
        String absPath = file.getAbsolutePath();
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component comp = editorTabs.getComponentAt(i);
            if (comp instanceof SourceViewerPanel sv && fileName.equals(sv.getObjectName())
                    || comp instanceof SqlEditorPanel ep && absPath.equals(ep.getFilePath())) {
                editorTabs.setSelectedIndex(i);
                statusBar.setMessage("已打开: " + fileName);
                return;
            }
        }

        new SwingWorker<FileContent, Void>() {
            @Override protected FileContent doInBackground() {
                try {
                    String content = Files.readString(file.toPath());
                    String objType = detectObjectType(content);
                    return new FileContent(content, objType);
                } catch (IOException e) {
                    return new FileContent(e);
                }
            }
            @Override protected void done() {
                try {
                    FileContent fc = get();
                    if (fc.error != null) {
                        JOptionPane.showMessageDialog(MainFrame.this, "打开文件失败:\n" + fc.error.getMessage(),
                            "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    String content = fc.content;
                    String objType = fc.objType;
                    log.debug("openFile: {} bytes read from {}, objType={}", content.length(), absPath, objType);
                    if (objType != null && ("PACKAGE".equals(objType) || "PACKAGE_BODY".equals(objType))) {
                        log.debug("openFile: opening in SourceViewerPanel (type={})", objType);
                        SourceViewerPanel viewer = new SourceViewerPanel(connectionManager, null, null,
                            file.getName(), objType, content);
                        saveHistorySnapshot(absPath, content);
                        showEditorTabs();
                        editorTabs.addTab(viewer.getTabTitle(), viewer);
                        int idx = editorTabs.indexOfComponent(viewer);
                        initTabComponent(idx, viewer);
                        editorTabs.setSelectedComponent(viewer);
                        viewer.getTextArea().requestFocusInWindow();
                        statusBar.setMessage("已打开: " + absPath);
                        rightPanel.onFileOpenedOrSaved(absPath);
                        saveWorkspace();
                    } else {
                        log.debug("openFile: opening in SqlEditorPanel (no object type detected)");
                        SqlEditorPanel editor = new SqlEditorPanel(connectionManager, file.getName());
                        editor.getTextArea().setSyntaxEditingStyle("text/plsql");
                        editor.setText(content);
                        editor.setFilePath(absPath);
                        editor.resetModified();
                        var connections = configManager.loadConnections();
                        editor.setConnections(connections);
                        editor.setOnExecute(() -> executeActiveEditor());
                        editor.setOnAppendExecute(() -> executeAppendEditor());
                        editor.setOnFormat(MainFrame.this::formatSql);
                        editor.setOnStatusMessage(msg -> {
                            bottomPanel.showToast(msg);
                            statusBar.setStatusText(msg);
                        });
                        editor.setOnHistoryRequest(() -> rightPanel.selectHistoryTab());
                        editor.setOnConnectionChange(() -> bottomPanel.refreshConnTree());
                        showEditorTabs();
                        editorTabs.addTab(editor.getTabTitle(), editor);
                        int idx = editorTabs.indexOfComponent(editor);
                        initTabComponent(idx, editor);
                        editorTabs.setSelectedComponent(editor);
                        editor.getTextArea().requestFocusInWindow();
                        statusBar.setMessage("已打开: " + absPath);
                        installCaretListener(editor);
                        rightPanel.onFileOpenedOrSaved(absPath);
                        saveWorkspace();
                    }
                } catch (Exception e) {
                    log.error("openFile failed", e);
                }
            }
        }.execute();
    }

    private static class FileContent {
        final String content;
        final String objType;
        final IOException error;
        FileContent(String content, String objType) { this.content = content; this.objType = objType; this.error = null; }
        FileContent(IOException error) { this.content = null; this.objType = null; this.error = error; }
    }

    private boolean saveActiveFile() {
        SqlEditorPanel editor = getActiveEditor();
        if (editor == null) return false;
        return saveFile(editor);
    }

    private boolean saveActiveFileAs() {
        SqlEditorPanel editor = getActiveEditor();
        if (editor == null) return false;
        return saveFileAs(editor);
    }

    // ── Auto-save ──

    public void restartAutoSaveTimer() {
        if (autoSaveTimer != null) {
            autoSaveTimer.stop();
            autoSaveTimer = null;
        }
        if (!configManager.isAutoSaveEnabled()) return;
        int interval = configManager.getAutoSaveInterval();
        int millis = switch (configManager.getAutoSaveUnit()) {
            case "seconds" -> interval * 1000;
            case "minutes" -> interval * 60 * 1000;
            case "hours"   -> interval * 3600 * 1000;
            default -> interval * 60 * 1000;
        };
        autoSaveTimer = new Timer(millis, e -> autoSaveAll());
        autoSaveTimer.setRepeats(true);
        autoSaveTimer.start();
        log.info("自动保存已启动, 间隔={}ms", millis);
    }

    private void startConnectionMonitor() {
        new Timer(5000, e -> {
            Component comp = editorTabs.getSelectedComponent();
            if (comp instanceof SqlEditorPanel ae) {
                String cn = ae.getConnectionName();
                if (cn != null && !cn.isEmpty()) {
                    statusBar.setConnection(cn, connectionManager.isConnected(cn));
                }
            }
        }).start();
    }

    private void autoSaveAll() {
        Path dir = Path.of(configManager.getAutoSavePath());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("创建自动保存目录失败: {}", dir, e);
            return;
        }
        int count = 0;
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component comp = editorTabs.getComponentAt(i);
            if (!(comp instanceof SqlEditorPanel editor)) continue;
            if (!editor.isModified()) continue;
            String filename = autoSaveFileName(editor);
            Path target = dir.resolve(filename);
            try {
                Files.writeString(target, editor.getText());
                count++;
            } catch (IOException e) {
                log.error("自动保存失败: {}", target, e);
            }
        }
        if (count > 0) {
            log.info("自动保存完成: {} 个文件 → {}", count, dir);
        }
    }

    private static String autoSaveFileName(SqlEditorPanel editor) {
        if (editor.getFilePath() != null) {
            return Path.of(editor.getFilePath()).getFileName().toString();
        }
        String name = editor.getTabTitle();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("[. ]+$", "").trim();
        if (name.isEmpty()) name = "untitled";
        return name + ".sql";
    }

    private boolean saveFile(SqlEditorPanel editor) {
        if (editor.getFilePath() != null) {
            return writeFile(editor, Path.of(editor.getFilePath()));
        }
        return saveFileAs(editor);
    }

    private boolean saveFileAs(SqlEditorPanel editor) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("SQL 文件 (*.sql)", "sql"));
        if (editor.getFilePath() != null) {
            chooser.setSelectedFile(new File(editor.getFilePath()));
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;

        File file = chooser.getSelectedFile();
        if (!file.getName().contains(".")) {
            file = new File(file.getAbsolutePath() + ".sql");
        }

        if (file.exists()) {
            int ret = JOptionPane.showConfirmDialog(this,
                "文件已存在，是否覆盖?\n" + file.getName(),
                "确认保存", JOptionPane.YES_NO_OPTION);
            if (ret != JOptionPane.YES_OPTION) return false;
        }

        editor.setFilePath(file.getAbsolutePath());
        return writeFile(editor, file.toPath());
    }

    private boolean writeFile(SqlEditorPanel editor, Path path) {
        try {
            saveLocalHistory(editor);
            Files.writeString(path, editor.getText());
            editor.resetModified();
            int idx = editorTabs.indexOfComponent(editor);
            if (idx >= 0) {
                editorTabs.setTitleAt(idx, editor.getTabTitle());
                JPanel tabPanel = (JPanel) editorTabs.getTabComponentAt(idx);
                if (tabPanel != null) {
                    JLabel label = (JLabel) tabPanel.getComponent(2);
                    label.setText(editor.getTabTitle());
                }
            }
            statusBar.setMessage("已保存: " + path.toAbsolutePath());
            rightPanel.onFileOpenedOrSaved(path.toAbsolutePath().toString());
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "保存文件失败:\n" + e.getMessage(),
                "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void closeCurrentTab() {
        int idx = editorTabs.getSelectedIndex();
        if (idx >= 0) closeTab(editorTabs, idx);
    }

    private void closeTab(int index) {
        closeTab(editorTabs, index);
    }

    private void closeTab(JTabbedPane tabs, int index) {
        if (index < 0 || index >= tabs.getTabCount()) return;
        if (isPinned(tabs, index)) return;
        Component comp = tabs.getComponentAt(index);
        if (comp instanceof SqlEditorPanel editor) {
            recentlyClosed.add(0, new ClosedTabInfo(editor.getTabTitle(), editor.getText(),
                editor.getConnectionName(), editor.getSchema(), editor.getFilePath()));
            if (recentlyClosed.size() > MAX_RECENTLY_CLOSED)
                recentlyClosed.remove(recentlyClosed.size() - 1);
            TabInfo ti = new TabInfo();
            ti.connName = editor.getConnectionName();
            ti.tabTitle = editor.getTabTitle();
            ti.filePath = editor.getFilePath();
            ti.open = false;
            removeFromClosedTabs(ti);
            closedTabs.add(0, ti);

            if (editor.isModified()) {
                int ret = JOptionPane.showConfirmDialog(this,
                    "保存对 \"" + editor.getTabTitle() + "\" 的更改?",
                    "未保存更改", JOptionPane.YES_NO_CANCEL_OPTION);
                if (ret == JOptionPane.CANCEL_OPTION) return;
                if (ret == JOptionPane.YES_OPTION) {
                    if (!saveFile(editor)) return;
                }
            }
        } else if (comp instanceof SourceViewerPanel sv) {
            recentlyClosed.add(0, new ClosedTabInfo(sv.getTabTitle(), sv.getTextArea().getText(), sv.getConnName(), null, null));
            if (recentlyClosed.size() > MAX_RECENTLY_CLOSED)
                recentlyClosed.remove(recentlyClosed.size() - 1);
        }
        tabs.removeTabAt(index);
        if (tabs == editorTabs) {
            saveWorkspace();
            if (editorTabs.getTabCount() == 0) showWelcome();
        }
    }

    // ── tab context menu ──────────────────────────────────────────

    private boolean isPinned(JTabbedPane tabs, int index) {
        if (index < 0 || index >= tabs.getTabCount()) return false;
        return pinnedTabs.contains(tabs.getComponentAt(index));
    }

    private void togglePin(JTabbedPane tabs, int index) {
        Component comp = tabs.getComponentAt(index);
        if (pinnedTabs.contains(comp)) pinnedTabs.remove(comp);
        else pinnedTabs.add(comp);
        updateEditorTabComponent(index, tabs);
    }

    private void installTabContextMenu(JTabbedPane tabs) {
        tabs.addMouseListener(new java.awt.event.MouseAdapter() {
            private void showMenu(java.awt.event.MouseEvent e) {
                int idx = tabs.indexAtLocation(e.getX(), e.getY());
                if (idx >= 0) {
                    tabs.setSelectedIndex(idx);
                    showTabContextMenu(e.getX(), e.getY(), idx, tabs);
                }
            }
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showMenu(e);
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showMenu(e);
            }
        });
    }

    private void showTabContextMenu(int x, int y, int index, JTabbedPane tabs) {
        Component comp = tabs.getComponentAt(index);
        JPopupMenu menu = new JPopupMenu();

        addItem(menu, "关闭", "x", () -> closeTab(tabs, index));
        addItem(menu, "关闭其他", "x", () -> closeOtherTabs(tabs, index));
        addItem(menu, "关闭全部", "x", () -> closeAllTabs(tabs));
        addItem(menu, "关闭未修改", "x", () -> closeUnmodifiedTabs(tabs, index));
        addItem(menu, "关闭左侧标签", "x", () -> closeLeftTabs(tabs, index));
        addItem(menu, "关闭右侧标签", "x", () -> closeRightTabs(tabs, index));
        menu.addSeparator();
        boolean pinned = isPinned(tabs, index);
        addItem(menu, pinned ? "取消固定" : "固定标签", pinned ? "pin-off" : "pin", () -> togglePin(tabs, index));
        menu.addSeparator();
        addItem(menu, "向右拆分", "split-vertical", () -> splitEditor(tabs, index, false));
        addItem(menu, "向下拆分", "split-vertical", () -> splitEditor(tabs, index, true));
        menu.addSeparator();
        if (comp instanceof SqlEditorPanel) {
            addItem(menu, "开始执行  (Ctrl+Shift+F10)", "execute", () -> executeActiveEditor());
            menu.addSeparator();
        }
        addItem(menu, "另存为...", "save-plus", () -> saveActiveFileAs());
        addItem(menu, "复制文件名", "copy", () -> copyFileName(tabs, index));
        if (comp instanceof SqlEditorPanel ep && ep.getFilePath() != null) {
            addItem(menu, "复制完整路径", "copy", () -> copyFilePath(ep));
        }
        menu.addSeparator();

        JMenu openInMenu = new JMenu("用其他方式打开");
        addItem(openInMenu, "文件管理器", null, () -> openInFileManager(comp));
        addItem(openInMenu, "终端", null, () -> openInTerminal(comp));
        addItem(openInMenu, "外部编辑器", null, () -> openInExternalEditor(comp));
        menu.add(openInMenu);

        JMenu histMenu = new JMenu("本地历史");
        addItem(histMenu, "显示历史", "history", () -> showLocalHistory(comp));
        addItem(histMenu, "对比上个版本", "compare", () -> diffLocalHistory(comp));
        addItem(histMenu, "恢复", "refresh-ccw", () -> restoreLocalHistory(comp));
        menu.add(histMenu);

        menu.addSeparator();
        addItem(menu, "重新打开已关闭标签", "refresh-ccw", () -> reopenClosedTab());

        menu.show(tabs, x, y);
    }

    private static void addItem(JMenu menu, String text, String icon, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (icon != null) item.setIcon(IconUtil.menuIcon(icon));
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private static void addItem(JPopupMenu menu, String text, String icon, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (icon != null) item.setIcon(IconUtil.menuIcon(icon));
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void closeOtherTabs(JTabbedPane tabs, int index) {
        for (int i = tabs.getTabCount() - 1; i >= 0; i--) {
            if (i != index) closeTab(tabs, i);
            if (i < index) index--;
        }
    }

    private void closeAllTabs(JTabbedPane tabs) {
        for (int i = tabs.getTabCount() - 1; i >= 0; i--) closeTab(tabs, i);
    }

    private void closeUnmodifiedTabs(JTabbedPane tabs, int keepIndex) {
        for (int i = tabs.getTabCount() - 1; i >= 0; i--) {
            if (i == keepIndex) continue;
            Component c = tabs.getComponentAt(i);
            if (c instanceof SqlEditorPanel ep && !ep.isModified()) {
                closeTab(tabs, i);
                if (i < keepIndex) keepIndex--;
            }
        }
    }

    private void closeLeftTabs(JTabbedPane tabs, int index) {
        for (int i = index - 1; i >= 0; i--) closeTab(tabs, i);
    }

    private void closeRightTabs(JTabbedPane tabs, int index) {
        for (int i = tabs.getTabCount() - 1; i > index; i--) closeTab(tabs, i);
    }

    private void copyFileName(JTabbedPane tabs, int index) {
        Component comp = tabs.getComponentAt(index);
        String name;
        if (comp instanceof SqlEditorPanel ep) name = ep.getFileName();
        else name = tabs.getTitleAt(index);
        if (name != null) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(name), null);
            statusBar.setMessage("已复制: " + name);
        }
    }

    private void copyFilePath(SqlEditorPanel ep) {
        String p = ep.getFilePath();
        if (p != null) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(p), null);
            statusBar.setMessage("已复制: " + p);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("win");
    }

    private void openInFileManager(Component comp) {
        String path = comp instanceof SqlEditorPanel ep ? ep.getFilePath() : null;
        if (path == null) { statusBar.setMessage("无文件路径"); return; }
        try {
            File f = new File(path);
            if (isWindows()) {
                Runtime.getRuntime().exec("explorer /select,\"" + path + "\"");
            } else {
                // Linux/Kylin: open parent folder
                Runtime.getRuntime().exec(new String[]{"xdg-open", f.getParent()});
            }
        } catch (Exception ex) {
            log.warn("openInFileManager failed", ex);
        }
    }

    private void openInTerminal(Component comp) {
        String path = comp instanceof SqlEditorPanel ep ? ep.getFilePath() : null;
        if (path == null) { statusBar.setMessage("无文件路径"); return; }
        try {
            String dir = new File(path).getParent();
            if (isWindows()) {
                Runtime.getRuntime().exec("cmd /c start cmd", null, new File(dir));
            } else {
                // Linux/Kylin: try common terminal emulators
                String[] terms = {"x-terminal-emulator", "gnome-terminal", "konsole", "xfce4-terminal", "mate-terminal"};
                boolean launched = false;
                for (String t : terms) {
                    try {
                        Runtime.getRuntime().exec(new String[]{t, "--working-directory=" + dir});
                        launched = true; break;
                    } catch (Exception ignored) {}
                }
                if (!launched) {
                    // fallback: open folder in file manager
                    Runtime.getRuntime().exec(new String[]{"xdg-open", dir});
                }
            }
        } catch (Exception ex) {
            log.warn("openInTerminal failed", ex);
        }
    }

    private void openInExternalEditor(Component comp) {
        String path = comp instanceof SqlEditorPanel ep ? ep.getFilePath() : null;
        if (path == null) { statusBar.setMessage("无文件路径"); return; }
        try {
            File f = new File(path);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(f);
            } else if (!isWindows()) {
                // Linux fallback
                Runtime.getRuntime().exec(new String[]{"xdg-open", path});
            }
        } catch (Exception ex) {
            log.warn("openInExternalEditor failed", ex);
        }
    }

    // ── local history ─────────────────────────────────────────────

    private Path localHistoryDir() {
        Path dir = configManager.getConfigPath().resolve(".local-history");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    private void saveLocalHistory(SqlEditorPanel editor) {
        String key = editor.getFilePath() != null ? editor.getFilePath() : editor.getTabTitle();
        String content = editor.getText();
        saveHistorySnapshotAsync(key, content);
    }

    private void saveHistorySnapshotAsync(String key, String content) {
        new Thread(() -> saveHistorySnapshot(key, content), "history-save").start();
    }

    private void saveHistorySnapshot(String key, String content) {
        String ts = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        try {
            Path dir = localHistoryDir().resolve(sanitizeFileName(key));
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(ts + ".sql"), content);
            pruneHistory(dir);
        } catch (IOException ignored) {}
    }

    private static void pruneHistory(Path dir) {
        File[] files = dir.toFile().listFiles((d, fn) -> fn.endsWith(".sql"));
        if (files != null && files.length > 30) {
            java.util.Arrays.sort(files);
            for (int i = 0; i < files.length - 30; i++) files[i].delete();
        }
    }

    private static String sanitizeFileName(String n) {
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void showLocalHistory(Component comp) {
        String name = getHistoryKey(comp);
        if (name == null) return;
        File dir = localHistoryDir().resolve(name).toFile();
        File[] files = dir.listFiles((d, fn) -> fn.endsWith(".sql"));
        if (files == null || files.length == 0) {
            JOptionPane.showMessageDialog(this, "暂无本地历史");
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
        String[] display = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            display[i] = files[i].getName().replace(".sql", "");
        }
        String sel = (String) JOptionPane.showInputDialog(this,
            "选择要查看的版本:", "本地历史",
            JOptionPane.PLAIN_MESSAGE, null, display, display[0]);
        if (sel == null) return;
        MainFrame frame = this;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return Files.readString(dir.toPath().resolve(sel + ".sql"));
            }
            @Override protected void done() {
                try {
                    String content = get();
                    openInNewEditor("-- " + name + " @" + sel + "\n\n" + content, null);
                } catch (Exception ex) {
                    log.warn("read local history failed", ex);
                }
            }
        }.execute();
    }

    private void diffLocalHistory(Component comp) {
        String current;
        if (comp instanceof SqlEditorPanel ep) {
            current = ep.getText();
        } else if (comp instanceof SourceViewerPanel sv) {
            current = sv.getTextArea().getText();
        } else {
            return;
        }
        String name = getHistoryKey(comp);
        if (name == null) return;
        File dir = localHistoryDir().resolve(name).toFile();
        File[] files = dir.listFiles((d, fn) -> fn.endsWith(".sql"));
        if (files == null || files.length < 1) {
            JOptionPane.showMessageDialog(this, "暂无历史版本");
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
        String currentRef = current;
        File latestFile = files[0];
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                String previous = Files.readString(latestFile.toPath());
                return simpleDiff(currentRef, previous);
            }
            @Override protected void done() {
                try {
                    String diff = get();
                    openInNewEditor("-- 当前 vs " + latestFile.getName().replace(".sql","") + "\n\n" + diff, null);
                } catch (Exception ex) {
                    log.warn("diffLocalHistory failed", ex);
                }
            }
        }.execute();
    }

    private void restoreLocalHistory(Component comp) {
        String name = getHistoryKey(comp);
        if (name == null) return;
        File dir = localHistoryDir().resolve(name).toFile();
        File[] files = dir.listFiles((d, fn) -> fn.endsWith(".sql"));
        if (files == null || files.length == 0) {
            JOptionPane.showMessageDialog(this, "暂无本地历史");
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
        String[] display = new String[files.length];
        for (int i = 0; i < files.length; i++) display[i] = files[i].getName().replace(".sql", "");
        String sel = (String) JOptionPane.showInputDialog(this,
            "选择要恢复的版本:", "恢复本地历史",
            JOptionPane.PLAIN_MESSAGE, null, display, display[0]);
        if (sel == null) return;
        Component compRef = comp;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return Files.readString(dir.toPath().resolve(sel + ".sql"));
            }
            @Override protected void done() {
                try {
                    String content = get();
                    if (compRef instanceof SqlEditorPanel ep) {
                        ep.setText(content);
                    } else if (compRef instanceof SourceViewerPanel sv) {
                        sv.getTextArea().setText(content);
                    }
                    statusBar.setMessage("已恢复到: " + sel);
                } catch (Exception ex) {
                    log.warn("restoreLocalHistory failed", ex);
                }
            }
        }.execute();
    }

    private String getHistoryKey(Component comp) {
        if (comp instanceof SqlEditorPanel ep) {
            String fp = ep.getFilePath();
            if (fp != null) return sanitizeFileName(fp);
            return sanitizeFileName(ep.getTabTitle());
        }
        if (comp instanceof SourceViewerPanel sv) {
            return sanitizeFileName(sv.getTabTitle());
        }
        return null;
    }

    private static String simpleDiff(String current, String previous) {
        String[] curLines = current.split("\n", -1);
        String[] prevLines = previous.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int max = Math.max(curLines.length, prevLines.length);
        for (int i = 0; i < max; i++) {
            String c = i < curLines.length ? curLines[i] : "";
            String p = i < prevLines.length ? prevLines[i] : "";
            if (!c.equals(p)) {
                sb.append("<<< 当前\n").append(c).append("\n---\n").append(p).append("\n>>> 历史\n\n");
            }
        }
        if (sb.isEmpty()) sb.append("(无差异)");
        return sb.toString();
    }

    // ── editor splitting ─────────────────────────────────────────

    private void splitEditor(JTabbedPane tabs, int tabIndex, boolean vertical) {
        if (tabs != editorTabs) return; // only split from main tab set
        Component comp = tabs.getComponentAt(tabIndex);
        String title = tabs.getTitleAt(tabIndex);
        tabs.removeTabAt(tabIndex);

        if (secondaryTabs == null) {
            secondaryTabs = new JTabbedPane();
            secondaryTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            installTabContextMenu(secondaryTabs);
            secondaryTabs.addChangeListener(e -> {
                Component c = secondaryTabs.getSelectedComponent();
                if (c instanceof SqlEditorPanel ae) {
                    rightPanel.setActiveEditor(ae);
                } else if (c instanceof SourceViewerPanel sv) {
                    rightPanel.setActiveSourceViewer(sv);
                }
            });
        }
        secondaryTabs.addTab(title, comp);
        int si = secondaryTabs.indexOfComponent(comp);
        secondaryTabs.setTabComponentAt(si, buildTabComponent(si, comp, secondaryTabs));

        if (editorSplit == null) {
            editorSplit = new JSplitPane(
                vertical ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT,
                editorTabs, secondaryTabs);
            editorSplit.setResizeWeight(0.5);
            tabContainer.removeAll();
            tabContainer.add(editorSplit, BorderLayout.CENTER);
            tabContainer.revalidate();
            tabContainer.repaint();
        } else {
            editorSplit.revalidate();
            editorSplit.repaint();
        }
    }

    private void reopenClosedTab() {
        if (recentlyClosed.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有已关闭的标签");
            return;
        }
        ClosedTabInfo info = recentlyClosed.remove(0);
        if (info.filePath != null) {
            openOrSwitchToFile(info.filePath);
        } else if (info.content != null) {
            openInNewEditor(info.content, info.connName, info.schema);
        }
    }

    private SqlEditorPanel getActiveEditor() {
        Component comp = editorTabs.getSelectedComponent();
        return (comp instanceof SqlEditorPanel) ? (SqlEditorPanel) comp : null;
    }

    private void removeFromClosedTabs(TabInfo ti) {
        String key = (ti.connName != null ? ti.connName : "") + "|" + ti.tabTitle;
        closedTabs.removeIf(ct -> {
            String ck = (ct.connName != null ? ct.connName : "") + "|" + ct.tabTitle;
            return ck.equals(key);
        });
    }

    private SqlEditorPanel findEditorByTabInfo(TabInfo ti) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component c = editorTabs.getComponentAt(i);
            if (c instanceof SqlEditorPanel ep
                && ti.tabTitle.equals(editorTabs.getTitleAt(i))
                && (ti.connName == null || ti.connName.equals(ep.getConnectionName()))) {
                return ep;
            }
        }
        return null;
    }

    private void loadSavedConnections() {
        var connections = configManager.loadConnections();
        objectBrowser.loadAll(connectionManager, connections);
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component comp = editorTabs.getComponentAt(i);
            if (comp instanceof SqlEditorPanel ep) ep.setConnections(connections);
        }
        statusBar.setMessage("已加载 " + connections.size() + " 个已保存的连接");
    }

    private void showConnectionDialog() {
        showConnectionDialog(null);
    }

    private void showConnectionDialog(String connName) {
        ConnectionDialog dialog = new ConnectionDialog(this, configManager, connectionManager, connName);
        dialog.setVisible(true);
        String saved = dialog.getSavedConnName();
        loadSavedConnections();
        if (saved != null) {
            syncConnectionMetadata(saved);
        }
    }

    private void syncConnectionMetadata(String connName) {
        ConnectionInfo info = configManager.loadConnections().stream()
                .filter(c -> c.getName().equals(connName))
                .findFirst().orElse(null);
        if (info == null) return;

        statusBar.setSyncProgress("同步元数据: " + connName, 0);

        new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish(0);
                if (!connectionManager.isConnected(connName)) {
                    connectionManager.connect(info);
                }
                publish(5);

                MetadataCache cache = MetadataCache.getInstance();
                java.util.List<String> schemas = new java.util.ArrayList<>();

                try (java.sql.Connection conn = connectionManager.getConnection(connName)) {
                    java.sql.DatabaseMetaData meta = conn.getMetaData();
                    String dbProduct = meta.getDatabaseProductName().toLowerCase();
                    publish(10);

                    try (java.sql.ResultSet rs = meta.getSchemas()) {
                        while (rs.next()) {
                            String s = rs.getString("TABLE_SCHEM");
                            if (s != null && !s.isEmpty()) schemas.add(s);
                        }
                    }
                    if (schemas.isEmpty() && info.getSchema() != null && !info.getSchema().isEmpty()) {
                        schemas.add(info.getSchema());
                    }

                    cache.putSchemas(connName, dbProduct, schemas);
                    int total = schemas.size();
                    if (total == 0) return null;

                    for (int i = 0; i < total; i++) {
                        String schema = schemas.get(i);
                        java.util.List<String> tables = new java.util.ArrayList<>();
                        try (java.sql.ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE", "VIEW"})) {
                            while (rs.next()) {
                                String tn = rs.getString("TABLE_NAME");
                                if (tn != null) tables.add(tn);
                            }
                        }
                        cache.putObjects(connName, schema, "TABLE", tables);

                        java.util.List<String> views = new java.util.ArrayList<>();
                        try (java.sql.ResultSet rs = meta.getTables(null, schema, "%", new String[]{"VIEW"})) {
                            while (rs.next()) {
                                String tn = rs.getString("TABLE_NAME");
                                if (tn != null) views.add(tn);
                            }
                        }
                        if (!views.isEmpty()) cache.putObjects(connName, schema, "VIEW", views);

                        int pct = 10 + (i + 1) * 85 / total;
                        publish(pct);
                    }
                }
                publish(100);
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int pct = chunks.get(chunks.size() - 1);
                String text = "同步元数据: " + connName;
                statusBar.setSyncProgress(text, pct);
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusBar.setMessage(connName + " 元数据同步完成");
                    loadSavedConnections();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String msg = cause.getMessage();
                    statusBar.setMessage("元数据同步失败: " + msg);
                    JOptionPane.showMessageDialog(MainFrame.this,
                        "连接 '" + connName + "' 失败:\n" + msg,
                        "连接失败", JOptionPane.ERROR_MESSAGE);
                }
                Timer t = new Timer(4000, ev -> statusBar.hideSyncProgress());
                t.setRepeats(false);
                t.start();
            }
        }.execute();
    }

    private void executeActiveEditor() { executeSql(false); }
    private void executeAppendEditor() { executeSql(true); }

    private void executeSql(boolean append) {
        SqlEditorPanel editor = getActiveEditor();
        if (editor == null) return;

        if (!append) bottomPanel.clearAll();

        String connName = editor.getConnectionName();
        if (connName == null) {
            JOptionPane.showMessageDialog(this, "此标签页未绑定数据库连接，请从对象浏览器打开");
            return;
        }
        String schema = editor.getSchema();
        if (!connectionManager.isConnected(connName)) {
            var connections = configManager.loadConnections();
            for (var ci : connections) {
                if (ci.getName().equals(connName)) {
                    try { connectionManager.connect(ci); } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "连接失败: " + ex.getMessage());
                        return;
                    }
                    break;
                }
            }
        }

        String sel = editor.getSelectedText();
        int qto = connectionManager.getQueryTimeout(connName);

        if (sel != null) {
            int execLine = editor.getSelectionStartLine();
            int skip = 0;
            for (int i = 0; i < sel.length(); i++) {
                if (!Character.isWhitespace(sel.charAt(i))) break;
                skip++;
            }
            if (skip > 0) {
                try {
                    execLine = 1 + editor.getTextArea().getLineOfOffset(editor.getTextArea().getSelectionStart() + skip);
                } catch (BadLocationException ignored) {}
            }
            String[] parts = sel.split(";", -1);
            if (parts.length > 1) {
                editor.clearExecResults();
                bottomPanel.setBatchExecuting(true);
                bottomPanel.appendMessage("----- 多语句执行 --------------------------");
                int baseOffset = editor.getTextArea().getSelectionStart();
                SqlEditorPanel edRef = editor;
                int execLineFinal = execLine;
                // Pre-compute line numbers on EDT
                int[] stmtLines = new int[parts.length];
                int cumulativeOffset = 0;
                for (int i = 0; i < parts.length; i++) {
                    String stmt = parts[i].trim();
                    int partLen = parts[i].length();
                    if (!stmt.isEmpty()) {
                        int stmtIndex = parts[i].indexOf(stmt);
                        int lineOff = baseOffset + cumulativeOffset + Math.max(stmtIndex, 0);
                        try {
                            stmtLines[i] = 1 + editor.getTextArea().getLineOfOffset(lineOff);
                        } catch (BadLocationException e) {
                            stmtLines[i] = execLineFinal;
                        }
                    } else {
                        stmtLines[i] = -1;
                    }
                    cumulativeOffset += partLen + 1;
                }
                // 多语句后台执行，避免 JDBC 阻塞 EDT
        new SwingWorker<List<StmtResult>, Void>() {
                    @Override protected List<StmtResult> doInBackground() {
                        List<StmtResult> results = new ArrayList<>();
                        try (Connection conn = connectionManager.getConnection(connName)) {
                            applySchemaIfNeeded(conn, connName, schema);
                            var executor = new com.kylin.plsql.core.db.SqlExecutor();
                            for (int i = 0; i < parts.length; i++) {
                                String stmt = parts[i].trim();
                                if (!stmt.isEmpty()) {
                                    int line = stmtLines[i];
                                    var result = executor.execute(conn, stmt, qto);
                                    results.add(new StmtResult(result, stmt, line));
                                }
                            }
                        } catch (Exception e) {
                            // will be handled as empty list
                        }
                        return results;
                    }
                    @Override protected void done() {
                        try {
                            List<StmtResult> results = get();
                            boolean anySuccess = false;
                            String firstSql = null;
                            for (StmtResult sr : results) {
                                if (firstSql == null) firstSql = sr.stmt;
                                bottomPanel.appendMessage("执行: " + sr.stmt);
                                bottomPanel.appendMessage("结果: " + sr.result.getSummary());
                                if (sr.result.isSuccess()) {
                                    anySuccess = true;
                                    bottomPanel.showResult(sr.stmt, sr.result, connName);
                                } else {
                                    bottomPanel.showError(sr.result.error);
                                }
                                edRef.markExecResult(sr.line, sr.result.isSuccess());
                            }
                            if (firstSql != null) {
                                sqlHistory.add(firstSql);
                                rightPanel.addHistoryEntry(firstSql, anySuccess, 0,
                                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                                statusBar.setMessage(anySuccess ? "多语句执行完成" : "多语句执行失败");
                            }
                        } catch (Exception e) {
                            statusBar.setMessage("执行失败: " + e.getMessage());
                            bottomPanel.showError(e.getMessage());
                        } finally {
                            bottomPanel.setBatchExecuting(false);
                            bottomPanel.appendMessage("-------------------------------");
                        }
                    }
                }.execute();
                return;
            }
            // single statement from selection
            String sql = parts[0].trim();
            executeSingle(editor, connName, schema, sql, execLine, append, qto);
        } else {
            String sql = editor.getCurrentStatement();
            int execLine = editor.getLastExecLine();
            if (sql == null || sql.isBlank()) {
                JOptionPane.showMessageDialog(this, "请输入 SQL 语句");
                return;
            }
            executeSingle(editor, connName, schema, sql, execLine, append, qto);
        }
    }

    private void executeSingle(SqlEditorPanel editor, String connName, String schema, String sql, int execLine, boolean append, int qto) {
        editor.clearExecResults();
        bottomPanel.appendMessage("----- SQL 执行 ----------------------");
        bottomPanel.appendMessage("执行 SQL: " + sql);
        new SwingWorker<SingleResult, Void>() {
            @Override protected SingleResult doInBackground() {
                try (Connection conn = connectionManager.getConnection(connName)) {
                    applySchemaIfNeeded(conn, connName, schema);
                    var executor = new com.kylin.plsql.core.db.SqlExecutor();
                    var result = executor.execute(conn, sql, qto);
                    return new SingleResult(result, null);
                } catch (Exception e) {
                    return new SingleResult(null, e.getMessage());
                }
            }
            @Override protected void done() {
                try {
                    SingleResult sr = get();
                    if (sr.error != null) {
                        editor.clearExecResults();
                        bottomPanel.appendMessage("执行失败: " + sr.error);
                        bottomPanel.appendMessage("-------------------------------");
                        statusBar.setMessage("执行失败: " + sr.error);
                        bottomPanel.showError(sr.error);
                        editor.markExecResult(execLine, false);
                        sqlHistory.add(sql);
                        rightPanel.addHistoryEntry(sql, false, 0,
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        return;
                    }
                    var result = sr.result;
                    bottomPanel.appendMessage("执行耗时: " + result.elapsedMs + "ms");
                    bottomPanel.appendMessage("结果: " + result.getSummary());
                    bottomPanel.appendMessage("-------------------------------");
                    bottomPanel.setBatchExecuting(append);
                    bottomPanel.showResult(sql, result, connName);
                    bottomPanel.setBatchExecuting(false);
                    statusBar.setMessage(result.getSummary());
                    editor.markExecResult(execLine, result.isSuccess());
                    sqlHistory.add(sql);
                    rightPanel.addHistoryEntry(sql, result.isSuccess(), result.elapsedMs,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } catch (Exception e) {
                    editor.clearExecResults();
                    bottomPanel.appendMessage("执行失败: " + e.getMessage());
                    bottomPanel.appendMessage("-------------------------------");
                    statusBar.setMessage("执行失败: " + e.getMessage());
                    bottomPanel.showError(e.getMessage());
                    editor.markExecResult(execLine, false);
                    sqlHistory.add(sql);
                    rightPanel.addHistoryEntry(sql, false, 0,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                }
            }
        }.execute();
    }

    private static class StmtResult {
        final com.kylin.plsql.core.db.SqlExecutor.SqlResult result;
        final String stmt;
        final int line;
        StmtResult(com.kylin.plsql.core.db.SqlExecutor.SqlResult result, String stmt, int line) {
            this.result = result; this.stmt = stmt; this.line = line;
        }
    }

    private static class SingleResult {
        final com.kylin.plsql.core.db.SqlExecutor.SqlResult result;
        final String error;
        SingleResult(com.kylin.plsql.core.db.SqlExecutor.SqlResult result, String error) {
            this.result = result; this.error = error;
        }
    }

    /** Before executing SQL, set the session schema to match the editor's schema combo. */
    private void applySchemaIfNeeded(Connection conn, String connName, String schema) {
        if (schema == null || schema.isEmpty()) return;
        String dbProduct = MetadataCache.getInstance().getDbProduct(connName);
        if (dbProduct == null) return;
        try {
            if (dbProduct.contains("mysql") || dbProduct.contains("mariadb")) {
                try (Statement st = conn.createStatement()) {
                    st.execute("USE " + schema);
                }
            } else if (dbProduct.contains("oracle") && !dbProduct.contains("oceanbase")) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER SESSION SET CURRENT_SCHEMA = " + schema);
                }
            } else {
                conn.setSchema(schema);
            }
        } catch (Exception e) {
            log.warn("设置 schema '{}' 失败: {}", schema, e.getMessage());
        }
    }

    private void formatSql() {
        Component comp = editorTabs.getSelectedComponent();
        if (comp == null) return;
        boolean isSqlEditor = comp instanceof SqlEditorPanel;
        String sql;
        if (isSqlEditor) {
            SqlEditorPanel editor = (SqlEditorPanel) comp;
            String sel = editor.getSelectedText();
            sql = (sel != null && !sel.isBlank()) ? sel : editor.getText();
            if (sql.isBlank()) return;
        } else {
            return;
        }
        Component compRef = comp;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return EngineManager.format(sql);
            }
            @Override protected void done() {
                try {
                    String formatted = get();
                    String engineName = EngineManager.getCurrent().getName();
                    if (isSqlEditor) {
                        ((SqlEditorPanel) compRef).replaceSelection(formatted);
                    }
                    statusBar.setMessage("格式化完成 (" + engineName + ")");
                    ToastManager.show(editorTabs, "格式化完成 - " + engineName, 3000);
                } catch (Exception ex) {
                    statusBar.setMessage("格式化失败");
                    ToastManager.show(editorTabs, "格式化失败: " + ex.getMessage(), 4000);
                }
            }
        }.execute();
    }

    private void toggleCaseSql() {
        Component comp = editorTabs.getSelectedComponent();
        if (comp == null) return;

        String text;
        boolean hasSelection;

        if (comp instanceof SqlEditorPanel ep) {
            String sel = ep.getSelectedText();
            hasSelection = sel != null;
            text = hasSelection ? sel : ep.getText();
            if (text == null || text.isBlank()) return;
            String converted = convertSqlCase(text, caseModeIsUpper);
            if (hasSelection) {
                ep.getTextArea().replaceSelection(converted);
            } else {
                ep.setText(converted);
            }
        } else if (comp instanceof SourceViewerPanel sv) {
            if (!sv.getTextArea().isEditable()) {
                ToastManager.show(sv, "只读模式不可转换");
                return;
            }
            var ta = sv.getTextArea();
            String sel = ta.getSelectedText();
            hasSelection = sel != null;
            text = hasSelection ? sel : ta.getText();
            if (text == null || text.isBlank()) return;
            String converted = convertSqlCase(text, caseModeIsUpper);
            if (hasSelection) {
                ta.replaceSelection(converted);
            } else {
                ta.setText(converted);
            }
        } else {
            return;
        }

        caseModeIsUpper = !caseModeIsUpper;
        caseBtn.setToolTipText(caseModeIsUpper ? "大小写转换 (大写)" : "大小写转换 (小写)");
    }

    private SqlDialect getCurrentDialect(Component comp) {
        String connName = null;
        if (comp instanceof SqlEditorPanel ep) connName = ep.getConnectionName();
        else if (comp instanceof SourceViewerPanel sv) connName = sv.getConnName();
        if (connName != null) {
            String dialectName = bottomPanel.getConnectionDialect(connName);
            if (dialectName != null) return DialectManager.forName(dialectName);
        }
        String dialect = formatOptions.getDialect();
        if (dialect == null || dialect.isEmpty()) dialect = "Oracle";
        return DialectManager.forName(dialect);
    }

    private void showSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, formatOptions, configManager);
        dialog.setVisible(true);
    }

    private void showGlobalSearch() {
        if (globalSearchDialog == null || !globalSearchDialog.isVisible()) {
            globalSearchDialog = new GlobalSearchDialog(this, editorTabs, connectionManager,
                configManager, this::openOrSwitchToFile);
            globalSearchDialog.showDialog();
        } else {
            globalSearchDialog.getSearchField().requestFocusInWindow();
        }
    }

    private void explainPlan() {
        SqlEditorPanel editor = getActiveEditor();
        if (editor == null) return;

        String connName = editor.getConnectionName();
        if (connName == null) {
            JOptionPane.showMessageDialog(this, "请先绑定数据库连接");
            return;
        }

        String sel = editor.getSelectedText();
        String sql = (sel != null && !sel.isBlank()) ? sel : editor.getText();
        int qto = connectionManager.getQueryTimeout(connName);

        new SwingWorker<ExplainPlanResult, Void>() {
            @Override protected ExplainPlanResult doInBackground() {
                boolean closeConn = connectionManager.isAutoCommit(connName);
                try (java.sql.Connection conn = connectionManager.getConnection(connName)) {
                    var executor = new com.kylin.plsql.core.db.SqlExecutor();
                    var result = executor.execute(conn, "EXPLAIN PLAN FOR " + sql, qto);
                    if (!result.isSuccess()) {
                        return new ExplainPlanResult(null, "执行计划失败: " + result.error);
                    }
                    var planResult = executor.execute(conn, "SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY)");
                    if (planResult.isSuccess()) {
                        return new ExplainPlanResult(planResult, "SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY)");
                    }
                    var fallback = executor.execute(conn,
                        "SELECT OPERATION, OPTIONS, OBJECT_NAME, COST, CARDINALITY, BYTES " +
                        "FROM PLAN_TABLE ORDER BY ID");
                    if (fallback.isSuccess() && !fallback.rows.isEmpty()) {
                        return new ExplainPlanResult(fallback, "SELECT OPERATION, OPTIONS, OBJECT_NAME, COST, CARDINALITY, BYTES FROM PLAN_TABLE ORDER BY ID");
                    }
                    return new ExplainPlanResult(null, "执行计划失败: " + planResult.error);
                } catch (Exception e) {
                    return new ExplainPlanResult(null, "执行计划失败: " + e.getMessage());
                }
            }
            @Override protected void done() {
                try {
                    ExplainPlanResult r = get();
                    if (r.result != null) {
                        bottomPanel.showResult(r.sql, r.result, connName);
                        statusBar.setMessage("执行计划完成");
                    } else {
                        bottomPanel.showError(r.error);
                    }
                } catch (Exception e) {
                    statusBar.setMessage("执行计划失败: " + e.getMessage());
                }
            }
        }.execute();
    }

    private static class ExplainPlanResult {
        final com.kylin.plsql.core.db.SqlExecutor.SqlResult result;
        final String sql;
        final String error;
        ExplainPlanResult(com.kylin.plsql.core.db.SqlExecutor.SqlResult result, String sql) {
            this.result = result; this.sql = sql; this.error = null;
        }
        ExplainPlanResult(String error) {
            this.result = null; this.sql = null; this.error = error;
        }
    }

    private void showCallHierarchy() {
        SqlEditorPanel editor = getActiveEditor();
        if (editor == null) return;
        String text = editor.getText();
        var entries = PlSqlNavigator.parse(text);
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "未找到过程/函数定义");
            return;
        }
        var root = PlSqlCallHierarchy.buildCallTree(entries, text);
        var dialog = new CallHierarchyDialog(this, root, (name, line) -> {
            editor.navigateToLine(line);
        });
        dialog.setVisible(true);
    }

    private void showSqlHistoryDialog() {
        var list = sqlHistory.getAll();
        if (list.isEmpty()) {
            ToastManager.show(this, "暂无 SQL 历史记录");
            return;
        }
        new SqlHistoryDialog(MainFrame.this, list, sql -> {
            SqlEditorPanel editor = getActiveEditor();
            if (editor != null) editor.setText(sql);
        }).setVisible(true);
    }

    private void showDataGeneratorDialog() {
        String[] conns = connectionManager.getActiveConnections();
        DataGeneratorDialog dlg = new DataGeneratorDialog(MainFrame.this,
            name -> { try { return connectionManager.getConnection(name); }
            catch (Exception ex) { return null; } });
        dlg.populateConnections(java.util.Arrays.asList(conns));
        dlg.setVisible(true);
    }

    private void showObjectSearchDialog() {
        String[] conns = connectionManager.getActiveConnections();
        ObjectSearchDialog dlg = new ObjectSearchDialog(MainFrame.this,
            name -> { try { return connectionManager.getConnection(name); }
            catch (Exception ex) { return null; } },
            (name, obj) -> ToastManager.show(MainFrame.this, "跳转: " + name + "/" + obj));
        dlg.populateConnections(java.util.Arrays.asList(conns));
        dlg.setVisible(true);
    }

    private void showAdvancedExportDialog() {
        var rp = bottomPanel.getResultPanel();
        if (rp == null) return;
        var model = rp.getCurrentTableModel();
        if (model == null) model = new javax.swing.table.DefaultTableModel();
        String connName = rp.getCurrentConnName();
        new AdvancedExportDialog(MainFrame.this, model, connName, serviceFactory, connectionManager).setVisible(true);
    }

    private void onObjectAction(String connName, String schema, String objectType, String objectName, String action) {
        statusBar.setMessage("对象: " + connName + "/" + schema + "." + objectName + " (" + objectType + ") [" + action + "]");
        if (connName == null) return;
        if (!connectionManager.isConnected(connName)) {
            var connections = configManager.loadConnections();
            for (var ci : connections) {
                if (ci.getName().equals(connName)) {
                    try { connectionManager.connect(ci); } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "连接失败: " + ex.getMessage());
                        return;
                    }
                    break;
                }
            }
        }
        var mainFrame = this;
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                boolean closeConn = connectionManager.isAutoCommit(connName);
                java.sql.Connection conn = null;
                try {
                    conn = connectionManager.getConnection(connName);
                    var executor = new SqlExecutor();
                    switch (action) {
                        case "SELECT", "INSERT", "UPDATE", "DELETE" -> {
                            var columns = executor.getColumns(conn, schema, objectName);
                            var sql = executor.generateDML(conn, schema, objectName, action, columns);
                            SwingUtilities.invokeLater(() -> insertToEditor(sql));
                        }
                        case "SELECT_NEWTAB", "INSERT_NEWTAB", "UPDATE_NEWTAB", "DELETE_NEWTAB" -> {
                            var columns = executor.getColumns(conn, schema, objectName);
                            String dmlType = action.replace("_NEWTAB", "");
                            var sql = executor.generateDML(conn, schema, objectName, dmlType, columns);
                            SwingUtilities.invokeLater(() -> openInNewEditor(sql, connName, schema));
                        }
                        case "PREVIEW" -> {
                            int qto2 = connectionManager.getQueryTimeout(connName);
                            var sql = executor.generatePreviewSQL(conn, schema, objectName);
                            boolean closeExecConn = connectionManager.isAutoCommit(connName);
                            java.sql.Connection execConn = null;
                            try {
                                execConn = connectionManager.getConnection(connName);
                                var executor2 = new SqlExecutor();
                                var result = executor2.execute(execConn, sql, qto2);
                                SwingUtilities.invokeLater(() -> {
            bottomPanel.showResult(sql, result, connName);
                                    statusBar.setMessage(result.getSummary());
                                });
                            } catch (Exception ex) {
                                SwingUtilities.invokeLater(() -> {
                                    statusBar.setMessage("执行失败: " + ex.getMessage());
                                    bottomPanel.showError(ex.getMessage());
                                });
                            } finally {
                                if (closeExecConn && execConn != null) {
                                    try { execConn.close(); } catch (java.sql.SQLException ignored) {}
                                }
                            }
                        }
                        case "DDL" -> {
                            MetadataCache cache = MetadataCache.getInstance();
                            String ddl = cache.getDDL(connName, schema, objectType, objectName);
                            if (ddl == null) {
                                ddl = executor.generateDDL(conn, schema, objectName, objectType);
                                cache.putDDL(connName, schema, objectType, objectName, ddl);
                            }
                            String finalDdl = ddl;
                            SwingUtilities.invokeLater(() -> openInNewEditor(finalDdl, connName, schema));
                        }
                    }
                } catch (Exception e) {
                    log.error("对象操作失败", e);
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(mainFrame, "操作失败: " + e.getMessage()));
                } finally {
                    if (closeConn && conn != null) {
                        try { conn.close(); } catch (java.sql.SQLException ignored) {}
                    }
                }
                return null;
            }
        };
        worker.execute();
    }

    private void showWelcome() {
        leftPanel.ensureDatabaseTab();
        CardLayout cl = (CardLayout) editorPanel.getLayout();
        cl.show(editorPanel, "welcome");
    }

    private void showEditorTabs() {
        CardLayout cl = (CardLayout) editorPanel.getLayout();
        cl.show(editorPanel, "tabs");
        editorPanel.revalidate();
    }

    void saveWorkspace() {
        WorkspaceState state = configManager.loadWorkspace();
        state.lastActiveIndex = editorTabs.getSelectedIndex();
        state.tabs.clear();
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component comp = editorTabs.getComponentAt(i);
            TabState ts = new TabState();
            if (comp instanceof SqlEditorPanel editor) {
                ts.filePath = editor.getFilePath();
                ts.connName = editor.getConnectionName();
                ts.schema = editor.getSchema();
                ts.tabName = editorTabs.getTitleAt(i);
                if (ts.filePath != null) {
                    ts.type = "file";
                    try { ts.content = editor.getTextArea().getText(); } catch (Exception ignored) {}
                } else {
                    ts.type = "console";
                    ts.content = editor.getTextArea().getText();
                }
                ts.caretPosition = editor.getCaretPosition();
                ts.scrollLine = editor.getScrollLine();
                ts.autoTx = editor.isAutoCommit();
            } else if (comp instanceof SourceViewerPanel sv) {
                ts.type = "sourceviewer";
                ts.tabName = editorTabs.getTitleAt(i);
                ts.connName = sv.getConnName();
                ts.schema = sv.getSchema();
                ts.objectName = sv.getObjectName();
                ts.objectType = sv.getObjectType();
                ts.showingBody = sv.isShowingBody();
            } else {
                continue;
            }
            state.tabs.add(ts);
        }
        state.formatProfiles = formatOptions.profilesToMap();
        state.activeFormatProfile = formatOptions.getActiveProfile();
        state.connectionDialects = bottomPanel.getConnectionDialects();
        // 保存对象浏览器展开状态
        state.treeExpandedPaths = objectBrowser.saveExpandedPaths();
        // 保存隐藏 schema 过滤
        state.hiddenSchemas = objectBrowser.getHiddenSchemas();
        // 保存 SQL 执行历史
        state.sqlHistory = sqlHistory.snapshot();
        state.currentEngineIndex = EngineManager.getEngines().indexOf(
            EngineManager.getCurrent());
        configManager.saveWorkspace(state);
        bottomPanel.refreshConnTree();
    }

    private boolean tryRestoreWorkspace() {
        WorkspaceState state = configManager.loadWorkspace();
        if (state.tabs == null || state.tabs.isEmpty()) return false;
        for (TabState ts : state.tabs) {
            if ("sourceviewer".equals(ts.type)) {
                openSourceObject(ts.connName, ts.schema, ts.objectType, ts.objectName);
                // SourceViewer 需要在加载完成后切 spec/body
                continue;
            }
            SqlEditorPanel editor = new SqlEditorPanel(connectionManager, ts.tabName);
            editor.getTextArea().setSyntaxEditingStyle("text/plsql");
            if (ts.content != null) editor.setText(ts.content);
            editor.setFilePath(ts.filePath);
            var connections = configManager.loadConnections();
            editor.setConnections(connections);
            if (ts.connName != null) editor.setConnectionName(ts.connName);
            if (ts.schema != null) editor.setSchema(ts.schema);
            editor.setAutoCommit(ts.autoTx);
        editor.setOnExecute(() -> executeActiveEditor());
        editor.setOnAppendExecute(() -> executeAppendEditor());
        editor.setOnFormat(this::formatSql);
        editor.setOnStatusMessage(msg -> {
    bottomPanel.showToast(msg);
    statusBar.setStatusText(msg);
});
        editor.setOnHistoryRequest(() -> rightPanel.selectHistoryTab());
        editor.setOnConnectionChange(() -> bottomPanel.refreshConnTree());
            int caretPos = ts.caretPosition;
            int scrollLine = ts.scrollLine;
            editorTabs.addTab(ts.tabName != null ? ts.tabName : editor.getTabTitle(), editor);
            int idx = editorTabs.indexOfComponent(editor);
            initTabComponent(idx, editor);
            installCaretListener(editor);
            // 延迟到布局完成后恢复光标和滚动位置
            int finalIdx = idx;
            SwingUtilities.invokeLater(() -> {
                try {
                    if (caretPos >= 0 && caretPos <= editor.getTextArea().getDocument().getLength()) {
                        editor.getTextArea().setCaretPosition(caretPos);
                    }
                    if (scrollLine > 0) {
                        int lineOff = editor.getTextArea().getLineStartOffset(
                            Math.min(scrollLine, editor.getTextArea().getLineCount() - 1));
                        editor.getTextArea().scrollRectToVisible(
                            editor.getTextArea().modelToView(lineOff));
                    }
                } catch (Exception ignored) {}
                // 恢复匹配的标签后激活相应标签
                if (finalIdx == state.lastActiveIndex) {
                    editorTabs.setSelectedIndex(finalIdx);
                }
            });
        }
        // 保存 tab 状态以便 finishLayout 完成后延迟恢复连接和 schema
        this.pendingTabStates = new java.util.ArrayList<>(state.tabs);
        // 恢复格式化配置
        if (state.formatProfiles != null && !state.formatProfiles.isEmpty()) {
            formatOptions.profilesFromMap(state.formatProfiles);
            formatOptions.setActiveProfile(state.activeFormatProfile != null ? state.activeFormatProfile : "默认 (Oracle)");
            formatOptions.switchTo(formatOptions.getActiveProfile());
        }
        // 恢复连接→方言
        if (state.connectionDialects != null) {
            bottomPanel.setConnectionDialects(state.connectionDialects);
        }
        // 恢复对象浏览器展开状态
        if (state.treeExpandedPaths != null) {
            objectBrowser.restoreExpandedPaths(state.treeExpandedPaths);
        }
        // 恢复隐藏 schema 过滤
        if (state.hiddenSchemas != null) {
            objectBrowser.setHiddenSchemas(state.hiddenSchemas);
        }
        // 恢复 SQL 执行历史
        if (state.sqlHistory != null) {
            sqlHistory.loadFrom(state.sqlHistory);
        }
        // 恢复格式化引擎选择
        int engineIdx = state.currentEngineIndex;
        if (engineIdx >= 0 && engineIdx < EngineManager.getEngines().size()) {
            EngineManager.setCurrent(engineIdx);
        }

        // 恢复上次活跃标签（作为后备）
        int idx = state.lastActiveIndex;
        if (idx >= 0 && idx < editorTabs.getTabCount()) {
            editorTabs.setSelectedIndex(idx);
        }
        showEditorTabs();
        return true;
    }

    private void showToast(String msg) {
        JWindow hint = new JWindow();
        JLabel label = new JLabel(msg, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(new Color(0x333333));
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getInstance().resolve("font.top"));
        label.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        hint.add(label);
        hint.pack();
        Point loc = leftPanel.getLocationOnScreen();
        hint.setLocation(loc.x + leftPanel.getWidth() / 2 - hint.getWidth() / 2, loc.y + 30);
        hint.setAlwaysOnTop(true);
        hint.setVisible(true);
        new Timer(2000, ev -> hint.dispose()).start();
    }

    /** 执行外部 SQL 脚本文件（分批提交事务，进度显示在消息面板）。 */
    private void executeSqlScript(String connName, String schema) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("SQL 文件 (*.sql)", "sql"));
        fc.setDialogTitle("选择要执行的 SQL 脚本");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File file = fc.getSelectedFile();
        String content;
        try {
            content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "读取文件失败: " + e.getMessage());
            return;
        }

        String[] raw = content.split(";");
        java.util.List<String> stmts = new java.util.ArrayList<>();
        for (String s : raw) {
            String t = s.trim();
            if (t.isEmpty() || t.startsWith("--") || t.startsWith("/*")) continue;
            if (t.endsWith("/")) t = t.substring(0, t.length() - 1).trim();
            if (!t.isEmpty()) stmts.add(t);
        }
        if (stmts.isEmpty()) { JOptionPane.showMessageDialog(this, "未找到有效的 SQL 语句"); return; }

        int bs = 500;
        try {
            var sizes = configManager.getBatchSizes();
            try (java.sql.Connection conn = connectionManager.getConnection(connName)) {
                bs = sizes.getOrDefault(SqlExecutor.getDbType(conn), 500);
            }
        } catch (Exception ignored) {}
        final int batchSize = bs;

        final String fileName = file.getName();
        bottomPanel.getResultPanel().selectMessageTab();
        bottomPanel.appendMessage("━━━ 执行脚本: " + fileName + " ━━━");
        bottomPanel.appendMessage("共 " + stmts.size() + " 条语句，批量大小: " + batchSize);

        new SwingWorker<Void, String>() {
            int success = 0, failed = 0;
            @Override protected Void doInBackground() {
                boolean closeConn = connectionManager.isAutoCommit(connName);
                java.sql.Connection conn = null;
                try {
                    conn = connectionManager.getConnection(connName);
                    conn.setAutoCommit(false);
                    var executor = new SqlExecutor();
                    int qto = connectionManager.getQueryTimeout(connName);
                    java.util.List<String> batch = new java.util.ArrayList<>();
                    int idx = 0;
                    for (String stmt : stmts) { idx++;
                        String upper = stmt.toUpperCase().trim();
                        boolean isDML = upper.startsWith("INSERT") || upper.startsWith("UPDATE") || upper.startsWith("DELETE") || upper.startsWith("MERGE");
                        if (isDML) {
                            batch.add(stmt);
                            if (batch.size() >= batchSize) {
                                flushBatch(conn, executor, batch, qto, idx);
                            }
                        } else {
                            if (!batch.isEmpty()) flushBatch(conn, executor, batch, qto, idx);
                            var r = executor.execute(conn, stmt, qto);
                            conn.commit();
                            if (r.isSuccess()) { success++; publish("第 " + idx + "/" + stmts.size() + ": OK  " + label(stmt)); }
                            else { failed++; publish("第 " + idx + "/" + stmts.size() + ": 失败 " + label(stmt) + " → " + r.error); }
                        }
                    }
                    if (!batch.isEmpty()) flushBatch(conn, executor, batch, qto, stmts.size());
                } catch (Exception e) { publish("异常: " + e.getMessage()); }
                finally { if (closeConn && conn != null) try { conn.setAutoCommit(true); } catch (Exception ignored) {} }
                return null;
            }
            void flushBatch(java.sql.Connection conn, SqlExecutor executor, java.util.List<String> batch, int qto, int idx) {
                int n = batch.size();
                try {
                    for (String s : batch) { var r = executor.execute(conn, s, qto); if (!r.isSuccess()) throw new Exception(r.error); }
                    conn.commit();
                    success += n;
                    publish("第 " + (idx - n + 1) + "-" + idx + " 批 (" + n + " 条) 提交 → OK");
                } catch (Exception e) {
                    failed += n;
                    try { conn.rollback(); } catch (Exception ignored) {}
                    publish("第 " + (idx - n + 1) + "-" + idx + " 批 (" + n + " 条) 失败 → " + e.getMessage());
                }
                batch.clear();
            }
            String label(String s) { return s.length() > 60 ? s.substring(0, 60) + "..." : s; }
            @Override protected void process(java.util.List<String> msgs) { for (String m : msgs) bottomPanel.appendMessage(m); }
            @Override protected void done() { bottomPanel.appendMessage("━━━ 完毕: " + success + " 成功, " + failed + " 失败 ━━━"); }
        }.execute();
    }
    private void insertToEditor(String sql) {
        SqlEditorPanel editor = getActiveEditor();
        if (editor == null) { openInNewEditor(sql, null); return; }
        RSyntaxTextArea ta = editor.getTextArea();
        int caret = ta.getCaretPosition();
        try {
            int line = ta.getLineOfOffset(caret);
            int lineStart = ta.getLineStartOffset(line);
            int lineEnd = ta.getLineEndOffset(line);
            if (ta.getText(lineStart, lineEnd - lineStart).trim().length() > 0) {
                ta.getDocument().insertString(caret, "\n", null);
                caret = ta.getCaretPosition();
            }
            ta.getDocument().insertString(caret, sql, null);
            ta.setCaretPosition(caret + sql.length());
        } catch (BadLocationException e) {
            ta.append("\n" + sql);
        }
        editor.requestFocusInWindow();
    }

    private void openInNewEditor(String content, String connName) {
        openInNewEditor(content, connName, null);
    }

    private void openInNewEditor(String content, String connName, String schema) {
        SqlEditorPanel editor = new SqlEditorPanel(connectionManager, getNextConsoleName());
        editor.getTextArea().setSyntaxEditingStyle("text/plsql");
        editor.setText(content);
        var connections = configManager.loadConnections();
        editor.setConnections(connections);
        if (connName != null) editor.setConnectionName(connName);
        if (schema != null) editor.setSchema(schema);
        editor.setOnExecute(() -> executeActiveEditor());
        editor.setOnAppendExecute(() -> executeAppendEditor());
        editor.setOnFormat(this::formatSql);
        editor.setOnStatusMessage(msg -> {
    bottomPanel.showToast(msg);
    statusBar.setStatusText(msg);
});
editor.setOnHistoryRequest(() -> rightPanel.selectHistoryTab());
        editor.setOnConnectionChange(() -> bottomPanel.refreshConnTree());
        showEditorTabs();
        editorTabs.addTab(editor.getTabTitle(), editor);
        int idx = editorTabs.indexOfComponent(editor);
        initTabComponent(idx, editor);
        editorTabs.setSelectedComponent(editor);
        editor.getTextArea().requestFocusInWindow();
        statusBar.setMessage("新建: " + editor.getTabTitle());
        installCaretListener(editor);
        saveWorkspace();
    }

    private void openSourceObject(String connName, String schema, String objectType, String objectName) {
        // Check if already open
        String tabKey = schema + "." + objectName;
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            if (tabKey.equals(editorTabs.getTitleAt(i))) {
                editorTabs.setSelectedIndex(i);
                return;
            }
        }
        // Ensure connection is established
        if (!connectionManager.isConnected(connName)) {
            var connections = configManager.loadConnections();
            for (var ci : connections) {
                if (ci.getName().equals(connName)) {
                    try { connectionManager.connect(ci); } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "连接失败: " + ex.getMessage());
                        return;
                    }
                    break;
                }
            }
        }
        SourceViewerPanel viewer = new SourceViewerPanel(connectionManager, connName, schema, objectName, objectType);
        showEditorTabs();
        editorTabs.addTab(tabKey, viewer);
        int idx = editorTabs.indexOfComponent(viewer);
        initTabComponent(idx, viewer);
        editorTabs.setSelectedComponent(viewer);
        viewer.getTextArea().requestFocusInWindow();
        statusBar.setMessage("打开: " + tabKey);
    }

    private void openOrSwitchToFile(String filePath) {
        String fileName = new File(filePath).getName();
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component comp = editorTabs.getComponentAt(i);
            if (comp instanceof SourceViewerPanel sv && fileName.equals(sv.getObjectName())) {
                log.debug("openOrSwitchToFile: switching to existing SourceViewerPanel tab");
                editorTabs.setSelectedIndex(i);
                return;
            }
            if (comp instanceof SqlEditorPanel ep && filePath.equals(ep.getFilePath())) {
                log.debug("openOrSwitchToFile: switching to existing SqlEditorPanel tab");
                editorTabs.setSelectedIndex(i);
                return;
            }
        }
        new SwingWorker<FileContent, Void>() {
            @Override protected FileContent doInBackground() {
                try {
                    if (!new File(filePath).exists()) return null;
                    String content = Files.readString(Path.of(filePath));
                    String objType = detectObjectType(content);
                    return new FileContent(content, objType);
                } catch (IOException e) {
                    return new FileContent(e);
                }
            }
            @Override protected void done() {
                try {
                    FileContent fc = get();
                    if (fc == null) return;
                    if (fc.error != null) {
                        JOptionPane.showMessageDialog(MainFrame.this, "打开文件失败:\n" + fc.error.getMessage());
                        return;
                    }
                    String content = fc.content;
                    String objType = fc.objType;
                    log.debug("openOrSwitchToFile: {} bytes read from {}, objType={}", content.length(), filePath, objType);
                    if (objType != null && ("PACKAGE".equals(objType) || "PACKAGE_BODY".equals(objType))) {
                        log.debug("openOrSwitchToFile: opening in SourceViewerPanel (type={})", objType);
                        SourceViewerPanel viewer = new SourceViewerPanel(connectionManager, null, null,
                            fileName, objType, content);
                        saveHistorySnapshotAsync(filePath, content);
                        showEditorTabs();
                        editorTabs.addTab(viewer.getTabTitle(), viewer);
                        int idx = editorTabs.indexOfComponent(viewer);
                        initTabComponent(idx, viewer);
                        editorTabs.setSelectedComponent(viewer);
                        viewer.getTextArea().requestFocusInWindow();
                        saveWorkspace();
                    } else {
                        log.debug("openOrSwitchToFile: opening in SqlEditorPanel (no object type detected)");
                        SqlEditorPanel editor = new SqlEditorPanel(connectionManager, new File(filePath).getName());
                        editor.getTextArea().setSyntaxEditingStyle("text/plsql");
                        editor.setText(content);
                        editor.setFilePath(filePath);
                        saveHistorySnapshotAsync(filePath, content);
                        editor.resetModified();
                        var connections = configManager.loadConnections();
                        editor.setConnections(connections);
                        editor.setOnExecute(() -> executeActiveEditor());
                        editor.setOnAppendExecute(() -> executeAppendEditor());
                        editor.setOnFormat(MainFrame.this::formatSql);
                        editor.setOnStatusMessage(msg -> {
                            bottomPanel.showToast(msg);
                            statusBar.setStatusText(msg);
                        });
                        editor.setOnHistoryRequest(() -> rightPanel.selectHistoryTab());
                        editor.setOnConnectionChange(() -> bottomPanel.refreshConnTree());
                        showEditorTabs();
                        editorTabs.addTab(editor.getTabTitle(), editor);
                        int idx = editorTabs.indexOfComponent(editor);
                        initTabComponent(idx, editor);
                        editorTabs.setSelectedComponent(editor);
                        editor.getTextArea().requestFocusInWindow();
                        installCaretListener(editor);
                        saveWorkspace();
                    }
                } catch (Exception e) {
                    log.error("openOrSwitchToFile failed", e);
                }
            }
        }.execute();
    }

    public ConnectionManager getConnectionManager() { return connectionManager; }
    public ConfigManager getConfigManager() { return configManager; }

    public void switchTheme(AppTheme theme) {
        // 1) Switch L&F first so UIManager colors are updated
        if ("DARK".equals(theme.config("flatlaf"))) {
            try { UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarculaLaf()); }
            catch (Exception ignored) {}
        } else {
            try { UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf()); }
            catch (Exception ignored) {}
        }
        // Set after L&F so UIManager.put is not overwritten by setLookAndFeel
        Color treeFg = theme.resolve("list.fg");
        UIManager.put("Tree.foreground", treeFg);
        UIManager.put("Tree.iconForeground", treeFg);
        Color mc = theme.resolve("bg.toolbar");
        UIManager.put("MenuBar.background", mc);
        UIManager.put("MenuBar.borderColor", mc);
        UIManager.put("Menu.background", mc);
        UIManager.put("MenuItem.background", mc);
        UIManager.put("TitlePane.background", mc);
        UIManager.put("TitlePane.unifiedBackground", false);
        // Prevent FlatLaf's PanelUI.installDefaults from overwriting FlatTitlePane background
        Color savedPanelBg = UIManager.getColor("Panel.background");
        UIManager.put("Panel.background", mc);
        SwingUtilities.updateComponentTreeUI(this);
        UIManager.put("Panel.background", savedPanelBg);

        // 2) Now fire ThemeManager listeners — UIManager colors are already correct
        ThemeManager.getInstance().switchTo(theme);

        ThemeManager.getInstance().saveToConfig(configManager);
        reapplyTheme();

        // Restore divider positions after updateComponentTreeUI
        SwingUtilities.invokeLater(() -> {
            if (leftSplitRef != null && leftSplitRef[0] != null)
                leftSplitRef[0].setDividerLocation(250);
            if (mainSplitRef != null && mainSplitRef[0] != null) {
                int w = mainSplitRef[0].getWidth();
                int rightW = rightPanel.getPreferredSize().width;
                mainSplitRef[0].setDividerLocation(Math.max(w - rightW, w / 2));
            }
        });
    }

    public void reapplyTheme() {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component c = editorTabs.getComponentAt(i);
            if (c instanceof SqlEditorPanel ep) {
                ep.applyTheme();
                JPopupMenu p = ep.getTextArea().getComponentPopupMenu();
                if (p != null) SwingUtilities.updateComponentTreeUI(p);
            }
            else if (c instanceof SourceViewerPanel sv) {
                sv.applyTheme();
                JPopupMenu p = sv.getTextArea().getComponentPopupMenu();
                if (p != null) SwingUtilities.updateComponentTreeUI(p);
            }
        }
        if (secondaryTabs != null) {
            for (int i = 0; i < secondaryTabs.getTabCount(); i++) {
                Component c = secondaryTabs.getComponentAt(i);
                if (c instanceof SqlEditorPanel ep) {
                    ep.applyTheme();
                    JPopupMenu p = ep.getTextArea().getComponentPopupMenu();
                    if (p != null) SwingUtilities.updateComponentTreeUI(p);
                }
                else if (c instanceof SourceViewerPanel sv) {
                    sv.applyTheme();
                    JPopupMenu p = sv.getTextArea().getComponentPopupMenu();
                    if (p != null) SwingUtilities.updateComponentTreeUI(p);
                }
            }
        }
        Color tabBg = ThemeManager.getInstance().resolve("bg.toolbar");
        if (editorSplit != null) {
            editorSplit.setBackground(ThemeManager.getInstance().resolve("bg.panel"));
        }
        if (tabContainer != null) {
            tabContainer.setBackground(tabBg);
            tabContainer.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1,
                ThemeManager.getInstance().resolve("border.light")));
        }
        Color panelBg = ThemeManager.getInstance().resolve("bg.panel");
        Color editorBg = ThemeManager.getInstance().resolve("bg.editor");
        Color activeFg = ThemeManager.getInstance().resolve("fg.tab.active");
        Color inactiveFg = ThemeManager.getInstance().resolve("fg.tab.inactive");
        UIManager.put("TabbedPane.tabAreaBackground", tabBg);
        UIManager.put("TabbedPane.background", panelBg);
        UIManager.put("TabbedPane.selectedBackground", editorBg);
        UIManager.put("TabbedPane.contentAreaColor", editorBg);
        UIManager.put("TabbedPane.foreground", inactiveFg);
        UIManager.put("TabbedPane.selectedForeground", activeFg);
        editorTabs.updateUI();
        editorTabs.setBackground(tabBg);
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            updateEditorTabComponent(i, editorTabs);
        }
        editorTabs.repaint();
        if (secondaryTabs != null) {
            secondaryTabs.updateUI();
            secondaryTabs.setBackground(tabBg);
            for (int i = 0; i < secondaryTabs.getTabCount(); i++) {
                updateEditorTabComponent(i, secondaryTabs);
            }
            secondaryTabs.repaint();
        }
        if (toolbar != null) {
            toolbar.setBackground(ThemeManager.getInstance().resolve("bg.toolbar"));
            toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0,
                ThemeManager.getInstance().resolve("border.light")));
        }
        if (menuBar != null) {
            Color mc = ThemeManager.getInstance().resolve("bg.toolbar");
            UIManager.put("MenuBar.background", mc);
            UIManager.put("MenuBar.borderColor", mc);
            UIManager.put("Menu.background", mc);
            UIManager.put("MenuItem.background", mc);
            UIManager.put("TitlePane.background", mc);
            UIManager.put("TitlePane.unifiedBackground", false);
            menuBar.updateUI();
            menuBar.setBackground(mc);
            menuBar.setOpaque(true);
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu menu = menuBar.getMenu(i);
                if (menu == null) continue;
                menu.updateUI();
                menu.setBackground(mc);
                menu.setOpaque(true);
                for (int j = 0; j < menu.getItemCount(); j++) {
                    JMenuItem item = menu.getItem(j);
                    if (item != null) {
                        item.setBackground(mc);
                        item.setOpaque(true);
                    }
                }
            }
        }
        leftPanel.applyTheme();
        rightPanel.applyTheme();
        bottomPanel.applyTheme();
        statusBar.applyTheme();
        if (welcomePanel != null) welcomePanel.applyTheme();

        // Update tree cell renderer UI to pick up new L&F colors
        if (objectBrowser != null) {
            var r = objectBrowser.getTree().getCellRenderer();
            if (r instanceof DefaultTreeCellRenderer dtr) dtr.updateUI();
        }

        revalidate();
        repaint();
    }

    private static JButton tb(String iconName, String fallback, String tip, java.awt.event.ActionListener action) {
        JButton btn = new JButton();
        btn.setToolTipText(tip);
        btn.setFocusable(false);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        btn.addActionListener(action);
        ImageIcon svgIcon = com.kylin.plsql.ui.component.common.IconUtil.loadButtonIcon(iconName, null);
        if (svgIcon != null) {
            btn.setIcon(svgIcon);
        } else {
            java.net.URL url = MainFrame.class.getResource("/icons/" + iconName + ".png");
            if (url != null) {
                btn.setIcon(new ImageIcon(url));
            } else {
                btn.setText(fallback);
            }
        }
        return btn;
    }

    public static String convertSqlCase(String sql, boolean toUpper) {
        StringBuilder sb = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);

            // String literal '...'
            if (c == '\'') {
                int start = i++;
                while (i < sql.length()) {
                    if (sql.charAt(i) == '\'') {
                        i++;
                        if (i < sql.length() && sql.charAt(i) == '\'') { i++; continue; }
                        break;
                    }
                    i++;
                }
                sb.append(sql, start, i);
                continue;
            }

            // Line comment --
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int start = i;
                i += 2;
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                sb.append(sql, start, i);
                continue;
            }

            // Block comment /* */
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < sql.length()) {
                    if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') { i += 2; break; }
                    i++;
                }
                sb.append(sql, start, i);
                continue;
            }

            // Quoted identifier "..."
            if (c == '"') {
                int start = i++;
                while (i < sql.length() && sql.charAt(i) != '"') i++;
                if (i < sql.length()) i++;
                sb.append(sql, start, i);
                continue;
            }

            // Word (identifier/keyword)
            if (Character.isLetter(c) || c == '_' || c == '#' || c == '$') {
                int start = i++;
                while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_' || sql.charAt(i) == '#' || sql.charAt(i) == '$'))
                    i++;
                String word = sql.substring(start, i);
                sb.append(toUpper ? word.toUpperCase(java.util.Locale.ROOT) : word.toLowerCase(java.util.Locale.ROOT));
                continue;
            }

            // Everything else (numbers, operators, whitespace, etc.)
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

}