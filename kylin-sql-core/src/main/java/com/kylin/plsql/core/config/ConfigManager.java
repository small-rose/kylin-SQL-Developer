package com.kylin.plsql.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.kylin.plsql.core.db.ConnectionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Configuration manager for preferences, connections, workspace, and file records. */
public class ConfigManager {
    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_DIR = ".kylin-sql";
    private static final String CONNECTIONS_FILE = "connections.json";
    private static final String PREFERENCES_FILE = "preferences.json";
    private static final String WORKSPACE_FILE = "workspace.json";

    public static class TabState {
        public String type;       // "file", "console", or "sourceviewer"
        public String filePath;
        public String connName;
        public String schema;
        public String content;    // content for console/unsaved tabs
        public String tabName;    // console label
        public String objectName; // for sourceviewer tabs
        public String objectType; // for sourceviewer tabs
        // ── per-tab detail state ──
        public int caretPosition;
        public int scrollLine;
        public boolean autoTx;       // auto-commit mode
        public boolean modified;     // dirty flag
        public boolean showingBody;  // sourceviewer spec vs body
    }

    public static class WorkspaceState {
        public int lastActiveIndex;
        public String theme = "DARK";
        public Map<String, String> colorOverrides = new HashMap<>();
        public List<TabState> tabs = new ArrayList<>();
        public Map<String, Map<String, String>> formatProfiles = new LinkedHashMap<>();
        public String activeFormatProfile = "Oracle";
        public Map<String, String> connectionDialects = new HashMap<>();
        // ── global state ──
        public java.util.List<String> treeExpandedPaths;      // flat list of expanded tree path strings
        public Map<String, List<String>> hiddenSchemas;        // connName → hidden schema names
        public java.util.List<String> sqlHistory;              // SQL execution history
        public int currentEngineIndex;                         // selected formatting engine index
    }

    public static class SavedFileRecord {
        public String filePath;
        public String fileName;
        public String notes;
        public long lastOpened;
    }

    private static final String SAVED_FILES_FILE = "saved_files.json";
    private static final String SQL_HISTORY_FILE = "sql_history.json";

    private static ConfigManager instance;

    private final Path configPath;
    private final Gson gson;
    private Map<String, String> preferences;

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public ConfigManager() {
        instance = this;
        String userHome = System.getProperty("user.home", ".");
        configPath = Paths.get(userHome, CONFIG_DIR);
        gson = new GsonBuilder().setPrettyPrinting().create();
        preferences = new HashMap<>();
        init();
    }

    private void init() {
        try {
            Files.createDirectories(configPath);
            log.info("配置目录: {}", configPath.toAbsolutePath());
            loadPreferences();
        } catch (IOException e) {
            log.error("创建配置目录失败", e);
        }
    }

    public Path getConfigPath() {
        return configPath;
    }

    // ── 连接管理 ──

