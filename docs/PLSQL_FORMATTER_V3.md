# PL/SQL 格式化引擎 — 架构设计文档 v3

---

## 整体设计

## 1.1. 设计原则

1. **ParseTree 驱动** — 块结构不从 token 流推导，而从 ANTLR4 生成的 CST（ParseTree）提取
2. **约束求解** — 每个 whitespace 间隙受约束控制，求解器找出最优解
3. **分层隔离** — 解析/模型/约束/求解/输出 各层独立，可单独替换
4. **容错** — 语法错误通过 ANTLRErrorListener 捕获为 Diagnostic，不崩溃
5. **向后兼容** — `PlSqlFormatter.format(source, options)` 入口不变，FormatResult 不变

## 1.2. kylin-sql-formatter 子模块

### 1.2.1 定位

`kylin-sql-formatter` 是从原 `kylin-sql-core` 中提取的独立 Maven 子模块，
负责 SQL/PL/SQL 格式化引擎的全部逻辑。与 `kylin-sql-core` 解耦，
仅依赖 ANTLR4 运行时和 JDK 标准库，可作为独立 JAR 在非 GUI 项目中复用。

### 1.2.2 Maven 坐标

```xml
<groupId>com.kylin.plsql</groupId>
<artifactId>kylin-sql-formatter</artifactId>
<version>3.0.0</version>
<packaging>jar</packaging>
```

### 1.2.3 模块依赖

```
kylin-sql-formatter
├── antlr4-runtime (4.13.2, compile)
├── JDK 17+ (provided)
├── kylin-plsql-parser (compile, ANTLR grammar JAR)
├── slf4j-api (2.x, optional)
└── junit-jupiter (5.x, test)
```

不依赖任何 GUI 框架（Swing/FlatLaf/RSyntaxTextArea），无 Spring/Guice 等容器依赖。

### 1.2.4 项目目录结构

```
kylin-sql-formatter/
├── pom.xml
├── src/
│   ├── main/java/com/kylin/plsql/formatter/
│   │   ├── PlSqlFormatter.java              ← 入口类（public API）
│   │   ├── FormatOptions.java               ← 参数模型
│   │   ├── FormatResult.java                ← 格式化结果
│   │   ├── builder/
│   │   │   ├── PlSqlModelBuilder.java
│   │   │   └── ParseTreeModelBuilder.java
│   │   ├── dialect/
│   │   │   ├── PlSqlDialect.java            ← 方言接口
│   │   │   ├── OraclePlSqlDialect.java
│   │   │   ├── MySqlPlSqlDialect.java
│   │   │   ├── PostgreSqlPlSqlDialect.java
│   │   │   ├── OceanBaseOraPlSqlDialect.java
│   │   │   └── PlSqlDialectFactory.java
│   │   ├── formatter/
│   │   │   ├── PlSqlFormatterEngine.java
│   │   │   └── layout/
│   │   │       ├── GapConstraint.java
│   │   │       ├── ConstraintGenerator.java
│   │   │       ├── ConstraintSolver.java
│   │   │       └── StringAssembler.java
│   │   ├── model/
│   │   │   ├── PlSqlBlock.java
│   │   │   ├── PlSqlBlockType.java
│   │   │   ├── TokenInfo.java
│   │   │   ├── Diagnostic.java
│   │   │   ├── Statement.java
│   │   │   ├── Declaration.java
│   │   │   └── ...
│   │   ├── qa/
│   │   │   └── PlSqlQualityChecker.java
│   │   └── post/
│   │       └── PostProcessor.java
│   └── test/java/com/kylin/plsql/formatter/
│       ├── PlSqlFormatterTest.java
│       ├── layout/ConstraintSolverTest.java
│       └── dialect/DialectTestSuite.java
```

### 1.2.5 公共 API

```java
// 唯一公开入口
public final class PlSqlFormatter {

    /**
     * @param source  源 SQL/PL/SQL 文本
     * @param options 格式化参数（可 null = 默认配置）
     * @param dialect 方言标识（可 null = 自动检测）
     * @return 格式化结果（含格式化文本 + 诊断信息列表）
     */
    public static FormatResult format(
        String source,
        FormatOptions options,
        String dialect
    );

    // 简化重载
    public static FormatResult format(String source);
    public static FormatResult format(String source, FormatOptions options);
}
```

`PlSqlFormatter` 是仅有的公开类，所有其他类均为包内可见（package-private）或内部实现。
外部调用者只需依赖 `PlSqlFormatter`、`FormatOptions`、`FormatResult` 三个类。

### 1.2.6 发布物

| 产物 | 说明 |
|------|------|
| `kylin-sql-formatter-3.0.0.jar` | 编译后的主 JAR |
| `kylin-sql-formatter-3.0.0-sources.jar` | 源码 |
| `kylin-sql-formatter-3.0.0-javadoc.jar` | API 文档 |

不 shade/uberjar ANTLR 运行时——由消费方自行管理依赖。

### 1.2.7 集成到主项目

```xml
<!-- kylin-sql-core/pom.xml -->
<dependency>
    <groupId>com.kylin.plsql</groupId>
    <artifactId>kylin-sql-formatter</artifactId>
    <version>3.0.0</version>
</dependency>
```

`kylin-sql-core` 通过 `PlSqlFormatter.format()` 调用格式化引擎，
将结果展示在 `SqlToolsDialog` 的格式化结果面板中。
`kylin-sql-ui` 的 `SettingsDialog` 读写 `FormatOptions`，传递给引擎。`

---

## 2. 整体架构与数据流

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PlSqlFormatter.format()                      │
│  public static FormatResult format(String source, FormatOptions)    │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Pipeline:                                                        │
│                                                                     │
│  ┌──────────┐   ┌──────────┐   ┌─────────────────┐   ┌──────────┐ │
│  │  Lexer   │──▶│  Parser  │──▶│  ModelBuilder   │──▶│  Engine  │ │
│  │ (ANTLR)  │   │ (ANTLR)  │   │ (ParseTree →     │   │ (约束求解)│ │
│  └──────────┘   └──────────┘   │  PlSqlBlock树)   │   └──────────┘ │
│                                └─────────────────┘        │       │
│                                                           ▼       │
│  ┌──────────┐   ┌──────────┐   ┌─────────────────┐   ┌──────────┐ │
│  │ Quality  │◀──│  Result  │◀──│  LayoutEngine   │◀──│  String  │ │
│  │ Checker  │   │  (文本)  │   │ (约束传播+DP折行) │   │Assembler │ │
│  └──────────┘   └──────────┘   └─────────────────┘   └──────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 词法分析 → TokenInfo[]

ANTLR4 `PlSqlLexer` 对输入源码进行词法分析，输出 `Token` 流。词法分析阶段不进行任何语义判断，仅完成文本到 token 的映射。

```
源文本: "SELECT * FROM employees;"
    ↓ Lexer
Token: [SELECT, *, FROM, employees, ;]
```

`TokenInfo` 结构：

```java
class TokenInfo {
    int    index;        // 全局 token 索引
    String text;         // 原始文本
    String upper;        // 大写形式（用于关键字匹配）
    int    type;         // ANTLR token type
    int    channel;      // DEFAULT / HIDDEN（注释/空白）
    int    line;         // 源行号
    int    charPosition; // 源列号
    boolean isKeyword;   // 是否方言关键字
    boolean isComment;   // 是否注释
    boolean isStringLiteral; // 是否字符串字面量
    boolean isQuotedIdentifier; // 是否引用标识符
    boolean isWhitespace; // 是否空白 token
}
```

### 2.3 语法分析 → ParseTree

ANTLR4 `PlSqlParser` 接收 Token 流，按语法规则生成 ParseTree（CST）。每个语法规则对应一个 `ParserRuleContext`，树中每个节点携带精确的语法角色信息。

```
ParseTree（简化）:
    select_statement
    ├── SELECT
    ├── select_list
    │   ├── column_ref (employee_id)
    │   ├── column_ref (first_name)
    │   └── column_ref (last_name)
    ├── FROM
    └── table_ref (employees)
```

ParseTree 的优势：
- **结构精确**：每个节点已知自己的语法角色（JOIN、WHERE、GROUP BY 等）
- **方言无关**：不同方言的语法规则由不同 grammar 文件处理，输出的 ParseTree 结构一致
- **容错**：语法错误不影响树生成，仅标记错误节点

### 2.4 ParseTree → PlSqlBlock 树

`ParseTreeModelBuilder` 遍历 ParseTree，将语法树转换为格式化的 `PlSqlBlock` 树。每个 `PlSqlBlock` 对应一个语法块（IF、LOOP、FUNCTION 等）。

```java
class PlSqlBlock {
    PlSqlBlockType  type;             // 块类型
    int             startTokenIdx;    // 块起始 token 索引
    int             endTokenIdx;      // 块结束 token 索引
    int             declStartIdx;     // DECLARE 起始索引（-1 若无）
    int             bodyStartIdx;     // BEGIN/LOOP/THEN 起始索引
    int             exceptionStartIdx;// EXCEPTION 起始索引（-1 若无）
    List<PlSqlBlock> children;        // 子块
    List<Statement>  statements;      // 语句列表
    List<Declaration> declarations;   // 声明列表
    // 块特有数据
    IfBranch[]      ifBranches;       // IF 分支
    CaseWhen[]      caseWhens;        // CASE 分支
    // ...
}
```

转换映射示例：

```java
// 每个语法规则 → 对应 PlSqlBlockType
ctx.if_statement()     → PlSqlBlockType.IF
ctx.loop_statement()   → PlSqlBlockType.FOR_LOOP / WHILE_LOOP / LOOP
ctx.case_statement()   → PlSqlBlockType.CASE_BLOCK
ctx.create_procedure() → PlSqlBlockType.PROCEDURE
```

### 2.5 约束生成 (PlSqlBlock → GapConstraint[])

`ConstraintGenerator` 遍历 `PlSqlBlock` 树，为每个 token 间隙生成 `GapConstraint`。

```java
class GapConstraint {
    int fromTokenIdx;        // 间隙起始 token
    int toTokenIdx;          // 间隙结束 token (通常 from+1)

    // 空格
    int minSpaces        = 1;
    int maxSpaces        = Integer.MAX_VALUE;
    int preferredSpaces  = 1;

    // 换行
    NewlineMode newlineMode = OPTIONAL;   // FORBIDDEN / REQUIRED / OPTIONAL
    int indentDelta      = 0;            // 换行时缩进增量（相对父块）
    Boolean endAlign     = null;         // true=端对齐, false=正常缩进

    // 对齐组
    String alignGroupId;                 // 同组内取最大宽度对齐

    // 空行
    boolean blankLineBefore = false;     // 换行前需加一个空行

    // 代价（用于 DP 折行）
    double breakPenalty  = 1.0;          // 在此断行的代价
}
```

每个 `PlSqlBlock` 类型对应一个约束生成方法。示例：

```java
class IfConstraintGenerator implements ConstraintGenerator {
    void generate(PlSqlBlock block, FormatOptions opts, List<GapConstraint> out) {
        if (opts.isThenOnNewLine()) {
            gap(before(THEN), THEN).forceNewline(true).indent(+1);
        }
        for (IfBranch branch : block.ifBranches) {
            GapConstraint g = gap(before(branch), branch.startTokenIdx);
            g.forceNewline(true);
            g.indentDelta(branch.type == IF ? 1 : 0);
        }
        if (opts.isEndAlign()) {
            gap(afterLastStmt, END).forceNewline(true).endAlign(true);
        } else {
            gap(afterLastStmt, END).forceNewline(true).indent(+1);
        }
    }
}
```

### 2.6 约束求解器

#### Step 1: 硬约束传播（确定性）

遍历所有 `GapConstraint`，应用强制约束：

```java
for (GapConstraint g : constraints) {
    if (g.newlineMode == FORBIDDEN) {
        solver.removeNewline(g.fromTokenIdx, g.toTokenIdx);
    } else if (g.newlineMode == REQUIRED) {
        solver.forceNewline(g.fromTokenIdx, g.toTokenIdx, g.indentDelta);
    }
}
```

此阶段确定所有必须换行和必须不换行的位置。

#### Step 2: DP 折行优化（非确定性）

对 `OPTIONAL` 间隙，使用动态规划选择最优换行点：

- **状态**：`dp[i]` = 处理到第 i 个 token 的最小总代价
- **转移**：`dp[j] = min(dp[i] + cost(i, j))`，其中 `(i, j)` 构成一行
- **代价函数**：行超长惩罚 + 断点不美观惩罚 + breakPenalty
- **约束**：行宽 ≤ `maxLineWidth`（无硬换行机会时放宽）

```
行宽 check: line.length() + indent <= maxLineWidth
    ├── 满足 → 继续加入 token
    └── 超出 → 在前一个 OPTIONAL 间隙处断行，或允许超长（无合适断点）
```

### 2.7 StringAssembler

将约束求解结果转换为最终格式化文本：

```java
class StringAssembler {
    void assemble(List<GapConstraint> solved, List<TokenInfo> tokens, StringBuilder out) {
        for (int i = 0; i < tokens.size(); i++) {
            // 输出 token 文本
            out.append(tokens.get(i).text);
            // 检查间隙约束
            GapConstraint g = solved.getGap(i, i+1);
            if (g.newlineMode == REQUIRED) {
                out.append('\n');
                out.append(repeat(' ', g.indentDelta));
            } else {
                out.append(repeat(' ', g.preferredSpaces));
            }
        }
    }
}
```

### 2.8 PostProcessor

格式化后的文本经过后处理：

- 关键字大小写转换（按 `FormatOptions.keywordCase`）
- 注释保护恢复（将占位符替换回原始注释）
- 空行处理（保留/压缩/删除）
- 行尾空格修剪
- 换行符统一（LF/CRLF）

---

## 3. 包结构

```
com.kylin.plsql.core.format.plsql/
├── builder/
│   ├── PlSqlModelBuilder.java         ← ParseTree → PlSqlBlock 树
│   └── ParseTreeModelBuilder.java     ← visitor 实现
├── dialect/
│   ├── PlSqlDialect.java              ← 方言接口（§4.2）
│   ├── OraclePlSqlDialect.java        ← Oracle 方言
│   ├── MySqlPlSqlDialect.java         ← MySQL 方言
│   ├── PostgreSqlPlSqlDialect.java    ← PostgreSQL 方言
│   └── OceanBaseOraPlSqlDialect.java  ← OceanBase 方言
├── formatter/
│   ├── PlSqlFormatterEngine.java      ← 重构: 替换为 LayoutEngine
│   ├── layout/
│   │   ├── GapConstraint.java         ← 间隙约束模型
│   │   ├── ConstraintGenerator.java   ← PlSqlBlock → GapConstraint[]
│   │   ├── ConstraintSolver.java      ← 约束求解器 (传播 + DP)
│   │   └── StringAssembler.java       ← 约束解 → 格式化文本
│   └── post/
│       └── PostProcessor.java         ← 关键字大小写/注释保护
├── model/
│   ├── PlSqlBlock.java
│   ├── PlSqlBlockType.java
│   ├── PlSqlModel.java
│   ├── TokenInfo.java
│   ├── Diagnostic.java
│   ├── FormatResult.java
│   ├── Statement.java
│   ├── Declaration.java
│   ├── IfBranch.java
│   ├── CaseWhen.java
│   ├── ExceptionSection.java
│   └── ConcatenationSegment.java
├── qa/
│   └── PlSqlQualityChecker.java
└── PlSqlFormatter.java                ← 入口不变
```

---

## 4. 方言系统

### 4.1 问题与设计决策

核心假设：**ANTLR ParseTree 自动消除方言消歧需求**。但该假设不完全成立：

| 维度 | 受 ParseTree 影响 | 仍需方言处理 |
|------|------------------|-------------|
| 块结构（IS/AS/BEGIN/END） | ✅ ParseTree 规则区分 | ❌ |
| END 匹配 | ✅ ParseTree 确定 | ❌ |
| PACKAGE/TYPE/TRIGGER 有无 | ✅ ParseTree 无节点则不创建块 | ❌ |
| **标识符引用符** | ❌ ANTLR lexer 模式混用 | ✅ 需方言指定 |
| **关键字大小写** | ❌ 关键字集因方言而异 | ✅ 各方言独立关键字集 |
| **字符串/美元定界符** | ❌ lexer 需方言模式 | ✅ PG $$ 需 lexer 扩展 |
| **方言特有 SQL 构造** | ❌ 格式化约束需识别 | ✅ CONNECT BY / ON CONFLICT 等 |
| **LIMIT/OFFSET 语法** | ❌ 格式化规则不同 | ✅ `LIMIT n OFFSET m` vs `OFFSET m ROWS FETCH NEXT n` |
| **多行 INSERT VALUES** | ❌ 方言语法不同 | ✅ MySQL `VALUES ROW()` / PG `ON CONFLICT` |

**设计决策**：方言系统提供插件化接口，各方言继承/实现，由 `PlSqlDialectFactory` 根据 `FormatOptions.dialect` 创建。

### 4.2 方言接口

```java
public interface PlSqlDialect {
    String getName();
    char getIdentifierQuote();
    char getStringDelimiter();
    boolean supportsDollarQuoting();
    Set<String> getSetOperators();
    Map<SqlConstruct, FormatTemplate> getConstructTemplates();
    Set<String> getKeywords();
    Set<PlSqlBlockType> getSupportedBlockTypes();
}
```

### 4.3 Oracle 方言

| 属性 | 值 |
|------|-----|
| 标识符引用符 | `"` (双引号) |
| 字符串定界符 | `'` (单引号) |
| 美元定界符 | 不支持 |
| 集运算符 | `UNION` / `INTERSECT` / `MINUS` |
| 特有构造 | `CONNECT BY` / `START WITH` / `FLASHBACK` / `MATERIALIZED VIEW` / `DBMS_SQL` |
| 支持块类型 | `PACKAGE_SPEC` / `PACKAGE_BODY` / `TYPE_SPEC` / `TYPE_BODY` / `TRIGGER` / `FUNCTION` / `PROCEDURE` / `IF` / `LOOP` / `FOR_LOOP` / `WHILE_LOOP` / `CASE_BLOCK` / `ANON_BLOCK` |
| `LIMIT` 语法 | `OFFSET m ROWS FETCH NEXT n ROWS ONLY` |
| `MERGE` | `MERGE INTO ... USING ... ON ...` |
| 递归 CTE | `WITH ... (col_aliases) AS (...)` (Oracle 11gR2+) |

### 4.4 MySQL 方言

```sql
CREATE PROCEDURE my_proc(IN p_id INT)
BEGIN
    DECLARE v_name VARCHAR(100);
    SET v_name = 'hello';
    REPEAT
        SET v_name = CONCAT(v_name, '!');
    UNTIL LENGTH(v_name) > 10
    END REPEAT;
END;
```

| 属性 | 值 |
|------|-----|
| 标识符引用符 | `` ` `` (反引号) |
| 字符串定界符 | `'` (单引号) |
| 美元定界符 | 不支持 |
| 集运算符 | `UNION` / `INTERSECT` / `EXCEPT` |
| 特有构造 | `ON DUPLICATE KEY UPDATE` / `LIMIT n OFFSET m` / `REPLACE INTO` |
| 不支持块类型 | `PACKAGE_SPEC` / `PACKAGE_BODY` / `TYPE_SPEC` / `TYPE_BODY` |
| 循环语法 | `REPEAT ... UNTIL ... END REPEAT`、`WHILE ... DO ... END WHILE`、`FOR ... DO ... END FOR` |
| 声明区 | 无隐式声明段，`DECLARE` 在 `BEGIN` 内部 |
| `MERGE` | 不支持 |

**格式化影响**：
- `REPEAT...UNTIL` → 新增 `REPEAT_LOOP` 块类型（§23.4），`UNTIL` 前缩进弹出
- 无 PACKAGE/TYPE → 约束生成器跳过不可用的块类型
- `LIMIT n OFFSET m` → 单行紧凑格式
- `ON DUPLICATE KEY UPDATE` → UPDATE 前 `OPTIONAL + breakPenalty=0.3`

### 4.5 PostgreSQL 方言

```sql
CREATE OR REPLACE FUNCTION my_func(p_id INT)
RETURNS INT
LANGUAGE plpgsql
AS $$
DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM t WHERE id = p_id;
    RETURN v_count;
END;
$$;

INSERT INTO t (id, name) VALUES (1, 'a')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

SELECT * FROM t LIMIT 10 OFFSET 5;
```

| 属性 | 值 |
|------|-----|
| 标识符引用符 | `"` (双引号) |
| 字符串定界符 | `'` (单引号) |
| 美元定界符 | 支持 `$$...$$`、`$func$...$func$` 等 |
| 集运算符 | `UNION` / `INTERSECT` / `EXCEPT` (支持 ALL) |
| 特有构造 | `ON CONFLICT ... DO UPDATE` / `RETURNING *` / `LATERAL` |
| 不支持块类型 | `PACKAGE_SPEC` / `PACKAGE_BODY` / `TYPE_SPEC` / `TYPE_BODY` |
| 函数体 | `AS $$ ... $$ LANGUAGE plpgsql` |

**格式化影响**：
- 美元定界符 `$$` → lexer 阶段标记为 CONTENT_BLOCK，格式化引擎原样透传
- `ON CONFLICT ... DO UPDATE` → UPDATE 前 `OPTIONAL + breakPenalty=0.3`
- `RETURNING *` → DML 后 `OPTIONAL` 换行
- `AS $$` 函数体内容不解析为 PL/SQL 结构

### 4.6 OceanBase 方言

| 属性 | 值 |
|------|-----|
| 实现策略 | extends OraclePlSqlDialect |
| 关键字集 | Oracle 集 + OceanBase 特有（`TENANT`、`REFRESH`、`OCEANBASE` 等） |
| 块结构 | 与 Oracle 完全一致 |
| 差异点 | OceanBase 2.x+ 支持 PACKAGE，早期版本不支持 |
| 策略 | 继承 Oracle 全部实现，仅在关键字集追加 OB 特有词 |

### 4.7 方言差异在格式化中的处理

#### 4.7.1 PlSqlBlockType 可用性

各方言支持的块类型不同，`ConstraintGenerator` 在构建约束时跳过不可用的块类型：

```java
Set<PlSqlBlockType> supported = dialect.getSupportedBlockTypes();
if (!supported.contains(PlSqlBlockType.PACKAGE_SPEC)) {
    return; // MySQL/PG: 不创建 PACKAGE_SPEC 约束
}
```

| 块类型 | Oracle | OceanBase | MySQL | PostgreSQL |
|--------|--------|-----------|-------|-----------|
| PACKAGE_SPEC | ✅ | ✅ | ❌ | ❌ |
| PACKAGE_BODY | ✅ | ✅ | ❌ | ❌ |
| TYPE_SPEC | ✅ | ✅ | ❌ | ❌ |
| TYPE_BODY | ✅ | ✅ | ❌ | ❌ |
| TRIGGER | ✅ | ✅ | ✅ | ✅ |
| FUNCTION | ✅ | ✅ | ✅ | ✅ |
| PROCEDURE | ✅ | ✅ | ✅ | ✅ |
| REPEAT_LOOP | ❌ | ❌ | ✅ | ❌ |
| FOR_LOOP | ✅ | ✅ | ✅ | ✅ |
| WHILE_LOOP | ✅ | ✅ | ✅ | ✅ |
| LOOP | ✅ | ✅ | ✅ | ✅ |
| ANON_BLOCK | ✅ | ✅ | ✅ | ✅ |

