# 主题系统指南

## 架构总览

```
AppTheme.java (枚举)          ← 每个主题定义颜色调色板 + 配置
    │
ThemeManager.java (单例)      ← resolve(key)、switchTo、override
    │
Component.java                ← theme.resolve("bg.main") 读取颜色
```

- 组件代码**从不硬编码颜色值**，均通过 `theme.resolve("key")` 获取
- 所有颜色键（`bg.main`、`fg.muted` 等）在所有主题中必须保持一致
- 用户可通过 SettingsDialog → 个性化 覆盖任意颜色键（覆盖值存在 `workspace.json`）

## 颜色键清单

### 背景
| 键 | 用途 | DARK | LIGHT | GREEN |
|---|---|---|---|---|
| `bg.main` | 主背景（面板/树/列表） | #2B2B2B | #F2F2F2 | #C7EDCC |
| `bg.editor` | 代码编辑器背景 | #1E1E1E | #FFFFFF | #D4EDDA |
| `bg.panel` | 面板内部背景 | #252526 | #ECECEC | #B8D4BA |
| `bg.toolbar` | 工具栏/状态栏背景 | #2B2B2B | #F2F2F2 | #C7EDCC |
| `bg.output` | 输出区域背景 | #1E1E1E | #FFFFFF | #D4EDDA |
| `list.bg` | 列表/树选项背景 | #252526 | #F5F5F5 | #B8D4BA |
| `scroll.bg` | 滚动面板背景 | #252526 | #ECECEC | #B8D4BA |

### 前景
| 键 | 用途 | DARK | LIGHT | GREEN |
|---|---|---|---|---|
| `fg.main` | 主文字 | #D4D4D4 | #333333 | #333333 |
| `fg.secondary` | 次要文字 | #CCCCCC | #555555 | #555555 |
| `fg.muted` | 弱化文字 | #888888 | #999999 | #6B8E6B |
| `fg.title` | 标题文字 | #E0E0E0 | #333333 | #333333 |
| `fg.tab.active` | 标签页激活文字 | #E0E0E0 | #333333 | #333333 |
| `fg.tab.inactive` | 标签页未激活文字 | #999999 | #999999 | #6B8E6B |
| `list.fg` | 列表文字 | #CCCCCC | #333333 | #333333 |

### 选择
| 键 | 用途 | DARK | LIGHT | GREEN |
|---|---|---|---|---|
| `selection.bg` | 编辑器选中背景 | #264F78 | #C6E2FF | #A8D8A8 |
| `selection.fg` | 编辑器选中文字 | #FFFFFF | #333333 | #333333 |
| `selection.listBg` | 列表选中背景 | #094771 | #A5C8FF | #8BC68B |
| `selection.listFg` | 列表选中文字 | #FFFFFF | #333333 | #333333 |

### 边框
| 键 | 用途 | DARK | LIGHT | GREEN |
|---|---|---|---|---|
| `border.default` | 默认边框 | #3C3C3C | #D0D0D0 | #9DBFA1 |
| `border.light` | 浅边框 | #4A4A4A | #E0E0E0 | #AED8B2 |

### 强调
| 键 | 用途 | DARK | LIGHT | GREEN |
|---|---|---|---|---|
| `accent.green` | 绿色强调（缩略图光标等） | #4A9B4A | #2D7D2D | #3D8B3D |
| `accent.tab` | 标签页激活下划线 | #4A9B4A | #2D7D2D | #3D8B3D |

### 编辑器
| 键 | 用途 | DARK | LIGHT | GREEN |
|---|---|---|---|---|
| `editor.caret` | 光标色 | #D4D4D4 | #333333 | #333333 |

### 执行状态
| 键 | 用途 | DARK | LIGHT | GREEN |
|---|---|---|---|---|
| `exec.success` | 执行成功色 | #5CB85C | #5CB85C | #3D8B3D |
| `exec.fail` | 执行失败色 | #D9534F | #D9534F | #D9534F |
| `exec.highlight` | 执行高亮行（半透明） | #0FFFFFFF | #0FFFFF00 | #0F3D8B3D |

