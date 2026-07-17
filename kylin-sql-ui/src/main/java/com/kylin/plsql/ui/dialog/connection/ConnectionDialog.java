package com.kylin.plsql.ui.dialog.connection;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;

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
        int row = 0;

        // ── 连接名称 (required) ──
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0;
        formPanel.add(new JLabel("<html>连接名称: " + REQ + "</html>"), c);
        c.gridx = 1; c.weightx = 1;
        nameField = new JTextField(20);
        formPanel.add(nameField, c);

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

        // ── 操作按钮 ──
        row++;
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

        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weighty = 0;
        formPanel.add(actionPanel, c);

        // blank filler row to push everything to top
        row++;
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 0; c.weighty = 1;
        formPanel.add(Box.createGlue(), c);
        c.fill = GridBagConstraints.HORIZONTAL;

        add(formScroll, BorderLayout.CENTER);

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
