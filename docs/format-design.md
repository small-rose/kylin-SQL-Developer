# SQL/PLSQL 格式化功能设计方案

## 一、混合策略总览

采用双入口混合方案，根据编辑器类型自动选择格式化引擎：

```
用户触发 (Ctrl+Shift+F / 按钮)
       │
       ├── 当前激活的是 SqlEditorPanel
       │   └── → SQL Quick Formatter（增强 Token 流）
       │         SELECT/INSERT/UPDATE/DELETE/MERGE 专用
       │         快速、轻量、精确对齐
       │
       └── 当前激活的是 SourceViewerPanel
           └── → PL/SQL Full Formatter（嵌套栈 + 上下文分段）
                 包/过程/函数/TYPE 专用
                 处理 BEGIN/END/IF/LOOP/CASE/EXCEPTION
```

格式化前先检测当前连接数据库方言，加载对应的 **方言配置**（关键字集、特殊子句、标识符风格）。

---

## 二、总体架构

```
┌──────────────────────────────────────────────────────────┐
│                    Profile 预设管理                        │
│  ┌─ "默认 (Oracle)" ──┐  ┌─ "紧凑型" ──┐  ┌─ "..." ──┐ │
│  │ keywordCase: UPPER │  │ indent: 2   │  │          │ │
│  │ indent: 4          │  │ and: LINE_  │  │          │ │
│  │ and: LINE_START    │  │ START       │  │          │ │
│  └────────────────────┘  └─────────────┘  └──────────┘ │
│                  当前选中 Profile → 一组 FormatOptions   │
└──────────────────────────┬───────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────┐
│                    FormatOptions                          │
│  ┌──────────┬───────────┬──────────┬──────────┐          │
│  │ 通用设置  │ DML 设置  │ PLSQL 设│ DDL 设置 │          │
│  │ keyword   │ select    │ begin    │ column   │          │
│  │ case      │ align     │ then     │ def      │          │
│  │ indent    │ from/join │ loop     │ align    │          │
│  │ max width │ where and │ if/case  │ storage  │          │
│  │ line end  │ comma     │ decl     │          │          │
│  └──────────┴───────────┴──────────┴──────────┘          │
└──────────────────────────┬───────────────────────────────┘
                           │ 当前方言选择
┌──────────────────────────▼───────────────────────────────┐
│                   方言配置适配层                            │
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │  SqlDialect (接口)                                 │  │
│  │  ├── 关键字集 (KEYWORDS, INDENT_INCREASE, ...)      │  │
│  │  ├── 标识符引用风格 (双引号/反引号/无引号)          │  │
│  │  ├── 特殊子句列表 (LIMIT/OFFSET/RETURNING/... )     │  │
│  │  └── 默认格式化偏好                                 │  │
│  └────────────────────────────────────────────────────┘  │
│       ▲          ▲          ▲          ▲                  │
│       │          │          │          │                  │
│  ┌────┴───┐ ┌───┴────┐ ┌──┴────┐ ┌───┴─────┐            │
│  │Oracle  │ │OceanBase│ │MySQL  │ │PostgreSQL│            │
│  │Dialect │ │ Dialect │ │Dialect│ │ Dialect  │            │
│  │(默认)  │ │(同Oracle)│ │       │ │          │            │
│  └────────┘ └─────────┘ └───────┘ └──────────┘            │
└──────────────────────────┬───────────────────────────────┘
                           │
     ┌─────────────────────┼─────────────────────┐
     ▼                     ▼                     ▼
┌────────────┐     ┌──────────────┐     ┌────────────────┐
│ 词法分析器  │     │ SQL格式化器   │     │ PLSQL格式化器   │
│ (Lexer)    │     │ SqlFormatter │     │ PlSqlFormatter  │
│ ANTLR      │ ──→ │ DML 专用     │     │ 嵌套栈+分段     │
│ PlSqlLexer │     │ 对齐+缩进    │     │                │
│ 共享实例   │     │ 读取方言配置  │     │ 读取方言配置    │
└────────────┘     └──────────────┘     └────────────────┘
```

