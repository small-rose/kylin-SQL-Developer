# Kylin PL/SQL Developer - 进度日志

## M1: 核心骨架 ✅ (2026-06-22)

### 完成项
- [x] Maven 多模块项目初始化 (parent + core + ui + assembly)
- [x] FlatLaf 主题集成
- [x] 主窗口 PL/SQL Developer 经典布局
  - 左侧: 对象浏览器
  - 中间: SQL 编辑器 + 底部结果集 (上下拆分)
  - 右侧: 大纲视图
- [x] 菜单栏 + 工具栏 + 状态栏
- [x] 连接管理对话框 (新建/编辑/保存/测试/删除)
- [x] HikariCP 连接池管理
- [x] SQL 执行器 (SELECT/DML 全支持)
- [x] 结果集表格展示 + 错误消息展示
- [x] 配置文件管理 (`~/.kylin-sql/connections.json`)
- [x] RSyntaxTextArea PL/SQL 编辑器 (语法高亮/折叠)
- [x] 解压即用打包配置 (tar.gz/zip + 启动脚本)
- [x] 大纲简单提取 (正则匹配 PROCEDURE/FUNCTION/PACKAGE)
- [x] 对象浏览器 (Schema → 表树形浏览)

### 技术决策
- 数据库驱动: 默认 PostgreSQL JDBC (OceanBase) + 可选 Oracle JDBC
- 配置目录: `~/.kylin-sql` (传统方式)
- JDK: 17 (目标麒麟 V10 SP1)
- 打包策略: 解压即用优先，deb 包后续

### 测试记录
- `mvn clean compile -DskipTests` — 待麒麟环境验证
- 解压即用包构建: `mvn clean package -DskipTests -pl kylin-sql-assembly -am`

---

## M2: 编辑器增强 ✅ (2026-06-22)

### 完成项
- [x] **多标签页编辑** — 每个标签独立 `SqlEditorPanel`，底部共享 `ResultPanel`
  - 标签页带关闭按钮（×），标签显示文件名 + `*`（修改标记）
  - 新建标签自动分配编号，关闭最后一个标签时自动新建
  - 快捷键: Ctrl+W 关闭当前标签
- [x] **打开/保存 SQL 文件**
  - 打开: `Ctrl+O`，文件过滤器（.sql），已打开文件跳转（不重复打开）
  - 保存: `Ctrl+S`，默认扩展名 .sql，覆盖确认
  - 另存为: `Ctrl+Shift+S`
  - 新建: `Ctrl+N`
  - 关闭未保存时提示保存确认（是/否/取消）
- [x] **自动补全** — `AutoCompletion` (autocomplete 3.3.3)
  - 触发延迟 300ms，自动激活
  - 7 大类补全词：SQL 关键字、PL/SQL 关键字、SELECT 子句、DDL、Oracle 函数、数据类型、内置包
  - 描述窗口支持（`setShowDescWindow(true)`）
  - `setAutoCompleteSingleChoices(false)` 避免干扰输入
- [x] **PL/SQL 语法高亮** — `SYNTAX_STYLE_SQL` 提供 SQL/PL/SQL 基础高亮
  - `rsyntaxtextarea` 升级 3.5.3→3.6.1
  - 代码折叠、括号匹配、出现标记、反锯齿、自动缩进

### 技术决策
- autocomplete 3.3.3 兼容 rsyntaxtextarea 3.6.1（autocomplete 依赖声明 3.6.1+）
- 文件修改状态通过 `DocumentListener` 自动跟踪，外部通过 `onModifiedChange` 回调通知标签更新
- 布局重构：`editorSplit` 从「每个标签内拆分」改为「上部 editorTabs + 下部 共享 resultPanel」
- 撤销/重做/查找/替换 菜单项绑定 RSyntaxTextArea 内置 Action

---

## M3: PL/SQL 导航 ✅ (2026-06-22)

### 完成项
- [x] **ANTLR 4.13.2 语法集成**
  - 从 grammars-v4 下载 `PlSqlLexer.g4`（2617 行）+ `PlSqlParser.g4`（10017 行）
  - `kylin-sql-core/pom.xml` 添加 antlr4-maven-plugin 自动生成 Java 代码
  - 创建 `PlSqlLexerBase.java` / `PlSqlParserBase.java`（grammars-v4 Java 目录原版，含 `IsNewlineAtPos`、`isVersion12`、`IsNotNumericFunction`、`isNotStartOfJoin`）
- [x] **包体大纲解析** — `PlSqlNavigator.java` 基于 ANTLR ParseTreeWalker + Listener
  - 提取: `CREATE PACKAGE` / `PACKAGE BODY` / 独立 `FUNCTION` / `PROCEDURE` / 包内 `FUNCTION` / `PROCEDURE` 及其行号
  - 返回值: `List<OutlineEntry>`（name + type + line）
  - `OutlinePanel` 从正则改写为 ANTLR 解析，回调传 `int line`（而非字符串匹配）
- [x] **定义跳转** — `SqlEditorPanel.navigateToLine(int)` 通过 `RSyntaxTextArea.getLineStartOffset()` + `setCaretPosition()` 实现
  - 异常处理 `BadLocationException`
  - `MainFrame` 添加 `refreshOutline()` + tab 切换监听器

### 技术决策
- @header 不重复声明 — ANTLR Maven 插件基于 `src/main/antlr4/` 目录结构自动推导 Java package，手动添加 @header 会导致双重复包声明
- 错误静默 — `parser.removeErrorListeners()` + `try-catch` 整体包裹，半合法 SQL 也能提取可用轮廓
- 大纲树节点存储 `OutlineEntry` 对象（而非字符串），`toString()` 由 `displayLabel()` 控制

### 测试记录
- `mvn compile -pl kylin-sql-ui -am` — 编译通过
- `mvn test -pl kylin-sql-core -am` — 测试通过

---

## M4: 格式化 ✅ (2026-06-22)

### 完成项
- [x] **格式化引擎** — `SqlFormatter.java` 基于 ANTLR PlSqlLexer 词法标记
  - 关键字大写转换（UPPER/LOWER/PRESERVE 三模式）
  - 缩进跟踪（`BEGIN`/`LOOP`/`THEN` 增加缩进，`END`/`ELSIF`/`ELSE`/`EXCEPTION` 减少缩进）
  - 分号 → 新行
  - 注释保留（`--` 单行 / `/* */` 多行）
  - 整体 try-catch 包裹，解析失败降级返回原文本
- [x] **格式化配置模型** — `FormatOptions.java`
  - `KeywordCase` 枚举（UPPER / LOWER / PRESERVE）
  - `indentSize` 可配置（1-8 空格）
- [x] **首选项对话框** — `PreferencesDialog.java`
  - 关键字大小写选择器
  - 缩进空格数微调
  - 保存/取消按钮
- [x] **编辑器集成**
  - `MainFrame.formatSql()` 使用 `SqlFormatter.format()` 替换选区或全文
  - 工具菜单 → 首选项
  - `Ctrl+Shift+F` 快捷键（菜单项已在 M2 定义）

### 技术决策
- ANTLR 词法分析 + 关键字集合匹配，而非 AST 遍历 → 更快速、更鲁棒
- 注释从 HIDDEN 通道提取并保留到输出中
- 缩进以关键字为中心（PL/SQL 块结构关键字驱动），非通用代码对齐
- `FormatOptions` 首选项暂存内存，未持久化到 JSON（后续可扩展至 ConfigManager）

### 测试记录
- `mvn compile -pl kylin-sql-ui -am` — 编译通过
- `mvn test -pl kylin-sql-core -am` — 测试通过

---

## M5: 对象浏览/结果 ✅ (2026-06-22)

### 完成项
- [x] **ObjectBrowser 树形扩展** — 表/视图/序列/索引/同义词/包 六大分类，按 Schema 组织
  - `public` schema 固定显示
  - 支持 `ALL_PROCEDURES` 查询获取包列表
- [x] **右键上下文菜单**
  - 表/视图: 生成 SELECT / INSERT / UPDATE / DELETE / 数据预览 (前100行) / 查看 DDL
  - 序列/索引/同义词: 查看 DDL
  - 包: 查看 DDL + 展开包 (过程/函数)
  - 左键双击: 自动执行 SELECT（数据预览）
  - Schema/分类节点: 刷新
- [x] **DDL 生成** — `SqlExecutor.generateDDL()`
  - 优先 `DBMS_METADATA.GET_DDL`
  - 降级: 从 JDBC 元数据拼接
- [x] **DML 模板生成** — `SqlExecutor.generateDML()`
  - SELECT / INSERT / UPDATE / DELETE 模板
- [x] **数据预览** — 双击或右键"数据预览"自动执行并显示结果
- [x] **ResultPanel CSV 导出** — 文件导出 + 剪贴板复制，UTF-8 BOM，CSV 转义
- [x] **ResultPanel 列宽自适应** — FontMetrics 精确计算
- [x] **ResultPanel 行数显示** — "N 行"

### 技术决策
- `ObjectBrowser` 回调改为 `ObjectActionCallback(String, String, String, String)` — action 扩展为 SELECT/INSERT/UPDATE/DELETE/PREVIEW/DDL
- DDL 降级策略: DBMS_METADATA → JDBC 元数据 → 注释
- CSV 导出带 UTF-8 BOM 确保 Excel 中文兼容

### 测试记录
- `mvn compile -pl kylin-sql-ui -am` — 编译通过
- `mvn test -pl kylin-sql-core -am` — 测试通过

---

## M6: 交付 ✅ (2026-06-22)

### 完成项
- [x] **解压即用包**
  - `mvn clean package -DskipTests` 一键构建（tar.gz + zip）
  - 包含 `kylin-sql.sh` (Linux/Kylin) + `kylin-sql.bat` (Windows) 启动脚本
  - 依赖自动打包到 `lib/` 目录
  - Fat-jar 备选（maven-shade-plugin 构建 kylin-sql-ui-1.0.0-SNAPSHOT.jar 含所有依赖）
- [x] **快捷键速查表** — `KEYBOARD_SHORTCUTS.md`
- [x] **使用手册** — `USER_MANUAL.md`
- [ ] **麒麟原生 deb 包** — `maven-jlink-plugin` 已配置，需麒麟环境 + `jpackage` 执行

