# 字体自定义功能设计方案

> 状态：规划中，待评审  
> 目标：允许用户在设置界面中按区域自定义字体系列和字号，与颜色个性化平行

---

## 一、现状

- 代码库中 54 处 `new Font(...)` 全部硬编码，分布在 12+ 个文件中
- `AppTheme` 只有颜色调色板（`PALETTES`），没有字体定义
- `ThemeManager` 只有颜色覆盖（`overrides: Map<String, Color>`），没有字体覆盖
- 所有 `applyTheme()` 方法只重新应用颜色，不重新应用字体
- 唯一例外：`ResultPanel.applyTableTheme()` 每次主题重用时都硬编码写回表格字体

---

## 二、数据模型

### 2.1 字体键定义

字体按**区域**分类，每个键代表一个区。用户自定义**系列 (family)** + **字号 (size)**，风格 (PLAIN/BOLD) 由组件语义决定不变。

| 键 | 默认系列 | 默认字号 | 默认风格 | 覆盖区域 |
|---|---|---|---|---|
| `font.editor` | Monospaced | 14 | PLAIN | SQL 编辑区、源码查看器、行号 |
| `font.table` | Monospaced | 12 | PLAIN | 结果集数据、分页下拉框 |
| `font.ui` | Segoe UI | 11 | PLAIN | 树节点、标签页、按钮、工具栏、列表 |
| `font.ui.bold` | Segoe UI | 11 | BOLD | 表头、节标题、图标标签（跟随 font.ui 字号） |

### 2.2 存储形式

在 `ThemeManager` 中新增 `Map<String, String> fontOverrides`，值与持久化一致：

```
key → "family,size"
```

示例：`"font.editor" → "Consolas,16"`

### 2.3 默认值表

由一组常量提供默认值，写入 `ThemeManager` 作为 fallback：

```
DEFAULT_FONTS = Map.of(
    "font.editor",    "Monospaced,14",
    "font.table",     "Monospaced,12",
    "font.ui",        "Segoe UI,11"
)
```

`font.ui.bold` 不单独存系列和字号，跟随 `font.ui` 的值，仅风格固定为 BOLD。

---

## 三、核心 API（ThemeManager 新增）

```java
// 获取解析后的字体值，返回 "family,size"
public String resolveFont(String key)

// 用户覆盖
public void setFontOverride(String key, String value)   // value = "family,size"
public void removeFontOverride(String key)
public void clearFontOverrides()
public Map<String, String> getFontOverrides()
public void loadFontOverrides(Map<String, String> map)

// 工具方法：从解析值构造 Font 对象（给各组件调用）
public static Font createFont(String fontKey, int style)
```

`createFont()` 内部实现：

```java
public static Font createFont(String fontKey, int style) {
    String val = ThemeManager.getInstance().resolveFont(fontKey);
    String[] parts = val.split(",");
    String family = parts[0].trim();
    int size = Integer.parseInt(parts[1].trim());
    return new Font(family, style, size);
}
```

---

## 四、UI 设计（设置对话框）

### 4.1 树节点

在"个性化"与"SQL 格式化"之间增加节点：

```
├─ 个性化
│  ├─ 背景
│  ├─ 前景
│  └─ ...
├─ 字体 (NEW)
│  ├─ 编辑器
│  ├─ 结果表格
│  ├─ 界面文字
│  └─ 标题粗体
├─ SQL 格式化
```

### 4.2 字体面板布局

点击"编辑器"时右侧显示：

