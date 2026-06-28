# 工具菜单设计文档

## 一、总体架构

### 1.1 包结构

```
com.kylin.plsql.ui
├── component
│   └── ToastManager.java          # 全局无打扰通知
└── dialog
    ├── BaseToolDialog.java          # 工具基类
    ├── SqlToolsDialog.java          # SQL 工具
    ├── SqlFormatDialog.java         # SQL 格式化
    ├── DataGeneratorDialog.java     # 数据生成器
    ├── SqlHistoryDialog.java        # SQL 历史
    ├── TextDiffDialog.java          # 文本比较
    ├── RegexTesterDialog.java       # 正则测试器
    ├── ObjectSearchDialog.java      # 对象搜索
    ├── AdvancedExportDialog.java    # 高级导出（同步）
    └── ExportTaskListDialog.java    # 导出任务列表（异步）
```

### 1.2 基类 BaseToolDialog

```
public abstract class BaseToolDialog extends JDialog
```

| 字段/方法 | 签名 | 说明 |
|---|---|---|
| 构造器 | `BaseToolDialog(Frame owner, String title)` | `super(owner, title, false)`, 初始化 theme, 末尾调用 `centerOnOwner()` + `applyTheme()` |
| `owner` | `protected final Frame` | 父窗口 |
| `theme` | `protected final ThemeManager` | `ThemeManager.getInstance()` |
| `wrapTitled` | `protected JPanel wrapTitled(String, Component)` | 创建带 `TitledBorder` 的面板 |
| `wrapScroll` | `protected JScrollPane wrapScroll(Component)` | `new JScrollPane(comp)` |
| `monoFont` | `protected Font monoFont()` | `new Font("Monospaced", PLAIN, 12)` |
| `btn` | `protected JButton btn(String, ActionListener)` | 创建按钮并添加监听器 |
| `themeColor` | `protected Color themeColor(String)` | `theme.resolve(key)` |
| `centerOnOwner` | `protected void centerOnOwner()` | `setLocationRelativeTo(owner)` |
| `setSizeRatio` | `protected void setSizeRatio(double)` | 按屏幕比例设置窗口大小 |
| `applyTheme` | `protected void applyTheme()` | `getContentPane().setBackground(themeColor("bg.main"))` |
| *通用原则* | | 所有 JTextArea / JTable / JList / JTree 必须包裹在 JScrollPane 中；所有分隔区域使用 JSplitPane 并设可调节 |

### 1.3 ToastManager 全局通知

```
com.kylin.plsql.ui.component.ToastManager
```

**设计:** 单例，同一时间只显示一个 toast，自动消失。

**方法:**

| 方法 | 说明 |
|---|---|
| `static void show(Component anchor, String msg)` | 默认 2 秒，anchor 右下角 |
| `static void show(Window owner, String msg)` | 默认 2 秒，owner 右下角 |
| `static void show(Component anchor, String msg, int durationMs)` | 自定义时长 |
| `static void showError(Component anchor, String msg)` | 红色背景，4 秒 |

**行为:**
- 自动判断 dark/light 模式（取 `bg.panel` 亮度），选择深色/浅色 toast 背景
- 定位到父窗口右下角（同 ObjectBrowser 实现）
- 关闭已有 toast 再显示新的
- 2 秒后用 `Timer` 自动 `dispose()`

**使用方式（所有 dialog 统一）:**
```java
ToastManager.show(this, "操作成功");
ToastManager.showError(this, "导出失败: " + e.getMessage());
```

---

## 二、各工具详设

### 2.1 SQL 工具 — SqlToolsDialog

```
public class SqlToolsDialog extends BaseToolDialog
```

**构造器签名:** `SqlToolsDialog(Frame owner)`

**功能:** SQL 字符串转义/反转义、IN 子句与多行互转

**字段:**

| 字段 | 类型 | 访问 | 说明 |
|---|---|---|---|
| `inputArea` | `JTextArea` | `private final` | 输入区 |
| `outputArea` | `JTextArea` | `private final` | 输出区（不可编辑） |
| `tabbedPane` | `JTabbedPane` | `private final` | "字符转义" / "IN子句转换" 两个标签页 |

**方法:**

| 方法 | 签名 | 说明 |
|---|---|---|
| `escapeSql` | `static String escapeSql(String)` | 单引号转义 `'` → `''` |
| `unescapeSql` | `static String unescapeSql(String)` | 反转义 `''` → `'` |
| `toInClause` | `static String toInClause(String, boolean)` | 多行转 `('a','b')`，第二个参数控制是否加引号 |
| `fromInClause` | `static String fromInClause(String)` | `('a','b')` 转多行 |
| `buildEscapePanel` | `private JPanel buildEscapePanel()` | 构建转义标签页 |
| `buildInClausePanel` | `private JPanel buildInClausePanel()` | 构建 IN 子句标签页 |

**UI 布局:**

```
JDialog ("SQL 工具", setSizeRatio(0.75))
  └─ JTabbedPane
       ├─ "字符转义"
       │    └─ BorderLayout
       │         ├─ North:    JScrollPane + JTextArea inputArea (4行)
       │         ├─ Center:   FlowLayout + [SQL 转义] [SQL 反转义]
       │         └─ South:    JScrollPane + JTextArea outputArea (4行, 不可编辑)
       └─ "IN 子句转换"
            └─ BorderLayout
                 ├─ North:    JScrollPane + JTextArea inputArea (4行)
                 ├─ Center:   FlowLayout + ☐ 带引号 + [→ IN 子句] [← 还原]
                 └─ South:    JScrollPane + JTextArea outputArea (4行, 不可编辑)
```

---

### 2.2 SQL 格式化 — SqlFormatDialog

```
public class SqlFormatDialog extends BaseToolDialog
```

**构造器签名:** `SqlFormatDialog(Frame owner, FormatOptions formatOptions)`

**功能:** 输入 SQL 文本，调用 `SqlFormatter` 格式化后预览