---

## 三、方言配置层

### 3.1 设计

方言配置不是独立解析器，而是一系列 **配置化参数**，格式化引擎根据当前激活的方言加载对应配置。

```java
public interface SqlDialect {
    String getName();                          // "Oracle", "MySQL", ...
    Set<String> getKeywords();                 // 方言关键字集
    Set<String> getIndentIncrease();           // 缩进增加关键字
    Set<String> getIndentDecrease();           // 缩进减少关键字
    String quoteIdentifier(String name);       // 标识符引用（"name" / `name` / name）
    List<SpecialClause> getSpecialClauses();   // 特殊子句（LIMIT, RETURNING 等）
    FormatOptions getDefaultOptions();         // 该方言的默认格式化偏好
}

// 内置方言
SqlDialect ORACLE     = new OracleDialect();     // 默认
SqlDialect OCEANBASE  = new OceanBaseDialect();  // 同 Oracle
SqlDialect MYSQL      = new MySqlDialect();
SqlDialect PG         = new PostgreSqlDialect();
```

### 3.2 方言差异示例

| 特性 | Oracle | MySQL | PostgreSQL |
|------|--------|-------|------------|
| 标识符引用 | `"name"` | `` `name` `` | `"name"` |
| SELECT 限制 | `WHERE ROWNUM` | `LIMIT n OFFSET m` | `LIMIT n OFFSET m` |
| INSERT 扩展 | 无 | `ON DUPLICATE KEY` | `ON CONFLICT DO` |
| UPDATE 扩展 | 无 | `LIMIT` | `RETURNING` |
| DELETE 扩展 | 无 | `LIMIT` | `RETURNING` |
| MERGE | 支持 | 不支持 | 不支持 |
| 关键字集 | PL/SQL 全量 | MySQL 专属 | PG 专属 |

### 3.3 方言检测

```java
// 根据连接自动检测（由 ConnectionManager.getDbType() 驱动）
SqlDialect dialect = DialectManager.detect(conn);  // 自动
// 或手动选择（Settings 中可覆盖）
SqlDialect dialect = DialectManager.forName("MySQL");
```

方言选择存储在每个连接的 ConnectionInfo 中，也可在格式化时通过连接名查询。

---

## 四、两个格式化入口设计

### 4.1 Entry 1: SQL Quick Formatter（增强现有 SqlFormatter）

**适用场景**：SqlEditorPanel 中的 SQL 语句

**现有基础**：`SqlFormatter.java`（215行，ANTLR Lexer + 关键字识别）

**增强计划**：

| 改进项 | 当前 | 目标 |
|--------|------|------|
| 子句识别 | 仅 SELECT | SELECT/INSERT/UPDATE/DELETE/MERGE |
| FROM/JOIN | 无处理 | 独立行、ON 对齐 |
| WHERE/AND | 无处理 | AND 行首/行尾可选、条件对齐 |
| INSERT 列 | 无处理 | 列列表对齐 |
| UPDATE SET | 无处理 | SET 列=值对齐 |
| 逗号换行 | 仅 SELECT 列表 | 可配置（结尾/开头）|
| 函数调用 | 无处理 | 括号内空格控制 |
| 注释保留 | 有（基本） | 保留位置 + 对齐 |
| 方言适配 | 硬编码 Oracle | 读取 SqlDialect |

**核心算法**（保持 Token 流方式）：

```
Token 流 → 加载 SqlDialect → 上下文检测器 → DML 子句路由
  ├── SELECT 段 → SELECT 对齐器（列/FROM/WHERE + 方言特殊子句）
  ├── INSERT 段 → INSERT 格式化器（列列表/VALUES + 方言扩展）
  ├── UPDATE 段 → UPDATE 格式化器（SET 对齐 + RETURNING）
  └── DELETE 段 → DELETE 格式化器（WHERE 对齐 + LIMIT）
```