#### 4.7.2 标识符引用符

```sql
-- Oracle: 引号保留，"" 内不格式化
SELECT "MyColumn", "table"."column" FROM "MyTable";
-- MySQL: 反引号保留，`` 内不格式化
SELECT `MyColumn`, `table`.`column` FROM `MyTable`;
```

`TokenInfo.isQuotedIdentifier` 在 lexer 阶段设置。被引用的标识符在 keyword case 转换中跳过（引用标识符大小写有语义）。

#### 4.7.3 美元定界符（PostgreSQL）

PG 函数体 `$$...$$` 的处理：

```
lexer 阶段:
  AS / 关键字                            → 普通 token
  $$ / $func$ / $任意名$                 → 标记为 CONTENT_BLOCK 起始
  ... (函数体内容, 不解析块结构)           → 整块标记为 channel=HIDDEN
  匹配的 $$                              → CONTENT_BLOCK 结束
```

格式化后原样透传：

```sql
CREATE OR REPLACE FUNCTION my_func(p_id INT)
RETURNS INT
LANGUAGE plpgsql
AS $$
DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM t WHERE id = p_id;
    RETURN v_count;
END;
$$;
```

#### 4.7.4 方言关键字集

各方言关键字集用于关键字大小写转换（§30.2）：

| 方言 | 关键字基数 | 特有关键字 |
|------|-----------|-----------|
| Oracle | ~300 | `PACKAGE`、`PRAGMA`、`CONNECT`、`MINUS` |
| MySQL | ~250 | `REPEAT`、`UNTIL`、`LIMIT`、`DUPLICATE` |
| PostgreSQL | ~280 | `LATERAL`、`RETURNING`、`CONFLICT`、`EXCEPT` |
| OceanBase | ~310 | Oracle 集 + `TENANT`、`REFRESH` |

`PostProcessor` 在构造时获取 `dialect.getKeywords()`，`convertLine()` 中仅将命中关键字集的大小写转换。

#### 4.7.5 方言特有 SQL 构造的格式化

| 构造 | 适用方言 | 格式化策略 |
|------|---------|-----------|
| `CONNECT BY` / `START WITH` | Oracle | `WHERE` 后 `OPTIONAL + breakPenalty=0.3`，同 JOIN 树缩进 |
| `MATERIALIZED VIEW` 选项 | Oracle | 每选项一行 |
| `ON DUPLICATE KEY UPDATE` | MySQL | UPDATE 子句前 `OPTIONAL + breakPenalty=0.3` |
| `ON CONFLICT ... DO UPDATE` | PG | `ON CONFLICT` 前 `OPTIONAL + breakPenalty=0.3` |
| `RETURNING *` | PG | 同 Oracle RETURNING INTO，无 INTO 变量 |
| `LIMIT n OFFSET m` | MySQL/PG | 保持一行或 FETCH 风格 |
| `LATERAL (SELECT ...)` | PG/Oracle | 子查询 INLINE 模式 |

#### 4.7.6 方言差异参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `dialectIdentifierQuote` | char | `"` | 标识符引用符，由方言自动设定 |
| `dialectSupportsPackage` | boolean | true | 是否支持 PACKAGE，由方言自动设定 |
| `dialectSupportsType` | boolean | true | 是否支持 TYPE，由方言自动设定 |
| `dialectSetOperator` | enum | ORACLE | 集运算符风格：ORACLE(MINUS)/ANSI(EXCEPT) |
| `dialectLimitStyle` | enum | ANSI_FETCH | LIMIT 语法：ANSI_FETCH / LIMIT_OFFSET |

### 4.8 方言设计总结

```
ParseTree 解决的差异:
  ┌─ IS/AS → BEGIN/END 结构
  ├─ END 匹配
  ├─ PACKAGE/TYPE/TRIGGER 存在性
  └─ 循环结构 (LOOP/WHILE/FOR)

仍需方言处理的差异:
  ┌─ 标识符引用符 (" vs `)         → lexer
  ├─ 字符串/美元定界符              → lexer + token 标记
  ├─ 关键字集                      → PostProcessor
  ├─ 支持的块类型                   → ConstraintGenerator
  ├─ 集运算符关键字                  → ConstraintGenerator (§11.4)
  ├─ 方言特有 DML 构造              → ConstraintGenerator
  └─ LIMIT/OFFSET 语法风格          → ConstraintGenerator
```

## 5. 保护策略

### 5.1 字符串拼接保护

#### 5.1.1 问题背景

PL/SQL 中动态 SQL 常通过字符串拼接构造：

```sql
v_sql := 'SELECT * FROM ' || v_table || ' WHERE id = ' || v_id;
```

格式化引擎不能：
- 将字符串内的 SQL 关键字（SELECT、FROM、WHERE）识别为关键字
- 修改字符串字面量内的空格/换行
- 破坏拼接操作符 `||` 两侧的语义关系

#### 5.1.2 五层保障体系

| 层级 | 阶段 | 措施 |
|------|------|------|
| L1 | Lexer | 字符串字面量标记为 `channel=HIDDEN`，不参与格式化 |
| L2 | Lexer | `||` 视为字符串拼接运算符，前后保持 1 空格 |
| L3 | Parser | 字符串表达式在 ParseTree 中标记为拼接链 |
| L4 | ConstraintGenerator | 拼接链内不施加任何关键字/缩进约束 |
| L5 | StringAssembler | 检查 token 的 stringLiteral 标记，原样输出 |

#### 5.1.3 拼接链换行策略

```sql
-- 长拼接链可选换行（在 || 处断行）
v_sql := 'SELECT employee_id, first_name, last_name, '
      || 'hire_date, salary, commission_pct '
      || 'FROM employees '
      || 'WHERE department_id = ' || v_dept_id;
```

| 位置 | 约束 |
|------|------|
| `'...'` → `\|\|` | `newlineMode=FORBIDDEN`（字符串尾与运算符同行） |
| `\|\|` → `'...'` | `newlineMode=OPTIONAL.breakPenalty(0.3)`（运算符前可选换行） |
| 换行后缩进 | `indentDelta = assignmentAlign ? 0 : 1`（赋值对齐或缩进一级） |

### 5.2 分号行末黏附

#### 5.2.1 问题

分号 `;` 在 PL/SQL 中是语句结束符。格式化引擎确保：
- `;` 紧跟最后一个 token，不换行
- `;` 前保持 0 空格（黏附）
- 分号后换行

#### 5.2.2 方案

```java
// 对所有结束语句的分号施加 FORBIDDEN 换行约束
gap(lastStmtToken, SEMICOLON).newlineMode(FORBIDDEN).minSpaces(0);
gap(SEMICOLON, nextToken).forceNewline(true);
```

```
-- 正确
v_count := 1;

-- 错误（分号前不应有空格或换行）
v_count := 1 ;
v_count := 1
  ;
```

#### 5.2.3 不受影响场景

以下场景中分号不黏附：
- 字符串字面量内的分号（L1 保护）
- 注释内的分号（L1 保护）
- `END;` 后的分号（块结束，继续使用块规则）

### 5.3 斜杠分隔符

#### 5.3.1 问题

Oracle SQL*Plus 中 `/` 用于执行缓冲区中的命令。格式化引擎需确保 `/` 不被当作 SQL 运算符。

#### 5.3.2 方案

```java
// 行首独立的 / 视为分隔符
// 条件: token.text == "/" && (前一个 token 是换行或文件开始)
if (token.text.equals("/") && isLineStart(token)) {
    // 保持原样输出，不施加任何格式化
}
```

```
-- 正确格式
CREATE OR REPLACE PROCEDURE p IS
BEGIN
    NULL;
END;
/

-- / 独占一行，不缩进，不被格式化
-- / 后自动跟随一个换行
```

---

## 6. 非 PL/SQL 指令透传

### 6.1 透传场景与策略

以下内容不属于 PL/SQL 语法，格式化引擎应原样保留：

| 场景 | 示例 | 策略 |
|------|------|------|
| SQL*Plus 指令 | `SET SERVEROUTPUT ON` | 原样保留 |
| SQL*Plus 变量替换 | `&v_var`、`&&v_var` | 不解析 |
| 提示（hint） | `/*+ INDEX(t idx) */` | 注释保护 + 提示原样 |
| 数据库链接 | `table@dblink` | `@` 保持 0 空格 |
| 绑定变量 | `:v_bind`、`:NEW.col` | 保持原样 |
| 条件编译 | `$IF ... $THEN ... $END` | 跳过不格式化 |

### 6.2 判定方式

```java
boolean isPassthrough(TokenInfo token) {
    return token.text.startsWith("&")
        || token.text.startsWith(":")
        || token.text.equals("@")
        || token.text.startsWith("$IF")
        || token.text.startsWith("$THEN")
        || token.text.startsWith("$ELSE")
        || token.text.startsWith("$END");
}
```

判定在 lexer 阶段完成，透传 token 标记为 `channel=HIDDEN`。

#### 6.2.1 SQL\*Plus 指令检测

SQL\*Plus 指令以关键字开头且独占一行，非 PL/SQL 语句。格式化引擎跳过整个指令行。

常见 SQL\*Plus 指令：

| 指令 | 示例 | 说明 |
|------|------|------|
| `SET` | `SET SERVEROUTPUT ON` | 会话设置，参数不定长 |
| `COLUMN` | `COLUMN salary FORMAT 999G999D99` | 列格式 |
| `DEFINE` | `DEFINE v_var = value` | 变量定义 |
| `UNDEFINE` | `UNDEFINE v_var` | 变量删除 |
| `WHENEVER` | `WHENEVER SQLERROR EXIT SQL.SQLCODE` | 错误处理 |
| `PROMPT` | `PROMPT Processing...` | 输出提示 |
| `PAUSE` | `PAUSE Press Enter to continue` | 暂停 |
| `EXECUTE` | `EXECUTE my_proc` | 执行过程（非原生 Oracle，SQL*Plus 语法） |
| `VARIABLE` | `VARIABLE v_ref REFCURSOR` | 绑定变量声明（SQL\*Plus） |
| `PRINT` | `PRINT v_ref` | 输出变量 |
| `REMARK` | `REM This is a comment` | 注释 |
| `SPOOL` | `SPOOL output.log` | 输出重定向 |
| `SPOOL OFF` | `SPOOL OFF` | 停止输出重定向 |
| `HOST` | `HOST ls -la` | 执行系统命令 |
| `CONNECT` | `CONNECT scott/tiger` | 切换连接 |
| `DISCONNECT` | `DISCONNECT` | 断开连接 |
| `START` / `@` | `@script.sql` | 执行脚本 |
| `EXIT` | `EXIT` | 退出 |
| `QUIT` | `QUIT` | 退出 |
| `WHENEVER SQLERROR` | `WHENEVER SQLERROR CONTINUE` | 错误处理 |
| `WHENEVER OSERROR` | `WHENEVER OSERROR EXIT` | 系统错误处理 |
| `CLEAR` | `CLEAR BUFFER` | 清除缓冲区 |
| `SAVE` | `SAVE script.sql` | 保存缓冲区 |
| `GET` | `GET script.sql` | 读取文件 |
| `TIMING` | `TIMING START` / `TIMING STOP` | 计时 |

**行级检测**（在 Lexer 阶段的 token 流中识别）：

```java
private boolean isSqlPlusCommandLine(List<TokenInfo> tokens, int lineStartIdx) {
    TokenInfo first = tokens.get(lineStartIdx);
    if (first == null || first.channel != HIDDEN) {
        return false; // 空白行跳过
    }
    String upper = first.upper;
    return upper.equals("SET")
        || upper.equals("COLUMN")
        || upper.equals("DEFINE")
        || upper.equals("UNDEFINE")
        || upper.equals("WHENEVER")
        || upper.equals("PROMPT")
        || upper.equals("PAUSE")
        || upper.equals("EXECUTE")
        || upper.equals("VARIABLE")
        || upper.equals("PRINT")
        || upper.equals("REMARK")
        || upper.startsWith("REM")
        || upper.equals("SPOOL")
        || upper.equals("HOST")
        || upper.equals("CONNECT")
        || upper.equals("DISCONNECT")
        || upper.equals("START")
        || upper.equals("EXIT")
        || upper.equals("QUIT")
        || upper.equals("CLEAR")
        || upper.equals("SAVE")
        || upper.equals("GET")
        || upper.equals("TIMING");
}
```

**处理方式**：一旦判定为 SQL\*Plus 指令行，该行所有 token 标记为 `channel=HIDDEN`，
格式化引擎跳过该行所有格式化操作。

#### 6.2.2 提示（hint）检测

```java
private boolean isHint(TokenInfo token) {
    return token.text.startsWith("/*+")
        && token.text.endsWith("*/");
}
```

Hint 标记为注释保护，不参与格式化。

#### 6.2.3 条件编译块检测

```java
private boolean isConditionalCompilationBlock(List<TokenInfo> tokens, int startIdx) {
    TokenInfo t = tokens.get(startIdx);
    return t.upper.equals("$IF")
        || t.upper.equals("$THEN")
        || t.upper.equals("$ELSE")
        || t.upper.equals("$ELSIF")
        || t.upper.equals("$END");
}
```

条件编译块作为整体跳过格式化，从 `$IF` 到匹配的 `$END` 原样保留。

### 6.3 `%` 和 `.` 属性标记

PL/SQL 属性标记：

```sql
-- %TYPE 和 %ROWTYPE
v_emp employees.employee_id%TYPE;
CURSOR c IS SELECT * FROM employees;
v_rec c%ROWTYPE;

-- 对象方法调用
obj.member_func();
pkg.proc();
```

| 标记 | 格式化规则 |
|------|-----------|
| `%TYPE` | `%` 前后 0 空格，不折行 |
| `%ROWTYPE` | 同上 |
| `.member_func` | `.` 前 0 空格 |
| `schema.table.column` | `.` 前后 0 空格 |

### 6.4 命名参数 `=>`

#### 6.4.1 语法场景

Oracle PL/SQL 支持命名参数传递，使用 `=>` 关联参数名与实参：

```sql
-- 函数调用
my_func(p_emp_id => 100, p_name => 'John');

-- 过程调用
my_proc(p_salary => 5000, p_commit => TRUE);

-- EXECUTE IMMEDIATE USING
EXECUTE IMMEDIATE v_sql USING IN p_id, OUT v_name;
```

#### 6.4.2 Token 解析

`=>` 在 ANTLR 词法中被识别为单一 token `ASSOCIATION_OPERATOR`（非两个独立 `=` 和 `>`）。

```java
case ASSOCIATION_OPERATOR:
    // 前后间距控制
    gap(paramName, ASSOC).minSpaces(1).maxSpaces(1); // 参数名后1空格
    gap(ASSOC, paramValue).minSpaces(1).maxSpaces(1); // => 后1空格
    break;
```

#### 6.4.3 格式化约束

| 约束位置 | 规则 |
|----------|------|
| 参数名 → `=>` | `minSpaces=1, maxSpaces=1`，不换行 |
| `=>` → 实参 | `minSpaces=1, maxSpaces=1`，不换行 |
| 实参 → 逗号 | 紧凑，不换行 |
| 逗号 → 下一参数 | `newlineMode=OPTIONAL.breakPenalty(0.3)` 或 `REQUIRED`（`parameterPerLine=true`） |

#### 6.4.4 对齐模式

`namedParameterAlign=true` 时，所有 `=>` 右对齐：

```sql
-- 对齐前
my_func(p_emp_id => 100, p_name => 'John', p_salary => 5000);

-- 对齐后（多行）
my_func(
    p_emp_id  => 100,
    p_name    => 'John',
    p_salary  => 5000
);
```

约束：`=>` 前建立 `alignGroupId="NAMED_PARAM"`，所有 `=>` 同列对齐。

#### 6.4.5 约束生成

```java
private void walkNamedParams(PlSqlBlock block) {
    List<TokenInfo> params = block.getNamedParams(); // => 所在行列表
    if (opts.isNamedParameterAlign() && params.size() > 1) {
        for (TokenInfo param : params) {
            GapConstraint g = gap(param.nameEnd, param.assocOp);
            g.alignGroupId("NAMED_PARAM");
            out.add(g);
        }
    }
}
```

---

## 7. 注释格式化

### 7.1 注释类型

| 类型 | 语法 | 用途 |
|------|------|------|
| 单行注释 | `-- text` | 行内说明、代码行尾注释 |
| 块注释 | `/* text */` | 多行说明、SQL hint |
| 文档注释 | `/** text */` | API 文档（JavaDoc 风格） |

### 7.2 注释缩进策略

#### 7.2.1 策略模式

```java
enum CommentIndentMode {
    CODE_LEVEL,   // 注释与代码对齐
    FIXED,        // 固定列（通常最左）
    RELATIVE      // 相对父块缩进
}
```

| 模式 | 效果 | 参数值 |
|------|------|--------|
| `CODE_LEVEL` | `IF ...`<br>`    -- comment`<br>`    THEN` | `commentIndent: CODE_LEVEL` |
| `FIXED` | `-- comment`<br>`IF ...`<br>`    THEN` | `commentIndent: FIXED` |
| `RELATIVE` | `DECLARE`<br>`    -- comment (缩进+1)`<br>`    v_var NUMBER;` | `commentIndent: RELATIVE` |

#### 7.2.2 约束规则

```java
switch (opts.getCommentIndentMode()) {
    case CODE_LEVEL:
        // 注释与上一行代码保持同一缩进级别
        gap(prevCode, comment).indentDelta(0);
        break;
    case FIXED:
        // 注释缩进到固定列（column = 0）
        gap(prevCode, comment).indentDelta(-block.currentIndent);
        break;
    case RELATIVE:
        // 注释在父块基础上加一级缩进
        gap(prevCode, comment).indentDelta(1);
        break;
}
```

### 7.3 行尾注释对齐

```java
enum TrailingCommentAlign {
    NONE,       // 不专门对齐
    ALIGN,      // 同一块内行尾注释右对齐
    FIXED_GAP   // 固定间距（如代码与注释间至少3空格）
}
```

```sql
-- NONE
v_var1 NUMBER; -- 员工ID
v_var2_long_name VARCHAR2(100); -- 员工姓名

-- ALIGN
v_var1           NUMBER;        -- 员工ID
v_var2_long_name VARCHAR2(100); -- 员工姓名

-- FIXED_GAP (gap=3)
v_var1 NUMBER;   -- 员工ID
v_var2_long_name VARCHAR2(100); -- 员工姓名
```

约束策略（ALIGN 模式）：

```java
if (opts.getTrailingCommentAlign() == ALIGN) {
    for (int i = 0; i < lines.size(); i++) {
        GapConstraint g = gap(line.getLastCodeIdx(), commentIdx);
        g.alignGroupId("TRAILING_COMMENT");
        out.add(g);
    }
}
```

### 7.4 单行注释 `--`

#### 7.4.1 `--` 后空格

| 模式 | 示例 | 参数 |
|------|------|------|
| 强制空格 | `-- text` | `commentSingleSpace: true` |
| 保留原始 | `--text` 或 `-- text` | `commentSingleSpace: false` |

#### 7.4.2 注释折行

长单行注释超长时折行：

```sql
-- 这是一段很长的注释内容，超出行宽限制后在这里
-- 换行继续，第二行缩进与第一行注释文本对齐
```

### 7.5 块注释 `/* */`

#### 7.5.1 单行 vs 多行

```sql
-- 单行块注释（保持一行）
/* 这是一个简短说明 */

-- 多行块注释（/* 和 */ 各占一行，中间行缩进+星号对齐）
/*
 * 这是一个多行注释
 * 每行开头保留星号
 * 星号垂直对齐
 */
```

#### 7.5.2 星号对齐

```java
enum BlockCommentStyle {
    PRESERVE,      // 保留原始格式
    ALIGN_STARS,   // 对齐星号列
    COMPACT        // 尽量压缩为单行
}
```

### 7.6 文档注释 `/** */`

文档注释（`/** ... */`）受保护，不修改内部格式：

```java
if (token.text.startsWith("/**")) {
    // 标记为 doc comment，整块原样保留
    protectBlock(token);
}
```

`@param` 标签后的参数名对齐（同 Java 约定）：

```java
/**
 * @param p_emp_id  员工ID
 * @param p_name    员工姓名
 * @param p_salary  薪资
 */
```

### 7.7 注释位置策略

```java
enum CommentPlacement {
    BEFORE,     // 注释在目标代码之前
    TRAILING,   // 注释在目标代码之后（行尾）
    KEEP        // 保持原始位置
}
```

### 7.8 注释前空行

`blankLineBeforeComment=true` 时，注释前如果非空则插入空行：

```sql
-- 上一段代码结束

-- 注释前有空格
v_var NUMBER;
```

### 7.9 参数汇总

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `commentPreserve` | boolean | true | 注释保留模式（true=原样保留） |
| `commentIndent` | enum | CODE_LEVEL | 注释缩进模式 |
| `commentSingleSpace` | boolean | true | `--` 后强制空格 |
| `commentWrap` | boolean | false | 单行注释超长折行 |
| `trailingCommentAlign` | enum | NONE | 行尾注释对齐模式 |
| `trailingCommentMinSpaces` | int | 3 | 行尾注释最小间距 |
| `blockCommentStyle` | enum | PRESERVE | 块注释风格 |
| `docCommentPreserve` | boolean | true | 文档注释保护 |
| `docCommentParamAlign` | boolean | true | `@param` 参数对齐 |
| `commentPlacement` | enum | KEEP | 注释位置策略 |
| `blankLineBeforeComment` | boolean | false | 注释前加空行 |

---

## 8. DQL 格式化总则

### 8.1 DQL 与 DML 的边界划分

按 SQL 标准，查询类语句与数据操作类语句在格式化规则上有本质差异，需独立处理：

| | DQL (Data Query Language) | DML (Data Manipulation Language) |
|--|--------------------------|----------------------------------|
| 涵盖 | SELECT、CTE(WITH)、SET 运算符 | INSERT、UPDATE、DELETE、MERGE |
| 核心结构 | SELECT-FROM-WHERE 子句树 | INSERT-VALUES、SET-WHERE、MERGE-INTO |
| 格式化焦点 | 子句对齐、JOIN 树缩进、列清单断行 | 值列表对齐、赋值对齐、多表操作 |
| 子查询 | 位置感知深度格式化（§9.6） | 仅 INLINE/AUTO 模式 |
| 对齐组 | 列名对齐、CTE 名称对齐 | SET 赋值对齐、变量对齐 |

V3 将 DQL 与 DML 完全分离，DQL 涵盖 §8-§11，DML 涵盖 §12-§15。

### 8.2 关键字断行约束

DQL/DML 关键字之间的间隙约束决定子句间的换行行为：

```sql
SELECT                              -- SELECT 前换行
    employee_id, first_name         -- 列清单