**字段:**

| 字段 | 类型 | 访问 | 说明 |
|---|---|---|---|
| `inputArea` | `JTextArea` | `private final` | SQL 输入区（语法高亮，可编辑） |
| `outputArea` | `RSyntaxTextArea` | `private final` | 格式化结果（语法高亮，不可编辑） |
| `formatOptions` | `FormatOptions` | `private final` | 格式化配置项 |
| `splitPane` | `JSplitPane` | `private final` | 输入/输出的分割面板，可调节 |
| `layoutToggleBtn` | `JToggleButton` | `private final` | 布局切换按钮 |

**方法:**

| 方法 | 签名 | 说明 |
|---|---|---|
| `doFormat` | `private void doFormat()` | `SqlFormatter.format(input, options)` → outputArea.setText |
| `applyOutputTheme` | `private void applyOutputTheme()` | outputArea 应用 RSyntaxTextArea 主题 |
| `toggleLayout` | `private void toggleLayout()` | 切换 splitPane 方向: HORIZONTAL ↔ VERTICAL；更新按钮文本和提示 |

**UI 布局:**

```
JDialog ("SQL 格式化", setSizeRatio(0.75))

  水平布局（默认）:
    BorderLayout
      └─ Center: JSplitPane (HORIZONTAL_SPLIT, resizeWeight=0.5, 可调节)
           ├─ Left:  wrapTitled("输入 SQL", JScrollPane + JTextArea inputArea)
           └─ Right: wrapTitled("格式化结果", JScrollPane + RSyntaxTextArea outputArea)
      └─ South: FlowLayout(CENTER)
           ├─ [⇔ 垂直布局]  ← layoutToggleBtn
           └─ [格式化 (Ctrl+Enter)]

  垂直布局（点击切换后）:
    BorderLayout
      └─ Center: JSplitPane (VERTICAL_SPLIT, resizeWeight=0.5, 可调节)
           ├─ Top:    wrapTitled("输入 SQL", JScrollPane + JTextArea inputArea)
           └─ Bottom: wrapTitled("格式化结果", JScrollPane + RSyntaxTextArea outputArea)
      └─ South: FlowLayout(CENTER)
           ├─ [⇕ 水平布局]  ← 按钮文本同步更新
           └─ [格式化 (Ctrl+Enter)]
```

---

### 2.3 数据生成器 — DataGeneratorDialog

```
public class DataGeneratorDialog extends BaseToolDialog
```

**构造器签名:** `DataGeneratorDialog(Frame owner, Function<String, Connection> connectionProvider)`

**功能:** 选择数据库表，按列类型生成测试数据

**字段:**

| 字段 | 类型 | 访问 | 说明 |
|---|---|---|---|
| `connCombo` | `JComboBox<String>` | `private final` | 连接选择 |
| `schemaCombo` | `JComboBox<String>` | `private final` | Schema 选择 |
| `tableCombo` | `JComboBox<String>` | `private final` | 表选择 |
| `rowCountSpinner` | `JSpinner` | `private final` | 生成行数 (SpinnerNumberModel 1-10000) |
| `dialectCombo` | `JComboBox<String>` | `private final` | 数据库方言: Oracle / MySQL / PostgreSQL / ANSI SQL |
| `columns` | `List<ColumnDef>` | `private` | 表列定义列表 |
| `colModel` | `ColumnTableModel` | `private final` | 列表格模型 (name, type, rule) |
| `columnTable` | `JTable` | `private final` | 列定义表格 |
| `outputArea` | `RSyntaxTextArea` | `private final` | 生成结果预览（语法高亮，不可编辑） |
| `splitPane` | `JSplitPane` | `private final` | 列定义/数据预览的分割面板，可调节 |
| `layoutToggleBtn` | `JToggleButton` | `private final` | 布局切换按钮 |
| `connProvider` | `Function<String, Connection>` | `private final` | 连接获取函数 |

**内部类:**

| 类名 | 说明 |
|---|---|
| `ColumnDef` | `(String name, String type, String rule)` 列定义 |
| `ColumnTableModel` | `extends AbstractTableModel`，三列: 列名/类型/生成规则 |

**方法:**

| 方法 | 签名 | 说明 |
|---|---|---|
| `populateConnections` | `public void populateConnections(List<String>)` | 填充连接下拉 |
| `loadSchemas` | `private void loadSchemas()` | 切换连接时加载 schema |
| `loadTables` | `private void loadTables()` | 切换 schema 时加载表 |
| `loadColumns` | `private void loadColumns()` | 切换表时加载列元数据 |
| `generate` | `private void generate()` | SwingWorker 异步执行，按规则生成数据 |
| `generateValue` | `private String generateValue(ColumnDef, Random, int, String)` | 按规则生成单个值，第4个参数 dialect |
| `inferRule` | `private String inferRule(String)` | 根据列类型推断默认生成规则 |
| `toggleLayout` | `private void toggleLayout()` | 切换 splitPane 方向: HORIZONTAL ↔ VERTICAL |

**生成规则:**

| 列类型 | 默认规则(生成模板) | 输出字面量格式 | 说明 |
|---|---|---|---|
| VARCHAR2 / VARCHAR / CHAR | `random_string(10)` | `'abc123'` | 随机字符串，单引号括起并转义 |
| NVARCHAR2 / NCHAR | `random_string(10)` | `'中文abc'` | 随机字符串，支持中文 |
| NUMBER / INTEGER / INT / FLOAT | `random_int(1, 1000)` | `42` | 数字直接输出 |
| DATE | `random_date('2024-01-01', '2025-12-31')` | 见下文方言表↓ | 随机日期，格式按方言 |
| TIMESTAMP | `random_timestamp('2024-01-01', '2025-12-31')` | 见下文方言表↓ | 随机时间戳，格式按方言 |
| CLOB / NCLOB | `random_text(50)` | `'long text...'` | 随机文本，单引号括起 |
| BLOB / RAW | `random_hex(20)` | 见下文方言表↓ | 随机十六进制，格式按方言 |
| BOOLEAN | `random_boolean()` | `1` / `0` | 数字表示 |