**性能要求**：< 50ms 对任意 SQL

### 4.2 Entry 2: PL/SQL Full Formatter（新增）

**适用场景**：SourceViewerPanel 中的包/过程/函数/TYPE

**新类**：`PlSqlFormatter.java`

**核心改进**：

#### 4.2.1 嵌套深度跟踪（栈，非计数器）

当前使用计数器（`indent++`/`indent--`），问题是 ELSE 会抵消 IF 的缩进，END 不能区分关闭哪个结构。

改为栈：

```
栈结构示例:
[CASE]           → 缩进 +1
[CASE, WHEN]     → 缩进 +2
[CASE, WHEN, IF] → 缩进 +3 (IF 嵌套在 WHEN 内)
[WHEN, IF]       → 遇到 THEN? 缩进不变
[CASE, WHEN]     → END IF 弹出 IF
[CASE]           → END CASE 弹出 CASE
```

#### 4.2.2 上下文分段

根据关键字将代码划分为段落，每段有独立格式化规则：

| 段落类型 | 检测关键字 | 格式化规则 |
|----------|-----------|-----------|
| **头部段** | CREATE OR REPLACE | 参数列表换行、IS/AS 对齐 |
| **声明段** | IS/AS 后 → DECLARE/BEGIN | 变量 := 对齐、TYPE 声明格式 |
| **可执行段** | BEGIN ... END | IF/CASE/LOOP 缩进、赋值对齐 |
| **异常段** | EXCEPTION | WHEN 缩进、OTHERS 格式 |
| **子程序段** | FUNCTION/PROCEDURE（嵌套）| 嵌套子程序独立格式化 |
| **SQL 段** | SELECT/INSERT/UPDATE（PLSQL 内）| 复用 SqlFormatter 规则 |

#### 4.2.3 结构识别

新增识别和正确缩进的 PL/SQL 结构：

| 结构 | 缩进策略 | 关闭关键字 |
|------|---------|-----------|
| IF-THEN | THEN 后 +1 | END IF |
| IF-THEN-ELSE | ELSE 对齐 IF, ELSE 后 +1 | END IF |
| CASE-WHEN | CASE +1, WHEN 对齐, WHEN 后 +1 | END CASE |
| FOR-LOOP | LOOP 后 +1 | END LOOP |
| WHILE-LOOP | LOOP 后 +1 | END LOOP |
| LOOP | 直接 +1 | END LOOP |
| BEGIN-EXCEPTION | BEGIN +1, EXCEPTION 对齐 | END |
| DECLARE | 声明区 +1 | BEGIN (过渡) |
| FUNCTION/PROCEDURE | IS/AS 后 +1 | END [name] |

---

## 五、FormatOptions + Profile 预设

### 5.1 FormatOptions 分组与字段

```
┌─ 通用 ───────────────────────────────────────────────┐
│  keywordCase: UPPER / LOWER / PRESERVE                │ (已有)
│  indentSize: 1-8, 默认 4                              │ (已有)
│  maxLineWidth: 0=不限制 / 80 / 100 / 120              │ (新增)
│  lineEnding: LF / CRLF                                │ (新增)
├─ DML ─────────────────────────────────────────────────┤
│  selectColumnMode: ALIGN / COMPACT / ONE_PER_LINE     │ (新增)
│  fromClauseNewline: true / false                      │ (新增)
│  joinOnNewline: true / false                          │ (新增)
│  joinOnAlign: true / false                            │ (新增, ON 对齐)
│  whereAndPosition: LINE_START / LINE_END              │ (新增)
│  commaPosition: TRAILING / LEADING                    │ (新增)
│  insertColumnMode: COMPACT / ONE_PER_LINE             │ (新增)
│  updateSetAlign: true / false                         │ (新增)
├─ PLSQL ───────────────────────────────────────────────┤
│  thenOnNewLine: false / true                          │ (新增, THEN 是否换行)
│  loopOnNewLine: false / true                          │ (新增, LOOP 是否换行)
│  elseOnNewLine: true / false                          │ (新增, ELSE 是否单独行)
│  exceptionAlign: INDENT / OUTDENT                     │ (新增)
│  declarationAlign: true / false                       │ (新增, := 对齐)
│  parameterListMode: COMPACT / ONE_PER_LINE            │ (新增)
│  parenthesisSpacing: NONE / INSIDE / BOTH             │ (新增)
├─ DDL ─────────────────────────────────────────────────┤
│  columnDefAlign: true / false                         │ (新增, TYPE/列对齐)
│  storageClauseFormat: COMPACT / LINE_BREAK            │ (新增)
└───────────────────────────────────────────────────────┘
```