### 技术决策
- 启动脚本使用 `-Dkylin.plsql.home` 标识安装目录
- dist.xml 中 `useProjectArtifact=false` 避免 pom 包类型警告

### 测试记录
- `mvn clean package -DskipTests -pl kylin-sql-assembly -am` — 构建成功

---

## M7: 剩余功能补全 ✅ (2026-06-22)

### 完成项

- [x] **7.1 deb 包构建说明** — `BUILDING.md` 编写完成，含前置要求、命令、常见问题
- [x] **7.2 对齐配置**
  - `FormatOptions.alignSelectColumns` + `SqlFormatter` SELECT 列对齐
  - `PreferencesDialog` 新增"启用列对齐"复选框
- [x] **7.3 事务控制**
  - `ConnectionManager`: `setAutoCommit()`/`commit()`/`rollback()` 专用连接管理
  - 工具栏: 自动提交复选框 + 提交/回滚按钮（自动禁用/启用）
- [x] **7.4 SQL 历史记录**
  - `SqlHistory`: 内存 200 条目环形缓冲，去重
  - 菜单"SQL 历史记录 (Ctrl+Shift+H)" → 弹出对话框，选中的 SQL 可复制到编辑器
- [x] **7.5 大纲同步**
  - `OutlinePanel.highlightForLine()`: 根据光标行号高亮大纲条目
  - `MainFrame.OutlineSyncListener`: CaretListener 挂载到编辑器
- [x] **7.6 执行计划可视化**
  - 改进 explainPlan：DBMS_XPLAN.DISPLAY + PLAN_TABLE 降级
- [x] **7.7 调用层级**
  - `PlSqlCallHierarchy`: 基于 OutlineEntry + 正则匹配构建调用树
  - `CallHierarchyDialog`: 树形展示 + 点击跳转到定义
  - 快捷键 Ctrl+Alt+H
- [x] **7.8 跨包符号索引**
  - `PlSqlSymbolIndex`: 本地（OutlineEntry）+ 数据库（ALL_SOURCE）双源索引
- [x] **7.9 语法高亮深化**
  - `PlSqlTokenMaker`: 自定义 RSyntaxTextArea TokenMaker
  - 覆盖 NULL/IDENTIFIER/KEYWORD/COMMENT/STRING/NUMBER/OPERATOR 各类别

### 新增/修改文件

| # | 文件 | 操作 |
|---|------|------|
| 1 | `BUILDING.md` | 新增 |
| 2 | `kylin-sql-core/.../db/ConnectionManager.java` | 修改 |
| 3 | `kylin-sql-core/.../db/SqlHistory.java` | 新增 |
| 4 | `kylin-sql-core/.../format/FormatOptions.java` | 修改 |
| 5 | `kylin-sql-core/.../format/SqlFormatter.java` | 修改 |
| 6 | `kylin-sql-core/.../parser/PlSqlCallHierarchy.java` | 新增 |
| 7 | `kylin-sql-core/.../parser/PlSqlSymbolIndex.java` | 新增 |
| 8 | `kylin-sql-ui/.../MainFrame.java` | 修改 |
| 9 | `kylin-sql-ui/.../component/SqlEditorPanel.java` | 修改 |
| 10 | `kylin-sql-ui/.../component/OutlinePanel.java` | 修改 |
| 11 | `kylin-sql-ui/.../component/PlSqlTokenMaker.java` | 新增 |
| 12 | `kylin-sql-ui/.../dialog/CallHierarchyDialog.java` | 新增 |
| 13 | `kylin-sql-ui/.../dialog/PreferencesDialog.java` | 修改 |
| 14 | `JK_PLAN.MD` | 修改 |
| 15 | `task_log.md` | 修改 |

### 测试记录
- `mvn compile -pl kylin-sql-core,kylin-sql-ui -am` — 编译通过
- `mvn test -pl kylin-sql-core -am` — 测试全部通过

---

## M7.5: 启动脚本排坑 ✅ (2026-06-22)

### 完成项
- [x] **bat 重写 7 轮**，解决所有 Windows 启动问题
- [x] **sh 脚本重写**，完善 JDK 查找逻辑 + 中文 UTF-8 编码适配

### bat 问题排查记录

| # | 症状 | 根因 | 修复 |
|---|------|------|------|
| 1 | 中文乱码，输出"卯夔" | bat 保存为 UTF-8，Windows 命令行 CP936 解码 | 纯 ASCII bat，删除所有中文字符 |
| 2 | `for /f` 循环闪退 | `for /f` + `|` 管道 + `setlocal enabledelayedexpansion` 冲突 | 版本检测改用临时文件 `%TEMP%\kylin-jver.txt` + `findstr` |
| 3 | `%MAIN_CLASS%` 变量为空 | 变量在 `if`/`for` 块内定义，`%` 扩展在解析时发生 | 直接内联类名字符串 |
| 4 | `where java` 路径含尾空格，Java 找不到 | `where java` 输出 `D:\...\java.exe `（末尾空格） | `:trim` 子程序循环剔除尾空格 |
| 5 | **Java usage 错误**（最顽固） | `%~dp0` → `D:\path\`，`-Dkey="%APP_HOME%"` → `"D:\path\"`，`\"` 被 `CommandLineToArgvW` 当转义引号，整行命令解析断裂 | `APP_HOME` 去尾 `\`，显式加 `\` 拼路径 |
| 6 | setlocal 语法错误 | `setlocal enabledelayedexpansion` 后多空格 | 确保 `; ` 前无空格 |

**问题 5 详细分析**（核心发现）：

Windows `CommandLineToArgvW` 规则：`奇数个 \ + " ` → `\` 被视作转义，`"` 成为字面字符。

展开后的命令：
```
-Dkylin.plsql.home="D:\path\"
                          ↑ ← `\"` 被解析为字面 `"`，引号未闭合
```
后续 `-Duser.home=..."` 被吞入上一个参数，导致 Java 无法识别 `-cp` 和主类，打印 usage。

### sh 脚本要点
- JDK 查找链同步 bat：bundled JDK → JAVA_HOME → `which -a java`（所有匹配选最高版本）
- 字符集：`export LANG/LC_ALL=zh_CN.UTF-8` + `-Dfile.encoding=UTF-8`
- Linux 用 `execve`（参数数组），无 `\"` 转义问题
- 版本解析：`awk -F '["_.]'` 一行提取，兼容 Java 8 (`1.8`) 和 Java 17+

### 最终文件

| 文件 | 说明 |
|------|------|
| `kylin-sql-assembly/src/main/assembly/kylin-sql.bat` | Windows 启动脚本（纯 ASCII，防 CP936 乱码） |
| `kylin-sql-assembly/src/main/assembly/kylin-sql.sh` | Linux/Kylin 启动脚本（UTF-8） |

### 技术决策
- bat 坚持纯 ASCII + 英文提示，不引入中文，避免多编码兼容问题
- 版本检测用临时文件而非管道，避免 `for /f` 嵌套管道的不确定性
- `APP_HOME` 去尾 `\` 后显式拼接路径，消除 `\"` 隐患
- `BEST_JAVA` 尾空格用单独 `:strip_loop` 处理，不依赖 `:trim`（后者只处理 `JPATH`）
- sh 用 `which -a java` 而非 `command -v`，获取 PATH 中所有 java 路径而非仅第一个

---

## M8: 连接 + 对象浏览器修复 ✅ (2026-06-22)

### 完成项

- [x] **连接对话框支持 JDBC URL 直连模式**
- [x] **对象浏览器 schema 获取降级 + 空数据提示**

### 改动文件

| 文件 | 改动 |
|------|------|
| `ConnectionInfo.java` | 新增 `useUrl`/`jdbcUrl` 字段；`getJdbcUrl()` 优先返回直接 URL；`getDriverClass()` 自动从 URL 前缀检测驱动 |
| `ConnectionDialog.java` | 新增"使用 JDBC URL"复选框 + URL 文本框，勾选时隐藏主机/端口/服务名/数据库类型字段 |
| `ConnectionManager.java` | `connect()` 异常捕获 + 测试查询根据库类型选 `SELECT 1 FROM DUAL` / `SELECT 1` |
| `MainFrame.java` | `onConnectionSelected()` 捕获 `connect()` 异常并弹窗提示 |
| `ObjectBrowser.java` | 完整重写：DataGrip 式树结构，按数据库类型自适应展示对象类型 |

### 问题 1：连接对话框不支持 JDBC URL 直连

**症状**：用户只能通过主机+端口+服务名三段式填写，无法使用已有的 JDBC URL 直接连接。

**根因**：`ConnectionDialog` 表单只有固定字段（主机/端口/服务名），`ConnectionInfo.getJdbcUrl()` 只根据字段拼装，不支持直接输入完整 URL。

**修复**：
1. `ConnectionInfo` 新增 `useUrl`（布尔开关）和 `jdbcUrl`（直接 JDBC URL）字段
2. `getJdbcUrl()`：`useUrl=true` 时直接返回 `jdbcUrl`，否则走原有拼装逻辑
3. `getDriverClass()`：新增 `detectDriverFromUrl()` 从 URL 前缀自动识别驱动类（支持 `jdbc:oceanbase:`、`jdbc:postgresql:`、`jdbc:oracle:`、`jdbc:mysql:`、`jdbc:mariadb:`、`jdbc:sqlserver:`、`jdbc:db2:`、`jdbc:h2:`、`jdbc:sqlite:`）
4. `ConnectionDialog` 新增"使用 JDBC URL"复选框 + URL 文本框，勾选时隐藏主机/端口/服务名/数据库类型字段
5. `saveConnection()`/`testConnection()` 均根据 `useUrl` 状态分别处理两种模式
6. `loadSelected()` 正确恢复 `useUrl` 状态

### 问题 2：对象浏览器什么也不显示

**症状**：连接数据库后，对象浏览器树为空；切换连接也无效。

**根因排查**：

