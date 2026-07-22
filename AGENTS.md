
## Summary

### Goal
全面重写 SQL 格式化引擎为模板驱动的体系，支持 4 种数据库方言（Oracle/OceanBase/MySQL/PostgreSQL），40+ 全方位可配置参数，DataGrip 风格设置 UI。

### Progress

| Phase | 内容 | 状态 | 文件 |
|-------|------|------|------|
| **1** | FormatOptions 40+ 参数 + 3预设Profile + FormatTemplate + TemplateRegistry + SqlType + SqlTypeClassifier | ✅ 完成 | FormatOptions.java, FormatTemplate.java, TemplateRegistry.java, SqlType.java, SqlTypeClassifier.java |
| **2** | SqlDialect 接口扩展 + 4方言完整关键字集（Oracle/OB/MySQL/PG）| ✅ 完成 | SqlDialect.java, OracleDialect.java, OceanBaseDialect.java, MySqlDialect.java, PostgreSqlDialect.java |
| **3** | SqlFormatter 模板化重构 — 基于模板的断行/缩进/对齐引擎，SqlTypeClassifier 分类 + TemplateRegistry 查模板 + FormatOptions 全参数驱动 | ✅ 完成 | SqlFormatter.java |
| **4** | SubqueryHandler 子查询检测+括号匹配+INLINE/EXPAND/AUTO判断 + SqlFormatter 集成子查询递归 | ✅ 完成 | SubqueryHandler.java, SqlFormatter.java |
| **5** | SettingsDialog UI 重构（左侧树+6面板+实时预览） | ⏳ 待开始 | SettingsDialog.java |
| **6** | 集成路由 + 方言选择持久化 + assembly | ⏳ 待开始 | MainFrame.java, ConfigManager.java |

### 编译状态
- kylin-sql-core: BUILD SUCCESS（40源文件，含 service 层 11 个新文件）
- kylin-sql-ui: BUILD SUCCESS（39源文件，含自动补全 + schema 执行修复 + 导出对话框服务化）
- assembly dist/ 清理仍偶发 ANTLR JAR 文件锁

### 架构变更

**旧架构：** `SqlFormatter.java` 硬编码关键字集 + 仅用 3 个 FormatOptions 参数 + 无 SQL 类型感知

**新架构（模板层）：**
```
SqlFormatter.format(source, options, dialect)
  ├── SqlTypeClassifier → SqlType
  ├── TemplateRegistry → FormatTemplate
  ├── FormatOptions → 40+ 参数全部生效
  └── TokenProcessor → 基于模板的断行/缩进/对齐 + 递归子查询
```
旧枚举（SelectColumnMode, WhereAndPosition 等 7 个文件）已被删除，全部合并到 `FormatOptions` 内部类。

**新架构（约束引擎层）：** 见 `kylin-sql-formatter/ARCHITECTURE.md`
- 数据模型分三层：`StructuralFrame`（层级）→ `AlignmentCover`（列）→ `FinalLayout`（输出）
- 模块编排四阶段：StructuralResolver → LineWidthResolver → AlignmentResolver → LayoutMerger
- 约束生成器分三层：缩进骨架 → 词法间距 → 语义对齐

### 待定设计决策
- 子查询展开风格（Inline/Expand/Auto）和位置独立控制
- 每行 N 列（columnsPerRow）机制
- SQL 类型分类的方言特定关键字路由

### 对话记录参考
- **当前会话（2026-07-22）**：自动补全 3 项修复 + Service 层（SchemaService/DataQueryService/ExportService/ServiceFactory 4服务12文件）+ 导出对话框"表"模式级联 + 编译 BUILD SUCCESS（core 40文件 / ui 39文件）
- **前一会话（2026-07-13）**：自动补全元数据体系 + 系统视图 + MetadataCache 预热
- **前一阶段**：格式化引擎完整重写，Phase 1-3 完成

## 操作规范

### Edit 范围最小化
用 edit 工具时，`oldString` 必须精确匹配**要改的最小局部**，不要包含不相关的周围代码。避免因替换范围过大而吞掉相邻的 listener、import 等逻辑。

例子：
- ❌ `oldString` 包含整个菜单块（5 行），只为了改其中 1 行 → 可能误删周围代码
- ✅ `oldString` 只包含目标行 + 前后 1 行上下文（最多 3 行）

### 改完后检查"邻居"
改完某段代码后，扫一眼其前后 10 行，确认没有意外影响到无关代码（如少删了 ActionListener、import、大括号配对）。

### 编译测试
每次改完必须 `mvn compile` 确认通过。若编译报错"找不到符号"，优先怀疑是误删了 import 或 listener 绑定。
## 待办
- **工具栏/菜单 SVG 图标锯齿感** — 2560×1600 高分屏下 16×16 图标有锯齿。`IconUtil.menuIcon()` 渲染质量不足，SVGSalamander 16×16 抗锯齿效果有限。`SmoothImageIcon`（重写 paintIcon 设双线性插值）会导致界面白屏，原因未明。需后续排查方案：改用 `FlatSVGIcon`（`flatlaf-extras`）或升级渲染方式。

- **Session 2026-07-13 自动补全：**
  - 将自动补全延迟设置移入 autosave 面板（删除独立节点/面板）
  - `AutoActivationListener` 始终未触发 → 改用 `SqlEditorPanel` 内 `DocumentListener` 直接调用 `ac.doCompletion()`
  - `PlSqlCompletionProvider` 重写：构造函数接收 `Supplier<String> connNameSupplier`；`getCompletionsImpl` 从 `MetadataCache` 查询表/视图/列
  - `MetadataCache` 新增 `getObjectNamesByType(connName, schema)` 返回 `Map<String, List<String>>`
  - 点后缀列补全（`tablename.` → 列列表）：`getTableBeforeDot()` 提取点前最后一个词 → `isKnownTable()` 直查 / `resolveAlias()` 通过正则`\b(?:FROM|JOIN|INTO)\s+table(?:\s+AS)?\s+alias` 解析别名
  - 单匹配自动补全保持默认（不调用 `setAutoCompleteSingleChoices`）

- **Session 2026-07-22 修复合集：**
  - NPE: `setDbTypeKey()` 将 `systemViews` 设为 `null` → `ensureSystemViews()` 抛 NPE → AutoCompletion 静默吞异常不弹窗。修复: `null` → `Collections.emptyMap()`
  - MySQL 大小写: 缓存表名全小写（`employee`）vs `upper`（`EMP`），`name.startsWith(upper)` 永远 false。修复 3 处：`name.toUpperCase().startsWith(upper)`、`col.name.toUpperCase().startsWith(upper)`、`isKnownTable` 改为 `equalsIgnoreCase`
  - Schema 不切换: SQL 执行始终跑连接池默认 schema。修复: `executeSql()` 读 `editor.getSchema()`，新增 `applySchemaIfNeeded(conn, connName, schema)` 方法，按 dbProduct 分支执行 `USE / ALTER SESSION / conn.setSchema`
  - 诊断日志: 日志从 DEBUG 改为 INFO `[DIAG]` 前缀（root logger 为 INFO），完成诊断后建议清理
  - Service 层: SchemaService（抽象+3方言）+ DataQueryService（抽象+3方言）+ ExportService + ServiceFactory + DataPreview model，共 12 文件，kylin-sql-core 40 源文件
  - AdvancedExportDialog 重构: ServiceFactory 注入，3 模式切换（结果集/表/自定义SQL），"表"模式 conn→schema→table 级联，加载列+预览 10 行，自动填充 tableNameField