### 5.2 Profile 预设管理

Profile 是**一组 FormatOptions 的命名快照**。目的是保存多套配置方案，一键切换。

```java
public class FormatOptions {
    // ── 实际格式化选项（同上 20+ 字段）──

    // ── Profile 管理 ──
    private String activeProfile = "默认 (Oracle)";
    private Map<String, FormatOptions> profiles = new LinkedHashMap<>();

    // 默认预设（初始化时创建）
    public void initDefaults() {
        profiles.put("默认 (Oracle)",   createOracleDefault());
        profiles.put("紧凑型",          createCompactProfile());
        profiles.put("宽排版",          createWideProfile());
    }

    // 切换到指定 Profile
    public void switchTo(String name) {
        FormatOptions target = profiles.get(name);
        if (target != null) {
            copyFrom(target);
            activeProfile = name;
        }
    }

    // 当前配置另存为新 Profile
    public void saveAs(String name) {
        profiles.put(name, snapshot());
        activeProfile = name;
    }

    // 导入/导出
    public String exportProfile(String name) { /* → JSON 字符串 */ }
    public void importProfile(String name, String json) { /* ← JSON 字符串 */ }

    // 序列化（存到 WorkspaceState）
    public Map<String, String> toMap() { ... }
    public static FormatOptions fromMap(Map<String, String> map) { ... }
}
```

### 5.3 Profile 使用场景

| 场景 | 操作 |
|------|------|
| 个人多项目 | 下拉切换「项目A规范」/「项目B规范」|
| 团队统一 | 张三导出 `.json` → 李四导入 |
| 方言切换 | Oracle 用一套、MySQL 用另一套（自动或手动）|

### 5.4 方言与 Profile 的关系

- **方言**控制的是"引擎能识别什么语法"（关键字集、特殊子句）
- **Profile**控制的是"格式化成什么样子"（缩进、大小写、对齐方式）
- 两者正交：同一方言可以有多个 Profile，同一 Profile 也可以应用于不同方言

---

## 六、设置面板设计

### 6.1 当前（单列 3 个控件）

```
┌─ SQL 格式化 ──────┐
│ 关键字大小写: [▼]  │
│ 缩进空格数:   [4]  │
│ SELECT 列对齐: [√] │
└────────────────────┘
```

### 6.2 改后（Preset 下拉 + JTabbedPane 四标签页 + 预览）

```
┌─ SQL 格式化 ──────────────────────────────────────────────┐
│ [配置方案: 默认 (Oracle) ▼]  [▼ 另存为] [导入] [导出]     │
├────────────────────────────────────────────────────────────┤
│ [通用]  [DML]  [PL/SQL]  [DDL]                            │
├────────────────────────────────────────────────────────────┤
│ 关键字大小写:    [UPPER       ▼]   最大行宽: [120 ▲ ▼]   │
│ 缩进空格数:      [4 ▲ ▼]          换行符: [LF        ▼]  │
│                                                            │
│ ── 预览 ────────────────────────────────────────────────  │
│ SELECT  a                                                  │
│       ,b                                                   │
│   FROM  t                                                  │
│  WHERE  x = 1                                              │
│    AND  y = 2                                              │
│                                                            │
│ [恢复默认值]                    [当前数据库方言: Oracle]   │
└────────────────────────────────────────────────────────────┘
```