```
┌─────────────────────────────────────────────┐
│  编辑器                                       │
│                                             │
│  SQL 编辑区 / 行号                            │
│  ┌──────────────────────┐  ┌──────┐         │
│  │ [Monospaced      ▼]  │  │ [14] │  ▲▼     │
│  └──────────────────────┘  └──────┘         │
│                                             │
│  预览:                                       │
│  ┌──────────────────────────────────────┐   │
│  │ SELECT * FROM users                   │   │
│  │ WHERE status = 'ACTIVE'               │   │
│  │ ORDER BY created_at DESC;             │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

- 字体系列：`JComboBox<String>` 填充 `GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()`
- 字号：`JSpinner`，范围 8-48
- 预览：`JLabel` 以所选字体实时渲染示例文本

### 4.3 其他面板

**结果表格：**
- 预览内容为两行三列表格模拟（ID / NAME / VALUE）

**界面文字：**
- 预览内容为"对象树 | 标签页 | 按钮 | 列表项"

**标题粗体：**
- 与"界面文字"共享字号，不重复显示系列选择器
- 仅展示预览，提示用户"字号跟随界面文字"

---

## 五、生效时机

与颜色个性化一致：
1. 用户在字号 Spinner 或系列 ComboBox 上操作时实时生效
2. 调用 `ThemeManager.setFontOverride()` 更新覆盖
3. 触发 `MainFrame.reapplyTheme()`（其内部调用各面板 `applyTheme()`）
4. 各面板 `applyTheme()` 改为通过 `ThemeManager.createFont()` 获取字体

---

## 六、持久化

### 6.1 存储位置

在已有的工作区 JSON 中添加 `fontOverrides` 字段：

```json
{
  "theme": "DARK",
  "colorOverrides": { ... },
  "fontOverrides": {
    "font.editor": "JetBrains Mono,15",
    "font.table": "Consolas,13"
  }
}
```

### 6.2 读写接口

`ThemeManager` 新增方法委托给 `ConfigManager`：

```java
// 在 loadFromConfig 中添加
if (ws.fontOverrides != null) loadFontOverrides(ws.fontOverrides);

// 在 saveToConfig 中添加
ws.fontOverrides = getFontOverrides();
```

`WorkspaceState` 类新增 `Map<String, String> fontOverrides` 字段。

---

## 七、修改清单

### 7.1 基础设施层（2 文件）

| 文件 | 改动 |
|---|---|
| `ThemeManager.java` | 新增 `DEFAULT_FONTS` 常量、`fontOverrides` 字段、`resolveFont()`/`setFontOverride()`/`removeFontOverride()`/`createFont()` 方法、`loadFromConfig`/`saveToConfig` 增加字体读写 |
| `ConfigManager.java` 中 `WorkspaceState` | 新增 `Map<String, String> fontOverrides` 字段 |

### 7.2 UI 层（1 文件）

| 文件 | 改动 |
|---|---|
| `SettingsDialog.java` | 新增 `FONT_GROUPS` 常量定义、树节点"字体"及其子节点、"字体"的右侧面板（系列 ComboBox + 字号 Spinner + 预览）、`onTreeSelect` 分发、`onFontChanged` 事件回调 |

### 7.3 应用层（~10 文件，54 处替换）

每个文件中的 `new Font(name, style, size)` 替换为 `ThemeManager.createFont(key, style)`：

| 文件 | 修改处数 | 替换的字体键 |
|---|---|---|
| `SqlEditorPanel.java` | ~4 处 | `font.editor` |
| `SourceViewerPanel.java` | ~6 处 | `font.editor` / `font.ui` |
| `ResultPanel.java` | ~8 处 | `font.table` / `font.ui` |
| `ObjectBrowser.java` | ~8 处 | `font.ui` / `font.ui.bold` |
| `BottomPanel.java` | ~3 处 | `font.ui` / `font.table` |
| `MainFrame.java` | ~4 处 | `font.ui` / `font.table` |
| `RightPanel.java` | ~2 处 | `font.ui` |
| `WelcomePanel.java` | ~4 处 | `font.ui` / `font.ui.bold` |
| `VerticalTabButton.java` | 1 处 | `font.ui.bold` |
| `SettingsDialog.java` | ~8 处 | `font.ui` |

---

## 八、向后兼容保证

1. 默认字体值与当前硬编码值完全一致，用户不修改时零变化
2. `resolveFont(key)` 有三级 fallback：`fontOverrides` → `DEFAULT_FONTS` → `"Monospaced,12"`，永不返回空值
3. 每个替换都是单向的：`new Font("Monospaced", PLAIN, 14)` → `ThemeManager.createFont("font.editor", PLAIN)`，语义完全等价
4. `WorkspaceState` 新增字段为可选，旧 JSON 文件读取时缺失该字段不会抛出异常