    public void saveConnections(List<ConnectionInfo> connections) {
        File file = configPath.resolve(CONNECTIONS_FILE).toFile();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(connections, writer);
            log.info("已保存 {} 个连接", connections.size());
        } catch (IOException e) {
            log.error("保存连接失败", e);
        }
    }

    public List<ConnectionInfo> loadConnections() {
        File file = configPath.resolve(CONNECTIONS_FILE).toFile();
        if (!file.exists()) return new ArrayList<>();
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<ConnectionInfo>>() {}.getType();
            List<ConnectionInfo> list = gson.fromJson(reader, type);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            log.error("加载连接失败", e);
            return new ArrayList<>();
        }
    }

    // ── 偏好设置 ──

    public void setPreference(String key, String value) {
        preferences.put(key, value);
        savePreferences();
    }

    public String getPreference(String key, String defaultValue) {
        return preferences.getOrDefault(key, defaultValue);
    }

    private void savePreferences() {
        File file = configPath.resolve(PREFERENCES_FILE).toFile();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(preferences, writer);
        } catch (IOException e) {
            log.error("保存偏好设置失败", e);
        }
    }

    public void saveWorkspace(WorkspaceState state) {
        Path target = configPath.resolve(WORKSPACE_FILE);
        Path tmp = configPath.resolve(WORKSPACE_FILE + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp.toFile()), StandardCharsets.UTF_8)) {
            gson.toJson(state, w);
            w.flush();
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException ignored) {}
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException ignored) {}
            // final fallback: write directly (antivirus file lock workaround)
            try (Writer w2 = new OutputStreamWriter(new FileOutputStream(target.toFile()), StandardCharsets.UTF_8)) {
                gson.toJson(state, w2);
            }
        } catch (IOException e) {
            log.warn("保存工作空间失败: {}", e.getMessage());
        }
        try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
    }

    public WorkspaceState loadWorkspace() {
        File file = configPath.resolve(WORKSPACE_FILE).toFile();
        if (!file.exists()) return new WorkspaceState();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            WorkspaceState s = gson.fromJson(r, WorkspaceState.class);
            return s != null ? s : new WorkspaceState();
        } catch (IOException e) {
            log.error("加载工作空间失败", e);
            return new WorkspaceState();
        } catch (JsonSyntaxException e) {
            log.warn("工作空间文件损坏: {}, 尝试 lenient 模式修复", e.getMessage());
            try (Reader r2 = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                JsonReader jr = new JsonReader(r2);
                jr.setLenient(true);
                WorkspaceState s = gson.fromJson(jr, WorkspaceState.class);
                if (s != null) return s;
            } catch (Exception ignored) {}
            log.warn("工作空间文件 {} 已损坏且无法自动修复, 请手动删除此文件后重启应用", file.getAbsolutePath());
            return new WorkspaceState();
        }
    }

    // ── 已保存文件记录 ──

    public void saveFileRecord(SavedFileRecord record) {
        List<SavedFileRecord> records = loadFileRecords();
        records.removeIf(r -> r.filePath != null && r.filePath.equals(record.filePath));
        records.add(record);
        File file = configPath.resolve(SAVED_FILES_FILE).toFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(records, w);
        } catch (IOException e) {
            log.error("保存文件记录失败", e);
        }
    }

    public void removeFileRecord(String filePath) {
        List<SavedFileRecord> records = loadFileRecords();
        records.removeIf(r -> r.filePath != null && r.filePath.equals(filePath));
        File file = configPath.resolve(SAVED_FILES_FILE).toFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(records, w);
        } catch (IOException e) {
            log.error("删除文件记录失败", e);
        }
    }

    public void updateFileNotes(String filePath, String notes) {
        List<SavedFileRecord> records = loadFileRecords();
        for (SavedFileRecord r : records) {
            if (r.filePath != null && r.filePath.equals(filePath)) {
                r.notes = notes;
                break;
            }
        }
        File file = configPath.resolve(SAVED_FILES_FILE).toFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(records, w);
        } catch (IOException e) {
            log.error("更新文件备注失败", e);
        }
    }

    public List<SavedFileRecord> loadFileRecords() {
        File file = configPath.resolve(SAVED_FILES_FILE).toFile();
        if (!file.exists()) return new ArrayList<>();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<SavedFileRecord>>() {}.getType();
            List<SavedFileRecord> list = gson.fromJson(r, type);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            log.error("加载文件记录失败", e);
            return new ArrayList<>();
        }
    }

    private void loadPreferences() {
        File file = configPath.resolve(PREFERENCES_FILE).toFile();
        if (!file.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            if (loaded != null) preferences = loaded;
        } catch (IOException e) {
            log.error("加载偏好设置失败", e);
        }
    }

    // ── 自动保存 ──

    public boolean isAutoSaveEnabled() {
        return "true".equals(getPreference("autoSaveEnabled", "false"));
    }

    public void setAutoSaveEnabled(boolean enabled) {
        setPreference("autoSaveEnabled", String.valueOf(enabled));
    }

    public int getAutoSaveInterval() {
        try {
            return Integer.parseInt(getPreference("autoSaveInterval", "5"));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    public void setAutoSaveInterval(int interval) {
        setPreference("autoSaveInterval", String.valueOf(interval));
    }

    public String getAutoSaveUnit() {
        String v = getPreference("autoSaveUnit", "minutes");
        return v != null ? v : "minutes";
    }

    public void setAutoSaveUnit(String unit) {
        setPreference("autoSaveUnit", unit);
    }

    public String getAutoSavePath() {
        return getPreference("autoSavePath", configPath.resolve("auto-save").toAbsolutePath().toString());
    }

    public void setAutoSavePath(String path) {
        setPreference("autoSavePath", path);
    }

    // ── Splash screen ──

    public int getSplashMinDuration() {
        try { return Integer.parseInt(getPreference("splashMinDuration", "2000")); }
        catch (Exception e) { return 2000; }
    }

    public void setSplashMinDuration(int ms) {
        setPreference("splashMinDuration", String.valueOf(ms));
    }

    public int getSplashMaxDuration() {
        try { return Integer.parseInt(getPreference("splashMaxDuration", "10000")); }
        catch (Exception e) { return 10000; }
    }

    public void setSplashMaxDuration(int ms) {
        setPreference("splashMaxDuration", String.valueOf(ms));
    }

    public java.util.List<String> getOpenFolders() {
        String json = getPreference("openFolders", "[]");
        try {
            Type type = new TypeToken<java.util.List<String>>(){}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    public void setOpenFolders(java.util.List<String> paths) {
        setPreference("openFolders", gson.toJson(paths));
    }

    // ── 元数据配置 ──

    private List<DbMetadataConfig> metadataConfigs;

    public List<DbMetadataConfig> loadMetadataConfigs() {
        if (metadataConfigs != null) return metadataConfigs;
        String json = getPreference("metadataConfigs", "");
        if (!json.isEmpty()) {
            try {
                Type type = new TypeToken<List<DbMetadataConfig>>() {}.getType();
                List<DbMetadataConfig> list = gson.fromJson(json, type);
                if (list != null) {
                    // 迁移：如果全部 disabled，重置为默认（现在默认 enabled=true）
                    boolean anyEnabled = list.stream().anyMatch(DbMetadataConfig::isEnabled);
                    if (!anyEnabled) {
                        metadataConfigs = DbMetadataConfig.createDefaults();
                        saveMetadataConfigs(metadataConfigs);
                        return metadataConfigs;
                    }
                    metadataConfigs = list; return list;
                }
            } catch (Exception e) {
                log.warn("解析 metadataConfigs 失败，使用默认配置", e);
            }
        }
        metadataConfigs = DbMetadataConfig.createDefaults();
        return metadataConfigs;
    }

    // ── SQL 执行历史 ──

    public void saveSqlHistory(List<String> history) {
        if (history == null) return;
        File file = configPath.resolve(SQL_HISTORY_FILE).toFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(history, w);
        } catch (IOException e) {
            log.warn("保存 SQL 历史失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> loadSqlHistory() {
        File file = configPath.resolve(SQL_HISTORY_FILE).toFile();
        if (!file.exists()) return new ArrayList<>();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<String>>() {}.getType();
            List<String> list = gson.fromJson(r, type);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            log.warn("加载 SQL 历史失败", e);
            return new ArrayList<>();
        }
    }

    public void saveMetadataConfigs(List<DbMetadataConfig> configs) {
        this.metadataConfigs = configs;
        setPreference("metadataConfigs", gson.toJson(configs));
    }

    public List<DbMetadataConfig> resetMetadataConfigs() {
        List<DbMetadataConfig> defaults = DbMetadataConfig.createDefaults();
        saveMetadataConfigs(defaults);
        return defaults;
    }
}