每个标签页：
- 两列 GridBagLayout（标签 + 控件）
- 底部实时预览区域（JTextArea，只读）
- 预览内容随选项变化即时更新
- 切换 Profile 时所有选项 + 预览同步切换
- 左下角显示当前连接检测到的方言

---

## 七、集成入口

### 7.1 MainFrame.formatSql() 升级

```java
private void formatSql() {
    Component editor = editorTabs.getSelectedComponent();
    if (editor == null) return;

    // 检测当前连接的方言
    SqlDialect dialect = detectCurrentDialect();
    // 加载当前 Profile 的格式化选项
    FormatOptions options = formatOptionsManager.resolve(dialect);
    //                     ↑ 可按（方言+Profile名）匹配最佳 Profile

    if (editor instanceof SqlEditorPanel sqlEditor) {
        String sql = sqlEditor.getSelectedText();
        if (sql == null || sql.isBlank()) sql = sqlEditor.getText();
        String formatted = SqlFormatter.format(sql, options, dialect);
        sqlEditor.replaceSelection(formatted);
        statusLabel.setText("格式化完成");

    } else if (editor instanceof SourceViewerPanel sourceViewer) {
        if (!sourceViewer.isEditable()) {
            statusLabel.setText("只读模式下无法格式化，请先点击 E 进入编辑");
            return;
        }
        String source = sourceViewer.getTextArea().getText();
        String formatted = PlSqlFormatter.format(source, options,
            sourceViewer.getObjectType());
        sourceViewer.getTextArea().setText(formatted);
        statusLabel.setText("格式化完成");
    }
}
```

### 7.2 新增入口

| 位置 | 类型 | 触发 |
|------|------|------|
| SourceViewerPanel 编辑模式下右键 | 右键菜单「格式化 (Ctrl+Shift+F)」| 格式化全文 |
| ObjectBrowser → 查看 DDL 对话框 | 按钮「格式化 DDL」 | 对 DDL 文本应用 DDL 格式化器 |
| 工具栏 ⚙ 按钮（已有）| 保持不变 | 自动路由到对应入口 |
| Ctrl+Shift+F（已有）| 快捷键 | 同上 |

---

## 八、持久化方案

### 8.1 WorkspaceState 扩展

```java
public static class WorkspaceState {
    // 已有
    public int lastActiveIndex;
    public String theme = "DARK";
    public Map<String, String> colorOverrides = new HashMap<>();
    public List<TabState> tabs = new ArrayList<>();

    // 新增
    public String activeFormatProfile = "默认 (Oracle)";          // 当前选中 Profile
    public Map<String, Map<String, String>> formatProfiles;       // name → option map
    public Map<String, String> connectionDialects;                // 连接名 → 方言名（可选覆盖）
}
```

### 8.2 方言选择持久化

每个连接可手选方言（在连接属性或 Settings 中设置），存储在 `connectionDialects`，自动检测的方言不存储：

```
connectionDialects: {
    "MyOracleConn": "Oracle",
    "MyOceanBaseConn": "Oracle",   // OceanBase Oracle 模式归 Oracle
    "MyMySQLConn": "MySQL",
    "MyPGConn": "PostgreSQL"
}
```

未在 map 中的连接使用自动检测。

### 8.3 保存时机

- `SettingsDialog` 点「OK」：保存当前 Profile + 所有 Profiles + 方言覆盖
- `MainFrame.saveWorkspace()`：同上
- Profile 切换或另存为：立即保存

### 8.4 加载时机