FROM employees                      -- FROM 前换行
WHERE department_id = 100           -- WHERE 前换行
    AND salary > 5000               -- AND 缩进
ORDER BY hire_date;                 -- ORDER BY 前换行
```

| 关键字 | 前换行 | 后缩进 | breakPenalty |
|--------|--------|--------|-------------|
| SELECT | REQUIRED(块首) | 0 | — |
| FROM | REQUIRED | 0 | — |
| WHERE | OPTIONAL | 1 | 0.15 |
| AND/OR (WHERE内) | OPTIONAL | 1 | 0.15 |
| GROUP BY | OPTIONAL | 0 | 0.2 |
| HAVING | OPTIONAL | 0 | 0.2 |
| ORDER BY | OPTIONAL | 0 | 0.2 |
| JOIN/LEFT/RIGHT | OPTIONAL | 1 | 0.13 |
| ON | OPTIONAL | 1 | 0.13 |

### 8.3 处理范围界定

DQL 格式化覆盖的范围：

```
独立 DQL 语句:
  SELECT ... FROM ... WHERE ...
  WITH cte AS (SELECT ...) SELECT ...
  SELECT ... UNION SELECT ...

块内 DQL 语句:
  FUNCTION/PROCEDURE 内的 SELECT ... INTO ...
  CURSOR 声明中的 SELECT ...
  EXECUTE IMMEDIATE 字符串内的 SELECT → 不处理
```

---

## 9. SELECT 子句树深度格式化

### 9.1 JOIN 树缩进

JOIN 树按逻辑关系分层缩进：

```sql
SELECT *
FROM employees e
JOIN departments d ON e.department_id = d.department_id
LEFT JOIN locations l ON d.location_id = l.location_id
    AND l.country_id = 'US'         -- AND 在 JOIN 内进一步缩进
JOIN jobs j ON e.job_id = j.job_id;
```

| 约束位置 | 约束规则 |
|----------|---------|
| FROM → 第一表 | `requireNewline(true).indentDelta(0)` |
| JOIN → ON | `newlineMode(OPTIONAL).breakPenalty(0.13).indentDelta(1)` |
| ON → 条件 | `requireNewline(true).indentDelta(1)` |
| AND (ON 内) | `newlineMode(OPTIONAL).breakPenalty(0.13).indentDelta(2)` |
| 嵌套 JOIN | 缩进层级累加 |

**参数**：`dmlJoinIndent` (boolean) — true 时 JOIN 缩进一级。

### 9.2 WHERE AND/OR 对齐

`FormatOptions.dmlWhereAndPosition` 参数控制 AND/OR 对齐方式：

| 模式 | 效果 |
|------|------|
| `INDENTED` | AND/OR 与 WHERE 后第一个列对齐 |
| `SAME_LINE` | AND/OR 跟随上一行末尾 |
| `OUTDENT` | AND 比 WHERE 少缩进 |

```sql
-- INDENTED
WHERE employee_id > 100
    AND department_id = 50
    OR salary > 10000

-- SAME_LINE
WHERE employee_id > 100 AND
    department_id = 50 OR
    salary > 10000

-- OUTDENT
WHERE employee_id > 100
  AND department_id = 50
  OR salary > 10000
```

### 9.3 SELECT 列清单断行风格

`selectColumnMode` 控制 SELECT 列清单的断行风格：

| 模式 | 效果 |
|------|------|
| `COMPACT` | 紧凑（尽量一行，超过行宽限制时按 selectColumnsPerRow 断行） |
| `ONE_PER_LINE` | 每列独立一行 |
| `ALIGN` | 每列独立一行 + 列名右对齐 |

```sql
-- COMPACT (行宽未超)
SELECT employee_id, first_name, last_name FROM employees;

-- COMPACT + selectColumnsPerRow=2
SELECT employee_id, first_name,
       last_name, email
FROM employees;

-- ONE_PER_LINE
SELECT employee_id
     , first_name
     , last_name
FROM employees;

-- ALIGN
SELECT employee_id
     , first_name
     , last_name
FROM employees;
```

**约束策略**：

| 参数 | 约束 |
|------|------|
| `selectColumnMode=COMPACT` | `gap(col_i, col_i+1).newlineMode(OPTIONAL).breakPenalty(MAX)` |
| `COMPACT + selectColumnsPerRow=N` | 每 N 列 `forceNewline(true)` |
| `selectColumnMode=ONE_PER_LINE` | `gap(col_i, col_i+1).forceNewline(true).indentDelta(selIndent)` |
| `selectColumnMode=ALIGN` | ONE_PER_LINE + `alignGroupId="SEL_COL"` 列名右对齐 |

### 9.4 逗号位置

`commaPosition` 控制逗号在行尾还是行首：

| 模式 | 约束 |
|------|------|
| `TRAILING` (行尾) | 逗号后 `newlineMode=OPTIONAL` |
| `LEADING` (行首) | 逗号前 `newlineMode=OPTIONAL`，逗号后 `FORBIDDEN` |

```sql
-- TRAILING
SELECT employee_id,
       first_name,
       last_name
FROM employees;

-- LEADING
SELECT employee_id
     , first_name
     , last_name
FROM employees;
```

### 9.5 IN 列表格式化

#### 9.5.1 格式化模式

`dmlInClauseExpand` 控制 IN 列表的展开方式：

| 模式 | 效果 |
|------|------|
| `COMPACT` | 整组不换行 |
| `ONE_PER_LINE` | 每值一行 |
| `MIXED` | 按 columnsPerRow 阈值切块，块内紧凑，块间换行 |

```sql
-- COMPACT
WHERE dept_id IN (10, 20, 30, 40, 50)

-- ONE_PER_LINE
WHERE dept_id IN (
    10,
    20,
    30,
    40,
    50
)

-- MIXED (columnsPerRow=2)
WHERE dept_id IN (
    10, 20,
    30, 40,
    50
)
```

#### 9.5.2 约束规则

```java
switch (opts.getDmlInClauseExpand()) {
    case COMPACT:
        gap(value, comma).newlineMode(FORBIDDEN);
        gap(comma, nextValue).newlineMode(FORBIDDEN);
        break;
    case ONE_PER_LINE:
        gap(comma, nextValue).forceNewline(true).indentDelta(+1);
        gap(lparen, firstValue).forceNewline(true).indentDelta(+1);
        gap(lastValue, rparen).forceNewline(true).indentDelta(-1);
        break;
    case MIXED:
        // 按 columnsPerRow 组切分，组内 FORBIDDEN，组间 OPTIONAL
        int perRow = opts.getDmlInColumnsPerRow();
        for (int i = 0; i < values.size(); i++) {
            if ((i + 1) % perRow == 0 && i < values.size() - 1) {
                gap(values.get(i).comma, values.get(i+1)).newlineMode(OPTIONAL);
            }
        }
        break;
}
```

#### 9.5.3 括号对齐

| 括号对齐模式 | 效果 |
|-------------|------|
| `IN_KEYWORD` | `IN (` 保持同行，`)` 与 IN 对齐 |
| `FIRST_VALUE` | `(` 与第一个值同行，`)` 对齐第一个值 |

```sql
-- IN_KEYWORD
WHERE dept_id IN (
    10,
    20,
    30
)

-- FIRST_VALUE
WHERE dept_id IN (10,
                  20,
                  30)
