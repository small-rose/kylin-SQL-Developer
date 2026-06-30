package com.kylin.plsql.ui.dialog;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.config.DbMetadataConfig;
import com.kylin.plsql.core.config.DbMetadataConfig.TypeDef;
import com.kylin.plsql.core.format.CommaPosition;
import com.kylin.plsql.core.format.ExceptionAlign;
import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.FormatOptions.KeywordCase;
import com.kylin.plsql.core.format.ParameterListMode;
import com.kylin.plsql.core.format.ParenthesisSpacing;
import com.kylin.plsql.core.format.SelectColumnMode;
import com.kylin.plsql.core.format.StorageClauseFormat;
import com.kylin.plsql.core.format.WhereAndPosition;
import com.kylin.plsql.core.format.SqlFormatter;
import com.kylin.plsql.core.format.dialect.DialectManager;
import com.kylin.plsql.core.format.dialect.SqlDialect;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SettingsDialog extends JDialog {

    static class KeyDesc {
        final String key;
        final String label;
        KeyDesc(String key, String label) { this.key = key; this.label = label; }
    }

    static class ColorGroup {
        final String name;
        final List<KeyDesc> keys = new ArrayList<>();
        ColorGroup(String name) { this.name = name; }
        ColorGroup add(String k, String l) { keys.add(new KeyDesc(k, l)); return this; }
    }

    static class ColorGroupNode extends DefaultMutableTreeNode {
        final ColorGroup group;
        ColorGroupNode(ColorGroup g) { super(g.name); this.group = g; }
    }

    static class LeafNode extends DefaultMutableTreeNode {
        final KeyDesc desc;
        LeafNode(KeyDesc d) { super(d.label); this.desc = d; }
    }

    static class ColorSwatch extends JPanel {
        final String configKey;
        Color color;
        ColorSwatch(Frame parent, String key, Color initial) {
            super();
            this.configKey = key;
            this.color = initial;
            setPreferredSize(new Dimension(60, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    Color c = JColorChooser.showDialog(parent, "\u9009\u62E9\u989C\u8272 - " + configKey, color);
                    if (c != null) { color = c; setBackground(c); }
                }
            });
        }
    }

    static class TypesTableModel extends AbstractTableModel {
        List<TypeDef> types = new ArrayList<>();
        @Override public int getRowCount() { return types.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int col) {
            return switch (col) { case 0 -> "\u6807\u7B7E"; case 1 -> "\u7C7B\u578B"; case 2 -> "\u6E90"; default -> ""; };
        }
        @Override public Object getValueAt(int row, int col) {
            var t = types.get(row);
            return switch (col) { case 0 -> t.getLabel(); case 1 -> t.getTypeCode(); case 2 -> t.getQueryType(); default -> ""; };
        }
        @Override public void setValueAt(Object v, int row, int col) {
            var t = types.get(row);
            switch (col) { case 0 -> t.setLabel((String) v); case 1 -> t.setTypeCode((String) v); }
            fireTableCellUpdated(row, col);
        }
        @Override public boolean isCellEditable(int row, int col) { return col <= 1; }
    }

    static class ColumnsTableModel extends AbstractTableModel {
        List<DbMetadataConfig.CustomColumn> cols = new ArrayList<>();
        @Override public int getRowCount() { return cols.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int col) {
            return col == 0 ? "\u6807\u7B7E" : "SQL \u67E5\u8BE2";
        }
        @Override public Object getValueAt(int row, int col) {
            var c = cols.get(row);
            return col == 0 ? c.getHeader() : c.getExpression();
        }
        @Override public void setValueAt(Object v, int row, int col) {
            var c = cols.get(row);
            if (col == 0) c.setHeader((String) v);
            else c.setExpression((String) v);
            fireTableCellUpdated(row, col);
        }
        @Override public boolean isCellEditable(int row, int col) { return true; }
    }

    static class MetadataNode extends DefaultMutableTreeNode {
        final DbMetadataConfig config;
        MetadataNode(DbMetadataConfig cfg) {
            super(cfg.getDisplayName() != null ? cfg.getDisplayName() : cfg.getDbTypeKey());
            this.config = cfg;
        }
    }

    private final ConfigManager configManager;
    private final FormatOptions formatOptions;
    private FormatOptions workingOptions; // mutable copy for UI editing
    private JTree settingsTree;
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private JTree metadataTree;
    private DefaultTreeModel metadataTreeModel;
    private JTable typeTable;
    private TypesTableModel typeTableModel;
    private JTextArea sqlArea;
    private JTable columnTable;
    private ColumnsTableModel columnModel;
    private JPanel sqlEditPanel;
    private JPanel columnEditPanel;
    private JLabel helpLabel;
    private List<DbMetadataConfig> metadataConfigs;
    private RSyntaxTextArea previewArea;
    private JComboBox<String> previewSqlCombo;

    private static final String[] FORMAT_UNITS = {"\u79D2", "\u5206", "\u5C0F\u65F6"};
    private static final String[] PREVIEW_SQLS = {
        "SELECT",
        "INSERT",
        "CREATE TABLE",
        "PL/SQL BLOCK"
    };
    private static final String SAMPLE_SELECT =
        "SELECT id, first_name, last_name, email, salary, hire_date, department_id\n" +
        "FROM employees e\n" +
        "LEFT JOIN departments d ON e.department_id = d.department_id\n" +
        "WHERE salary > 5000 AND (status = 'ACTIVE' OR status = 'PROBATION')\n" +
        "GROUP BY department_id\n" +
        "HAVING COUNT(*) > 5\n" +
        "ORDER BY salary DESC, last_name ASC\n" +
        "FETCH FIRST 20 ROWS ONLY";
    private static final String SAMPLE_INSERT =
        "INSERT INTO employees (employee_id, first_name, last_name, email, phone_number, hire_date, job_id, salary, department_id)\n" +
        "VALUES (100, 'John', 'Doe', 'JDOE', '555-0100', SYSDATE, 'IT_PROG', 75000, 60)";
    private static final String SAMPLE_DDL =
        "CREATE TABLE project_assignments (\n" +
        "    assignment_id   NUMBER(10) NOT NULL,\n" +
        "    project_id      NUMBER(10) NOT NULL,\n" +
        "    employee_id     NUMBER(10) NOT NULL,\n" +
        "    role            VARCHAR2(50),\n" +
        "    start_date      DATE DEFAULT SYSDATE,\n" +
        "    end_date        DATE,\n" +
        "    CONSTRAINT pk_assignments PRIMARY KEY (assignment_id),\n" +
        "    CONSTRAINT fk_assign_proj FOREIGN KEY (project_id) REFERENCES projects(project_id),\n" +
        "    CONSTRAINT fk_assign_emp FOREIGN KEY (employee_id) REFERENCES employees(employee_id)\n" +
        ") TABLESPACE users";
    private static final String SAMPLE_PLSQL =
        "CREATE OR REPLACE FUNCTION calculate_bonus(\n" +
        "    p_employee_id  IN NUMBER,\n" +
        "    p_performance  IN VARCHAR2 DEFAULT 'AVERAGE'\n" +
        ") RETURN NUMBER AS\n" +
        "    v_salary       NUMBER;\n" +
        "    v_bonus        NUMBER := 0;\n" +
        "    v_rating       VARCHAR2(20);\n" +
        "BEGIN\n" +
        "    SELECT salary, performance_rating\n" +
        "    INTO v_salary, v_rating\n" +
        "    FROM employees\n" +
        "    WHERE employee_id = p_employee_id;\n" +
        "    IF v_rating = 'EXCELLENT' THEN\n" +
        "        v_bonus := v_salary * 0.20;\n" +
        "    ELSIF v_rating = 'GOOD' THEN\n" +
        "        v_bonus := v_salary * 0.10;\n" +
        "    ELSE\n" +
        "        v_bonus := v_salary * 0.05;\n" +
        "    END IF;\n" +
        "    RETURN ROUND(v_bonus, 2);\n" +
        "END calculate_bonus;";

    private JComboBox<String> dbTypeCombo;
    private JButton testBtn;
    private JButton saveBtn;
    private JButton cancelBtn;

    private JList<String> connList;
    private DefaultListModel<String> connListModel;

    public SettingsDialog(Frame owner, FormatOptions formatOptions, ConfigManager configManager) {
        super(owner, "\u8BBE\u7F6E", true);
        this.formatOptions = formatOptions;
        this.configManager = configManager;
        this.workingOptions = formatOptions.snapshot();
        setSize(960, 680);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // Left tree + right card panel
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.22);
        split.setContinuousLayout(true);

        settingsTree = buildSettingsTree();
        JScrollPane treeScroll = new JScrollPane(settingsTree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());
        treeScroll.setMinimumSize(new Dimension(160, 0));

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // Build and register all card panels
        cardPanel.add(buildGeneralPanel(), "general");
        cardPanel.add(buildSqlFormatPanel(), "sqlFormat");
        cardPanel.add(buildThemePanel(), "theme");
        cardPanel.add(buildAutosavePanel(), "autosave");
        cardPanel.add(buildMetadataPanel(), "metadata");

        settingsTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                settingsTree.getLastSelectedPathComponent();
            if (node == null) return;
            String card = getCardName(node);
            if (card != null) {
                cardLayout.show(cardPanel, card);
            }
        });

        split.setLeftComponent(treeScroll);
        split.setRightComponent(cardPanel);
        add(split, BorderLayout.CENTER);

        // Expand first level + select first node
        for (int i = 0; i < settingsTree.getRowCount(); i++) {
            settingsTree.expandRow(i);
        }
        SwingUtilities.invokeLater(() -> {
            settingsTree.setSelectionRow(0);
        });

        // Bottom buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveBtn = new JButton("\u5E94\u7528");
        saveBtn.addActionListener(e -> saveSettings());
        cancelBtn = new JButton("\u53D6\u6D88");
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);

        applyTheme();
    }

    private void applyTheme() {
        ThemeManager tm = ThemeManager.getInstance();
        Color bg = tm.resolve("bg.main");
        Color fg = tm.resolve("fg.main");
        Color treeBg = tm.resolve("bg.panel");
        getContentPane().setBackground(bg);
        settingsTree.setBackground(treeBg);
        settingsTree.setForeground(fg);
        cardPanel.setBackground(bg);
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
    }

    // ── Left tree ──

    private JTree buildSettingsTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("\u8BBE\u7F6E");
        DefaultMutableTreeNode general = new DefaultMutableTreeNode("\u901A\u7528");
        root.add(general);

        DefaultMutableTreeNode sqlNode = new DefaultMutableTreeNode("SQL \u683C\u5F0F\u5316");
        root.add(sqlNode);

        DefaultMutableTreeNode theme = new DefaultMutableTreeNode("\u4E3B\u9898");
        root.add(theme);

        DefaultMutableTreeNode editorNode = new DefaultMutableTreeNode("\u7F16\u8F91\u5668");
        DefaultMutableTreeNode autosave = new DefaultMutableTreeNode("\u81EA\u52A8\u4FDD\u5B58");
        editorNode.add(autosave);
        root.add(editorNode);

        DefaultMutableTreeNode dbNode = new DefaultMutableTreeNode("\u6570\u636E\u5E93");
        DefaultMutableTreeNode meta = new DefaultMutableTreeNode("\u5143\u6570\u636E\u914D\u7F6E");
        dbNode.add(meta);
        root.add(dbNode);

        DefaultTreeModel model = new DefaultTreeModel(root);
        JTree tree = new JTree(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(24);
        tree.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return tree;
    }

    private String getCardName(DefaultMutableTreeNode node) {
        String label = node.getUserObject().toString();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
        if (parent == null || parent.isRoot()) {
            return switch (label) {
                case "\u901A\u7528" -> "general";
                case "SQL \u683C\u5F0F\u5316" -> "sqlFormat";
                case "\u4E3B\u9898" -> "theme";
                default -> null;
            };
        }
        String parentLabel = parent.getUserObject().toString();
        if ("\u7F16\u8F91\u5668".equals(parentLabel) && "\u81EA\u52A8\u4FDD\u5B58".equals(label)) return "autosave";
        if ("\u6570\u636E\u5E93".equals(parentLabel) && "\u5143\u6570\u636E\u914D\u7F6E".equals(label)) return "metadata";
        return null;
    }

    // ── General panel (保留原有功能) ──

    private JPanel buildGeneralPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        p.add(new JLabel("\u8FDE\u63A5\u7BA1\u7406:"), c);
        c.gridx = 1; c.weightx = 1;
        connListModel = new DefaultListModel<>();
        connList = new JList<>(connListModel);
        loadConnections();
        var connScroll = new JScrollPane(connList);
        connScroll.setPreferredSize(new Dimension(200, 100));
        p.add(connScroll, c);
        applyPanelTheme(p);
        return p;
    }

    private void loadConnections() {
        connListModel.clear();
        for (var ci : configManager.loadConnections()) {
            connListModel.addElement(ci.getName() + " (" + ci.getDbType() + ")");
        }
    }

    // ── Theme panel (保留原有功能) ──

    private JPanel buildThemePanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        var colorGroup = new java.util.ArrayList<ColorGroup>();
        colorGroup.add(new ColorGroup("\u80CC\u666F (Background)")
            .add("bg.main", "\u4E3B\u80CC\u666F\u989C\u8272")
            .add("bg.editor", "\u7F16\u8F91\u5668\u80CC\u666F")
            .add("bg.panel", "\u9762\u677F\u80CC\u666F")
            .add("bg.toolbar", "\u5DE5\u5177\u680F\u80CC\u666F")
            .add("bg.output", "\u8F93\u51FA\u7A97\u53E3\u80CC\u666F"));
        colorGroup.add(new ColorGroup("\u524D\u666F (Foreground)")
            .add("fg.main", "\u4E3B\u6587\u672C\u989C\u8272")
            .add("fg.secondary", "\u6B21\u8981\u6587\u672C\u989C\u8272")
            .add("fg.muted", "\u6697\u6DE1\u6587\u672C\u989C\u8272")
            .add("fg.title", "\u6807\u9898\u6587\u672C\u989C\u8272")
            .add("fg.tab.active", "\u6807\u7B7E\u9875\u6D3B\u8DC3\u989C\u8272")
            .add("fg.tab.inactive", "\u6807\u7B7E\u9875\u975E\u6D3B\u8DC3\u989C\u8272"));
        colorGroup.add(new ColorGroup("\u9009\u62E9 (Selection)")
            .add("selection.bg", "\u9009\u4E2D\u80CC\u666F\u989C\u8272")
            .add("selection.fg", "\u9009\u4E2D\u6587\u672C\u989C\u8272")
            .add("selection.listBg", "\u5217\u8868\u9009\u4E2D\u80CC\u666F")
            .add("selection.listFg", "\u5217\u8868\u9009\u4E2D\u6587\u672C"));
        colorGroup.add(new ColorGroup("\u8FB9\u6846 (Border)")
            .add("border.default", "\u9ED8\u8BA4\u8FB9\u6846")
            .add("border.light", "\u6D45\u8272\u8FB9\u6846"));
        colorGroup.add(new ColorGroup("\u5F3A\u8C03\u8272 (Accent)")
            .add("accent.green", "\u7EFF\u8272\u5F3A\u8C03")
            .add("accent.tab", "\u6807\u7B7E\u9875\u5F3A\u8C03"));
        colorGroup.add(new ColorGroup("\u7F16\u8F91\u5668 (Editor)")
            .add("editor.caret", "\u5149\u6807\u989C\u8272"));
        colorGroup.add(new ColorGroup("\u5217\u8868 (List)")
            .add("list.bg", "\u5217\u8868\u80CC\u666F")
            .add("list.fg", "\u5217\u8868\u6587\u672C"));
        colorGroup.add(new ColorGroup("\u6EDA\u52A8 (Scroll)")
            .add("scroll.bg", "\u6EDA\u52A8\u6761\u80CC\u666F"));
        colorGroup.add(new ColorGroup("\u6267\u884C\u7ED3\u679C (Execution)")
            .add("exec.success", "\u6267\u884C\u6210\u529F")
            .add("exec.fail", "\u6267\u884C\u5931\u8D25")
            .add("exec.highlight", "\u6267\u884C\u9AD8\u4EAE"));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("\u4E3B\u9898\u989C\u8272");
        for (var g : colorGroup) {
            var gn = new ColorGroupNode(g);
            root.add(gn);
            for (var kd : g.keys) gn.add(new LeafNode(kd));
        }
        var treeModel = new DefaultTreeModel(root);
        var tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        var swatchPanel = new JPanel(new GridBagLayout());
        var swatchCons = new GridBagConstraints();
        swatchCons.anchor = GridBagConstraints.NORTHWEST;
        swatchCons.fill = GridBagConstraints.HORIZONTAL;
        swatchCons.insets = new Insets(2, 2, 2, 2);

        tree.addTreeSelectionListener((TreeSelectionEvent e) -> {
            var node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node instanceof LeafNode) {
                rebuildSwatches(swatchPanel, swatchCons, colorGroup);
            }
        });

        rebuildSwatches(swatchPanel, swatchCons, colorGroup);

        p.add(new JScrollPane(tree), BorderLayout.WEST);
        p.add(new JScrollPane(swatchPanel), BorderLayout.CENTER);
        applyPanelTheme(p);
        return p;
    }

    private void rebuildSwatches(JPanel panel, GridBagConstraints gc, java.util.List<ColorGroup> groups) {
        panel.removeAll();
        int row = 0;
        for (var g : groups) {
            GridBagConstraints labelC = (GridBagConstraints) gc.clone();
            labelC.gridx = 0; labelC.gridy = row; labelC.gridwidth = 2;
            JLabel header = new JLabel(g.name);
            header.setFont(header.getFont().deriveFont(Font.BOLD));
            panel.add(header, labelC);
            row++;
            for (var kd : g.keys) {
                GridBagConstraints kc = (GridBagConstraints) gc.clone();
                kc.gridx = 0; kc.gridy = row; kc.weightx = 0;
                panel.add(new JLabel(kd.label), kc);
                kc.gridx = 1; kc.weightx = 1;
                Color init = ThemeManager.getInstance().resolve(kd.key);
                if (init == null) init = UIManager.getColor("Label.foreground");
                panel.add(new ColorSwatch((Frame) SwingUtilities.getWindowAncestor(panel), kd.key, init), kc);
                row++;
            }
        }
        panel.revalidate(); panel.repaint();
    }

    // ── SQL Format panel (完全重写，按设计文档组织) ──

    private JPanel buildSqlFormatPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Top: Profile toolbar + dialect
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));

        JLabel dialectLbl = new JLabel("\u65B9\u8A00:");
        JComboBox<String> dialectCombo = new JComboBox<>(new String[]{"Oracle", "MySQL", "PostgreSQL", "OceanBase"});
        dialectCombo.setSelectedItem(workingOptions.getDialect() != null ? workingOptions.getDialect() : "Oracle");
        dialectCombo.addActionListener(e -> {
            workingOptions.setDialect((String) dialectCombo.getSelectedItem());
            refreshPreview();
        });
        topBar.add(dialectLbl);
        topBar.add(dialectCombo);

        topBar.add(Box.createHorizontalStrut(16));

        JLabel profileLbl = new JLabel("Profile:");
        JComboBox<String> profileCombo = new JComboBox<>();
        refreshProfileCombo(profileCombo);
        profileCombo.setPreferredSize(new Dimension(160, 26));
        topBar.add(profileLbl);
        topBar.add(profileCombo);

        JButton saveProfileBtn = new JButton("\u4FDD\u5B58");
        saveProfileBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "\u8F93\u5165 Profile \u540D\u79F0:");
            if (name != null && !name.trim().isEmpty()) {
                name = name.trim();
                applyCurrentOptionsToWorking(dialectCombo);
                workingOptions.saveAs(name);
                refreshProfileCombo(profileCombo);
                profileCombo.setSelectedItem(name);
                refreshPreview();
            }
        });
        topBar.add(saveProfileBtn);

        JButton deleteProfileBtn = new JButton("\u5220\u9664");
        deleteProfileBtn.addActionListener(e -> {
            String sel = (String) profileCombo.getSelectedItem();
            if (sel != null) {
                workingOptions.deleteProfile(sel);
                refreshProfileCombo(profileCombo);
                refreshPreview();
            }
        });
        topBar.add(deleteProfileBtn);

        profileCombo.addActionListener(e -> {
            if (profileCombo.getSelectedItem() != null) {
                workingOptions.switchTo((String) profileCombo.getSelectedItem());
                applyWorkingToControls(dialectCombo);
                refreshPreview();
            }
        });

        p.add(topBar, BorderLayout.NORTH);

        // Center: sub-tabs for parameter groups + preview
        JTabbedPane formatTabs = new JTabbedPane();
        formatTabs.addTab("\u901A\u7528", buildGeneralFormatPanel(dialectCombo, profileCombo));
        formatTabs.addTab("DQL", buildDqlPanel());
        formatTabs.addTab("DML", buildDmlPanel());
        formatTabs.addTab("DDL", buildDdlPanel());
        formatTabs.addTab("PL/SQL", buildPlsqlPanel());

        // Preview area
        JPanel previewPanel = new JPanel(new BorderLayout(4, 4));
        previewPanel.setBorder(BorderFactory.createTitledBorder("\u5B9E\u65F6\u9884\u89C8"));

        previewSqlCombo = new JComboBox<>(PREVIEW_SQLS);
        previewSqlCombo.addActionListener(e -> refreshPreview());

        previewArea = new RSyntaxTextArea(8, 60);
        previewArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        previewArea.setEditable(false);
        previewArea.setCodeFoldingEnabled(false);
        previewArea.setHighlightCurrentLine(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        try {
            String path = ThemeManager.getInstance().getCurrentTheme().config("rsta.theme");
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in != null) Theme.load(in).apply(previewArea);
            }
            try (InputStream in = RSyntaxTextArea.class.getResourceAsStream(path)) {
                if (in != null) Theme.load(in).apply(previewArea);
            }
        } catch (Exception ignored) {}

        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel previewToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        previewToolbar.add(new JLabel("\u9884\u89C8 SQL:"));
        previewToolbar.add(previewSqlCombo);

        previewPanel.add(previewToolbar, BorderLayout.NORTH);
        previewPanel.add(previewScroll, BorderLayout.CENTER);

        JSplitPane formatSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formatTabs, previewPanel);
        formatSplit.setResizeWeight(0.65);
        formatSplit.setContinuousLayout(true);

        p.add(formatSplit, BorderLayout.CENTER);

        applyPanelTheme(p);
        return p;
    }

    private void refreshProfileCombo(JComboBox<String> combo) {
        combo.removeAllItems();
        for (String name : workingOptions.getProfiles().keySet()) {
            combo.addItem(name);
        }
        if (workingOptions.getActiveProfile() != null) {
            combo.setSelectedItem(workingOptions.getActiveProfile());
        }
    }

    private void applyCurrentOptionsToWorking(JComboBox<String> dialectCombo) {
        // Read all controls back into workingOptions
        // This is called before saveAs
    }

    private void applyWorkingToControls(JComboBox<String> dialectCombo) {
        for (java.awt.event.ActionListener al : dialectCombo.getActionListeners()) {
            dialectCombo.removeActionListener(al);
        }
        dialectCombo.setSelectedItem(workingOptions.getDialect() != null ? workingOptions.getDialect() : "Oracle");
        for (java.awt.event.ActionListener al : dialectCombo.getActionListeners()) {
            dialectCombo.removeActionListener(al);
        }
    }

    private void refreshPreview() {
        if (previewArea == null) return;
        String sql = switch (previewSqlCombo.getSelectedIndex()) {
            case 1 -> SAMPLE_INSERT;
            case 2 -> SAMPLE_DDL;
            case 3 -> SAMPLE_PLSQL;
            default -> SAMPLE_SELECT;
        };
        try {
            SqlDialect dialect = DialectManager.forName(
                workingOptions.getDialect() != null ? workingOptions.getDialect() : "Oracle");
            String formatted = SqlFormatter.format(sql, workingOptions, dialect);
            previewArea.setText(formatted);
            previewArea.setCaretPosition(0);
        } catch (Exception e) {
            previewArea.setText(sql);
        }
    }

    // ── SQL Format sub-panels ──

    private JPanel buildGeneralFormatPanel(JComboBox<String> dialectCombo, JComboBox<String> profileCombo) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("\u5173\u952E\u5B57\u5927\u5C0F\u5199:"), c);
        c.gridx = 1; c.weightx = 1;
        JComboBox<String> kcCombo = new JComboBox<>(new String[]{"UPPER", "LOWER", "PRESERVE"});
        kcCombo.setSelectedItem(workingOptions.getKeywordCase().name());
        kcCombo.addActionListener(e -> {
            workingOptions.setKeywordCase(KeywordCase.valueOf((String) kcCombo.getSelectedItem()));
            refreshPreview();
        });
        p.add(kcCombo, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        p.add(new JLabel("\u7F29\u8FDB\u7A7A\u683C\u6570:"), c);
        c.gridx = 1;
        JSpinner indentSpin = new JSpinner(new SpinnerNumberModel(workingOptions.getIndentSize(), 0, 8, 1));
        indentSpin.addChangeListener(e -> {
            workingOptions.setIndentSize((Integer) indentSpin.getValue());
            refreshPreview();
        });
        p.add(indentSpin, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("\u6700\u5927\u884C\u5BBD (0=\u4E0D\u9650):"), c);
        c.gridx = 1;
        JSpinner widthSpin = new JSpinner(new SpinnerNumberModel(workingOptions.getMaxLineWidth(), 0, 999, 10));
        widthSpin.addChangeListener(e -> {
            workingOptions.setMaxLineWidth((Integer) widthSpin.getValue());
            refreshPreview();
        });
        p.add(widthSpin, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("\u6362\u884C\u7B26:"), c);
        c.gridx = 1;
        JComboBox<String> lsCombo = new JComboBox<>(new String[]{"LF", "CRLF"});
        lsCombo.setSelectedItem(workingOptions.getLineEnding());
        lsCombo.addActionListener(e -> {
            workingOptions.setLineEnding((String) lsCombo.getSelectedItem());
            refreshPreview();
        });
        p.add(lsCombo, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("\u9017\u53F7\u4F4D\u7F6E:"), c);
        c.gridx = 1;
        JComboBox<String> commaCombo = new JComboBox<>(new String[]{"TRAILING", "LEADING"});
        commaCombo.setSelectedItem(workingOptions.getCommaPosition().name());
        commaCombo.addActionListener(e -> {
            workingOptions.setCommaPosition(CommaPosition.valueOf((String) commaCombo.getSelectedItem()));
            refreshPreview();
        });
        p.add(commaCombo, c);

        applyPanelTheme(p);
        return p;
    }

    private JPanel buildDqlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        c.gridx = 0; c.gridy = row;
        JLabel selLbl = new JLabel("SELECT \u5217\u6A21\u5F0F:");
        selLbl.setFont(selLbl.getFont().deriveFont(Font.BOLD));
        p.add(selLbl, c);
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        p.add(new JLabel("  \u5217\u6A21\u5F0F:"), c);
        c.gridx = 1; c.weightx = 1;
        JComboBox<String> scCombo = new JComboBox<>(new String[]{"ALIGN", "COMPACT", "ONE_PER_LINE"});
        scCombo.setSelectedItem(workingOptions.getSelectColumnMode().name());
        scCombo.addActionListener(e -> {
            workingOptions.setSelectColumnMode(SelectColumnMode.valueOf((String) scCombo.getSelectedItem()));
            refreshPreview();
        });
        p.add(scCombo, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        JLabel fromLbl = new JLabel("FROM/JOIN");
        fromLbl.setFont(fromLbl.getFont().deriveFont(Font.BOLD));
        p.add(fromLbl, c);
        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  FROM \u524D\u6362\u884C:"), c);
        c.gridx = 1;
        JCheckBox fromCb = new JCheckBox();
        fromCb.setSelected(workingOptions.isFromClauseNewline());
        fromCb.addActionListener(e -> {
            workingOptions.setFromClauseNewline(fromCb.isSelected());
            refreshPreview();
        });
        p.add(fromCb, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  JOIN \u524D\u6362\u884C:"), c);
        c.gridx = 1;
        JCheckBox joinCb = new JCheckBox();
        joinCb.setSelected(workingOptions.isJoinOnNewline());
        joinCb.addActionListener(e -> {
            workingOptions.setJoinOnNewline(joinCb.isSelected());
            refreshPreview();
        });
        p.add(joinCb, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  ON \u6761\u4EF6\u5BF9\u9F50:"), c);
        c.gridx = 1;
        JCheckBox joinAlignCb = new JCheckBox();
        joinAlignCb.setSelected(workingOptions.isJoinOnAlign());
        joinAlignCb.addActionListener(e -> {
            workingOptions.setJoinOnAlign(joinAlignCb.isSelected());
            refreshPreview();
        });
        p.add(joinAlignCb, c);

        row++;
        c.gridx = 0; c.gridy = row;
        JLabel whereLbl = new JLabel("WHERE");
        whereLbl.setFont(whereLbl.getFont().deriveFont(Font.BOLD));
        p.add(whereLbl, c);
        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  AND/OR \u4F4D\u7F6E:"), c);
        c.gridx = 1;
        JComboBox<String> waCombo = new JComboBox<>(new String[]{"LINE_START", "LINE_END"});
        waCombo.setSelectedItem(workingOptions.getWhereAndPosition().name());
        waCombo.addActionListener(e -> {
            workingOptions.setWhereAndPosition(WhereAndPosition.valueOf((String) waCombo.getSelectedItem()));
            refreshPreview();
        });
        p.add(waCombo, c);

        applyPanelTheme(p);
        return p;
    }

    private JPanel buildDmlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        c.gridx = 0; c.gridy = row;
        JLabel insertLbl = new JLabel("INSERT");
        insertLbl.setFont(insertLbl.getFont().deriveFont(Font.BOLD));
        p.add(insertLbl, c);
        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  \u5217\u7D27\u51D1\u6A21\u5F0F:"), c);
        c.gridx = 1; c.weightx = 1;
        JCheckBox insertCb = new JCheckBox();
        insertCb.setSelected(workingOptions.isInsertColumnModeCompact());
        insertCb.addActionListener(e -> {
            workingOptions.setInsertColumnModeCompact(insertCb.isSelected());
            refreshPreview();
        });
        p.add(insertCb, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        JLabel updateLbl = new JLabel("UPDATE");
        updateLbl.setFont(updateLbl.getFont().deriveFont(Font.BOLD));
        p.add(updateLbl, c);
        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  SET \u5BF9\u9F50:"), c);
        c.gridx = 1;
        JCheckBox setAlignCb = new JCheckBox();
        setAlignCb.setSelected(workingOptions.isUpdateSetAlign());
        setAlignCb.addActionListener(e -> {
            workingOptions.setUpdateSetAlign(setAlignCb.isSelected());
            refreshPreview();
        });
        p.add(setAlignCb, c);

        applyPanelTheme(p);
        return p;
    }

    private JPanel buildDdlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("\u5217\u5B9A\u4E49\u5BF9\u9F50:"), c);
        c.gridx = 1; c.weightx = 1;
        JCheckBox colDefCb = new JCheckBox();
        colDefCb.setSelected(workingOptions.isColumnDefAlign());
        colDefCb.addActionListener(e -> {
            workingOptions.setColumnDefAlign(colDefCb.isSelected());
            refreshPreview();
        });
        p.add(colDefCb, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        p.add(new JLabel("\u5B58\u50A8\u5B50\u53E5\u683C\u5F0F:"), c);
        c.gridx = 1;
        JComboBox<String> scCombo = new JComboBox<>(new String[]{"COMPACT", "LINE_BREAK"});
        scCombo.setSelectedItem(workingOptions.getStorageClauseFormat().name());
        scCombo.addActionListener(e -> {
            workingOptions.setStorageClauseFormat(StorageClauseFormat.valueOf((String) scCombo.getSelectedItem()));
            refreshPreview();
        });
        p.add(scCombo, c);

        applyPanelTheme(p);
        return p;
    }

    private JPanel buildPlsqlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        c.gridx = 0; c.gridy = row;
        JLabel declLbl = new JLabel("\u58F0\u660E/\u53C2\u6570");
        declLbl.setFont(declLbl.getFont().deriveFont(Font.BOLD));
        p.add(declLbl, c);
        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  \u58F0\u660E\u5BF9\u9F50 (:=):"), c);
        c.gridx = 1; c.weightx = 1;
        JCheckBox declAlignCb = new JCheckBox();
        declAlignCb.setSelected(workingOptions.isDeclarationAlign());
        declAlignCb.addActionListener(e -> {
            workingOptions.setDeclarationAlign(declAlignCb.isSelected());
            refreshPreview();
        });
        p.add(declAlignCb, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        p.add(new JLabel("  \u53C2\u6570\u5217\u8868:"), c);
        c.gridx = 1;
        JComboBox<String> plCombo = new JComboBox<>(new String[]{"COMPACT", "ONE_PER_LINE"});
        plCombo.setSelectedItem(workingOptions.getParameterListMode().name());
        plCombo.addActionListener(e -> {
            workingOptions.setParameterListMode(ParameterListMode.valueOf((String) plCombo.getSelectedItem()));
            refreshPreview();
        });
        p.add(plCombo, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  \u62EC\u53F7\u5185\u7A7A\u683C:"), c);
        c.gridx = 1;
        JComboBox<String> psCombo = new JComboBox<>(new String[]{"NONE", "INSIDE", "BOTH"});
        psCombo.setSelectedItem(workingOptions.getParenthesisSpacing().name());
        psCombo.addActionListener(e -> {
            workingOptions.setParenthesisSpacing(ParenthesisSpacing.valueOf((String) psCombo.getSelectedItem()));
            refreshPreview();
        });
        p.add(psCombo, c);

        row++;
        c.gridx = 0; c.gridy = row;
        JLabel ctrlLbl = new JLabel("\u63A7\u5236\u7ED3\u6784");
        ctrlLbl.setFont(ctrlLbl.getFont().deriveFont(Font.BOLD));
        p.add(ctrlLbl, c);
        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  THEN \u6362\u884C:"), c);
        c.gridx = 1;
        JCheckBox thenCb = new JCheckBox();
        thenCb.setSelected(workingOptions.isThenOnNewLine());
        thenCb.addActionListener(e -> {
            workingOptions.setThenOnNewLine(thenCb.isSelected());
            refreshPreview();
        });
        p.add(thenCb, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  LOOP \u6362\u884C:"), c);
        c.gridx = 1;
        JCheckBox loopCb = new JCheckBox();
        loopCb.setSelected(workingOptions.isLoopOnNewLine());
        loopCb.addActionListener(e -> {
            workingOptions.setLoopOnNewLine(loopCb.isSelected());
            refreshPreview();
        });
        p.add(loopCb, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  ELSE \u72EC\u7ACB\u884C:"), c);
        c.gridx = 1;
        JCheckBox elseCb = new JCheckBox();
        elseCb.setSelected(workingOptions.isElseOnNewLine());
        elseCb.addActionListener(e -> {
            workingOptions.setElseOnNewLine(elseCb.isSelected());
            refreshPreview();
        });
        p.add(elseCb, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("  EXCEPTION \u5BF9\u9F50:"), c);
        c.gridx = 1;
        JComboBox<String> eaCombo = new JComboBox<>(new String[]{"INDENT", "OUTDENT"});
        eaCombo.setSelectedItem(workingOptions.getExceptionAlign().name());
        eaCombo.addActionListener(e -> {
            workingOptions.setExceptionAlign(ExceptionAlign.valueOf((String) eaCombo.getSelectedItem()));
            refreshPreview();
        });
        p.add(eaCombo, c);

        applyPanelTheme(p);
        return p;
    }

    // ── Autosave panel (保留原有功能) ──

    private JPanel buildAutosavePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.gridx = 0; c.weightx = 0;

        int row = 0;
        c.gridy = row; p.add(new JLabel("\u81EA\u52A8\u4FDD\u5B58\u95F4\u9694:"), c);
        c.gridx = 1; c.weightx = 1;
        int interval = 30;
        try { interval = Integer.parseInt(configManager.getPreference("autosave.interval", "30")); } catch (Exception ignored) {}
        var intervalSpinner = new JSpinner(new SpinnerNumberModel(interval, 5, 3600, 5));
        p.add(intervalSpinner, c);
        c.gridx = 2; c.weightx = 0;
        var unitCombo = new JComboBox<>(FORMAT_UNITS);
        String unit = configManager.getPreference("autosave.unit", "seconds");
        unitCombo.setSelectedIndex(unit.equals("hours") ? 2 : unit.equals("minutes") ? 1 : 0);
        p.add(unitCombo, c);

        row++;
        c.gridy = row; c.gridx = 0; c.weightx = 0;
        p.add(new JLabel("\u81EA\u52A8\u4FDD\u5B58\u8DEF\u5F84:"), c);
        c.gridx = 1; c.weightx = 1;
        var pathField = new JTextField(configManager.getPreference("autosave.path", System.getProperty("user.home") + "/.kylin-plsql/autosave"));
        p.add(pathField, c);
        c.gridx = 2; c.weightx = 0;
        var browseBtn = new JButton("\u6D4F\u89C8...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        p.add(browseBtn, c);

        applyPanelTheme(p);
        return p;
    }

    // ── Metadata panel (保留原有功能) ──

    private JPanel buildMetadataPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        metadataConfigs = configManager.loadMetadataConfigs();
        metadataTreeModel = new DefaultTreeModel(buildMetadataTree());
        metadataTree = new JTree(metadataTreeModel);
        metadataTree.setRootVisible(false);
        metadataTree.setShowsRootHandles(true);
        metadataTree.addTreeSelectionListener(e -> onMetadataTreeSelect(e));

        typeTableModel = new TypesTableModel();
        typeTable = new JTable(typeTableModel);
        typeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onTypeSelect();
        });

        var typeBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        var addTypeBtn = new JButton("+ \u7C7B\u578B");
        addTypeBtn.addActionListener(e -> {
            if (metadataTree.getLastSelectedPathComponent() instanceof MetadataNode mn) {
                var td = new TypeDef();
                td.setLabel("\u65B0\u7C7B\u578B");
                td.setTypeCode("NEW_TYPE");
                td.setQueryType("SQL");
                td.setExpandable(false);
                typeTableModel.types.add(td);
                typeTableModel.fireTableRowsInserted(typeTableModel.types.size() - 1, typeTableModel.types.size() - 1);
                saveMetadataConfig();
            }
        });
        var delTypeBtn = new JButton("- \u5220\u9664");
        delTypeBtn.addActionListener(e -> {
            int sel = typeTable.getSelectedRow();
            if (sel >= 0) {
                typeTableModel.types.remove(sel);
                typeTableModel.fireTableRowsDeleted(sel, sel);
                saveMetadataConfig();
            }
        });
        typeBtnPanel.add(addTypeBtn);
        typeBtnPanel.add(delTypeBtn);

        sqlArea = new JTextArea(4, 30);
        sqlArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        sqlArea.setLineWrap(true);
        var sqlScroll = new JScrollPane(sqlArea);
        sqlScroll.setBorder(BorderFactory.createTitledBorder("SQL \u67E5\u8BE2 (\u9009\u4E2D\u540E\u7F16\u8F91)"));

        sqlEditPanel = new JPanel(new BorderLayout());
        sqlEditPanel.add(sqlScroll, BorderLayout.CENTER);
        var saveSqlBtn = new JButton("\u4FDD\u5B58 SQL");
        saveSqlBtn.addActionListener(e -> saveCurrentSql());
        var existingBtn = new JButton("\u5305\u542B\u73B0\u6709");
        existingBtn.addActionListener(e -> {
            int sel = typeTable.getSelectedRow();
            if (sel >= 0) {
                var td = typeTableModel.types.get(sel);
                if ("FIXED_LIST".equals(td.getQueryType())) {
                    String vals = String.join("\n", td.getFixedValues());
                    sqlArea.setText("# FIXED_LIST \u7C7B\u578B\uFF0C\u4E0D\u9700\u8981 SQL\uFF0C\u56FA\u5B9A\u503C:\n" + vals);
                }
            }
        });
        sqlEditPanel.add(saveSqlBtn, BorderLayout.SOUTH);

        columnModel = new ColumnsTableModel();
        columnTable = new JTable(columnModel);

        var colBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        var addColBtn = new JButton("+ \u81EA\u5B9A\u4E49");
        addColBtn.addActionListener(e -> {
            int sel = typeTable.getSelectedRow();
            if (sel >= 0) {
                columnModel.cols.add(new DbMetadataConfig.CustomColumn("\u65B0\u5217", "SELECT ? FROM ... WHERE owner = ?"));
                columnModel.fireTableRowsInserted(columnModel.cols.size() - 1, columnModel.cols.size() - 1);
                saveMetadataConfig();
            }
        });
        var delColBtn = new JButton("- \u5220\u9664");
        delColBtn.addActionListener(e -> {
            int sel = columnTable.getSelectedRow();
            if (sel >= 0) {
                columnModel.cols.remove(sel);
                columnModel.fireTableRowsDeleted(sel, sel);
                saveMetadataConfig();
            }
        });
        colBtnPanel.add(addColBtn);
        colBtnPanel.add(delColBtn);

        helpLabel = new JLabel("\u81EA\u5B9A\u4E49\u5217\u5728\u5BF9\u5E94\u7C7B\u578B\u7684\u8282\u70B9\u4E0A\u663E\u793A\u989D\u5916\u4FE1\u606F\uFF0C\u72B6\u6001\u680F\u3001\u5FEB\u901F\u63D0\u793A\u7B49\uFF1BSQL \u4E2D\u7684 ? \u4F1A\u88AB\u5F53\u524D Schema \u548C\u5BF9\u8C61\u540D\u66FF\u6362\u3002");
        helpLabel.setFont(UIManager.getFont("Label.font"));
        helpLabel.setForeground(ThemeManager.getInstance().resolve("fg.muted"));

        columnEditPanel = new JPanel(new BorderLayout());
        columnEditPanel.add(new JScrollPane(columnTable), BorderLayout.CENTER);
        columnEditPanel.add(colBtnPanel, BorderLayout.NORTH);
        columnEditPanel.add(helpLabel, BorderLayout.SOUTH);

        var rightPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sqlEditPanel, columnEditPanel);
        rightPanel.setResizeWeight(0.5);
        var rightOuter = new JPanel(new BorderLayout());
        rightOuter.add(typeBtnPanel, BorderLayout.NORTH);
        rightOuter.add(rightPanel, BorderLayout.CENTER);

        var topSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(typeTable), rightOuter);
        topSplit.setResizeWeight(0.3);

        var mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(metadataTree), topSplit);
        mainSplit.setResizeWeight(0.3);

        p.add(mainSplit, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            if (metadataTree.getRowCount() > 0) {
                metadataTree.setSelectionRow(0);
            }
        });

        applyPanelTheme(p);
        return p;
    }

    private DefaultMutableTreeNode buildMetadataTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("\u6570\u636E\u5E93\u7C7B\u578B");
        for (var cfg : metadataConfigs) {
            if (cfg.isEnabled()) {
                root.add(new MetadataNode(cfg));
            }
        }
        return root;
    }

    private void onMetadataTreeSelect(TreeSelectionEvent e) {
        var node = (DefaultMutableTreeNode) metadataTree.getLastSelectedPathComponent();
        if (node instanceof MetadataNode mn) {
            typeTableModel.types = mn.config.getTypes();
            typeTableModel.fireTableDataChanged();
            columnModel.cols = mn.config.getExtraColumns();
            columnModel.fireTableDataChanged();
        }
    }

    private void onTypeSelect() {
        int sel = typeTable.getSelectedRow();
        if (sel < 0 || sel >= typeTableModel.types.size()) {
            sqlArea.setText("");
            return;
        }
        var td = typeTableModel.types.get(sel);
        if ("FIXED_LIST".equals(td.getQueryType())) {
            sqlArea.setText("# FIXED_LIST \u7C7B\u578B\uFF0C\u4E0D\u9700\u8981 SQL\uFF0C\u56FA\u5B9A\u503C:\n"
                + String.join("\n", td.getFixedValues()));
            sqlArea.setEditable(false);
        } else {
            sqlArea.setText(td.getQuerySql() != null ? td.getQuerySql() : "");
            sqlArea.setEditable(true);
        }
    }

    private void saveCurrentSql() {
        int sel = typeTable.getSelectedRow();
        if (sel >= 0 && sel < typeTableModel.types.size()) {
            var td = typeTableModel.types.get(sel);
            if (!"FIXED_LIST".equals(td.getQueryType())) {
                td.setQuerySql(sqlArea.getText());
                saveMetadataConfig();
            }
        }
    }

    private void saveMetadataConfig() {
        var node = (DefaultMutableTreeNode) metadataTree.getLastSelectedPathComponent();
        if (node instanceof MetadataNode mn) {
            mn.config.setTypes(new ArrayList<>(typeTableModel.types));
            mn.config.setExtraColumns(new ArrayList<>(columnModel.cols));
            configManager.saveMetadataConfigs(metadataConfigs);
        }
    }

    // ── Save settings ──

    private void saveSettings() {
        formatOptions.copyFrom(workingOptions);
        configManager.setPreference("format.dialect", workingOptions.getDialect());
        configManager.setPreference("format.keywordCase", workingOptions.getKeywordCase().name());
        configManager.setPreference("format.indent", String.valueOf(workingOptions.getIndentSize()));
        configManager.setPreference("format.maxWidth", String.valueOf(workingOptions.getMaxLineWidth()));
        configManager.setPreference("format.lineEnding", workingOptions.getLineEnding());
        JOptionPane.showMessageDialog(this, "\u8BBE\u7F6E\u5DF2\u4FDD\u5B58");
        dispose();
    }

    // ── Theme helper ──

    private void applyPanelTheme(JPanel panel) {
        ThemeManager tm = ThemeManager.getInstance();
        Color bg = tm.resolve("bg.main");
        panel.setBackground(bg);
        for (Component c : panel.getComponents()) {
            if (c instanceof JPanel) c.setBackground(bg);
            if (c instanceof JLabel) c.setForeground(tm.resolve("fg.main"));
            if (c instanceof JCheckBox) c.setForeground(tm.resolve("fg.main"));
        }
    }
}
