package com.kylin.plsql.ui.dialog.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class LogViewerDialog extends JDialog {
    private static final File LOG_FILE = new File(
            System.getProperty("user.home") + "/.kylin-sql/logs/kylin-sql.log");

    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" ");
    private final ThemeManager theme = ThemeManager.getInstance();
    private final JCheckBox followTail = new JCheckBox("跟踪尾部");
    private final JCheckBox debugToggle = new JCheckBox("调试日志");
    private final JTextField filterField = new JTextField();
    private String filterText = "";
    private final ConfigManager config = ConfigManager.getInstance();

    public LogViewerDialog(Frame owner) {
        super(owner, "应用日志", false);
        setSize(800, 500);
        setLocationRelativeTo(owner);
        initUI();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { reloadLog(); return null; }
            @Override protected void done() { logArea.setCaretPosition(logArea.getDocument().getLength()); }
        }.execute();
    }

    private void initUI() {
        logArea.setEditable(false);
        logArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        filterField.setToolTipText("过滤关键字 (输入后自动筛选)");
        filterField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) {
                filterText = filterField.getText().trim().toLowerCase();
                reloadLog();
            }
        });

        followTail.setSelected(true);
        // 调试日志开关：从持久化配置读取初始状态
        boolean debugOn = "true".equals(config.getPreference("debugLogEnabled", "false"));
        debugToggle.setSelected(debugOn);
        applyDebugLevel(debugOn);
        debugToggle.addActionListener(e -> {
            boolean on = debugToggle.isSelected();
            config.setPreference("debugLogEnabled", String.valueOf(on));
            applyDebugLevel(on);
        });

        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> reloadLog());
        JButton clearBtn = new JButton("清空显示");
        clearBtn.addActionListener(e -> {
            logArea.setText("");
            statusLabel.setText("已清空显示 (日志文件未受影响)");
        });

        JScrollPane scrollPane = new JScrollPane(logArea);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(new JLabel("日志路径: " + LOG_FILE.getAbsolutePath()));
        toolbar.addSeparator();
        toolbar.add(new JLabel(" 过滤:"));
        toolbar.add(filterField);
        toolbar.addSeparator();
        toolbar.add(refreshBtn);
        toolbar.add(clearBtn);
        toolbar.addSeparator();
        toolbar.add(followTail);
        toolbar.addSeparator();
        toolbar.add(debugToggle);

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        applyTheme();
        ThemeManager.getInstance().addListener(this::applyTheme);
    }

    private void reloadLog() {
        if (!LOG_FILE.exists()) {
            logArea.setText("日志文件不存在: " + LOG_FILE.getAbsolutePath());
            statusLabel.setText("文件不存在");
            return;
        }

        long maxBytes = 500 * 1024;
        try (RandomAccessFile raf = new RandomAccessFile(LOG_FILE, "r")) {
            long fileLen = raf.length();
            long startPos = Math.max(0, fileLen - maxBytes);

            raf.seek(startPos);
            if (startPos > 0) {
                int c;
                while ((c = raf.read()) != -1 && c != '\n') {
                    // skip the first partial line
                }
            }

            byte[] buf = new byte[(int) (raf.length() - raf.getFilePointer())];
            raf.readFully(buf);
            String content = new String(buf, StandardCharsets.UTF_8);
            String text = filterText.isEmpty() ? content
                    : filterLines(content, filterText);
            logArea.setText(text);
            statusLabel.setText(String.format("文件大小: %.1f KB / 显示: %d 行",
                    fileLen / 1024.0, logArea.getLineCount()));

            if (followTail.isSelected()) {
                SwingUtilities.invokeLater(() -> {
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            } else {
                logArea.setCaretPosition(0);
            }
        } catch (Exception e) {
            logArea.setText("读取日志失败: " + e.getMessage());
            statusLabel.setText("读取错误");
        }
    }

    private static String filterLines(String content, String filter) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            if (line.toLowerCase().contains(filter)) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /** 运行时切换 root logger 级别（DEBUG / INFO），借助 Logback API 实时生效。 */
    private static void applyDebugLevel(boolean debug) {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(debug ? Level.DEBUG : Level.INFO);
    }

    private void applyTheme() {
        Color bg = theme.resolve("bg.main");
        Color editorBg = theme.resolve("bg.editor");
        Color fg = theme.resolve("fg.main");
        if (bg != null) getContentPane().setBackground(bg);
        if (editorBg != null) logArea.setBackground(editorBg);
        if (fg != null) {
            logArea.setForeground(fg);
            statusLabel.setForeground(fg);
        }
    }
}
