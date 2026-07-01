package com.kylin.plsql.ui.dialog.settings;

import com.kylin.plsql.ui.MainFrame;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.config.DbMetadataConfig;
import com.kylin.plsql.core.config.DbMetadataConfig.TypeDef;
import com.kylin.plsql.core.format.enums.BlankLineHandling;
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
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
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
                    Color c = JColorChooser.showDialog(parent, "\u9009\u62E9\u989C\u8272 - " + configKey, color);
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
    private final Frame owner;
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
    private JLabel previewStatusLabel;
    private JComboBox<String> previewSqlCombo;
    private JTabbedPane formatTabs;
    private JComboBox<String> dialectCombo;
    private JComboBox<String> profileCombo;
    private String previewSection;
    private boolean manualPreviewSelection;

    private static final String[] FORMAT_UNITS = {"\u79D2", "\u5206", "\u5C0F\u65F6"};
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
        "-- \u67E5\u8BE2\u5458\u5DE5\u4FE1\u606F\n" +
        "SELECT e.id,  -- \u5458\u5DE5ID\n" +
        "       e.name\n" +
        "  FROM employees e\n" +
        " WHERE e.status = 'ACTIVE'\n" +
        "/* \u6309\u7EC4\u7EC7\u67E5\u8BE2 */\n" +
        "   AND e.dept_id = 10\n" +
        "ORDER BY e.name;\n" +
        "\n" +
        "-- \u66F4\u65B0\u72B6\u6001\n" +
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
        super(owner, "\u8BBE\u7F6E", true);
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
        root.add(new DefaultMutableTreeNode("\u4E3B\u9898\u4E2A\u6027\u5316"));
        root.add(new DefaultMutableTreeNode("\u81EA\u52A8\u4FDD\u5B58"));
        root.add(new DefaultMutableTreeNode("\u5143\u6570\u636E\u914D\u7F6E"));
        root.add(new DefaultMutableTreeNode("SQL \u683C\u5F0F\u5316"));

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
            case "SQL \u683C\u5F0F\u5316" -> "sqlFormat";
            case "\u4E3B\u9898\u4E2A\u6027\u5316" -> "theme";
            case "\u81EA\u52A8\u4FDD\u5B58" -> "autosave";
            case "\u5143\u6570\u636E\u914D\u7F6E" -> "metadata";
            default -> null;
        };
    }

    // ── Theme panel (颜色个性化) ──

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
        JButton resetBtn = new JButton("\u91CD\u7F6E\u9ED8\u8BA4");
        resetBtn.setToolTipText("\u5C06\u5DF2\u4FEE\u6539\u7684\u989C\u8272\u91CD\u7F6E\u4E3A\u5F53\u524D\u4E3B\u9898\u5BF9\u5E94\u7684\u9ED8\u8BA4\u989C\u8272");
        resetBtn.addActionListener(e -> {
            int ret = JOptionPane.showConfirmDialog(this,
                "\u786E\u5B9A\u8981\u5C06\u6240\u6709\u989C\u8272\u91CD\u7F6E\u4E3A\u5F53\u524D\u4E3B\u9898\u7684\u9ED8\u8BA4\u503C\u5417\uFF1F",
                "\u91CD\u7F6E\u989C\u8272", JOptionPane.YES_NO_OPTION);
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

        JLabel dialectLbl = new JLabel("\u65B9\u8A00:");
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
        previewToolbar.add(new JLabel("\u9884\u89C8 SQL:"));
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
        formatTabs.addTab("\u901A\u7528", scrollWrap(buildGeneralFormatPanel(dialectCombo, profileCombo)));
        formatTabs.addTab("DQL", scrollWrap(buildDqlPanel()));
        formatTabs.addTab("DML", scrollWrap(buildDmlPanel()));
        formatTabs.addTab("DDL", scrollWrap(buildDdlPanel()));
        formatTabs.addTab("PL/SQL", scrollWrap(buildPlsqlPanel()));
        formatTabs.addTab("\u6CE8\u91CA/\u7A7A\u767D", scrollWrap(buildCommentsPanel()));
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
                previewStatusLabel.setText("\u26A0 \u683C\u5F0F\u5316\u5931\u8D25\uFF0C\u5DF2\u4FDD\u7559\u539F\u59CB\u4EE3\u7801\uFF08\u8D28\u91CF\u8BC4\u5206 " + qs + "/100\uFF09");
                previewStatusLabel.setForeground(new Color(0xD9534F));
            } else if (diagCount > 0) {
                previewStatusLabel.setText("\u26A0 \u683C\u5F0F\u5316\u5B8C\u6210\uFF0C" + diagCount + " \u4E2A\u95EE\u9898\uFF08\u8D28\u91CF\u8BC4\u5206 " + qs + "/100\uFF09");
                previewStatusLabel.setForeground(new Color(0xF0AD4E));
            } else {
                previewStatusLabel.setText("\u2713 \u683C\u5F0F\u5316\u5B8C\u6210\uFF08\u8D28\u91CF\u8BC4\u5206 " + qs + "/100\uFF09");
                previewStatusLabel.setForeground(new Color(0x5CB85C));
            }
        } catch (Exception e) {
            previewArea.setText(sql);
            previewStatusLabel.setText("\u2717 \u683C\u5F0F\u5316\u5F02\u5E38: " + e.getMessage());
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
        JSpinner indentSpin = new JSpinner(new SpinnerNumberModel(workingOptions.getIndentSize(), 0, 64, 1));
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

        addSection(p, c, row, "SELECT \u5217"); row++;
        row = addComboCtrl(p, c, row, "  \u5217\u6A21\u5F0F", workingOptions.getSelectColumnMode().name(),
            new String[]{"ALIGN","COMPACT","ONE_PER_LINE"}, v -> { workingOptions.setSelectColumnMode(SelectColumnMode.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  COMPACT\u6BCF\u884C\u5217\u4E2A\u6570", workingOptions.getSelectColumnsPerRow(), 0, 20,
            v -> { workingOptions.setSelectColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u9017\u53F7\u4F4D\u7F6E", workingOptions.getCommaPosition().name(),
            new String[]{"TRAILING","LEADING"}, v -> { workingOptions.setCommaPosition(CommaPosition.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "FROM/JOIN"); row++;
        row = addCheckCtrl(p, c, row, "  FROM \u524D\u6362\u884C", workingOptions.isFromClauseNewline(),
            v -> { workingOptions.setFromClauseNewline(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  FROM \u989D\u5916\u7F29\u8FDB", workingOptions.getFromClauseIndent(), 0, 8,
            v -> { workingOptions.setFromClauseIndent(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  JOIN \u524D\u6362\u884C", workingOptions.isJoinOnNewline(),
            v -> { workingOptions.setJoinOnNewline(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  \u5B50\u67E5\u8BE2\u5C55\u5F00\u6837\u5F0F", workingOptions.isSubqueryFromNewline(),
            v -> { workingOptions.setSubqueryFromNewline(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  ON \u6761\u4EF6\u5BF9\u9F50", workingOptions.isJoinOnAlign(),
            v -> { workingOptions.setJoinOnAlign(v); refreshPreview(); });

        addSection(p, c, row, "WHERE"); row++;
        row = addComboCtrl(p, c, row, "  AND/OR \u4F4D\u7F6E", workingOptions.getWhereAndPosition().name(),
            new String[]{"LINE_START","LINE_END"}, v -> { workingOptions.setWhereAndPosition(WhereAndPosition.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u6761\u4EF6\u7F29\u8FDB", workingOptions.getWhereIndentSize(), 0, 8,
            v -> { workingOptions.setWhereIndentSize(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u5B50\u67E5\u8BE2\u6837\u5F0F", workingOptions.getWhereSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { workingOptions.setWhereSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "\u96C6\u5408\u64CD\u4F5C"); row++;
        row = addCheckCtrl(p, c, row, "  UNION/MINUS \u524D\u6362\u884C", workingOptions.isSetOperatorNewline(),
            v -> { workingOptions.setSetOperatorNewline(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  UNION \u4E24\u4FA7\u5BF9\u9F50", workingOptions.isSetOperatorAlign(),
            v -> { workingOptions.setSetOperatorAlign(v); refreshPreview(); });

        addSection(p, c, row, "\u5B50\u67E5\u8BE2"); row++;
        row = addComboCtrl(p, c, row, "  SELECT \u5217\u4E2D", workingOptions.getSelectListSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { workingOptions.setSelectListSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  FROM \u4E2D", workingOptions.getFromSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { workingOptions.setFromSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u901A\u7528\u9ED8\u8BA4", workingOptions.getSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { workingOptions.setSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u5C55\u5F00\u9608\u503C", workingOptions.getSubqueryThreshold(), 10, 500,
            v -> { workingOptions.setSubqueryThreshold(v); refreshPreview(); });

        addSection(p, c, row, "IN \u5217\u8868"); row++;
        row = addComboCtrl(p, c, row, "  \u5217\u8868\u683C\u5F0F", workingOptions.getInListFormat().name(),
            new String[]{"COMPACT","ONE_PER_LINE"}, v -> { workingOptions.setInListFormat(InListFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u6BCF\u884C\u503C\u6570", workingOptions.getInListColumnsPerRow(), 1, 20,
            v -> { workingOptions.setInListColumnsPerRow(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u81EA\u52A8\u6362\u884C\u9608\u503C", workingOptions.getInListThreshold(), 3, 100,
            v -> { workingOptions.setInListThreshold(v); refreshPreview(); });

        addSection(p, c, row, "CTE (WITH ... AS)"); row++;
        row = addComboCtrl(p, c, row, "  CTE \u683C\u5F0F", workingOptions.getCteFormat().name(),
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
        row = addComboCtrl(p, c, row, "  \u5217\u683C\u5F0F", workingOptions.getInsertColumnFormat().name(),
            new String[]{"COMPACT","ONE_PER_LINE"}, v -> { previewSection = "INSERT"; workingOptions.setInsertColumnFormat(InsertColumnFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u6BCF\u884C\u5217\u6570", workingOptions.getInsertColumnsPerRow(), 0, 20,
            v -> { previewSection = "INSERT"; workingOptions.setInsertColumnsPerRow(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u6BCF\u884C\u503C\u6570", workingOptions.getInsertValuesPerRow(), 0, 20,
            v -> { previewSection = "INSERT"; workingOptions.setInsertValuesPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u5B50\u67E5\u8BE2\u6837\u5F0F", workingOptions.getInsertSubqueryStyle().name(),
            new String[]{"INLINE","EXPAND","AUTO"}, v -> { previewSection = "INSERT"; workingOptions.setInsertSubqueryStyle(SubqueryStyle.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "UPDATE"); row++;
        row = addCheckCtrl(p, c, row, "  SET \u5BF9\u9F50", workingOptions.isUpdateSetAlign(),
            v -> { previewSection = "UPDATE"; workingOptions.setUpdateSetAlign(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u6BCF\u884C\u8868\u8FBE\u5F0F\u6570", workingOptions.getUpdateSetColumnsPerRow(), 0, 20,
            v -> { previewSection = "UPDATE"; workingOptions.setUpdateSetColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u9017\u53F7\u4F4D\u7F6E", workingOptions.getUpdateSetCommaPosition().name(),
            new String[]{"TRAILING","LEADING"}, v -> { previewSection = "UPDATE"; workingOptions.setUpdateSetCommaPosition(CommaPosition.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "DELETE"); row++;
        row = addCheckCtrl(p, c, row, "  FROM \u524D\u6362\u884C", workingOptions.isDeleteFromNewline(),
            v -> { previewSection = "DELETE"; workingOptions.setDeleteFromNewline(v); refreshPreview(); });

        addSection(p, c, row, "MERGE"); row++;
        row = addCheckCtrl(p, c, row, "  INTO \u524D\u6362\u884C", workingOptions.isMergeIntoNewline(),
            v -> { previewSection = "MERGE"; workingOptions.setMergeIntoNewline(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  WHEN \u524D\u6362\u884C", workingOptions.isMergeWhenNewline(),
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
        row = addCheckCtrl(p, c, row, "  \u5217\u5B9A\u4E49\u5BF9\u9F50", workingOptions.isColumnDefAlign(),
            v -> { workingOptions.setColumnDefAlign(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u6BCF\u884C\u5217\u6570", workingOptions.getColumnDefColumnsPerRow(), 0, 20,
            v -> { workingOptions.setColumnDefColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u7C7B\u578B\u5927\u5C0F\u5199", workingOptions.getColumnDefTypeCase().name(),
            new String[]{"UPPER","LOWER","PRESERVE"}, v -> { workingOptions.setColumnDefTypeCase(KeywordCase.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u7EA6\u675F\u683C\u5F0F", workingOptions.getConstraintFormat().name(),
            new String[]{"INLINE","SEPARATE_LINE"}, v -> { workingOptions.setConstraintFormat(ConstraintFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u7EA6\u675F\u6BCF\u884C\u6570", workingOptions.getConstraintColumnsPerRow(), 1, 20,
            v -> { workingOptions.setConstraintColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u5B58\u50A8\u5B50\u53E5", workingOptions.getStorageClauseFormat().name(),
            new String[]{"COMPACT","LINE_BREAK"}, v -> { workingOptions.setStorageClauseFormat(StorageClauseFormat.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "INDEX"); row++;
        row = addComboCtrl(p, c, row, "  \u7D22\u5F15\u5217\u6A21\u5F0F", workingOptions.getIndexColumnFormat().name(),
            new String[]{"COMPACT","ONE_PER_LINE"}, v -> { previewSection = "INDEX"; workingOptions.setIndexColumnFormat(IndexColumnFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u6BCF\u884C\u5217\u6570", workingOptions.getIndexColumnsPerRow(), 1, 20,
            v -> { previewSection = "INDEX"; workingOptions.setIndexColumnsPerRow(v); refreshPreview(); });

        addSection(p, c, row, "\u5206\u533A"); row++;
        row = addComboCtrl(p, c, row, "  \u5206\u533A\u683C\u5F0F", workingOptions.getPartitionFormat().name(),
            new String[]{"COMPACT","EXPAND"}, v -> { workingOptions.setPartitionFormat(PartitionFormat.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u6BCF\u884C\u5206\u533A\u6570", workingOptions.getPartitionColumnsPerRow(), 1, 20,
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

        addSection(p, c, row, "\u58F0\u660E/\u53C2\u6570"); row++;
        row = addCheckCtrl(p, c, row, "  \u58F0\u660E\u5BF9\u9F50 (:=)", workingOptions.isDeclarationAlign(),
            v -> { previewSection = "PACKAGE"; workingOptions.setDeclarationAlign(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u53C2\u6570\u5217\u8868", workingOptions.getParameterListMode().name(),
            new String[]{"COMPACT","ONE_PER_LINE"}, v -> { previewSection = "PACKAGE"; workingOptions.setParameterListMode(ParameterListMode.valueOf(v)); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u6BCF\u884C\u53C2\u6570\u6570", workingOptions.getParameterColumnsPerRow(), 1, 10,
            v -> { previewSection = "PACKAGE"; workingOptions.setParameterColumnsPerRow(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  IN/OUT \u5927\u5C0F\u5199", workingOptions.getParameterDirectionCase().name(),
            new String[]{"UPPER","LOWER","PRESERVE"}, v -> { previewSection = "PACKAGE"; workingOptions.setParameterDirectionCase(KeywordCase.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  \u7C7B\u578B\u5927\u5C0F\u5199", workingOptions.getParameterTypeCase().name(),
            new String[]{"UPPER","LOWER","PRESERVE"}, v -> { previewSection = "PACKAGE"; workingOptions.setParameterTypeCase(KeywordCase.valueOf(v)); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  INTO \u53D8\u91CF\u5BF9\u9F50", workingOptions.isIntoVariableAlign(),
            v -> { previewSection = "PACKAGE"; workingOptions.setIntoVariableAlign(v); refreshPreview(); });

        addSection(p, c, row, "\u63A7\u5236\u7ED3\u6784"); row++;
        row = addCheckCtrl(p, c, row, "  THEN \u6362\u884C", workingOptions.isThenOnNewLine(),
            v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setThenOnNewLine(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  LOOP \u6362\u884C", workingOptions.isLoopOnNewLine(),
            v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setLoopOnNewLine(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  ELSE \u72EC\u7ACB\u884C", workingOptions.isElseOnNewLine(),
            v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setElseOnNewLine(v); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  EXCEPTION", workingOptions.getExceptionAlign().name(),
            new String[]{"INDENT","OUTDENT"}, v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setExceptionAlign(ExceptionAlign.valueOf(v)); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  END \u5BF9\u9F50", workingOptions.isEndAlign(),
            v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setEndAlign(v); refreshPreview(); });
        row = addSpinCtrl(p, c, row, "  \u7F29\u8FDB\u7A7A\u683C\u6570", workingOptions.getPlsqlIndentSize(), 0, 64,
            v -> { workingOptions.setPlsqlIndentSize(v); previewSection = "PL/SQL BLOCK"; refreshPreview(); });
        row = addComboCtrl(p, c, row, "  FOR LOOP", workingOptions.getForLoopFormat().name(),
            new String[]{"COMPACT","EXPAND"}, v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setForLoopFormat(ForLoopFormat.valueOf(v)); refreshPreview(); });
        row = addComboCtrl(p, c, row, "  CASE", workingOptions.getCaseExpressionFormat().name(),
            new String[]{"COMPACT","EXPAND"}, v -> { previewSection = "PL/SQL BLOCK"; workingOptions.setCaseExpressionFormat(CaseExpressionFormat.valueOf(v)); refreshPreview(); });

        addSection(p, c, row, "\u62EC\u53F7\u95F4\u8DDD"); row++;
        row = addComboCtrl(p, c, row, "  \u62EC\u53F7\u5185\u7A7A\u683C", workingOptions.getParenthesisSpacing().name(),
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

        addSection(p, c, row, "\u6CE8\u91CA"); row++;
        row = addCheckCtrl(p, c, row, "  \u4FDD\u7559\u6CE8\u91CA", workingOptions.isCommentPreserve(),
            v -> { previewSection = "COMMENTS"; workingOptions.setCommentPreserve(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  \u6CE8\u91CA\u7F29\u8FDB", workingOptions.isCommentIndent(),
            v -> { previewSection = "COMMENTS"; workingOptions.setCommentIndent(v); refreshPreview(); });

        addSection(p, c, row, "\u7A7A\u767D"); row++;
        row = addComboCtrl(p, c, row, "  \u7A7A\u884C\u5904\u7406", workingOptions.getBlankLineHandling().name(),
            new String[]{"PRESERVE","COLLAPSE","STRIP"}, v -> { previewSection = "COMMENTS"; workingOptions.setBlankLineHandling(BlankLineHandling.valueOf(v)); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  \u884C\u5C3E\u7A7A\u683C\u6E05\u7406", workingOptions.isTrailingWhitespaceTrim(),
            v -> { previewSection = "COMMENTS"; workingOptions.setTrailingWhitespaceTrim(v); refreshPreview(); });
        row = addCheckCtrl(p, c, row, "  \u5757\u524D\u7A7A\u884C", workingOptions.isBlankLineBeforeBlock(),
            v -> { previewSection = "COMMENTS"; workingOptions.setBlankLineBeforeBlock(v); refreshPreview(); });

        applyPanelTheme(p);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(p, BorderLayout.NORTH);
        return wrapper;
    }

    // ── Autosave panel (保留原有功能) ──

    private JPanel buildAutosavePanel() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.gridx = 0; c.weightx = 0;

        JLabel header = new JLabel("\u81EA\u52A8\u4FDD\u5B58\u8BBE\u7F6E");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        GridBagConstraints hc = new GridBagConstraints();
        hc.gridx = 0; hc.gridy = 0; hc.gridwidth = 3; hc.weightx = 1;
        hc.insets = new Insets(0, 0, 12, 0); hc.anchor = GridBagConstraints.WEST;
        grid.add(header, hc);

        int row = 1;
        c.gridy = row; c.gridwidth = 1;
        grid.add(new JLabel("\u81EA\u52A8\u4FDD\u5B58\u95F4\u9694:"), c);
        c.gridx = 1; c.weightx = 1;
        int interval = 30;
        try { interval = Integer.parseInt(configManager.getPreference("autosave.interval", "30")); } catch (Exception ignored) {}
        var intervalSpinner = new JSpinner(new SpinnerNumberModel(interval, 5, 3600, 5));
        grid.add(intervalSpinner, c);
        c.gridx = 2; c.weightx = 0;
        var unitCombo = new JComboBox<>(FORMAT_UNITS);
        String unit = configManager.getPreference("autosave.unit", "seconds");
        unitCombo.setSelectedIndex(unit.equals("hours") ? 2 : unit.equals("minutes") ? 1 : 0);
        grid.add(unitCombo, c);

        row++;
        c.gridy = row; c.gridx = 0; c.weightx = 0;
        grid.add(new JLabel("\u81EA\u52A8\u4FDD\u5B58\u8DEF\u5F84:"), c);
        c.gridx = 1; c.weightx = 1;
        var pathField = new JTextField(configManager.getPreference("autosave.path",
            configManager.getConfigPath().resolve("auto-save").toAbsolutePath().toString()));
        grid.add(pathField, c);
        c.gridx = 2; c.weightx = 0;
        var browseBtn = new JButton("\u6D4F\u89C8...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        grid.add(browseBtn, c);

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
        ThemeManager.getInstance().saveToConfig(configManager);
        configManager.setPreference("format.dialect", workingOptions.getDialect());
        configManager.setPreference("format.keywordCase", workingOptions.getKeywordCase().name());
        configManager.setPreference("format.indent", String.valueOf(workingOptions.getIndentSize()));
        configManager.setPreference("format.maxWidth", String.valueOf(workingOptions.getMaxLineWidth()));
        configManager.setPreference("format.lineEnding", workingOptions.getLineEnding());
        // Apply color overrides immediately
        if (owner instanceof MainFrame) {
            ((MainFrame) owner).reapplyTheme();
        }
        JOptionPane.showMessageDialog(this, "\u8BBE\u7F6E\u5DF2\u4FDD\u5B58");
        dispose();
    }

    // ── Theme helper ──

    private void applyPanelTheme(JPanel panel) {
        panel.setBackground(ThemeManager.getInstance().resolve("bg.main"));
    }
}
