package com.kylin.plsql.ui.dialog.connection;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.table.DefaultTableModel;

/** Database connection management dialog: create, edit, test, save, delete connections */
public class ConnectionDialog extends JDialog {
    private final ConfigManager configManager;
    private final ConnectionManager connectionManager;
    private final JList<ConnectionInfo> connList;
    private final DefaultListModel<ConnectionInfo> listModel;
    private JTextField nameField, hostField, portField, serviceField, userField, schemaField, timeoutField;
    private JTextArea urlField;
    private JScrollPane urlScroll;
    private JPasswordField passwordField;
    private JComboBox<String> dbTypeCombo;
    private JCheckBox useUrlCheck;
    private JLabel urlLabel, hostLabel, portLabel, serviceLabel;
    private JTable paramsTable;
    private DefaultTableModel paramsTableModel;
    private JTextField customJarField;
    private JComboBox<String> customDriverCombo;
    private ConnectionInfo editing;
    private String savedConnName;
    private boolean parsingUrl;

    private static final String REQ = "<font color='red'>*</font>";

    public ConnectionDialog(Frame owner, ConfigManager configManager, ConnectionManager connectionManager) {
        this(owner, configManager, connectionManager, null);
    }

    public ConnectionDialog(Frame owner, ConfigManager configManager, ConnectionManager connectionManager, String selectConnName) {
        super(owner, "管理连接", true);
        this.configManager = configManager;
        this.connectionManager = connectionManager;

        setSize(720, 560);
        setMinimumSize(new Dimension(600, 420));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        listModel = new DefaultListModel<>();
        connList = new JList<>(listModel);
        connList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        connList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ConnectionInfo) setText(((ConnectionInfo) value).getName());
                return c;
            }
        });
        connList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelected();
        });

        setLayout(new BorderLayout());

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("已保存的连接"));
        leftPanel.setPreferredSize(new Dimension(220, 0));

        JButton addBtn = new JButton("+ 新建");
        addBtn.addActionListener(e -> newConnection());

        JButton delBtn = new JButton("删除");
        delBtn.addActionListener(e -> deleteConnection());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.add(addBtn);
        btnPanel.add(delBtn);

        leftPanel.add(btnPanel, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(connList), BorderLayout.CENTER);
        add(leftPanel, BorderLayout.WEST);

        // ── Form panel (scrollable) ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("连接信息"));
        JScrollPane formScroll = new JScrollPane(formPanel);
        formScroll.setBorder(null);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);
        int row = -1;

        // ── 数据库类型 ──
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("数据库类型:"), c);
        c.gridx = 1;
        dbTypeCombo = new JComboBox<>(new String[]{"oceanbase (OceanBase 原生驱动)", "postgresql (PG 兼容驱动)", "oracle"});
        dbTypeCombo.addActionListener(e -> {
            if (connList.getSelectedValue() == null) {
                portField.setText(dbTypeCombo.getSelectedIndex() == 2 ? "1521" :
                    dbTypeCombo.getSelectedIndex() == 1 ? "2883" : "2881");
            }
        });
        formPanel.add(dbTypeCombo, c);

        // ── 使用 JDBC URL ──
        row++;
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        useUrlCheck = new JCheckBox("使用 JDBC URL");
        useUrlCheck.addActionListener(e -> toggleUrlMode());
        formPanel.add(useUrlCheck, c);

        // ── JDBC URL (required in URL mode) ──
        row++;
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0;
        urlLabel = new JLabel("<html>JDBC URL: " + REQ + "</html>");
        formPanel.add(urlLabel, c);
        c.gridx = 1; c.weightx = 1;
        urlField = new JTextArea(3, 1);
        urlField.setLineWrap(true);
        urlField.setWrapStyleWord(true);
        urlField.setFont(UIManager.getFont("TextField.font"));
        urlField.setMargin(new Insets(2, 4, 2, 4));
        urlField.setToolTipText("例如: jdbc:oracle:thin:@host:1521/service");
        urlField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onUrlChange(); }
            @Override public void removeUpdate(DocumentEvent e) { onUrlChange(); }
            @Override public void changedUpdate(DocumentEvent e) { onUrlChange(); }
        });
        urlScroll = new JScrollPane(urlField);
        formPanel.add(urlScroll, c);

        // ── 主机 (required) ──
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        hostLabel = new JLabel("<html>主机: " + REQ + "</html>");
        formPanel.add(hostLabel, c);
        c.gridx = 1; c.weightx = 1;
        hostField = new JTextField("127.0.0.1");
        hostField.setToolTipText("数据库服务器地址");
        formPanel.add(hostField, c);

        // ── 端口 ──
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        portLabel = new JLabel("端口:");
        formPanel.add(portLabel, c);
        c.gridx = 1; c.weightx = 1;
        portField = new JTextField("2881");
        portField.setToolTipText("数据库监听端口");
        formPanel.add(portField, c);

        // ── 服务名/数据库 ──
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        serviceLabel = new JLabel("服务名/数据库:");
        formPanel.add(serviceLabel, c);
        c.gridx = 1; c.weightx = 1;
        serviceField = new JTextField("oceanbase");
        serviceField.setToolTipText("SID 或服务名");
        formPanel.add(serviceField, c);

        // ── 用户名 (required) ──
        row++;
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0;
        formPanel.add(new JLabel("<html>用户名: " + REQ + "</html>"), c);
        c.gridx = 1; c.weightx = 1;
        userField = new JTextField();
        formPanel.add(userField, c);

        // ── 密码 ──
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("密码:"), c);
        c.gridx = 1; c.weightx = 1;
        passwordField = new JPasswordField();
        formPanel.add(passwordField, c);

        // ── Schema ──
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("Schema:"), c);
        c.gridx = 1; c.weightx = 1;
        schemaField = new JTextField();
        formPanel.add(schemaField, c);

        // ── 查询超时(秒) ──
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("查询超时(秒):"), c);
        c.gridx = 1; c.weightx = 1;
        timeoutField = new JTextField("0");
        timeoutField.setToolTipText("SQL/存过执行超时秒数，0 表示不限时");
        formPanel.add(timeoutField, c);

        toggleUrlMode();

        // blank filler row to push everything to top
        row++;
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 0; c.weighty = 1;
        formPanel.add(Box.createGlue(), c);
        c.fill = GridBagConstraints.HORIZONTAL;

        // ── 右侧：名称 + 标签面板 + 按钮 ──
        JPanel rightPanel = new JPanel(new BorderLayout());

        // 顶部：连接名称（始终可见）
        JPanel namePanel = new JPanel(new BorderLayout(6, 0));
        namePanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        namePanel.add(new JLabel("<html>连接名称: " + REQ + "</html>"), BorderLayout.WEST);
        nameField = new JTextField(20);
        namePanel.add(nameField, BorderLayout.CENTER);
        rightPanel.add(namePanel, BorderLayout.NORTH);

        // 中间：标签面板
        JTabbedPane rightTabs = new JTabbedPane();
        rightTabs.addTab("连接信息", formScroll);
        rightTabs.addTab("参数", createParamsPanel());
        rightTabs.addTab("自定义驱动", createDriverPanel());
        rightPanel.add(rightTabs, BorderLayout.CENTER);

        // 底部：操作按钮
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton testBtn = new JButton("测试连接");
        testBtn.addActionListener(e -> testConnection());
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> saveConnection());
        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dispose());
        actionPanel.add(testBtn);
        actionPanel.add(saveBtn);
        actionPanel.add(closeBtn);
        rightPanel.add(actionPanel, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.CENTER);

        loadConnections();
        if (selectConnName != null && !selectConnName.isEmpty()) {
            for (int i = 0; i < listModel.size(); i++) {
                if (selectConnName.equals(listModel.getElementAt(i).getName())) {
                    connList.setSelectedIndex(i);
                    break;
                }
            }
        }
        applyTheme();
    }

    private void onUrlChange() {
        if (parsingUrl || !useUrlCheck.isSelected()) return;
        String url = urlField.getText();
        if (url == null || url.isBlank()) return;
        parsingUrl = true;
        try {
            parseJdbcUrl(url.trim());
        } finally {
            parsingUrl = false;
        }
    }

    private void parseJdbcUrl(String url) {
        String lower = url.toLowerCase();
        try {
            if (lower.startsWith("jdbc:oracle:thin:@")) {
                String rest = url.substring("jdbc:oracle:thin:@".length());
                String[] path = rest.split("/");
                if (path.length >= 1) {
                    String[] hp = path[0].split(":");
                    if (hp.length >= 1 && !hp[0].isEmpty()) hostField.setText(hp[0]);
                    if (hp.length >= 2 && !hp[1].isEmpty()) portField.setText(hp[1]);
                }
                if (path.length >= 2 && !path[1].isEmpty()) {
                    serviceField.setText(path[1].contains("?") ? path[1].substring(0, path[1].indexOf('?')) : path[1]);
                }
                return;
            }
            if (lower.startsWith("jdbc:postgresql://")) {
                parseHostPortDb(url, "jdbc:postgresql://".length());
                return;
            }
            if (lower.startsWith("jdbc:oceanbase:oracle://")) {
                parseHostPortDb(url, "jdbc:oceanbase:oracle://".length());
                return;
            }
            if (lower.startsWith("jdbc:oceanbase://")) {
                parseHostPortDb(url, "jdbc:oceanbase://".length());
                return;
            }
            if (lower.startsWith("jdbc:mysql://")) {
                parseHostPortDb(url, "jdbc:mysql://".length());
                return;
            }
            if (lower.startsWith("jdbc:mariadb://")) {
                parseHostPortDb(url, "jdbc:mariadb://".length());
                return;
            }
            if (lower.startsWith("jdbc:sqlserver://")) {
                String rest = url.substring("jdbc:sqlserver://".length());
                String[] params = rest.split(";");
                for (String p : params) {
                    String[] kv = p.split("=", 2);
                    if (kv.length == 2) {
                        if ("serverName".equalsIgnoreCase(kv[0])) hostField.setText(kv[1]);
                        if ("portNumber".equalsIgnoreCase(kv[0])) portField.setText(kv[1]);
                        if ("databaseName".equalsIgnoreCase(kv[0])) serviceField.setText(kv[1]);
                    }
                }
            }
        } catch (Exception ignored) {
            // silent parse failure
        }
    }

    private void parseHostPortDb(String url, int prefixLen) {
        String rest = url.substring(prefixLen);
        String[] path = rest.split("/");
        if (path.length >= 1) {
            String[] hp = path[0].split(":");
            if (hp.length >= 1 && !hp[0].isEmpty()) hostField.setText(hp[0]);
            if (hp.length >= 2 && !hp[1].isEmpty()) portField.setText(hp[1]);
        }
        if (path.length >= 2 && !path[1].isEmpty()) {
            String db = path[1].contains("?") ? path[1].substring(0, path[1].indexOf('?')) : path[1];
            serviceField.setText(db);
        }
    }

    private void applyTheme() {
        ThemeManager tm = ThemeManager.getInstance();
        getContentPane().setBackground(tm.resolve("bg.main"));
        for (Component c : getContentPane().getComponents()) {
            if (c instanceof JPanel) {
                c.setBackground(tm.resolve("bg.main"));
            }
        }
    }

    private void toggleUrlMode() {
        boolean useUrl = useUrlCheck.isSelected();
        urlLabel.setVisible(useUrl);
        urlScroll.setVisible(useUrl);
        hostLabel.setVisible(!useUrl);
        hostField.setVisible(!useUrl);
        portLabel.setVisible(!useUrl);
        portField.setVisible(!useUrl);
        serviceLabel.setVisible(!useUrl);
        serviceField.setVisible(!useUrl);
    }

    // ── 参数标签页 ──

    private JPanel createParamsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel hint = new JLabel("追加到 JDBC URL 的参数（键值对）");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(hint, BorderLayout.NORTH);

        paramsTableModel = new DefaultTableModel(new String[]{"参数名", "参数值"}, 0);
        paramsTable = new JTable(paramsTableModel);
        paramsTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        paramsTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        panel.add(new JScrollPane(paramsTable), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addParamBtn = new JButton("+");
        addParamBtn.setToolTipText("添加参数");
        addParamBtn.addActionListener(e -> paramsTableModel.addRow(new Object[]{"", ""}));
        JButton rmParamBtn = new JButton("-");
        rmParamBtn.setToolTipText("删除选中参数");
        rmParamBtn.addActionListener(e -> {
            int row = paramsTable.getSelectedRow();
            if (row >= 0) paramsTableModel.removeRow(row);
        });
        btnPanel.add(addParamBtn);
        btnPanel.add(rmParamBtn);
        JButton presetBtn = new JButton("预设");
        presetBtn.setToolTipText("插入当前数据库类型的常用参数");
        presetBtn.addActionListener(e -> insertPresetParams());
        btnPanel.add(presetBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    /** 从参数表收集所有参数到 Map。 */
    private Map<String, String> collectParams() {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < paramsTableModel.getRowCount(); i++) {
            Object n = paramsTableModel.getValueAt(i, 0);
            Object v = paramsTableModel.getValueAt(i, 1);
            if (n != null && !n.toString().trim().isEmpty()) {
                map.put(n.toString().trim(), v != null ? v.toString().trim() : "");
            }
        }
        return map;
    }

    /** 用 ConnectionInfo 的参数填充参数表。 */
    private void loadParamsFrom(ConnectionInfo ci) {
        paramsTableModel.setRowCount(0);
        if (ci.getJdbcParams() != null) {
            for (Map.Entry<String, String> e : ci.getJdbcParams().entrySet()) {
                paramsTableModel.addRow(new Object[]{e.getKey(), e.getValue()});
            }
        }
    }

    /** 根据当前数据库类型插入常用 JDBC URL 参数。 */
    private void insertPresetParams() {
        String dbType;
        int idx = dbTypeCombo.getSelectedIndex();
        if (idx == 2) dbType = "oracle";
        else if (idx == 1) dbType = "postgresql";
        else dbType = "oceanbase";
        Map<String, String> presets = new LinkedHashMap<>();
        switch (dbType) {
            case "oceanbase" -> {
                presets.put("compatibleOjdbcVersion", "8");
                presets.put("useServerPrepStmts", "true");
                presets.put("characterEncoding", "UTF-8");
                presets.put("useCompression", "true");
                presets.put("rewriteBatchedStatements", "true");
                presets.put("useLocalSessionState", "true");
                presets.put("maintainTimeStats", "false");
            }
            case "postgresql" -> {
                presets.put("ApplicationName", "KylinSQL");
                presets.put("stringtype", "unspecified");
                presets.put("prepareThreshold", "5");
                presets.put("ssl", "false");
                presets.put("sslmode", "prefer");
                presets.put("reWriteBatchedInserts", "true");
            }
            default -> { // oracle
                presets.put("oracle.jdbc.defaultNChar", "true");
                presets.put("oracle.jdbc.J2EE13Compliant", "true");
                presets.put("defaultLongFetchSize", "1000");
                presets.put("oracle.jdbc.ReadTimeout", "30000");
                presets.put("oracle.net.CONNECT_TIMEOUT", "10000");
            }
        }
        for (Map.Entry<String, String> e : presets.entrySet()) {
            // 跳过已有同名参数
            boolean exists = false;
            for (int i = 0; i < paramsTableModel.getRowCount(); i++) {
                Object n = paramsTableModel.getValueAt(i, 0);
                if (n != null && e.getKey().equals(n.toString().trim())) {
                    exists = true; break;
                }
            }
            if (!exists) paramsTableModel.addRow(new Object[]{e.getKey(), e.getValue()});
        }
    }

    // ── 自定义驱动标签页 ──

    private JPanel createDriverPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel hint = new JLabel("自定义 JDBC 驱动（选择驱动包后自动识别驱动类）");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(hint, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(4, 8, 4, 8);

        // Row 0: JAR path
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
        form.add(new JLabel("驱动包路径:"), gc);
        gc.gridx = 1; gc.weightx = 1;
        customJarField = new JTextField();
        form.add(customJarField, gc);
        gc.gridx = 2; gc.weightx = 0;
        JButton browseBtn = new JButton("浏览");
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JAR 文件 (*.jar)", "jar"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                customJarField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        form.add(browseBtn, gc);
        JButton scanBtn = new JButton("扫描");
        scanBtn.addActionListener(e -> scanDriverJar());
        form.add(scanBtn, gc);

        // Row 1: Driver class
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        form.add(new JLabel("驱动类名:"), gc);
        gc.gridx = 1; gc.gridwidth = 2; gc.weightx = 1;
        customDriverCombo = new JComboBox<>();
        customDriverCombo.setEditable(true);
        form.add(customDriverCombo, gc);

        panel.add(form, BorderLayout.NORTH);
        return panel;
    }

    /** 扫描 JAR 包中的 JDBC 驱动类，识别后填入下拉框。 */
    private void scanDriverJar() {
        String jarPath = customJarField.getText().trim();
        if (jarPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先选择驱动包路径");
            return;
        }
        File jarFile = new File(jarPath);
        if (!jarFile.exists() || !jarFile.isFile()) {
            JOptionPane.showMessageDialog(this, "文件不存在: " + jarPath);
            return;
        }
        customDriverCombo.removeAllItems();
        try (java.net.URLClassLoader cl = new java.net.URLClassLoader(
                new java.net.URL[]{jarFile.toURI().toURL()},
                ClassLoader.getSystemClassLoader())) {
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.endsWith(".class")) {
                        String className = name.replace('/', '.').replace(".class", "");
                        try {
                            Class<?> cls = cl.loadClass(className);
                            if (java.sql.Driver.class.isAssignableFrom(cls) && !cls.isInterface()) {
                                customDriverCombo.addItem(className);
                            }
                        } catch (Exception | NoClassDefFoundError ignored) {}
                    }
                }
            }
            if (customDriverCombo.getItemCount() == 0) {
                JOptionPane.showMessageDialog(this, "未在 JAR 包中找到 JDBC 驱动类");
            } else {
                customDriverCombo.setSelectedIndex(0);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "扫描失败: " + e.getMessage());
        }
    }

    private void loadConnections() {
        listModel.clear();
        for (ConnectionInfo ci : configManager.loadConnections()) {
            listModel.addElement(ci);
        }
    }

    private void loadSelected() {
        ConnectionInfo ci = connList.getSelectedValue();
        if (ci == null) return;
        editing = ci;
        nameField.setText(ci.getName());
        useUrlCheck.setSelected(ci.isUseUrl());
        hostField.setText(ci.getHost());
        portField.setText(String.valueOf(ci.getPort()));
        serviceField.setText(ci.getServiceName());
        // set urlField last so parseJdbcUrl fills host/port/service from URL (overriding empty saved values)
        urlField.setText(ci.getRawJdbcUrl());
        userField.setText(ci.getUsername());
        passwordField.setText(ci.getPassword());
        schemaField.setText(ci.getSchema());
        timeoutField.setText(String.valueOf(ci.getQueryTimeout()));
        dbTypeCombo.setSelectedIndex("oracle".equals(ci.getDbType()) ? 2 : "postgresql".equals(ci.getDbType()) ? 1 : 0);
        loadParamsFrom(ci);
        customJarField.setText(ci.getCustomDriverJar());
        customDriverCombo.removeAllItems();
        if (ci.getCustomDriverClass() != null) customDriverCombo.addItem(ci.getCustomDriverClass());
        toggleUrlMode();
    }

    private void newConnection() {
        editing = null;
        nameField.setText("");
        useUrlCheck.setSelected(false);
        urlField.setText("");
        hostField.setText("127.0.0.1");
        portField.setText("2881");
        serviceField.setText("oceanbase");
        userField.setText("");
        passwordField.setText("");
        schemaField.setText("");
        timeoutField.setText("0");
        dbTypeCombo.setSelectedIndex(0);
        paramsTableModel.setRowCount(0);
        insertPresetParams();
        customJarField.setText("");
        customDriverCombo.removeAllItems();
        connList.clearSelection();
        toggleUrlMode();
    }

    private void saveConnection() {
        String name = nameField.getText().trim();
        String username = userField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入连接名称");
            nameField.requestFocus();
            return;
        }
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入用户名");
            userField.requestFocus();
            return;
        }
        if (useUrlCheck.isSelected() && urlField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入 JDBC URL");
            urlField.requestFocus();
            return;
        }
        if (!useUrlCheck.isSelected() && hostField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入主机地址");
            hostField.requestFocus();
            return;
        }

        ConnectionInfo ci = editing != null ? editing : new ConnectionInfo();
        ci.setName(name);
        ci.setUseUrl(useUrlCheck.isSelected());
        ci.setDbType(dbTypeCombo.getSelectedIndex() == 2 ? "oracle" : dbTypeCombo.getSelectedIndex() == 1 ? "postgresql" : "oceanbase");
        if (useUrlCheck.isSelected()) {
            ci.setRawJdbcUrl(urlField.getText().trim());
            ci.setHost("");
            ci.setPort(0);
            ci.setServiceName("");
        } else {
            ci.setRawJdbcUrl(null);
            ci.setHost(hostField.getText().trim());
            ci.setPort(Integer.parseInt(portField.getText().trim()));
            ci.setServiceName(serviceField.getText().trim());
        }
        ci.setUsername(username);
        ci.setPassword(new String(passwordField.getPassword()));
        ci.setJdbcParams(collectParams());
        ci.setCustomDriverJar(customJarField.getText().trim());
        Object sel = customDriverCombo.getSelectedItem();
        ci.setCustomDriverClass(sel != null ? sel.toString().trim() : "");
        ci.setSchema(schemaField.getText().trim());
        try {
            ci.setQueryTimeout(Integer.parseInt(timeoutField.getText().trim()));
        } catch (NumberFormatException ignored) {}

        List<ConnectionInfo> all = configManager.loadConnections();
        all.removeIf(c -> c.getName().equalsIgnoreCase(ci.getName()));
        if (editing == null) {
            all.add(ci);
        } else {
            int idx = all.indexOf(editing);
            if (idx >= 0) all.set(idx, ci);
            else all.add(ci);
        }
        configManager.saveConnections(all);
        loadConnections();
        savedConnName = ci.getName();
        JOptionPane.showMessageDialog(this, "连接已保存");
        editing = null;
    }

    private void deleteConnection() {
        ConnectionInfo ci = connList.getSelectedValue();
        if (ci == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            "确认删除连接 '" + ci.getName() + "'?",
            "确认", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        List<ConnectionInfo> all = configManager.loadConnections();
        all.remove(ci);
        configManager.saveConnections(all);
        loadConnections();
        newConnection();
    }

    private void testConnection() {
        ConnectionInfo ci = new ConnectionInfo();
        ci.setName("__test__");
        ci.setUseUrl(useUrlCheck.isSelected());
        ci.setDbType(dbTypeCombo.getSelectedIndex() == 2 ? "oracle" : dbTypeCombo.getSelectedIndex() == 1 ? "postgresql" : "oceanbase");
        if (useUrlCheck.isSelected()) {
            ci.setRawJdbcUrl(urlField.getText().trim());
            ci.setHost("");
            ci.setPort(0);
            ci.setServiceName("");
        } else {
            ci.setHost(hostField.getText().trim());
            ci.setPort(Integer.parseInt(portField.getText().trim()));
            ci.setServiceName(serviceField.getText().trim());
        }
        ci.setUsername(userField.getText().trim());
        ci.setPassword(new String(passwordField.getPassword()));

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                return connectionManager.testConnection(ci);
            }
            @Override protected void done() {
                if (!ConnectionDialog.this.isDisplayable()) return;
                try {
                    boolean ok = get();
                    if (ok) {
                        JOptionPane.showMessageDialog(ConnectionDialog.this, "连接成功！");
                    } else {
                        JOptionPane.showMessageDialog(ConnectionDialog.this, "连接失败，请检查参数");
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ConnectionDialog.this, "连接失败: " + e.getMessage());
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }

    public String getSavedConnName() { return savedConnName; }
}