**方言相关输出格式（generateValue 中按 dialectCombo 分发）:**

| 列类型 | Oracle | MySQL | PostgreSQL | ANSI SQL |
|---|---|---|---|---|
| DATE | `TO_DATE('2024-01-01','YYYY-MM-DD')` | `'2024-01-01'` | `DATE '2024-01-01'` | `DATE '2024-01-01'` |
| TIMESTAMP | `TO_TIMESTAMP('2024-01-01 12:00:00','YYYY-MM-DD HH24:MI:SS')` | `'2024-01-01 12:00:00'` | `TIMESTAMP '2024-01-01 12:00:00'` | `TIMESTAMP '2024-01-01 12:00:00'` |
| BLOB / RAW | `HEXTORAW('AB12')` | `X'AB12'` | `'\xAB12'::bytea` | 不支持直接 INSERT |

**UI 布局:**

```
JDialog ("数据生成器", setSizeRatio(0.75))

  水平布局（默认）:
    BorderLayout
      ├─ North: GridBagLayout + TitledBorder("数据源")
      │    ├─ Row 0:
      │    │    (0,0) 连接: [connCombo]  (1,0) Schema: [schemaCombo]
      │    │    (2,0) 表: [tableCombo]   (3,0) 行数: [rowCountSpinner(100)]
      │    │    (4,0) 方言: [dialectCombo(Oracle/MySQL/PostgreSQL/ANSI)]
      │    └─ Row 1:
      │         (4,1) [加载列] [生成数据]
      └─ Center: JSplitPane splitPane (HORIZONTAL_SPLIT, resizeWeight=0.35, 可调节)
           ├─ Left:  wrapTitled("列定义", JScrollPane + JTable columnTable)
           │         列: 列名 | 类型 | 生成规则(可编辑)
           └─ Right: wrapTitled("数据预览", JScrollPane + RSyntaxTextArea outputArea)
      └─ South: FlowLayout(CENTER)
           └─ [⇔ 垂直布局]  ← layoutToggleBtn

  垂直布局（点击切换后）:
    BorderLayout
      ├─ North: 同上
      └─ Center: JSplitPane splitPane (VERTICAL_SPLIT, resizeWeight=0.35, 可调节)
           ├─ Top:    wrapTitled("列定义", JScrollPane + JTable columnTable)
           └─ Bottom: wrapTitled("数据预览", JScrollPane + RSyntaxTextArea outputArea)
      └─ South: FlowLayout(CENTER)
           └─ [⇕ 水平布局]  ← 按钮文本同步更新
```

---

### 2.4 SQL 历史 — SqlHistoryDialog

```
public class SqlHistoryDialog extends BaseToolDialog
```

**构造器签名:** `SqlHistoryDialog(Frame owner, List<String> history, Consumer<String> onSelect)`

**功能:** 浏览 SQL 执行历史，支持搜索过滤

**字段:**

| 字段 | 类型 | 访问 | 说明 |
|---|---|---|---|
| `listModel` | `DefaultListModel<String>` | `private final` | 列表数据模型 |
| `historyList` | `JList<String>` | `private final` | SQL 历史列表 |
| `previewArea` | `RSyntaxTextArea` | `private final` | SQL 预览区（不可编辑） |
| `countLabel` | `JLabel` | `private final` | "共 N 条，已选 0 条" |
| `filterField` | `JTextField` | `private final` | 搜索过滤输入框 |
| `callback` | `Consumer<String>` | `private final` | 双击回调 |
| `allEntries` | `List<String>` | `private` | 全量历史数据（用于过滤） |

**方法:**

| 方法 | 签名 | 说明 |
|---|---|---|
| `filterList` | `private void filterList()` | 根据 filterField 文本过滤，实时更新 |
| `showPreview` | `private void showPreview()` | 选中项 → previewArea.setText |
| `applyOutputTheme` | `private void applyOutputTheme()` | previewArea 应用 RSyntaxTextArea 主题 |

**UI 布局:**

```
JDialog ("SQL 历史", setSizeRatio(0.75))
  └─ BorderLayout
       ├─ North: BorderLayout
       │    ├─ West:  "搜索:" + JTextField filterField (宽 250)
       │    └─ East:  countLabel ("共 100 条")
       └─ Center: JSplitPane (水平, resizeWeight=0.35)
            ├─ Left:  wrapTitled("历史记录", JScrollPane + JList historyList)
            └─ Right: wrapTitled("预览", JScrollPane + RSyntaxTextArea previewArea)
       └─ South: FlowLayout(RIGHT)
            └─ [使用选中的 SQL]
```

**交互规则:**

| 事件 | 行为 |
|---|---|
| filterField 文本变化 | 实时过滤 listModel（使用 DocumentListener） |
| historyList 选中变化 | 刷新 previewArea |
| [使用选中的 SQL] 点击 | callback.accept(selectedText) + dispose() |
| 双击 historyList 条目 | 同点击按钮 |

---

### 2.5 文本比较 — TextDiffDialog

```
public class TextDiffDialog extends BaseToolDialog
```

**构造器签名:** `TextDiffDialog(Frame owner)`

**功能:** 左右对照文本差异，基于 LCS 算法

**字段:**

| 字段 | 类型 | 访问 | 说明 |
|---|---|---|---|
| `leftArea` | `JTextArea` | `private final` | 左侧文本输入（等宽字体） |
| `rightArea` | `JTextArea` | `private final` | 右侧文本输入（等宽字体） |
| `resultArea` | `JTextArea` | `private final` | 差异结果展示（不可编辑） |
| `ignoreWsCb` | `JCheckBox` | `private final` | "忽略空白" |
| `statsLabel` | `JLabel` | `private final` | 统计标签 |
| `viewTabs` | `JTabbedPane` | `private final` | "对照视图" / "统一视图" |