- `MainFrame` 初始化时：恢复 activeProfile + profiles + connectionDialects
- `SettingsDialog` 打开时：加载当前 Profiles 列表
- 格式化触发时：根据当前连接获取方言 + 对应 Profile

---

## 九、实施路线图

| 阶段 | 内容 | 工作量 |
|------|------|--------|
| 阶段 | 内容 | 工作量 | 状态 |
|------|------|--------|------|
| **1A** | `FormatOptions` 扩展（全部字段 + Profile 管理 + toMap/fromMap）| ~1天 | ✅ 完成 |
| **1B** | `SqlDialect` 接口 + 四种方言配置（Oracle/OB/MySQL/PG）| ~1天 | ✅ 完成 |
| **1C** | `SettingsDialog` 格式化面板改造（Profile 下拉 + 四标签页 + 预览）| ~1天 | ✅ 完成 |
| **1D** | 配置持久化（WorkspaceState + save/load）| ~半天 | ✅ 完成 |
| **2A** | `SqlFormatter` 增强（方言适配 + DML 子句对齐）| ~2天 | 🔄 进行中（方言重载已加，DML 增强待续）|
| **2B** | `PlSqlFormatter` 新建（嵌套栈 + 上下文分段 + 控制结构）| ~3天 | ⏳ 待开始 |
| **3A** | 集成（MainFrame 路由 + 右键菜单 + DDL 格式化）| ~1天 | 🔄 进行中（路由 + SqlEditorPanel 右键完成）|
| **3B** | 测试 + 边界情况处理 | ~1天 | ⏳ 待开始 |

**总计：约 10-11 人天**（已实施约 4 天）

### Phase 1A-1D 实施总结

| 模块 | 文件 | 变更说明 |
|------|------|----------|
| 枚举类 | 7 个新文件 | `SelectColumnMode`, `WhereAndPosition`, `CommaPosition`, `ParameterListMode`, `ExceptionAlign`, `ParenthesisSpacing`, `StorageClauseFormat` |
| FormatOptions | `format/FormatOptions.java` | 21 字段 + `initDefaults()` 三预设 + `switchTo/saveAs/delete/export/import` + `toMap/fromMap/profilesToMap/profilesFromMap` |
| 方言接口 | `format/dialect/SqlDialect.java` | 接口定义（keywords/indentIncreaseDecrease/quoteIdentifier/SpecialClauses/defaultOptions）|
| Oracle 方言 | `format/dialect/OracleDialect.java` | ~150 关键字 + PL/SQL 控制结构缩进 |
| OceanBase 方言 | `format/dialect/OceanBaseDialect.java` | 继承 OracleDialect |
| MySQL 方言 | `format/dialect/MySqlDialect.java` | 反引号引用 + LIMIT/OFFSET 子句 + 2空格缩进 |
| PostgreSQL 方言 | `format/dialect/PostgreSqlDialect.java` | ILIKE/RETURNING/ON CONFLICT + LIMIT/OFFSET |
| DialectManager | `format/dialect/DialectManager.java` | 注册/按名称查询/按 Connection 自动检测 |
| ConfigManager | `config/ConfigManager.java` | WorkspaceState 新增 `formatProfiles`/`activeFormatProfile`/`connectionDialects` |
| SettingsDialog | `ui/dialog/SettingsDialog.java` | Profile 工具栏（另存为/删除/导入/导出）+ 4 标签页（通用/DML/PLSQL/DDL）共 21 控件 |
| SqlFormatter | `format/SqlFormatter.java` | 新增 `format(source, options, dialect)` 重载 |
| MainFrame | `ui/MainFrame.java` | `formatSql()` 方言路由 + WorkspaceState 持久化 Profile/方言 + `setOnFormat` 注册 |
| SqlEditorPanel | `ui/component/SqlEditorPanel.java` | `onFormat` 回调 + 右键菜单「格式化」项 |
| BottomPanel | `ui/component/BottomPanel.java` | `connectionDialects` 字段及 getter/setter |