| 可能性 | 实际原因 |
|--------|----------|
| `isConnected()` 返回 false | 连接存在且可执行 SQL，排除 |
| `meta.getSchemas()` 返回空 | ✅ 某些驱动（OceanBase Oracle 模式等）`getSchemas()` 返回空结果集 |
| Schema 名被过滤 | `_` 前缀过滤可能误杀业务 schema |
| `getTables()` 返回空 | 用户无 `ALL_TABLES` 权限时可能为空 |
| 异常静默捕获 | 有 log 但无用户可见反馈 |

**修复**：
1. **降级查询**：`getSchemas()` 返回空或无有效 schema 时，自动尝试 SQL 降级：
   - Oracle/OceanBase 系：`SELECT DISTINCT owner FROM all_objects WHERE owner NOT IN ('SYS','SYSTEM','PUBLIC','OCEANBASE','MYSQL')`
   - PostgreSQL 系：`SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_schema NOT IN ('information_schema','pg_catalog','pg_toast')`
2. **宽松 schema 过滤**：去掉 `_` 前缀过滤；仅过滤明确已知的系统 schema（`information_schema`、`pg_*`、`sys`、`system`、`oceanbase`、`mysql`）
3. **`public` 正常纳入**：不再跳过 `public` 再单独添加，直接参与循环
4. **日志增强**：空 schema 时 `log.warn`；连接未就绪时 `log.warn` 说明原因

### DataGrip 风格树结构重构

`ObjectBrowser.java` 完整重写，核心改动：

1. **按数据库类型自适应对象类型**：
   - Oracle/OceanBase：表、视图、索引、序列、同义词、函数、过程、包（可展开）
   - PostgreSQL：表、视图、索引、序列、函数、过程
   - MySQL/MariaDB：表、视图、函数、过程

2. **SQL 直查取代 `meta.getTables()`**：用各数据库特有的系统视图/表查询（`all_tables`、`pg_catalog.pg_tables`、`information_schema.tables`），更可靠

3. **新增"函数"和"过程"独立分类**：之前只有"包"，过程和函数淹没在包查询中

4. **包展开**：`PreparedStatement` 替代字符串拼接，防 SQL 注入

5. **类型标签→类型编码映射**：中文标签 ↔ 英文编码（TABLE/VIEW/...），context menu 统一映射

### 追加修复：HikariCP 连接测试查询不兼容 Oracle

**症状**：`ORA-00923: 未找到要求的 FROM 关键字`，`SELECT 1` 在 Oracle 上失败，导致连接池初始化异常，`isConnected()` 返回 false → 对象浏览器不渲染。

**根因**：`ConnectionManager.connect()` 硬编码 `config.setConnectionTestQuery("SELECT 1")`，Oracle JDBC 驱动某些版本/配置下需要 `SELECT 1 FROM DUAL`。

**修复**：
1. `ConnectionManager.connect()` — 根据 URL/dbType 自动选测试查询：Oracle/OceanBase 用 `SELECT 1 FROM DUAL`，其他用 `SELECT 1`
2. `ConnectionManager.connect()` — try-catch 包裹 `new HikariDataSource(config)`，失败时不抛异常而是 log error + 不注册连接
3. `MainFrame.onConnectionSelected()` — 捕获 `connect()` 异常，弹窗提示用户连接失败

### 测试记录
- `mvn compile -pl kylin-sql-core,kylin-sql-ui -am` — 编译通过
- `mvn test -pl kylin-sql-core -am` — 测试通过
- `mvn package -DskipTests -pl kylin-sql-assembly -am` — 打包通过

---

## M9: DataGrip 风格编辑器 + 元数据缓存 ✅ (2026-06-22)

### 完成项

#### 9.1 SQL 编辑器顶部区域重设计（DataGrip 风格）
- [x] **顶部连接下拉移除** — 主工具栏不再有连接选择器
- [x] **每个标签页自带工具栏**：▶ 执行 / 历史 / 连接下拉 / Schema下拉 / 自动提交 / 提交 / 回滚
- [x] **连接下拉全量展示** — 始终列出所有已保存连接，格式 `名称[IP:端口]`（URL 模式显示 `名称[URL]`）
- [x] **Schema 联动** — 切换连接时自动从 MetadataCache 加载该连接的 schema 列表，填充下拉
- [x] **Schema 可编辑** — 支持手动输入
- [x] **连接绑定** — 从树节点打开标签页时自动选中来源连接 + 当前 schema
- [x] **标签页命名** — DataGrip 风格：`console` → `console @ connName`（绑定连接时）
- [x] **`newFile(connName, schema)`** — 新建标签时可选传入连接和 schema，`openInNewEditor(content, connName, schema)` 同理
- [x] **`loadSavedConnections()` 推送到所有已有标签** — 连接变更后刷新所有标签的连接下拉

#### 9.2 左侧对象浏览器（DataGrip 风格图标）
- [x] **工具栏纯图标按钮** — 新建(⊕)、属性(⚙)、刷新(↻)、SQL(▶)，透明背景 + 小边框 + 无焦点（解决卡顿）
- [x] **树节点彩色图标** — 连接(DB蓝) / Schema(S绿) / 表(T蓝) / 视图(V青) / 索引(I橙) / 序列(N紫) / 同义词(Y灰) / 函数(F红) / 过程(P红) / 包(K棕) / 列(C灰)
- [x] **刷新只刷新选中连接** — `refreshSelected()` 获取树选中节点所属连接，仅清缓存 + 重载该连接
- [x] **点击表名复制** — 单击第 4-5 层节点仅复制节点文字到剪贴板，不创建标签页（DML 通过右键菜单）
- [x] **表节点展开显示列** — 懒加载，从 MetadataCache 读取，无缓存时查数据库

#### 9.3 增强数据预览（数据库语法差异）
- [x] Oracle/OceanBase: `SELECT * FROM "s"."t" FETCH FIRST 100 ROWS ONLY`
- [x] MySQL/PostgreSQL/SQLite: `SELECT * FROM "s"."t" LIMIT 100`
- [x] `SqlExecutor.generatePreviewSQL(Connection, schema, table)` 自动检测数据库类型

#### 9.4 增强 DDL 生成
- [x] **Oracle 路径**：`DBMS_METADATA.GET_DDL` + `all_tab_comments`(表注释) + `all_col_comments`(列注释) + `all_indexes`+`all_ind_columns`(索引)
- [x] **通用回退**：`getColumns()` + `getPrimaryKeys()` + `information_schema` 注释 + `pg_indexes`/`STATISTICS` 索引
- [x] 输出包含：列定义(NOT NULL) / 主键约束 / 表注释 / 列注释 / CREATE INDEX

#### 9.5 元数据本地缓存
- [x] **`MetadataCache.java`**（新建）— `kylin-sql-core/.../cache/`，单例 + Gson 序列化
- [x] **缓存位置**：`~/.kylin-sql/cache/{conn_hash}/_cache.json`
- [x] **缓存内容**：dbProduct / schemas / 各类型对象名 / 表列定义 / DDL 文本
- [x] **缓存策略**：首次查 DB 后写入，后续读缓存（内存 + 磁盘双层）
- [x] **失效机制**：手动刷新（工具栏/右键）→ `clearConnection()` 删除该连接缓存文件
- [x] **ObjectBrowser**：`loadConnection()` 先 `hasMetadata()` → 命中则从缓存重建树
- [x] **ObjectBrowser**：`loadColumns()` 先 `getColumns()` → 命中则直接加载
- [x] **MainFrame**：DDL 操作先 `getDDL()` → 命中不查库

#### 9.6 连接管理对话框
- [x] **属性按钮预选** — `ConnectionDialog(owner, config, cm, selectConnName)` 自动在列表中定位该连接
- [x] **MainFrame.showConnectionDialog(String connName)** — 传入 connName，对话框打开后自动选中

### 新增/修改文件

| # | 文件 | 操作 |
|---|------|------|
| 1 | `kylin-sql-core/.../cache/MetadataCache.java` | **新增** |
| 2 | `kylin-sql-core/.../db/SqlExecutor.java` | 修改 — 增强 DDL + DB 感知预览 SQL |
| 3 | `kylin-sql-ui/.../MainFrame.java` | 修改 — 多连接标签 + DDL 缓存 + schema 传递 |
| 4 | `kylin-sql-ui/.../component/ObjectBrowser.java` | 修改 — 图标 + 缓存 + 列加载 + 只复制不创建标签 |
| 5 | `kylin-sql-ui/.../component/SqlEditorPanel.java` | 修改 — 连接/Schema 联动工具栏 + displayName 格式 |
| 6 | `kylin-sql-ui/.../dialog/ConnectionDialog.java` | 修改 — 支持 selectConnName 自动预选 |

### 技术决策
- **缓存格式**：单 JSON 文件/连接，Gson 序列化（复用 ConfigManager 方案），`_cache.json`
- **Schema 联动**：不查库，直接从 `MetadataCache.getSchemas()` 获取（前提是连接已在树中展开过）
- **连接显示格式**：`名称[IP:端口]` 非 URL 模式；URL 模式显示 `名称[URL]`
- **编辑栏按钮无焦点**：`setFocusable(false)` + `setContentAreaFilled(false)` 避免卡顿
- **刷新语义**：工具栏 ↻ 只刷新当前选中的连接（向上查找 ConnHolder 节点），无选中则刷新全部

### 测试记录
- `mvn compile -pl kylin-sql-core,kylin-sql-ui -am` — 编译通过
- `mvn test -pl kylin-sql-core -am` — 测试通过
- `mvn package -DskipTests -pl kylin-sql-assembly -am` — 打包通过

---

## M10: DataGrip Darcula 主题 + UI 精简 + 查询超时 ✅ (2026-06-24)

### 完成项

- [x] **Darcula 暗色主题** — FlatLightLaf → FlatDarculaLaf，DataGrip UI 默认值（underline tabs、紧凑高度、无竖线表格、紧凑树行高）
- [x] **工具栏纯图标化** — 主工具栏/编辑器工具栏/对象浏览器全部 icon-only flat buttons，`setContentAreaFilled(false)`
- [x] **TitledBorder 全移除** — OutlinePanel、ResultPanel 不再有 `BorderFactory.createTitledBorder()`
- [x] **RSyntaxTextArea 暗色主题** — 从 `/org/fife/ui/rsyntaxtextarea/themes/dark.xml` 加载
- [x] **连接查询超时** — `ConnectionInfo.queryTimeout`（int，0=不限），`ConnectionManager.queryTimeouts` 映射，`SqlExecutor.execute(conn,sql,timeoutSec)` 调用 `stmt.setQueryTimeout()`，`ConnectionDialog` 添加"查询超时(秒)"输入