**内部类:**

| 类名 | 说明 |
|---|---|
| `DiffType` | `enum { EQUAL, INSERT, DELETE, MODIFY }` |
| `DiffLine` | `(DiffType type, String text)` 差异行 |

**方法:**

| 方法 | 签名 | 说明 |
|---|---|---|
| `runDiff` | `private void runDiff()` | 执行 LCS 差异比较 |
| `linesEqual` | `private boolean linesEqual(String, String)` | 比较两行（考虑 ignoreWsCb） |
| `computeDiff` | `private List<DiffLine> computeDiff(String[], String[])` | LCS 算法 |
| `renderSideBySide` | `private void renderSideBySide(List<DiffLine>)` | 渲染对照视图 |
| `renderUnified` | `private void renderUnified(List<DiffLine>)` | 渲染统一视图（+/- 标记） |
| `loadFile` | `private void loadFile(JTextArea)` | 从文件加载文本 |

**UI 布局:**

```
JDialog ("文本比较", setSizeRatio(0.85))
  └─ BorderLayout
       ├─ North: GridBagLayout
       │    ├─ (0,0) [打开左侧文件]  (1,0) [打开右侧文件]
       │    └─ (2,0) ☐ 忽略空白  (3,0) [比较]
       └─ Center: JSplitPane (VERTICAL_SPLIT, resizeWeight=0.55, 可调节)
            ├─ Top: JSplitPane (HORIZONTAL_SPLIT, resizeWeight=0.5, 可调节)
            │    ├─ Left:  wrapTitled("左侧文本", JScrollPane + JTextArea leftArea)
            │    └─ Right: wrapTitled("右侧文本", JScrollPane + JTextArea rightArea)
            └─ Bottom: BorderLayout
                 ├─ North: statsLabel ("差异: +3 -2 ~1")
                 └─ Center: JTabbedPane
                      ├─ "对照视图": JScrollPane + JTextArea resultArea (行前缀: =/-/+)
                      └─ "统一视图": JScrollPane + JTextArea resultArea (diff 格式)
```

**差异标记:**

| 行前缀 | 含义 | 颜色 |
|---|---|---|
| `= ` | 相同 | 默认 |
| `- ` | 左侧删除 | 红色 |
| `+ ` | 右侧新增 | 绿色 |
| `~ ` | 修改 | 黄色 |

---

### 2.6 正则测试器 — RegexTesterDialog

```
public class RegexTesterDialog extends BaseToolDialog
```

**构造器签名:** `RegexTesterDialog(Frame owner)`

**功能:** 交互式测试正则表达式，显示匹配结果和分组捕获

**字段:**

| 字段 | 类型 | 访问 | 说明 |
|---|---|---|---|
| `regexField` | `JTextField` | `private final` | 正则表达式输入 |
| `testArea` | `JTextArea` | `private final` | 测试文本（6行） |
| `resultArea` | `JTextArea` | `private final` | 匹配结果（不可编辑） |
| `replaceField` | `JTextField` | `private final` | 替换文本输入 |
| `replaceResultArea` | `JTextArea` | `private final` | 替换结果（不可编辑） |
| `caseCb` | `JCheckBox` | `private final` | "忽略大小写" |
| `multilineCb` | `JCheckBox` | `private final` | "多行模式" |
| `dotallCb` | `JCheckBox` | `private final` | "DOTALL 模式" |
| `favoriteModel` | `DefaultListModel<String>` | `private final` | 常用正则列表（显示名称 + 表达式） |
| `favoriteList` | `JList<String>` | `private final` | 常用正则列表 UI |
| `modeTabs` | `JTabbedPane` | `private final` | "匹配" / "替换" 标签 |
| `groupModel` | `GroupTableModel` | `private final` | 分组捕获表格模型 |
| `groupTable` | `JTable` | `private final` | 分组捕获表格 |

**内部类:**

| 类名 | 说明 |
|---|---|
| `GroupTableModel extends AbstractTableModel` | `(组号, 值)` 两列表格模型 |

**方法:**

| 方法 | 签名 | 说明 |
|---|---|---|
| `runTest` | `private void runTest()` | 编译 Pattern，执行 matcher，显示匹配数和分组 |
| `runReplace` | `private void runReplace()` | 执行 replaceAll/replaceFirst |
| `loadDefaultFavorites` | `private void loadDefaultFavorites()` | 加载常用正则（15 个预设） |
| `favoriteSelected` | `private void favoriteSelected()` | 双击常用正则 → 填入 regexField + 执行测试 |

**常用正则预设（`loadDefaultFavorites`）:**

| # | 名称 | 表达式 |
|---|---|---|
| 1 | 邮箱 | `^[\w.-]+@[\w.-]+\.\w{2,}$` |
| 2 | 手机号 | `^1[3-9]\d{9}$` |
| 3 | URL | `^https?://[\w.-]+(/\S*)?$` |
| 4 | IP 地址 | `^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$` |
| 5 | 匹配中文 | `[\u4e00-\u9fa5]+` |
| 6 | 身份证号 | `^\d{17}[\dXx]$` |
| 7 | 日期 (YYYY-MM-DD) | `^\d{4}-\d{2}-\d{2}$` |
| 8 | 时间 (HH:MM:SS) | `^\d{2}:\d{2}:\d{2}$` |
| 9 | 邮编 | `^\d{6}$` |
| 10 | HTML 标签 | `<[^>]+>` |
| 11 | 空白行 | `^\s*$` |
| 12 | 颜色十六进制 | `^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$` |
| 13 | 用户名 (字母数字下划线) | `^\w{3,20}$` |
| 14 | 文件扩展名 | `\.\w+$` |
| 15 | 数字 | `^-?\d+(\.\d+)?$` |

**UI 布局:**

