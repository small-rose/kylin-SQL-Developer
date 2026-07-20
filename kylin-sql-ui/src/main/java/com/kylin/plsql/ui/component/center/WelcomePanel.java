package com.kylin.plsql.ui.component.center;

import com.kylin.plsql.core.config.ThemeManager;
import com.kylin.plsql.core.config.FontManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/** Welcome screen with keyboard shortcuts, usage tips, new/open buttons. */
public class WelcomePanel extends JPanel {
    private static final Font TITLE_FONT = UIManager.getFont("TitledBorder.font").deriveFont(Font.PLAIN);
    private final ThemeManager theme = ThemeManager.getInstance();
    private final JPanel shortcutsPanel = new JPanel(new GridBagLayout());
    private final JPanel tipsPanel = new JPanel(new GridBagLayout());

    public WelcomePanel(Runnable onNewFile, Runnable onOpenFile) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        JLabel title = new JLabel("Kylin SQL Developer");
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 18f));
        title.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        topPanel.add(title, BorderLayout.NORTH);
        topPanel.add(new JSeparator(JSeparator.HORIZONTAL), BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        String[][] shortcuts = {
            {"Ctrl + N", "新建 SQL 文件"},
            {"Ctrl + O", "打开 SQL 文件"},
            {"Ctrl + S", "保存"},
            {"Ctrl + Shift + S", "另存为"},
            {"Ctrl + W", "关闭当前标签"},
            {"F8", "执行 SQL / 选中代码"},
            {"Ctrl + Shift + F", "格式化 SQL"},
            {"Ctrl + E", "执行计划"},
            {"Ctrl + Alt + H", "调用层级"},
            {"Ctrl + F", "查找"},
            {"Ctrl + H", "替换"},
        };

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(1, 0, 1, 20);
        gc.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < shortcuts.length; i++) {
            gc.gridx = 0; gc.gridy = i;
            JLabel key = new JLabel(shortcuts[i][0]);
            key.setFont(FontManager.getInstance().resolve("font.editor"));
            key.setForeground(theme.resolve("accent.green"));
            shortcutsPanel.add(key, gc);
            gc.gridx = 1;
            shortcutsPanel.add(new JLabel(shortcuts[i][1]), gc);
        }
        shortcutsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "快捷键",
                TitledBorder.LEFT, TitledBorder.TOP, TITLE_FONT));

        String[] tips = {
            "●  左侧对象浏览器右键生成 SELECT/INSERT/UPDATE/DELETE/DDL",
            "●  连接/Schema 下拉在每个编辑器标签页顶部工具栏",
            "●  光标定位到 SQL 语句上按 F8 自动识别当前 SQL 块",
            "●  光标移动时自动高亮当前 SQL 语句范围",
            "●  右侧大纲面板显示包/过程/函数，单击跳转定义",
            "●  右键 “数据预览” 快速查看表前 100 行",
            "●  结果集工具栏支持 CSV 导出 / 复制",
            "●  连接可设置 SQL 执行超时时间 (0=不限)",
        };

        GridBagConstraints tc = new GridBagConstraints();
        tc.gridx = 0; tc.anchor = GridBagConstraints.WEST; tc.insets = new Insets(1, 0, 1, 0);
        for (int i = 0; i < tips.length; i++) {
            tc.gridy = i;
            tipsPanel.add(new JLabel(tips[i]), tc);
        }
        tipsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "使用技巧",
                TitledBorder.LEFT, TitledBorder.TOP, TITLE_FONT));

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        centerPanel.add(shortcutsPanel);
        centerPanel.add(tipsPanel);
        add(centerPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        btnPanel.setOpaque(false);
        JButton newBtn = new JButton("新建 SQL 文件");
        newBtn.addActionListener(e -> onNewFile.run());
        JButton openBtn = new JButton("打开 SQL 文件");
        openBtn.addActionListener(e -> onOpenFile.run());
        btnPanel.add(newBtn);
        btnPanel.add(openBtn);
        add(btnPanel, BorderLayout.SOUTH);

        applyTheme();
    }

    public void applyTheme() {
        Color bg = theme.resolve("bg.main");
        setBackground(bg);
        shortcutsPanel.setBackground(bg);
        tipsPanel.setBackground(bg);
        repaint();
    }
}