### 改动文件

| 文件 | 改动 |
|------|------|
| `KylinPlSqlApp.java` | 换 FlatDarculaLaf + DataGrip UI 默认值 |
| `MainFrame.java` | 工具栏纯图标化 + 编辑器工具栏 flatBtn() |
| `SqlEditorPanel.java` | flatBtn() 辅助方法 + 暗色主题加载 |
| `ObjectBrowser.java` | 工具栏 3→2px |
| `OutlinePanel.java` | 移除 TitledBorder |
| `ResultPanel.java` | 移除 TitledBorder |
| `ConnectionInfo.java` | 新增 queryTimeout 字段 |
| `ConnectionManager.java` | 新增 queryTimeouts 映射 |
| `SqlExecutor.java` | execute() 支持 queryTimeout |
| `ConnectionDialog.java` | 新增超时输入 |

### 测试记录
- `mvn compile` — 编译通过
- `mvn package -DskipTests` — 打包通过

---

## M11: SQL 语句自动检测 + 执行日志 + 异步优化 ✅ (2026-06-24)

### 完成项

- [x] **SQL 语句自动检测** — `SqlEditorPanel.getCurrentStatement()` 基于分号分隔段，光标落空段时回退到前一个非空段
- [x] **语句高亮** — `StmtHighlightPainter`（2px 垂直内边距 + 半透白），光标移动自动更新，选中文本时隐藏
- [x] **分段缓存** — `cachedSegments` 仅在文档变更时重算，`Character.isWhitespace()` 替代 `trim().isEmpty()`（零临时分配）
- [x] **Gutter 执行图标** — `Gutter.addLineTrackingIcon()` → 绿色 ✓ / 红色 ❗ 标记执行结果
- [x] **执行日志** — ResultPanel.appendMessage() 添加结构化日志（时间/耗时/行数/错误）
- [x] **`SqlExecutor` 去尾分号** — 自动去除末尾 `;`，修复 ORA-00933
- [x] **`onObjectAction` 全异步** — 6 种操作均用 `SwingWorker` 避免 EDT 阻塞

### 改动文件

| 文件 | 改动 |
|------|------|
| `SqlEditorPanel.java` | `getCurrentStatement()` + `cachedSegments` + `StmtHighlightPainter` + `markExecResult()` |
| `ResultPanel.java` | `appendMessage()` 结构化日志 |
| `SqlExecutor.java` | 去尾分号 |
| `MainFrame.java` | `onObjectAction` SwingWorker 异步化 |

### 技术决策
- 分号检测不依赖 SQL 语法解析，覆盖 >95% 场景
- Gutter 图标复用 RSTA 内置 API，无需自定义行号渲染
- 执行日志追加不覆盖，保留历史

### 测试记录
- `mvn compile` — 编译通过
- `mvn package -DskipTests` — 打包通过

---

## M12: 工作空间持久化 + 欢迎页 ✅ (2026-06-24)

### 完成项

- [x] **ConfigManager 工作空间** — `WorkspaceState`/`TabState` 内部类，`saveWorkspace()`/`loadWorkspace()` 持久化到 `~/.kylin-sql/workspace.json`
- [x] **WelcomePanel** — 快捷键清单 + 使用技巧 + 新建/打开按钮，GridBagLayout 布局
- [x] **MainFrame 编辑器区 CardLayout** — welcome / tabs 双卡切换，启动优先恢复上次会话，标签变更自动保存，窗口关闭保存

### 改动文件

| 文件 | 改动 |
|------|------|
| `ConfigManager.java` | 新增 WorkspaceState/TabState + 持久化方法 |
| `WelcomePanel.java` | **新增** — 欢迎页组件 |
| `MainFrame.java` | editorTabs → CardLayout(welcome+tabs)；saveWorkspace() 在所有建/关/切换标签时调用；tryRestoreWorkspace() 恢复会话 |

### 测试记录
- `mvn compile` — 编译通过
- `mvn test` — 测试通过
- `mvn package -DskipTests` — 打包通过

---

## M13: DataGrip 风格右侧面板（FILES + THUMBNAIL）✅ (2026-06-24)

### 完成项

- [x] **替代 OutlinePanel** — `RightPanel` 带 30px 竖排标签栏（`VerticalTabButton`，文字旋转 -90°）
- [x] **FILES 标签** — JList 文件列表，格式：文件名（`#DDDDDD`）+ 路径（`#888888` 灰、超长截断 + tooltip），无表头/表格线，双击打开
- [x] **THUMBNAIL 标签** — 迷你缩略图，每行渲染为水平短条，点击/拖拽跳转，当前行绿色高亮
- [x] **已保存文件追踪** — `ConfigManager.SavedFileRecord` 持久化到 `saved_files.json`，打开/保存时自动记录
- [x] **`VerticalTabButton` 抽取为独立组件** — 供左右面板复用

### 改动文件

| 文件 | 改动 |
|------|------|
| `VerticalTabButton.java` | **新增**（从 RightPanel 抽取） |
| `RightPanel.java` | **新增** — 完整实现 |
| `ConfigManager.java` | 新增 SavedFileRecord + 持久化方法 |
| `MainFrame.java` | OutlinePanel → RightPanel；移除 refreshOutline/OutlineSyncListener outline 逻辑；新增 openOrSwitchToFile() |

### 交互效果
- 右侧标签悬停时显示完整路径 tooltip
- 选中标签左侧绿条高亮
- 标签条 opaque + 背景色防止遮挡

### 测试记录
- `mvn compile` — 编译通过
- `mvn test` — 测试通过
- `mvn package -DskipTests` — 打包通过

---

## M14: DataGrip 风格左侧面板（可折叠）+ 工具栏样式 ✅ (2026-06-24)

### 完成项

- [x] **LeftPanel** — DATABASE 竖排标签（30px），点击折叠/展开对象浏览器，分割线显式控制
- [x] **工具栏风格** — 底部 1px `#4A4A4A` 分割线，按钮组之间竖线分隔符

### 改动文件

| 文件 | 改动 |
|------|------|
| `LeftPanel.java` | **新增** — DATABASE 标签 + 折叠展开 + isExpanded() |
| `MainFrame.java` | ObjectBrowser → LeftPanel 包裹；leftSplit 回调改用 setDividerLocation；工具栏加分割线/底边 |

### 交互效果
- 展开时分割线 250px，折叠时 30px（仅标签条）
- 非最大化窗口不阻挡折叠/展开
- 标签条 opaque 防遮挡

### 测试记录
- `mvn compile` — 编译通过
- `mvn test` — 测试通过
- `mvn package -DskipTests` — 打包通过

---

## M15: VerticalTabButton 字体/尺寸修复 + 左右标签条 Debug ✅ (2026-06-24)

### 完成项

- [x] **VerticalTabButton 彻底重写** — 解决文字不可见/被裁剪问题
  - **根因**：`getFontMetrics(getFont())` 在 `getPreferredSize()` 中返回无效指标（高度≈0），导致按钮宽度仅 10px，文字被严重裁剪
  - **修复**：`getPreferredSize()` 改为使用 `Toolkit.getDefaultToolkit().getFontMetrics(TAB_FONT)` 替代组件 `getFontMetrics(getFont())`，确保获取稳定字体指标
  - **字体**：Segoe UI BOLD 13（旧：PLAIN 11）
  - **标签条宽度**：30→32px
  - **按钮尺寸**：宽度固定 `TAB_STRIP_WIDTH=32`，高度 = `stringWidth + 28`
  - `setAlignmentX(LEFT_ALIGNMENT)` 确保按钮紧贴标签条左边缘
  - 添加 `KEY_TEXT_ANTIALIAS_LCD_HRGB` 文本抗锯齿提示
  - 文字颜色：`#BBBBBB` → `#E0E0E0`（更亮）
  - 选中指示器：2→3px 绿条
- [x] **`setContentAreaFilled(false)` 不可移除** — FlatLaf 的 `FlatButtonUI.paint()` 在 `contentAreaFilled=true` 时返回 `true`，导致 `JComponent.paint()` 跳过 `paintComponent()` 调用，自定义绘制完全失效
- [x] **Debug 彩色边框保留** — 用于后续视觉调试，后续统一移除

### 改动文件

| 文件 | 改动 |
|------|------|
| `VerticalTabButton.java` | 完全重写：静态字体常量、Toolkit FontMetrics、硬编码宽度、LEFT_ALIGNMENT、文本抗锯齿 |
| `LeftPanel.java` | tabStrip 宽度 34→32；minimum/preferred size 同步 |
| `RightPanel.java` | tabStrip 宽度 34→32 |
| `MainFrame.java` | 折叠 divider 34→32 |

### 关键发现

1. `getFontMetrics(getFont())` 在组件未 displayable 时可能返回无效值 → 改用 `Toolkit.getDefaultToolkit().getFontMetrics()`
2. FlatButtonUI.paint() 返回 true 时会拦截 paintComponent() → `setContentAreaFilled(false)` 必须保留
3. `setBorder(null)` 会移除按钮需要的边距 → 必须用 `emptyBorder(2,2,2,2)`

### 测试记录
- `mvn compile` — 编译通过
- `mvn package -DskipTests` — 打包通过

---

## M16: DataGrip 风格底部工具窗口（TODO + Services） ✅ (2026-06-24)

### 完成项

- [x] **BottomPanel** — 全宽底部工具窗口，点击标签展开/收起
  - 标签条：28px，水平布局，选中项顶部 2px 绿条指示
  - TODO 标签：Monospaced JTextArea，支持粘贴
  - Services 标签：左侧连接→标签树 + 右侧 ResultPanel（结果集/消息）