```
JDialog ("正则测试器", setSizeRatio(0.75))
  └─ BorderLayout
       ├─ North: GridBagLayout
       │    ├─ (0,0,colspan=7) "正则表达式:" + [regexField] + [测试]
       │    └─ (1,0) ☐ 忽略大小写  (1,1) ☐ 多行模式  (1,2) ☐ DOTALL
       ├─ Center: JSplitPane (水平, resizeWeight=0.75)
       │    ├─ Left: BorderLayout
       │    │    ├─ North: wrapTitled("测试文本", JScrollPane + JTextArea testArea)
       │    │    └─ Center: JTabbedPane modeTabs
       │    │         ├─ "匹配结果": JSplitPane (垂直)
       │    │         │    ├─ Top: JScrollPane + JTextArea resultArea
       │    │         │    └─ Bottom: JScrollPane + JTable groupTable (组号 | 值)
       │    │         └─ "替换": BorderLayout
       │    │              ├─ North: FlowLayout + "替换为:" + [replaceField] + [替换全部]
       │    │              └─ Center: JScrollPane + JTextArea replaceResultArea
       │    └─ Right: wrapTitled("常用正则", JScrollPane + JList favoriteList)
```

---

### 2.7 对象搜索 — ObjectSearchDialog

```
public class ObjectSearchDialog extends BaseToolDialog
```

**构造器签名:** `ObjectSearchDialog(Frame owner, Function<String, Connection> connectionProvider, BiConsumer<String, String> onNavigate)`

**功能:** 按名称搜索数据库对象（表、视图、存储过程等）

**字段:**

| 字段 | 类型 | 访问 | 说明 |
|---|---|---|---|
| `searchField` | `JTextField` | `private final` | 搜索关键词 |
| `connCombo` | `JComboBox<String>` | `private final` | 连接选择 |
| `typeCombo` | `JComboBox<String>` | `private final` | 对象类型过滤（TABLE/VIEW/PROCEDURE/FUNCTION/PACKAGE/ALL） |
| `allSchemaCb` | `JCheckBox` | `private final` | "搜索所有 Schema" |
| `resultModel` | `DefaultListModel<SearchResult>` | `private final` | 搜索结果模型 |
| `resultList` | `JList<SearchResult>` | `private final` | 搜索结果列表 |
| `detailLabel` | `JLabel` | `private final` | 详情区域标题 |
| `detailModel` | `DetailTableModel` | `private final` | 对象详情模型（列名/类型/可为空 等） |
| `detailTable` | `JTable` | `private final` | 对象详情表格 |
| `connProvider` | `Function<String, Connection>` | `private final` | 连接获取函数 |
| `onNavigate` | `BiConsumer<String, String>` | `private final` | 导航回调 (connName, objName) |

**内部类:**

| 类名 | 说明 |
|---|---|
| `SearchResult` | `(String name, String type, String schema)` 搜索结果项，重写 toString 显示 `schema.name (type)` |
| `DetailTableModel extends AbstractTableModel` | `(属性, 值)` 两列表格 |

**方法:**

| 方法 | 签名 | 说明 |
|---|---|---|
| `populateConnections` | `public void populateConnections(List<String>)` | 填充连接下拉 |
| `doSearch` | `private void doSearch()` | SwingWorker 异步搜索（查询 ALL_OBJECTS） |
| `showDetails` | `private void showDetails()` | 选中结果后查询 ALL_TAB_COLUMNS / ALL_ARGUMENTS |

**UI 布局:**

```
JDialog ("对象搜索", setSizeRatio(0.75))
  └─ BorderLayout
       ├─ North: GridBagLayout + TitledBorder("搜索条件")
       │    ├─ (0,0) "关键词:"  (1,0) [searchField]  (2,0) [搜索]
       │    ├─ (0,1) "连接:"  (1,1) [connCombo]
       │    ├─ (2,1) "类型:"  (3,1) [typeCombo: ALL/TABLE/VIEW/PROCEDURE/FUNCTION/PACKAGE]
       │    └─ (4,1) ☐ 搜索所有 Schema
       └─ Center: JSplitPane (水平, resizeWeight=0.35)
            ├─ Left:  wrapTitled("搜索结果", JScrollPane + JList resultList)
            └─ Right: BorderLayout
                 ├─ North: detailLabel ("未选中对象")
                 └─ Center: JScrollPane + JTable detailTable (属性 | 值)
```

---

### 2.8 高级导出 — AdvancedExportDialog

```
public class AdvancedExportDialog extends BaseToolDialog
```

**构造器签名:** `AdvancedExportDialog(Frame owner, TableModel sourceModel)`

**功能:** 将查询结果导出为多种格式，支持同步预览和异步后台导出

#### 字段

| 字段 | 类型 | 访问 | 说明 |
|---|---|---|---|
| `sourceModel` | `TableModel` | `private final transient` | 数据源 |
| `outputArea` | `JTextArea` | `private final` | 导出预览（不可编辑，等宽字体） |
| `formatCombo` | `JComboBox<String>` | `private final` | 导出格式: INSERT / CSV / JSON / XML / Markdown |
| `dialectCombo` | `JComboBox<String>` | `private final` | 数据库方言: Oracle / MySQL / PostgreSQL / ANSI SQL |
| `tableNameField` | `JTextField` | `private final` | 表名（仅 INSERT 格式可用） |
| `headerCb` | `JCheckBox` | `private final` | "包含列头"（默认选中） |
| `charsetCombo` | `JComboBox<String>` | `private final` | 编码: UTF-8 / GBK / ISO-8859-1 / UTF-16 |
| `selectedColumns` | `List<Integer>` | `private final` | 已选的列索引列表 |
| `dateFormatField` | `JTextField` | `private final` | 日期格式模板（默认 `yyyy-MM-dd HH:mm:ss`） |
| `nullPlaceholder` | `JTextField` | `private final` | NULL 占位符（默认 `NULL`） |
| `maxBlobSize` | `JSpinner` | `private final` | BLOB 最大输出长度（KB，默认 64） |
| `theme` | `ThemeManager` | `private final` | 主题管理器 |