---

### 近期 Bugfix

| 问题 | 根因 | 修复 |
|------|------|------|
| SqlFormatter 初始化崩溃 (NoClassDefFoundError) | `Set.of()` 中包含重复元素 `"IN"`（第13行和第26行各一个） | 删除第26行重复 `"IN"` |
| 结果区背景不跟随主题 | `ResultPanel.applyTheme()` 只遍历 JScrollPane，漏掉 JPanel 结果标签及其内部 JTable | 新增 `applyComponentTreeTheme()` 递归遍历所有子组件并更新 JPanel/JTable/JTableHeader/JViewport/JToolBar |
| ALL_SCHEMA 弹窗不同步 | ALL_SCHEMA 只改 hidden 集合不更新单项；单项也不更新 ALL_SCHEMA | 收集所有 schema checkbox 到列表，双向同步：ALL_SCHEMA 变则遍历设置单项；单项变则检查全选中状态 |
| 连接树背景不随主题变 | ObjectBrowser 无 `applyTheme()` 方法；连接节点渲染器用硬编码 `UIManager.getColor()` + fallback 色值 | ObjectBrowser 新增 `applyTheme()`；LeftPanel 存储 browser 引用并调用；渲染器改用 `theme.resolve()` 色键 |
| 连接树默认 schema 不自动展开 | `rebuildConnectionNode` 只展开到连接层级 | 遍历子节点找到默认 schema 后额外调用 `expandPath` |

### 标签页图标

编辑器标签页和结果集标签页现在显示方块字母图标（16x16 圆角方框 + 白色字母），风格与左侧 ObjectBrowser 树节点图标一致。

**编辑器标签：**

| 标签类型 | 图标 | 颜色 |
|---|---|---|
| SqlEditorPanel (SQL 编辑器/控制台) | S | 蓝 `0x337AB7` |
| SourceViewer PACKAGE | K | 棕 `0xA0522D` |
| SourceViewer PROCEDURE | P | 红 `0xD9534F` |
| SourceViewer FUNCTION | F | 红 `0xD9534F` |
| SourceViewer 其他 | V | 青 `0x5BC0DE` |

**结果集标签：**

| 标签名来源 | 图标 | 颜色 |
|---|---|---|
| 表名（`guessLabel` 成功） | T | 蓝 `0x337AB7` |
| 默认（result1/result2...） | R | 蓝 `0x337AB7` |

### 关闭按钮效果

- 默认态：灰色 `×` 无背景
- 鼠标悬浮：`#E81123` 红色实心圆形背景 + 白色 `×`
- 使用 `getModel().isRollover()` + 自定义 `paintComponent` 实现
- 标签页组件布局：`icon → 2px → label → 2px → glue → closeBtn → 2px`，使关闭按钮紧贴右侧

---

## 十、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 方言支持 | 一套引擎 + 方言配置（非独立解析器）| 复用 80% 共性逻辑，低成本覆盖 4 方言 |
| Profile 预设 | 命名快照方案（Map<String, FormatOptions>）| 实现简单，用户直观 |
| 方言检测 | 自动检测 + 手动覆盖 | 自动省事，手动兜底 |
| 格式化引擎 | Token 流 + 嵌套栈（非 AST）| OceanBase 兼容性好，语法错误不崩溃 |
| 双入口路由 | 按编辑器类型自动选择 | 对用户透明，一个按钮处理所有场景 |
| 配置存储 | WorkspaceState JSON | 与现有持久化机制一致 |
| 预览 | 设置面板内实时预览 | 所见即所得，降低配置学习成本 |
| PLSQL 对象类型传参 | 传给 PlSqlFormatter 辅助检测 | PACKAGE/BODY/FUNCTION 头部格式不同 |
| 注释处理 | 保留原文位置 + 可选对齐 | 不破坏手写注释 |
