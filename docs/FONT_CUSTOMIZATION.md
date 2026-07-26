# 字体自定义

> 状态：已实现  
> FontManager 单例管理全局字体，支持按 UI 区域自定义字体、样式、颜色，实时预览，持久化到工作区 JSON。

---

## 一、架构

```
FontManager (kylin-sql-core)
 ├── 17 个字体键，6 个 UI 区域
 ├── 三级级联回退：用户覆盖 → 默认值 → Dialog,PLAIN,12
 ├── 颜色解析（可选 #RRGGBB 后缀）
 ├── 预览文本模板（每键对应不同示例）
 ├── 系统字体枚举（GraphicsEnvironment）
 └── 监听器 + fireListeners()
       ↓
SettingsDialog 字体面板
 ├── 树节点选择器（17 区域）
 ├── 字体系列 ComboBox（可编辑，自动补全）
 ├── 字号 Spinner（6-72）
 ├── 样式 ComboBox（PLAIN / BOLD / ITALIC / BOLDITALIC）
 ├── 颜色色块（点击打开 JColorChooser）
 ├── 上下文感知预览（表格/编辑区/标签）
 └── 重置为默认按钮
       ↓
ConfigManager.WorkspaceState.fontOverrides → workspace.json
```

---

## 二、字体键定义

### 2.1 完整列表

| 键 | 区域 | 默认值 | 默认颜色 |
|---|------|--------|---------|
| `font.default` | 通用界面（兜底） | `Microsoft YaHei UI,12` | — |
| `font.top` | 顶部（菜单/工具栏/标签） | → `font.default` | — |
| `font.left` | 左侧面板内容 | → `font.default` | — |
| `font.left.title` | 左侧面板标题 | → `font.default` | — |
| `font.editor` | 代码编辑器 | `Monospaced,14` | RSTA IDENTIFIER |
| `font.editor.comment` | 代码注释 | `Monospaced,13` | RSTA COMMENT_EOL |
| `font.editor.lineNum` | 行号栏 | → `font.editor` | — |
| `font.right` | 右侧面板 | → `font.default` | — |
| `font.right.title` | 右侧面板标题 | → `font.default` | — |
| `font.bottom` | 底部面板 | → `font.default` | — |
| `font.bottom.title` | 底部面板标题 | → `font.default` | — |
| `font.bottom.result` | 结果表（等宽） | `Monospaced,12` | — |
| `font.bottom.message` | 消息面板 | `Monospaced,12` | — |
| `font.bottom.result.header` | 结果表表头 | → `font.default` | — |
| `font.status` | 状态栏 | → `font.default` | — |
| `font.dialog` | 对话框内容 | → `font.default` | — |
| `font.dialog.title` | 对话框标题 | → `font.default` | — |

"→ x" 表示级联回退到 x 键的值。

### 2.2 值格式

```
FontName,Size
FontName,Size,Style
FontName,Size,Style,#RRGGBB
FontName,Size,#RRGGBB
```

- `FontName`：系统字体名称（如 `Consolas`, `Microsoft YaHei UI`）
- `Size`：字号（整数，6-72）
- `Style`：`PLAIN` / `BOLD` / `ITALIC` / `BOLDITALIC`
- `#RRGGBB`：可选字体颜色（如 `#FF0000`）
- 不指定 Style 时默认 `PLAIN`

### 2.3 级联回退规则

```
用户覆盖值 → DEFAULTS 值 → font.default → Dialog,PLAIN,12
```

- `resolve(key)` 优先读 `overrides`，无则读 `DEFAULTS`
- 若键本身无默认值且不是 `font.default`，级联到 `font.default`
- `font.default` 无配置时返回 `Dialog,PLAIN,12`

---

## 三、API 参考

### 3.1 字体解析

```java
FontManager fm = FontManager.getInstance();

// 返回 java.awt.Font 对象
Font font = fm.resolve("font.editor");
Font font = fm.resolve("font.top");

// 返回字体颜色（无颜色覆盖时返回 null）
Color color = fm.resolveColor("font.editor");

// 返回原始字符串值（含回退）
String val = fm.resolveValue("font.editor");
// → "Consolas,14" 或 "Monospaced,14"
```

### 3.2 覆盖管理

```java
// 简版：字体名+字号+颜色
fm.setOverride("font.editor", "JetBrains Mono", 15, null);

// 完整版：字体名+字号+样式+颜色
fm.setOverride("font.editor", "JetBrains Mono", 15, "BOLD", Color.BLUE);
// 内部序列化为 "JetBrains Mono,15,BOLD,#0000FF"

// 读取/移除
String val = fm.resolveValue("font.editor");
fm.removeOverride("font.editor");
fm.clearOverrides();
Map<String, String> all = fm.getOverrideMap();
```

### 3.3 持久化

```java
// 从 ConfigManager 加载
fm.loadFromConfig(configManager);
// 内部: ws.fontOverrides → overrides

// 保存到 ConfigManager
fm.saveToConfig(configManager);
// 内部: overrides → ws.fontOverrides → saveWorkspace(ws)
```

