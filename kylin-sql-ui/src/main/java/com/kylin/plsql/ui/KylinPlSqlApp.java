package com.kylin.plsql.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.kylin.plsql.core.config.AppTheme;
import com.kylin.plsql.core.config.ConfigManager;
import com.kylin.plsql.core.config.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

public class KylinPlSqlApp {
    private static final Logger log = LoggerFactory.getLogger(KylinPlSqlApp.class);

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarculaLaf());
            UIManager.put("TabbedPane.tabType", "underline");
            UIManager.put("TabbedPane.showTabSeparators", false);
            UIManager.put("TabbedPane.tabHeight", 28);
            UIManager.put("TabbedPane.tabInsets", new Insets(2,5,2,5));
            UIManager.put("TabbedPane.contentAreaInsets", new Insets(0,0,0,0));
            UIManager.put("TabbedPane.tabAreaInsets", new Insets(2,0,0,0));
            UIManager.put("Table.rowHeight", 22);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Tree.rowHeight", 20);
            UIManager.put("ScrollBar.showButtons", false);
        } catch (Exception e) {
            log.error("初始化 FlatLaf 失败", e);
        }

        ConfigManager config = new ConfigManager();
        log.info("配置目录: {}", config.getConfigPath().toAbsolutePath());

        ThemeManager themeMgr = ThemeManager.getInstance();
        themeMgr.loadFromConfig(config);
        AppTheme saved = themeMgr.getCurrentTheme();
        if ("LIGHT".equals(saved.config("flatlaf"))) {
            try { UIManager.setLookAndFeel(new FlatLightLaf()); }
            catch (Exception e) { log.warn("FlatLightLaf init failed", e); }
        } else {
            try { UIManager.setLookAndFeel(new FlatDarculaLaf()); }
            catch (Exception e) { log.warn("FlatDarculaLaf init failed", e); }
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(config);
            frame.setVisible(true);
        });
    }
}