## 配置文件键

| 键 | 用途 | 值 |
|---|---|---|
| `rsta.theme` | RSyntaxTextArea 主题 XML 路径 | 内建用 `/org/fife/ui/rsyntaxtextarea/themes/dark.xml`，自定义用 `themes/xxx.xml` |
| `flatlaf` | FlatLaf 模式 | `"DARK"` 或 `"LIGHT"` |

## 如何新增一个主题

以新增一个 "High Contrast" 主题为例：

### 步骤 1 — 添加 RSTA 主题文件（可选）

如果需要自定义编辑器配色，在 `kylin-sql-ui/src/main/resources/themes/` 下新建 `hc.xml`。如果沿用已有 LAF 默认色可跳过此步。

### 步骤 2 — 在 `AppTheme.java` 中添加枚举常量

```java
// ── HIGH_CONTRAST ──
Map<String, Color> hc = new HashMap<>();
hc.put("bg.main", new Color(0x000000));
hc.put("bg.editor", new Color(0x000000));
// ... 所有键都必须有值
hc.put("scroll.bg", new Color(0x1A1A1A));
PALETTES.put(HIGH_CONTRAST, Collections.unmodifiableMap(hc));
Map<String, String> hcc = new HashMap<>();
hcc.put("rsta.theme", "themes/hc.xml");           // 或沿用内建
hcc.put("flatlaf", "DARK");                       // 用 Darcula LAF
CONFIGS.put(HIGH_CONTRAST, Collections.unmodifiableMap(hcc));
```

> **注意**：枚举常量名会被持久化到 `workspace.json`，改名会导致已保存的配置失效。

### 步骤 3 — 在 `MainFrame.switchTheme()` 中处理 LAF 映射

```java
public void switchTheme(AppTheme theme) {
    String flatlaf = theme.config("flatlaf");
    if ("DARK".equals(flatlaf)) {
        try { UIManager.setLookAndFeel(new FlatDarculaLaf()); }
        catch (Exception ignored) {}
    } else {
        try { UIManager.setLookAndFeel(new FlatLightLaf()); }
        catch (Exception ignored) {}
    }
    // ... 后续不变
}
```

如果新主题需要不同的 FlatLaf（当前只有 Darcula 和 Light 两种），在此处加分支。

### 步骤 4 — 在菜单和设置对话框中添加标签

**`MainFrame.java`**（主题菜单）:
```java
String[] themeLabels = {"Darcula", "Light", "豆沙绿", "High Contrast"};
AppTheme[] themeValues = {AppTheme.DARK, AppTheme.LIGHT, AppTheme.GREEN, AppTheme.HIGH_CONTRAST};
```

**`SettingsDialog.java`**（主题显示）:
```java
String[] labels = {"Darcula", "Light", "豆沙绿", "High Contrast"};
themeLabel.setText(labels[current.ordinal()]);
```

### 步骤 5 — 构建验证

```bash
mvn compile -pl kylin-sql-ui -am
```

启动后在菜单或设置中切换到新主题，检查所有区域的颜色是否正确。

## 涉及的源文件

| 文件 | 改动类型 |
|---|---|
| `kylin-sql-core/.../config/AppTheme.java` | **必须**：新增 enum 常量 + 完整调色板 |
| `kylin-sql-ui/.../MainFrame.java` | **必须**：switchTheme() LAF 分支 + 菜单 labels |
| `kylin-sql-ui/.../dialog/SettingsDialog.java` | **必须**：labels 数组同步 |
| `kylin-sql-ui/src/main/resources/themes/*.xml` | 可选：RSTA 主题文件 |
| 所有其他组件 | **不改**：均通过 theme.resolve(key) 自动适配 |