- [x] **布局改造** — `editorSplit` 移除，`editorPanel` 直接放 `leftSplit` 右侧；`bottomPanel` + `statusLabel` 放入 `bottomWrapper` 在 `contentPane.SOUTH`
  - 展开时 278px，收起时仅 28px 标签条
  - 全宽覆盖，出现在左侧对象树面板的视觉上层（与 DataGrip 一致）
- [x] **自动展开** — `showResult()`/`appendMessage()`/`showError()` 自动切换到 Services 并展开面板
- [x] **连接树自动刷新** — `saveWorkspace()` 末尾调用 `refreshConnTree()`，标签增删/切换自动更新

### 新增/修改文件

| 文件 | 操作 | 改动 |
|------|------|------|
| `BottomPanel.java` | **新增** | 工具窗口面板：TabDataProvider 接口、Todo JTextArea、Services connTree + ResultPanel、toggle 展开/收起 |
| `MainFrame.java` | **修改** | 去掉 editorSplit；editorPanel 直入 leftSplit；新增 bottomPanel + bottomWrapper；所有 resultPanel.xxx() → bottomPanel.xxx()；TabDataProvider lambda；saveWorkspace 中 refreshConnTree |

### 技术决策
- BorderLayout SOUTH 区域根据 BottomPanel.preferredSize 变化动态调整高度
- `ensureServicesVisible()` 在 showResult/appendMessage/showError 时自动触发展开到 Services
- 连接树数据通过 `TabDataProvider` 回调实时从 MainFrame 收集

---

## M17: DataGrip 风格 Services 结果区 + 语句高亮修复 + 标签紧凑化 ✅ (2026-06-25)

### 完成项

#### 17.1 Services 结果区仿 DataGrip
- [x] **消息标签固定在左侧第 1 个** — JTabbedPane index 0
- [x] **多结果标签** — 每次 SQL 执行创建 `resultN` 标签
- [x] **行号列** — 列 0 = `#`，45px 固定宽，显示绝对行号
- [x] **分页工具栏** — 嵌入每个结果标签内容区 NORTH（标签按钮之下、表格之上）
  - ◀ 上页 / 每页条数下拉框（55px 定宽）/ ▶ 下页
  - ↻ 刷新（重置到第 1 页）
  - ■ 停止（待接入真正 cancel）
  - 📌 Pin 标签（固定后高亮绿色）
- [x] **消息标签无工具栏**
- [x] **分页模型** — `PaginatedTableModel`（AbstractTableModel），只渲染当前页数据

#### 17.2 同 SQL 复用 + 关闭按钮 + 智能命名
- [x] **同 SQL 覆盖** — `showResult(sql, result)` 接收 SQL，遍历 unpinned 标签匹配同 SQL → 替换数据不新建
- [x] **Pin 保护** — 固定标签不参与匹配
- [x] **关闭按钮** — 每个结果标签右侧 × 关闭
- [x] **智能标签名** — `guessLabel(sql)`：单表查询提取表名，复杂查询回落 `resultN`

#### 17.3 右侧面板折叠整面板
- [x] **toggle callback** — RightPanel `Runnable onToggle`，MainFrame 显式 `setDividerLocation`
- [x] **点击活跃标签 → 整个右面板隐藏；再次点击 → 展开**

#### 17.4 左右标签紧凑化
- [x] **字体**：Segoe UI PLAIN 10（旧 9pt），padding +2，border 纵向归零
- [x] **标签条宽度**：26px 统一

#### 17.5 SQL 语句高亮彻底消除闪烁
- **根因**：光标移动 `removeHighlight` → `addHighlight` 反复重绘
- **修复**：`DynamicSegmentPainter` — 一次性 `addHighlight(0, docLen)` 注册 LayerPainter，动态检查语句块，光标移动只 `repaint()`
- **高亮**：透明度 0x30→0x60，每行左侧 3px 竖线
- **编辑器**：Monospaced 14pt，禁用 markOccurrences

### 新增/修改文件

| 文件 | 改动 |
|------|------|
| `VerticalTabButton.java` | 重写 — 10pt、padding +2、border 纵向归零 |
| `RightPanel.java` | 新增 `onToggle` 回调；折叠通知 MainFrame |
| `MainFrame.java` | mainSplitRef 处理右侧折叠；`showResult(sql, result)` |
| `BottomPanel.java` | `showResult(sql, result)` 签名 |
| `ResultPanel.java` | **完全重写** — PaginatedTableModel、GuessLabel、per-tab toolbar、close button、pin |
| `SqlEditorPanel.java` | DynamicSegmentPainter LayerPainter；字体 14pt；高亮增强；禁用 markOccurrences |

### 测试记录
- `mvn compile` — 编译通过
- `mvn package -DskipTests` — 打包通过

---

## M18: 源码查看器(PL/SQL Developer风格) ✅ (2026-06-25)

### 完成项

- [x] **SourceViewerPanel 独立组件** — 不继承 SqlEditorPanel，通过 `instanceof` 分支处理
  - 所有类型: 左侧 20% 方法导航列表 + 右侧代码编辑器 (JSplitPane)
  - PACKAGE 额外有 Spec/Body 子标签切换
  - Spec/Body 标签激活态用绿色底部边框 (MatteBorder) 标识
  - 编辑(E)/编译(C)单字母图标按钮，带 ToolTip
  - 底部编译输出面板
  - 方法列表 13px，ToolTip 200ms 初始延迟
  - 方法解析正则：`^\s*(FUNCTION|PROCEDURE)\s+(\w+)\b`
  - 点击方法列表跳转到对应行 (带滚动定位到可视区 1/3 处)
- [x] **ObjectBrowser 双击打开源码**
  - `Callback` 新增 `onOpenSourceObject` 接口
  - level-4 节点 (PROCEDURE/FUNCTION/PACKAGE) 双击 → 打开 SourceViewerPanel
  - level-5 包下子节点 → 定位到父 PACKAGE 打开
- [x] **MainFrame 集成**
  - `openSourceObject()` — 自动绑定连接(不可修改)，检查重复打开，创建 SourceViewerPanel 标签
  - `editorTabs.addChangeListener` 兼容 SqlEditorPanel + SourceViewerPanel
  - `initTabComponent` 改为接受 `Component` 统一处理两种面板
  - `instanceof` 保护所有循环 (saveWorkspace/loadSavedConnections/openOrSwitchToFile/closeTab/dataProvider)
  - `TabState` 新增 `objectName`/`objectType` 字段
  - `saveWorkspace`/`tryRestoreWorkspace` 支持 `type="sourceviewer"` 工作区恢复
  - `SourceViewerCaretSync` 光标同步监听器 → 右侧缩略图联动
- [x] **RightPanel 支持 SourceViewerPanel**
  - `ThumbnailContent` 新增 `setText`/`setOnNavigate`
  - `setActiveSourceViewer()` 设置缩略图内容和导航
- [x] **布局与交互修复**
  - `mainSplit.setResizeWeight(1.0)` — 多余空间全部分配给左侧编辑器
  - `SwingUtilities.invokeLater` 延迟设置 divider location (0.20)
  - 窗口关闭: `EXIT_ON_CLOSE` → `DO_NOTHING_ON_CLOSE`，`windowClosing` 中 `saveWorkspace() + System.exit(0)` 无提示
  - BottomPanel 默认展开 SERVICES 标签
  - `storeSources` 内容检测已移除 (数据库不会返回错乱数据)
  - 竞态修复: `loadSource`/`reloadSource` 单次 `invokeLater` + `applyCurrentSource()` 统一入口
  - `MainFrame.java:228` `return` → `flag + break` 修复 (避免跳过 `refreshConnTree()`)

### 新增/修改文件

| 文件 | 操作 | 改动 |
|------|------|------|
| `SourceViewerPanel.java` | **新增** | 源码查看器完整实现 |
| `MainFrame.java` | 修改 | sourceviewer 全流程支持 + 布局修复 + workspace restore |
| `ObjectBrowser.java` | 修改 | doubleClick + onOpenSourceObject callback |
| `ConfigManager.java` | 修改 | TabState 新增 objectName/objectType |
| `RightPanel.java` | 修改 | ThumbnailContent setText/setOnNavigate + setActiveSourceViewer |
| `BottomPanel.java` | 修改 | 默认展开 SERVICES |
| `SqlExecutor.java` | 修改 | getSource() 源码检索方法 |

### 技术决策
- SourceViewerPanel 为独立 JPanel，不继承 SqlEditorPanel，通过 `instanceof` 分支处理
- 工作区保存/恢复新增 `"sourceviewer"` 类型，重建时调用 `openSourceObject`
- `applyCurrentSource()` 作为唯一设置编辑器文本入口
- 数据库返回的 spec/body 直接赋值，不做内容检测

### 测试记录
- `mvn compile` — 编译通过
- `mvn package -DskipTests` — 打包通过

---

## M19: ExceptionInInitializerError 修复 + 连接去重 + 主题切换树不折叠 ✅ (2026-06-26)

### 完成项
- [x] **SqlFormatter KEYWORDS Set 去重** — `Set.of()` → `Collections.unmodifiableSet(new HashSet<>(Arrays.asList(...)))`，运行时自动去重，消除 `ExceptionInInitializerError`
- [x] **连接保存去重** — `ConnectionDialog.saveConnection()` 在添加前 `removeIf(c → c.getName().equalsIgnoreCase(ci.getName()))`，同名连接替换而非追加
- [x] **主题切换树不折叠** — `ObjectBrowser.applyTheme()` 保存/恢复展开路径

### 改动文件

| 文件 | 改动 |
|------|------|
| `SqlFormatter.java` | Set.of → HashSet 自动去重 |
| `ConnectionDialog.java` | saveConnection 同名去重 |
| `ObjectBrowser.java` | applyTheme 保存展开路径 |

### 测试记录
- `mvn compile` — 编译通过

---

## M20: 标签页右键菜单 + 拆分编辑器 + 本地历史 + 多项 UI 修复 ✅ (2026-06-27)

### 完成项