#### 数据库列类型 → INSERT 格式映射（按方言）

| JDBC 类型 | Oracle | MySQL | PostgreSQL | ANSI SQL |
|---|---|---|---|---|
| `NUMERIC` / `INTEGER` / `FLOAT` / `DOUBLE` / `DECIMAL` | `42` | `42` | `42` | `42` |
| `VARCHAR` / `CHAR` / `NVARCHAR2` / `NCHAR` / `TEXT` | `'value'` (单引号转义) | `'value'` (单引号转义) | `'value'` (单引号转义) | `'value'` (单引号转义) |
| `DATE` | `TO_DATE('2024-01-01','YYYY-MM-DD')` | `'2024-01-01'` | `DATE '2024-01-01'` | `DATE '2024-01-01'` |
| `TIMESTAMP` / `TIMESTAMP WITH TIME ZONE` | `TO_TIMESTAMP('2024-01-01 12:00:00','YYYY-MM-DD HH24:MI:SS')` | `'2024-01-01 12:00:00'` | `TIMESTAMP '2024-01-01 12:00:00'` | `TIMESTAMP '2024-01-01 12:00:00'` |
| `CLOB` / `NCLOB` / `LONG` | `'value'` (超阈值截断+提示) | `'value'` | `'value'` | `'value'` |
| `BLOB` / `RAW` / `VARBINARY` / `BYTEA` | `HEXTORAW('AB12')` | `X'AB12'` | `'\xAB12'::bytea` | 不支持直接 INSERT |

**CSV / JSON / XML / Markdown 格式（所有方言统一）:**

| JDBC 类型 | CSV | JSON | XML | Markdown |
|---|---|---|---|---|
| 数字类型 | `42` | `42` | `<c>42</c>` | `42` |
| 字符串类型 | `"value"` (双引号转义) | `"value"` (JSON 转义) | 实体转义 | `value` |
| 日期/时间戳 | ISO 字符串 | ISO 字符串 | ISO 字符串 | ISO 字符串 |
| BLOB / RAW | base64 | base64 | base64 | 十六进制 |
| BOOLEAN | `true`/`false` | `true`/`false` | `true`/`false` | `true`/`false` |
| NULL | `""` 或 `null`（可配置） | `null` | 空标签 | 空 |

#### 方言切换行为

dialectCombo 切换时：
- 仅影响 INSERT 格式的日期/时间戳/BLOB 函数调用
- CSV / JSON / XML / Markdown 格式不受影响
- 切换后自动刷新 doExport() 预览

#### 方法

| 方法 | 签名 | 说明 |
|---|---|---|
| `doExport` | `private void doExport()` | 同步导出预览 |
| `doExportAsync` | `private void doExportAsync()` | 提交异步导出任务到 ExportTaskList |
| `exportInsert` | `private String exportInsert()` | 生成 INSERT 语句 |
| `exportCsv` | `private String exportCsv()` | 生成 CSV |
| `exportJson` | `private String exportJson()` | 生成 JSON |
| `exportXml` | `private String exportXml()` | 生成 XML |
| `exportMarkdown` | `private String exportMarkdown()` | 生成 Markdown |
| `formatCellValue` | `private String formatCellValue(Object, int)` | 按列类型格式化单元格值 |
| `escapeXml` | `private static String escapeXml(String)` | XML 5 个预定义实体转义 |
| `applyTheme` | `private void applyTheme()` | 设置背景色 |

#### UI 布局

```
JDialog ("高级导出", setSizeRatio(0.75))
  └─ BorderLayout
       ├─ North: BorderLayout
       │    ├─ North: GridBagLayout + TitledBorder("导出设置")
       │    │    ├─ Row 0:
       │    │    │    (0,0) "导出格式:"  (1,0) [formatCombo, weightx=0.3]
       │    │    │    (2,0) "表名:"      (3,0) [tableNameField, weightx=0.5]
       │    │    │    (4,0) ☐ 包含列头  (5,0) "编码:"  (6,0) [charsetCombo, weightx=0.3]
│    │    ├─ Row 1:
│    │    │    (0,1) "方言:"      (1,1) [dialectCombo(Oracle/MySQL/PostgreSQL/ANSI), weightx=0.3]
│    │    │    (2,1) "日期格式:"  (3,1) [dateFormatField("yyyy-MM-dd HH:mm:ss"), weightx=0.5]
│    │    │    (4,1) "NULL值:"    (5,1) [nullPlaceholder("NULL"), weightx=0.3]
│    │    ├─ Row 2:
│    │    │    (0,2) "BLOB上限:"  (1,2) [maxBlobSize(64 KB), weightx=0.3]
       │    │    └─ formatCombo 切换时: 仅 INSERT 启用 tableNameField
       │    └─ South: JLabel("选择要导出的列，切换格式自动预览 | INSERT 格式需填写表名 | 大数据量建议使用异步导出")
       │        (Font("Dialog", PLAIN, 11), bg=bg.panel, fg=fg.muted, opaque)
       └─ Center: JSplitPane (水平, resizeWeight=0.25)
            ├─ Left:  wrapTitled("选择导出列", JScrollPane + JPanel 列 CheckBox 面板)
            │         每个列名一个 JCheckBox（默认全选），切换时自动更新预览
            └─ Right: wrapTitled("导出预览", JScrollPane + JTextArea outputArea)
       └─ South: FlowLayout(RIGHT)
            ├─ [同步导出]     → 复制到剪贴板（< 1000 行直接预览；否则提示是否异步）
            ├─ [异步导出]     → 提交到 ExportTaskList
            └─ [保存文件...]   → JFileChooser 保存，使用 charsetCombo 编码
```

#### 交互规则

