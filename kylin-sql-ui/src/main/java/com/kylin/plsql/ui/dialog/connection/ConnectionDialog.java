package com.kylin.plsql.ui.dialog.connection;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Database connection management dialog: create, edit, test, save, delete connections */
public class ConnectionDialog extends JDialog {
    private final ConfigManager configManager;
    private final ConnectionManager connectionManager;
    private final JList<ConnectionInfo> connList;
    private final DefaultListModel<ConnectionInfo> listModel;
    private JTextField nameField, hostField, portField, serviceField, userField, schemaField, urlField, timeoutField;
    private JPasswordField passwordField;
    private JComboBox<String> dbTypeCombo;
    private JCheckBox useUrlCheck;
    private JLabel hostLabel, portLabel, serviceLabel, dbTypeLabel;
    private ConnectionInfo editing;
    private String savedConnName;

    public ConnectionDialog(Frame owner, ConfigManager configManager, ConnectionManager connectionManager) {
        this(owner, configManager, connectionManager, null);
    }

    public ConnectionDialog(Frame owner, ConfigManager configManager, ConnectionManager connectionManager, String selectConnName) {
        super(owner, "管理连接", true);
        this.configManager = configManager;
        this.connectionManager = connectionManager;

        setSize(700, 500);
        setLocationRelativeTo(owner);

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

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("连接信息"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 5);
        int row = 0;

        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0;
        formPanel.add(new JLabel("连接名称:"), c);
        c.gridx = 1; c.weightx = 1;
        nameField = new JTextField(20);
        formPanel.add(nameField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        dbTypeLabel = new JLabel("数据库类型:");
        formPanel.add(dbTypeLabel, c);
        c.gridx = 1;
        dbTypeCombo = new JComboBox<>(new String[]{"oceanbase (OceanBase 原生驱动)", "postgresql (PG 兼容驱动)", "oracle"});
        dbTypeCombo.addActionListener(e -> {
            if (connList.getSelectedValue() == null) {
                portField.setText(dbTypeCombo.getSelectedIndex() == 2 ? "1521" :
                    dbTypeCombo.getSelectedIndex() == 1 ? "2883" : "2881");
            }
        });
        formPanel.add(dbTypeCombo, c);

        row++;
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        useUrlCheck = new JCheckBox("使用 JDBC URL");
        useUrlCheck.addActionListener(e -> toggleUrlMode());
        formPanel.add(useUrlCheck, c);

        row++;
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0;
        formPanel.add(new JLabel("JDBC URL:"), c);
        c.gridx = 1; c.weightx = 1;
        urlField = new JTextField();
        urlField.setToolTipText("例如: jdbc:oracle:thin:@host:1521/service");
        formPanel.add(urlField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        hostLabel = new JLabel("主机:");
        formPanel.add(hostLabel, c);
        c.gridx = 1; c.weightx = 1;
        hostField = new JTextField("127.0.0.1");
        formPanel.add(hostField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        portLabel = new JLabel("端口:");
        formPanel.add(portLabel, c);
        c.gridx = 1; c.weightx = 1;
        portField = new JTextField("2881");
        formPanel.add(portField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        serviceLabel = new JLabel("服务名/数据库:");
        formPanel.add(serviceLabel, c);
        c.gridx = 1; c.weightx = 1;
        serviceField = new JTextField("oceanbase");
        formPanel.add(serviceField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("用户名:"), c);
        c.gridx = 1; c.weightx = 1;
        userField = new JTextField();
        formPanel.add(userField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("密码:"), c);
        c.gridx = 1; c.weightx = 1;
        passwordField = new JPasswordField();
        formPanel.add(passwordField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("Schema:"), c);
        c.gridx = 1; c.weightx = 1;
        schemaField = new JTextField();
        formPanel.add(schemaField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("查询超时(秒):"), c);
        c.gridx = 1; c.weightx = 1;
        timeoutField = new JTextField("0");
        timeoutField.setToolTipText("SQL/存过执行超时秒数，0 表示不限时");
        formPanel.add(timeoutField, c);

        toggleUrlMode();

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

        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        formPanel.add(actionPanel, c);

        add(formPanel, BorderLayout.CENTER);

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
        urlField.setVisible(useUrl);
        dbTypeLabel.setVisible(!useUrl);
        dbTypeCombo.setVisible(!useUrl);
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
        urlField.setText(ci.getRawJdbcUrl());
        hostField.setText(ci.getHost());
        portField.setText(String.valueOf(ci.getPort()));
        serviceField.setText(ci.getServiceName());
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
        ConnectionInfo ci = editing != null ? editing : new ConnectionInfo();
        ci.setName(nameField.getText().trim());
        ci.setUseUrl(useUrlCheck.isSelected());
        if (useUrlCheck.isSelected()) {
            ci.setRawJdbcUrl(urlField.getText().trim());
            ci.setHost("");
            ci.setPort(0);
            ci.setServiceName("");
            ci.setDbType("");
        } else {
            ci.setRawJdbcUrl(null);
            ci.setHost(hostField.getText().trim());
            ci.setPort(Integer.parseInt(portField.getText().trim()));
            ci.setServiceName(serviceField.getText().trim());
            ci.setDbType(dbTypeCombo.getSelectedIndex() == 2 ? "oracle" : dbTypeCombo.getSelectedIndex() == 1 ? "postgresql" : "oceanbase");
        }
        ci.setUsername(userField.getText().trim());
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
        if (useUrlCheck.isSelected()) {
            ci.setRawJdbcUrl(urlField.getText().trim());
            ci.setHost("");
            ci.setPort(0);
            ci.setServiceName("");
            ci.setDbType("");
        } else {
            ci.setHost(hostField.getText().trim());
            ci.setPort(Integer.parseInt(portField.getText().trim()));
            ci.setServiceName(serviceField.getText().trim());
            ci.setDbType(dbTypeCombo.getSelectedIndex() == 2 ? "oracle" : dbTypeCombo.getSelectedIndex() == 1 ? "postgresql" : "oceanbase");
        }
        ci.setUsername(userField.getText().trim());
        ci.setPassword(new String(passwordField.getPassword()));

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            boolean ok = connectionManager.testConnection(ci);
            if (ok) {
                JOptionPane.showMessageDialog(this, "连接成功！");
            } else {
                JOptionPane.showMessageDialog(this, "连接失败，请检查参数");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "连接失败: " + e.getMessage());
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    public String getSavedConnName() { return savedConnName; }
}