#### 20.1 SQL 编辑区标签右键菜单（DataGrip 风格）
- [x] **20 项右键菜单**：
  - 关闭 / 关闭其他 / 关闭所有 / 关闭未修改 / 关闭左侧标签 / 关闭右侧标签
  - **向右拆分 / 向下拆分** — 当前标签移入分屏区，分屏标签有完整图标+关闭按钮+主题同步
  - **开始执行 (Ctrl+Shift+F10)** — 运行当前标签 SQL
  - **另存为...** — 当前内容另存文件
  - **复制文件名 / 复制完整路径**
  - **用其他方式打开 >** — 文件管理器 / 终端 / 外部编辑器
  - **本地历史 >** — 显示历史 / 对比上个版本 / 恢复
  - **重新打开已关闭标签** — 保存最近 15 个关闭标签
- [x] **Ctrl+Shift+F10 快捷键** — 全局注册，与 F8 等效
- [x] **右键菜单同时支持主标签页和分屏标签页**

#### 20.2 本地历史
- [x] **自动保存** — `writeFile()` 保存文件时自动备份到 `~/.kylin-sql/.local-history/{filename}/`
- [x] **显示历史** — 对话框选择历史版本，在新标签中打开查看
- [x] **对比** — 当前内容与最近历史版本的简单行级对比
- [x] **恢复** — 选择历史版本覆盖当前编辑器内容
- [x] **版本清理** — 每个文件最多保留 30 个版本

#### 20.3 标签页空白修复
- [x] `TabbedPane.tabInsets` → `[2,5,2,5]`，`contentAreaInsets` → `[0,0,0,0]`，`tabAreaInsets` → `[2,0,0,0]`

#### 20.4 缩略图点击位置修复
- [x] `ThumbnailContent` 鼠标点击/拖拽公式从 `e.getY() / getHeight() * totalLines` 改为 `(e.getY()-2) / (getHeight()-4) * totalLines`，对齐 2px 顶部留白

#### 20.5 树展开/收起箭头颜色修复
- [x] `switchTheme` 中在 `updateComponentTreeUI` 前设置 `UIManager.put("Tree.foreground", theme.resolve("list.fg"))`

#### 20.6 用其他方式打开跨平台兼容（Windows + Kylin/Linux）
- [x] **文件管理器**：Windows `explorer /select,path`，Linux `xdg-open parentDir`
- [x] **终端**：Windows `cmd /c start cmd`，Linux 尝试 `x-terminal-emulator`/`gnome-terminal`/`konsole`/`xfce4-terminal`/`mate-terminal`，均失败则 `xdg-open` 打开文件夹
- [x] **外部编辑器**：优先 `Desktop.getDesktop().open()`，Linux 备降 `xdg-open`

#### 20.7 设置页左侧面板宽度修复
- [x] `SettingsDialog` JSplitPane 添加 `setDividerLocation(180)` + `setResizeWeight(0.15)`

#### 20.8 结果集列名双击复制
- [x] 表头双击自动复制列名（去掉 `table.` 前缀）到剪贴板

### 新增/修改文件

| 文件 | 操作 | 改动 |
|------|------|------|
| `MainFrame.java` | 修改 | 右键菜单 + 拆分编辑器 + 本地历史 + open-in + 快捷键 + reapplyTheme 分屏同步 |
| `KylinPlSqlApp.java` | 修改 | tabInsets/contentAreaInsets/tabAreaInsets |
| `ResultPanel.java` | 修改 | 缩略图点击偏移修复 + 表头双击复制列名 |
| `SettingsDialog.java` | 修改 | dividerLocation + resizeWeight |
| `RightPanel.java` | 修改 | 缩略图 Y 坐标计算 |
| `task_log.md` | 修改 | 本轮所有修复记录 |
| `USER_MANUAL.md` | 修改 | 新增功能说明 |
| `KEYBOARD_SHORTCUTS.md` | 修改 | Ctrl+Shift+F10 快捷键 |

### 测试记录
- `mvn compile` — 编译通过

---

## M21: Bug 修复 — 主题预览 + 全局搜索 Ctrl+P ✅ (2026-06-27)

### Bug 1：SettingsDialog 主题预览未随设置切换

**症状**：在设置对话框的主题选项中切换不同主题，但编辑器预览始终显示初始主题，不跟随切换。

**根因**：`SettingsDialog` 中的预览编辑器 `RSyntaxTextArea` 没有监听主题切换事件。`ThemeManager` 的监听器注册在设置关闭后，预览编辑器使用的颜色/高亮方案仍然是初始主题时的值。

**修复**：
1. `SettingsDialog` 中为主题分类（`themeList`）添加 `ListSelectionListener`，在选择新主题时：
   - 通过 `ThemeManager` 临时切换主题
   - 调用 `previewArea.updateUI()` 刷新 UI
   - 重新加载 `RSyntaxTextArea` 的 `.xml` 主题文件
   - 更新语法颜色方案（`SyntaxScheme`）及行号颜色、括号匹配色等
2. 通过 `SwingUtilities.invokeLater()` 解决 `UIManager.setLookAndFeel()` 与 RSyntaxTextArea UI 更新的竞态问题
3. 发现 `RSyntaxTextArea` 的 `.xml` 主题文件和自定义 TokenMaker/FoldParser 在 `updateUI()` 后丢失，需重新设置

### Bug 2：全局搜索 Ctrl+P 在编辑器内有焦点时不生效

**症状**：菜单显示 Ctrl+P 为全局搜索快捷键，但在编辑器（`RSyntaxTextArea`）有焦点时按下无效，对话框不出现。

**根因 - 表层**：`GlobalSearchDialog` 构造时调用 `syncTheme()`，此时 `previewScroll` 尚未初始化，导致 `NullPointerException`，对话框构造失败。

```
Cannot invoke "javax.swing.JScrollPane.setBackground(java.awt.Color)" because "this.previewScroll" is null
  at GlobalSearchDialog.syncTheme(GlobalSearchDialog.java:283)
  at GlobalSearchDialog.<init>(GlobalSearchDialog.java:94)
  at MainFrame.showGlobalSearch(MainFrame.java:1342)
```

**根因 - 深层**：
1. JDK 17 的 `JTextComponent.setKeymap()` 调用 `updateInputMap()`，向 `InputMap` 链中插入 `KeymapWrapper`，该 Wrapper 包装了 `DefaultEditorKit` 的默认绑定（包括 `Ctrl+P → printAction`）
2. `KeymapWrapper.get(Ctrl+P)` **直接返回 Action 对象**（而非 String），绕过常见的 `ActionMap` 字符串覆盖手段
3. `KeymapActionMap.get(Action)` 有特殊逻辑：若 key `instanceof Action`，直接原样返回该 Action
4. 因此 `textArea.getActionMap().put("print", disabledAction)` 的写法无效

**修复**：

| # | 文件 | 改动 |
|---|------|------|
| 1 | `GlobalSearchDialog.java:94` | 将 `syncTheme()` 从构造器开始处移至所有 UI 组件初始化完成后 |
| 2 | `GlobalSearchDialog.java:270-286` | `syncTheme()` 中添加 `previewScroll`/`previewArea` 空指针保护 |
| 3 | `SqlEditorPanel.java:123-128` | 移除无效的 `getActionMap().put("print", disabledAction)`，替换为 `getInputMap().put(Ctrl+P, "none")` 阻止 KeymapWrapper 的打印绑定 |
| 4 | `SourceViewerPanel.java:165` | 同上 |
| 5 | `MainFrame.java:412-424` | 添加 `KeyEventPostProcessor` 兜底拦截已被消费的 Ctrl+P 事件 |
| 6 | `MainFrame.java:44` | 添加 `import java.awt.event.KeyEvent` |

### 排错指南：检查日志

应用日志文件位置：
```
~/.kylin-sql/logs/kylin-sql.log
~/.kylin-sql/logs/kylin-sql.{yyyy-MM-dd}.log  （按日期滚动，保留 30 天）
```

**每次遇到异常行为时，优先检查日志文件是否有最新时间点的 ERROR 级别异常。** 大多数 UI 无反应的问题（如对话框不弹出、按钮点击无效）往往是因为构造器或事件处理器中抛出了未被捕获的异常，日志中会有完整堆栈。

快速检查：
```bash
# Linux/Kylin
tail -100 ~/.kylin-sql/logs/kylin-sql.log | grep -A 20 "ERROR"

# Windows (PowerShell)
Get-Content ~\.kylin-sql\logs\kylin-sql.log -Tail 100 | Select-String "ERROR" -Context 0,20
```

### 测试记录
- `mvn compile -pl kylin-sql-ui -am -q` — 编译通过（无错误输出）

---

## M22: 全局搜索增强 + 右侧 Files 重构 + 多项修复 ✅ (2026-06-27)

### 22.1 全局搜索对话框增强

#### 标题显示匹配数 + 当前 Schema
- 搜索完成后标题显示 `全局搜索 [PUBLIC] - 找到 15 个相关项`
- 打开对话框时重置为 `全局搜索`

#### 元数据搜索限当前 Schema
- 去掉跨所有连接/schema 的遍历，改为只取当前激活标签页的 `getConnName()` + `getSchema()`
- 支持 `SqlEditorPanel` 和 `SourceViewerPanel` 两种标签页类型
- 无激活标签页或无 schema 时跳过元数据搜索，改为搜索所有编辑器 + 持久化文件

#### 预览区语法高亮 (RSyntaxTextArea)
- `JTextArea` → `RSyntaxTextArea(SYNTAX_STYLE_SQL)`
- `syncTheme()` 通过 `Theme.load(in).apply(previewArea)` 加载 RSTA 主题文件，主题切换自动跟随
- 关闭代码折叠、当前行高亮、行号

#### 编辑器匹配预览增强
- 上下文 ±15 行，搜索关键字黄色高亮 (`DefaultHighlighter`)
- 匹配行自动滚动到视口上 1/3 处 (`centerLineInPreviewInContext`)
- 支持编辑器匹配 + 文件匹配两种模式

#### 元数据匹配预览 (异步加载)
- 选中对象后 `SwingWorker` 异步加载:
  - TABLE → `SqlExecutor.generateDDL()`
  - VIEW/INDEX/SEQUENCE/SYNONYM → `DBMS_METADATA.GET_DDL` 直查
  - FUNCTION/PROCEDURE/PACKAGE → `SqlExecutor.getSource()`
- 前一个 worker 未完成时自动 `cancel(true)`
- 加载完成后同样进行关键词高亮

