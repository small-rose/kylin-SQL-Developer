# Kylin SQL Developer — 主题系统指南

## 内置主题一览

| 主题 | 枚举值 | FlatLaf | RSTA 主题 XML |
|------|--------|---------|---------------|
| Darcula（深色） | `DARK` | FlatDarculaLaf | `themes/dark.xml` |
| Light（浅色） | `LIGHT` | FlatLightLaf | `themes/light.xml` |
| 豆沙绿（护眼） | `GREEN` | FlatLightLaf | `themes/green.xml` |

源码位置：
- 主题定义：`kylin-sql-core/.../config/AppTheme.java`（枚举，28 色调色板）
- 主题管理器：`kylin-sql-core/.../config/ThemeManager.java`（单例，resolve/override/listener）
- 主题 XML：`kylin-sql-ui/src/main/resources/themes/{dark,light,green}.xml`
- 菜单绑定：`kylin-sql-ui/.../MainFrame.java:974-986`
- 设置面板：`kylin-sql-ui/.../settings/SettingsDialog.java:465-510`

---

## 架构概述

```
AppTheme.java (枚举)           ← 每个主题定义 28 色调色板 + 配置（flatlaf/rsta.theme）
    │
ThemeManager.java (单例)       ← resolve(key) / switchTo(theme) / addListener()
    │                                    颜色覆盖持久化（workspace.json）
    ▼
组件代码                       ← theme.resolve("bg.main") 获取颜色，不硬编码
```

**设计原则：**
- 组件代码**从不硬编码颜色值**，均通过 `theme.resolve("key")` 获取
- 所有颜色键（`bg.main`、`fg.muted` 等）在所有主题中必须存在
- 用户可通过 `SettingsDialog` → 主题个性化 覆盖任意颜色键（覆盖值存于 `workspace.json`）

---

## 颜色键清单

| 键 | 用途 | DARK | LIGHT | GREEN |
|---|---|---|---|---|
| **背景** | | | | |
| `bg.main` | 主背景（面板/树/列表） | `#2B2B2B` | `#F2F2F2` | `#C7EDCC` |
| `bg.editor` | 代码编辑器背景 | `#1E1E1E` | `#FFFFFF` | `#D4EDDA` |
| `bg.panel` | 面板内部背景 | `#252526` | `#ECECEC` | `#B8D4BA` |
| `bg.toolbar` | 工具栏/状态栏背景 | `#2B2B2B` | `#F2F2F2` | `#C7EDCC` |
| `bg.output` | 输出区域背景 | `#1E1E1E` | `#FFFFFF` | `#D4EDDA` |
| **前景** | | | | |
| `fg.main` | 主文字 | `#D4D4D4` | `#333333` | `#333333` |
| `fg.secondary` | 次要文字 | `#CCCCCC` | `#555555` | `#555555` |
| `fg.muted` | 弱化文字 | `#888888` | `#999999` | `#6B8E6B` |
| `fg.title` | 标题文字 | `#E0E0E0` | `#333333` | `#333333` |
| `fg.tab.active` | 标签页激活文字 | `#E0E0E0` | `#333333` | `#333333` |
| `fg.tab.inactive` | 标签页未激活文字 | `#999999` | `#999999` | `#6B8E6B` |
| **选择** | | | | |
| `selection.bg` | 编辑器选中背景 | `#264F78` | `#C6E2FF` | `#A8D8A8` |
| `selection.fg` | 编辑器选中文字 | `#FFFFFF` | `#333333` | `#333333` |
| `selection.listBg` | 列表选中背景 | `#094771` | `#A5C8FF` | `#8BC68B` |
| `selection.listFg` | 列表选中文字 | `#FFFFFF` | `#333333` | `#333333` |
| **边框** | | | | |
| `border.default` | 默认边框 | `#3C3C3C` | `#D0D0D0` | `#9DBFA1` |
| `border.light` | 浅边框 | `#4A4A4A` | `#E0E0E0` | `#AED8B2` |
| **强调** | | | | |
| `accent.green` | 绿色强调（缩略图光标等） | `#4A9B4A` | `#2D7D2D` | `#3D8B3D` |
| `accent.tab` | 标签页激活下划线 | `#4A9B4A` | `#2D7D2D` | `#3D8B3D` |
| **编辑器** | | | | |
| `editor.caret` | 光标色 | `#D4D4D4` | `#333333` | `#333333` |
| **列表/滚动** | | | | |
| `list.bg` | 列表/树选项背景 | `#252526` | `#F5F5F5` | `#B8D4BA` |
| `list.fg` | 列表文字 | `#CCCCCC` | `#333333` | `#333333` |
| `scroll.bg` | 滚动面板背景 | `#252526` | `#ECECEC` | `#B8D4BA` |
| **执行状态** | | | | |
| `exec.success` | 执行成功色 | `#5CB85C` | `#5CB85C` | `#3D8B3D` |
| `exec.fail` | 执行失败色 | `#D9534F` | `#D9534F` | `#D9534F` |
| `exec.highlight` | 执行高亮行 | `#0FFFFFFF` | `#0FFFFF00` | `#0F3D8B3D` |

> `exec.highlight` 使用带 alpha 通道的 ARGB 颜色，格式为 `0xAARRGGBB`。

---

## ThemeManager API