| 事件 | 行为 |
|---|---|
| formatCombo 切换 | 自动刷新 doExport()，INSERT 时启用 tableNameField+dialectCombo，其他禁用 |
| dialectCombo 切换 | 自动刷新 doExport()（仅影响 INSERT 格式的日期/时间戳/BLOB 函数） |
| 列 CheckBox 切换 | 更新 selectedColumns，自动刷新 doExport() |
| tableNameField 文本变化 | 自动刷新 doExport() |
| dateFormatField / nullPlaceholder / maxBlobSize 变化 | 自动刷新 doExport() |
| [同步导出] | outputArea 内容复制到系统剪贴板 + ToastManager.show("已复制到剪贴板") |
| [同步导出] 且行数 > 1000 | 弹出确认: "数据量较大（N 行），是否改为异步导出？[同步] [异步] [取消]" |
| [异步导出] | 创建 ExportTask 提交到 ExportTaskListDialog，打开任务列表 |
| [保存文件...] | JFileChooser 另存为，写入文件 + ToastManager.show("已保存到: xxx") |
| headerCb 切换 | CSV/Markdown 控制是否输出列头行，自动刷新 |

---

### 2.9 导出任务列表 — ExportTaskListDialog

```
public class ExportTaskListDialog extends BaseToolDialog
```

**构造器签名:** `ExportTaskListDialog(Frame owner)`

**功能:** 管理异步导出任务

**设计说明:** 单例（整个应用共享一个实例），第一次调用时创建，后续 `setVisible(true)`。

#### ExportTask 内部类

```java
static class ExportTask {
    String id;
    String name;              // 显示名称: "导出 my_table (INSERT, 5000行)"
    String format;             // INSERT/CSV/JSON/XML/Markdown
    volatile String status;    // "排队中" / "执行中" / "已完成" / "失败"
    long startTime;            // System.currentTimeMillis()
    long endTime;              // 完成时记录
    int totalRows;
    int exportedRows;
    String filePath;           // 保存路径（完成时赋值）
    String errorMessage;       // 失败原因
    SwingWorker<Void, Void> worker;
}
```

#### 字段

| 字段 | 类型 | 访问 | 说明 |
|---|---|---|---|
| `taskModel` | `TaskTableModel` | `private final` | 任务表格模型 |
| `taskTable` | `JTable` | `private final` | 任务列表 |
| `tasks` | `List<ExportTask>` | `private final` | 全部任务 |

#### TaskTableModel 列定义

| 列 | 类型 | 宽度 | 说明 |
|---|---|---|---|
| 任务名 | `String` | 200 | "导出 my_table (INSERT, 5000行)" |
| 格式 | `String` | 80 | INSERT/CSV/JSON/XML/Markdown |
| 状态 | `String` | 80 | "排队中" ⏳ / "执行中" 🔄 / "已完成" ✅ / "失败" ❌ |
| 开始时间 | `String` | 150 | `2024-01-01 12:00:00` |
| 耗时 | `String` | 80 | `3.2s` 或 `--`（排队中） |
| 进度 | `String` | 80 | `1200/5000` 或 `100%` |
| 操作 | `JButton` | 100 | "打开文件"(已完成) / "重试"(失败) / "取消"(排队中/执行中) |

#### 方法

| 方法 | 签名 | 说明 |
|---|---|---|
| `getInstance` | `static synchronized ExportTaskListDialog getInstance(Frame owner)` | 单例获取 |
| `submitTask` | `public void submitTask(TableModel model, String format, List<Integer> columns, String tableName, boolean header, Charset charset, String dateFormat, String nullPlaceholder, int maxBlobSize)` | 创建并执行任务 |
| `cancelTask` | `private void cancelTask(int row)` | 取消排队中/执行中的任务 |
| `retryTask` | `private void retryTask(int row)` | 重试失败任务 |
| `openFile` | `private void openFile(int row)` | 打开已完成任务的文件 |
| `clearCompleted` | `private void clearCompleted()` | 清除所有已完成/失败的任务 |
| `updateProgress` | `private void updateProgress(int row, int exported, int total)` | 更新进度 |
| `completeTask` | `private void completeTask(int row, String filePath)` | 标记完成 |
| `failTask` | `private void failTask(int row, String error)` | 标记失败 |

#### UI 布局

```
JDialog ("导出任务列表", 700x350, MODELESS)
  └─ BorderLayout
       ├─ Center: JScrollPane + JTable taskTable
       │         列: [任务名] [格式] [状态] [开始时间] [耗时] [进度] [操作]
       │         渲染: 状态列用彩色标签，操作列渲染为 JButton
       └─ South: FlowLayout(RIGHT)
            ├─ [清除已完成]
            └─ [关闭]
```

#### 异步导出流程

```
[用户点击"异步导出"]
  → submitTask()
  → 创建 ExportTask(status="排队中", startTime=now)
  → taskModel.addRow(task)
  → 创建 SwingWorker:
       doInBackground():
         status = "执行中"
         // 逐行读取 sourceModel，按 format 生成文本
         // 每 100 行 publish() 更新进度
         // 写入临时文件
         return filePath
       done():
         if (success) status="已完成", filePath, ToastManager.show(...)
         else status="失败", errorMessage, ToastManager.showError(...)
  → worker.execute()
```

---

## 三、MainFrame.java 改动清单

### 3.1 新增 import

```java
import com.kylin.plsql.ui.component.ToastManager;
import com.kylin.plsql.ui.dialog.BaseToolDialog;
import com.kylin.plsql.ui.dialog.SqlToolsDialog;
import com.kylin.plsql.ui.dialog.SqlFormatDialog;
import com.kylin.plsql.ui.dialog.DataGeneratorDialog;
import com.kylin.plsql.ui.dialog.SqlHistoryDialog;
import com.kylin.plsql.ui.dialog.TextDiffDialog;
import com.kylin.plsql.ui.dialog.RegexTesterDialog;
import com.kylin.plsql.ui.dialog.ObjectSearchDialog;
import com.kylin.plsql.ui.dialog.AdvancedExportDialog;
import com.kylin.plsql.ui.dialog.ExportTaskListDialog;
```

### 3.2 菜单插入代码