### 3.4 监听器

```java
fm.addListener(() -> {
    // 字体变更后统一刷新
    MainFrame.getInstance().reapplyTheme();
});
fm.fireListeners();
```

### 3.5 辅助方法

```java
// 获取系统所有可用字体
String[] allFonts = fm.getAllFonts();
// 示例: ["Arial", "Consolas", "Microsoft YaHei UI", ...]

// 获取字体标签（含等宽/中文标记）
String label = FontManager.getFontLabel("Consolas");
// → "Consolas  [等宽]  [中文]"

// 获取所有键
Set<String> keys = FontManager.getKeys();

// 获取默认值
String def = FontManager.getDefault("font.editor");

// 获取中文标签
String label = FontManager.getLabel("font.editor");
// → "代码编辑器"

// 获取预览文本
String text = FontManager.getPreviewText("font.editor");
// → "SELECT * FROM orders"
```

---

## 四、UI 设置界面

位于设置对话框的**字体**节点（树节点在"个性化"与"SQL 格式化"之间）。

### 4.1 布局

```
设置对话框
 ├── 主题
 ├── 个性化（颜色组）
 ├── 字体 ← 本节
 │    ├── 通用界面（兜底）
 │    ├── 顶部
 │    ├── 左侧面板
 │    ├── 左侧面板标题
 │    ├── 代码编辑器
 │    ├── 代码注释
 │    ├── 行号栏
 │    ├── 右侧面板
 │    ├── 右侧面板标题
 │    ├── 底部面板
 │    ├── 底部面板标题
 │    ├── 结果表（等宽）
 │    ├── 消息面板
 │    ├── 结果表表头
 │    ├── 状态栏
 │    ├── 对话框内容
 │    └── 对话框标题
 ├── 常用配置
 └── 元数据
```

### 4.2 每项控件

- **字体系列**：可编辑 `JComboBox`，列出所有系统字体，支持输入搜索
- **字号**：`JSpinner` 范围 6-72，步进 1
- **样式**：`JComboBox` — PLAIN（常规）/ BOLD（粗体）/ ITALIC（斜体）/ BOLDITALIC（粗斜体）
- **颜色**：色块 → 点击弹出 `JColorChooser`，支持自定义颜色
- **预览**：上下文感知
  - 编辑器键 → `JTextArea` 渲染 SQL 示例
  - 表格键 → `JTable` 渲染假数据行
  - 其余 → `JLabel` 渲染区域文本
- **重置为默认**：恢复该键的出厂默认值

### 4.3 生效时机

修改任意控件后**实时生效**（`applyFontOverride()` 调用并刷新预览），关闭对话框时通过 `saveSettings()` → `FontManager.saveToConfig()` 持久化。

---

## 五、使用统计

73+ 处 `FontManager.resolve()` 调用遍布 12 个 UI 文件：

| 文件 | 调用数 | 使用的键 |
|------|--------|---------|
| SourceViewerPanel.java | 9 | `font.top`, `font.editor`, `font.editor.lineNum`, `font.left`, `font.left.title`, `font.bottom.result`, `font.bottom.title`, `font.status` |
| ResultPanel.java | 11 | `font.bottom.message`, `font.bottom.result`, `font.bottom.result.header`, `font.bottom` |
| SqlEditorPanel.java | 6 | `font.editor.lineNum`, `font.editor`, `font.editor.comment` |
| BottomPanel.java | 6 | `font.bottom.title`, `font.bottom` |
| SettingsDialog.java | 12 | `font.editor`, `font.dialog`, `font.dialog.title`, `font.status`, `font.bottom.result`, `font.bottom.result.header`, `font.bottom.message`, `font.editor.lineNum`, `font.editor.comment` |
| ObjectBrowser.java | 5 | `font.left`, `font.left.title` |
| LocalFileBrowser.java | 3 | `font.left` |
| AboutDialog.java | 5 | `font.dialog.title`, `font.dialog` |
| BaseToolDialog.java | 1 | `font.editor` |
| LogViewerDialog.java | 1 | `font.dialog` |
| GlobalSearchDialog.java | 3 | `font.dialog`, `font.editor` |
| ExportDialog.java | 2 | `font.dialog` |
| AdvancedExportDialog.java | 1 | `font.dialog` |
| WelcomePanel.java | 1 | `font.editor` |
| MainFrame.java | 2 | `font.top` |
| VerticalTabButton.java | 2 | `font.top` |
| SplashScreen.java | 1 | `font.top` |

---

## 六、向后兼容

1. 默认值与原硬编码值一致，用户不修改时零变化
2. `resolve(key)` 永不返回 `null`
3. 每个替换语义等价：`new Font("Monospaced", PLAIN, 14)` → `fm.resolve("font.editor")`
4. `WorkspaceState.fontOverrides` 可选字段，旧 JSON 缺失时不会抛异常
5. `resolveColor(key)` 无颜色覆盖时返回 `null`，调用方自行 fallback
