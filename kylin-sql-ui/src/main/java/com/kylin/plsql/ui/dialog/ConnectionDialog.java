package com.kylin.plsql.ui.dialog;

import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.ConnectionManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

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

    public ConnectionDialog(Frame owner, ConfigManager configManager, ConnectionManager connectionManager) {
        this(owner, configManager, connectionManager, null);
    }

    public ConnectionDialog(Frame owner, ConfigManager configManager, ConnectionManager connectionManager, String selectConnName) {
        super(owner, "\u7BA1\u7406\u8FDE\u63A5", true);
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
        leftPanel.setBorder(BorderFactory.createTitledBorder("\u5DF2\u4FDD\u5B58\u7684\u8FDE\u63A5"));
        leftPanel.setPreferredSize(new Dimension(220, 0));

        JButton addBtn = new JButton("+ \u65B0\u5EFA");
        addBtn.addActionListener(e -> newConnection());

        JButton delBtn = new JButton("\u5220\u9664");
        delBtn.addActionListener(e -> deleteConnection());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.add(addBtn);
        btnPanel.add(delBtn);

        leftPanel.add(btnPanel, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(connList), BorderLayout.CENTER);
        add(leftPanel, BorderLayout.WEST);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("\u8FDE\u63A5\u4FE1\u606F"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 5, 3, 5);
        int row = 0;

        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0;
        formPanel.add(new JLabel("\u8FDE\u63A5\u540D\u79F0:"), c);
        c.gridx = 1; c.weightx = 1;
        nameField = new JTextField(20);
        formPanel.add(nameField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        dbTypeLabel = new JLabel("\u6570\u636E\u5E93\u7C7B\u578B:");
        formPanel.add(dbTypeLabel, c);
        c.gridx = 1;
        dbTypeCombo = new JComboBox<>(new String[]{"oceanbase (OceanBase \u539F\u751F\u9A71\u52A8)", "postgresql (PG \u517C\u5BB9\u9A71\u52A8)", "oracle"});
        dbTypeCombo.addActionListener(e -> {
            if (connList.getSelectedValue() == null) {
                portField.setText(dbTypeCombo.getSelectedIndex() == 2 ? "1521" :
                    dbTypeCombo.getSelectedIndex() == 1 ? "2883" : "2881");
            }
        });
        formPanel.add(dbTypeCombo, c);

        row++;
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        useUrlCheck = new JCheckBox("\u4F7F\u7528 JDBC URL");
        useUrlCheck.addActionListener(e -> toggleUrlMode());
        formPanel.add(useUrlCheck, c);

        row++;
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0;
        formPanel.add(new JLabel("JDBC URL:"), c);
        c.gridx = 1; c.weightx = 1;
        urlField = new JTextField();
        urlField.setToolTipText("\u4F8B\u5982: jdbc:oracle:thin:@host:1521/service");
        formPanel.add(urlField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        hostLabel = new JLabel("\u4E3B\u673A:");
        formPanel.add(hostLabel, c);
        c.gridx = 1; c.weightx = 1;
        hostField = new JTextField("127.0.0.1");
        formPanel.add(hostField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        portLabel = new JLabel("\u7AEF\u53E3:");
        formPanel.add(portLabel, c);
        c.gridx = 1; c.weightx = 1;
        portField = new JTextField("2881");
        formPanel.add(portField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        serviceLabel = new JLabel("\u670D\u52A1\u540D/\u6570\u636E\u5E93:");
        formPanel.add(serviceLabel, c);
        c.gridx = 1; c.weightx = 1;
        serviceField = new JTextField("oceanbase");
        formPanel.add(serviceField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("\u7528\u6237\u540D:"), c);
        c.gridx = 1; c.weightx = 1;
        userField = new JTextField();
        formPanel.add(userField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        formPanel.add(new JLabel("\u5BC6\u7801:"), c);
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
        formPanel.add(new JLabel("\u67E5\u8BE2\u8D85\u65F6(\u79D2):"), c);
        c.gridx = 1; c.weightx = 1;
        timeoutField = new JTextField("0");
        timeoutField.setToolTipText("SQL/\u5B58\u8FC7\u6267\u884C\u8D85\u65F6\u79D2\u6570\uFF0C0 \u8868\u793A\u4E0D\u9650\u65F6");
        formPanel.add(timeoutField, c);

        toggleUrlMode();

        row++;
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton testBtn = new JButton("\u6D4B\u8BD5\u8FDE\u63A5");
        testBtn.addActionListener(e -> testConnection());
        JButton saveBtn = new JButton("\u4FDD\u5B58");
        saveBtn.addActionListener(e -> saveConnection());
        JButton closeBtn = new JButton("\u5173\u95ED");
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
        JOptionPane.showMessageDialog(this, "\u8FDE\u63A5\u5DF2\u4FDD\u5B58");
        editing = null;
    }

    private void deleteConnection() {
        ConnectionInfo ci = connList.getSelectedValue();
        if (ci == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            "\u786E\u8BA4\u5220\u9664\u8FDE\u63A5 '" + ci.getName() + "'?",
            "\u786E\u8BA4", JOptionPane.YES_NO_OPTION);
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
                JOptionPane.showMessageDialog(this, "\u8FDE\u63A5\u6210\u529F\uFF01");
            } else {
                JOptionPane.showMessageDialog(this, "\u8FDE\u63A5\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u53C2\u6570");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "\u8FDE\u63A5\u5931\u8D25: " + e.getMessage());
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }
}