```

#### 9.5.4 自动展开阈值

`dmlInClauseThreshold` — 值数量超过此阈值时自动展开（COMPACT→ONE_PER_LINE）：

```java
int threshold = opts.getDmlInClauseThreshold(); // 默认 5
if (values.size() > threshold) {
    // 自动切换到 ONE_PER_LINE
}
```

#### 9.5.5 IN 子查询 vs 值列表

检测 `IN (` 后的第一个 token 区分子查询和值列表：

```java
TokenInfo first = tokens.get(lparenIdx + 1);
if (first.upper.equals("SELECT") || first.upper.equals("WITH")) {
    // 子查询 — 走 §9.6 子查询规则
    handleSubquery(block, lparenIdx, rparenIdx);
} else {
    // 值列表 — 走 §9.5 规则
    handleInValueList(block, lparenIdx, rparenIdx);
}
```

#### 9.5.6 多列 IN

```sql
WHERE (col1, col2) IN ((1, 'a'), (2, 'b'), (3, 'c'))

-- 展开后
WHERE (col1, col2) IN (
    (1, 'a'),
    (2, 'b'),
    (3, 'c')
)
```

#### 9.5.7 NOT IN

与 IN 格式一致，仅关键字差异。需额外检查 NULL 安全问题（格式化工具不检查语义，仅格式）。

### 9.6 子查询位置感知格式化

#### 9.6.1 格式化模式

`dmlSubqueryFormat` 控制子查询展开方式：

| 模式 | 效果 |
|------|------|
| `INLINE` | 子查询内嵌不换行 |
| `EXPAND` | 子查询换行缩进，内部递归格式化 |
| `AUTO` | 按 tokens 数/预估行数与 threshold 比较决定 |


```sql
-- INLINE
SELECT e.* FROM employees e WHERE e.department_id IN (
    SELECT d.department_id FROM departments d WHERE d.location_id = 1000
);

-- EXPAND
SELECT e.*
FROM employees e
WHERE e.department_id IN (
    SELECT d.department_id
    FROM departments d
    WHERE d.location_id = 1000
);
```

#### 9.6.2 位置感知约束总表

子查询在 9 个位置有不同的格式化行为：

```java
enum SubqueryPosition {
    SELECT_LIST,    // SELECT 列清单内
    WHERE,          // WHERE 条件内
    FROM,           // FROM/JOIN 子句中
    HAVING,         // HAVING 条件内
    ON,             // JOIN ON 条件内
    INSERT,         // INSERT INTO ... SELECT
    EXISTS,         // EXISTS/NOT EXISTS
    LATERAL,        // LATERAL (...)
    DEFAULT         // 其他位置（兜底）
}
```

每个位置对应独立的格式化参数（见 §9.6.8）。

#### 9.6.3 位置判定逻辑

位置通过分析子查询左侧最近的 SQL 关键字来确定：

```java
SubqueryPosition detectPosition(int lparenIdx) {
    // 向前扫描最近的 SELECT 子句关键字
    int cursor = lparenIdx;
    while (cursor >= 0) {
        TokenInfo t = tokens.get(cursor);
        switch (t.upper) {
            case "FROM":  return SubqueryPosition.FROM;
            case "WHERE": return SubqueryPosition.WHERE;
            case "HAVING":return SubqueryPosition.HAVING;
            case "ON":    return SubqueryPosition.ON;
            case "EXISTS":return SubqueryPosition.EXISTS;
            case "IN":    return SubqueryPosition.WHERE; // 归入 WHERE
            case "LATERAL": return SubqueryPosition.LATERAL;
            case "INSERT": return SubqueryPosition.INSERT;
            case ",":     return SubqueryPosition.SELECT_LIST;
        }
        cursor--;
    }
    return SubqueryPosition.DEFAULT;
}
```

#### 9.6.4 AUTO 模式展开阈值

```java
boolean shouldExpand(int tokensInSubquery) {
    int threshold = opts.getSubqueryThreshold(); // 默认 80
    return tokensInSubquery > threshold;
}
```

#### 9.6.5 各位置格式化示例

```sql
-- SELECT_LIST 位置：子查询紧凑（默认 INLINE）
SELECT e.*,
       (SELECT MAX(salary) FROM salaries WHERE emp_id = e.id) AS max_sal
FROM employees e;

-- WHERE 位置：EXPAND 模式
SELECT e.*
FROM employees e
WHERE e.salary > (
    SELECT AVG(salary)
    FROM employees
    WHERE department_id = e.department_id
);

-- FROM 位置：EXPAND 模式 + 别名
SELECT dept_stats.*
FROM (
    SELECT department_id,
           COUNT(*) AS emp_count,
           AVG(salary) AS avg_salary
    FROM employees
    GROUP BY department_id
) dept_stats
WHERE dept_stats.emp_count > 5;

-- EXISTS 位置：紧凑或展开
SELECT d.*
FROM departments d
WHERE EXISTS (
    SELECT 1
    FROM employees e
    WHERE e.department_id = d.department_id
);

-- LATERAL 位置：同 FROM 子查询
SELECT d.department_name, dept_stats.*
FROM departments d,
LATERAL (
    SELECT COUNT(*) AS emp_count,
           AVG(salary) AS avg_salary
    FROM employees e
    WHERE e.department_id = d.department_id
) dept_stats;
```

#### 9.6.6 多层嵌套递归

子查询内包含子查询时递归格式化：

```java
private void walkSelect(PlSqlBlock block) {
    // ... 格式化当前 SELECT ...
    for (PlSqlBlock subquery : block.nestedSubqueries) {
        if (opts.getDmlSubqueryFormat() == EXPAND
            || (opts.getDmlSubqueryFormat() == AUTO && shouldExpand(subquery))) {
            walkSelect(subquery);  // 递归
        }
    }
}
```

递归深度限制：`maxSubqueryDepth`（默认 5）。

#### 9.6.7 子查询内格式化规则

子查询内部的格式化规则继承自外层，但可被位置特定参数覆盖：

```sql
-- 外层 SELECT 列清单：ONE_PER_LINE
-- 子查询内 SELECT 列清单：COMPACT（由 subquerySelectMode 控制）
SELECT e.employee_id,
       e.first_name,
       (SELECT MAX(s.salary) FROM salaries s WHERE s.emp_id = e.id) AS max_sal
FROM employees e;
```

#### 9.6.8 参数汇总

| 参数 | 类型 | 默认值 | 适用位置 |
|------|------|--------|---------|
| `selectListSubqueryStyle` | enum | INLINE | SELECT_LIST |
| `whereSubqueryStyle` | enum | EXPAND | WHERE |
| `fromSubqueryStyle` | enum | EXPAND | FROM |
| `havingSubqueryStyle` | enum | EXPAND | HAVING |
| `onClauseSubqueryStyle` | enum | INLINE | ON |
| `insertSubqueryStyle` | enum | EXPAND | INSERT |
| `existsSubqueryStyle` | enum | INLINE | EXISTS |
| `lateralSubqueryStyle` | enum | EXPAND | LATERAL |
| `defaultSubqueryStyle` | enum | EXPAND | DEFAULT |
| `subqueryThreshold` | int | 80 | AUTO 模式 |

### 9.7 参数汇总

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `fromClauseNewline` | boolean | true | FROM 前换行 |
| `fromClauseIndent` | int | 1 | FROM 缩进量（0=不缩进，1=一级） |
| `joinOnAlign` | boolean | true | JOIN ON 条件对齐 |
| `dmlJoinIndent` | boolean | true | JOIN 子句缩进 |
| `dmlJoinOnNewLine` | boolean | false | JOIN 前换行 |
| `dmlWhereAndPosition` | enum | INDENTED | AND/OR 对齐模式 |
| `whereIndentSize` | int | 1 | WHERE 条件缩进量 |
| `selectColumnMode` | enum | ALIGN | 列模式 COMPACT/ONE_PER_LINE/ALIGN |
| `selectColumnsPerRow` | int | 0 | COMPACT 模式每行列数 |
| `commaPosition` | enum | TRAILING | 逗号位置 TRAILING/LEADING |
| `dmlInClauseExpand` | enum | COMPACT | IN 列表展开模式 |
| `dmlInClauseThreshold` | int | 5 | IN 列表自动展开阈值 |
| `dmlInColumnsPerRow` | int | 5 | MIXED 模式每行列数 |
| `dmlSubqueryFormat` | enum | AUTO | 子查询格式化模式 |

---

## 10. CTE (WITH 子句) 格式化

### 10.1 名称对齐模式

```sql
-- COMPACT
WITH
    dept_stats AS (SELECT department_id, COUNT(*) AS cnt FROM employees GROUP BY department_id),
    avg_sal AS (SELECT AVG(salary) AS avg_sal FROM employees)
SELECT * FROM dept_stats, avg_sal;

-- ONE_PER_LINE（每 CTE 一行）
WITH
    dept_stats AS (
        SELECT department_id, COUNT(*) AS cnt
        FROM employees
        GROUP BY department_id
    ),
    avg_sal AS (
        SELECT AVG(salary) AS avg_sal FROM employees
    )
SELECT * FROM dept_stats, avg_sal;

-- ALIGN（CTE 名称右对齐）
WITH
    dept_stats AS (
        ...
    ),
    avg_sal    AS (
        ...
    )
SELECT * FROM dept_stats, avg_sal;
```

| 参数 | 约束 |
|------|------|
| `cteFormat=COMPACT` | CTE 间 `newlineMode(OPTIONAL).breakPenalty(MAX)` |
| `cteFormat=ONE_PER_LINE` | CTE 间 `forceNewline(true)` |
| `cteFormat=ALIGN` | ONE_PER_LINE + CTE 名 `alignGroupId="CTE_NAME"` |

### 10.2 逗号位置

同 `commaPosition` 规则（TRAILING/LEADING），CTE 间的 `cteCommaPosition` 独立控制。

```sql
-- TRAILING
WITH
    dept_stats AS (...),
    avg_sal AS (...)
SELECT ...

-- LEADING
WITH
    dept_stats AS (...)
    , avg_sal AS (...)
SELECT ...
```

### 10.3 递归 CTE

```sql
WITH RECURSIVE org_tree AS (
    -- 锚点成员
    SELECT employee_id, manager_id, 1 AS level
    FROM employees
    WHERE manager_id IS NULL
    UNION ALL
    -- 递归成员
    SELECT e.employee_id, e.manager_id, t.level + 1
    FROM employees e
    JOIN org_tree t ON e.manager_id = t.employee_id
)
SELECT * FROM org_tree;
```

| 位置 | 约束 |
|------|------|
| `RECURSIVE` 关键字 | 与 `WITH` 同行 |
| 锚点成员 | 第一个 SELECT 前换行缩进 |
| `UNION ALL` | 换行，与锚点成员同缩进级别 |
| 递归成员 | 同锚点缩进 |
| Oracle 不支持 `RECURSIVE` | Oracle: `WITH ... (col_aliases) AS (...)` |

### 10.4 子查询因子化

```sql
WITH
    dept_stats AS (
        SELECT department_id,
               COUNT(*) AS cnt
        FROM employees
        GROUP BY department_id
    ),
    emp_stats AS (
        SELECT job_id,
               AVG(salary) AS avg_sal
        FROM employees
        GROUP BY job_id
    )
SELECT d.department_name, ds.cnt
FROM departments d
JOIN dept_stats ds ON d.department_id = ds.department_id;
```

### 10.5 CTE 与 PL/SQL 块的交互

PL/SQL 块内 CTE 作为独立语句出现在 `BEGIN...END` 内部。处理方式：
- CTE 作为一个整体 `PlSqlBlock`，内部 SELECT 按 §9 规则格式化
- CTE 之后的分号走 §5.2 规则

### 10.6 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `cteFormat` | enum | ONE_PER_LINE | CTE 格式 COMPACT/ONE_PER_LINE/ALIGN |
| `cteCommaPosition` | enum | TRAILING | CTE 逗号位置 |
| `cteRecursiveKeyword` | boolean | true | 标注 RECURSIVE 关键字 |

---

## 11. SET 运算符

### 11.1 操作符位置

```sql
-- UNION 前换行
SELECT employee_id FROM employees
UNION
SELECT employee_id FROM former_employees;

-- UNION ALL
SELECT employee_id FROM employees
UNION ALL
SELECT employee_id FROM former_employees;
```

| 模式 | 约束 |
|------|------|
| `setOperatorNewline=true` | `gap(prevStmt, UNION).forceNewline(true).indentDelta(0)` |
| `setOperatorNewline=false` | `gap(prevStmt, UNION).newlineMode(OPTIONAL)` |

### 11.2 两侧 SELECT 列对齐

```sql
-- 列对齐（setOperatorColumnAlign=true）
SELECT employee_id, first_name, last_name
FROM employees
UNION
SELECT employee_id, first_name, last_name
FROM former_employees;
```

### 11.3 括号复合嵌套

```sql
-- 复合 SET 操作：括号控制优先级
(
    SELECT employee_id FROM employees
    MINUS
    SELECT employee_id FROM retired_employees
)
INTERSECT
(
    SELECT employee_id FROM active_employees
    UNION
    SELECT employee_id FROM contractors
);
```

| 约束位置 | 规则 |
|----------|------|
| `(` 前 | `forceNewline(true).indentDelta(1)` (若为括号复合) |
| 复合内 SET 操作 | 与独立 SET 操作同规则 |
| `)` 后 | 换行，弹出缩进 |

### 11.4 MINUS vs EXCEPT

| 方言 | 关键字 |
|------|-------|
| Oracle | `MINUS` |
| PostgreSQL | `EXCEPT [ALL]` |
| MySQL | `EXCEPT` |

方言通过 `SqlDialect.getSetOperators()` 提供关键字列表，`ConstraintGenerator` 统一处理。

### 11.5 全局 ORDER BY / FETCH FIRST

```sql
-- 全局 ORDER BY 作用于最终结果集
SELECT employee_id, salary FROM employees
UNION
SELECT employee_id, salary FROM former_employees
ORDER BY salary DESC
FETCH FIRST 10 ROWS ONLY;
```

`ORDER BY` 和 `FETCH FIRST` 在最后一个 SET 分支后，与最终结果同行或缩进同级。

### 11.6 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `setOperatorNewline` | boolean | true | 操作符前换行 |
| `setOperatorColumnAlign` | boolean | true | 两侧列对齐 |
| `setOperatorIndent` | boolean | true | 复合括号内缩进 |

---

## 12. DML 格式化总则

### 12.1 设计原则

DML（INSERT/UPDATE/DELETE/MERGE）格式化遵循以下原则：

1. **单引擎统一处理** — DML 格式化不走模板引擎，全部在 `ConstraintGenerator` 中处理
2. **AlignGroup 对齐** — 赋值、变量列表等使用 AlignGroup 模型对齐
3. **关键词断行** — DML 关键字间间隙受约束控制
4. **值列表格式化** — 多值/多列值列表支持 COMPACT/EXPAND 模式

### 12.2 关键字断行约束

| DML 关键字 | 前换行 | 后缩进 | 控制参数 |
|-----------|--------|--------|---------|
| INSERT INTO | REQUIRED(块首) | 0 | — |
| VALUES | OPTIONAL | 0 | `dmlValuesNewline` |
| SET | REQUIRED(块首) | 0 | — |
| UPDATE | REQUIRED(块首) | 0 | — |
| DELETE | REQUIRED(块首) | 0 | — |
| MERGE INTO | REQUIRED(块首) | 0 | — |
| USING | OPTIONAL | 1 | `mergeUsingNewline` |
| ON (MERGE) | OPTIONAL | 1 | `mergeOnNewline` |
| WHEN MATCHED | OPTIONAL | 0 | `mergeWhenNewline` |
| WHEN NOT MATCHED | OPTIONAL | 0 | `mergeWhenNewline` |

### 12.3 处理范围界定

```
独立 DML 语句:
  INSERT INTO ... VALUES ...
  UPDATE ... SET ... WHERE ...
  DELETE FROM ... WHERE ...
  MERGE INTO ... USING ...

块内 DML 语句:
  BEGIN ... INSERT INTO ... END;  → 同规则
  FORALL idx IN ... INSERT INTO ...  → §25.3 FORALL

不处理:
  字符串内的 DML（EXECUTE IMMEDIATE 'INSERT INTO ...'）
```

---

## 13. MERGE 语句格式化

### 13.1 格式化约束

```sql
MERGE INTO employees e
USING (
    SELECT employee_id, first_name, last_name, salary
    FROM new_employees
) n
ON (e.employee_id = n.employee_id)
WHEN MATCHED THEN
    UPDATE SET
        e.first_name = n.first_name,
        e.last_name  = n.last_name,
        e.salary     = n.salary
    DELETE WHERE n.salary IS NULL
WHEN NOT MATCHED THEN
    INSERT (employee_id, first_name, last_name, salary)
    VALUES (n.employee_id, n.first_name, n.last_name, n.salary);
```

| 约束位置 | 约束 |
|----------|------|
| `MERGE INTO` 头部 | 保持一行 |
| `USING` → 源表/子查询 | `gap(INTO_clause, USING).forceNewline(true).indentDelta(1)` |
| `ON` → 条件 | `gap(USING_clause, ON).forceNewline(true).indentDelta(1)` |
| `WHEN MATCHED THEN` | 前换行，缩进与 MERGE 同级 |
| `WHEN NOT MATCHED THEN` | 前换行，缩进与 WHEN MATCHED 同级 |
| `UPDATE SET` | WHEN MATCHED 后换行缩进一级 |
| `INSERT (...)` | WHEN NOT MATCHED 后换行缩进一级 |
| `DELETE WHERE` | UPDATE SET 后 `OPTIONAL + breakPenalty=0.3` |

### 13.2 对齐组

```sql
WHEN MATCHED THEN
    UPDATE SET
        e.first_name = n.first_name,     -- SET 赋值 = 对齐
        e.last_name  = n.last_name,
        e.salary     = n.salary
```

`UPDATE SET` 的 `=` 对齐复用 `dmlUpdateSetAlign` 参数（§14.2.2）。

### 13.3 多 WHEN 子句

Oracle 23c+ 支持多 `WHEN NOT MATCHED` 子句：

```sql
MERGE INTO inventory i
USING (
    SELECT product_id, quantity, type FROM new_shipments
) n
ON (i.product_id = n.product_id)
WHEN MATCHED THEN
    UPDATE SET i.quantity = i.quantity + n.quantity
WHEN NOT MATCHED THEN
    INSERT (product_id, quantity) VALUES (n.product_id, n.quantity)
WHEN NOT MATCHED THEN
    INSERT (product_id, quantity) VALUES (n.product_id, 0);
```

### 13.4 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mergeIntoNewline` | boolean | true | MERGE INTO 前换行 |
| `mergeUsingNewline` | boolean | true | USING 前换行 |
| `mergeOnNewline` | boolean | true | ON 前换行 |
| `mergeWhenNewline` | boolean | true | WHEN MATCHED/NOT MATCHED 前换行 |
| `mergeUpdateSetAlign` | boolean | true | UPDATE SET = 对齐 |

---

## 14. INSERT / UPDATE / DELETE 格式化

### 14.1 INSERT 格式化

#### 14.1.1 列清单与 VALUES

```sql
-- 单行（紧凑）
INSERT INTO employees (employee_id, first_name, last_name)
VALUES (100, 'John', 'Doe');

-- 列清单换行
INSERT INTO employees (
    employee_id,
    first_name,
    last_name
) VALUES (100, 'John', 'Doe');

-- VALUES 多值跨行
INSERT INTO employees (employee_id, first_name, last_name)
VALUES (
    100,
    'John',
    'Doe'
);
```

| 约束位置 | 约束 |
|----------|------|
| `INSERT INTO` → 表名 | `minSpaces=1`，保持同行 |
| 表名 → 列清单 `(...)` | `newlineMode(OPTIONAL)`，`dmlInsertColumnNewline` 控制 |
| 列清单内 `col1, col2` | 同 `selectColumnMode` 规则 |
| 列清单 `)` → `VALUES` | `newlineMode(OPTIONAL)`，`dmlValuesNewline` 控制 |
| `VALUES (...)` 值列表 | `dmlValuesExpand` 控制紧凑/展开 |
| `INTO` → 变量列表 | 逗号后 OPTIONAL 换行 |

#### 14.1.2 INSERT ALL / FIRST

INSERT ALL / FIRST 有两种形式：

**形式 A — SELECT 源**（Oracle 特有，SELECT 为所有 INTO 子句提供数据）：

```sql
INSERT ALL
    INTO employees (employee_id, first_name, last_name)
    VALUES (emp_id, first_name, last_name)
    INTO departments (department_id, department_name)
    VALUES (dept_id, dept_name)
SELECT emp_id, first_name, last_name, dept_id, dept_name
FROM temp_table
WHERE temp_table.status = 'ACTIVE';
```

**形式 B — VALUES 字面量**（无 SELECT 尾句）：

```sql
INSERT ALL
    INTO employees (employee_id, first_name, last_name)
    VALUES (100, 'John', 'Doe')
    INTO departments (department_id, department_name)
    VALUES (50, 'Engineering')
SELECT * FROM DUAL;
```

**INSERT FIRST**（条件插入，与 INSERT ALL 语法差异仅关键字）：

```sql
INSERT FIRST
    WHEN salary > 10000 THEN
        INTO high_salaries (emp_id, salary) VALUES (emp_id, salary)
    WHEN salary > 5000 THEN
        INTO mid_salaries (emp_id, salary) VALUES (emp_id, salary)
    ELSE
        INTO low_salaries (emp_id, salary) VALUES (emp_id, salary)
SELECT employee_id AS emp_id, salary
FROM employees;
```

| 约束位置 | 约束 |
|----------|------|
| `INSERT ALL` / `INSERT FIRST` | 保持一行 |
| 每个 `INTO` | `forceNewline(true)`，缩进一级 |
| `INTO` → 目标表 | 同行 |
| `VALUES` → 源列 / 字面量 | 与 `INTO table` 同行或缩进对齐 |
| **最后一 `VALUES` → `SELECT`** | `forceNewline(true).indentDelta(0)` |
| `WHEN ... THEN`（FIRST 模式） | `WHEN` 前 `forceNewline(true).indentDelta(1)`，`THEN` 后同行 |
| `ELSE`（FIRST 模式） | `ELSE` 前 `forceNewline(true).indentDelta(0)` |
| SELECT 语句 | 独立一行，复用 §9 DQL 子句树深度格式化规则 |
| SELECT → FROM | 同 §8.2 DQL 关键字断行约束 |
| 无 SELECT 的形式 B | `SELECT * FROM DUAL` 作为尾部，同 SELECT 规则处理 |

**INSERT ALL/FIRST 检测**：

```java
private boolean isInsertAllOrFirst(PlSqlBlock block) {
    int insertIdx = block.startTokenIdx;
    TokenInfo next = nextVisible(insertIdx + 1);
    return next != null && ("ALL".equals(next.upper) || "FIRST".equals(next.upper));
}
```

**约束生成**：

```java
private void walkInsertAllOrFirst(PlSqlBlock block) {
    // INSERT ALL/FIRST 保持一行
    int insertIdx = block.startTokenIdx;
    int allOrFirstIdx = insertIdx + 1;
    gap(insertIdx, allOrFirstIdx).newlineMode(FORBIDDEN).minSpaces(1);

    // 收集所有 INTO 子句
    List<InsertIntoClause> intoClauses = block.getIntoClauses();

    // 每个 INTO 前换行并缩进
    for (int i = 0; i < intoClauses.size(); i++) {
        InsertIntoClause clause = intoClauses.get(i);
        int intoIdx = clause.getIntoTokenIndex();

        if (i == 0) {
            // 第一个 INTO：与 ALL/FIRST 之间
            gap(allOrFirstIdx, intoIdx).forceNewline(true).indentDelta(1);
        } else {
            // 后续 INTO：与上一 VALUES 之间
            gap(intoClauses.get(i-1).getValuesEnd(), intoIdx)
                .forceNewline(true).indentDelta(1);
        }

        // INTO → table — 同行
        gap(intoIdx, clause.getTableIdx()).newlineMode(FORBIDDEN);
        // table → VALUES — 同行或缩进对齐
        gap(clause.getTableIdx(), clause.getValuesKeywordIdx())
            .newlineMode(OPTIONAL).breakPenalty(0.3).indentDelta(0);
        // VALUES → ( — 同行
        gap(clause.getValuesKeywordIdx(), clause.getLparenIdx())
            .newlineMode(FORBIDDEN);
    }

    // 最后一 VALUES → SELECT（形式 A）或 SELECT → FROM DUAL（形式 B）
    InsertIntoClause last = intoClauses.get(intoClauses.size() - 1);
    int selectIdx = block.getSelectStartIdx(); // SELECT 关键字位置
    if (selectIdx > 0) {
        gap(last.getValuesEnd(), selectIdx).forceNewline(true).indentDelta(0);
        // SELECT 内部复用 §9 DQL 约束生成
        walkSelect(block.getSelectSubBlock());
    }

    // 分号黏附
    int semiIdx = block.endTokenIdx;
    gap(semiIdx - 1, semiIdx).newlineMode(FORBIDDEN).minSpaces(0);
}
```

**INSERT FIRST 的 WHEN/ELSE 条件约束**：

```java
if (block.isInsertFirst()) {
    List<WhenBranch> whenBranches = block.getWhenBranches();
    for (int i = 0; i < whenBranches.size(); i++) {
        WhenBranch wb = whenBranches.get(i);
        // WHEN 前换行，缩进一级
        gap(wb.getPrevTokenIdx(), wb.getWhenIdx())
            .forceNewline(true).indentDelta(1);
        // WHEN condition → THEN — 同行
        gap(wb.getConditionEnd(), wb.getThenIdx())
            .newlineMode(FORBIDDEN);
        // THEN → INTO — 同行
        gap(wb.getThenIdx(), wb.getIntoIdx())
            .minSpaces(1);
    }
    int elseIdx = block.getElseIdx();
    if (elseIdx > 0) {
        gap(elseIdx - 1, elseIdx).forceNewline(true).indentDelta(0);
    }
}
```

#### 14.1.3 RETURNING INTO

```sql
INSERT INTO employees (employee_id, first_name, last_name)
VALUES (emp_seq.NEXTVAL, 'John', 'Doe')
RETURNING employee_id INTO v_emp_id;

-- 多变量
INSERT INTO employees (employee_id, first_name, last_name)
VALUES (emp_seq.NEXTVAL, 'John', 'Doe')
RETURNING employee_id, salary INTO v_emp_id, v_salary;
```

| 模式 | 约束 |
|------|------|
| `SINGLE_LINE` | `gap(RETURNING, into_clause).newlineMode(FORBIDDEN)` |
| `ALIGNED` | `RETURNING` 后变量对齐 `alignGroupId="RETURNING"` |
| `EXPAND` | `gap(RETURNING, var1).forceNewline(true).indentDelta(+1)` |

### 14.2 UPDATE 格式化

#### 14.2.1 SET 子句约束

```sql
UPDATE employees
SET
    first_name = 'John',
    last_name = 'Doe',
    salary = 5000,
    department_id = 50
WHERE employee_id = 100;
```

| 约束位置 | 约束 |
|----------|------|
| `UPDATE` → 表名 | `minSpaces=1` |
| `SET` → 第一赋值 | `forceNewline(true).indentDelta(1)` |
| 赋值 `,` → 下一赋值 | `OPTIONAL` 或 `REQUIRED`（由 `dmlUpdateSetPerLine` 控制） |

#### 14.2.2 SET 赋值对齐模式

```sql
-- 不对齐
SET
    first_name = 'John',
    last_name = 'Doe',
    salary = 5000

-- = 对齐
SET
    first_name = 'John',
    last_name  = 'Doe',
    salary     = 5000
```

`dmlUpdateSetAlign` (boolean) — true 时 `=` 前 `alignGroupId="UPDATE_SET"`。

#### 14.2.3 SET 每行列数

`dmlUpdateColumnsPerRow` (int) — UPDATE SET 每行最大赋值数量。0=不限，≥1 时分行：

```sql
UPDATE employees
SET
    first_name = 'John', last_name = 'Doe',
    salary = 5000, department_id = 50
WHERE employee_id = 100;
```

### 14.3 DELETE 格式化

#### 14.3.1 FROM 省略处理

Oracle PL/SQL 允许 `DELETE table_name` 省略 `FROM`：

| 模式 | 输出 |
|------|------|
| `KEEP` | `DELETE FROM employees WHERE ...` |
| `REMOVE` | `DELETE employees WHERE ...` |

`dmlDeleteFromHandling` (enum: KEEP/REMOVE)。

```sql
DELETE FROM employees
WHERE department_id = 50;

-- REMOVE 模式
DELETE employees
WHERE department_id = 50;
```

### 14.4 参数汇总

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `insertColumnFormat` | enum | COMPACT | INSERT 列清单格式（COMPACT/EXPAND） |
| `insertColumnsPerRow` | int | 0 | INSERT 列每行 N 列（0=不限） |
| `insertValuesPerRow` | int | 0 | VALUES 每行 N 值（0=不限） |
| `dmlInsertColumnNewline` | boolean | false | INSERT 列清单前换行 |
| `dmlValuesNewline` | boolean | false | VALUES 前换行 |
| `dmlValuesExpand` | boolean | false | VALUES 值列表每值一行 |
| `dmlInsertAllIndent` | boolean | true | INSERT ALL 子 INSERT 缩进 |
| `dmlReturningIntoStyle` | enum | SINGLE_LINE | RETURNING INTO 格式 |
| `dmlIntoAlign` | boolean | true | INTO 变量列表对齐 |
| `dmlUpdateSetPerLine` | boolean | true | SET 每赋值一行 |
| `dmlUpdateSetAlign` | boolean | false | SET = 对齐 |
| `dmlUpdateColumnsPerRow` | int | 0 | SET 每行列数 |
| `updateSetCommaPosition` | enum | TRAILING | UPDATE SET 逗号位置 |
| `dmlDeleteFromHandling` | enum | KEEP | DELETE FROM 处理 |

---

## 15. BULK COLLECT / USING 对齐

### 15.1 BULK COLLECT INTO 多变量对齐

```sql
-- 不对齐
SELECT employee_id, first_name, last_name
BULK COLLECT INTO v_emp_id, v_first_name, v_last_name
FROM employees;

-- 对齐（dmlBulkCollectAlign=true）
SELECT employee_id, first_name, last_name
BULK COLLECT INTO v_emp_id,
                  v_first_name,
                  v_last_name
FROM employees;
```

约束：`dmlBulkCollectAlign=true` 时，BULK COLLECT INTO 后变量列表接入 `alignGroupId="BULK_INTO"`，跨行对齐首变量。

### 15.2 EXECUTE IMMEDIATE USING 对齐

```sql
-- 不对齐
EXECUTE IMMEDIATE v_sql
USING p_id, p_name, p_salary;

-- 对齐（dmlUsingAlign=true）
EXECUTE IMMEDIATE v_sql
USING IN p_id,
      IN p_name,
      OUT p_salary;
```

约束：`dmlUsingAlign=true` 时，USING 后表达式列表 `alignGroupId="DML_USING"` 对齐首元素。

### 15.3 参数汇总

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `dmlBulkCollectAlign` | boolean | true | BULK COLLECT INTO 对齐 |
| `dmlUsingAlign` | boolean | true | USING 子句对齐 |

---

## 16. DDL 格式化总则

### 16.1 设计原则

DDL 格式化遵循以下原则：

1. **约束引擎统一处理** — DDL 不走外部模板引擎，均在 `ConstraintGenerator` 中以 `GapConstraint` + `AlignGroup` 模型处理
2. **列对齐优先** — 列定义、约束、索引选项等使用 AlignGroup 对齐模型
3. **语句类型路由** — 按 DDL 类型路由到对应的约束生成方法
4. **方言差异隔离** — 各方言特有的 DDL 语法通过方言模板工厂处理

### 16.2 关键字断行约束

| DDL 关键字 | 前换行 | 后缩进 |
|-----------|--------|--------|
| CREATE [OR REPLACE] | REQUIRED(块首) | 0 |
| TABLE | 同行 | 0 |
| INDEX | 同行 | 0 |
| VIEW | 同行 | 0 |
| MATERIALIZED VIEW | 同行 | 0 |
| `(` (列清单) | OPTIONAL | 1 |
| `)` | OPTIONAL | -1 |
| AS | OPTIONAL | 1 |
| TABLESPACE | OPTIONAL | 0 |
| ENABLE/DISABLE | OPTIONAL | 0 |
| USING INDEX | OPTIONAL | 1 |
| STORAGE | OPTIONAL | 1 |
| PCTFREE/PCTUSED | OPTIONAL | 0 |
| LOGGING/NOLOGGING | OPTIONAL | 0 |

### 16.3 DDL 类型路由

```java
switch (block.type) {
    case DDL_CREATE_TABLE:   walkCreateTable(block); break;
    case DDL_CREATE_INDEX:   walkCreateIndex(block); break;
    case DDL_CREATE_VIEW:    walkCreateView(block);  break;
    case DDL_CREATE_MVIEW:   walkCreateMView(block); break;
    case DDL_ALTER_TABLE:    walkAlterTable(block);  break;
    case DDL_ALTER_INDEX:    walkAlterIndex(block);  break;
    case DDL_ALTER_TABLESPACE: walkAlterTbsp(block); break;
    case DDL_ALTER_USER:     walkAlterUser(block);   break;
    case DDL_ALTER_SEQUENCE: walkAlterSeq(block);    break;
    // ... 其他 DDL 类型
}
```

---

## 17. CREATE TABLE 格式化

### 17.1 基本列定义

```sql
-- 列名左对齐 + 类型列左对齐
CREATE TABLE employees (
    employee_id    NUMBER(6)        NOT NULL,
    first_name     VARCHAR2(20)     NOT NULL,
    last_name      VARCHAR2(25)     NOT NULL,
    email          VARCHAR2(25)     NOT NULL,
    phone_number   VARCHAR2(20),
    hire_date      DATE             NOT NULL,
    salary         NUMBER(8,2),
    commission_pct NUMBER(2,2),
    department_id  NUMBER(4)
);
```

列对齐约束：
- `alignGroupId="COL_NAME"` — 列名左对齐
- `alignGroupId="COL_TYPE"` — 类型左对齐
- `alignGroupId="COL_NULL"` — NOT NULL 左对齐

### 17.2 约束定义

```sql
CREATE TABLE employees (
    employee_id    NUMBER(6),
    first_name     VARCHAR2(20)     NOT NULL,
    last_name      VARCHAR2(25)     NOT NULL,
    email          VARCHAR2(25)     NOT NULL,
    department_id  NUMBER(4),
    CONSTRAINT emp_pk PRIMARY KEY (employee_id)
        USING INDEX TABLESPACE users,
    CONSTRAINT emp_email_uk UNIQUE (email),
    CONSTRAINT emp_dept_fk FOREIGN KEY (department_id)
        REFERENCES departments (department_id),
    CONSTRAINT emp_salary_ck CHECK (salary > 0),
    CONSTRAINT emp_dept_nn CHECK (department_id IS NOT NULL)
);
```

| 约束类型 | 关键字 | 格式化规则 |
|---------|--------|-----------|
| PRIMARY KEY | `PRIMARY KEY (cols)` | 与列同行，可选换行 |
| FOREIGN KEY | `FOREIGN KEY (col) REFERENCES t(col)` | 关键子句每选项一行 |
| UNIQUE | `UNIQUE (cols)` | 同列定义行 |
| CHECK | `CHECK (expr)` | 同列定义行 |
| NOT NULL | `col NOT NULL` | 同列定义行 |
| EXCLUDE (PG) | `EXCLUDE USING gist (col WITH =)` | 同列定义行 |

### 17.3 约束状态子句

```sql
-- ENABLE / DISABLE / VALIDATE / NOVALIDATE
CONSTRAINT emp_salary_ck CHECK (salary > 0)
    ENABLE NOVALIDATE,
CONSTRAINT emp_fk FOREIGN KEY (dept_id)
    REFERENCES departments(dept_id)
    DISABLE NOVALIDATE,
CONSTRAINT pk_emp PRIMARY KEY (emp_id)
    ENABLE VALIDATE
    USING INDEX TABLESPACE users
    MONITORING
```

每个状态子句独立一行或紧凑同行（由 `ddlConstraintStatePerLine` 控制）。

### 17.4 虚拟列 / 标识列

```sql
-- 虚拟列 (Oracle 11g+)
CREATE TABLE employees (
    employee_id    NUMBER(6),
    first_name     VARCHAR2(20),
    last_name      VARCHAR2(25),
    full_name      VARCHAR2(46) GENERATED ALWAYS AS (
                       first_name || ' ' || last_name
                   ) VIRTUAL
);

-- 标识列 (Oracle 12c+)
CREATE TABLE departments (
    department_id NUMBER(2)
        GENERATED BY DEFAULT AS IDENTITY (
            START WITH 10
            INCREMENT BY 10
            MAXVALUE 99
            CYCLE
        ),
    department_name VARCHAR2(30)
);
```

| 约束位置 | 规则 |
|----------|------|
| `GENERATED ALWAYS AS` → `(` | 保持同行或 OPTIONAL 换行 |
| `GENERATED BY DEFAULT AS IDENTITY` → `(` | 保持同行 |
| 标识列选项 | 每选项一行或紧凑 |

### 17.5 LOB / 集合列

```sql
CREATE TABLE documents (
    doc_id      NUMBER(6),
    doc_content CLOB,
    doc_xml     XMLTYPE,
    doc_image   BLOB,
    doc_meta    my_metadata_type
) LOB (doc_content, doc_xml)
  STORE AS SECUREFILE (
      TABLESPACE lob_data
      STORAGE (INITIAL 1M NEXT 1M)
      ENABLE STORAGE IN ROW
  );
```

LOB 存储子句每选项一行，由 `ddlLobOptionPerLine` 控制。

### 17.6 分区表

```sql
-- 范围分区
CREATE TABLE sales (
    sale_id    NUMBER,
    sale_date  DATE,
    amount     NUMBER
)
PARTITION BY RANGE (sale_date) (
    PARTITION p_2024q1 VALUES LESS THAN (DATE '2024-04-01'),
    PARTITION p_2024q2 VALUES LESS THAN (DATE '2024-07-01'),
    PARTITION p_2024q3 VALUES LESS THAN (DATE '2024-10-01'),
    PARTITION p_2024q4 VALUES LESS THAN (DATE '2025-01-01')
);

-- 列表分区
PARTITION BY LIST (region) (
    PARTITION p_asia VALUES ('CN', 'JP', 'KR'),
    PARTITION p_americas VALUES ('US', 'CA', 'MX')
);

-- 间隔分区 (Oracle 11g+)
PARTITION BY RANGE (sale_date)
INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))
(
    PARTITION p_before_2024 VALUES LESS THAN (DATE '2024-01-01')
);

-- 自动列表分区 (Oracle 12c+)
PARTITION BY LIST (region) AUTOMATIC (
    PARTITION p_known VALUES ('CN', 'US', 'JP')
);

-- 引用分区
CREATE TABLE order_items (
    order_id    NUMBER,
    item_id     NUMBER,
    product_id  NUMBER,
    quantity    NUMBER,
    CONSTRAINT fk_order FOREIGN KEY (order_id)
        REFERENCES orders(order_id)
)
PARTITION BY REFERENCE (fk_order);
```

| 分区类型 | 特有格式 |
|---------|---------|
| RANGE | 每分区一行，`VALUES LESS THAN` 对齐 |
| LIST | 每分区一行，`VALUES (...)` 对齐 |
| INTERVAL | `INTERVAL (expr)` 独立一行 |
| HASH | 每分区一行，同 RANGE |
| REFERENCE | 引用外键约束 |
| AUTOMATIC | 关键字跟在 PARTITION BY 后 |

### 17.7 表组织 / 存储选项

```sql
-- 堆组织表
CREATE TABLE employees (
    ...
) TABLESPACE users
  STORAGE (INITIAL 64K NEXT 64K)
  PCTFREE 10
  PCTUSED 40
  LOGGING;

-- 索引组织表
CREATE TABLE emp_iot (
    employee_id NUMBER(6) PRIMARY KEY,
    ...
) ORGANIZATION INDEX
  TABLESPACE users
  PCTTHRESHOLD 20;
```

### 17.8 AS SELECT

```sql
-- CTAS
CREATE TABLE emp_backup
AS
    SELECT employee_id, first_name, last_name
    FROM employees
    WHERE department_id = 100;
```

`AS` 后换行，SELECT 独立一行并缩进一级。SELECT 内部复用 §9 DQL 规则。

### 17.9 CREATE TABLE 参数汇总

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ddlColumnAlign` | boolean | true | 列名/类型/NOT NULL 三列对齐 |
| `columnDefColumnsPerRow` | int | 0 | 列定义每行 N 列（0=无限） |
| `columnDefTypeCase` | enum | PRESERVE | 类型关键字大小写 |
| `constraintFormat` | enum | PER_LINE | 约束格式（PER_LINE/COMPACT） |
| `constraintColumnsPerRow` | int | 0 | 约束每行 N 列 |
| `storageClauseFormat` | enum | PER_LINE | STORAGE 子句格式 |
| `partitionFormat` | enum | PER_LINE | 分区定义格式 |
| `ddlConstraintStatePerLine` | boolean | true | 约束状态子句每行一个 |

---

## 18. CREATE INDEX 格式化

```sql
-- 单列索引
CREATE INDEX emp_name_idx
    ON employees (last_name, first_name)
    TABLESPACE users;

-- 函数索引
CREATE INDEX emp_upper_name_idx
    ON employees (UPPER(last_name));

-- 位图索引
CREATE BITMAP INDEX emp_dept_bmp
    ON employees (department_id);

-- 唯一索引
CREATE UNIQUE INDEX emp_email_uk
    ON employees (email);

-- 局部索引
CREATE INDEX sales_region_idx
    ON sales (region)
    LOCAL;

-- 全局索引
CREATE INDEX sales_date_gidx
    ON sales (sale_date)
    GLOBAL;
```

| 约束位置 | 规则 |
|----------|------|
| `CREATE [UNIQUE/BITMAP] INDEX` → 索引名 | 同行 |
| 索引名 → `ON` | 同行 |
| `ON` → `table(cols)` | 同行 |
| `(cols)` → 存储/表空间选项 | `OPTIONAL` 换行，每选项一行 |
| `LOCAL` / `GLOBAL` | 索引选项后可选换行 |

### 18.1 索引选项

```sql
CREATE INDEX emp_name_idx
    ON employees (last_name, first_name)
    TABLESPACE users
    STORAGE (INITIAL 64K NEXT 64K)
    PCTFREE 10
    COMPUTE STATISTICS
    ONLINE;
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ddlIndexOptionPerLine` | boolean | true | 索引选项每行一个 |
| `indexColumnFormat` | enum | COMPACT | 索引列格式（COMPACT/EXPAND） |
| `indexColumnsPerRow` | int | 0 | 索引列每行 N 列 |

---

## 19. CREATE VIEW 格式化

```sql
-- 简单视图
CREATE OR REPLACE VIEW emp_view AS
    SELECT e.employee_id,
           e.first_name,
           e.last_name,
           d.department_name
    FROM employees e
    JOIN departments d ON e.department_id = d.department_id;

-- 带列别名
CREATE OR REPLACE VIEW emp_sal_view (
    emp_id,
    emp_name,
    monthly_sal
) AS
    SELECT employee_id,
           first_name || ' ' || last_name,
           salary / 12
    FROM employees;

-- 物化视图
CREATE MATERIALIZED VIEW emp_summary_mv
    TABLESPACE users
    BUILD IMMEDIATE
    REFRESH COMPLETE ON DEMAND
AS
    SELECT department_id,
           COUNT(*) AS emp_count,
           AVG(salary) AS avg_salary
    FROM employees
    GROUP BY department_id;
```

| 约束位置 | 规则 |
|----------|------|
| `VIEW` → 视图名 | 同行 |
| 视图名 → `(列别名)` / `AS` | 非必须括号同行或 OPTIONAL 换行 |
| `AS` → SELECT | `forceNewline(true).indentDelta(1)` |
| 物化视图选项 | 每选项一行 |

---

## 20. ALTER 语句格式化

### 20.1 ALTER TABLE

```sql
ALTER TABLE employees
    ADD (
        middle_name VARCHAR2(50),
        birth_date  DATE
    );

ALTER TABLE employees
    MODIFY (
        salary NUMBER(10,2) DEFAULT 0 NOT NULL,
        email  VARCHAR2(50)
    );

ALTER TABLE employees
    DROP (
        middle_name,
        birth_date
    );

ALTER TABLE employees
    RENAME COLUMN email TO email_address;

ALTER TABLE employees
    SET UNUSED COLUMN phone_number;

ALTER TABLE employees
    ADD CONSTRAINT emp_mgr_fk FOREIGN KEY (manager_id)
        REFERENCES employees (employee_id)
        ENABLE;

ALTER TABLE employees
    MODIFY CONSTRAINT emp_salary_ck CHECK (salary >= 0);

ALTER TABLE employees
    RENAME CONSTRAINT emp_salary_ck TO emp_sal_ck;

-- Oracle 12c+
ALTER TABLE employees
    DROP CONSTRAINT emp_salary_ck;

ALTER TABLE employees
    ENABLE CONSTRAINT emp_pk;

ALTER TABLE employees
    DISABLE CONSTRAINT emp_fk;

-- 多列操作
ALTER TABLE employees
    ADD (street VARCHAR2(50)),
    ADD (city VARCHAR2(30)),
    DROP (phone_number);
```

### 20.2 ALTER INDEX

```sql
ALTER INDEX emp_name_idx
    RENAME TO emp_name_idx_new;

ALTER INDEX emp_name_idx
    REBUILD
    TABLESPACE users
    STORAGE (INITIAL 128K NEXT 128K)
    ONLINE;

ALTER INDEX emp_name_idx
    MONITORING USAGE;

ALTER INDEX emp_name_idx
    UNUSABLE;
```

### 20.3 ALTER TABLESPACE

```sql
ALTER TABLESPACE users
    ADD DATAFILE 'users02.dbf' SIZE 100M
    AUTOEXTEND ON NEXT 10M MAXSIZE 1G;

ALTER TABLESPACE users
    RENAME TO users_data;

ALTER TABLESPACE users
    READ ONLY;

ALTER TABLESPACE users
    DEFAULT STORAGE (INITIAL 128K NEXT 128K);

ALTER TABLESPACE users
    OFFLINE NORMAL;
```

### 20.4 ALTER SEQUENCE

```sql
ALTER SEQUENCE emp_seq
    INCREMENT BY 10
    MAXVALUE 99999
    NOCACHE
    CYCLE;
```

### 20.5 ALTER USER

```sql
ALTER USER scott
    IDENTIFIED BY tiger
    DEFAULT TABLESPACE users
    TEMPORARY TABLESPACE temp
    QUOTA 100M ON users
    PROFILE app_user
    ACCOUNT UNLOCK;
```

### 20.6 参数汇总

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ddlAlterColumnPerLine` | boolean | true | ALTER 多列每列一行 |
| `ddlAlterAddDropPerLine` | boolean | true | ADD/DROP 每操作一行 |
| `ddlConstraintStatePerLine` | boolean | true | 约束状态子句每行一个 |
| `ddlIndexOptionPerLine` | boolean | true | 索引选项每行一个 |
| `ddlTbspOptionPerLine` | boolean | true | TABLESPACE 选项每行一个 |
| `ddlSeqOptionPerLine` | boolean | true | SEQUENCE 选项每行一个 |
| `ddlUserOptionPerLine` | boolean | true | USER 选项每行一个 |
| `ddlGrantPrivPerLine` | boolean | false | GRANT 权限每行一个 |
| `ddlFlashbackOptionPerLine` | boolean | true | FLASHBACK 子句每行一个 |
| `ddlFlashbackCompact` | boolean | false | FLASHBACK 整句一行 |
| `ddlAnalyzeFormat` | enum | COMPACT | ANALYZE 格式 |
| `ddlAnalyzePartitionPerLine` | boolean | false | 分区子句独立一行 |

---

## 21. 其他 DDL 语句

### 21.1 CREATE TABLESPACE

```sql
CREATE TABLESPACE users
    DATAFILE 'users01.dbf'
    SIZE 500M
    AUTOEXTEND ON NEXT 100M
    MAXSIZE 2G
    EXTENT MANAGEMENT LOCAL
    SEGMENT SPACE MANAGEMENT AUTO;
```

### 21.2 CREATE USER / PROFILE

```sql
CREATE USER app_user
    IDENTIFIED BY password
    DEFAULT TABLESPACE users
    TEMPORARY TABLESPACE temp
    QUOTA UNLIMITED ON users
    PROFILE app_profile;

CREATE PROFILE app_profile LIMIT
    SESSIONS_PER_USER         10
    CPU_PER_SESSION           UNLIMITED
    CONNECT_TIME              60
    IDLE_TIME                 30
    FAILED_LOGIN_ATTEMPTS     5;
```

### 21.3 CREATE SEQUENCE

```sql
CREATE SEQUENCE emp_seq
    START WITH 1000
    INCREMENT BY 1
    MAXVALUE 9999999
    NOCACHE
    NOCYCLE;
```

### 21.4 GRANT / REVOKE

```sql
-- 系统权限
GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW
    TO app_user;

-- 对象权限
GRANT SELECT, INSERT, UPDATE (emp_id, emp_name)
    ON employees
    TO app_user
    WITH GRANT OPTION;

-- 角色
GRANT app_role
    TO app_user;

-- REVOKE
REVOKE CREATE TABLE
    FROM app_user;

REVOKE SELECT, INSERT
    ON employees
    FROM app_user;
```

| 约束位置 | 规则 |
|----------|------|
| `GRANT` → 权限列表 | 同行 |
| 权限间逗号 | `FORBIDDEN`（保持同行） |
| 权限列表 → `ON` | 可选换行（`breakPenalty=0.5`） |
| 权限列表 → `TO` | 可选换行（权限多时换行） |
| `WITH GRANT OPTION` | 行尾，权限语句后 |

**walkGrant 约束生成**：

```java
private void walkGrant(PlSqlBlock block) {
    List<TokenInfo> tokens = block.tokens;
    int grantIdx = block.startTokenIdx;          // GRANT
    int onIdx = findKeyword(block, "ON");
    int toIdx = findKeyword(block, "TO");
    int wgoIdx = findKeyword(block, "WITH");

    // 权限列表间逗号：禁止换行
    for (int i = grantIdx + 1; i < (onIdx > 0 ? onIdx : toIdx); i++) {
        if (tokens.get(i).equals(",")) {
            gap(i, i+1).newlineMode(FORBIDDEN);
            gap(i-1, i).newlineMode(FORBIDDEN);
        }
    }

    // 权限列表 → TO：可选换行
    if (toIdx > 0) {
        GapConstraint g = gap(toIdx - 1, toIdx);
        g.newlineMode(OPTIONAL).breakPenalty(0.5).indentDelta(1);
        out.add(g);
    }

    // 权限列表 → ON：可选换行
    if (onIdx > 0) {
        GapConstraint g = gap(onIdx - 1, onIdx);
        g.newlineMode(OPTIONAL).breakPenalty(0.5).indentDelta(1);
        out.add(g);
    }

    // ON → TO：可选换行
    if (onIdx > 0 && toIdx > 0) {
        GapConstraint g = gap(onIdx, toIdx);
        g.newlineMode(OPTIONAL).breakPenalty(0.5).indentDelta(0);
        out.add(g);
    }

    // WITH GRANT OPTION 前可选换行
    if (wgoIdx > 0) {
        gap(wgoIdx - 1, wgoIdx).newlineMode(OPTIONAL).breakPenalty(0.5);
    }

    // 分号黏附
    int semiIdx = block.endTokenIdx;
    gap(semiIdx - 1, semiIdx).newlineMode(FORBIDDEN).minSpaces(0);
}

private void walkRevoke(PlSqlBlock block) {
    // REVOKE → FROM 结构，约束逻辑与 GRANT 对称
    List<TokenInfo> tokens = block.tokens;
    int revokeIdx = block.startTokenIdx;
    int onIdx = findKeyword(block, "ON");
    int fromIdx = findKeyword(block, "FROM");

    // 权限列表间逗号：禁止换行
    for (int i = revokeIdx + 1; i < (onIdx > 0 ? onIdx : fromIdx); i++) {
        if (tokens.get(i).equals(",")) {
            gap(i, i+1).newlineMode(FORBIDDEN);
            gap(i-1, i).newlineMode(FORBIDDEN);
        }
    }

    // 权限列表 → ON（可选）
    if (onIdx > 0) {
        gap(onIdx - 1, onIdx).newlineMode(OPTIONAL).breakPenalty(0.5).indentDelta(1);
    }

    // ON → FROM（可选）
    if (onIdx > 0 && fromIdx > 0) {
        gap(onIdx, fromIdx).newlineMode(OPTIONAL).breakPenalty(0.5).indentDelta(0);
    }

    // 权限列表 → FROM（可选）
    if (fromIdx > 0) {
        gap(fromIdx - 1, fromIdx).newlineMode(OPTIONAL).breakPenalty(0.5).indentDelta(1);
    }

    // 分号黏附
    int semiIdx = block.endTokenIdx;
    gap(semiIdx - 1, semiIdx).newlineMode(FORBIDDEN).minSpaces(0);
}
```

### 21.5 TRUNCATE / DROP / RENAME

```sql
TRUNCATE TABLE employees;
DROP TABLE employees PURGE;
DROP INDEX emp_name_idx;
DROP VIEW emp_view;
DROP SEQUENCE emp_seq;
RENAME employees TO employees_old;
```

格式化规则：一行，不换行。

**walkTruncate / walkDrop / walkRename 约束生成**：

```java
private void walkTruncate(PlSqlBlock block) {
    // TRUNCATE TABLE name [PURGE] — 一行
    int start = block.startTokenIdx;
    int end = block.endTokenIdx;
    for (int i = start; i <= end; i++) {
        gap(i, i+1).newlineMode(FORBIDDEN).minSpaces(1);
    }
    gap(end - 1, end).newlineMode(FORBIDDEN).minSpaces(0); // 分号黏附
}

private void walkDrop(PlSqlBlock block) {
    // DROP TABLE/INDEX/VIEW/SEQUENCE name [PURGE] — 一行
    int start = block.startTokenIdx;
    int end = block.endTokenIdx;
    for (int i = start; i <= end; i++) {
        gap(i, i+1).newlineMode(FORBIDDEN).minSpaces(1);
    }
    gap(end - 1, end).newlineMode(FORBIDDEN).minSpaces(0);
}

private void walkRename(PlSqlBlock block) {
    // RENAME old TO new — 一行
    int start = block.startTokenIdx;
    int end = block.endTokenIdx;
    for (int i = start; i <= end; i++) {
        gap(i, i+1).newlineMode(FORBIDDEN).minSpaces(1);
    }
    gap(end - 1, end).newlineMode(FORBIDDEN).minSpaces(0);
}
```

### 21.6 COMMENT

```sql
COMMENT ON TABLE employees IS '员工信息表';
COMMENT ON COLUMN employees.salary IS '月薪';
```

格式化规则：一行，字符串内容不修改。

**walkComment 约束生成**：

```java
private void walkComment(PlSqlBlock block) {
    // COMMENT ON {TABLE|COLUMN} name IS 'text' — 一行
    int start = block.startTokenIdx;
    int end = block.endTokenIdx;

    // 字符串字面量原样透传（channel=HIDDEN），不施加约束
    for (int i = start; i <= end; i++) {
        TokenInfo t = tokens.get(i);
        if (t.isStringLiteral || t.channel == HIDDEN) {
            // 跳过字符串/注释内部
            continue;
        }
        gap(i, i+1).newlineMode(FORBIDDEN).minSpaces(1);
    }
    gap(end - 1, end).newlineMode(FORBIDDEN).minSpaces(0); // 分号黏附
}
```

### 21.7 FLASHBACK / PURGE

#### 21.7.1 FLASHBACK 语法变体

```sql
-- 1. 闪回删除
FLASHBACK TABLE employees TO BEFORE DROP;
FLASHBACK TABLE employees TO BEFORE DROP RENAME TO employees_old;

-- 2. 闪回时间点
FLASHBACK TABLE employees TO TIMESTAMP SYSTIMESTAMP - INTERVAL '1' HOUR;
FLASHBACK TABLE employees TO TIMESTAMP TO_TIMESTAMP('2024-01-01 10:00:00', 'YYYY-MM-DD HH24:MI:SS');

-- 3. 闪回 SCN
FLASHBACK TABLE employees TO SCN 1234567;

-- 4. 闪回数据库（需 MOUNT 状态）
FLASHBACK DATABASE TO TIMESTAMP SYSTIMESTAMP - INTERVAL '2' HOUR;

-- 5. 带 ENABLE/DISABLE
ALTER TABLE employees ENABLE ROW MOVEMENT;
FLASHBACK TABLE employees TO TIMESTAMP SYSTIMESTAMP - INTERVAL '1' HOUR;
```

#### 21.7.2 FLASHBACK 约束规则

| 约束位置 | 规则 |
|----------|------|
| `FLASHBACK` → `TABLE`/`DATABASE` | 同行 |
| `TABLE name` → `TO` | 同行 |
| `TO` → `BEFORE`/`TIMESTAMP`/`SCN` | `OPTIONAL` 换行，缩进一级（`ddlFlashbackOptionPerLine=true` 时强制换行） |
| `BEFORE DROP` → `RENAME TO` | `OPTIONAL` 换行（缩进一级） |
| 时间值表达式 | 整段不折行 |
| `FLASHBACK` 前 | `forceNewline(true)`（独立语句） |

#### 21.7.3 PURGE 语法变体

```sql
PURGE TABLE employees;
PURGE INDEX emp_idx;
PURGE RECYCLEBIN;
PURGE DBA_RECYCLEBIN;
PURGE TABLESPACE users;
PURGE TABLESPACE users USER scott;
```

#### 21.7.4 PURGE 约束规则

| 约束位置 | 规则 |
|----------|------|
| `PURGE` → 对象类型 | 同行 |
| 对象类型 → 名称（可选） | 同行 |
| `TABLESPACE ts` → `USER`（可选） | `OPTIONAL` 换行（缩进一级） |
| 整句 | 保持一行，不换行 |

#### 21.7.5 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ddlFlashbackOptionPerLine` | boolean | true | FLASHBACK 子句每行一个 |
| `ddlFlashbackCompact` | boolean | false | FLASHBACK 整句一行 |

`ddlFlashbackOptionPerLine=true` 时：

```sql
FLASHBACK TABLE employees
    TO TIMESTAMP
        SYSTIMESTAMP - INTERVAL '1' HOUR;
```

`ddlFlashbackCompact=true` 时：

```sql
FLASHBACK TABLE employees TO TIMESTAMP SYSTIMESTAMP - INTERVAL '1' HOUR;
```

#### 21.7.6 walkFlashback / walkPurge 约束生成

```java
private void walkFlashback(PlSqlBlock block) {
    List<TokenInfo> tokens = block.tokens;
    int start = block.startTokenIdx;

    // FLASHBACK TABLE/DATABASE name → TO：同行
    int toIdx = findKeyword(block, "TO");

    if (opts.isDdlFlashbackCompact()) {
        // 整句一行
        int end = block.endTokenIdx;
        for (int i = start; i <= end; i++) {
            gap(i, i+1).newlineMode(FORBIDDEN).minSpaces(1);
        }
        gap(end - 1, end).newlineMode(FORBIDDEN).minSpaces(0);
        return;
    }

    if (opts.isDdlFlashbackOptionPerLine()) {
        // TO 前换行
        if (toIdx > 0) {
            gap(toIdx - 1, toIdx).forceNewline(true).indentDelta(1);
        }

        // TO → BEFORE/TIMESTAMP/SCN
        int modeIdx = findAnyKeyword(block, "BEFORE", "TIMESTAMP", "SCN");
        if (modeIdx > 0 && toIdx > 0) {
            gap(toIdx, modeIdx).forceNewline(true).indentDelta(1);
        }

        // BEFORE → RENAME TO
        if (findKeyword(block, "BEFORE") > 0) {
            int renameIdx = findKeyword(block, "RENAME");
            if (renameIdx > 0) {
                gap(renameIdx - 1, renameIdx).forceNewline(true).indentDelta(1);
            }
        }
    } else {
        // 一行（紧凑不展开）
        int end = block.endTokenIdx;
        for (int i = start; i <= end; i++) {
            gap(i, i+1).newlineMode(FORBIDDEN).minSpaces(1);
        }
    }

    // 分号黏附
    gap(block.endTokenIdx - 1, block.endTokenIdx).newlineMode(FORBIDDEN).minSpaces(0);
}

private void walkPurge(PlSqlBlock block) {
    // PURGE {TABLE|INDEX name|RECYCLEBIN|DBA_RECYCLEBIN|TABLESPACE ts [USER u]}
    int start = block.startTokenIdx;
    int end = block.endTokenIdx;

    // 基础结构同行
    for (int i = start; i <= end; i++) {
        gap(i, i+1).newlineMode(FORBIDDEN).minSpaces(1);
    }

    // TABLESPACE → USER 可选换行
    int tsIdx = findKeyword(block, "TABLESPACE");
    int userIdx = findKeyword(block, "USER");
    if (tsIdx > 0 && userIdx > 0 && userIdx > tsIdx) {
        gap(tsIdx, userIdx).newlineMode(OPTIONAL).breakPenalty(0.5).indentDelta(1);
    }

    // 分号黏附
    gap(end - 1, end).newlineMode(FORBIDDEN).minSpaces(0);
}
```

### 21.8 ANALYZE

#### 21.8.1 语法变体

```sql
-- 1. 表分析（收集统计信息）
ANALYZE TABLE employees COMPUTE STATISTICS;
ANALYZE TABLE employees ESTIMATE STATISTICS SAMPLE 5 PERCENT;

-- 2. 索引分析
ANALYZE INDEX emp_pk VALIDATE STRUCTURE;
ANALYZE INDEX emp_name_idx COMPUTE STATISTICS;

-- 3. 分区/子分区分析
ANALYZE TABLE sales PARTITION (p_2024q1) COMPUTE STATISTICS;
ANALYZE TABLE sales SUBPARTITION (sp_us) COMPUTE STATISTICS;

-- 4. 簇分析
ANALYZE CLUSTER emp_cluster COMPUTE STATISTICS;

-- 5. 验证 + 级联
ANALYZE TABLE employees VALIDATE STRUCTURE CASCADE;
ANALYZE TABLE employees VALIDATE STRUCTURE CASCADE FAST;

-- 6. 列出链式行
ANALYZE TABLE employees LIST CHAINED ROWS INTO chained_rows;

-- 7. 删除统计信息
ANALYZE TABLE employees DELETE STATISTICS;
```

#### 21.8.2 格式化约束

`ddlAnalyzeFormat` 提供两种模式：

| 模式 | 效果 |
|------|------|
| `COMPACT` | 整句保持一行，不换行 |
| `EXPAND` | 主要子句前可选换行（缩进一级） |

| 约束位置 | COMPACT 模式 | EXPAND 模式 |
|----------|-------------|-------------|
| `ANALYZE` → 对象类型 | 同行 | 同行 |
| 对象类型 → 对象名 | 同行 | 同行 |
| `TABLE name` → `PARTITION`/`SUBPARTITION` | 同行 | `OPTIONAL` 换行（缩进+1） |
| 对象/分区 → 操作（COMPUTE/ESTIMATE/VALIDATE/LIST/DELETE） | 同行 | `OPTIONAL` 换行（缩进+1） |
| `VALIDATE` → `STRUCTURE` | 同行 | 同行 |
| `STRUCTURE` → `CASCADE [FAST]` | 同行 | 同行 |
| `LIST CHAINED ROWS` → `INTO` | 同行 | `OPTIONAL` 换行（缩进+1） |
| `COMPUTE STATISTICS` | 同行 | 同行 |
| `ESTIMATE STATISTICS` → `SAMPLE n PERCENT` | 同行 | 同行 |
| 整句 | `FORBIDDEN` 任一子句内不换行 | 仅在主要子句边界可选换行 |

#### 21.8.3 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ddlAnalyzeFormat` | enum | COMPACT | ANALYZE 格式 COMPACT/EXPAND |
| `ddlAnalyzePartitionPerLine` | boolean | false | EXPAND 模式下分区子句独立一行 |

#### 21.8.4 walkAnalyze 约束生成

```java
private void walkAnalyze(PlSqlBlock block) {
    String mode = opts.getDdlAnalyzeFormat();
    List<TokenInfo> tokens = block.tokens;
    int start = block.startTokenIdx;
    int end = block.endTokenIdx;

    if ("COMPACT".equals(mode)) {
        // 整句一行
        for (int i = start; i <= end; i++) {
            gap(i, i+1).newlineMode(FORBIDDEN).minSpaces(1);
        }
    } else {
        // EXPAND：主要子句前可选换行
        int partitionIdx = findAnyKeyword(block, "PARTITION", "SUBPARTITION");
        int operationIdx = findAnyKeyword(block,
            "COMPUTE", "ESTIMATE", "VALIDATE", "LIST", "DELETE");

        // 对象名 → PARTITION/SUBPARTITION
        if (partitionIdx > 0) {
            TokenInfo prev = tokens.get(partitionIdx - 1);
            // 分区前 token 是对象名或 DDL 关键字
            GapConstraint g = gap(partitionIdx - 1, partitionIdx);
            g.newlineMode(opts.isDdlAnalyzePartitionPerLine() ? REQUIRED : OPTIONAL);
            g.indentDelta(1);
            out.add(g);
        }

        // 对象/分区 → 操作
        if (operationIdx > 0) {
            int boundary = partitionIdx > 0 ? partitionIdx : start;
            // 定位到对象名结束/分区名结束后的下一个 token
            int beforeOp = operationIdx - 1;
            GapConstraint g = gap(beforeOp, operationIdx);
            g.newlineMode(OPTIONAL).breakPenalty(0.3).indentDelta(1);
            out.add(g);
        }

        // LIST → INTO
        int listIdx = findKeyword(block, "LIST");
        int intoIdx = findKeyword(block, "INTO");
        if (listIdx > 0 && intoIdx > 0) {
            GapConstraint g = gap(listIdx, intoIdx);
            g.newlineMode(OPTIONAL).breakPenalty(0.4).indentDelta(1);
            out.add(g);
        }
    }

    // 分号黏附
    gap(end - 1, end).newlineMode(FORBIDDEN).minSpaces(0);
}
```

### 21.9 DDL 方言差异

| 构造 | Oracle | MySQL | PostgreSQL |
|------|--------|-------|-----------|
| 标识列 | `GENERATED BY DEFAULT AS IDENTITY` | `AUTO_INCREMENT` | `GENERATED ... AS IDENTITY` |
| 虚拟列 | `GENERATED ALWAYS AS (...) VIRTUAL` | `GENERATED ALWAYS AS (...) VIRTUAL` | `GENERATED ... AS ... STORED` |
| 分区语法 | `PARTITION BY RANGE/LIST/HASH/REFERENCE` | `PARTITION BY RANGE/LIST/HASH` | `PARTITION BY RANGE/LIST` |
| TABLESPACE | `TABLESPACE name` | 不支持 | `TABLESPACE name` (PG 9+) |
| 物化视图 | `MATERIALIZED VIEW` | 不支持 | `MATERIALIZED VIEW` |
| 索引类型 | B-tree / BITMAP / FUNCTION | BTREE / HASH / FULLTEXT | BTREE / HASH / GIN / GiST |
| ON CONFLICT | 不支持 | `ON DUPLICATE KEY UPDATE` | `ON CONFLICT ... DO UPDATE` |

---

## 22. 块类型系统

### 22.1 PlSqlBlockType 定义对照

```java
public enum PlSqlBlockType {
    // PL/SQL 块类型
    PACKAGE_SPEC,   // CREATE PACKAGE ... IS/AS
    PACKAGE_BODY,   // CREATE PACKAGE BODY ... IS/AS
    PROCEDURE,      // CREATE [OR REPLACE] PROCEDURE ... IS/AS
    FUNCTION,       // CREATE [OR REPLACE] FUNCTION ... IS/AS
    ANON_BLOCK,     // DECLARE ... BEGIN ... END (匿名块)
    IF,             // IF ... THEN ... ELSIF ... ELSE ... END IF
    CASE_BLOCK,     // CASE ... WHEN ... THEN ... END CASE
    LOOP,           // LOOP ... END LOOP
    FOR_LOOP,       // FOR ... LOOP ... END LOOP
    WHILE_LOOP,     // WHILE ... LOOP ... END LOOP
    REPEAT_LOOP,    // REPEAT ... UNTIL ... END REPEAT (MySQL)
    TRIGGER,        // CREATE TRIGGER ... BEGIN ... END
    TYPE_SPEC,      // CREATE TYPE ... AS OBJECT
    TYPE_BODY,      // CREATE TYPE BODY ...
    NAMED_END,      // END name (辅助类型)

    // DDL 类型
    DDL_CREATE_TABLE, DDL_CREATE_INDEX, DDL_CREATE_VIEW,
    DDL_CREATE_MVIEW, DDL_CREATE_MVIEW_LOG, DDL_CREATE_TABLESPACE,
    DDL_CREATE_USER, DDL_CREATE_ROLE, DDL_CREATE_PROFILE,
    DDL_CREATE_DIRECTORY, DDL_CREATE_SEQUENCE, DDL_CREATE_SYNONYM,
    DDL_CREATE_DATABASE_LINK, DDL_ALTER_TABLE, DDL_ALTER_INDEX,
    DDL_ALTER_TABLESPACE, DDL_ALTER_USER, DDL_ALTER_SEQUENCE,
    DDL_DROP_TABLE, DDL_DROP_INDEX, DDL_DROP_VIEW, DDL_TRUNCATE,
    DDL_RENAME, DDL_COMMENT, DDL_GRANT, DDL_REVOKE,
    DDL_FLASHBACK, DDL_PURGE, DDL_ANALYZE,
}
```

### 22.2 walkBlock 通用流程

```java
private void walkBlock(PlSqlBlock block, int parentIndent) {
    // 类型派发
    if (block.type == PlSqlBlockType.TYPE_SPEC
        || block.type == PlSqlBlockType.TYPE_BODY) {
        walkTypeBlock(block);
        return;
    }
    if (block.type == PlSqlBlockType.TRIGGER) {
        walkTriggerBlock(block);
        return;
    }
    if (block.type == PlSqlBlockType.IF)   { walkIfBlock(block); return; }
    if (block.type == PlSqlBlockType.CASE_BLOCK) { walkCaseBlock(block); return; }
    if (block.type == PlSqlBlockType.LOOP
        || block.type == PlSqlBlockType.FOR_LOOP
        || block.type == PlSqlBlockType.WHILE_LOOP
        || block.type == PlSqlBlockType.REPEAT_LOOP) {
        walkLoopBlock(block);
        return;
    }

    // 通用 walkBlock 逻辑（PACKAGE / FUNCTION / PROCEDURE / ANON_BLOCK）
    // - 处理 DECLARE 段
    // - 处理 BEGIN 段
    // - 处理 EXCEPTION 段
    // - 处理 END
    // - 递归处理子块
}
```

### 22.3 各块类型约束汇总

| 块类型 | 处理方法 | 缩进规则 |
|--------|---------|---------|
| PACKAGE_SPEC | `walkBlock` | IS→+1, END→-1 |
| PACKAGE_BODY | `walkBlock` | IS→+1, BEGIN→+1, EXCEPTION→0, END→-1 |
| FUNCTION | `walkBlock` | IS→+1, BEGIN→+1, EXCEPTION→0, END→-1 |
| PROCEDURE | `walkBlock` | IS→+1, BEGIN→+1, EXCEPTION→0, END→-1 |
| ANON_BLOCK | `walkBlock` | DECLARE\|BEGIN→+1, EXCEPTION→0, END→-1 |
| IF | `walkIfBlock` | THEN→+1, ELSIF→-1+1, ELSE→0, END→-1 |
| CASE_BLOCK | `walkCaseBlock` | WHEN→+1, ELSE→0, END→-1 |
| LOOP/FOR/WHILE | `walkLoopBlock` | LOOP→+1, END→-1 |
| REPEAT_LOOP | `walkLoopBlock` | REPEAT→+1, UNTIL→0, END→-1 |
| TRIGGER | `walkTriggerBlock` | DECLARE→+1, BEGIN→+1, EXCEPTION→0, END→-1 |
| TYPE_SPEC | `walkTypeBlock` | AS→+1, END→-1 |
| TYPE_BODY | `walkTypeBlock` | AS→+1, BEGIN→+1, END→-1 |

---

## 23. 块类型实现详述

### 23.1 LOOP / FOR / WHILE 循环（walkLoopBlock）

```java
private void walkLoopBlock(PlSqlBlock block) {
    int headerEnd = findLoopHeaderEnd(block);    // 定位 LOOP 关键字
    if (headerEnd >= 0) {
        int hn = nextVisible(headerEnd + 1);     // LOOP 后的第一个 token
        if (hn < tokens.size()) {
            requireNewline(headerEnd, hn, 1);    // LOOP 后推一级缩进
        }
    }
    addEndConstraint(block);                     // END LOOP 缩进弹出
}
```

三种 LOOP 子类型共享此实现：
- `LOOP` — 无限循环：`LOOP ... END LOOP;`
- `FOR_LOOP` — FOR 循环：`FOR i IN 1..10 LOOP ... END LOOP;`
- `WHILE_LOOP` — WHILE 循环：`WHILE condition LOOP ... END LOOP;`
- `REPEAT_LOOP` — REPEAT-UNTIL 循环：`REPEAT ... UNTIL ... END REPEAT;`（REPEAT→+1, UNTIL→0）

参数控制：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `loopOnNewLine` | boolean | true | LOOP 前换行 |
| `forLoopFormat` | enum | EXPAND | FOR 循环体格式 |
| `blankLineBeforeBlock` | boolean | false | 块前加空行 |

### 23.2 IF 块参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `thenOnNewLine` | boolean | false | THEN 前换行 |
| `elseOnNewLine` | boolean | true | ELSE/ELSIF 前换行 |

### 23.3 TYPE_SPEC / TYPE_BODY（walkTypeBlock）

#### 23.2.1 声明格式

```sql
CREATE OR REPLACE TYPE t_emp_obj AS OBJECT (
    emp_id    NUMBER,
    emp_name  VARCHAR2(100),
    MEMBER FUNCTION get_full_name RETURN VARCHAR2,
    MEMBER PROCEDURE display_info,
    STATIC FUNCTION create_blank RETURN t_emp_obj,
    CONSTRUCTOR FUNCTION t_emp_obj(self IN OUT NOCOPY t_emp_obj,
                                    emp_id NUMBER) RETURN SELF AS RESULT,
    MAP MEMBER FUNCTION get_id RETURN NUMBER,
    ORDER MEMBER FUNCTION compare(other t_emp_obj) RETURN NUMBER
);
```

```sql
CREATE OR REPLACE TYPE BODY t_emp_obj AS
    MEMBER FUNCTION get_full_name RETURN VARCHAR2 IS
    BEGIN
        RETURN emp_name;
    END get_full_name;

    MEMBER PROCEDURE display_info IS
    BEGIN
        DBMS_OUTPUT.PUT_LINE('ID: ' || emp_id);
    END display_info;

    STATIC FUNCTION create_blank RETURN t_emp_obj IS
    BEGIN
        RETURN t_emp_obj(NULL, NULL);
    END create_blank;
END;
```

#### 23.2.2 walkTypeBlock 实现

```java
private void walkTypeBlock(PlSqlBlock block) {
    // AS OBJECT / AS 后缩进
    int asIdx = findTypeAsKeyword(block);
    if (asIdx >= 0) {
        int next = nextVisible(asIdx + 1);
        if (next >= 0) {
            requireNewline(asIdx, next, 1);
        }
    }

    // TYPE BODY 的 BEGIN 段
    if (block.type == PlSqlBlockType.TYPE_BODY) {
        int beginIdx = block.bodyStartIdx;
        if (beginIdx >= 0) {
            int next = nextVisible(beginIdx + 1);
            if (next >= 0) {
                requireNewline(beginIdx, next, 1);
            }
        }
    }

    // 成员声明（字段对齐）
    if (opts.isTypeMemberAlign()) {
        for (Declaration decl : block.declarations) {
            if (decl.isField()) {
                GapConstraint g = gap(decl.nameEnd, decl.typeStart);
                g.alignGroupId("TYPE_FIELD");
                out.add(g);
            }
        }
    }

    // END
    addEndConstraint(block);
}
```

#### 23.2.3 成员对齐

```sql
-- 字段对齐
CREATE TYPE t_emp_rec AS OBJECT (
    emp_id      NUMBER,
    emp_name    VARCHAR2(100),
    hire_date   DATE,
    salary      NUMBER(8,2)
);

-- MEMBER/STATIC 函数声明
MEMBER FUNCTION get_full_name  RETURN VARCHAR2,
STATIC FUNCTION create_blank    RETURN t_emp_obj,
```

| 对齐组 | 对齐元素 |
|--------|---------|
| `TYPE_FIELD` | 字段名（左对齐） |
| `TYPE_MEMBER_FUNC` | MEMBER/STATIC 函数名 |
| `TYPE_MEMBER_RETURN` | RETURN 类型 |

#### 23.2.4 MAP/ORDER 成员函数

```sql
MAP MEMBER FUNCTION get_id RETURN NUMBER;
ORDER MEMBER FUNCTION compare(other t_emp_obj) RETURN NUMBER;
```

MAP 和 ORDER 成员函数格式与普通 MEMBER 函数一致，函数名前标注 `MAP`/`ORDER`。

#### 23.2.5 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `typeMemberAlign` | boolean | true | TYPE 成员对齐 |

### 23.3 TRIGGER（walkTriggerBlock）

```sql
CREATE OR REPLACE TRIGGER trg_audit
    BEFORE INSERT OR UPDATE ON employees
    FOR EACH ROW
DECLARE
    v_user VARCHAR2(30);
BEGIN
    v_user := USER;
    :NEW.audit_date := SYSDATE;
    :NEW.audit_user := v_user;
END;
```

**walkTriggerBlock 实现**：

```java
private void walkTriggerBlock(PlSqlBlock block) {
    // 1. 触发器头部（从 CREATE TRIGGER name 到 DECLARE/BEGIN）
    int headerEnd = findTriggerHeaderEnd(block);
    if (headerEnd < 0) { addEndConstraint(block); return; }

    String triggerFormat = opts.getTriggerFormat();
    boolean expand = "EXPAND".equals(triggerFormat);

    int tok = block.startTokenIdx;
    while (tok < headerEnd) {
        int next = nextVisible(tok + 1);
        if (next >= headerEnd) break;

        if (isTriggerClauseStart(tokens.get(next))) {
            GapConstraint g = gap(tok, next);
            if (expand) {
                g.forceNewline(true).indentDelta(1);
            } else {
                g.newlineMode(OPTIONAL).breakPenalty(0.8);
            }
            out.add(g);
        }
        tok = next;
    }

    // 2. DECLARE / BEGIN / EXCEPTION / END
    int declIdx = block.declStartIdx;
    if (declIdx >= 0) {
        int next = nextVisible(declIdx + 1);
        if (next >= 0 && next < block.bodyStartIdx) {
            requireNewline(declIdx, next, 1);
        }
    }

    int beginIdx = block.bodyStartIdx;
    if (beginIdx >= 0) {
        int next = nextVisible(beginIdx + 1);
        if (next >= 0) {
            requireNewline(beginIdx, next, 1);
        }
    }

    int exceptIdx = block.exceptionStartIdx;
    if (exceptIdx >= 0) {
        int next = nextVisible(exceptIdx + 1);
        if (next >= 0) {
            GapConstraint g = gap(exceptIdx, next);
            g.forceNewline(true).indentDelta(0);
            out.add(g);
        }
    }

    addEndConstraint(block);
}

private boolean isTriggerClauseStart(TokenInfo t) {
    switch (t.upper) {
        case "BEFORE": case "AFTER": case "INSTEAD":
        case "ON": case "FOR": case "WHEN":
        case "FOLLOWS": case "PRECEDES":
        case "REFERENCING": case "ENABLE": case "DISABLE":
            return true;
        default: return false;
    }
}

private int findTriggerHeaderEnd(PlSqlBlock block) {
    if (block.declStartIdx >= 0) return block.declStartIdx - 1;
    if (block.bodyStartIdx >= 0) return block.bodyStartIdx - 1;
    return block.endTokenIdx - 1;
}
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `triggerFormat` | enum | EXPAND | 头部子句格式 |
| `triggerHeaderClauseOrder` | boolean | true | 强制标准顺序 |

### 23.4 REPEAT_LOOP

REPEAT-UNTIL 循环（MySQL 特有）复用 `walkLoopBlock` 通用流程，但 END 关键字为 `END REPEAT`：

```sql
REPEAT
    SET v_name = CONCAT(v_name, '!');
    SET v_count = v_count + 1;
UNTIL v_count > 10
END REPEAT;
```

| 位置 | 约束 |
|------|------|
| `REPEAT` → 第一语句 | `requireNewline(repeatIdx, next, 1)`（同 LOOP 后缩进） |
| `UNTIL` 前 | `forceNewline(true).indentDelta(0)`（与 REPEAT 同级别） |
| `END REPEAT` | 弹出缩进（`addEndConstraint`） |

### 23.5 LABEL 标签

#### 23.5.1 标签语法

```sql
<<process_loop>>
FOR i IN 1..10 LOOP
    <<inner_loop>>
    FOR j IN 1..10 LOOP
        ...
    END LOOP inner_loop;
END LOOP process_loop;
```

#### 23.5.2 格式化规则

| 约束位置 | 规则 |
|----------|------|
| `<<label>>` → 块头 | 标签独立一行，前换行，缩进与块头同级别 |
| `<<label>>` 内部 | 标签名前后 0 空格 |


```java
private void walkLabel(PlSqlBlock block) {
    // 标签 <<label>> 独立一行
    int labelIdx = block.labelStartIdx;
    if (labelIdx >= 0) {
        int blockStart = block.startTokenIdx;

        // 标签前换行
        GapConstraint g1 = gap(labelIdx - 1, labelIdx);
        g1.forceNewline(true).indentDelta(0);
        out.add(g1);

        // 标签后换行（与块头之间）
        GapConstraint g2 = gap(labelIdx + 1, blockStart);
        g2.forceNewline(true).indentDelta(0);
        out.add(g2);
    }
}
```

#### 23.5.3 GOTO 与标签

```sql
GOTO process_end;
...
<<process_end>>
v_status := 'done';
```

GOTO 语句保持一行，目标标签定位到对应块。

---

## 24. 特殊 PL/SQL 语句

### 24.1 COLLECTION 方法调用

```sql
-- 集合方法（紧凑）
my_array.EXTEND;
my_array.DELETE(5);
my_array.TRIM;
my_array.EXISTS(3);
my_array.FIRST;
my_array.LAST;
v_count := my_array.COUNT;
v_prior := my_array.PRIOR(5);
```

集合方法名与集合变量间 `.` 前后 0 空格。方法参数括号格式由括号间距参数控制。

### 24.2 CURSOR 声明

#### 24.2.1 语法示例

```sql
CURSOR c_emp IS
    SELECT employee_id, first_name, last_name
    FROM employees
    WHERE department_id = 10;

CURSOR c_emp(p_dept_id NUMBER) IS
    SELECT employee_id, first_name
    FROM employees
    WHERE department_id = p_dept_id;
```

#### 24.2.2 格式化约束

| 位置 | 约束 |
|------|------|
| `CURSOR name` → `IS` | `minSpaces=1`，保持同行 |
| `IS` → `SELECT` | `forceNewline(true).indentDelta(1)` |
| 参数列表 `(p_dept_id NUMBER)` | 同参数声明格式化规则（§25） |
| SELECT 内部 | `cursorSelectFormat=true` 时复用 §9 DQL 规则 |

#### 24.2.3 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `cursorSelectFormat` | boolean | true | CURSOR 体内 SELECT 格式化 |

### 24.3 FORALL 语句

#### 24.3.1 语法示例

```sql
FORALL i IN 1..v_emp_ids.COUNT
    INSERT INTO employees_temp
    VALUES v_emp_ids(i);

FORALL i IN INDICES OF v_emp_ids BETWEEN 1 AND 10
    UPDATE employees_temp SET salary = 5000
    WHERE employee_id = v_emp_ids(i);

FORALL i IN v_emp_ids.FIRST..v_emp_ids.LAST SAVE EXCEPTIONS
    DELETE FROM employees_temp
    WHERE employee_id = v_emp_ids(i);
```

#### 24.3.2 格式化约束

| 位置 | 约束 |
|------|------|
| `FORALL` 头 | 保持一行 |
| `IN` 范围 | 与 FORALL 同行 |
| `INDICES OF` | 与 FORALL 同行 |
| `SAVE EXCEPTIONS` | 与 FORALL 同行 |
| FORALL → DML 语句 | `forceNewline(true).indentDelta(1)` |

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `forallFormat` | boolean | true | FORALL 格式化 |

### 24.4 CASE 表达式

#### 24.4.1 问题

CASE 表达式（非 CASE 语句）出现在 SQL 中：

```sql
SELECT employee_id,
       CASE
           WHEN salary > 10000 THEN 'High'
           WHEN salary > 5000  THEN 'Medium'
           ELSE 'Low'
       END AS salary_grade
FROM employees;
```

#### 24.4.2 与 CASE 语句的区别

| | CASE 语句 | CASE 表达式 |
|--|----------|-----------|
| 语法 | `CASE ... WHEN ... THEN ... END CASE;` | `CASE WHEN ... THEN ... END` |
| 块类型 | `CASE_BLOCK` | 嵌套在 SELECT 中的表达式 |
| 格式化 | walkCaseBlock | §9.6 子查询规则 |

#### 24.4.3 约束策略

`caseExprFormat` (COMPACT/EXPAND) 控制 CASE 表达式格式：

```java
switch (opts.getCaseExprFormat()) {
    case COMPACT:
        gap(WHEN, expr).newlineMode(FORBIDDEN);
        gap(WHEN_i, WHEN_i+1).newlineMode(OPTIONAL).breakPenalty(0.9);
        break;
    case EXPAND:
        gap(prevWHEN, nextWHEN).forceNewline(true);
        gap(ELSE, expr).forceNewline(true);
        break;
}
```

### 24.5 SUBTYPE 声明

```sql
SUBTYPE salary_t IS NUMBER(8,2);
SUBTYPE name_t IS VARCHAR2(100) NOT NULL;
```

| 位置 | 约束 |
|------|------|
| `SUBTYPE name` → `IS` | `minSpaces=1` |
| `IS` → 基类型 | `minSpaces=1` |
| `NOT NULL` | 基类型后 1 空格 |

```java
private void walkSubtypeBlock(PlSqlBlock block) {
    int isIdx = findKeyword(block, "IS");
    if (isIdx >= 0) {
        int baseType = nextVisible(isIdx + 1);
        gap(isIdx, baseType).minSpaces(1).maxSpaces(1);
    }
    // SUBTYPE 保持一行
}
```

### 24.6 PRAGMA 细化

```sql
PRAGMA AUTONOMOUS_TRANSACTION;
PRAGMA EXCEPTION_INIT(exception_name, -20001);
PRAGMA SERIALLY_REUSABLE;
PRAGMA INLINE(func_name, 'YES');
PRAGMA RESTRICT_REFERENCES(default, WNDS, RNDS);
PRAGMA DEPRECATE;
PRAGMA DEPRECATE(procedure_name, 'Use new_proc instead');
PRAGMA UDF;
```

#### 24.6.1 约束规则

| PRAGMA 类型 | 括号内格式 | 断行规则 |
|-------------|-----------|---------|
| 无参（AUTONOMOUS_TRANSACTION / SERIALLY_REUSABLE / UDF / DEPRECATE） | 无括号 | 一行 |
| `EXCEPTION_INIT(name, code)` | 第一参数：异常名；第二参数：错误码（数字或 `-`+数字） | `(` 后 0 空格，`,` 后 1 空格，`)` 前 0 空格。参数间 `FORBIDDEN` 换行 |
| `INLINE(name, 'YES'\|'NO')` | 第一参数：函数名；第二参数：字面量 `'YES'`/`'NO'` | 同 EXCEPTION_INIT，字符串字面量原样保留 |
| `DEPRECATE(name, 'msg')` | 第一参数：对象名；第二参数：说明字符串（可选） | 同 EXCEPTION_INIT |
| `RESTRICT_REFERENCES(default, [...] )` | 变长参数，第一个为对象名，后续为权限约束（WNDS/RNDS/RNPS/WNPS/TRUST） | 参数间 `FORBIDDEN` 换行；逗号后 1 空格 |
| `CONTRACT(name, ...)` | 同 RESTRICT_REFERENCES | 同 RESTRICT_REFERENCES |

#### 24.6.2 括号内约束

| 约束位置 | 约束 |
|----------|------|
| `PRAGMA` → 指令名 | `minSpaces=1`，同行 |
| 指令名（无参） → `;` | 同行，`FORBIDDEN` 换行 |
| 指令名 → `(`（有参） | `minSpaces=0`（`EXCEPTION_INIT(` 黏附） |
| `(` 后 → 第一参数 | `minSpaces=0`，`FORBIDDEN` 换行 |
| 参数 `,` → 下一参数 | `newlineMode=FORBIDDEN`，逗号后 `preferredSpaces=1` |
| 最后一参数 → `)` | `minSpaces=0`，`FORBIDDEN` 换行 |
| `)` → `;` | `minSpaces=0`（黏附） |

```java
private void walkPragmaBlock(PlSqlBlock block) {
    int pragmaIdx = findKeyword(block, "PRAGMA");
    int semiIdx = block.endTokenIdx;

    // PRAGMA → 指令名：同行
    int stmtIdx = pragmaIdx + 1;

    // 检查是否有括号参数
    int lparen = findNextChar(block, '(', stmtIdx);
    int rparen = findMatchParen(block, lparen);

    if (lparen > 0 && rparen > 0) {
        // 有参 PRAGMA
        // 指令名 → (
        gap(stmtIdx, lparen).minSpaces(0).maxSpaces(0);

        // ( 后
        int firstArg = nextVisible(lparen + 1);
        if (firstArg >= 0 && firstArg < rparen) {
            gap(lparen, firstArg).minSpaces(0);
        }

        // 参数间：逗号后 1 空格，禁止换行
        for (int i = lparen + 1; i < rparen; i++) {
            TokenInfo t = tokens.get(i);
            if (t.text.equals(",")) {
                int nextTok = nextVisible(i + 1);
                if (nextTok >= 0 && nextTok < rparen) {
                    gap(i, nextTok).newlineMode(FORBIDDEN).preferredSpaces(1);
                }
            }
        }

        // ) 前
        int lastParam = prevVisible(rparen - 1);
        if (lastParam > lparen) {
            gap(lastParam, rparen).minSpaces(0);
        }
    } else {
        // 无参 PRAGMA：指令名 → ; 整段 FORBIDDEN
        gap(stmtIdx, semiIdx).newlineMode(FORBIDDEN);
    }

    // PRAGMA 前换行
    gap(block.prevTokenIdx, pragmaIdx).forceNewline(true);
    // 分号黏附
    gap(semiIdx - 1, semiIdx).newlineMode(FORBIDDEN).minSpaces(0);
}
```

### 24.7 集合运算符

```sql
-- MULTISET 集合运算
SELECT column1 MULTISET EXCEPT DISTINCT column2 FROM t;
SELECT column1 MULTISET INTERSECT column2 FROM t;
SELECT column1 MULTISET UNION column2 FROM t;

-- SET 操作
column1 MULTISET EXCEPT DISTINCT column2
```

集合运算符 `MULTISET EXCEPT`、`MULTISET INTERSECT`、`MULTISET UNION` 保持一行，前后空格 1。

---

## 25. 参数声明格式化

### 25.1 适用场景与 DataGrip 风格

函数和过程的参数声明需要细致的格式化控制：

```sql
-- 未格式化
CREATE OR REPLACE PROCEDURE update_employee(p_emp_id IN NUMBER, p_first_name IN VARCHAR2 DEFAULT NULL, p_last_name IN VARCHAR2, p_salary IN NUMBER DEFAULT 0, p_commit IN BOOLEAN DEFAULT TRUE) IS

-- DataGrip 风格：参数每行一个
CREATE OR REPLACE PROCEDURE update_employee(
    p_emp_id     IN NUMBER,
    p_first_name IN VARCHAR2 DEFAULT NULL,
    p_last_name  IN VARCHAR2,
    p_salary     IN NUMBER   DEFAULT 0,
    p_commit     IN BOOLEAN  DEFAULT TRUE
) IS

-- 紧凑风格
CREATE OR REPLACE PROCEDURE update_employee(
    p_emp_id IN NUMBER,
    p_first_name IN VARCHAR2 DEFAULT NULL,
    p_last_name IN VARCHAR2,
    p_salary IN NUMBER DEFAULT 0,
    p_commit IN BOOLEAN DEFAULT TRUE
) IS
```

### 25.2 格式化约束

| 约束位置 | 约束 |
|----------|------|
| `(` → 第一参数 | `requireNewline(true).indentDelta(1)` |
| 参数 `,` → 下一参数 | `forceNewline(true).indentDelta(1)` |
| 最后一参数 → `)` | `requireNewline(lastParam, closeParen, -1)` |
| `OUT` / `IN` / `IN OUT` | 参数名后 1 空格，方向关键字前 1 空格 |

### 25.3 对齐模式

#### 25.3.1 完全对齐（ALIGNED）

`parameterAlignMode=ALIGNED` 时，三列对齐：

```sql
CREATE OR REPLACE PROCEDURE update_employee(
    p_emp_id     IN  NUMBER            DEFAULT NULL,
    p_first_name IN  VARCHAR2(20)      DEFAULT NULL,
    p_last_name  IN  VARCHAR2(25),
    p_salary     IN  NUMBER(8,2)       DEFAULT 0,
    p_commit     IN  BOOLEAN           DEFAULT TRUE
)
```

- 参数名列：`alignGroupId="PARAM_NAME"`，右对齐（`parameterNameRightAlign=true`）
- 方向列：`alignGroupId="PARAM_DIR"`
- 类型列：`alignGroupId="PARAM_TYPE"`

#### 25.3.2 紧凑对齐（COMPACT）

```sql
CREATE OR REPLACE PROCEDURE update_employee(
    p_emp_id IN NUMBER DEFAULT NULL,
    p_first_name IN VARCHAR2(20) DEFAULT NULL,
    p_last_name IN VARCHAR2(25),
    p_salary IN NUMBER(8,2) DEFAULT 0,
    p_commit IN BOOLEAN DEFAULT TRUE
)
```

### 25.4 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `parameterPerLine` | boolean | true | 参数每行一个 |
| `parameterAlignMode` | enum | ALIGNED | 对齐模式 |
| `parameterNameRightAlign` | boolean | false | 参数名右对齐 |
| `parameterDirectionCase` | enum | UPPER | IN/OUT/IN OUT 大小写 |
| `parameterTypeCase` | enum | PRESERVE | 参数类型关键字大小写 |

---

## 26. 其余语句补充

### 26.1 SAVEPOINT / COMMIT / ROLLBACK

```sql
SAVEPOINT sp_before_update;
COMMIT;
COMMIT WORK;
COMMIT COMMENT 'update completed';
ROLLBACK;
ROLLBACK TO sp_before_update;
ROLLBACK WORK;
```

| 语句 | 格式化规则 |
|------|-----------|
| `SAVEPOINT name;` | 一行 |
| `COMMIT [WORK] [COMMENT 'text'];` | 一行 |
| `ROLLBACK [WORK] [TO savepoint];` | 一行 |

### 26.2 LOG ERRORS

```sql
INSERT INTO employees (employee_id, first_name)
VALUES (100, 'John')
LOG ERRORS INTO err_employees ('INSERT') REJECT LIMIT UNLIMITED;
```

`LOG ERRORS INTO` 子句保持与 DML 同行或 `OPTIONAL` 换行，缩进一级。

### 26.3 ACCESSIBLE BY / DEFAULT COLLATION

```sql
-- Oracle 12c+ 单元可见性
CREATE OR REPLACE PACKAGE pkg_internal IS
    PROCEDURE internal_proc;
END;
/
CREATE OR REPLACE PACKAGE pkg_api IS
    PROCEDURE public_proc;
END;
/

-- DEFAULT COLLATION
CREATE OR REPLACE PACKAGE pkg DEFAULT COLLATION USING_NLS_COMP IS
    ...
END;
```

`ACCESSIBLE BY` 和 `DEFAULT COLLATION` 在 PACKAGE/FUNCTION 头部的关键字后，保持一行或换行（`OPTIONAL`）。

### 26.4 XML / JSON 函数

```sql
-- XML
SELECT XMLELEMENT("emp", XMLATTRIBUTES(e.employee_id AS "id"))
FROM employees e;

SELECT XMLAGG(XMLELEMENT("emp", e.first_name))
FROM employees e;

-- JSON
SELECT JSON_OBJECT('id' VALUE e.employee_id, 'name' VALUE e.first_name)
FROM employees e;

SELECT JSON_ARRAYAGG(e.first_name ORDER BY e.employee_id)
FROM employees e;
```

XML/JSON 函数参数列表内部紧凑，`breakPenalty=0.8`。

### 26.5 DBMS_SQL

#### 26.5.1 问题

DBMS_SQL 是 Oracle 动态 SQL 的核心包。格式化需识别常见调用模式，
确保 `DBMS_SQL.xxx` 前缀不被折行破坏。

#### 26.5.2 常见模式

```sql
-- 1. 基础生命周期
DECLARE
    c       INTEGER;
    ignore  INTEGER;
    v_col1  VARCHAR2(100);
BEGIN
    -- 打开游标
    c := DBMS_SQL.OPEN_CURSOR;

    -- 解析 SQL
    DBMS_SQL.PARSE(c, 'SELECT * FROM employees WHERE id = :id', DBMS_SQL.NATIVE);

    -- 绑定变量
    DBMS_SQL.BIND_VARIABLE(c, ':id', 100);
    DBMS_SQL.BIND_VARIABLE(c, ':name', 'John');
    DBMS_SQL.BIND_VARIABLE(c, ':dept_id', 50);

    -- 定义列
    DBMS_SQL.DEFINE_COLUMN(c, 1, v_col1, 100);
    DBMS_SQL.DEFINE_COLUMN(c, 2, SYSDATE);
    DBMS_SQL.DEFINE_COLUMN(c, 3, v_salary);

    -- 执行
    ignore := DBMS_SQL.EXECUTE(c);

    -- 取数据
    WHILE DBMS_SQL.FETCH_ROWS(c) > 0 LOOP
        DBMS_SQL.COLUMN_VALUE(c, 1, v_col1);
        DBMS_SQL.COLUMN_VALUE(c, 2, v_date);
        DBMS_SQL.COLUMN_VALUE(c, 3, v_salary);
    END LOOP;

    -- 关闭游标
    DBMS_SQL.CLOSE_CURSOR(c);
END;

-- 2. EXECUTE_AND_FETCH 模式
DECLARE
    c INTEGER;
    v_count INTEGER;
BEGIN
    c := DBMS_SQL.OPEN_CURSOR;
    DBMS_SQL.PARSE(c, 'SELECT COUNT(*) FROM employees', DBMS_SQL.NATIVE);
    DBMS_SQL.DEFINE_COLUMN(c, 1, v_count);
    v_result := DBMS_SQL.EXECUTE_AND_FETCH(c);  -- 一次完成
    DBMS_SQL.COLUMN_VALUE(c, 1, v_count);
    DBMS_SQL.CLOSE_CURSOR(c);
END;

-- 3. 数值游标 ID 变量命名
c := DBMS_SQL.OPEN_CURSOR;          -- 变量 c 存储游标 ID
v_cursor := DBMS_SQL.OPEN_CURSOR;    -- 常见命名 v_cursor
h := DBMS_SQL.TO_CURSOR_NUMBER(r);   -- REF CURSOR 转换
```

#### 26.5.3 可识别调用模式

| DBMS_SQL 函数 | 角色 | 格式化规则 |
|--------------|------|-----------|
| `OPEN_CURSOR` | 打开游标 | 赋值语句，保持一行 |
| `TO_CURSOR_NUMBER(r)` | REF CURSOR 转换 | 参数紧凑 |
| `PARSE(c, sql, flags)` | 解析 SQL | `sql` 参数是字符串字面量（L1 保护） |
| `BIND_VARIABLE(c, name, val)` | 绑定变量 | 每参数调用一行，`:name` 保持原样 |
| `BIND_VARIABLE(c, name, val, len)` | 绑定变量（定长） | 同 BIND_VARIABLE |
| `DEFINE_COLUMN(c, pos, var)` | 定义列 | 每列一行，参数紧凑 |
| `DEFINE_COLUMN(c, pos, var, len)` | 定义列（定长） | 同 DEFINE_COLUMN |
| `EXECUTE(c)` | 执行 | 独立一行 |
| `EXECUTE_AND_FETCH(c)` | 执行并取数 | 独立一行 |
| `FETCH_ROWS(c)` | 取行 | 在 WHILE 条件中，一行 |
| `COLUMN_VALUE(c, pos, var)` | 取值 | 每列一行，参数紧凑 |
| `CLOSE_CURSOR(c)` | 关闭游标 | 独立一行 |
| `IS_OPEN(c)` | 检查状态 | 条件判断中，一行 |

#### 26.5.4 DBMS_SQL 标记方法

```java
private boolean isDbmsSqlCall(TokenInfo token) {
    // 匹配 DBMS_SQL.xxx 调用模式
    return token.upper.equals("DBMS_SQL")
        && tokens.get(token.index + 1).text.equals(".");
}
```

#### 26.5.5 约束规则

| 约束位置 | 规则 |
|----------|------|
| `DBMS_SQL` → `.` | `FORBIDDEN` 换行，`minSpaces=0, maxSpaces=0` |
| `.` → 函数名 | `FORBIDDEN` 换行，`minSpaces=0` |
| 函数名 → `(` | 同行 |
| `(` → 参数 | `minSpaces=0`（紧凑） |
| 参数 `,` → 下一参数 | `FORBIDDEN`（保持同行） |
| 每个 DBMS_SQL 调用 | 独立一行 |
| 字符串参数 `'...'` | 原样透传（L1 保护） |

#### 26.5.6 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `dbmsSqlFormat` | boolean | true | DBMS_SQL 调用格式化 |
| `dbmsSqlBindPerLine` | boolean | true | BIND_VARIABLE 每绑定一行 |
| `dbmsSqlColumnPerLine` | boolean | true | DEFINE_COLUMN/COLUMN_VALUE 每列一行 |

#### 26.5.7 walkDbmsSqlStatement 约束生成

```java
private void walkDbmsSqlStatement(PlSqlBlock block) {
    List<Statement> stmts = block.statements;
    for (Statement stmt : stmts) {
        if (!isDbmsSqlCall(stmt.getFirstToken())) continue;

        List<TokenInfo> tokens = stmt.getTokens();
        int dbmsIdx = stmt.getFirstTokenIndex();

        // DBMS_SQL. 前缀：禁止折行和空格
        int dotIdx = dbmsIdx + 1;  // .
        int funcIdx = dbmsIdx + 2; // 函数名
        gap(dbmsIdx, dotIdx).newlineMode(FORBIDDEN).minSpaces(0).maxSpaces(0);
        gap(dotIdx, funcIdx).newlineMode(FORBIDDEN).minSpaces(0).maxSpaces(0);

        // 函数名 → 左括号
        int lparen = findNext(lparen, "(", funcIdx);
        if (lparen > 0) {
            gap(funcIdx, lparen).newlineMode(FORBIDDEN);
            // 参数列表紧凑
            int rparen = matchParen(lparen);
            for (int i = lparen + 1; i < rparen; i++) {
                if (tokens.get(i).text.equals(",")) {
                    gap(i, i+1).newlineMode(FORBIDDEN);
                }
            }
        }

        // 分号黏附
        int semiIdx = stmt.getEndTokenIndex();
        gap(semiIdx - 1, semiIdx).newlineMode(FORBIDDEN).minSpaces(0);

        // 语句前换行
        gap(stmt.getPrevTokenIndex(), stmt.getFirstTokenIndex())
            .forceNewline(true).indentDelta(0);
    }
}
```

### 26.6 排序规则（NLS / COLLATE）

```sql
-- PL/SQL 排序规则声明
ORDER BY last_name COLLATE BINARY_CI;

CREATE TABLE t (
    name VARCHAR2(100) COLLATE BINARY_CI
);
```

`COLLATE` 关键字与表达式同行，不换行。

### 26.7 EXECUTE IMMEDIATE 中的 DDL

#### 26.7.1 常见模式

PL/SQL 块内通过 `EXECUTE IMMEDIATE` 执行动态 DDL：

```sql
PROCEDURE p IS
    v_sql VARCHAR2(4000);
    v_table_name VARCHAR2(30) := 'employees';
BEGIN
    -- DDL 字符串拼接
    v_sql := 'CREATE TABLE temp_' || v_table_name || ' (
        id        NUMBER(6),
        name      VARCHAR2(100),
        created   DATE
    )';
    EXECUTE IMMEDIATE v_sql;

    -- 行内 DDL
    EXECUTE IMMEDIATE 'DROP TABLE temp_' || v_table_name;

    -- DDL 参数化
    EXECUTE IMMEDIATE 'ALTER TABLE ' || v_table_name
        || ' ADD (new_col NUMBER DEFAULT 0)';
END;
```

#### 26.7.2 DBMS_UTILITY.EXEC_DDL_STATEMENT

```sql
DBMS_UTILITY.EXEC_DDL_STATEMENT('CREATE INDEX idx_temp ON temp_tab(col)');
DBMS_UTILITY.EXEC_DDL_STATEMENT('DROP TABLE ' || v_table_name);
```

同上策略——字符串内 DDL 不处理。

#### 26.7.3 约束策略

| 场景 | 处理策略 | 原因 |
|------|---------|------|
| `EXECUTE IMMEDIATE` + 变量（`v_sql`） | 变量引用原样保留 | 运行时的 SQL 内容格式化引擎无法获知 |
| `EXECUTE IMMEDIATE` + 字符串字面量 | 字符串由 L1 保护规则透传，内部不解析 | 字符串内 SQL 属于运行时构造 |
| `EXECUTE IMMEDIATE` + 字符串拼接 | 拼接链走 §5.1 保护策略，每段分别透传 | 分段内容可能来自不同来源 |
| `DBMS_UTILITY.EXEC_DDL_STATEMENT('...')` | 同 EXECUTE IMMEDIATE 策略 | 同上 |
| `v_sql := 'CREATE TABLE ...'` | 赋值语句中字符串原样保留 | 拼接链保护（§5.1 L1-L2） |

**边界声明**：字符串内 SQL 格式化属于 IDE 级别的"字符串内 SQL 检测/注入"功能范畴，
不在本格式化引擎范围内。格式化引擎仅确保字符串字面量原样透传，不修改其内部格式。

#### 26.7.4 外部引用

如需对字符串内 DDL 进行格式化（如 `v_sql := 'CREATE TABLE ...'` 的内部），
需依赖 IDE 或第三方工具的 SQL 字符串检测能力，提取字符串内容后独立调用 `PlSqlFormatter.format()`。

---

## 27. FormatOptions 参数对接总览

### 27.1 参数到约束的完整映射

本表列出每个 FormatOptions 参数对应的 GapConstraint API 调用及代码位置。

| 参数 | 类型 | 默认值 | `GapConstraint` API | 代码类 |
|------|------|--------|-------------------|--------|
| `keywordCase` | enum | UPPER | -（PostProcessor） | `PostProcessor.java` |
| `maxLineWidth` | int | 120 | -（ConstraintSolver） | `ConstraintSolver.java` |
| `indent` | int | 4 | `indentDelta(indent/4)` | `BlockConstraintGen.java` |
| `indentCount` | int | 4 | `indentDelta(indentCount/4)` | `BlockConstraintGen.java` |
| `lineEnding` | enum | LF | -（PostProcessor） | `PostProcessor.java` |
| `blankLineHandling` | enum | PRESERVE | -（PostProcessor） | `PostProcessor.java` |
| `trailingWhitespaceTrim` | boolean | true | -（PostProcessor） | `PostProcessor.java` |
| `commaPosition` | enum | TRAILING | `gap(comma, next).newlineMode(OPTIONAL/FORBIDDEN)` | `CommaConstraintGen.java` |
| `dmlJoinIndent` | boolean | true | `gap(JOIN, ON).indentDelta(1)` | `SelectConstraintGen.java` |
| `dmlJoinOnNewLine` | boolean | false | `gap(prev, JOIN).forceNewline()` | `SelectConstraintGen.java` |
| `joinOnAlign` | boolean | true | `gap(ON, cond).alignGroupId("JOIN_ON")` | `SelectConstraintGen.java` |
| `dmlWhereAndPosition` | enum | INDENTED | `gap(AND, cond).indentDelta(0/1/父块)` | `WhereConstraintGen.java` |
| `whereIndentSize` | int | 1 | `gap(AND/OR, cond).indentDelta(whereIndentSize)` | `WhereConstraintGen.java` |
| `fromClauseNewline` | boolean | true | `gap(prev, FROM).forceNewline(true)` | `SelectConstraintGen.java` |
| `fromClauseIndent` | int | 1 | `gap(FROM, table).indentDelta(fromClauseIndent)` | `SelectConstraintGen.java` |
| `dmlInClauseExpand` | enum | COMPACT | `gap(value, comma).newlineMode()` | `InClauseConstraintGen.java` |
| `dmlInClauseThreshold` | int | 5 | `if(values.size() > threshold) forceNewline()` | `InClauseConstraintGen.java` |
| `dmlInColumnsPerRow` | int | 5 | `gap(value, comma).newlineMode(MIXED)` | `InClauseConstraintGen.java` |
| `dmlSubqueryFormat` | enum | AUTO | `gap(lparen, select).newlineMode()` | `SubqueryConstraintGen.java` |
| `dmlBulkCollectAlign` | boolean | true | `alignGroupId("BULK_COLLECT")` | `DmlConstraintGen.java` |
| `dmlUsingAlign` | boolean | true | `alignGroupId("DML_USING")` | `DmlConstraintGen.java` |
| `selectColumnMode` | enum | ALIGN | `forceNewline/gap/alignGroupId("SEL_COL")` | `SelectConstraintGen.java` |
| `selectColumnsPerRow` | int | 0 | `gap(group, nextGroup).forceNewline()` | `SelectConstraintGen.java` |
| `insertColumnFormat` | enum | COMPACT | `gap(col, comma).newlineMode(OPTIONAL)` | `InsertConstraintGen.java` |
| `insertColumnsPerRow` | int | 0 | `gap(group, nextGroup).forceNewline()` | `InsertConstraintGen.java` |
| `insertValuesPerRow` | int | 0 | `gap(value, comma).newlineMode(OPTIONAL)` | `InsertConstraintGen.java` |
| `updateSetCommaPosition` | enum | TRAILING | `gap(assign, comma).newlineMode()` | `UpdateConstraintGen.java` |
| `mergeIntoNewline` | boolean | true | `gap(prev, MERGE).forceNewline()` | `MergeConstraintGen.java` |
| `mergeUsingNewline` | boolean | true | `gap(prev, USING).forceNewline()` | `MergeConstraintGen.java` |
| `mergeOnNewline` | boolean | true | `gap(prev, ON).forceNewline()` | `MergeConstraintGen.java` |
| `mergeWhenNewline` | boolean | true | `gap(prev, WHEN).forceNewline()` | `MergeConstraintGen.java` |
| `mergeUpdateSetAlign` | boolean | true | `alignGroupId("MERGE_SET")` | `MergeConstraintGen.java` |
| `thenOnNewLine` | boolean | false | `gap(THEN, stmt).forceNewline(true)` | `IfConstraintGen.java` |
| `elseOnNewLine` | boolean | true | `gap(prev, ELSE).forceNewline(true)` | `IfConstraintGen.java` |
| `isEndAlign` | boolean | true | `endAlign` | `BlockConstraintGen.java` |
| `isDeclarationAlign` | boolean | true | `alignGroupId("DECL")` | `BlockConstraintGen.java` |
| `namedParameterAlign` | boolean | true | `alignGroupId("NAMED_PARAM")` | `NamedParamConstraintGen.java` |
| `parameterPerLine` | boolean | true | `gap(comma, nextParam).forceNewline()` | `ParamConstraintGen.java` |
| `parameterAlignMode` | enum | ALIGNED | `alignGroupId("PARAM_NAME/DIR/TYPE")` | `ParamConstraintGen.java` |
| `parameterNameRightAlign` | boolean | false | `alignGroupId("PARAM_NAME").rightAlign()` | `ParamConstraintGen.java` |
| `parameterDirectionCase` | enum | UPPER | `PostProcessor.convertDirection()` | `PostProcessor.java` |
| `parameterTypeCase` | enum | PRESERVE | `PostProcessor.convertTypeKeyword()` | `PostProcessor.java` |
| `loopOnNewLine` | boolean | true | `gap(LOOP, stmt).forceNewline()` | `LoopConstraintGen.java` |
| `forLoopFormat` | enum | EXPAND | `gap(LOOP, body).newlineMode()` | `LoopConstraintGen.java` |
| `blankLineBeforeBlock` | boolean | false | `blankLineBefore(true)` | `BlockConstraintGen.java` |
| `exceptionAlign` | enum | FLAT | `gap(EXCEPTION, WHEN).indentDelta(0/1)` | `BlockConstraintGen.java` |
| `triggerFormat` | enum | EXPAND | `gap(clause, value).forceNewline()` | `TriggerConstraintGen.java` |
| `caseExprFormat` | enum | EXPAND | `gap(WHEN, expr).newlineMode()` | `CaseExprConstraintGen.java` |
| `cursorSelectFormat` | boolean | true | 复用 DQL 约束 | `SelectConstraintGen.java` |
| `typeMemberAlign` | boolean | true | `alignGroupId("TYPE_FIELD")` | `TypeConstraintGen.java` |
| `cteFormat` | enum | ONE_PER_LINE | `gap(CTE, CTE).forceNewline/align` | `CteConstraintGen.java` |
| `cteCommaPosition` | enum | TRAILING | `gap(comma, nextCTE).newlineMode()` | `CteConstraintGen.java` |
| `setOperatorNewline` | boolean | true | `gap(prev, UNION).forceNewline()` | `SetOpConstraintGen.java` |
| `setOperatorColumnAlign` | boolean | true | `alignGroupId("SET_COL")` | `SetOpConstraintGen.java` |
| `commentPreserve` | boolean | true | 注释原样保留 | `CommentConstraintGen.java` |
| `commentIndent` | enum | CODE_LEVEL | `gap(prevCode, comment).indentDelta()` | `CommentConstraintGen.java` |
| `commentSingleSpace` | boolean | true | `gap(--, text).minSpaces(1)` | `CommentConstraintGen.java` |
| `trailingCommentAlign` | enum | ALIGN | `alignGroupId("TRAILING_COMMENT")` | `CommentConstraintGen.java` |
| `blockCommentStyle` | enum | PRESERVE | 块注释格式选择 | `CommentConstraintGen.java` |
| `parenthesisSpacing` | enum | NONE | `gap((, token).preferredSpaces()` | `ParenConstraintGen.java` |
| `ddlColumnAlign` | boolean | true | `alignGroupId("COL_NAME/TYPE/NULL")` | `DdlConstraintGen.java` |
| `columnDefColumnsPerRow` | int | 0 | `gap(col, col).forceNewline()` | `DdlConstraintGen.java` |
| `columnDefTypeCase` | enum | PRESERVE | `PostProcessor.convertType()` | `PostProcessor.java` |
| `constraintFormat` | enum | PER_LINE | `gap(constraint, constraint).newlineMode()` | `DdlConstraintGen.java` |
| `storageClauseFormat` | enum | PER_LINE | `gap(opts, opts).newlineMode()` | `DdlConstraintGen.java` |
| `partitionFormat` | enum | PER_LINE | `gap(part, part).forceNewline()` | `DdlConstraintGen.java` |
| `indexColumnFormat` | enum | COMPACT | `gap(idxcol, comma).newlineMode()` | `DdlConstraintGen.java` |
| `ddlIndexOptionPerLine` | boolean | true | `gap(opt, opt).forceNewline()` | `DdlConstraintGen.java` |
| `ddlConstraintStatePerLine` | boolean | true | `gap(state, state).forceNewline()` | `DdlConstraintGen.java` |
| `ddlAlterColumnPerLine` | boolean | true | `gap(alter, alter).forceNewline()` | `DdlConstraintGen.java` |
| `ddlAlterAddDropPerLine` | boolean | true | `gap(ADD/DROP, next).forceNewline()` | `DdlConstraintGen.java` |
| `ddlTbspOptionPerLine` | boolean | true | `gap(opt, opt).forceNewline()` | `DdlConstraintGen.java` |
| `ddlSeqOptionPerLine` | boolean | true | `gap(opt, opt).forceNewline()` | `DdlConstraintGen.java` |
| `ddlUserOptionPerLine` | boolean | true | `gap(opt, opt).forceNewline()` | `DdlConstraintGen.java` |
| `ddlGrantPrivPerLine` | boolean | false | `gap(priv, priv).forceNewline()` | `DdlConstraintGen.java` |
| `ddlFlashbackOptionPerLine` | boolean | true | `gap(clause, clause).forceNewline()` | `DdlConstraintGen.java` |
| `ddlAnalyzeFormat` | enum | COMPACT | `gap(keyword, opt).newlineMode()` | `DdlConstraintGen.java` |
| `forallFormat` | boolean | true | `gap(FORALL, DML).forceNewline()` | `ForallConstraintGen.java` |
| `dbmsSqlFormat` | boolean | true | 调用独立一行 | `DbmsSqlConstraintGen.java` |
| `dbmsSqlBindPerLine` | boolean | true | BIND 每变量一行 | `DbmsSqlConstraintGen.java` |
| `dbmsSqlColumnPerLine` | boolean | true | DEFINE/COLUMN_VALUE 每列一行 | `DbmsSqlConstraintGen.java` |
| `dialect` | String | ORACLE | 方言标识 | `PlSqlDialectFactory.java` |
| `activeProfile` | String | default | 当前激活预设 | `FormatOptions.java` |
| `profiles` | Map | - | 预设集合（key=预设名, value=FormatOptions） | `FormatOptions.java` |

---

## 28. 括号间距控制

### 28.1 问题

括号间距控制 `(` 和 `)` 前后的空白。不同括号上下文有不同的间距期望：

| 上下文 | 控制点 | 示例 |
|--------|--------|------|
| 函数调用 | `func(...)` vs `func (...)` | 声明/调用括号间距 |
| 表达式分组 | `(a+b)` vs `( a+b )` | 表达式括号间距 |
| 控制结构 | `IF(cond)` vs `IF (cond)` | 控制结构括号间距 |
| 类型声明 | `NUMBER(10)` vs `NUMBER (10)` | 类型括号间距 |
| 参数列表 | `(a, b)` vs `( a, b )` | 参数括号间距 |
| 空括号 | `()` vs `( )` | 空括号间距 |

### 28.2 上下文类型

```java
enum ParenContextType {
    FUNCTION_CALL,  // func(...)
    EXPRESSION,     // (a + b)
    CONTROL_FLOW,   // IF/WHILE/FOR (...)
    TYPE_DECL,      // NUMBER(10)
    PARAM_LIST,     // (a IN NUMBER, b IN VARCHAR2)
    CONSTRUCTOR,    // MyType(...)
    ARRAY_INDEX,    // arr(...)
    EMPTY_PARENS    // ()
}
```

### 28.3 格式化约束

每个上下文类型独立控制 4 个间距：

```sql
-- 函数调用：左括号前1空格，左括号后0空格，右括号前0空格，右括号后1空格
func (arg)

-- 表达式：左括号前后0空格，右括号前后0空格
(a + b)

-- 类型声明：左括号前0空格，括号内0空格
NUMBER(10)

-- 控制结构：左括号前1空格
IF (condition) THEN

-- 空括号：括号内无空格
func()
```

```java
void applyParenSpacing(ParenContextType ctx, int lparen, int rparen) {
    ParenSpacingConfig cfg = opts.getParenSpacing(ctx);

    // 左括号前
    gap(lparen - 1, lparen).preferredSpaces(cfg.beforeOpen);
    // 左括号后
    gap(lparen, lparen + 1).preferredSpaces(cfg.afterOpen);
    // 右括号前
    gap(rparen - 1, rparen).preferredSpaces(cfg.beforeClose);
    // 右括号后
    gap(rparen, rparen + 1).preferredSpaces(cfg.afterClose);
}
```

### 28.4 参数定义

```java
class ParenSpacingConfig {
    int beforeOpen;     // ( 前空格
    int afterOpen;      // ( 后空格
    int beforeClose;    // ) 前空格
    int afterClose;     // ) 后空格
}
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `parenFuncCallBeforeOpen` | int | 0 | 函数调用 ( 前 |
| `parenFuncCallInside` | int | 0 | 函数调用括号内 |
| `parenExprBeforeOpen` | int | 1 | 表达式 ( 前 |
| `parenExprInside` | int | 0 | 表达式括号内 |
| `parenControlFlowBeforeOpen` | int | 1 | 控制结构 ( 前 |
| `parenTypeDeclBeforeOpen` | int | 0 | 类型声明 ( 前 |
| `parenTypeDeclInside` | int | 0 | 类型声明括号内 |
| `parenParamListInside` | int | 0 | 参数列表括号内 |
| `parenEmpty` | int | 0 | 空括号间距 ( ) |

### 28.5 兼容性

旧参数 `parenthesisSpacing`（NONE/INSIDE/BOTH）映射到新参数：

| 旧值 | 映射 |
|------|------|
| `NONE` | 所有 `*BeforeOpen=0, *Inside=0` |
| `INSIDE` | 所有 `*BeforeOpen=0, *Inside=1` |
| `BOTH` | 所有 `*BeforeOpen=1, *Inside=1` |

---

## 29. EXCEPTION 段缩进修复

### 29.1 当前问题与根因

`walkBlock` 中 EXCEPTION 段被施加了 `requireNewline(exceptIdx, next, 1)`，导致 WHEN 缩进比正确级别深一级：

```sql
-- 当前（错误）
EXCEPTION
    WHEN NO_DATA_FOUND THEN  -- 多缩进了一级
        ...

-- 正确
EXCEPTION
WHEN NO_DATA_FOUND THEN  -- 与 EXCEPTION 同级别
    ...
```

### 29.2 修复方案

```java
// 修正后：EXCEPTION → WHEN 的缩进增量为 0（同级）
int exceptIdx = block.exceptionStartIdx;
if (exceptIdx >= 0) {
    int next = nextVisible(exceptIdx + 1);
    if (next >= 0) {
        GapConstraint g = gap(exceptIdx, next);
        g.forceNewline(true);
        g.indentDelta(0);  // 与 EXCEPTION 同级
        out.add(g);
    }
}
```

WHEN 内的处理语句缩进一级（与现有一致）：

```sql
EXCEPTION
WHEN NO_DATA_FOUND THEN
    v_msg := 'Not found';  -- 缩进一级
WHEN OTHERS THEN
    v_msg := SQLERRM;
```

### 29.3 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `exceptionAlign` | enum | FLAT | WHEN 对齐模式：FLAT（与 EXCEPTION 同级）/ INDENTED（缩进一级） |

---

## 30. PostProcessor 修复

### 30.1 问题清单

| 问题 | 阶段 | 原因 |
|------|------|------|
| 关键字大小写不一致 | 输出 | 同一个关键字在方言不同语境下大小写不同 |
| 注释位置偏移 | 保护恢复 | 注释占位符替换后缩进错误 |
| 字符串拼接破坏 | 词法 | `\|\|` 两侧空格被移除 |
| 属性标记大小写 | 输出 | .member 和 %TYPE 不应转换 |

### 30.2 关键字集方案

`PostProcessor` 在构造时获取 `dialect.getKeywords()`，仅对命中关键字集的 token 进行大小写转换：

```java
class PlSqlPostProcessor {
    Set<String> keywords;

    void convertLine(List<TokenInfo> tokens, int start, int end) {
        for (int i = start; i <= end; i++) {
            TokenInfo t = tokens.get(i);
            if (t.isKeyword && keywords.contains(t.upper)) {
                switch (opts.getKeywordCase()) {
                    case UPPER: t.text = t.upper; break;
                    case LOWER: t.text = t.upper.toLowerCase(); break;
                    case CAPITALIZE: t.text = capitalize(t.upper); break;
                }
            }
            // 跳过引用标识符、字符串、注释
        }
    }
}
```

### 30.3 保护恢复流程

```
1. Lexer 阶段：注释、字符串替换为占位符（§5.1 L1-L2）
2. Engine 处理：占位符作为普通 token 参与格式化
3. PostProcessor：占位符替换回原始注释/字符串文本
4. 对齐调整：替换后行尾注释的对齐
```

### 30.4 后处理参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `lineEnding` | enum | LF | 换行符（LF/CRLF） |
| `blankLineHandling` | enum | PRESERVE | 空白行处理：PRESERVE（保留）/ COMPRESS（合并连续空行）/ DELETE（删除） |
| `trailingWhitespaceTrim` | boolean | true | 行尾空格修剪 |

---

## 31. 错误处理

通过 ANTLR 的 `ANTLRErrorListener` 捕获语法错误，输出 `Diagnostic`：

| 级别 | 代码 | 含义 | 处理 |
|------|------|------|------|
| WARNING | SYNTAX_ERROR | 某条语句语法有误 | 该语句跳过格式化，原样输出,提示错误信息 |
| WARNING | UNCLOSED_BLOCK | 块缺少 END | 自动补全缺失的 `END;` |
| WARNING | MISMATCHED_PAREN | 括号不匹配 | 跳过该括号范围 |
| INFO | EMPTY_INPUT | 输入为空 | 返回空字符串 |

---

## 32. 旧方案对照

### 32.1 与 Phase 1-2 的差异

| 维度 | Phase 1-2（旧） | Phase 3（V3） |
|------|----------------|--------------|
| 块结构推导 | Token 流试探式分析 | ParseTree 精确提取 |
| 缩进控制 | 硬编码在方法中 | GapConstraint.indentDelta 声明式 |
| 折行策略 | 贪婪（先放满再断行） | DP 最优折行 |
| 参数映射 | 无，参数仅影响少数硬编码点 | 所有参数映射到约束 |
| DDL 处理 | 外部模板引擎 | 约束引擎 DDL 子模块 |
| 方言处理 | 单一方言 | 插件化 4 方言 |
| DQL/DML | 混为一谈 | 严格分离 |
| 注释处理 | 原始保留或忽略 | 多模式格式化 |
| 实施状态 | 部分实现 | 全面设计 + 全部实现 |

### 32.2 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `builder/PlSqlModelBuilder.java` | 新建 | ParseTree → PlSqlBlock 树 |
| `builder/ParseTreeModelBuilder.java` | 新建 | visitor 实现 |
| `dialect/PlSqlDialect.java` | 重写 | 精简接口（删除 detectBlockStart/End） |
| `dialect/OraclePlSqlDialect.java` | 重写 | 仅保留 keywordCase/stringDelimiter |
| `dialect/MySqlPlSqlDialect.java` | 新建 | MySQL 方言 |
| `dialect/PostgreSqlPlSqlDialect.java` | 新建 | PostgreSQL 方言 |
| `dialect/OceanBaseOraPlSqlDialect.java` | 新建 | OceanBase 方言 |
| `dialect/PlSqlDialectFactory.java` | 保留 | 不变 |
| `formatter/PlSqlFormatterEngine.java` | 重写 | 替换为 LayoutEngine |
| `formatter/layout/GapConstraint.java` | 新建 | 间隙约束模型 |
| `formatter/layout/ConstraintGenerator.java` | 新建 | PlSqlBlock → GapConstraint[] |
| `formatter/layout/ConstraintSolver.java` | 新建 | 约束求解器 |
| `formatter/layout/StringAssembler.java` | 新建 | 约束解 → 文本 |
| `formatter/post/PostProcessor.java` | 重写 | 关键字大小写/注释保护 |
| `model/PlSqlBlock.java` | 修改 | 扩展块属性 |
| `model/PlSqlBlockType.java` | 修改 | 新增 DDL 类型 |
| `model/TokenInfo.java` | 精简 | 删除冗余字段 |
| `qa/PlSqlQualityChecker.java` | 保留 | 不变 |
| `PlSqlFormatter.java` | 保留 | 入口不变 |

---

## 33. 实施路线

### Phase 1-2（已完成）

- FormatOptions 40+ 参数定义
- SqlDialect 接口扩展 + 4 方言关键字集
- SqlFormatter 模板引擎
- SubqueryHandler 子查询检测

### Phase 3 格式调整（当前阶段）

- 约束引擎整体架构实现
- GapConstraint / ConstraintGenerator / ConstraintSolver / StringAssembler
- DQL 格式化（SELECT/JOIN/WHERE/IN/子查询）
- DML 格式化（MERGE/INSERT/UPDATE/DELETE）
- DDL 格式化（CREATE TABLE/INDEX/VIEW/ALTER）
- PL/SQL 块格式化（IF/LOOP/CASE/TRIGGER/TYPE）
- 注释格式化 / 括号间距
- PostProcessor 关键字大小写

### Phase 4 集成 + 测试

- SettingsDialog UI 对接 FormatOptions
- 方言选择持久化
- 测试用例验证
- 性能调优

---

## 34. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| ParseTree 对语法错误的容错性 | 错误语句无法格式化 | ANTLRErrorListener 恢复，未解析语句原样输出 |
| DP 折行性能 | 大文件格式化慢 | 断点数量限制，超过阈值回退到贪婪算法 |
| 方言差异覆盖不全 | 某些方言特有构造被错误格式化 | 扩展方言接口 + 构造模板 |
| 注释占位符替换错误 | 注释位置偏移 | 五层保障体系 + 后处理验证 |
| 参数过多导致配置复杂 | 用户难以理解 | 预设 Profile（Default/DataGrip/Compact）|

---


