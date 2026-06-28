package com.kylin.plsql.ui.dialog;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.config.DbMetadataConfig;
import com.kylin.plsql.core.config.DbMetadataConfig.TypeDef;
import com.kylin.plsql.core.format.FormatOptions;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
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

    private static final String[] FORMAT_UNITS = {"\u79D2", "\u5206", "\u5C0F\u65F6"};

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
        setSize(800, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        var tabbed = new JTabbedPane();
        tabbed.addTab("\u901A\u7528", buildGeneralPanel());
        tabbed.addTab("\u4E3B\u9898", buildThemePanel());
        tabbed.addTab("SQL \u683C\u5F0F\u5316", buildFormatPanel());
        tabbed.addTab("\u81EA\u52A8\u4FDD\u5B58", buildAutosavePanel());
        tabbed.addTab("\u5143\u6570\u636E\u914D\u7F6E", buildMetadataPanel());

        add(tabbed, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveBtn = new JButton("\u5E94\u7528");
        saveBtn.addActionListener(e -> saveSettings());
        cancelBtn = new JButton("\u53D6\u6D88");
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

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

        return p;
    }

    private void loadConnections() {
        connListModel.clear();
        for (var ci : configManager.loadConnections()) {
            connListModel.addElement(ci.getName() + " (" + ci.getDbType() + ")");
        }
    }

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

    private JPanel buildFormatPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        c.gridx = 0; c.weightx = 0;
        c.gridy = 0; p.add(new JLabel("\u5173\u952E\u5B57\u5927\u5C0F\u5199:"), c);
        c.gridx = 1; c.weightx = 1;
        var keywordCase = new JComboBox<>(new String[]{"\u5927\u5199", "\u5C0F\u5199", "\u9996\u5B57\u6BCD\u5927\u5199"});
        p.add(keywordCase, c);

        c.gridy = 1; c.gridx = 0; c.weightx = 0;
        p.add(new JLabel("\u8FDE\u63A5\u7A7A\u683C\u6570:"), c);
        c.gridx = 1;
        var indentSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 8, 1));
        p.add(indentSpinner, c);

        c.gridy = 2; c.gridx = 0;
        p.add(new JLabel("\u6700\u5927\u5BBD (0=\u4E0D\u9650):"), c);
        c.gridx = 1;
        var widthSpinner = new JSpinner(new SpinnerNumberModel(120, 0, 999, 10));
        p.add(widthSpinner, c);

        c.gridy = 3; c.gridx = 0;
        p.add(new JLabel("\u6362\u884C\u7B26:"), c);
        c.gridx = 1;
        var lineSep = new JComboBox<>(new String[]{"LF", "CRLF"});
        p.add(lineSep, c);

        loadFormatSettings(keywordCase, indentSpinner, widthSpinner, lineSep);
        return p;
    }

    private void loadFormatSettings(JComboBox<String> kc, JSpinner indent, JSpinner width, JComboBox<String> ls) {
        var fmt = configManager.getPreference("format.keywordCase", null);
        if ("LOWER".equals(fmt)) kc.setSelectedIndex(1);
        else if ("CAPITALIZE".equals(fmt)) kc.setSelectedIndex(2);
        else kc.setSelectedIndex(0);
        try { indent.setValue(Integer.parseInt(configManager.getPreference("format.indent", "4"))); } catch (Exception ignored) {}
        try { width.setValue(Integer.parseInt(configManager.getPreference("format.maxWidth", "120"))); } catch (Exception ignored) {}
        ls.setSelectedItem(configManager.getPreference("format.lineSeparator", "LF"));
    }

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

        return p;
    }

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

    private void saveSettings() {
        configManager.setPreference("format.indent", "4");
        JOptionPane.showMessageDialog(this, "\u8BBE\u7F6E\u5DF2\u4FDD\u5B58");
        dispose();
    }
}