#### 钉住/取消钉住功能
- 右上角 `JToggleButton`：`📍`(未固定) / `📌`(已固定)
- **已固定**: ESC 不关闭、`windowLostFocus` 不关闭、窗口自带 X 不关闭
- **未固定**: ESC 关闭、外部点击关闭、窗口自带 X 关闭
- 每次 `showDialog()` 重置为未固定

#### 持久化文件内容搜索 (无 Schema 时)
- 读取 `saved_files.json` 中记录的 SQL 文件磁盘内容
- 搜索匹配后 `SearchResult` 携带 `filePath`，结果列表显示文件名
- 双击通过 `MainFrame.openOrSwitchToFile()` 打开
- 预览时从磁盘读取文件内容并展示上下文

#### 面板背景一致性
- `resultsList.setBackground(UIManager.getColor("Panel.background"))`
- `JSplitPane.setBackground(UIManager.getColor("Panel.background"))`
- `top`/`topRight` 保持 `setOpaque(false)` 透传 contentPane

### 22.2 右侧 FILES 面板重构

#### 树形结构 (JList → JTree)
- **根节点**: "SQL"（黄色文件夹图标，粗体 11px）
- **子节点**: 每个已保存文件（扩展名对应颜色字母图标 + 文件名 + 截断路径）
- 行高 38px 适配两行显示

#### 文件图标 (参照左侧对象树)
| 扩展名 | 颜色 | 字母 |
|--------|------|------|
| `.sql` | 蓝 `#337AB7` | S |
| `.csv` | 绿 `#27AE60` | C |
| `.txt` | 灰 `#7F8C8D` | T |
| `.xml` | 橙 `#E67E22` | X |
| `.json` | 紫 `#8E44AD` | J |
| `.log` | 浅灰 `#95A5A6` | L |
| 其他 | 浅灰 `#95A5A6` | F |

#### 右键菜单
- **在标签页中打开** — 双击等效
- **打开文件所在位置** — `Desktop.getDesktop().open(parentFile)`
- **删除文件** — 仅从 `saved_files.json` 移除
- **永久删除文件** — `JOptionPane` 二次确认后删除磁盘文件 + 移除记录

#### 选中背景色修复
- `FileTreeRenderer` 使用 `theme.resolve("selection.listBg")`/`"selection.listFg"`
- 面板本身 `setOpaque(true)` + 正确背景色

### 22.3 syncTheme() 颜色修复

#### 问题
`syncTheme()` 用第一个编辑器标签背景色做亮度启发式判断 + 硬编码颜色值，不匹配当前主题。

#### 修复
改为 `UIManager.getColor("Panel.background")` / `"Label.foreground"` / `"TextArea.background"` / `"TextArea.foreground"`，主题切换时始终与 FlatLaf 一致。

### 改动文件

| 文件 | 改动 |
|------|------|
| `GlobalSearchDialog.java` | 标题匹配数、元数据限 schema、RSyntaxTextArea 预览、上下文高亮居中、DDL/源码异步加载、钉住/取消钉住、持久化文件搜索、面板背景一致性 |
| `MainFrame.java:1358` | 构造 GlobalSearchDialog 传入 configManager + fileOpener |
| `RightPanel.java` | JList→JTree 两层结构、两行渲染器、文件夹图标、文件图标、右键菜单、选中背景色修复 |

### 测试记录
- `mvn compile -pl kylin-sql-ui -am -q` — 编译通过

---

## 23. 自动保存 + 图标/主题修复 (2026-06-27)

### 23.1 右侧 FILES 树路径截断

#### 问题
`FileTreeRenderer` 硬编码 `"Segoe UI"` 字体，路径过长时 `...` 截断不准确；窗口 resize 后宽度变化不重新计算截断；子节点缩进过大。

#### 修复
- 使用 `t.getFont()` 而非硬编码字体，`fontMetrics` 跟随主题字体
- 添加 `ComponentAdapter.componentResized` → `tree.repaint()` 触发重绘
- 设置 `Tree.leftChildIndent=3, rightChildIndent=5` 减小缩进

### 23.2 自动保存

#### 功能
- **ConfigManager**: 新增 `isAutoSaveEnabled()` / `getAutoSaveInterval()` / `getAutoSaveUnit()` / `getAutoSavePath()`，存入 `preferences.json`
- **SettingsDialog**: 添加"自动保存"树节点 + 面板（启用勾选框、间隔微调+单位下拉、路径输入+浏览按钮）；Apply/OK 保存并重启定时器，Cancel 恢复快照
- **MainFrame**: `javax.swing.Timer` 单次触发 + 重复模式；`restartAutoSaveTimer()` 读取配置启动定时器；`autoSaveAll()` 遍历 editorTabs，保存已修改的 `SqlEditorPanel` 到自动保存目录
- **文件名安全**: `autoSaveFileName()` 先剥离 `"* "` 前缀，再替换 Windows 非法字符 `[\/*?\"<>|]` 为 `_`

### 23.3 Services 树图标

- 根节点 → 文件夹图标（黄色）
- 连接节点 → 橙色 **C**
- 已保存文件叶子 → 蓝色 **F**
- 未保存叶子 → 红色 **S**
- 过滤掉 `SourceViewerPanel` 类型的 tabs

### 23.4 Services 树右键菜单

- 添加 `MouseListener` 弹出菜单
- 菜单项 "重新打开已关闭标签页" → 调用 `MainFrame.reopenClosedTab()`

### 23.5 GlobalSearchDialog 主题同步重写

#### 问题
`syncTheme()` 使用 `UIManager.getColor()` 读取颜色。但 GREEN 主题底层 FlatLaf 仍是 `"LIGHT"`，`UIManager` 返回浅灰而非绿色，导致主题切换失效。

#### 修复
- `syncTheme()` 全部改为从 `ThemeManager.resolve()` 读取，`UIManager.getColor()` 做降级
- `searchField` 使用 `TextField.background`（而非 `TextArea.background`），与 updateComponentTreeUI 默认一致
- 移除所有 `setOpaque(false)` → 全部 opaque，每个组件 `setBackground(panelBg/fg)`
- 将 `top`, `topRight`, `listScroll`, `split` 局部变量提升为字段

#### switchTheme() 顺序调整
`MainFrame.switchTheme()` 执行顺序改为：
```
setLookAndFeel → updateComponentTreeUI(MainFrame) → ThemeManager.switchTo() → reapplyTheme()
```
确保 ThemeManager 监听器触发时 `UIManager` 已是最新。

#### 独立 JDialog 处理
GlobalSearchDialog 是独立 `JDialog`，不在 MainFrame 组件树中。其 ThemeManager 监听器额外调用：
```
SwingUtilities.updateComponentTreeUI(dialog) → syncTheme() → 分割线位置保存/恢复
```

### 23.6 Tab 标题 @connectionName 去重

#### 问题
`getTabTitle()` 拼接 `" @connectionName"` 时出现重复（如 `"tab @ORCL @ORCL"`）或错误标记（旧连接断开后标记与新连接不匹配）。

#### 修复
- `lastIndexOf(" @")` 检测后缀，仅当后缀匹配 `[a-zA-Z0-9_\-]+` 才触发修正
- 单个标记与当前 `connectionName` 一致 → 保留
- 单个标记不匹配 → 剥离后重新添加正确标记
- 多个标记 → 全部剥离后重新添加

### 23.7 默认标签名

`"console"` → `"sql"`（`MainFrame.getNextConsoleName()` 及 `SqlEditorPanel` 构造 fallback）

### 23.8 Tab 图标

- 编辑器标签页：已保存（`filePath != null`）→ 绿色 **S**，未保存 → 红色 **S**
- Services 树：已保存 → 蓝色 **F**，未保存 → 红色 **S**（与文件节点区分）

### 改动文件

| 文件 | 改动 |
|------|------|
| `RightPanel.java` | FileTreeRenderer font 动态获取、resize 监听器、child indent 缩小 |
| `ConfigManager.java` | auto-save getter/setter 方法 |
| `SettingsDialog.java` | 自动保存面板 UI + ConfigManager 参数 |
| `MainFrame.java` | 自动保存定时器/保存/文件名、switchTheme() 顺序、tab 图标、默认标签名 |
| `BottomPanel.java` | ConnTreeRenderer 图标、右键菜单、过滤 SourceViewer、font 修复 |
| `GlobalSearchDialog.java` | syncTheme() 重写、字段提升、独立 updateComponentTreeUI |
| `SqlEditorPanel.java` | getTabTitle() 去重、默认 tabName "sql" |
| `AppTheme.java` | （仅记录）GREEN 主题 flatlaf=LIGHT 是 UIManager 颜色不匹配的根因 |

### 测试记录
- `mvn compile -pl kylin-sql-ui -am -q` — 编译通过

---

## 24. 元数据配置 + OceanBase 对象浏览器增强 ✅ (2026-06-28)

### 完成项

#### Step 1: 数据模型 + 持久化
- [x] `DbMetadataConfig.java` — 数据模型（`TypeDef` / `CustomColumn` 内部类），含 Oracle / OceanBase / MySQL / PostgreSQL 4 组默认值
- [x] `ConfigManager.java` — 新增 `loadMetadataConfigs()` / `saveMetadataConfigs()` / `resetMetadataConfigs()`，JSON 存储于 `preferences.json["metadataConfigs"]`
- [x] `ObjectBrowser.java` — 新增 `OB_ORACLE_TYPES` 常量组，`detectTypes()` 增加 `oceanbase` 分支

#### Step 2: Settings 对话框 UI
- [x] 左侧树新增「元数据配置」节点 → Oracle / OceanBase / MySQL / PostgreSQL 子节点
- [x] 总览面板：启用/禁用 checkbox 表格
- [x] 每数据库类型编辑面板：启用 checkbox + 类型定义编辑表格（标签/类型码/查询来源/可展开）+ SQL 编辑区 + 自定义列表格 + 增删改按钮 + 恢复默认按钮
- [x] Apply 保存、Cancel 恢复原值