于 `viewMenu` 与 `helpMenu` 之间插入：

```java
JMenu toolsMenu = new JMenu("\u5DE5\u5177");

JMenuItem sqlToolsItem = new JMenuItem("SQL \u5DE5\u5177");
sqlToolsItem.addActionListener(e -> new SqlToolsDialog(MainFrame.this).setVisible(true));
toolsMenu.add(sqlToolsItem);

JMenuItem sqlFmtItem = new JMenuItem("SQL \u683C\u5F0F\u5316");
sqlFmtItem.addActionListener(e -> new SqlFormatDialog(MainFrame.this, formatOptions).setVisible(true));
toolsMenu.add(sqlFmtItem);

JMenuItem dataGenItem = new JMenuItem("\u6570\u636E\u751F\u6210\u5668");
dataGenItem.addActionListener(e -> showDataGeneratorDialog());
toolsMenu.add(dataGenItem);

JMenuItem sqlHistItem = new JMenuItem("SQL \u5386\u53F2");
sqlHistItem.addActionListener(e -> showSqlHistoryDialog());
toolsMenu.add(sqlHistItem);

JMenuItem diffItem = new JMenuItem("\u6587\u672C\u6BD4\u8F83");
diffItem.addActionListener(e -> new TextDiffDialog(MainFrame.this).setVisible(true));
toolsMenu.add(diffItem);

JMenuItem regexItem = new JMenuItem("\u6B63\u5219\u6D4B\u8BD5\u5668");
regexItem.addActionListener(e -> new RegexTesterDialog(MainFrame.this).setVisible(true));
toolsMenu.add(regexItem);

JMenuItem objSearchItem = new JMenuItem("\u5BF9\u8C61\u641C\u7D22");
objSearchItem.addActionListener(e -> showObjectSearchDialog());
toolsMenu.add(objSearchItem);

toolsMenu.addSeparator();

JMenuItem advExportItem = new JMenuItem("\u9AD8\u7EA7\u5BFC\u51FA");
advExportItem.addActionListener(e -> showAdvancedExportDialog());
toolsMenu.add(advExportItem);

menuBar.add(toolsMenu);
```

### 3.3 新增 helper 方法（4 个）

```java
private void showDataGeneratorDialog() {
    String[] conns = connectionManager.getActiveConnections();
    DataGeneratorDialog dlg = new DataGeneratorDialog(MainFrame.this,
        name -> { try { return connectionManager.getConnection(name); }
        catch (Exception ex) { return null; } });
    dlg.populateConnections(java.util.Arrays.asList(conns));
    dlg.setVisible(true);
}

private void showSqlHistoryDialog() {
    var list = sqlHistory.getAll();
    if (list.isEmpty()) { ToastManager.show(this, "\u6682\u65E0 SQL \u5386\u53F2\u8BB0\u5F55"); return; }
    SqlHistoryDialog dlg = new SqlHistoryDialog(MainFrame.this, list, sql -> {
        SqlEditorPanel editor = getActiveEditor();
        if (editor != null) editor.setText(sql);
    });
    dlg.setVisible(true);
}

private void showObjectSearchDialog() {
    String[] conns = connectionManager.getActiveConnections();
    ObjectSearchDialog dlg = new ObjectSearchDialog(MainFrame.this,
        name -> { try { return connectionManager.getConnection(name); }
        catch (Exception ex) { return null; } },
        (name, obj) -> ToastManager.show(MainFrame.this, "\u8DF3\u8F6C: " + name + "/" + obj));
    dlg.populateConnections(java.util.Arrays.asList(conns));
    dlg.setVisible(true);
}

private void showAdvancedExportDialog() {
    var rp = bottomPanel.getResultPanel();
    if (rp == null) { ToastManager.show(this, "\u6CA1\u6709\u7ED3\u679C\u96C6"); return; }
    var model = rp.getCurrentTableModel();
    if (model == null || model.getRowCount() == 0) { ToastManager.show(this, "\u7ED3\u679C\u96C6\u4E3A\u7A7A"); return; }
    new AdvancedExportDialog(MainFrame.this, model).setVisible(true);
}
```

---

## 四、不涉及的文件

以下现有文件**不做任何修改**：

- `CallHierarchyDialog.java` ✓
- `ConnectionDialog.java` ✓
- `SettingsDialog.java` ✓
- `GlobalSearchDialog.java` ✓
- 所有 component/ 下文件（除新增 `ToastManager.java`） ✓
- 所有 core/ 下文件 ✓

---

## 五、变更摘要

| 变更类型 | 内容 |
|---|---|
| **新增文件** | `ToastManager.java`, `BaseToolDialog.java`, `SqlToolsDialog.java`, `SqlFormatDialog.java`, `DataGeneratorDialog.java`, `SqlHistoryDialog.java`, `TextDiffDialog.java`, `RegexTesterDialog.java`, `ObjectSearchDialog.java`, `AdvancedExportDialog.java`, `ExportTaskListDialog.java` |
| **修改文件** | `MainFrame.java`（加 import + 菜单 + helper 方法，共约 80 行） |
| **主题感知通知** | 所有提示统一使用 `ToastManager.show()`，删除各文件的 `showToast()` 私有实现 |
| **窗口尺寸统一** | 全部使用 `setSizeRatio(0.75)` |
| **布局统一** | `applyTheme()` + `centerOnOwner()` 由基类构造器自动调用 |
| **输出区统一** | 所有 outputArea 不可编辑，仅供预览 |
| **方言感知** | DataGeneratorDialog + AdvancedExportDialog 均支持 Oracle/MySQL/PostgreSQL/ANSI SQL 四种方言，日期/时间戳/BLOB 字面量按方言输出 |
| **布局切换** | SqlFormatDialog + DataGeneratorDialog 支持水平/垂直分屏一键切换 |
| **TextDiffDialog 重构** | 结果区从 South 移入 JSplitPane(垂直)，与输入区构成可调节分割 |
