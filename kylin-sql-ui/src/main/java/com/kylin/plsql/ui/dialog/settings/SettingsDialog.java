package com.kylin.plsql.ui.dialog.settings;

import com.kylin.plsql.ui.MainFrame;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.FontManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.config.DbMetadataConfig;
import com.kylin.plsql.core.config.DbMetadataConfig.TypeDef;
import com.kylin.plsql.core.format.enums.BlankLineHandling;
import com.kylin.plsql.core.format.enums.BlockCommentStyle;
import com.kylin.plsql.core.format.enums.CommentIndent;
import com.kylin.plsql.core.format.enums.CommentPlacement;
import com.kylin.plsql.core.format.enums.CaseExpressionFormat;
import com.kylin.plsql.core.format.enums.CommaPosition;
import com.kylin.plsql.core.format.enums.ConstraintFormat;
import com.kylin.plsql.core.format.enums.CteFormat;
import com.kylin.plsql.core.format.enums.ExceptionAlign;
import com.kylin.plsql.core.format.enums.ForLoopFormat;
import com.kylin.plsql.core.format.enums.IndexColumnFormat;
import com.kylin.plsql.core.format.enums.InListFormat;
import com.kylin.plsql.core.format.enums.InsertColumnFormat;
import com.kylin.plsql.core.format.enums.ParameterListMode;
import com.kylin.plsql.core.format.enums.ParenthesisSpacing;
import com.kylin.plsql.core.format.enums.PartitionFormat;
import com.kylin.plsql.core.format.enums.SelectColumnMode;
import com.kylin.plsql.core.format.enums.StorageClauseFormat;
import com.kylin.plsql.core.format.enums.SubqueryStyle;
import com.kylin.plsql.core.format.enums.WhereAndPosition;
import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.FormatOptions.KeywordCase;
import com.kylin.plsql.core.format.SqlFormatter;
import com.kylin.plsql.core.format.dialect.DialectManager;
import com.kylin.plsql.core.format.dialect.SqlDialect;
import com.kylin.plsql.core.format.plsql.PlSqlFormatter;
import com.kylin.plsql.core.format.plsql.model.FormatResult;
import com.kylin.plsql.ui.MainFrame;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/** Application settings dialog with left-tree navigation: format options, theme, autosave, metadata config */
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
        final Color originalColor;
        Color color;
        ColorSwatch(Frame parent, String key, Color initial) {
            super();
            this.configKey = key;
            this.originalColor = initial;
            this.color = initial;
            setBackground(initial);
            setOpaque(true);
            setPreferredSize(new Dimension(60, 24));
            setBorder(BorderFactory.createLineBorder(new Color(0x666666), 1));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    Color c = JColorChooser.showDialog(parent, "选择颜色 - " + configKey, color);
                    if (c != null) {
                        color = c;
                        setBackground(c);
                        ThemeManager.getInstance().setOverride(configKey, c);
                        updateModifiedIndicator();
                    }
                }
            });
        }

        void updateModifiedIndicator() {
            if (!color.equals(originalColor)) {
                setBorder(BorderFactory.createLineBorder(new Color(0x5CB85C), 2));
            } else {
                setBorder(BorderFactory.createLineBorder(new Color(0x666666), 1));
            }
        }
    }

    static class TypesTableModel extends AbstractTableModel {
        List<TypeDef> types = new ArrayList<>();
        @Override public int getRowCount() { return types.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int col) {
            return switch (col) { case 0 -> "标签"; case 1 -> "类型"; case 2 -> "源"; default -> ""; };
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
            return col == 0 ? "标签" : "SQL 查询";
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
    private final Frame owner;
    private FormatOptions workingOptions; // mutable copy for UI editing
    private JTree settingsTree;
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private JTextField autocompleteDelayField;
    private JSpinner autoIntervalSpinner;
    private JComboBox<String> autoUnitCombo;
    private JTextField autoPathField;
    private JTextField splashMinField;
    private JTextField splashMaxField;
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
    private JLabel previewStatusLabel;
    private JComboBox<String> previewSqlCombo;
    private JTabbedPane formatTabs;
    private JComboBox<String> dialectCombo;
    private JComboBox<String> profileCombo;
    private String previewSection;
    private boolean manualPreviewSelection;

    private static final String[] FORMAT_UNITS = {"秒", "分", "小时"};
    private static final String[] PREVIEW_SQLS = {
        "SELECT",
        "INSERT",
        "UPDATE",
        "DELETE",
        "MERGE",
        "CREATE TABLE",
        "INDEX",
        "PACKAGE",
        "PL/SQL BLOCK",
        "COMMENTS",
        "IN LIST",
        "UNION"
    };
    private static final String SAMPLE_SELECT =
        "SELECT id, first_name, last_name, email, salary, hire_date, department_id\n" +
        "FROM employees e\n" +
        "LEFT JOIN departments d ON e.department_id = d.department_id\n" +
        "WHERE salary > 5000 AND (status = 'ACTIVE' OR status = 'PROBATION')\n" +
        "  AND dept_id IN (SELECT department_id FROM departments WHERE location = 'Tokyo')\n" +
        "ORDER BY CASE\n" +
        "    WHEN salary > 10000 THEN 1\n" +
        "    WHEN salary > 5000 THEN 2\n" +
        "    ELSE 3\n" +
        "END, last_name ASC";
    private static final String SAMPLE_INSERT =
        "INSERT INTO employees (employee_id, first_name, last_name, email, phone_number, hire_date, job_id, salary, department_id)\n" +
        "VALUES (100, 'John', 'Doe', 'JDOE', '555-0100', SYSDATE, 'IT_PROG', 75000, 60)";
    private static final String SAMPLE_UPDATE =
        "UPDATE employees\n" +
        "SET salary = salary * 1.1,\n" +
        "    last_name = 'Smith',\n" +
        "    email = 'JSMITH',\n" +
        "    phone_number = '555-0199'\n" +
        "WHERE employee_id = 100\n" +
        "  AND department_id = 60";
    private static final String SAMPLE_DELETE =
        "DELETE FROM employees\n" +
        "WHERE employee_id = 100\n" +
        "  AND department_id = 60";
    private static final String SAMPLE_MERGE =
        "MERGE INTO employees t\n" +
        "USING (SELECT 200 AS emp_id, 'Jane' AS name FROM dual) s\n" +
        "ON (t.employee_id = s.emp_id)\n" +
        "WHEN MATCHED THEN\n" +
        "    UPDATE SET t.first_name = s.name\n" +
        "WHEN NOT MATCHED THEN\n" +
        "    INSERT (employee_id, first_name) VALUES (s.emp_id, s.name)";
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
    private static final String SAMPLE_INDEX =
        "CREATE INDEX idx_emp_salary\n" +
        "ON employees (salary DESC, department_id)\n" +
        "TABLESPACE users\n" +
        "COMPUTE STATISTICS";
    private static final String SAMPLE_PACKAGE =
        "CREATE OR REPLACE PACKAGE emp_pkg AS\n" +
        "    FUNCTION get_salary(p_emp_id IN NUMBER) RETURN NUMBER;\n" +
        "    PROCEDURE update_dept(\n" +
        "        p_emp_id   IN NUMBER,\n" +
        "        p_dept_id  IN NUMBER DEFAULT 10,\n" +
        "        p_commit   IN BOOLEAN DEFAULT TRUE\n" +
        "    );\n" +
        "END emp_pkg;";
    private static final String SAMPLE_INLIST =
        "SELECT id, name, status\n" +
        "FROM employees\n" +
        "WHERE dept_id IN (10, 20, 30, 40, 50, 60, 70, 80, 90, 100)\n" +
        "  AND status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')\n" +
        "ORDER BY name";
    private static final String SAMPLE_SETOPS =
        "SELECT id, name FROM employees\n" +
        "UNION ALL\n" +
        "SELECT id, name FROM former_employees\n" +
        "MINUS\n" +
        "SELECT id, name FROM contractors\n" +
        "ORDER BY name";
    private static final String SAMPLE_COMMENTS =
        "-- 查询员工信息\n" +
        "SELECT e.id,  -- 员工ID\n" +
        "       e.name\n" +
        "  FROM employees e\n" +
        " WHERE e.status = 'ACTIVE'\n" +
        "/* 按组织查询 */\n" +
        "   AND e.dept_id = 10\n" +
        "ORDER BY e.name;\n" +
        "\n" +
        "-- 更新状态\n" +
        "UPDATE employees SET status = 'INACTIVE'\n" +
        " WHERE last_login < SYSDATE - 90;\n";
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

    public SettingsDialog(Frame owner, FormatOptions formatOptions, ConfigManager configManager) {
        super(owner, "设置", true);
        this.formatOptions = formatOptions;
        this.configManager = configManager;
        this.owner = owner;
        this.workingOptions = formatOptions.snapshot();
        // Load old individual preferences into workingOptions (migration)
        String kc = configManager.getPreference("format.keywordCase", null);
        if (kc != null) { try { workingOptions.setKeywordCase(KeywordCase.valueOf(kc)); } catch (Exception ignored) {} }
        String indent = configManager.getPreference("format.indent", null);
        if (indent != null) { try { workingOptions.setIndentSize(Integer.parseInt(indent)); } catch (Exception ignored) {} }
        String mw = configManager.getPreference("format.maxWidth", null);
        if (mw != null) { try { workingOptions.setMaxLineWidth(Integer.parseInt(mw)); } catch (Exception ignored) {} }
        String le = configManager.getPreference("format.lineEnding", null);
        if (le != null) { try { workingOptions.setLineEnding(le); } catch (Exception ignored) {} }
        String di = configManager.getPreference("format.dialect", null);
        if (di != null) { try { workingOptions.setDialect(di); } catch (Exception ignored) {} }
        // 65% screen size
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize((int)(screen.width * 0.65), (int)(screen.height * 0.65));
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
        cardPanel.add(buildSqlFormatPanel(), "sqlFormat");
        cardPanel.add(buildThemePanel(), "theme");
        cardPanel.add(buildFontPanel(), "font");
        cardPanel.add(buildCommonPanel(), "common");
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
        saveBtn = new JButton("应用");
        saveBtn.addActionListener(e -> saveSettings());
        cancelBtn = new JButton("取消");
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
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("设置");
        root.add(new DefaultMutableTreeNode("主题个性化"));
        root.add(new DefaultMutableTreeNode("字体个性化"));
        root.add(new DefaultMutableTreeNode("常用配置"));
        root.add(new DefaultMutableTreeNode("元数据配置"));
        root.add(new DefaultMutableTreeNode("SQL 格式化"));

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
        return switch (label) {
            case "SQL 格式化" -> "sqlFormat";
            case "主题个性化" -> "theme";
            case "字体个性化" -> "font";
            case "常用配置" -> "common";
            case "元数据配置" -> "metadata";
            default -> null;
        };
    }

    // ── Theme panel (颜色个性化) ──

    private JPanel buildThemePanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        var colorGroup = new java.util.ArrayList<ColorGroup>();
        colorGroup.add(new ColorGroup("背景 (Background)")
            .add("bg.main", "主背景颜色")
            .add("bg.editor", "编辑器背景")
            .add("bg.panel", "面板背景")
            .add("bg.toolbar", "工具栏背景")
            .add("bg.output", "输出窗口背景"));
        colorGroup.add(new ColorGroup("前景 (Foreground)")
            .add("fg.main", "主文本颜色")
            .add("fg.secondary", "次要文本颜色")
            .add("fg.muted", "暗淡文本颜色")
            .add("fg.title", "标题文本颜色")
            .add("fg.tab.active", "标签页活跃颜色")
            .add("fg.tab.inactive", "标签页非活跃颜色"));
        colorGroup.add(new ColorGroup("选择 (Selection)")
            .add("selection.bg", "选中背景颜色")
            .add("selection.fg", "选中文本颜色")
            .add("selection.listBg", "列表选中背景")
            .add("selection.listFg", "列表选中文本"));
        colorGroup.add(new ColorGroup("边框 (Border)")
            .add("border.default", "默认边框")
            .add("border.light", "浅色边框"));
        colorGroup.add(new ColorGroup("强调色 (Accent)")
            .add("accent.green", "绿色强调")
            .add("accent.tab", "标签页强调"));
        colorGroup.add(new ColorGroup("编辑器 (Editor)")
            .add("editor.caret", "光标颜色"));
        colorGroup.add(new ColorGroup("列表 (List)")
            .add("list.bg", "列表背景")
            .add("list.fg", "列表文本"));
        colorGroup.add(new ColorGroup("滚动 (Scroll)")
            .add("scroll.bg", "滚动条背景"));
        colorGroup.add(new ColorGroup("执行结果 (Execution)")
            .add("exec.success", "执行成功")
            .add("exec.fail", "执行失败")
            .add("exec.highlight", "执行高亮"));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("主题颜色");
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

        // Expand all tree nodes
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);

        tree.addTreeSelectionListener((TreeSelectionEvent e) -> {
            var node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node instanceof ColorGroupNode gn) {
                rebuildSwatches(swatchPanel, swatchCons, java.util.Collections.singletonList(gn.group), owner);
            } else if (node instanceof LeafNode ln) {
                rebuildSwatches(swatchPanel, swatchCons, java.util.Collections.singletonList(
                    new ColorGroup(ln.desc.label).add(ln.desc.key, ln.desc.label)), owner);
            } else {
                rebuildSwatches(swatchPanel, swatchCons, colorGroup, owner);
            }
        });

        JPanel resetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton resetBtn = new JButton("重置默认");
        resetBtn.setToolTipText("将已修改的颜色重置为当前主题对应的默认颜色");
        resetBtn.addActionListener(e -> {
            int ret = JOptionPane.showConfirmDialog(this,
                "确定要将所有颜色重置为当前主题的默认值吗？",
                "重置颜色", JOptionPane.YES_NO_OPTION);
            if (ret != JOptionPane.YES_OPTION) return;
            ThemeManager.getInstance().clearOverrides();
            // Rebuild current view
            var selNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selNode instanceof ColorGroupNode gn) {
                rebuildSwatches(swatchPanel, swatchCons, java.util.Collections.singletonList(gn.group), owner);
            } else if (selNode instanceof LeafNode ln) {
                rebuildSwatches(swatchPanel, swatchCons, java.util.Collections.singletonList(
                    new ColorGroup(ln.desc.label).add(ln.desc.key, ln.desc.label)), owner);
            } else {
                rebuildSwatches(swatchPanel, swatchCons, colorGroup.subList(0, 1), owner);
                tree.setSelectionRow(0);
            }
        });
        resetPanel.add(resetBtn);
        resetPanel.setOpaque(false);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);
        rightPanel.add(new JScrollPane(swatchPanel), BorderLayout.CENTER);
        rightPanel.add(resetPanel, BorderLayout.SOUTH);

        // Initial: show first category
        rebuildSwatches(swatchPanel, swatchCons, colorGroup.subList(0, 1), owner);
        tree.setSelectionRow(0);

        p.add(new JScrollPane(tree), BorderLayout.WEST);
        p.add(rightPanel, BorderLayout.CENTER);
        return p;
    }

    private void rebuildSwatches(JPanel panel, GridBagConstraints gc,
                                  java.util.List<ColorGroup> groups, Frame owner) {
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
                JLabel nameLbl = new JLabel(kd.label);
                panel.add(nameLbl, kc);
                kc.gridx = 1; kc.weightx = 1;
                Color init = ThemeManager.getInstance().resolve(kd.key);
                ColorSwatch swatch = new ColorSwatch(owner, kd.key, init);
                swatch.updateModifiedIndicator();
                panel.add(swatch, kc);
                row++;
            }
        }
        // Vertical filler: push content to top
        GridBagConstraints filler = (GridBagConstraints) gc.clone();
        filler.gridx = 0; filler.gridy = row; filler.gridwidth = 2;
        filler.weighty = 1.0; filler.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), filler);
        panel.revalidate(); panel.repaint();
    }

    // ── SQL Format panel (完全重写，按设计文档组织) ──

    private JPanel buildSqlFormatPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Top: Profile toolbar + dialect
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));

        JLabel dialectLbl = new JLabel("方言:");
        dialectCombo = new JComboBox<>(new String[]{"Oracle", "MySQL", "PostgreSQL", "OceanBase"});
        dialectCombo.setSelectedItem(workingOptions.getDialect() != null ? workingOptions.getDialect() : "Oracle");
        dialectCombo.addActionListener(e -> {
            workingOptions.setDialect((String) dialectCombo.getSelectedItem());
            refreshPreview();
        });
        topBar.add(dialectLbl);
        topBar.add(dialectCombo);

        topBar.add(Box.createHorizontalStrut(16));

        JLabel profileLbl = new JLabel("Profile:");
        profileCombo = new JComboBox<>();
        refreshProfileCombo(profileCombo);
        profileCombo.setPreferredSize(new Dimension(160, 26));
        topBar.add(profileLbl);
        topBar.add(profileCombo);

        JButton saveProfileBtn = new JButton("保存");
        saveProfileBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "输入 Profile 名称:");
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

        JButton deleteProfileBtn = new JButton("删除");
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
                rebuildFormatTabs();
                applyWorkingToControls(dialectCombo);
                refreshPreview();
            }
        });

        p.add(topBar, BorderLayout.NORTH);

        // Center: left tabs + right preview
        formatTabs = new JTabbedPane(JTabbedPane.LEFT);
        buildFormatTabs();
        formatTabs.addChangeListener(e -> {
            int idx = formatTabs.getSelectedIndex();
            if (idx < 0) return;
            previewSection = null;
            manualPreviewSelection = false;
            int previewIdx = switch (idx) {
                case 2 -> { previewSection = "INSERT"; yield 1; }
                case 3 -> 5;
                case 4 -> 8;
                case 5 -> 9;
                default -> 0;
            };
            if (previewSqlCombo.getSelectedIndex() != previewIdx) {
                previewSqlCombo.setSelectedIndex(previewIdx);
            }
            refreshPreview();
        });

        // Preview area (right side)
        previewSqlCombo = new JComboBox<>(PREVIEW_SQLS);
        previewSqlCombo.addActionListener(e -> { previewSection = null; manualPreviewSelection = true; refreshPreview(); });

        previewArea = new RSyntaxTextArea(8, 60);
        previewArea.setSyntaxEditingStyle("text/plsql");
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
        previewToolbar.add(new JLabel("预览 SQL:"));
        previewToolbar.add(previewSqlCombo);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(previewToolbar, BorderLayout.NORTH);
        rightPanel.add(previewScroll, BorderLayout.CENTER);

        previewStatusLabel = new JLabel(" ");
        previewStatusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        rightPanel.add(previewStatusLabel, BorderLayout.SOUTH);

        JSplitPane hSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formatTabs, rightPanel);
        hSplit.setResizeWeight(0.35);
        hSplit.setContinuousLayout(true);
        p.add(hSplit, BorderLayout.CENTER);

        // Initial preview
        refreshPreview();

        applyPanelTheme(p);
        return p;
    }

    private void buildFormatTabs() {
        formatTabs.addTab("通用", scrollWrap(buildGeneralFormatPanel(dialectCombo, profileCombo)));
        formatTabs.addTab("DQL", scrollWrap(buildDqlPanel()));
        formatTabs.addTab("DML", scrollWrap(buildDmlPanel()));
        formatTabs.addTab("DDL", scrollWrap(buildDdlPanel()));
        formatTabs.addTab("PL/SQL", scrollWrap(buildPlsqlPanel()));
        formatTabs.addTab("注释/空白", scrollWrap(buildCommentsPanel()));
    }

    private JScrollPane scrollWrap(JPanel panel) {
        JScrollPane s = new JScrollPane(panel);
        s.setBorder(null);
        return s;
    }

    private void rebuildFormatTabs() {
        int oldIdx = formatTabs.getSelectedIndex();
        // Temporarily remove listener to avoid recursive refreshPreview
        javax.swing.event.ChangeListener[] cls = formatTabs.getChangeListeners();
        for (var cl : cls) formatTabs.removeChangeListener(cl);
        formatTabs.removeAll();
        buildFormatTabs();
        if (oldIdx >= 0 && oldIdx < formatTabs.getTabCount()) {
            formatTabs.setSelectedIndex(oldIdx);
        }
        for (var cl : cls) formatTabs.addChangeListener(cl);
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
        if (previewSection != null && !manualPreviewSelection) {
            for (int i = 0; i < PREVIEW_SQLS.length; i++) {
                if (PREVIEW_SQLS[i].equalsIgnoreCase(previewSection)) {
                    if (previewSqlCombo.getSelectedIndex() != i) {
                        previewSqlCombo.setSelectedIndex(i);
                    }
                    break;
                }
            }
        }
        String sql = switch (previewSqlCombo.getSelectedIndex()) {
            case 1 -> SAMPLE_INSERT;
            case 2 -> SAMPLE_UPDATE;
            case 3 -> SAMPLE_DELETE;
            case 4 -> SAMPLE_MERGE;
            case 5 -> SAMPLE_DDL;
            case 6 -> SAMPLE_INDEX;
            case 7 -> SAMPLE_PACKAGE;
            case 8 -> SAMPLE_PLSQL;
            case 9 -> SAMPLE_COMMENTS;
            case 10 -> SAMPLE_INLIST;
            case 11 -> SAMPLE_SETOPS;
            default -> SAMPLE_SELECT;
        };
        try {
            FormatResult result = PlSqlFormatter.format(sql, workingOptions);
            previewArea.setText(result.getEffectiveText());
            previewArea.setCaretPosition(0);
            int qs = result.getQualityScore();
            int diagCount = result.getDiagnostics().size();
            if (result.isFallback()) {
                previewStatusLabel.setText("⚠ 格式化失败，已保留原始代码（质量评分 " + qs + "/100）");
                previewStatusLabel.setForeground(new Color(0xD9534F));
            } else if (diagCount > 0) {
                previewStatusLabel.setText("⚠ 格式化完成，" + diagCount + " 个问题（质量评分 " + qs + "/100）");
                previewStatusLabel.setForeground(new Color(0xF0AD4E));
            } else {
                previewStatusLabel.setText("✓ 格式化完成（质量评分 " + qs + "/100）");
                previewStatusLabel.setForeground(new Color(0x5CB85C));
            }
        } catch (Exception e) {
            previewArea.setText(sql);
            previewStatusLabel.setText("✗ 格式化异常: " + e.getMessage());
            previewStatusLabel.setForeground(new Color(0xD9534F));
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
        p.add(new JLabel("关键字大小写:"), c);
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
        p.add(new JLabel("缩进空格数:"), c);
        c.gridx = 1;
        JSpinner indentSpin = new JSpinner(new SpinnerNumberModel(workingOptions.getIndentSize(), 0, 64, 1));
        indentSpin.addChangeListener(e -> {
            workingOptions.setIndentSize((Integer) indentSpin.getValue());
            refreshPreview();
        });
        p.add(indentSpin, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("最大行宽 (0=不限):"), c);
        c.gridx = 1;
        JSpinner widthSpin = new JSpinner(new SpinnerNumberModel(workingOptions.getMaxLineWidth(), 0, 999, 10));
        widthSpin.addChangeListener(e -> {
            workingOptions.setMaxLineWidth((Integer) widthSpin.getValue());
            refreshPreview();
        });
        p.add(widthSpin, c);

        row++;
        c.gridx = 0; c.gridy = row;
        p.add(new JLabel("换行符:"), c);
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
        p.add(new JLabel("逗号位置:"), c);
        c.gridx = 1;
        JComboBox<String> commaCombo = new JComboBox<>(new String[]{"TRAILING", "LEADING"});
        commaCombo.setSelectedItem(workingOptions.getCommaPosition().name());
        commaCombo.addActionListener(e -> {
            workingOptions.setCommaPosition(CommaPosition.valueOf((String) commaCombo.getSelectedItem()));
            refreshPreview();
        });
        p.add(commaCombo, c);

        applyPanelTheme(p);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        return wrapper;
    }

    // ── UI helper methods for format sub-panels ──

    private void addSection(JPanel p, GridBagConstraints c, int row, String title) {
        JLabel lbl = new JLabel(title);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        GridBagConstraints sc = (GridBagConstraints) c.clone();
        sc.gridx = 0; sc.gridy = row; sc.gridwidth = 2; sc.weightx = 0;
        p.add(lbl, sc);
    }

    private int addComboCtrl(JPanel p, GridBagConstraints c, int row, String label,
                              String value, String[] items, java.util.function.Consumer<String> onChange) {
        GridBagConstraints lc = (GridBagConstraints) c.clone();
        lc.gridx = 0; lc.gridy = row; lc.weightx = 0;
        p.add(new JLabel("  " + label + ":"), lc);
        GridBagConstraints vc = (GridBagConstraints) c.clone();
        vc.gridx = 1; vc.gridy = row; vc.weightx = 1;
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setSelectedItem(value);
        combo.addActionListener(e -> onChange.accept((String) combo.getSelectedItem()));
        p.add(combo, vc);
        return row + 1;
    }

    private int addSpinCtrl(JPanel p, GridBagConstraints c, int row, String label,
                             int value, int min, int max, java.util.function.Consumer<Integer> onChange) {
        GridBagConstraints lc = (GridBagConstraints) c.clone();
        lc.gridx = 0; lc.gridy = row; lc.weightx = 0;
        p.add(new JLabel("  " + label + ":"), lc);
        GridBagConstraints vc = (GridBagConstraints) c.clone();
        vc.gridx = 1; vc.gridy = row; vc.weightx = 1;
        JSpinner spin = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spin.addChangeListener(e -> {
            try { spin.commitEdit(); } catch (Exception ignored) {}
            onChange.accept((Integer) spin.getValue());
        });
        p.add(spin, vc);
        return row + 1;
    }

    private int addCheckCtrl(JPanel p, GridBagConstraints c, int row, String label,
                              boolean value, java.util.function.Consumer<Boolean> onChange) {
        GridBagConstraints lc = (GridBagConstraints) c.clone();
        lc.gridx = 0; lc.gridy = row; lc.weightx = 0;
        p.add(new JLabel("  " + label + ":"), lc);
        GridBagConstraints vc = (GridBagConstraints) c.clone();
        vc.gridx = 1; vc.gridy = row; vc.weightx = 1;
        JCheckBox cb = new JCheckBox();
        cb.setSelected(value);
        cb.addActionListener(e -> onChange.accept(cb.isSelected()));
        p.add(cb, vc);
        return row + 1;
    }

    private JPanel buildDqlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        addSection(p, c, row, "SELECT 列"); row++;
        row = addComboCtrl(p, c, row, "  列模式", workingOptions.getSelectColumnMode().name(),
            new String[]{"ALIGN","COMPACT","ONE_PER_LINE"}, v -> { workingOptions.setSelectColumnMode(SelectColumnMode.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  COMPACT每行列个数", workingOptions.getSelectColumnsPerRow(), 0, 20,
            v -> { workingOptions.setSelectColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  逗号位置", workingOptions.getCommaPosition().name(),
            new String[]{"TRAILING","LEADING"}, v -> { workingOptions.setCommaPosition(CommaPosition.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "FROM/JOIN"); row++;
        row = addCheckCtrl(p, c, row, "  FROM 前换行", workingOptions.isFromClauseNewline(),
            v -> { workingOptions.setFromClauseNewline(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  FROM 额外缩进", workingOptions.getFromClauseIndent(), 0, 8,
            v -> { workingOptions.setFromClauseIndent(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  JOIN 前换行", workingOptions.isJoinOnNewline(),
            v -> { workingOptions.setJoinOnNewline(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  子查询展开样式", workingOptions.isSubqueryFromNewline(),
            v -> { workingOptions.setSubqueryFromNewline(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  ON 条件对齐", workingOptions.isJoinOnAlign(),
            v -> { workingOptions.setJoinOnAlign(v); refreshPreview(); });

        addSection(p, c, row, "WHERE"); row++;
        row = addComboCtrl(p, c, row, "  AND/OR 位置", workingOptions.getWhereAndPosition().name(),
            new String[]{"LINE_START","LINE_END"}, v -> { workingOptions.setWhereAndPosition(WhereAndPosition.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  条件缩进", workingOptions.getWhereIndentSize(), 0, 8,
            v -> { workingOptions.setWhereIndentSize(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  子查询样式", workingOptions.getWhereSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { workingOptions.setWhereSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "集合操作"); row++;
        row = addCheckCtrl(p, c, row, "  UNION/MINUS 前换行", workingOptions.isSetOperatorNewline(),
            v -> { workingOptions.setSetOperatorNewline(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  UNION 两侧对齐", workingOptions.isSetOperatorAlign(),
            v -> { workingOptions.setSetOperatorAlign(v); refreshPreview(); });

        addSection(p, c, row, "子查询"); row++;
        row = addComboCtrl(p, c, row, "  SELECT 列中", workingOptions.getSelectListSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { workingOptions.setSelectListSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  FROM 中", workingOptions.getFromSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { workingOptions.setFromSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  通用默认", workingOptions.getSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { workingOptions.setSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  展开阈值", workingOptions.getSubqueryThreshold(), 10, 500,
            v -> { workingOptions.setSubqueryThreshold(v); refreshPreview(); });

        addSection(p, c, row, "IN 列表"); row++;
        row = addComboCtrl(p, c, row, "  列表格式", workingOptions.getInListFormat().name(),
            new String[]{"COMPACT","ONE_PER_LINE"}, v -> { workingOptions.setInListFormat(InListFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  每行值数", workingOptions.getInListColumnsPerRow(), 1, 20,
            v -> { workingOptions.setInListColumnsPerRow(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  自动换行阈值", workingOptions.getInListThreshold(), 3, 100,
            v -> { workingOptions.setInListThreshold(v); refreshPreview(); });

        addSection(p, c, row, "CTE (WITH ... AS)"); row++;
        row = addComboCtrl(p, c, row, "  CTE 格式", workingOptions.getCteFormat().name(),
            new String[]{"COMPACT","ONE_PER_LINE","ALIGN"}, v -> { workingOptions.setCteFormat(CteFormat.valueOf(v)); refreshPreview(); });

        applyPanelTheme(p);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel buildDmlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        addSection(p, c, row, "INSERT"); row++;
        row = addComboCtrl(p, c, row, "  列格式", workingOptions.getInsertColumnFormat().name(),
            new String[]{"COMPACT","ONE_PER_LINE"}, v -> { previewSection = "INSERT"; workingOptions.setInsertColumnFormat(InsertColumnFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  每行列数", workingOptions.getInsertColumnsPerRow(), 0, 20,
            v -> { previewSection = "INSERT"; workingOptions.setInsertColumnsPerRow(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  每行值数", workingOptions.getInsertValuesPerRow(), 0, 20,
            v -> { previewSection = "INSERT"; workingOptions.setInsertValuesPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  子查询样式", workingOptions.getInsertSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { previewSection = "INSERT"; workingOptions.setInsertSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "UPDATE"); row++;
        row = addCheckCtrl(p, c, row, "  SET 对齐", workingOptions.isUpdateSetAlign(),
            v -> { previewSection = "UPDATE"; workingOptions.setUpdateSetAlign(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  每行表达式数", workingOptions.getUpdateSetColumnsPerRow(), 0, 20,
            v -> { previewSection = "UPDATE"; workingOptions.setUpdateSetColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  逗号位置", workingOptions.getUpdateSetCommaPosition().name(),
            new String[]{"TRAILING","LEADING"}, v -> { previewSection = "UPDATE"; workingOptions.setUpdateSetCommaPosition(CommaPosition.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "DELETE"); row++;
        row = addCheckCtrl(p, c, row, "  FROM 前换行", workingOptions.isDeleteFromNewline(),
            v -> { previewSection = "DELETE"; workingOptions.setDeleteFromNewline(v); refreshPreview(); });

        addSection(p, c, row, "MERGE"); row++;
        row = addCheckCtrl(p, c, row, "  INTO 前换行", workingOptions.isMergeIntoNewline(),
            v -> { previewSection = "MERGE"; workingOptions.setMergeIntoNewline(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  WHEN 前换行", workingOptions.isMergeWhenNewline(),
            v -> { previewSection = "MERGE"; workingOptions.setMergeWhenNewline(v); refreshPreview(); });

        applyPanelTheme(p);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel buildDdlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        addSection(p, c, row, "CREATE TABLE"); row++;
        row = addCheckCtrl(p, c, row, "  列定义对齐", workingOptions.isColumnDefAlign(),
            v -> { workingOptions.setColumnDefAlign(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  每行列数", workingOptions.getColumnDefColumnsPerRow(), 0, 20,
            v -> { workingOptions.setColumnDefColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  类型大小写", workingOptions.getColumnDefTypeCase().name(),
            new String[]{"UPPER","LOWER","PRESERVE"}, v -> { workingOptions.setColumnDefTypeCase(KeywordCase.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  约束格式", workingOptions.getConstraintFormat().name(),
            new String[]{"INLINE","SEPARATE_LINE"}, v -> { workingOptions.setConstraintFormat(ConstraintFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  约束每行数", workingOptions.getConstraintColumnsPerRow(), 1, 20,
            v -> { workingOptions.setConstraintColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  存储子句", workingOptions.getStorageClauseFormat().name(),
            new String[]{"COMPACT","LINE_BREAK"}, v -> { workingOptions.setStorageClauseFormat(StorageClauseFormat.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "INDEX"); row++;
        row = addComboCtrl(p, c, row, "  索引列模式", workingOptions.getIndexColumnFormat().name(),
            new String[]{"COMPACT","ONE_PER_LINE"}, v -> { previewSection = "INDEX"; workingOptions.setIndexColumnFormat(IndexColumnFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  每行列数", workingOptions.getIndexColumnsPerRow(), 1, 20,
            v -> { previewSection = "INDEX"; workingOptions.setIndexColumnsPerRow(v); refreshPreview(); });

        addSection(p, c, row, "分区"); row++;
        row = addComboCtrl(p, c, row, "  分区格式", workingOptions.getPartitionFormat().name(),
            new String[]{"COMPACT","EXPAND"}, v -> { workingOptions.setPartitionFormat(PartitionFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  每行分区数", workingOptions.getPartitionColumnsPerRow(), 1, 20,
            v -> { workingOptions.setPartitionColumnsPerRow(v); refreshPreview(); });

        applyPanelTheme(p);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel buildPlsqlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        addSection(p, c, row, "声明/参数"); row++;
        row = addCheckCtrl(p, c, row, "  声明对齐 (:=)", workingOptions.isDeclarationAlign(),
            v -> { previewSection = "PACKAGE"; workingOptions.setDeclarationAlign(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  参数列表", workingOptions.getParameterListMode().name(),
            new String[]{"COMPACT","ONE_PER_LINE"}, v -> { previewSection = "PACKAGE"; workingOptions.setParameterListMode(ParameterListMode.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  每行参数数", workingOptions.getParameterColumnsPerRow(), 1, 10,
            v -> { previewSection = "PACKAGE"; workingOptions.setParameterColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  IN/OUT 大小写", workingOptions.getParameterDirectionCase().name(),
            new String[]{"UPPER","LOWER","PRESERVE"}, v -> { previewSection = "PACKAGE"; workingOptions.setParameterDirectionCase(KeywordCase.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  类型大小写", workingOptions.getParameterTypeCase().name(),
            new String[]{"UPPER","LOWER","PRESERVE"}, v -> { previewSection = "PACKAGE"; workingOptions.setParameterTypeCase(KeywordCase.valueOf(v)); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  INTO 变量对齐", workingOptions.isIntoVariableAlign(),
            v -> { previewSection = "PACKAGE"; workingOptions.setIntoVariableAlign(v); refreshPreview(); });

        addSection(p, c, row, "控制结构"); row++;
        row = addCheckCtrl(p, c, row, "  THEN 换行", workingOptions.isThenOnNewLine(),
            v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setThenOnNewLine(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  LOOP 换行", workingOptions.isLoopOnNewLine(),
            v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setLoopOnNewLine(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  ELSE 独立行", workingOptions.isElseOnNewLine(),
            v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setElseOnNewLine(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  EXCEPTION", workingOptions.getExceptionAlign().name(),
            new String[]{"INDENT","OUTDENT"}, v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setExceptionAlign(ExceptionAlign.valueOf(v)); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  END 对齐", workingOptions.isEndAlign(),
            v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setEndAlign(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  缩进空格数", workingOptions.getPlsqlIndentSize(), 0, 64,
            v -> { workingOptions.setPlsqlIndentSize(v); previewSection = "PL/SQL BLOCK"; refreshPreview(); });
        row = addComboCtrl(p, c, row, "  FOR LOOP", workingOptions.getForLoopFormat().name(),
            new String[]{"COMPACT","EXPAND"}, v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setForLoopFormat(ForLoopFormat.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  CASE", workingOptions.getCaseExpressionFormat().name(),
            new String[]{"COMPACT","EXPAND"}, v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setCaseExpressionFormat(CaseExpressionFormat.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "括号间距"); row++;
        row = addComboCtrl(p, c, row, "  括号内空格", workingOptions.getParenthesisSpacing().name(),
            new String[]{"NONE","INSIDE","BOTH"}, v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setParenthesisSpacing(ParenthesisSpacing.valueOf(v)); refreshPreview(); });

        applyPanelTheme(p);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel buildCommentsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.weightx = 0;
        int row = 0;

        addSection(p, c, row, "注释"); row++;
        row = addCheckCtrl(p, c, row, "  保留注释", workingOptions.isCommentPreserve(),
            v -> { previewSection = "COMMENTS"; workingOptions.setCommentPreserve(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  注释缩进", workingOptions.isCommentIndent(),
            v -> { previewSection = "COMMENTS"; workingOptions.setCommentIndent(v ? CommentIndent.CODE_LEVEL : CommentIndent.BLOCK_LEVEL); refreshPreview(); });

        addSection(p, c, row, "空白"); row++;
        row = addComboCtrl(p, c, row, "  空行处理", workingOptions.getBlankLineHandling().name(),
            new String[]{"PRESERVE","COLLAPSE","STRIP"}, v -> { previewSection = "COMMENTS"; workingOptions.setBlankLineHandling(BlankLineHandling.valueOf(v)); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  行尾空格清理", workingOptions.isTrailingWhitespaceTrim(),
            v -> { previewSection = "COMMENTS"; workingOptions.setTrailingWhitespaceTrim(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  块前空行", workingOptions.isBlankLineBeforeBlock(),
            v -> { previewSection = "COMMENTS"; workingOptions.setBlankLineBeforeBlock(v); refreshPreview(); });

        applyPanelTheme(p);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        return wrapper;
    }

    // ── Common panel (自动保存 + 启动画面等常用设置) ──

    private JPanel buildCommonPanel() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.gridx = 0; c.weightx = 0;

        // ── Section 1: 自动保存 ──
        GridBagConstraints hc = new GridBagConstraints();
        hc.gridx = 0; hc.gridy = 0; hc.gridwidth = 3; hc.weightx = 1;
        hc.insets = new Insets(0, 0, 8, 0); hc.anchor = GridBagConstraints.WEST;
        JLabel autoHeader = new JLabel("自动保存");
        autoHeader.setFont(autoHeader.getFont().deriveFont(Font.BOLD, 14f));
        grid.add(autoHeader, hc);

        int row = 1;
        c.gridy = row; c.gridwidth = 1;
        grid.add(new JLabel("自动保存间隔:"), c);
        c.gridx = 1; c.weightx = 1;
        int interval = configManager.getAutoSaveInterval();
        autoIntervalSpinner = new JSpinner(new SpinnerNumberModel(interval, 1, 3600, 1));
        grid.add(autoIntervalSpinner, c);
        c.gridx = 2; c.weightx = 0;
        autoUnitCombo = new JComboBox<>(new String[]{"秒", "分", "小时"});
        String unit = configManager.getAutoSaveUnit();
        autoUnitCombo.setSelectedIndex(unit.equals("hours") ? 2 : unit.equals("minutes") ? 1 : 0);
        grid.add(autoUnitCombo, c);

        row++;
        c.gridy = row; c.gridx = 0; c.weightx = 0;
        grid.add(new JLabel("自动保存路径:"), c);
        c.gridx = 1; c.weightx = 1;
        autoPathField = new JTextField(configManager.getAutoSavePath());
        grid.add(autoPathField, c);
        c.gridx = 2; c.weightx = 0;
        var browseBtn = new JButton("浏览...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                autoPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        grid.add(browseBtn, c);

        // ── Section 2: 启动画面 ──
        row += 2;
        hc.gridy = row;
        hc.insets = new Insets(12, 0, 8, 0);
        JLabel splashHeader = new JLabel("启动画面");
        splashHeader.setFont(splashHeader.getFont().deriveFont(Font.BOLD, 14f));
        grid.add(splashHeader, hc);

        row++;
        c.gridy = row; c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        c.insets = new Insets(4, 6, 4, 6);
        grid.add(new JLabel("最短停留(毫秒):"), c);
        c.gridx = 1; c.weightx = 1;
        splashMinField = new JTextField(String.valueOf(configManager.getSplashMinDuration()), 10);
        splashMinField.setToolTipText("启动画面至少显示的时间，用于观察启动过程");
        grid.add(splashMinField, c);

        row++;
        c.gridy = row; c.gridx = 0; c.weightx = 0;
        grid.add(new JLabel("最长等待(毫秒):"), c);
        c.gridx = 1; c.weightx = 1;
        splashMaxField = new JTextField(String.valueOf(configManager.getSplashMaxDuration()), 10);
        splashMaxField.setToolTipText("超过此时间强制关闭启动画面，防止卡死");
        grid.add(splashMaxField, c);

        // ── Section 3: 自动补全 ──
        row += 2;
        hc.gridy = row;
        hc.insets = new Insets(12, 0, 8, 0);
        JLabel acHeader = new JLabel("自动补全");
        acHeader.setFont(acHeader.getFont().deriveFont(Font.BOLD, 14f));
        grid.add(acHeader, hc);

        row++;
        c.gridy = row; c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        c.insets = new Insets(4, 6, 4, 6);
        grid.add(new JLabel("补全延迟(毫秒):"), c);
        c.gridx = 1; c.weightx = 1;
        autocompleteDelayField = new JTextField(configManager.getPreference("autocomplete.delay", "300"), 10);
        autocompleteDelayField.setToolTipText("输入后等待多少毫秒触发自动补全");
        grid.add(autocompleteDelayField, c);

        applyPanelTheme(grid);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(grid, BorderLayout.NORTH);
        return wrapper;
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
        var addTypeBtn = new JButton("+ 类型");
        addTypeBtn.addActionListener(e -> {
            if (metadataTree.getLastSelectedPathComponent() instanceof MetadataNode mn) {
                var td = new TypeDef();
                td.setLabel("新类型");
                td.setTypeCode("NEW_TYPE");
                td.setQueryType("SQL");
                td.setExpandable(false);
                typeTableModel.types.add(td);
                typeTableModel.fireTableRowsInserted(typeTableModel.types.size() - 1, typeTableModel.types.size() - 1);
                saveMetadataConfig();
            }
        });
        var delTypeBtn = new JButton("- 删除");
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
        sqlScroll.setBorder(BorderFactory.createTitledBorder("SQL 查询 (选中后编辑)"));

        sqlEditPanel = new JPanel(new BorderLayout());
        sqlEditPanel.add(sqlScroll, BorderLayout.CENTER);
        var saveSqlBtn = new JButton("保存 SQL");
        saveSqlBtn.addActionListener(e -> saveCurrentSql());
        var existingBtn = new JButton("包含现有");
        existingBtn.addActionListener(e -> {
            int sel = typeTable.getSelectedRow();
            if (sel >= 0) {
                var td = typeTableModel.types.get(sel);
                if ("FIXED_LIST".equals(td.getQueryType())) {
                    String vals = String.join("\n", td.getFixedValues());
                    sqlArea.setText("# FIXED_LIST 类型，不需要 SQL，固定值:\n" + vals);
                }
            }
        });
        sqlEditPanel.add(saveSqlBtn, BorderLayout.SOUTH);

        columnModel = new ColumnsTableModel();
        columnTable = new JTable(columnModel);

        var colBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        var addColBtn = new JButton("+ 自定义");
        addColBtn.addActionListener(e -> {
            int sel = typeTable.getSelectedRow();
            if (sel >= 0) {
                columnModel.cols.add(new DbMetadataConfig.CustomColumn("新列", "SELECT ? FROM ... WHERE owner = ?"));
                columnModel.fireTableRowsInserted(columnModel.cols.size() - 1, columnModel.cols.size() - 1);
                saveMetadataConfig();
            }
        });
        var delColBtn = new JButton("- 删除");
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

        helpLabel = new JLabel("自定义列在对应类型的节点上显示额外信息，状态栏、快速提示等；SQL 中的 ? 会被当前 Schema 和对象名替换。");
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
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("数据库类型");
        for (var cfg : metadataConfigs) {
            root.add(new MetadataNode(cfg));
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
            sqlArea.setText("# FIXED_LIST 类型，不需要 SQL，固定值:\n"
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

    // ── Font panel (字体个性化) ──

    private JComboBox<String> fontNameCombo;
    private JSpinner fontSizeSpinner;
    private JPanel fontColorSwatch;
    private JLabel fontSectionHeader;
    private JPanel fontPreviewPanel;
    private String fontPanelSelectedKey;
    private RSyntaxTextArea fontPreviewEditor;

    private JPanel buildFontPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Left tree
        DefaultMutableTreeNode fontRoot = new DefaultMutableTreeNode("字体");
        java.util.List<String> keys = new java.util.ArrayList<>(FontManager.getKeys());
        for (String key : keys) {
            fontRoot.add(new DefaultMutableTreeNode(FontManager.getLabel(key)));
        }
        JTree fontTree = new JTree(new DefaultTreeModel(fontRoot));
        fontTree.setRootVisible(false);
        fontTree.setShowsRootHandles(true);
        fontTree.setRowHeight(24);

        // Right settings panel
        JPanel settingPanel = new JPanel(new GridBagLayout());
        settingPanel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 8, 8, 8);

        String[] allFonts = FontManager.getInstance().getAllFonts();

        // Section header
        c.gridx = 0; c.gridy = 0; c.gridwidth = 3; c.weightx = 1;
        fontSectionHeader = new JLabel(" ");
        fontSectionHeader.setFont(fontSectionHeader.getFont().deriveFont(Font.BOLD, 14f));
        settingPanel.add(fontSectionHeader, c);
        c.gridwidth = 1;

        c.anchor = GridBagConstraints.WEST;
        Dimension labelSize = new Dimension(50, 24);

        Runnable         addControl = () -> { c.gridx = 1; c.weightx = 0.4; c.gridwidth = 1; };
        Runnable addSpacer = () -> { c.gridx = 2; c.weightx = 0.6; settingPanel.add(new JLabel(""), c); };

        // Font name row
        JLabel fontLabel = new JLabel("字体:");
        fontLabel.setPreferredSize(labelSize);
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        settingPanel.add(fontLabel, c);
        addControl.run();
        fontNameCombo = new JComboBox<>();
        for (String fn : allFonts) fontNameCombo.addItem(FontManager.getFontLabel(fn));
        fontNameCombo.setEditable(true);
        settingPanel.add(fontNameCombo, c);
        addSpacer.run();

        // Size row
        JLabel sizeLabel = new JLabel("大小:");
        sizeLabel.setPreferredSize(labelSize);
        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        settingPanel.add(sizeLabel, c);
        addControl.run();
        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(12, 6, 72, 1));
        fontSizeSpinner.setPreferredSize(new Dimension(70, 26));
        settingPanel.add(fontSizeSpinner, c);
        addSpacer.run();

        // Color picker row
        JLabel colorLabel = new JLabel("颜色:");
        colorLabel.setPreferredSize(labelSize);
        c.gridx = 0; c.gridy = 3; c.weightx = 0; c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        settingPanel.add(colorLabel, c);
        addControl.run();
        fontColorSwatch = new JPanel();
        fontColorSwatch.setPreferredSize(new Dimension(36, 24));
        fontColorSwatch.setBorder(BorderFactory.createLineBorder(new Color(0x888888)));
        fontColorSwatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fontColorSwatch.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent ev) {
                Color cur = fontColorSwatch.getBackground();
                Color chosen = JColorChooser.showDialog(SettingsDialog.this, "选择字体颜色 - " + FontManager.getLabel(fontPanelSelectedKey), cur);
                if (chosen != null) {
                    fontColorSwatch.setBackground(chosen);
                    applyFontOverride();
                }
            }
        });
        settingPanel.add(fontColorSwatch, c);

        // Preview panel
        c.gridx = 0; c.gridy = 4; c.gridwidth = 3; c.weightx = 1; c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        fontPreviewPanel = new JPanel(new BorderLayout());
        fontPreviewPanel.setBorder(BorderFactory.createTitledBorder("预览"));
        fontPreviewPanel.setOpaque(true);
        fontPreviewPanel.setBackground(ThemeManager.getInstance().resolve("bg.editor"));
        fontPreviewPanel.setPreferredSize(new Dimension(0, 80));
        // Default: a read-only RSyntaxTextArea for code-style preview
        fontPreviewEditor = new RSyntaxTextArea(3, 30);
        fontPreviewEditor.setSyntaxEditingStyle("text/plsql");
        fontPreviewEditor.setEditable(false);
        fontPreviewEditor.setHighlightCurrentLine(false);
        fontPreviewEditor.setCodeFoldingEnabled(false);
        fontPreviewEditor.setBorder(null);
        fontPreviewPanel.add(fontPreviewEditor, BorderLayout.CENTER);
        settingPanel.add(fontPreviewPanel, c);

        // Filler
        c.gridx = 0; c.gridy = 5; c.gridwidth = 3; c.weighty = 1;
        settingPanel.add(Box.createGlue(), c);

        // Reset button bottom
        c.gridx = 0; c.gridy = 6; c.gridwidth = 3; c.weighty = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        JButton resetBtn = new JButton("重置为默认");
        settingPanel.add(resetBtn, c);

        // ── Selection handler ──
        Runnable loadFontSettings = () -> {
            var node = (DefaultMutableTreeNode) fontTree.getLastSelectedPathComponent();
            if (node == null || node.isRoot()) return;
            int idx = fontRoot.getIndex(node);
            if (idx < 0 || idx >= keys.size()) return;
            fontPanelSelectedKey = keys.get(idx);
            String val = FontManager.getInstance().resolveValue(fontPanelSelectedKey);
            String curName = val.split(",")[0].trim();
            int curSize = Integer.parseInt(val.split(",")[1].trim());

            for (int i = 0; i < allFonts.length; i++) {
                if (allFonts[i].equalsIgnoreCase(curName)) {
                    fontNameCombo.setSelectedIndex(i);
                    break;
                }
            }
            fontSizeSpinner.setValue(curSize);
            Color fc = FontManager.getInstance().resolveColor(fontPanelSelectedKey);
            fontColorSwatch.setBackground(fc != null ? fc : ThemeManager.getInstance().resolve("fg.main"));
            fontSectionHeader.setText(FontManager.getLabel(fontPanelSelectedKey));
            rebuildFontPreview();
            applyFontPreview();
        };

        fontTree.addTreeSelectionListener(e -> loadFontSettings.run());

        // ── Change listeners ──
        Runnable onFontChange = () -> {
            if (fontPanelSelectedKey == null) return;
            applyFontOverride();
        };
        fontNameCombo.addActionListener(e -> onFontChange.run());
        fontSizeSpinner.addChangeListener(e -> onFontChange.run());

        // ── Reset handler ──
        resetBtn.addActionListener(e -> {
            if (fontPanelSelectedKey == null) return;
            String def = FontManager.getDefault(fontPanelSelectedKey);
            FontManager.getInstance().setOverride(fontPanelSelectedKey, def);
            String defName = def.split(",")[0].trim();
            for (int i = 0; i < allFonts.length; i++) {
                if (allFonts[i].equalsIgnoreCase(defName)) {
                    fontNameCombo.setSelectedIndex(i);
                    break;
                }
            }
            fontSizeSpinner.setValue(Integer.parseInt(def.split(",")[1].trim()));
            fontColorSwatch.setBackground(ThemeManager.getInstance().resolve("fg.main"));
            applyFontOverride();
        });

        // ── Layout ──
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(fontTree), settingPanel);
        split.setResizeWeight(0.25);
        root.add(split, BorderLayout.CENTER);

        // Select first item
        SwingUtilities.invokeLater(() -> {
            fontTree.setSelectionRow(0);
        });

        return root;
    }

    private Color getRstaCommentColor() {
        try {
            String path = ThemeManager.getInstance().getCurrentTheme().config("rsta.theme");
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in != null) {
                    Color c = Theme.load(in).scheme.getStyle(TokenTypes.COMMENT_EOL).foreground;
                    if (c != null) return c;
                }
            }
            try (InputStream in = RSyntaxTextArea.class.getResourceAsStream(path)) {
                if (in != null) {
                    Color c = Theme.load(in).scheme.getStyle(TokenTypes.COMMENT_EOL).foreground;
                    if (c != null) return c;
                }
            }
        } catch (Exception ignored) {}
        return new Color(0x6A9955); // fallback dark theme comment green
    }

    private void applyFontOverride() {
        String raw = (String) fontNameCombo.getSelectedItem();
        if (raw == null) return;
        int bracket = raw.indexOf("  [");
        if (bracket > 0) raw = raw.substring(0, bracket);
        int size = (Integer) fontSizeSpinner.getValue();
        Color color = fontColorSwatch.getBackground();
        if (fontPanelSelectedKey != null) {
            FontManager.getInstance().setOverride(fontPanelSelectedKey, raw, size, color);
        }
        applyFontPreview();
    }

    private void rebuildFontPreview() {
        fontPreviewPanel.removeAll();
        String key = fontPanelSelectedKey;
        if (key == null) { fontPreviewPanel.repaint(); return; }

        FontManager fm = FontManager.getInstance();
        ThemeManager tm = ThemeManager.getInstance();
        Color editorBg = tm.resolve("bg.editor");
        Color panelBg = tm.resolve("bg.panel");
        Color mainBg = tm.resolve("bg.main");

        if ("font.editor".equals(key) || "font.editor.comment".equals(key)) {
            String text = FontManager.getPreviewText(key);
            fontPreviewEditor.setText(text);
            fontPreviewEditor.setBackground(editorBg);
            try {
                String path = tm.getCurrentTheme().config("rsta.theme");
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                    if (in != null) { Theme.load(in).apply(fontPreviewEditor); }
                }
                try (InputStream in = RSyntaxTextArea.class.getResourceAsStream(path)) {
                    if (in != null) { Theme.load(in).apply(fontPreviewEditor); }
                }
            } catch (Exception ignored) {}
            // Apply user font/size/color AFTER theme (theme overrides font)
            fontPreviewEditor.setFont(fm.resolve(key).deriveFont((float) Math.min(fm.resolve(key).getSize(), 28)));
            Color fc = fm.resolveColor(key);
            if (fc != null) fontPreviewEditor.setForeground(fc);
            fontPreviewEditor.setEditable(false);
            fontPreviewPanel.add(fontPreviewEditor, BorderLayout.CENTER);
        } else if ("font.table".equals(key)) {
            String[] cols = {"用户名", "状态", "创建时间"};
            Object[][] data = {{"张三", "ACTIVE", "2024-01-15"}, {"李四", "INACTIVE", "2024-02-20"}};
            JTable table = new JTable(data, cols);
            table.setRowHeight(Math.min(fm.resolve("font.table").getSize() + 6, 24));
            table.setEnabled(false);
            table.setBackground(editorBg);
            table.setForeground(tm.resolve("fg.main"));
            table.setFont(fm.resolve("font.table"));
            table.getTableHeader().setFont(fm.resolve("font.ui.bold"));
            Color fc = fm.resolveColor(key);
            if (fc != null) table.setForeground(fc);
            fontPreviewPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        } else if ("font.mono".equals(key)) {
            JTextArea ta = new JTextArea(FontManager.getPreviewText(key));
            ta.setFont(fm.resolve("font.mono"));
            ta.setBackground(editorBg);
            ta.setForeground(tm.resolve("fg.main"));
            Color fc = fm.resolveColor(key);
            if (fc != null) ta.setForeground(fc);
            ta.setEditable(false);
            ta.setBorder(null);
            fontPreviewPanel.add(ta, BorderLayout.CENTER);
        } else {
            JPanel sim = new JPanel(new BorderLayout());
            sim.setBackground(mainBg);
            JLabel line1 = new JLabel(FontManager.getPreviewText(key));
            line1.setFont(fm.resolve(key));
            line1.setForeground(tm.resolve("fg.main"));
            Color fc = fm.resolveColor(key);
            if (fc != null) line1.setForeground(fc);
            line1.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            sim.add(line1, BorderLayout.CENTER);
            JLabel line2 = new JLabel("次要信息 / Secondary Info");
            line2.setFont(fm.resolve("font.status"));
            line2.setForeground(tm.resolve("fg.muted"));
            line2.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
            sim.add(line2, BorderLayout.SOUTH);
            fontPreviewPanel.add(sim, BorderLayout.CENTER);
        }
        fontPreviewPanel.revalidate();
        fontPreviewPanel.repaint();
    }

    private void applyFontPreview() {
        String key = fontPanelSelectedKey;
        if (key == null) return;
        rebuildFontPreview();
    }

    // ── Save settings ──

    private void saveSettings() {
        formatOptions.copyFrom(workingOptions);
        ThemeManager.getInstance().saveToConfig(configManager);
        FontManager.getInstance().saveToConfig(configManager);
        configManager.setPreference("format.dialect", workingOptions.getDialect());
        configManager.setPreference("format.keywordCase", workingOptions.getKeywordCase().name());
        configManager.setPreference("format.indent", String.valueOf(workingOptions.getIndentSize()));
        configManager.setPreference("format.maxWidth", String.valueOf(workingOptions.getMaxLineWidth()));
        configManager.setPreference("format.lineEnding", workingOptions.getLineEnding());
        if (autocompleteDelayField != null) configManager.setPreference("autocomplete.delay", autocompleteDelayField.getText());
        // 常用配置 - 自动保存
        if (autoIntervalSpinner != null) {
            configManager.setAutoSaveInterval((Integer) autoIntervalSpinner.getValue());
        }
        if (autoUnitCombo != null) {
            int idx = autoUnitCombo.getSelectedIndex();
            configManager.setAutoSaveUnit(idx == 2 ? "hours" : idx == 1 ? "minutes" : "seconds");
        }
        if (autoPathField != null) {
            configManager.setAutoSavePath(autoPathField.getText());
        }
        // 常用配置 - 启动画面
        if (splashMinField != null) {
            try { configManager.setSplashMinDuration(Integer.parseInt(splashMinField.getText())); }
            catch (NumberFormatException ignored) {}
        }
        if (splashMaxField != null) {
            try { configManager.setSplashMaxDuration(Integer.parseInt(splashMaxField.getText())); }
            catch (NumberFormatException ignored) {}
        }
        // Apply color overrides immediately
        if (owner instanceof MainFrame) {
            ((MainFrame) owner).reapplyTheme();
        }
        JOptionPane.showMessageDialog(this, "设置已保存");
        dispose();
    }

    // ── Theme helper ──

    private void applyPanelTheme(JPanel panel) {
        panel.setBackground(ThemeManager.getInstance().resolve("bg.main"));
    }
}