#### Step 3: ObjectBrowser 集成
- [x] `ConfigManager` 通过 `setConfigManager()` 注入 ObjectBrowser
- [x] `detectTypes()` 优先检查配置：启用 → 从配置读取 TypeDef；未启用 → 硬编码回退
- [x] `ObjectType` 扩展支持 `fixedValues`（FIXED_LIST 来源走内存，不查库）
- [x] `queryObjects()` 对 FIXED_LIST 直接返回预定义值，不走 SQL
- [x] `loadConnection()` 处理 FIXED_LIST 类型的对象加载
- [x] `dbProductToKey()` 辅助方法

### 附带改进

#### 视觉调整
- [x] **窗口标题**: `"Kylin PL/SQL Developer"` → `"Kylin SQL Developer"`
- [x] **应用 Logo**: 蓝底 `"Kylin"` → 蓝底 `"K"`，蓝红徽章连接处压平（无圆角）
- [x] **调试边框移除**: leftSplit 和 mainSplit 边框清除
- [x] **左侧面板折叠修复**: 点击已激活标签时折叠面板
- [x] **工具栏上边框**: `MatteBorder(0,0,1,0)` → `MatteBorder(1,0,1,0)`
- [x] **面板边框统一**: tabStrip / contentPanel / tabContainer / BottomPanel 两边或四边增加 `border.light` 分割线
- [x] **VerticalTabButton 字号+宽度**: 10pt → 11pt，strip 宽度 26 → 28，全组件同步
- [x] **菜单栏背景同步**: `UIManager.put("MenuBar.background", ...)` + 递归 JMenu/JMenuItem 更新

#### 状态栏
- [x] **锁图标颜色**: 🔒 红色 `#D9534F`，🔓 绿色 `#5CB85C`
- [x] **MemoryBar 重设计**: 全高柱状条 + 2px 内边距 + `border.light` 圆角边框 + 深色背景 + 比例填充 + 文字覆盖
- [x] **Navigate 对话框美化**: Spinner 宽度 80→120px，新增 Cancel 按钮，Enter/Escape 快捷键绑定，主题色感知

#### 结果集
- [x] **单元格双击复制**: 双击单元格复制内容到剪贴板 + Toast 反馈

### 改动文件

| 文件 | 改动 |
|------|------|
| `DbMetadataConfig.java` (新建) | 数据模型 + 4 组默认值 |
| `ConfigManager.java` | metadata config load/save/reset 方法 |
| `ObjectBrowser.java` | OB_ORACLE_TYPES、detectTypes 配置优先、FIXED_LIST 支持、ConfigManager 注入 |
| `SettingsDialog.java` | MetadataNode 内部类、元数据配置树节点 + 编辑面板 |
| `MainFrame.java` | 注入 ConfigManager；窗口标题；Logo；调试边框移除；菜单栏背景同步；tabContainer 边框 |
| `LeftPanel.java` | collapse 逻辑修复；contentPanel/tabStrip 边框；宽度 28px 同步 |
| `RightPanel.java` | contentPanel/tabStrip 边框；宽度 28px 同步 |
| `StatusBar.java` | MemoryBar 重设计；Navigate 美化；锁颜色 |
| `VerticalTabButton.java` | font 11pt；width 28 |
| `ResultPanel.java` | 单元格双击复制 |
| `BottomPanel.java` | 顶部 border.light 边框 |
| `USER_MANUAL.md` | 元数据配置文档；窗口标题更新；配置文件表 |

### 测试记录
- `mvn compile -pl kylin-sql-ui -am -q` — 编译通过

---

## 25. 工具菜单设计 ✅ (2026-06-28)

### 完成项
- [x] 设计并实现 **工具菜单**（菜单栏 → 工具）
- [x] **SQL 工具对话框**: 字符串转义 / IN 格式转换 / SQL 格式化 三合一

### 规划中的工具

| 工具 | 说明 | 优先级 |
|------|------|--------|
| SQL 工具 ✅ | 转义、IN转换、格式化 | 高 |
| 数据生成器 | 按表结构智能生成测试数据 | 中 |
| SQL 历史 | 会话执行记录浏览/搜索/回填 | 中 |
| 文本比较 | 双栏 diff 对比 | 中 |
| 正则测试器 | 实时正则匹配测试 | 中 |
| 对象搜索 | 跨 Schema 全局搜索数据库对象 | 中 |
| 高级导出 | 结果集导出为 INSERT/JSON/XML/Markdown | 低 |

### 改动文件

| 文件 | 改动 |
|------|------|
| `MainFrame.java` | 工具菜单条目 + SQL 工具对话框入口 |
| `dialog/SqlToolsDialog.java` (新建) | SQL 工具三栏对话框 |
| `USER_MANUAL.md` | 新增工具菜单 §3.8 |

### 测试记录
- `mvn compile -pl kylin-sql-ui -am -q` — 编译通过

---

## 26. 代码审计 — 问题修复 (2026-06-29)

### 审查范围
扫描 31 个 UI 源文件 + 29 个 Core 源文件，发现 7 类 12 个问题。

### P0 — 事务连接被意外关闭（3 处，已修复）

**根因**: `try-with-resources` 会关闭 `ConnectionManager.getConnection()` 返回的连接。手动事务模式下返回的是专用事务连接，关闭它会丢失未提交的事务。`executeSql()` 此前已修复，但其余 3 处漏修。

**修复模式**: 替换 `try-with-resources` 为 `isAutoCommit()` 判断 + finally 条件 close。

| 位置 | 改动 |
|------|------|
| `MainFrame.java` `bottomPanel.setRefreshExecutor` 回调 | `try-with-resources` → 手动 finally |
| `MainFrame.java` `explainPlan()` | 同上 |
| `MainFrame.java` `onObjectAction()` doInBackground 外层 + PREVIEW 内层 | 两层均改为手动模式 |

### P1 — `writeFile()` 写错标签组件（已修复）

**症状**: 保存文件后标签标题不更新。

**根因**: `MainFrame.java:1097` 用 `tabPanel.getComponent(0)` 获取标题标签，但 `buildTabComponent()` 中索引 0 是图标标签，标题标签在索引 2。

**修复**: `getComponent(0)` → `getComponent(2)`

### P1 — 对话框缺少主题响应（3 个，已修复）

| 文件 | 改动 |
|------|------|
| `ConnectionDialog.java` | 添加 `applyTheme()` + 构造器末尾调用 |
| `SettingsDialog.java` | 同上 |
| `CallHierarchyDialog.java` | 同上 |

### P2 — `StatusBar.setStatusText()` Timer 覆盖（已修复）

**症状**: 3 秒内连续调用 `setStatusText()`，前一个 Timer 仍会触发并清除文本。

**根因**: 没有保存 Timer 引用，创建新 Timer 前未停止旧的。

**修复**: 添加 `statusTextTimer` 字段，创建新 Timer 前 `stop()` 旧的。

### P2 — `ThumbnailContent` DocumentListener 泄漏（已修复）

**症状**: 切换编辑器标签时，每次都会向旧 editor 的 document 添加新监听器且不移除旧的，冗余监听器累积。

**修复**: 添加 `previousEditor`/`currentDocListener` 字段，add 新 listener 前 `removeDocumentListener()` 旧的。

### P2 — SQL 注入风险（DDL 生成路径，未修复）

**风险**: `SqlExecutor.tryOracleDdl()`/`buildDDLFromMeta()` 中多处使用字符串拼接构建 SQL 查询。虽转义了单引号，但查询值可能来自用户输入。

**暂缓原因**: 查询仅读元数据不写数据，改动面大。

### P3 — 硬编码颜色 30+ 处（未修复）

分布：`MainFrame`（图标/按钮/toast 色）、`StatusBar`（连接点/锁/内存条）、`ResultPanel`（关闭按钮/图标）、`RightPanel`（文件图标）、`SqlEditorPanel`（按钮色）等。

需先明确语义豁免规则再批量替换。

### 改动文件

| 文件 | 改动 |
|------|------|
| `MainFrame.java` | P0 ×3 + P1 writeFile 标签索引 |
| `StatusBar.java` | P2 Timer 字段 + setStatusText 停止旧 Timer |
| `RightPanel.java` | P2 DocumentListener 防泄漏 |
| `ConnectionDialog.java` | P1 applyTheme |
| `SettingsDialog.java` | P1 applyTheme |
| `CallHierarchyDialog.java` | P1 applyTheme |

### 测试记录
- `mvn compile -q` — 编译通过

---

## 27. SettingsDialog UI 重构 — 左侧树 + SQL Format 子面板 + 实时预览 (2026-06-30)

### 完成项

- [x] **布局重构**: `JTabbedPane` → `JSplitPane`（左树 + 右 CardLayout）
- [x] **左侧树导航**:
  ```
  ├── 通用 (General)
  ├── SQL 格式化 (SQL Formatting)
  ├── 主题 (Theme)
  ├── 编辑器 → 自动保存 (Editor → Auto Save)
  └── 数据库 → 元数据配置 (Database → Metadata)
  ```
- [x] **SQL 格式化子面板（5 个标签页）**:
  - **通用**: 关键字大小写、缩进、最大行宽、换行符、逗号位置
  - **DQL**: SELECT 列模式、FROM/JOIN 换行、ON 对齐、AND/OR 位置
  - **DML**: INSERT 列紧凑模式、UPDATE SET 对齐
  - **DDL**: 列定义对齐、存储子句格式
  - **PL/SQL**: 声明对齐、参数列表、括号间距、THEN/LOOP/ELSE 换行、EXCEPTION 对齐
- [x] **实时预览**: `RSyntaxTextArea`，4 种 SQL 模板可选，参数调整即时刷新
- [x] **Profile 管理**: 保存/删除自定义 Profile，下拉切换
- [x] **主题同步**: `applyTheme()` 覆盖左树、卡片面板、预览区颜色
- [x] **保留所有原有功能**: 通用(连接列表)、主题(色板编辑器)、自动保存(间隔/路径)、元数据配置(类型/SQL/扩展列)

### 改动文件

| 文件 | 改动 |
|------|------|
| `SettingsDialog.java` | 完全重写 — 左侧树 + 5 个 SQL Format 子面板 + 实时预览 + Profile 管理 |

### 测试记录
- `mvn compile -q` — 编译通过