| 方法 | 说明 |
|------|------|
| `resolve(key)` | 获取颜色值（优先返回 override，否则返回当前主题调色板） |
| `switchTo(theme)` | 切换主题，触发所有 listener |
| `getCurrentTheme()` | 返回当前 `AppTheme` 枚举 |
| `setOverride(key, color)` | 覆盖指定颜色键 |
| `removeOverride(key)` | 移除单键覆盖 |
| `clearOverrides()` | 清空所有覆盖 |
| `getOverrideHexMap()` | 导出覆盖为十六进制 Map（用于持久化） |
| `loadOverrideHexMap(map)` | 从十六进制 Map 加载覆盖 |
| `addListener(r)` | 注册主题切换监听器（用于实时刷新 UI） |
| `loadFromConfig(config)` | 从 ConfigManager 加载主题 + 覆盖 |
| `saveToConfig(config)` | 保存当前主题 + 覆盖到 ConfigManager |

---

## 自定义扩展主题指南

以新增 **"High Contrast"（高对比度）主题**为例。

### 步骤 1 — 在 AppTheme 枚举中添加常量

文件：`kylin-sql-core/.../config/AppTheme.java`

枚举声明处新增：
```java
public enum AppTheme {
    DARK, LIGHT, GREEN, HIGH_CONTRAST;  // ← 新增
```

在 `static {}` 初始化块中新增调色板和配置：
```java
// ── HIGH_CONTRAST ──
Map<String, Color> hc = new HashMap<>();
hc.put("bg.main", new Color(0x000000));
hc.put("bg.editor", new Color(0x000000));
hc.put("bg.panel", new Color(0x0A0A0A));
hc.put("bg.toolbar", new Color(0x000000));
hc.put("bg.output", new Color(0x0A0A0A));
hc.put("fg.main", new Color(0xFFFFFF));
hc.put("fg.secondary", new Color(0xE0E0E0));
hc.put("fg.muted", new Color(0xAAAAAA));
hc.put("fg.title", new Color(0xFFFFFF));
hc.put("fg.tab.active", new Color(0xFFFFFF));
hc.put("fg.tab.inactive", new Color(0x888888));
hc.put("selection.bg", new Color(0xFFD700));
hc.put("selection.fg", new Color(0x000000));
hc.put("selection.listBg", new Color(0xFFD700));
hc.put("selection.listFg", new Color(0x000000));
hc.put("border.default", new Color(0x555555));
hc.put("border.light", new Color(0x333333));
hc.put("accent.green", new Color(0xFFD700));
hc.put("accent.tab", new Color(0xFFD700));
hc.put("editor.caret", new Color(0xFFFFFF));
hc.put("exec.success", new Color(0x00FF00));
hc.put("exec.fail", new Color(0xFF4444));
hc.put("exec.highlight", new Color(0x44FFFF00, true));
hc.put("list.bg", new Color(0x0A0A0A));
hc.put("list.fg", new Color(0xFFFFFF));
hc.put("scroll.bg", new Color(0x0A0A0A));
PALETTES.put(HIGH_CONTRAST, Collections.unmodifiableMap(hc));

Map<String, String> hcc = new HashMap<>();
hcc.put("rsta.theme", "themes/hc.xml");
hcc.put("flatlaf", "DARK");
CONFIGS.put(HIGH_CONTRAST, Collections.unmodifiableMap(hcc));
```

> **注意**：枚举常量名称会被持久化到 `workspace.json`，改名会导致已保存的配置失效。

### 步骤 2 — 新建 RSTA 主题 XML（可选）

如果需要自定义编辑器语法高亮配色，在 `kylin-sql-ui/src/main/resources/themes/` 下新建 `hc.xml`。

可以参考 `dark.xml` 的格式。如果沿用已有 LAF 默认色可跳过此步。

### 步骤 3 — 在主题菜单中添加选项

文件：`kylin-sql-ui/.../MainFrame.java`（约第 976 行）

```java
AppTheme[] themes = {AppTheme.DARK, AppTheme.LIGHT, AppTheme.GREEN, AppTheme.HIGH_CONTRAST};
String[] themeLabels = {"Darcula", "Light", "豆沙绿", "High Contrast"};
```

### 步骤 4 — 处理 FlatLaf 映射

`switchTheme()` 方法（第 3121 行）根据 `theme.config("flatlaf")` 的值选择 LookAndFeel：

```java
if ("DARK".equals(theme.config("flatlaf"))) {
    UIManager.setLookAndFeel(new FlatDarculaLaf());
} else {
    UIManager.setLookAndFeel(new FlatLightLaf());
}
```

如果新主题使用 `"DARK"`，无需修改；如果需要第三种 LAF，在此处加分支。

### 步骤 5 — 构建验证

```bash
mvn compile -pl kylin-sql-ui -am
```

启动后在菜单或设置中切换到新主题，检查所有 UI 区域的颜色是否正确。

---

## 设置对话框中的颜色覆盖

用户可在 `SettingsDialog` → 主题个性化 中覆盖任意颜色键：

- 左侧树节点名：`主题个性化`，对应 card name `"theme"`
- 面板使用 `ColorGroup` 分组，支持实时预览
- 覆盖值以十六进制字符串持久化到 `workspace.json` 的 `colorOverrides` 字段
- 点击 **重置** 按钮可清空所有覆盖，恢复主题默认值

---

## 涉及源文件清单

| 文件 | 扩展时需要改什么 |
|------|----------------|
| `kylin-sql-core/.../config/AppTheme.java` | **必须**：新增 enum 常量 + 完整 28 色调色板 + config 映射 |
| `kylin-sql-ui/.../MainFrame.java` | **必须**：`themes[]` + `themeLabels[]` 数组同步 |
| `kylin-sql-ui/.../MainFrame.java` | 可选：`switchTheme()` 中加 FlatLaf 分支 |
| `kylin-sql-ui/src/main/resources/themes/*.xml` | 可选：RSTA 语法高亮主题文件 |
| 其他所有组件 | **不改**：均通过 `theme.resolve(key)` 自动适配 |
