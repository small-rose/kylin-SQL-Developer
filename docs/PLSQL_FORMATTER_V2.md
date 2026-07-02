# PL/SQL 格式化引擎 — 架构设计文档 v2

## 1. 设计原则

1. **ParseTree 驱动** — 块结构不从 token 流推导，而从 ANTLR4 生成的 CST（ParseTree）提取
2. **约束求解** — 每个 whitespace 间隙受约束控制，求解器找出最优解
3. **分层隔离** — 解析/模型/约束/求解/输出 各层独立，可单独替换
4. **容错** — 语法错误通过 ANTLRErrorListener 捕获为 Diagnostic，不崩溃
5. **向后兼容** — PlSqlFormatter.format(source, options) 入口不变，FormatResult 不变

---

## 2. 整体架构

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

---

## 3. 包结构

当前包结构保留，新增 `constraint/` 包：

```
com.kylin.plsql.core.format.plsql/
├── builder/
│   ├── PlSqlModelBuilder.java         ← ⭐ 新增: ParseTree → PlSqlBlock 树
│   └── ParseTreeModelBuilder.java     ← visitor 实现
├── dialect/
│   └── PlSqlDialect.java + 4实现      ← 精简: 只保留 keywordCase/stringDelimiter
├── formatter/
│   ├── PlSqlFormatterEngine.java      ← 重构: 替换为 LayoutEngine
│   ├── layout/
│   │   ├── GapConstraint.java         ← 间隙约束模型
│   │   ├── ConstraintGenerator.java   ← PlSqlBlock → GapConstraint[]
│   │   ├── ConstraintSolver.java      ← 约束求解器 (传播 + DP)
│   │   └── StringAssembler.java       ← 约束解 → 格式化文本
│   └── post/
│       └── PostProcessor.java         ← 关键字大小写/注释保护
├── model/                             ← 基本不变, 精简 TokenInfo
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
│   └── PlSqlQualityChecker.java       ← 不变
└── PlSqlFormatter.java                ← 入口不变
```

---

## 3a. 方言差异与适应性设计

### 3a.1 问题

V2 引擎将 `PlSqlDialect` 精简为仅保留 `keywordCase` / `stringDelimiter`，核心假设是 **ANTLR ParseTree 自动消除方言消歧需求**。但该假设不完全成立：

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

### 3a.2 插件化方言接口

```java
public interface PlSqlDialect {
    /** 方言名称 */
    String getName();

    /** 标识符引用符（Oracle/ANSI: "、MySQL: `、PG: "） */
    char getIdentifierQuote();

    /** 字符串定界符（Oracle/MySQL/PG: '、MySQL 可选: "） */
    char getStringDelimiter();

    /** 是否支持美元定界符 $$（仅 PostgreSQL） */
    boolean supportsDollarQuoting();

    /** 集运算符集（Oracle: {UNION, INTERSECT, MINUS}; PG/MySQL: {UNION, INTERSECT, EXCEPT}） */
    Set<String> getSetOperators();

    /** 方言特有 SQL 构造函数 */
    Map<SqlConstruct, FormatTemplate> getConstructTemplates();

    /** 关键字集（大小写转换用） */
    Set<String> getKeywords();

    /** 块类型可用性掩码 */
    Set<PlSqlBlockType> getSupportedBlockTypes();
}
```

### 3a.3 方言具体差异

#### 3a.3.1 Oracle（默认实现）

| 属性 | 值 |
|------|-----|
| 标识符引用符 | `"` (双引号) |
| 字符串定界符 | `'` (单引号) |
| 美元定界符 | 不支持 |
| 集运算符 | `UNION` / `INTERSECT` / `MINUS` |
| 特有构造 | `CONNECT BY` / `START WITH` / `FLASHBACK` / `MATERIALIZED VIEW` / `DBMS_SQL` |
| 特有块类型 | `PACKAGE_SPEC` / `PACKAGE_BODY` / `TYPE_SPEC` / `TYPE_BODY` |
| `LIMIT` 语法 | `OFFSET m ROWS FETCH NEXT n ROWS ONLY` |
| `MERGE` 语法 | `MERGE INTO ... USING ... ON ... WHEN MATCHED THEN UPDATE SET ... DELETE WHERE ... WHEN NOT MATCHED THEN INSERT ...` |
| 递归 CTE | `WITH ... (col_aliases) AS (...)` (无需 RECURSIVE 关键字，Oracle 11gR2+) |

#### 3a.3.2 MySQL

```sql
-- MySQL 存储过程结构差异
CREATE PROCEDURE my_proc(IN p_id INT)
BEGIN
    DECLARE v_name VARCHAR(100);
    SET v_name = 'hello';
    -- MySQL 循环语法
    REPEAT
        SET v_name = CONCAT(v_name, '!');
    UNTIL LENGTH(v_name) > 10
    END REPEAT;
END;

-- MySQL 特有 DML 构造
INSERT INTO t VALUES (1, 'a')
ON DUPLICATE KEY UPDATE name = VALUES(name);

SELECT * FROM t LIMIT 10 OFFSET 5;
```

| 属性 | 值 |
|------|-----|
| 标识符引用符 | `` ` `` (反引号) |
| 字符串定界符 | `'` (单引号，SQL 模式可配双引号) |
| 美元定界符 | 不支持 |
| 集运算符 | `UNION` / `INTERSECT` / `EXCEPT` |
| 特有构造 | `ON DUPLICATE KEY UPDATE` / `LIMIT n OFFSET m` / `REPLACE INTO` |
| 不支持块类型 | `PACKAGE_SPEC` / `PACKAGE_BODY` / `TYPE_SPEC` / `TYPE_BODY` |
| 循环语法 | `REPEAT ... UNTIL ... END REPEAT`、`WHILE ... DO ... END WHILE`、`FOR ... DO ... END FOR` |
| 声明区 | 无隐式声明段，`DECLARE` 在 `BEGIN` 内部 |
| `MERGE` 语法 | 不支持（MySQL 无 MERGE） |

**格式化影响**：
- `REPEAT...UNTIL` → 新增 `REPEAT_LOOP` 块类型（§18.5），`UNTIL` 前缩进弹出
- 无 PACKAGE/TYPE → 约束生成器跳过不可用的块类型
- `LIMIT n OFFSET m` → 单行紧凑格式
- `ON DUPLICATE KEY UPDATE` → UPDATE 前 `OPTIONAL + breakPenalty=0.3`

#### 3a.3.3 PostgreSQL

```sql
-- PostgreSQL 函数体使用美元定界符
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

-- PostgreSQL 特有 DML 构造
INSERT INTO t (id, name) VALUES (1, 'a')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

SELECT * FROM t LIMIT 10 OFFSET 5;
```

| 属性 | 值 |
|------|-----|
| 标识符引用符 | `"` (双引号) |
| 字符串定界符 | `'` (单引号) |
| 美元定界符 | 支持 `$$...$$`、`$func$...$func$` 等命名美元引号 |
| 集运算符 | `UNION` / `INTERSECT` / `EXCEPT`（支持 `ALL`） |
| 特有构造 | `ON CONFLICT ... DO UPDATE` / `RETURNING *` / `LATERAL` / `GENERATED ... AS IDENTITY` |
| 不支持块类型 | 同 MySQL：无 PACKAGE/TYPE |
| 循环语法 | `FOR ... LOOP ... END LOOP`、`WHILE ... LOOP ... END LOOP`、`FOR i IN ... LOOP` |
| 函数体结构 | `AS $$ ... $$ LANGUAGE plpgsql` |

**格式化影响**：
- 美元定界符 `$$` → lexer 阶段需方言模式，格式化引擎将 `$$...$$` 视为单一 content block 不处理内部
- `ON CONFLICT ... DO UPDATE` → UPDATE 前 `OPTIONAL + breakPenalty=0.3`
- `RETURNING *` → DML 后 `OPTIONAL` 换行
- `AS $$` 与函数体 → 函数体内容不解析为 PL/SQL 结构，整体保留

#### 3a.3.4 OceanBase（Oracle 兼容模式）

| 属性 | 值 |
|------|-----|
| 方言基类 | extends OraclePlSqlDialect |
| 关键字集 | Oracle 集 + OceanBase 特有（`TENANT`、`REFRESH`、`OCEANBASE` 等） |
| 块结构 | 与 Oracle 完全一致 |
| `MERGE` 语法 | 同 Oracle |
| 差异点 | OceanBase 2.x+ 支持 PACKAGE，早期版本不支持 |
| 策略 | 继承 Oracle 全部实现，仅在关键字集追加 OB 特有词 |

### 3a.4 方言差异在格式化中的处理

#### 3a.4.1 PlSqlBlockType 可用性

各方言支持的块类型不同，`ConstraintGenerator` 在构建约束时跳过当前方言不支持的块类型：

```java
Set<PlSqlBlockType> supported = dialect.getSupportedBlockTypes();

if (!supported.contains(PlSqlBlockType.PACKAGE_SPEC)) {
    // MySQL/PG: 不创建 PACKAGE_SPEC 约束
    return;
}
```

| 块类型 | Oracle | OB | MySQL | PG |
|--------|--------|----|-------|----|
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
| LOOP | ✅ | ✅ | ✅ | ✅ (FOR/WHILE 循环) |
| ANON_BLOCK | ✅ | ✅ | ✅ (BEGIN...END) | ✅ (DO $$...$$) |

#### 3a.4.2 标识符引用符

标识符引号在格式化后的影响：

```sql
-- Oracle: 引号保留，"" 内不格式化
SELECT "MyColumn", "table"."column" FROM "MyTable";

-- MySQL: 反引号保留，`` 内不格式化
SELECT `MyColumn`, `table`.`column` FROM `MyTable`;
```

**约束策略**：
- `TokenInfo` 中标记 `isQuotedIdentifier`（lexer 阶段设置）
- 被引用的标识符在 keyword case 转换中跳过（引用标识符大小写有语义）
- 引用符前后空格不受影响

#### 3a.4.3 美元定界符（PostgreSQL）

PG 函数体 `$$...$$` 的处理：

```
lexer 阶段:
  AS                                        → 普通 token
  $$ / $func$ / $任意名$                    → 标记为 CONTENT_BLOCK 起始
  ... (函数体内容, 不同部分块结构)           → 整块标记为 channel=HIDDEN
  匹配的 $$                                 → CONTENT_BLOCK 结束

后续阶段:
  ConstraintGenerator:  跳过 CONTENT_BLOCK 范围，不生成约束
  PostProcessor:         内容原样透传（大小写不转换）
  StringAssembler:       原样输出
```

```sql
-- 格式化后 — 函数体原样保留
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

#### 3a.4.4 方言关键字集

各方言的关键字集用于 §17.2 的大小写转换：

| 方言 | 关键字基数 | 特有关键字示例 |
|------|-----------|---------------|
| Oracle | ~300 | `PACKAGE`、`PRAGMA`、`CONNECT`、`MINUS` |
| MySQL | ~250 | `REPEAT`、`UNTIL`、`LIMIT`、`DUPLICATE` |
| PostgreSQL | ~280 | `LATERAL`、`RETURNING`、`CONFLICT`、`EXCEPT` |
| OceanBase | ~310 | Oracle 集 + `TENANT`、`REFRESH`、`OCEANBASE` |

**约束策略**：`PostProcessor` 在构造时获取当前方言的 `getKeywords()` 集，
`convertLine()` 中仅将命中关键字集的大小写转换，非关键字单词（用户标识符）保持不变。

#### 3a.4.5 方言特有 SQL 构造的格式化

| 构造 | 适用方言 | 格式化策略 |
|------|---------|-----------|
| `CONNECT BY` / `START WITH` | Oracle | `WHERE` 后 `OPTIONAL + breakPenalty=0.3`，同 JOIN 树缩进 |
| `FLASHBACK TABLE ... TO TIMESTAMP` | Oracle | 保持一行 |
| `MATERIALIZED VIEW` 选项 | Oracle | 每选项一行（同 §25.8.2） |
| `ON DUPLICATE KEY UPDATE` | MySQL | UPDATE 子句前 `OPTIONAL + breakPenalty=0.3` |
| `ON CONFLICT ... DO UPDATE/NOTHING` | PG | `ON CONFLICT` 前 `OPTIONAL + breakPenalty=0.3` |
| `RETURNING *` / `RETURNING col1, col2` | PG | 同 Oracle RETURNING INTO 但无 INTO 变量，换行缩进 |
| `LIMIT n OFFSET m` | MySQL/PG | 保持一行或 FETCH 风格 |
| `GENERATED ... AS IDENTITY` | PG | 列定义内紧凑 |
| `LATERAL (SELECT ...)` | PG/Oracle | 同 §11.5.7.7 LATERAL 策略 |

#### 3a.4.6 方言差异参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `dialectIdentifierQuote` | char | `"` | 标识符引用符，由方言自动设定 |
| `dialectSupportsPackage` | boolean | true | 是否支持 PACKAGE，由方言自动设定 |
| `dialectSupportsType` | boolean | true | 是否支持 TYPE，由方言自动设定 |
| `dialectSetOperator` | enum | ORACLE | 集运算符风格：ORACLE(MINUS) / ANSI(EXCEPT) |
| `dialectLimitStyle` | enum | ANSI_FETCH | LIMIT 语法：ANSI_FETCH / LIMIT_OFFSET |

### 3a.5 V2 方言设计总结

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
  ├─ 集运算符关键字                  → ConstraintGenerator (§11.9.7)
  ├─ 方言特有 DML 构造              → ConstraintGenerator (addDialectConstraints)
  └─ LIMIT/OFFSET 语法风格          → ConstraintGenerator
```

---

## 4. 数据流详解

### 4.1 词法分析 → TokenInfo[]

```
ANTLR 原有:
  CharStream(源码) → PlSqlLexer → CommonTokenStream(ANTLR Token)

新加:
  CommonTokenStream → TokenInfo[] 转换器
  (只提取 index/type/text/upper/line/column/channel, 去掉 isBlockStart/blockStartType 等旧标记)
```

`TokenInfo` 精简为：

```java
public class TokenInfo {
    public final int index;
    public final int type;       // ANTLR Token 类型 ID
    public final String text;    // 原始文本
    public final String upper;   // 大写
    public final int line;
    public final int column;
    public final int channel;    // 0=代码, 1=空白/注释
}
```

移除旧标记：`isKeyword`, `isBlockStart`, `isBlockEnd`, `blockStartType`, `blockEndVariant`, `statementGroupIdx`, `isConcatChain`。

### 4.2 语法分析 → ParseTree

```
CommonTokenStream → PlSqlParser → ParseTree (CST)

入口规则: sql_script
  sql_script     → (unit_statement ';'?)* EOF
  unit_statement → create_function_body
                 | create_procedure_body
                 | create_package
                 | create_package_body
                 | anonymous_block
                 | dml_statement
                 | ...

结构规则:
  anonymous_block           → (DECLARE seq_of_declare_specs?)? BEGIN seq_of_statements (EXCEPTION exception_handler+)? END (';')
  create_function_body      → CREATE FUNCTION name '(' params ')' RETURN type IS|AS (DECLARE? declare_specs? body)
  create_procedure_body     → CREATE PROCEDURE name '(' params ')' IS|AS (DECLARE? declare_specs? body)
  create_package            → CREATE PACKAGE name IS|AS package_obj_spec* END name?
  create_package_body       → CREATE PACKAGE BODY name IS|AS package_obj_body* (BEGIN stmts EXCEPTION?)? END name?
  body                      → BEGIN seq_of_statements (EXCEPTION exception_handler+)? END name?
  if_statement              → IF condition THEN seq_of_statements elsif_part* else_part? END IF
  loop_statement            → label? (WHILE condition | FOR cursor_loop_param)? LOOP seq_of_statements END LOOP label?
  case_statement            → CASE (expr)? WHEN expr THEN seq_of_statements ... END CASE
  exception_handler         → WHEN name (OR name)* THEN seq_of_statements
```

ANTLR `ParserRuleContext` 为每个规则提供：
- `ctx.start` → 起始 Token (可转 tokenIdx)
- `ctx.stop` → 结束 Token
- `ctx.getRuleIndex()` → 规则类型
- `ctx.children` → 子节点 (TerminalNode / ParserRuleContext)

这些直接用于确定块边界，不需要手写状态机。

### 4.3 ParseTree → PlSqlBlock 树 (ModelBuilder)

```java
class ParseTreeModelBuilder extends PlSqlParserBaseVisitor<PlSqlBlock> {
    private List<TokenInfo> tokens;
    private List<Diagnostic> diagnostics;
    private ANTLRErrorStrategy errorStrategy;

    // 每个语法规则 → 对应 PlSqlBlockType

    @Override
    public PlSqlBlock visitAnonymous_block(Anonymous_blockContext ctx) {
        PlSqlBlock block = new PlSqlBlock(ANON_BLOCK);
        block.startTokenIdx = tokenIndex(ctx.DECLARE() != null ? ctx.DECLARE().getSymbol() : ctx.BEGIN().getSymbol());
        block.stmtStartIdx  = tokenIndex(ctx.BEGIN().getSymbol()) + 1;  // BEGIN 后
        block.exceptStartIdx = ctx.exception_handler() != null
            ? tokenIndex(ctx.exception_handler(0).start) : -1;
        block.endTokenIdx   = tokenIndex(ctx.END().getSymbol());
        // 子节点: ctx.seq_of_declare_specs → DeclSection
        //         ctx.seq_of_statements   → Statement[]
        //         ctx.exception_handler   → ExceptionSection
        return block;
    }

    @Override
    public PlSqlBlock visitIf_statement(If_statementContext ctx) {
        PlSqlBlock block = new PlSqlBlock(IF);
        block.startTokenIdx = tokenIndex(ctx.IF().getSymbol());
        block.endTokenIdx   = tokenIndex(ctx.END().getSymbol());
        // 分支: ctx.IF condition + ctx.THEN → IfBranch
        //       ctx.elsif_part             → IfBranch(ELSIF)
        //       ctx.else_part              → IfBranch(ELSE)
        return block;
    }

    @Override
    public PlSqlBlock visitLoop_statement(Loop_statementContext ctx) {
        PlSqlBlockType type = ctx.WHILE() != null ? WHILE_LOOP
                            : ctx.FOR() != null   ? FOR_LOOP
                            : LOOP;
        PlSqlBlock block = new PlSqlBlock(type);
        block.startTokenIdx = tokenIndex(ctx.LOOP().getSymbol());  // ← 关键: 仅 LOOP 关键字
        // WHILE/FOR 的 header 由 ctx.WHILE/ctx.FOR 到 ctx.LOOP 确定
        block.stmtStartIdx  = tokenIndex(ctx.LOOP().getSymbol()) + 1;
        block.endTokenIdx   = tokenIndex(ctx.END().getSymbol());
        return block;
    }
    // ... 其余类似
}
```

**核心收益：** 不需要 `detectBlockStart/detectBlockEnd`、不需要 phase 状态机、不需要自己解析 `END` 对应谁——语法树给出了精确答案。

### 4.4 约束生成 (PlSqlBlock → GapConstraint[])

```java
class GapConstraint {
    int fromTokenIdx;     // 间隙起始 token
    int toTokenIdx;       // 间隙结束 token (通常 from+1)

    // 空格
    int minSpaces        = 1;
    int maxSpaces        = Integer.MAX_VALUE;
    int preferredSpaces  = 1;

    // 换行
    NewlineMode newlineMode = OPTIONAL;  // FORBIDDEN / REQUIRED / OPTIONAL
    int indentDelta      = 0;           // 换行时缩进增量 (相对于父块)
    Boolean endAlign     = null;        // true=端对齐, false=正常缩进

    // 对齐组 — 声明对齐/参数对齐
    String alignGroupId;                // 同组内取最大宽度对齐

    // 空行
    boolean blankLineBefore = false;    // 换行前需加一个空行

    // 代价 (用于 DP 折行)
    double breakPenalty  = 1.0;         // 在此断行的代价
}
```

每个 PlSqlBlock 类型对应一个约束生成器。示例：

```java
class IfConstraintGenerator implements ConstraintGenerator {
    void generate(PlSqlBlock block, FormatOptions opts, List<GapConstraint> out) {
        // THEN 前换行控制
        if (opts.isThenOnNewLine()) {
            gap(before(THEN), THEN).forceNewline(true).indent(+1);
        }
        // ELSIF/ELSE 与 IF 对齐
        for (IfBranch branch : block.ifBranches) {
            GapConstraint g = gap(before(branch), branch.startTokenIdx);
            g.forceNewline(true);
            g.indentDelta(branch.type == IF ? 1 : 0);  // ELSIF/ELSE 缩进与 IF 同级别
        }
        // END IF 端对齐
        if (opts.isEndAlign()) {
            gap(afterLastStmt, END).forceNewline(true).endAlign(true);
        } else {
            gap(afterLastStmt, END).forceNewline(true).indent(+1);
        }
    }
}
```

### 4.5 约束求解器

**两步求解：**

#### Step 1: 硬约束传播 (确定性)

遍历所有 GapConstraint，应用强制约束：

```java
for (GapConstraint g : constraints) {
    if (g.newlineMode == REQUIRED) {
        result[g].spaces = 1;           // 换行时空格为 1 (缩进级别)
        result[g].newline = true;
        result[g].indent = computeIndent(g, blockStack);
    }
    if (g.newlineMode == FORBIDDEN) {
        result[g].newline = false;
        result[g].spaces = clamp(g.preferredSpaces, g.minSpaces, g.maxSpaces);
    }
    if (g.alignGroupId != null) {
        registerAlignGroup(g, result);  // 组内行对齐
    }
}
```

这一步解决：缩进（BEGIN/END 级别）、端对齐、关键字换行（THEN/ELSE/ELSIF）、声明对齐、参数对齐等。

#### Step 2: DP 折行优化 (非确定性)

对剩余 `newlineMode == OPTIONAL` 的间隙，用动态规划决定换行位置：

```
状态: gapIndex i, currentLineWidth w
决策: break (换行) 或 noBreak (不换行)
代价函数:
  cost(break)    = breakPenalty[i]
  cost(noBreak)  = 0 (w + tokenWidth <= maxLineWidth)
                  | exponential (w + tokenWidth > maxLineWidth)

DP[i][w] = min(cost(break) + DP[i+1][indentSize],
               cost(noBreak) + DP[i+1][w + tokenWidth + 1])

目标: 找到 DP[0][0] 对应的路径
```

这是 Knuth-Plass line breaking 的简化版，与 Prettier/IntelliJ 一致。时间复杂度 O(gaps × maxWidth)，在 token 数量 ~5000、maxWidth ~120 下是微秒级。

### 4.6 StringAssembler

```java
class StringAssembler {
    String assemble(TokenInfo[] tokens, GapResult[] gaps) {
        StringBuilder sb = new StringBuilder();
        int currentIndent = 0;
        for (int i = 0; i < tokens.length; i++) {
            GapResult g = gaps[i];  // tokens[i] 与 tokens[i+1] 之间的间隙
            if (g == null) continue;
            if (g.newline) {
                sb.append('\n');
                appendIndent(sb, g.indent);
            } else {
                sb.append(repeat(' ', g.spaces));
            }
            appendTokenText(sb, tokens[i]);
        }
        return sb.toString();
    }
}
```

### 4.7 PostProcessor

```
格式化文本 → 关键字大小写转换
           → 注释保护 (@formatter:off/on)
           → 空白行压缩/保留
           → 尾随空格修剪
           → 最终文本
```

---

## 5. 错误处理

```
解析阶段:
  PlSqlParser.addErrorListener(new BaseErrorListener() {
    @Override
    public void syntaxError(Recognizer, offendingSymbol, line, charPositionInLine, msg, e) {
      diagnostics.add(new Diagnostic(ERROR, SYNTAX_ERROR, line, charPositionInLine, msg, ""));
    }
  });
  ParseTree tree = parser.sql_script();  // ANTLR 默认错误恢复, 即使有错也会产出部分树

模型构建阶段:
  if (diagnostics 包含 ERROR) {
    model.isComplete = false;  // 标记不完全, 后续 quality checker 会 fallback
  }

格式化阶段:
  try { engine.format(model); }
  catch (Exception e) {
    diagnostics.add(new Diagnostic(ERROR, FORMAT_ERROR, 0, 0, e.getMessage(), ""));
    return source;  // fallback
  }
```

**Parse 失败不崩溃**，ANTLR 的单 token 插入/删除恢复机制保证总能得到部分 ParseTree。但 `isComplete=false` 会触发 quality checker 的 fallback 逻辑。

---

## 6. 旧方案对照

| 特性 | 旧方案 | 新方案 |
|---|---|---|
| 语法分析 | 不用 Parser，只用 Lexer | 完整 ParseTree (CST) |
| 块结构分析 | 手写状态机 (`analyzeStructure` ~500 行) | ParseTreeVisitor 从语法树提取 |
| `END` 匹配 | `detectBlockEnd` + 方言消歧 + 栈比对 | ParseTree 天然确定 |
| phase 状态机 | DECL/STMT/EXCEPTION 手动维护 | 无状态机，靠树结构 |
| 缩进 | 硬编码在 `format*Block()` 方法中 | GapConstraint.indentDelta 声明式 |
| 折行 | 无（长行不处理） | DP 求解器自适应 |
| 对齐 | 固定宽度 | AlignGroup 约束 |
| 错误诊断 | 只有 `MISMATCHED_END`/`EXTRA_END` 等猜测 | ANTLR 精确语法错误 + 行/列 |

---

## 7. 文件变更清单

| 操作 | 文件 | 说明 |
|---|---|---|
| **新建** | `builder/ParseTreeModelBuilder.java` | ParseTreeVisitor, 替换旧 analyzer |
| **新建** | `formatter/layout/GapConstraint.java` | 间隙约束模型 |
| **新建** | `formatter/layout/ConstraintGenerator.java` | PlSqlBlock → GapConstraint[] |
| **新建** | `formatter/layout/ConstraintSolver.java` | 约束传播 + DP 折行 |
| **新建** | `formatter/layout/StringAssembler.java` | GapResult → String |
| **新建** | `formatter/layout/ConstraintGeneratorFactory.java` | BlockType → Generator 映射 |
| **新建** | `formatter/post/PostProcessor.java` | 关键字大小写/注释/空白 |
| **修改** | `builder/PlSqlModelBuilder.java` | 重命名为旧方案的迁移垫片，或直接替换为新实现 |
| **修改** | `model/TokenInfo.java` | 移除旧标记字段 (isBlockStart/isBlockEnd/blockStartType/blockEndVariant/statementGroupIdx/isConcatChain) |
| **修改** | `formatter/PlSqlFormatterEngine.java` | 替换为 LayoutEngine 调度 |
| **修改** | `PlSqlFormatter.java` | 入口逻辑微调 (传入 ANTLR ErrorListener) |
| **修改** | `dialect/OraclePlSqlDialect.java` | 删除 `detectBlockStart/detectBlockEnd/hasIsAs/hasDeclSection/hasStmtSection`，只保留 `isKeyword`/`getStringDelimiter` |
| **删除** | 旧 `PlSqlBlock.Phase` 枚举 | 不再需要 |
| **删除** | 旧 `PlSqlBlockEnd.java` | 不再需要 |
| **保留** | `model/Statement.java`, `Declaration.java`, `IfBranch.java`, `CaseWhen.java`, `ExceptionSection.java`, `ConcatenationSegment.java` | 内容解析逻辑复用 |
| **保留** | `qa/PlSqlQualityChecker.java` | 不变 |
| **保留** | `dialect/PlSqlDialectFactory.java` | 不变 |

---

## 8. 实施路线

```
Phase 1: ParseTreeModelBuilder
  └─ 新建 ParseTreeModelBuilder.java
  └─ 实现 visitAnonymous_block / visitIf_statement / visitLoop_statement /
     visitCase_statement / visitCreate_function_body / visitCreate_procedure_body /
     visitCreate_package / visitCreate_package_body
  └─ 输出 PlSqlBlock 树 (startTokenIdx/endTokenIdx/stmtStartIdx/stmtEndIdx/children)
  └─ 期望: 旧 formatter 能用新 builder 产出同级别格式化质量

Phase 2: Constraint 框架
  └─ 新建 GapConstraint.java / ConstraintGenerator.java
  └─ 新建 ConstraintSolver.java (传播 + DP)
  └─ 新建 StringAssembler.java

Phase 3: 格式调整
  └─ 对齐 FormatOptions 的 67 个参数
  └─ 实现 AlignGroup (声明对齐/参数对齐)
  └─ 实现 PostProcessor

Phase 4: 集成 + 测试
  └─ PlSqlFormatter.format() 入口集成
  └─ DemoTest 回归测试
  └─ PlSqlQualityChecker 验证
```

---

## 9. 字符串拼接保护策略

### 9.1 问题背景

Oracle PL/SQL 中字符串拼接使用 `||`，但 ANTLR 词法规则 (`PlSqlLexer.g4:2579`) 将其定义为两个独立 `BAR` 标记：
```
BAR: '|';                                          // 单个 | 字符
...
concatenation: ... | concatenation BAR BAR concatenation | ...    // 两个 || 形成拼接
```

因此 token 流中 `||` 表现为两个相邻的 channel-0 token：`|` + `|`。格式化引擎必须保证它们在任何情况下不被拆开。

### 9.2 五层保障体系

| 层面 | 机制 | 负责层 | 状态 |
|------|------|--------|------|
| ① 字符串字面量完整性 | `'delete from '` 在 lexer 中为单 token，任何处理不超过 token 边界 | Lexer / Assembler | ✅ |
| ② `\|\|` 不拆开 | FORBIDDEN 约束锁死 `BAR` + `BAR` 之间零空格 | `ConstraintGenerator` | ✅ |
| ③ 引号内不改大小写 | PostProcessor 扫描到 `'` 跳过直到匹配结束引号 | `PlSqlPostProcessor` | ✅ |
| ④ 字符串内不断行 | DP 折行只在 token 间 OPTIONAL 间隙决策，不会闯入单 token | `ConstraintSolver` | ✅ |
| ⑤ 拼接链优先在 `\|\|` 后换行 | `computeBreakPenalty` 中第二个 `BAR` 后方 `breakPenalty = 0.1` | `ConstraintGenerator` | ⬜ 待加 |

### 9.3 拼接链换行策略

DataGrip / PL/SQL Developer 风格：长字符串拼接链优先在 `||` 后换行，续行缩进一级。

```
-- 期望输出
v_del_sql := 'delete from ' || v_back_up.originaltable
    || ' ' || v_back_up.condition1
    || ' and rownum <= ' || v_back_up.batchsize;

-- 等价约束: 第二个 BAR 后方间隙 breakPenalty=0.1
// ConstraintGenerator.computeBreakPenalty() 中追加:
if ("|".equals(pt) && "|".equals(nt)) return 0.1;  // || 后优先换行
```

### 9.4 正确性保证

约束引擎单引擎处理 PL/SQL 块内全部内容（含 DML），**不存在**将 DML 抽离后经第二引擎重解析的路径。这是之前 `||` 被拆成 `| |` 的唯一原因。当前架构已消除此桥接逻辑。

---

## 10. 分号行末黏附策略

### 10.1 问题

行宽 DP 可能在 `;` 前的 OPTIONAL 间隙断行，产生 `;` 单独一行：
```
v_del_sql := 'delete from ' || v_back_up.originaltable || ' ' || v_back_up.condition1
;
```

DataGrip / PL/SQL Developer 中 `;` 永远黏在语句末尾 token 之后，不可能出现在行首。

### 10.2 方案

在 `ConstraintGenerator.generate()` 末尾，对所有 `;` 前一个 channel-0 token 到 `;` 之间的间隙施加 FORBIDDEN 约束：

```java
// 伪代码：组装阶段追加
for (int i = 0; i < tokens.size(); i++) {
    if (";".equals(tokens.get(i).text) && tokens.get(i).channel == 0) {
        int prev = prevVisible(i - 1);
        if (prev >= 0) {
            addConstraint(new GapConstraint(prev, i)
                .forceNewline(false).spaces(0, 1, 1));
        }
    }
}
```

注意：FORBIDDEN 只禁止 `;` 前换行，不禁止 `;` 与前一 token 间的空格（保留 `1` 格）。

### 10.3 不受影响场景

- `END IF ;` 中 `IF` 与 `;` 之间加 FORBIDDEN，仅禁止在 `IF ;` 中间换行。
- IF/LOOP/CASE 块的 `END ... ;` 中，`;` 的 FORBIDDEN 只影响它前面的 token，不影响 END 关键字的缩进 pop。

---

## 11. DML 约束 — 单引擎处理策略

### 11.1 设计原则

PL/SQL 块内部出现的 DML 语句（SELECT INTO、INSERT、UPDATE、DELETE、MERGE、EXECUTE IMMEDIATE 等）**不**抽离给 `SqlFormatter` 引擎处理，而是由约束引擎在同一趟流程中完成格式化。

依据：
- DataGrip / PL/SQL Developer 均为单引擎一次跑完
- 双引擎桥接逻辑 `applyStatementFormatting` 导致：输出重排序、`||` 被解码器拆开、缩进上下文丢失
- 约束引擎已具备 all-in-one 能力：FORBIDDEN、REQUIRED、OPTIONAL + breakPenalty DP 足以处理 DML 断行

### 11.2 DML 关键字断行约束

检测 PL/SQL 块内部 `seq_of_statements` 中出现的 DML 语句，在以下关键字前施加 `OPTIONAL + breakPenalty` 约束：

| 关键字 | breakPenalty | 说明 |
|--------|-------------|------|
| `FROM` | 0.3 | SELECT/INSERT 的数据源 |
| `WHERE` | 0.3 | 条件子句 |
| `ORDER` | 0.3 | 排序子句 |
| `GROUP` | 0.3 | 分组子句 |
| `HAVING` | 0.3 | 分组过滤 |
| `START` | 0.3 | START WITH (层次查询) |
| `CONNECT` | 0.3 | CONNECT BY (层次查询) |
| `UNION` / `MINUS` / `INTERSECT` | 0.3 | 集合操作 |
| `INTO` | 0.3 | SELECT INTO / BULK COLLECT INTO |
| `BULK COLLECT` | 0.3 | BULK COLLECT INTO |
| `FOR UPDATE` | 0.3 | 锁定子句 |
| `RETURNING` | 0.3 | 返回子句 |

**实现位置**：`ConstraintGenerator` 内新增 `addDmlConstraints(block)` 方法，在 `walkBlock()` 扫描到块内 statement 时，根据其 `startTokenIdx` ~ `endTokenIdx` 范围找出 DML 关键字并施加约束。

**约束模式**：均为 `OPTIONAL`（不强制换行），只影响 DP 折行决策。

### 11.3 拼接链换行

`||` 连接的长 AL 字符串链（常见于动态 SQL 拼接），在第二个 `BAR` 后的间隙设 `breakPenalty=0.1`，确保超长时优先在 `||` 后换行 + 缩进续行（参见 §9.3）。

### 11.4 处理范围界定

```
┌─ 独立 DML (不在任何块内) ───────→ SqlFormatter (模板引擎)
│   SELECT * FROM t WHERE id = 1;
│
└─ 块内 DML ─────────────────────→ 约束引擎
    PROCEDURE p IS
    BEGIN
        SELECT name INTO v       ← 不抽离，不走 SqlFormatter
        FROM users               ← OPTIONAL break
        WHERE id = p_id;         ← OPTIONAL break
    END;
```

---

### 11.5 DML 子句树深度格式化

#### 11.5.1 问题背景

§11.2 的 DML 关键字断行仅对 FROM/WHERE/JOIN 等关键字施加 `breakPenalty=0.3`，让 DP 求解器自行断行。
这能处理简单 DML，但复杂 DML（多表 JOIN、多层子查询、长列清单、WHERE 多条件）会出现对齐混乱。

**DataGrip 做法**：对块内 DML 也运行完整的 SQL 格式化规则（JOIN 树缩进、WHERE AND/OR 对齐、列清单断行风格等），
而非仅依赖 DP 断行惩罚。

#### 11.5.2 JOIN 树缩进

DataGrip 风格——多表 JOIN 时右表缩进一级：

```sql
-- 期望输出
SELECT e.*, d.department_name
FROM employees e
JOIN departments d ON e.department_id = d.department_id
LEFT JOIN (
    SELECT manager_id, COUNT(*) AS cnt
    FROM projects
    GROUP BY manager_id
) p ON e.employee_id = p.manager_id;
```

**约束策略**：检测 `JOIN` / `LEFT JOIN` / `RIGHT JOIN` / `FULL JOIN` / `CROSS JOIN` / `INNER JOIN` 关键字，
对其前方间隙施加 `OPTIONAL + breakPenalty=0.2`，且 `indentDelta` 相对于 `FROM` 缩进一级。

| JOIN 类型 | breakPenalty | indentDelta |
|-----------|-------------|-------------|
| `JOIN` / `INNER JOIN` | 0.2 | 1 (相对于 FROM) |
| `LEFT JOIN` / `RIGHT JOIN` / `FULL JOIN` | 0.2 | 1 (相对于 FROM) |
| `CROSS JOIN` | 0.2 | 1 (相对于 FROM) |
| `ON` / `USING` | 0.2 | 1 (相对于 JOIN) |

#### 11.5.3 WHERE AND/OR 对齐

DataGrip 风格——条件运算符换行时与第一个条件列对齐：

```sql
-- 期望输出
SELECT *
FROM employees
WHERE department_id = 100
  AND salary > 5000
  OR (manager_id IS NULL AND status = 'ACTIVE');
```

**约束策略**：检测 `AND` / `OR` 关键字（不在 WHERE 条件内部的嵌套子句中），
在 WHERE 后的第一个 AND/OR 的间距施加连续缩进（continuation indent）。
`FormatOptions.dmlWhereAndPosition` 参数控制对齐方式。

| 对齐模式 | 效果 |
|----------|------|
| `INDENTED` | AND/OR 与 WHERE 后第一个列对齐（缩进与 SELECT 同级别 + 连续缩进） |
| `SAME_LINE` | AND/OR 跟随上一行末尾 |
| `OUTDENT` | AND 比 WHERE 少缩进 |

#### 11.5.4 SELECT 列清单断行风格

DataGrip 风格——列清单超长时按风格断行：

```sql
-- COMPACT（紧凑）
SELECT employee_id, first_name, last_name, email, phone_number, hire_date, job_id, salary, commission_pct, manager_id, department_id
FROM employees;

-- 每行一列（EXPAND）
SELECT employee_id
     , first_name
     , last_name
     , email
     , phone_number
FROM employees;
```

**约束策略**：在 SELECT 关键字后的列分隔间隙施加约束。

| 参数 | 效果 |
|------|------|
| `selectColumnMode` | COMPACT(紧凑)/ONE_PER_LINE(每行一列)/ALIGN(对齐) |
| `selectColumnsPerRow` | COMPACT 模式每行列数上限，0=不限 |
| `commaPosition` | 逗号位置 TRAILING(列尾)/LEADING(列首) |

#### 11.5.5 逗号位置

DataGrip 支持两种逗号断行风格：

```sql
-- 行尾逗号
SELECT employee_id,
       first_name,
       last_name
FROM employees;

-- 行首逗号
SELECT employee_id
     , first_name
     , last_name
FROM employees;
```

**约束策略**：`FormatOptions.commaPosition` 控制 `SELECT` 列表中逗号与下一列之间的换行方向。

| 模式 | 约束 |
|------|------|
| `TRAILING` (行尾) | 逗号后 `newlineMode=OPTIONAL` |
| `LEADING` (行首) | 逗号前 `newlineMode=OPTIONAL`，逗号后 `FORBIDDEN` |

#### 11.5.6 IN 列表格式化

##### 11.5.6.1 问题

IN 列表（`WHERE col IN (...)`）是 SQL 中高频使用的条件子句。当前 §11.5.6 仅提供
最简单的 binary 展开/紧凑控制（`dmlInClauseExpand`），缺少 DataGrip 级别的细粒度格式化维度。

**DataGrip 做法**：
- 三种格式化模式：COMPACT（全部一行）/ ONE_PER_LINE（每行一个值）/ 混合（每行 N 个值）
- 自动展开阈值：当值数量超限时自动切换模式
- 括号对齐策略：`)` 可与 `IN` 关键字对齐或与第一个值对齐
- IN (SELECT ...) 子查询与值列表的约束策略分离
- 多列 IN `(a,b) IN ((1,2), (3,4))` 的元组对齐

##### 11.5.6.2 格式化模式

```sql
-- COMPACT（全部一行）
WHERE department_id IN (10, 20, 30, 40, 50, 60, 70, 80, 90, 100);

-- ONE_PER_LINE（每行一个值）
WHERE department_id IN (
    10,
    20,
    30,
    40,
    50
);

-- 混合：每行 N 个值（此处 N=4）
WHERE department_id IN (
    10, 20, 30, 40,
    50, 60, 70, 80,
    90, 100
);
```

##### 11.5.6.3 约束规则

| 约束位置 | 约束模式 | 参数控制 | 说明 |
|----------|---------|----------|------|
| `IN` / `NOT IN` 后 `(` | 1 空格 | — | FORBIDDEN 不拆开 |
| `(` 后 → 第一个值 | `OPTIONAL newline` | `inListFormat` | COMPACT 时不换行，展开时换行 |
| 值间逗号 → 下一个值 | 取决于模式 | `inListColumnsPerRow` | COMPACT: no newline；混合: 每 N 个换行；ONE_PER_LINE: 总是换行 |
| 最后一个值 → `)` | `OPTIONAL newline`（展开时换行） | `inListBraceAlign` | 括号对齐策略 |
| `)` 对齐 | 与 `IN` 关键字/第一个值对齐 | `inListBraceAlign` | IN_KEYWORD / FIRST_VALUE |

##### 11.5.6.4 括号对齐策略

```sql
-- IN_KEYWORD（DataGrip 默认）— ) 与 IN 对齐
WHERE department_id IN (
    10, 20, 30, 40,
    50, 60, 70, 80
);

-- FIRST_VALUE — ) 与第一个值对齐
WHERE department_id IN (
    10, 20, 30, 40,
    50, 60, 70, 80
       );
```

**约束策略**：INDENTED 模式下，值列表缩进一级（相对于 `IN`）。
括号对齐模式仅影响 `)` 的缩进级别：
- `IN_KEYWORD`：`)` 缩进与 `IN` 同级别（缩进弹出到 `IN` 级别）
- `FIRST_VALUE`：`)` 缩进与第一个值同级别

##### 11.5.6.5 自动展开阈值

当值列表长度超过 `inListThreshold` 时，即使 `inListFormat=COMPACT` 也自动展开为混合模式：

```sql
-- 阈值 = 10，IN 列表有 15 个值 → 自动展开
WHERE department_id IN (
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
    11, 12, 13, 14, 15
);
```

**约束策略**：`ConstraintGenerator` 在检测到 `IN (...)` 时，先统计值个数，
若 `count > inListThreshold`，则覆盖 `inListFormat` 为混合模式，`columnsPerRow` 用 `inListColumnsPerRow`。

##### 11.5.6.6 IN 子查询 vs 值列表

IN 子查询 `IN (SELECT ...)` 与值列表 `IN (1, 2, 3)` 的格式化策略不同：

| 场景 | 策略 | 说明 |
|------|------|------|
| `IN (value, value, ...)` | 走 IN 列表格式化（§11.5.6） | 值列表使用逗号分隔 |
| `IN (SELECT ...)` | 走子查询格式化（§11.5.7） | `(` 后 SELECT 推一级缩进，查询体内复用 DML 格式化 |
| `IN (SELECT ... UNION SELECT ...)` | 复合子查询 | `(` 后整体缩进，内部 UNION 按 §11.9 处理 |

**判定方式**：解析 `(` 后第一个非空白 token：
- 若为数字/字符串/标识符/参数 → 值列表
- 若为 `SELECT` / `WITH` → 子查询

```sql
-- 值列表
WHERE id IN (100, 101, 102, 103, 104);

-- 子查询
WHERE id IN (
    SELECT employee_id
    FROM employees
    WHERE department_id = 50
);

-- 复合子查询
WHERE id IN (
    SELECT employee_id FROM employees
    UNION ALL
    SELECT employee_id FROM ex_employees
);
```

##### 11.5.6.7 多列 IN

```sql
-- 多列 IN（元组比较）
WHERE (department_id, manager_id) IN (
    (100, 200),
    (101, 201),
    (102, 202)
);

-- 行表达式 IN
WHERE (col1, col2, col3) IN (
    (1, 'a', SYSDATE),
    (2, 'b', SYSDATE)
);
```

**约束策略**：
- 外层 `(col1, col2)` 保持一行，FORBIDDEN 内部分行
- 每个元组 `(val1, val2)` 整体为一行，逗号后 `REQUIRED newline`
- 元组内 `val1, val2` 紧凑不换行（FORBIDDEN）
- 多列元组对齐：所有行第一列右对齐？第二列左对齐？（由 `inListMultiColumn` 控制）

##### 11.5.6.8 NOT IN

`NOT IN` 的格式化规则与 `IN` 完全一致：
- `NOT IN` 视为一个整体（FORBIDDEN 不拆开）
- 值列表/子查询/多列策略复用 IN 规则

```sql
WHERE department_id NOT IN (10, 20, 30, 40, 50);
```

##### 11.5.6.9 新增参数汇总

| 参数 | 类型 | 默认值 | 说明 | 参考 |
|------|------|--------|------|------|
| `inListFormat` | enum | COMPACT | IN 列表模式：COMPACT / ONE_PER_LINE / MIXED | §11.5.6.2 |
| `inListColumnsPerRow` | int | 5 | 混合模式每行值个数 | §11.5.6.2 |
| `inListThreshold` | int | 10 | 自动展开阈值 | §11.5.6.5 |
| `inListBraceAlign` | enum | IN_KEYWORD | `)` 对齐：IN_KEYWORD / FIRST_VALUE | §11.5.6.4 |

> **兼容性**：`dmlInClauseExpand` (boolean) 保留为快捷映射：
> `false` → `inListFormat=COMPACT`；`true` → `inListFormat=ONE_PER_LINE`

#### 11.5.7 子查询位置感知格式化

##### 11.5.7.1 问题

子查询（`(SELECT ...)`）出现在 SQL 的不同位置，其格式化偏好截然不同：

| 出现位置 | 典型偏好 | 原因 |
|----------|---------|------|
| SELECT 列清单 | 紧凑（INLINE） | 列值表达式，展开会打断列对齐 |
| WHERE 条件 | 自动（AUTO） | 短子查询可内联，长时展开 |
| FROM 子句（派生表） | 展开（EXPAND） | 结构复杂需让读者看到数据源 |
| INSERT ... SELECT | 展开（EXPAND） | 数据源结构通常复杂 |
| HAVING / ON / JOIN 条件 | 同 WHERE 策略 | — |
| EXISTS / NOT EXISTS | 紧凑 | 存在性检查通常短小 |

当前文档仅以 `dmlSubqueryFormat` (boolean) 控制是否递归格式化子查询，
未区分位置、未提供不同风格（INLINE/EXPAND/AUTO）、无展开阈值。

**DataGrip 做法**：按子查询位置独立控制风格，每个位置可选 INLINE/EXPAND/AUTO。

##### 11.5.7.2 格式化模式

```sql
-- INLINE（内联，保持一行）
SELECT e.emp_id,
       (SELECT d.dept_name FROM departments d WHERE d.dept_id = e.dept_id) AS dept_name
FROM employees e;

-- EXPAND（展开，缩进换行）
SELECT e.*
FROM (
    SELECT employee_id, first_name, last_name,
           ROW_NUMBER() OVER (PARTITION BY department_id ORDER BY salary DESC) AS rn
    FROM employees
) ranked
WHERE ranked.rn = 1;

-- 短 INLINE / 长 EXPAND（AUTO）
-- 子查询文本长度 ≤ threshold → INLINE，否则 EXPAND
WHERE department_id IN (
    SELECT department_id FROM departments WHERE location_id = 100
);
```

##### 11.5.7.3 位置感知约束总表

| 位置 | 参数 | 默认值 | 说明 |
|------|------|--------|------|
| SELECT 列清单中的子查询 | `selectListSubqueryStyle` | INLINE | 列值表达式，紧凑优先 |
| WHERE 条件中的子查询 | `whereSubqueryStyle` | AUTO | 短内联长展开 |
| FROM 子句中的子查询（派生表） | `fromSubqueryStyle` | EXPAND | 数据源结构展开 |
| HAVING 条件中的子查询 | `havingSubqueryStyle` | AUTO | 同 WHERE |
| ON / JOIN 条件中的子查询 | `onSubqueryStyle` | AUTO | — |
| INSERT ... SELECT 子查询 | `insertSubqueryStyle` | EXPAND | 数据源展开 |
| EXISTS / NOT EXISTS | `existsSubqueryStyle` | INLINE | 存在性检查紧凑 |
| LATERAL 子查询 | `lateralSubqueryStyle` | EXPAND | 侧向视图展开 |
| 通用后备 | `subqueryStyle` | AUTO | 未匹配位置的默认值 |

##### 11.5.7.4 位置判定逻辑

`ConstraintGenerator` 在遍历 `ParseTree` 时判定子查询 `(SELECT ...)` 的上下文位置：

```java
enum SubqueryPosition {
    SELECT_LIST,    // SELECT col, (SELECT ...)
    WHERE_CLAUSE,   // WHERE col IN (SELECT ...)
    FROM_CLAUSE,    // FROM (SELECT ...) alias
    HAVING_CLAUSE,  // HAVING ... (SELECT ...)
    ON_CLAUSE,      // JOIN ... ON ... (SELECT ...)
    INSERT_SELECT,  // INSERT INTO ... SELECT ...
    EXISTS,         // WHERE EXISTS (SELECT ...)
    LATERAL,        // LATERAL (SELECT ...)
    SCALAR,         // 标量子查询（单行单列）
    OTHER           // 未识别
}
```

判定策略（伪代码）：
```java
SubqueryPosition detectPosition(ParserRuleContext subqueryCtx) {
    ParserRuleContext parent = subqueryCtx.getParent();
    // 向上遍历找到容器规则
    if (parent instanceof Select_listContext)    return SELECT_LIST;
    if (parent instanceof Where_clauseContext)   return WHERE_CLAUSE;
    if (parent instanceof Table_refContext)      return FROM_CLAUSE;
    if (parent instanceof Exists_clauseContext)  return EXISTS;
    // ...
}
```

##### 11.5.7.5 格式化约束

| 约束位置 | INLINE | EXPAND | AUTO |
|----------|--------|--------|------|
| `(` → SELECT | 1 空格，不换行 | `REQUIRED newline + indentDelta=1` | 同 EXPAND（若展开） |
| SELECT 内部 | 不换行 | 复用 §11.5.2-§11.5.5 | 同 EXPAND（若展开） |
| `)` 前 | 无 | `newline + indentDelta=-1` | 同 EXPAND |
| `)` → 外部后续 token | 1 空格 | `newline` 或空格 | 取决于上下文 |

##### 11.5.7.6 AUTO 模式展开阈值

```java
boolean shouldExpand(List<Token> subTokens, FormatOptions opts) {
    // 1. 子查询文本长度超过阈值
    int totalChars = subTokens.stream()
        .filter(t -> t.channel == 0)
        .mapToInt(t -> t.text.length() + 1)
        .sum();
    if (totalChars > opts.getSubqueryThreshold()) return true;

    // 2. 子查询包含集合操作
    if (containsSetOperator(subTokens)) return true;

    // 3. 子查询包含多层嵌套
    if (subqueryDepth(subTokens) >= 2) return true;

    return false;
}
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `subqueryThreshold` | int | 80 | AUTO 模式下展开的字符数阈值 |

##### 11.5.7.7 各位置格式化示例

**SELECT 列清单中的子查询：**
```sql
-- INLINE（推荐）
SELECT e.emp_id,
       e.first_name,
       (SELECT d.dept_name FROM departments d WHERE d.dept_id = e.dept_id) AS dept_name,
       e.salary
FROM employees e;

-- EXPAND（选中时）
SELECT e.emp_id,
       e.first_name,
       (SELECT d.dept_name
        FROM departments d
        WHERE d.dept_id = e.dept_id) AS dept_name,
       e.salary
FROM employees e;
```

**WHERE 中的子查询：**
```sql
-- AUTO: 短 → INLINE
SELECT * FROM employees
WHERE department_id IN (SELECT department_id FROM departments WHERE location_id = 100);

-- AUTO: 长 → EXPAND
SELECT * FROM employees
WHERE department_id IN (
    SELECT department_id
    FROM departments
    WHERE location_id IN (SELECT location_id FROM locations WHERE country_id = 'US')
);
```

**FROM 中的子查询（派生表）：**
```sql
-- EXPAND（默认）
SELECT sub.dept_id, sub.emp_count, sub.avg_salary
FROM (
    SELECT department_id AS dept_id,
           COUNT(*)      AS emp_count,
           AVG(salary)   AS avg_salary
    FROM employees
    GROUP BY department_id
) sub
WHERE sub.emp_count > 10;
```

**INSERT ... SELECT：**
```sql
-- EXPAND（默认）
INSERT INTO emp_summary (dept_id, emp_count, avg_salary)
SELECT department_id, COUNT(*), AVG(salary)
FROM employees
GROUP BY department_id;
```

**EXISTS / NOT EXISTS：**
```sql
-- INLINE（默认）
SELECT * FROM employees e
WHERE EXISTS (SELECT 1 FROM departments d WHERE d.dept_id = e.department_id);

-- EXPAND（可选）
SELECT * FROM employees e
WHERE EXISTS (
    SELECT 1
    FROM departments d
    WHERE d.dept_id = e.department_id
      AND d.status = 'ACTIVE'
);
```

**LATERAL 子查询：**
```sql
SELECT e.emp_id, e.first_name, d_summary.total_sales
FROM employees e,
LATERAL (
    SELECT SUM(o.amount) AS total_sales
    FROM orders o
    WHERE o.salesman_id = e.emp_id
) d_summary;
```

**标量子查询（SCALAR）：**
```sql
-- 标量子查询通常紧凑
SET v_count = (SELECT COUNT(*) FROM employees);
```

##### 11.5.7.8 多层嵌套递归

深度 >= 2 的子查询递归格式化，每层缩进递增一级：

```sql
SELECT e.*
FROM employees e
WHERE e.department_id IN (
    SELECT department_id                       -- level 1, indent=1
    FROM departments d
    WHERE d.location_id IN (
        SELECT location_id                    -- level 2, indent=2
        FROM locations l
        WHERE l.country_id IN (
            SELECT country_id                 -- level 3, indent=3
            FROM countries
            WHERE region_id = 1
        )
    )
);
```

**约束策略**：
- 递归深度由括号嵌套层级决定
- 每层缩进 = 当前层数 × `indentSize`
- `subqueryMaxDepth` 参数可限制最大递归深度（超限后不展开，保持一行）

##### 11.5.7.9 子查询内格式化规则

子查询内部的 SELECT 格式化复用 §11.5.2-§11.5.5 的规则：
- JOIN 树缩进
- WHERE AND/OR 对齐
- 列清单风格
- 逗号位置

但受 `subqueryOverrideOptions` 参数控制：展开的子查询可独立覆盖这些选项。

##### 11.5.7.10 新增参数汇总

| 参数 | 类型 | 默认值 | 说明 | 参考 |
|------|------|--------|------|------|
| `subqueryStyle` | enum | AUTO | 通用后备风格 INLINE/EXPAND/AUTO | §11.5.7.3 |
| `selectListSubqueryStyle` | enum | INLINE | SELECT 列中子查询 | §11.5.7.3 |
| `whereSubqueryStyle` | enum | AUTO | WHERE 条件中子查询 | §11.5.7.3 |
| `fromSubqueryStyle` | enum | EXPAND | FROM 子句派生表 | §11.5.7.3 |
| `havingSubqueryStyle` | enum | AUTO | HAVING 中子查询 | §11.5.7.3 |
| `onSubqueryStyle` | enum | AUTO | ON/JOIN 条件中子查询 | §11.5.7.3 |
| `insertSubqueryStyle` | enum | EXPAND | INSERT ... SELECT | §11.5.7.3 |
| `existsSubqueryStyle` | enum | INLINE | EXISTS / NOT EXISTS | §11.5.7.3 |
| `lateralSubqueryStyle` | enum | EXPAND | LATERAL 子查询 | §11.5.7.3 |
| `subqueryThreshold` | int | 80 | AUTO 模式展开字符阈值 | §11.5.7.6 |
| `subqueryMaxDepth` | int | 10 | 最大递归格式化深度 | §11.5.7.8 |

> **兼容性**：`dmlSubqueryFormat` (boolean) 保留为快捷映射：
> `false` → 全部子查询 `INLINE`；`true` → 按各自位置参数决定

#### 11.5.8 BULK COLLECT INTO 多变量对齐

```sql
-- 期望输出
SELECT employee_id, first_name, last_name
BULK COLLECT INTO v_emp_id,
                 v_first_name,
                 v_last_name
FROM employees
WHERE department_id = p_dept;
```

**约束策略**：`BULK COLLECT INTO` 后检测逗号分隔的变量列表，建立 AlignGroup 约束对齐第二个及后续变量。

#### 11.5.9 EXECUTE IMMEDIATE USING 子句对齐

```sql
-- 期望输出
EXECUTE IMMEDIATE v_sql
    INTO v_result
    USING p_param1, p_param2, p_param3
    RETURNING INTO v_out;
```

**约束策略**：`USING` / `INTO` / `RETURNING INTO` 关键字前施加 `OPTIONAL + breakPenalty=0.2`，
且续行缩进一级（相对于 EXECUTE IMMEDIATE）。

#### 11.5.10 DML 子格式化参数汇总

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `dmlJoinIndent` | boolean | true | JOIN 子句缩进 |
| `dmlWhereAndPosition` | enum | INDENTED | AND/OR 对齐模式 |
| `selectColumnMode` | enum | ALIGN | SELECT 列模式 COMPACT/ONE_PER_LINE/ALIGN |
| `selectColumnsPerRow` | int | 0 | COMPACT 模式每行列数上限 |
| `commaPosition` | enum | TRAILING | 逗号位置 TRAILING/LEADING |
| `dmlInClauseExpand` | boolean | false | IN 子句值跨行 |
| `dmlSubqueryFormat` | boolean | true | 子查询递归格式化 |
| `dmlBulkCollectAlign` | boolean | true | BULK COLLECT INTO 对齐 |
| `dmlUsingAlign` | boolean | true | USING 子句对齐 |

### 11.6 MERGE 语句格式化

#### 11.6.1 问题

MERGE INTO（UPSERT）是 Oracle PL/SQL 中高频使用的 DML 语句，其语法结构与 SELECT/INSERT/UPDATE 完全不同：

```sql
MERGE INTO target_table t
USING source_table s
ON (t.id = s.id AND t.type = s.type)
WHEN MATCHED THEN
    UPDATE SET
        t.name  = s.name,
        t.col1  = s.col1,
        t.col2  = s.col2
    DELETE WHERE t.status = 'OBSOLETE'
WHEN NOT MATCHED THEN
    INSERT (id, name, col1, col2)
    VALUES (s.id, s.name, s.col1, s.col2)
LOG ERRORS INTO err_log ('merge_batch_1') REJECT LIMIT UNLIMITED;
```

§11.2 的 DML 关键字断行表未包含任何 MERGE 独有关键字（USING/ON/WHEN MATCHED/WHEN NOT MATCHED），现有约束生成器遇到 MERGE 时无规则可依。

**DataGrip** 对 MERGE 有独立的格式化规则集：
- `MERGE INTO` 通常与 `USING` 同行或换行（可配）
- `ON` 子句可跟随 USING 或换行
- `WHEN MATCHED THEN` / `WHEN NOT MATCHED THEN` 各自独占一行
- UPDATE SET 和 INSERT 子句推一级缩进
- DELETE WHERE 作为 WHEN MATCHED 的可选子句

#### 11.6.2 格式化约束

| 约束位置 | 约束模式 | 参数控制 | 说明 |
|----------|---------|----------|------|
| `MERGE INTO` 头部 | 保持一行 | — | `MERGE INTO table alias` |
| `USING` 前 | `OPTIONAL + breakPenalty=0.3` | `mergeUsingNewline` | USING 前可选换行 |
| `USING table` | USING 与表名间 1 空格 | — | FORBIDDEN 不拆 |
| `ON` 前 | `OPTIONAL + breakPenalty=0.2` | `mergeOnNewline` | ON 前可选换行 |
| `ON (...)` 括号内 | 复用 §11.5.3 AND/OR 对齐规则 | — | 条件换行 |
| `ON` → `WHEN MATCHED` | `REQUIRED newline` | — | 必须换行 |
| `WHEN MATCHED` → `THEN` | `FORBIDDEN newline` | — | `WHEN MATCHED THEN` 同一行 |
| `THEN` → `UPDATE` | `REQUIRED newline + indentDelta=1` | — | UPDATE 推一级 |
| `UPDATE SET` 内赋值 | 赋值逗号后 `OPTIONAL newline` | `mergeUpdateSetAlign` | 赋值对齐 |
| `UPDATE SET` → `DELETE WHERE` | `OPTIONAL + breakPenalty=0.3` | — | DELETE 可选换行 |
| 前 WHEN 块结束 → `WHEN NOT MATCHED` | `REQUIRED newline` | — | 必须换行 |
| `WHEN NOT MATCHED` → `THEN` | `FORBIDDEN newline` | — | 同一行 |
| `THEN` → `INSERT` | `REQUIRED newline + indentDelta=1` | — | INSERT 推一级 |
| `INSERT (cols)` → `VALUES (...)` | 紧凑同行；超长可在 VALUES 前换行 | — | |
| `LOG ERRORS INTO` | `OPTIONAL + breakPenalty=0.3` | — | 同 §24.3 |

#### 11.6.3 对齐组

`UPDATE SET` 赋值的列名列对齐：

```sql
-- 对齐前
WHEN MATCHED THEN
    UPDATE SET
        t.name = s.name,
        t.col1 = s.col1,
        t.long_column_name = s.long_column_name;

-- 对齐后
WHEN MATCHED THEN
    UPDATE SET
        t.name             = s.name,
        t.col1             = s.col1,
        t.long_column_name = s.long_column_name;
```

**约束策略**：`UPDATE SET` 后在逗号分隔的赋值链中，每个 `=` 前目标列建立 AlignGroup 对齐。

#### 11.6.4 多 WHEN 子句

Oracle MERGE 支持多 `WHEN NOT MATCHED` 子句（Oracle 23c+）：

```sql
MERGE INTO target t
USING source s
ON (t.key = s.key)
WHEN MATCHED THEN
    UPDATE SET t.val = s.val
WHEN NOT MATCHED THEN
    INSERT (key, val) VALUES (s.key, s.val)
WHEN NOT MATCHED THEN
    INSERT (key, val) VALUES (s.key, s.val2);
```

每个 `WHEN` 子句前均 `REQUIRED newline`。

#### 11.6.5 新增参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mergeUsingNewline` | boolean | true | USING 前换行 |
| `mergeOnNewline` | boolean | false | ON 前换行 |
| `mergeWhenNewline` | boolean | true | WHEN MATCHED/NOT MATCHED 前换行 |
| `mergeUpdateSetAlign` | boolean | true | UPDATE SET 赋值 = 对齐 |

### 11.7 INSERT / UPDATE / DELETE 语句格式化

#### 11.7.1 问题

INSERT/UPDATE/DELETE 是最基础的 DML 语句，但当前文档仅将其列在枚举中（§11.1），
未提供任何独有格式化约束。§11.2 的 DML 关键字断行表未包含 `VALUES`、`SET`、`INTO` 等关键字。

**DataGrip 做法**：每条 DML 语句都有独立的格式化规则：
- INSERT：列清单风格、VALUES 括号跨行、INSERT ALL 缩进、RETURNING INTO 对齐
- UPDATE：SET 赋值 `=` 对齐、赋值逗号位置（行首/行尾）、每行列数
- DELETE：FROM 换行、WHERE 对齐

#### 11.7.2 INSERT 格式化

##### 语法概览

```sql
-- 单行 INSERT
INSERT INTO employees (employee_id, first_name, last_name, hire_date)
VALUES (100, 'John', 'Doe', SYSDATE);

-- INSERT ... SELECT（查询插入）
INSERT INTO emp_summary (dept_id, emp_count, avg_salary)
SELECT department_id, COUNT(*), AVG(salary)
FROM employees
GROUP BY department_id;

-- INSERT ... RETURNING INTO
INSERT INTO employees (employee_id, first_name)
VALUES (seq_emp.NEXTVAL, 'John')
RETURNING employee_id INTO v_emp_id;

-- INSERT ALL（多表插入）
INSERT ALL
    INTO t1 VALUES (1, 'a')
    INTO t2 VALUES (2, 'b')
SELECT * FROM dual;

-- 多行 VALUES（VALUES 内多行）
INSERT INTO t (id, name)
VALUES (1, 'a'), (2, 'b'), (3, 'c');
```

##### 格式化约束

| 约束位置 | 约束模式 | 参数控制 | 说明 |
|----------|---------|----------|------|
| `INSERT INTO` 头部 | 保持一行 | — | `INSERT INTO table alias` |
| `INSERT INTO` → 列清单 `(...)` | 同行或换行 | `dmlInsertColumnNewline` | 列清单前可选换行 |
| 列清单内 `col1, col2, ...` | 同 SELECT 列清单风格 | `selectColumnMode` / `commaPosition` / `selectColumnsPerRow` | 复用 §11.5.4-§11.5.5 |
| 列清单 `)` → `VALUES` | 同行或换行 | `dmlValuesNewline` | VALUES 前可选换行 |
| `VALUES (...)` 值列表 | 紧凑（一行）或值列表跨行 | `dmlValuesExpand` | 值列表 `minSpaces=1`，逗号后 OPTIONAL 换行 |
| `VALUES (...)` → `RETURNING` | `OPTIONAL + breakPenalty=0.3` | — | 可选换行，缩进一级 |
| `INTO` → 变量列表 | 变量逗号后 `OPTIONAL` 换行 | `dmlIntoAlign` | 第二行起对齐第一个变量 |
| `INSERT ALL` → 子 INSERT | `REQUIRED newline`，缩进一级 | — | 每个 `INTO` 新行 |
| `INSERT ... SELECT` | SELECT 前 `REQUIRED newline + indentDelta=1` | — | 查询体复用 §11.5 |

##### VALUES 多值跨行

```sql
-- 紧凑
INSERT INTO t (id, name) VALUES (1, 'a'), (2, 'b'), (3, 'c');

-- 展开
INSERT INTO t (id, name)
VALUES (1, 'a'),
       (2, 'b'),
       (3, 'c');
```

**约束策略**：多值行 `(val1, val2)` 内每值逗号可选换行（COMPACT 时不换行，EXPAND 时每值一行）。
第二行起的值列表左括号对齐第一个左括号。

##### VALUES 值列表对齐

```sql
-- 对齐前
INSERT INTO t VALUES (1, 'long text', 100.50, SYSDATE);
-- 对齐后（列对齐）
INSERT INTO t VALUES (
    1,
    'long text',
    100.50,
    SYSDATE
);
```

**约束策略**：`dmlValuesExpand=true` 时，`VALUES (` 后的每个值逗号后换行，
值列表推一级缩进，`)` 弹出与 `VALUES` 对齐。

##### INSERT ALL / FIRST 格式化

```sql
-- INSERT ALL
INSERT ALL
    INTO t1 (id, name) VALUES (1, 'a')
    INTO t2 (id, name) VALUES (2, 'b')
    INTO t3 (id, name) VALUES (3, 'c')
SELECT * FROM dual;

-- 带条件的 INSERT ALL
INSERT ALL
    WHEN salary > 5000 THEN
        INTO emp_high (id, name) VALUES (emp_id, emp_name)
    WHEN salary > 3000 THEN
        INTO emp_mid (id, name) VALUES (emp_id, emp_name)
    ELSE
        INTO emp_low (id, name) VALUES (emp_id, emp_name)
SELECT emp_id, emp_name, salary FROM employees;
```

**约束策略**：
- `INSERT ALL` / `INSERT FIRST` 保持一行
- 每个 `INTO` 子句前 `REQUIRED newline + indentDelta=1`
- `WHEN condition THEN` 前 `REQUIRED newline`，`THEN` 同行
- `SELECT` 源子查询前 `REQUIRED newline + indentDelta=1`

##### RETURNING INTO 对齐

```sql
-- 单变量
INSERT INTO t (id) VALUES (seq.NEXTVAL)
RETURNING id INTO v_id;

-- 多变量对齐
INSERT INTO t (id, name) VALUES (seq.NEXTVAL, 'test')
RETURNING id, created_date
      INTO v_id, v_date;
```

**约束策略**：`RETURNING` 前 `OPTIONAL + breakPenalty=0.3`，缩进一级。
`RETURNING` 列与 `INTO` 变量建立 AlignGroup 对齐。

#### 11.7.3 UPDATE 格式化

##### 语法概览

```sql
-- 单列 SET
UPDATE employees SET salary = 5000 WHERE employee_id = 100;

-- 多列 SET — 赋值对齐
UPDATE employees SET
    salary        = 5000,
    first_name    = 'John',
    last_name     = 'Doe',
    hire_date     = SYSDATE,
    department_id = 100
WHERE employee_id = 200;

-- 多列 SET — 紧凑
UPDATE employees SET salary = 5000, first_name = 'John', last_name = 'Doe'
WHERE employee_id = 200;

-- UPDATE ... RETURNING INTO
UPDATE employees SET salary = salary * 1.1
WHERE department_id = 50
RETURNING employee_id, salary INTO v_id, v_salary;
```

##### SET 子句约束

| 约束位置 | 约束模式 | 参数控制 | 说明 |
|----------|---------|----------|------|
| `UPDATE table` 头部 | 保持一行 | — | |
| `SET` 前 | 1 空格，不换行 | — | FORBIDDEN 不拆开 |
| `SET col = val` 逗号后 | `OPTIONAL newline` | `updateSetColumnsPerRow` | 每行列数控制 |
| `col = val` 中 `=` 前后 | `preferredSpaces=1` | `updateSetAlign` | 可对齐 |
| `SET` → `WHERE` | `OPTIONAL + breakPenalty=0.3` | — | WHERE 前可选换行 |
| `SET` → `RETURNING` | `OPTIONAL + breakPenalty=0.3` | — | RETURNING 前可选换行 |

##### SET 赋值对齐模式

```sql
-- TRAILING（行尾逗号）
UPDATE employees SET
    salary        = 5000,
    first_name    = 'John',
    department_id = 100;

-- LEADING（行首逗号）
UPDATE employees SET
    salary        = 5000
  , first_name    = 'John'
  , department_id = 100;
```

**约束策略**：所有赋值 `=` 的目标列名建立 AlignGroup 对齐到最长列名宽度。
逗号位置由 `updateSetCommaPosition` 控制（TRAILING/LEADING）。
`=` 左右各 1 格空格。

##### SET 每行列数

当 `updateSetColumnsPerRow > 0` 时，允许每行多个赋值：

```sql
-- updateSetColumnsPerRow = 2
UPDATE employees SET
    salary = 5000, first_name = 'John',
    last_name = 'Doe', hire_date = SYSDATE,
    department_id = 100
WHERE employee_id = 200;
```

**约束策略**：计数 SET 后的赋值链，每 `N` 个赋值后在逗号后换行。
`updateSetColumnsPerRow = 0`（默认）表示每行一个赋值。

#### 11.7.4 DELETE 格式化

##### 语法概览

```sql
-- 简单 DELETE
DELETE FROM employees WHERE employee_id = 100;

-- DELETE table（省略 FROM）
DELETE employees WHERE employee_id = 100;

-- DELETE ... RETURNING INTO
DELETE FROM employees
WHERE department_id = 50
RETURNING employee_id, salary INTO v_id, v_salary;
```

##### 格式化约束

| 约束位置 | 约束模式 | 参数控制 | 说明 |
|----------|---------|----------|------|
| `DELETE` 头部 | 保持一行 | — | |
| `FROM` 前 | 1 空格（可选） | — | 可与 DELETE 同行或省略 FROM |
| `FROM table` → `WHERE` | `OPTIONAL + breakPenalty=0.3` | `deleteFromNewline` | WHERE 前可选换行 |
| `WHERE` 内部 | 复用 §11.5.3 AND/OR 对齐 | — | |
| `WHERE` → `RETURNING` | `OPTIONAL + breakPenalty=0.3` | — | |
| `RETURNING ... INTO` | 复用 §11.7.2 RETURNING INTO 对齐 | — | |

##### FROM 省略处理

Oracle PL/SQL 允许 `DELETE table_name` 省略 `FROM` 关键字。
检测到 `DELETE` 后直接接表名时，不施加额外约束，按紧凑一行处理。

#### 11.7.5 新增参数汇总

| 参数 | 类型 | 默认值 | 说明 | 参考 |
|------|------|--------|------|------|
| `dmlInsertColumnNewline` | boolean | false | INSERT 列清单前换行 | §11.7.2 |
| `dmlValuesNewline` | boolean | true | VALUES 前换行 | §11.7.2 |
| `dmlValuesExpand` | boolean | false | VALUES 值列表跨行 | §11.7.2 |
| `dmlIntoAlign` | boolean | true | RETURNING INTO / SELECT INTO 变量对齐 | §11.7.2 |
| `dmlInsertAllIndent` | boolean | true | INSERT ALL 子 INSERT 缩进 | §11.7.2 |
| `updateSetAlign` | boolean | true | UPDATE SET `=` 对齐 | §11.7.3 |
| `updateSetColumnsPerRow` | int | 1 | UPDATE SET 每行赋值个数（0=不限） | §11.7.3 |
| `updateSetCommaPosition` | enum | TRAILING | UPDATE SET 逗号位置 TRAILING/LEADING | §11.7.3 |
| `deleteFromNewline` | boolean | true | DELETE FROM 后 WHERE 前换行 | §11.7.4 |

### 11.8 CTE (WITH 子句) 格式化

#### 11.8.1 问题

CTE（Common Table Expression，``WITH`` 子句）是 DML 语句中常用的查询结构，
但当前文档未提供任何 CTE 独有格式化约束。§11.2 的 DML 关键字断行表、§11.5 的子句树深度格式化均未涉及 CTE。

```sql
WITH
    dept_stats AS (
        SELECT department_id,
               COUNT(*)    AS emp_count,
               AVG(salary) AS avg_salary
        FROM employees
        GROUP BY department_id
    ),
    high_depts AS (
        SELECT *
        FROM dept_stats
        WHERE emp_count > 10
    )
SELECT d.*, h.avg_salary
FROM departments d
JOIN high_depts h ON d.department_id = h.department_id;
```

**DataGrip 做法**：CTE 拥有独立的格式化控制维度：
- WITH 关键字位置（与第一 CTE 同行/单独一行）
- CTE 名称与 AS 子句的对齐模式
- 多 CTE 间逗号位置（行尾/行首）
- CTE 查询体缩进与主查询的关系
- 递归 CTE（WITH RECURSIVE）的 SEARCH/CYCLE 子句

#### 11.8.2 格式化约束总表

| 约束位置 | 约束模式 | 参数控制 | 说明 |
|----------|---------|----------|------|
| `WITH` 前 | 与主查询之间保留空格或换行 | — | 由主查询断行决策 |
| `WITH` 与第一 CTE 名 | `OPTIONAL newline` | `cteWithNewline` | WITH 后可选换行 |
| CTE 名与 `AS` | 保持同行，1 空格 | — | FORBIDDEN 不拆开 |
| `AS` 与 `(` | 换行 (`REQUIRED newline + indentDelta=1`) | `cteAsNewline` | AS 后换行推一级缩进 |
| CTE 查询体 `(...)` 内 | 复用 §11.5 DML 子句树规则 | — | SELECT/JOIN/WHERE 等 |
| `)` 后逗号 | 同行或换行 | `cteCommaPosition` | 行尾/行首逗号 |
| CTE 逗号 → 下一 CTE | `REQUIRED newline`（多 CTE 时） | — | 每个 CTE 独立行 |
| CTE 间空行 | 可选空行分隔 | `cteBlankLineBetween` | 相邻 CTE 间加空行 |
| 最后 CTE `)` → 主查询 | `REQUIRED newline + indentDelta=-1` | — | 弹出缩进 |

#### 11.8.3 CTE 名称对齐模式

```sql
-- COMPACT（紧凑）
WITH dept_stats AS (SELECT * FROM departments),
high_depts AS (SELECT * FROM dept_stats)
SELECT * FROM high_depts;

-- ONE_PER_LINE（每 CTE 一行，名称左对齐）
WITH
    dept_stats AS (
        SELECT * FROM departments
    ),
    high_depts AS (
        SELECT * FROM dept_stats
    )
SELECT * FROM high_depts;

-- ALIGN（CTE 名列对齐）
WITH
    dept_stats  AS (
        SELECT * FROM departments
    ),
    high_depts  AS (
        SELECT * FROM dept_stats
    ),
    third_cte   AS (
        SELECT * FROM high_depts
    )
SELECT * FROM third_cte;
```

**ALIGN 模式约束策略**：`WITH` 后的第一个 token 对齐组建立，各 CTE 名称右对齐到组内最大宽度。`AS` 列左对齐。

#### 11.8.4 逗号位置

```sql
-- TRAILING（行尾逗号）
WITH
    dept_stats AS (
        SELECT * FROM departments
    ),
    high_depts AS (
        SELECT * FROM dept_stats
    )

-- LEADING（行首逗号）
WITH
    dept_stats AS (
        SELECT * FROM departments
    )
  , high_depts AS (
        SELECT * FROM dept_stats
    )
```

**约束策略**：与 UPDATE SET 逗号位置实现共享 `commaPosition` 框架。
行首逗号时，逗号缩进与 `(` 对齐或与上一行 `AS` 对齐。

#### 11.8.5 递归 CTE（RECURSIVE）

Oracle 从 11gR2 开始支持递归 CTE，使用 `WITH ... (col_aliases) AS (...)` 或 `WITH RECURSIVE`：

```sql
WITH RECURSIVE org_tree (emp_id, manager_id, level) AS (
    -- 锚点成员
    SELECT employee_id, manager_id, 1
    FROM employees
    WHERE manager_id IS NULL
    UNION ALL
    -- 递归成员
    SELECT e.employee_id, e.manager_id, t.level + 1
    FROM employees e
    JOIN org_tree t ON e.manager_id = t.emp_id
)
SEARCH DEPTH FIRST BY emp_id SET order_col
CYCLE emp_id SET is_cycle TO 'Y' DEFAULT 'N'
SELECT emp_id, manager_id, level
FROM org_tree
ORDER BY order_col;
```

**格式化约束**：

| 位置 | 约束 |
|------|------|
| `WITH RECURSIVE` / `WITH` + 列别名 `(col1, col2)` | 保持一行，列别名紧凑 |
| 锚点成员 SELECT | CTE 查询体内，复用 §11.5 |
| `UNION ALL` / `UNION` | `REQUIRED newline`（锚点与递归成员间换行） |
| 递归成员 SELECT | `UNION ALL` 后换行，缩进与锚点同级 |
| `SEARCH` 子句 | `)` → `SEARCH` 间换行，缩进与 CTE 同级 |
| `CYCLE` 子句 | `REQUIRED newline`（SEARCH 后换行） |
| `SEARCH` / `CYCLE` → 主查询 | `REQUIRED newline` |

#### 11.8.6 子查询因子化（Subquery Factoring）

Oracle 支持在子查询内嵌套 CTE：

```sql
SELECT *
FROM (
    WITH local_cte AS (
        SELECT * FROM departments
    )
    SELECT d.*, l.*
    FROM departments d
    JOIN local_cte l ON d.department_id = l.department_id
);
```

**约束策略**：嵌套 CTE 复用 §11.8.2 的约束规则，缩进深度基于外层 `(` 的缩进级别。

#### 11.8.7 CTE 与 PL/SQL 块的交互

PL/SQL 块内使用 CTE（常见于动态 SQL 中的 WITH 子句）：

```sql
PROCEDURE p AS
    v_sql VARCHAR2(4000);
BEGIN
    v_sql := 'WITH
        dept_stats AS (
            SELECT department_id, COUNT(*) AS cnt
            FROM employees
            GROUP BY department_id
        )
        SELECT * FROM dept_stats';

    EXECUTE IMMEDIATE v_sql;
END;
```

**约束策略**：
- CTE 字符串作为普通字符串拼接处理（§9 保护策略）
- 字符串内的 CTE 格式化不予处理（同 §25.11 的块内 DDL 策略）
- 若 CTE 作为块内独立 DML 直接出现（非字符串），则约束引擎按 §11.8.2 规则处理

#### 11.8.8 新增参数汇总

| 参数 | 类型 | 默认值 | 说明 | 参考 |
|------|------|--------|------|------|
| `cteFormat` | enum | ONE_PER_LINE | CTE 格式：COMPACT/ONE_PER_LINE/ALIGN | §11.8.3 |
| `cteWithNewline` | boolean | true | WITH 关键字后换行 | §11.8.2 |
| `cteAsNewline` | boolean | true | AS 后换行 | §11.8.2 |
| `cteCommaPosition` | enum | TRAILING | CTE 逗号位置 TRAILING/LEADING | §11.8.4 |
| `cteBlankLineBetween` | boolean | false | CTE 间加空行 | §11.8.2 |
| `cteRecursiveFormat` | boolean | true | 递归 CTE SEARCH/CYCLE 格式化 | §11.8.5 |

### 11.9 SET 运算符 (UNION / INTERSECT / MINUS / EXCEPT) 格式化

#### 11.9.1 问题

SET 运算符（UNION / UNION ALL / INTERSECT / MINUS / EXCEPT）用于组合多个查询结果，
其格式化策略与 SELECT 内部子句完全不同：操作符前的换行决策、操作符两侧的列对齐、
复合集合操作的括号嵌套缩进等，均需独立设计。

当前文档仅在 §11.2 的 DML 关键字断行表中为 `UNION / MINUS / INTERSECT` 设了 `breakPenalty=0.3`，
无任何专属约束规则。旧 `format-design.md` 中的 `setOperatorNewline` / `setOperatorAlign` 参数未迁移。

**DataGrip 做法**：
- UNION / INTERSECT / MINUS 前换行（可配）
- 操作符两侧的 SELECT/子查询列清单对齐
- UNION ALL 的 ALL 保持与 UNION 同行
- 括号复合操作支持递归嵌套缩进
- 多段 UNION ALL 的段间可加空行
- 全局 ORDER BY / FETCH FIRST 与最后一段查询保持距离

#### 11.9.2 语法概览

```sql
-- 基本 UNION
SELECT employee_id, first_name, last_name
FROM employees
UNION
SELECT employee_id, first_name, last_name
FROM ex_employees;

-- UNION ALL（多段）
SELECT department_id, SUM(salary) AS total
FROM employees
GROUP BY department_id
UNION ALL
SELECT department_id, SUM(salary) AS total
FROM temp_employees
GROUP BY department_id
UNION ALL
SELECT department_id, SUM(salary) AS total
FROM contractors
GROUP BY department_id;

-- 带括号的复合集合操作（改变优先级）
(SELECT * FROM t1 WHERE status = 'A')
INTERSECT
(SELECT * FROM t2 WHERE status = 'B')
MINUS
(SELECT * FROM t3 WHERE status = 'C');

-- 深层嵌套复合
SELECT * FROM t1
UNION ALL
(
    SELECT * FROM t2
    INTERSECT
    SELECT * FROM t3
)
MINUS
SELECT * FROM t4;

-- UNION 后全局 ORDER BY / FETCH FIRST
SELECT emp_id, salary FROM employees
UNION ALL
SELECT emp_id, salary FROM ex_employees
ORDER BY salary DESC
FETCH FIRST 10 ROWS ONLY;
```

#### 11.9.3 格式化约束总表

| 约束位置 | 约束模式 | 参数控制 | 说明 |
|----------|---------|----------|------|
| **操作符前换行** | | | |
| `SELECT` → `UNION`/`ALL`/`INTERSECT`/`MINUS`/`EXCEPT` | `REQUIRED newline`（默认）或 `OPTIONAL` | `setOperatorNewline` | 操作符前换行 |
| **操作符段** | | | |
| `UNION` / `UNION ALL` | `UNION` 与 `ALL` 间 FORBIDDEN | — | 同一行 |
| `INTERSECT` / `MINUS` / `EXCEPT` | 保持一行 | — | |
| **操作符后换行** | | | |
| `UNION` → 下一个 `SELECT` / `(` | `REQUIRED newline` | — | 操作符后换行，缩进弹出与上一级同级别 |
| **两侧 SELECT 列对齐** | | | |
| UNION 上下列清单列名 | AlignGroup: 各段对应列的列名对齐 | `setOperatorAlign` | 每段 SELECT 的列清单组对齐 |
| **括号复合嵌套** | | | |
| `(` → 子 SELECT | `REQUIRED newline + indentDelta=1` | — | 括号 SELECT 推一级 |
| 子 SELECT → `)` | 最后一行后 `REQUIRED newline + indentDelta=-1` | — | 弹出缩进 |
| `)` → 下一操作符 | `REQUIRED newline` | — | `)` 与操作符各行 |
| **多段操作符** | | | |
| 段间空行 | 可选添加空行 | `setOperatorBlankLine` | 当连续 3+ 段时有效 |
| **全局 ORDER BY / FETCH FIRST** | | | |
| 最后一段 → ORDER BY | `OPTIONAL + breakPenalty=0.3` | — | 与最后一段之间保持间距 |
| ORDER BY → FETCH FIRST | 同行或换行 | — | |

#### 11.9.4 操作符位置

```sql
-- 前换行（标准）
SELECT a, b FROM t1
UNION ALL
SELECT a, b FROM t2;

-- 紧凑（不换行）
SELECT a, b FROM t1 UNION ALL
SELECT a, b FROM t2;
```

**约束策略**：`setOperatorNewline=true`（默认）时，前一 SELECT 最后一行与操作符之间 `REQUIRED newline`。
`setOperatorNewline=false` 时，两者之间 `preferredSpaces=1` 不换行（仅在超长时由 DP 决定）。

#### 11.9.5 两侧 SELECT 列对齐

```sql
-- 不对齐
SELECT employee_id, first_name, last_name, hire_date
FROM employees
UNION ALL
SELECT employee_id, first_name, last_name, hire_date
FROM ex_employees;

-- 对齐后（setOperatorAlign=true）
SELECT employee_id, first_name, last_name,      hire_date
FROM employees
UNION ALL
SELECT employee_id, first_name, last_name,      hire_date
FROM ex_employees;
```

**约束策略**：逐段扫描 SET 操作两端的 SELECT 列清单，建立跨段 AlignGroup。
要求参与 UNION 的 SELECT 列数相同（否则不对齐）。

**实现方式**：
```java
void alignSetOperatorColumns(PlSqlBlock unionBlock) {
    List<PlSqlBlock> selects = collectSelectsAroundOperators(unionBlock);
    for (int colIdx = 0; colIdx < selects.get(0).columnCount; colIdx++) {
        String alignGroupId = "setop_col_" + colIdx;
        for (PlSqlBlock sel : selects) {
            GapConstraint g = gapBeforeColumn(sel, colIdx);
            g.alignGroupId = alignGroupId;
        }
    }
}
```

#### 11.9.6 括号复合嵌套

```sql
-- 一层括号
(SELECT * FROM t1 WHERE status = 'A')
INTERSECT
(SELECT * FROM t2 WHERE status = 'B');

-- 两层嵌套
SELECT * FROM t1
UNION ALL
(
    SELECT * FROM t2
    INTERSECT
    SELECT * FROM t3
)
MINUS
SELECT * FROM t4;
```

**约束策略**：

```
顶层 SELECT                               ← indent=0
UNION ALL                                 ← indent=0
(                                         ← indent=0
    SELECT * FROM t2                      ← indent=1
    INTERSECT                             ← indent=1
    SELECT * FROM t3                      ← indent=1
)                                         ← indent=0 (弹出)
MINUS                                     ← indent=0
SELECT * FROM t4                          ← indent=0
```

`(` 后所有内容缩进一级。`)` 弹出缩进回到上一级。

括号复合内的 SET 操作符不产生额外缩进（与括号外的操作符区别）。

#### 11.9.7 MINUS 与 EXCEPT

Oracle 使用 `MINUS`，标准 SQL 和 PostgreSQL 使用 `EXCEPT`。两者语义相同，格式一致。

| 方言 | 关键字 | Note |
|------|--------|------|
| Oracle | `MINUS` | Oracle 原生 |
| PostgreSQL | `EXCEPT [ALL]` | PG 支持 EXCEPT ALL |
| MySQL | `EXCEPT` | MySQL 8.0+ 支持 |
| SQL Server | `EXCEPT` | SQL Server 支持 |

**约束策略**：`MINUS` / `EXCEPT` 的格式化规则与 `UNION` 完全一致。
方言通过 `SqlDialect.getSetOperators()` 提供关键字列表，`ConstraintGenerator` 统一处理。

#### 11.9.8 全局 ORDER BY / FETCH FIRST

```sql
-- 带全局 ORDER BY
SELECT emp_id, salary FROM employees
UNION ALL
SELECT emp_id, salary FROM ex_employees
ORDER BY salary DESC
FETCH FIRST 10 ROWS ONLY;

-- OFFSET + FETCH
SELECT emp_id FROM t1
INTERSECT
SELECT emp_id FROM t2
ORDER BY emp_id
OFFSET 5 ROWS
FETCH NEXT 10 ROWS ONLY;
```

**约束策略**：
- `ORDER BY` / `FETCH FIRST` / `OFFSET` 是全局作用于整个 UNION 结果
- `ORDER BY` 前 `OPTIONAL + breakPenalty=0.3`
- `FETCH FIRST/OFFSET` 保持与 `ORDER BY` 同行或换行
- 这些子句不被包含在任何段的对齐组中

#### 11.9.9 新增参数汇总

| 参数 | 类型 | 默认值 | 说明 | 参考 |
|------|------|--------|------|------|
| `setOperatorNewline` | boolean | true | SET 操作符前换行 | §11.9.4 |
| `setOperatorAlign` | boolean | false | 两侧 SELECT 列对齐 | §11.9.5 |
| `setOperatorBlankLine` | boolean | false | 多段操作符间加空行 | §11.9.3 |

## 12. EXCEPTION 段缩进修复

### 12.1 当前问题

`walkBlock` 中 EXCEPTION 段被施加了 `requireNewline(exceptIdx, next, 1)`，导致 WHEN 缩进比正确级别深一级。

```
-- 当前输出
            EXCEPTION           ← level 3 (BEGIN+1)
                WHEN            ← level 4 (EXCEPTION+1) ✗
                NO_DATA_FOUND   ← level 4

-- 期望输出
            EXCEPTION           ← level 3 (与 BEGIN 同级别)
            WHEN                ← level 3 (与 EXCEPTION 同级别)
                NO_DATA_FOUND   ← level 4 (WHEN THEN 的语句)
```

### 12.2 根因

`walkBlock` 中 EXCEPTION 的约束代码：
```java
if (exceptIdx >= 0) {
    int next = nextVisible(exceptIdx + 1);
    requireNewline(exceptIdx, next, 1);  // indentDelta=1 导致推深一级
}
```

EXCEPTION 不应该推深缩进（它应当与 BEGIN 同级别），WHEN 也应该与 EXCEPTION 同级别，WHEN THEN 内部的语句才推深一级。

### 12.3 修复方案

| 约束 | 位置 | indentDelta | 说明 |
|------|------|-------------|------|
| EXCEPTION → 第一个 token | `(exceptIdx, next)` | 0 | EXCEPTION 不改变缩进级别 |
| WHEN → WHEN THEN 语句 | `(whenTokenIdx, next)` | 1 | 仅 WHEN THEN 后语句推深一级 |
| `endAlign` 参数影响 | 当 `endAlign=true` 时 END 对齐块起始 | — | 另处理 |

---

## 13. FormatOptions 参数对接总览

当前已实现的 FormatOptions 参数与约束引擎对接情况：

### 13.1 已对接

| 参数 | 使用位置 | 值影响 |
|------|----------|--------|
| `indentSize` | `ConstraintSolver` | 缩进步长 |
| `maxLineWidth` | `ConstraintSolver` | DP 折行阈值 |
| `commentPreserve` | `StringAssembler` | 注释保留/剔除 |
| `keywordCase` | `PlSqlPostProcessor` | 关键字大小写 |
| `blankLineHandling` | `PlSqlPostProcessor` | 空行处理 |
| `trailingWhitespaceTrim` | `PlSqlPostProcessor` | 行尾空格 |
| `lineEnding` | `PlSqlPostProcessor` | 换行符 |
| `exceptionAlign` | `ConstraintGenerator` | EXCEPTION 对齐模式 |

### 13.2 待对接

所有参数通过 `ConstraintGenerator` 读取 `FormatOptions`，在各块类型对应生成器中调整 `GapConstraint` 属性。`ConstraintSolver` 和 `StringAssembler` 不读取参数（见 §13.3）。

GapConstraint 核心属性（定义见 §4.4）：
- `newlineMode`: FORBIDDEN / REQUIRED / OPTIONAL
- `indentDelta`: int（缩进增量，相对父块）
- `endAlign`: Boolean（true=端对齐至块起始列）
- `alignGroupId`: String（同组对齐）
- `breakPenalty`: double（0.0~1.0，DP 折行代价）
- `blankLineBefore`: boolean（换行前加空行，新增属性，见 §4.4 GapConstraint）

| 参数 | GapConstraint 映射 | 代码位置 | 优先级 |
|------|-------------------|----------|--------|
| `endAlign` (boolean) | endAlign=true → `gap(endToken).endAlign(true)`, 不使用 indentDelta | `IfCG` / `LoopCG` / `ProcedureCG` 等所有块类型的 END 间隙 | P1 |
| `thenOnNewLine` (boolean) | thenOnNewLine=true → `gap(condEnd, THEN).forceNewline(true).indentDelta(0)` | `IfConstraintGenerator` (THEN 前) | P1 |
| `elseOnNewLine` (boolean) | elseOnNewLine=true → `gap(prevBranchEnd, ELSE).forceNewline(true).indentDelta(0)`; ELSIF 同策略 | `IfConstraintGenerator` (ELSIF/ELSE 前) | P1 |
| `loopOnNewLine` (boolean) | loopOnNewLine=true → `gap(forCond, LOOP).forceNewline(true)`; WHILE 同 | `ForLoopCG` / `WhileLoopCG` / `BasicLoopCG` | P1 |
| `forLoopFormat` (COMPACT/EXPAND) | COMPACT → `gap(stmt, stmt).newlineMode(FORBIDDEN)` 相邻语句合并;<br> EXPAND → 每条语句独立 REQUIRED newline | `ForLoopCG` (FOR 循环体) | P2 |
| `caseExpressionFormat` (COMPACT/EXPAND) | COMPACT → `gap(WHEN x, THEN y).newlineMode(FORBIDDEN)`, WHEN 间 OPTIONAL;<br> EXPAND → `gap(prevWHEN, nextWHEN).forceNewline(true)`, THEN 前 OPTIONAL | `CaseExprCG` (§20.6) | P2 |
| `declarationAlign` (boolean) | true → 声明中 `:=` 前加入同一 `alignGroupId=“DECL_ASSIGN”` 对齐组; false → 普通空格 | `DeclareCG` (已实现) | ✅ |
| `parenthesisSpacing` (§13.4) | 按 ParenContextType 分别设置 `gap(lparen, rparen).preferredSpaces(0/1)` 及括号外间隙 | `ParenSpacingCG` (§13.4.3) | P1 |
| `blankLineBeforeBlock` (boolean) | true → `gap(prevStmtEnd, blockStart).blankLineBefore(true)` 施加于 FUNCTION/PROCEDURE/IF/LOOP/CASE 起始间隙 | `StructureCG` (所有顶级块生成器) | P2 |
| `continuousIndentSize` (int) | 作为 `ConstraintSolver` 全局默认 indentDelta, 在 DP 折行续行时使用;<br> 非块缩进，专用于断行续行:<br> `row.prefix(continuousIndentSize)` | `ConstraintSolver` (折行步骤) | P1 |
| `dmlJoinIndent` (boolean) | true → `gap(prevRel, JOIN).indentDelta(+1)`, 嵌套 JOIN 层级累加;<br> false → `gap(JOIN).indentDelta(0)` 与 FROM 同列 | `DmlSelectCG` (§11.5.2) | P1 |
| `dmlWhereAndPosition` (enum) | INDENTED → `gap(WHERE_kw, AND).indentDelta(+1)`; <br> SAME_LINE → `gap(AND).forceNewline(true).indentDelta(0)` (与 WHERE 同列);<br> OUTDENT → `gap(AND).forceNewline(true).indentDelta(-1)` (少一级) | `DmlWhereCG` (§11.5.3) | P1 |
| `selectColumnMode` (enum: COMPACT/ONE_PER_LINE/ALIGN) | COMPACT → `gap(col_i, col_i+1).newlineMode(OPTIONAL).breakPenalty(MAX)`; <br> COMPACT + selectColumnsPerRow=N → 每 N 列 `forceNewline(true)`; <br> ONE_PER_LINE → `gap(col_i, col_i+1).forceNewline(true).indentDelta(selIndent)`; <br> ALIGN → ONE_PER_LINE + `alignGroupId="SEL_COL"` 列名右对齐 | `DmlSelectCG` (§11.5.4) | P1 |
| `selectColumnsPerRow` (int) | selectColumnMode=COMPACT 时生效: N≥1 → 每 N 列 `gap(第N列, 第N+1列).forceNewline(true)`; <br> N=0 → 不强制换行 | `DmlSelectCG` (§11.5.4) | P1 |
| `commaPosition` (enum: TRAILING/LEADING) | TRAILING → `gap(expr, comma).newlineMode(FORBIDDEN)`, `gap(comma, nextExpr).newlineMode(OPTIONAL)`;<br> LEADING → `gap(comma, nextExpr).forceNewline(true)`, `gap(expr, comma).newlineMode(FORBIDDEN)` (逗号在行首) | `DmlSelectCG` / `ColumnListCG` (§11.5.5) | P1 |
| `dmlInClauseExpand` (enum: COMPACT/ONE_PER_LINE/MIXED) | COMPACT → `gap(value, comma).newlineMode(FORBIDDEN)`, 整组不换行;<br> ONE_PER_LINE → `gap(comma, nextValue).forceNewline(true).indentDelta(+1)`;<br> MIXED → 按 columnsPerRow 阈值切块，块内 FORBIDDEN, 块间 OPTIONAL | `InClauseCG` (§11.5.6) | P1 |
| `dmlSubqueryFormat` (enum: INLINE/EXPAND/AUTO) | INLINE → `gap(subqueryStartToken).newlineMode(FORBIDDEN)` 子查询内嵌不换行;<br> EXPAND → `gap(lparen, subStmt).forceNewline(true).indentDelta(+1)`, 子查询内递归生成约束;<br> AUTO → 按 tokens/estimatedLines 与 threshold 比较 | `SubqueryCG` (§11.5.7) | P1 |
| `dmlBulkCollectAlign` (boolean) | true → BULK COLLECT INTO 后变量列表接入 `alignGroupId=“BULK_INTO”`, 跨行对齐首变量 | `DmlSelectCG` (§11.5.8) | P1 |
| `dmlUsingAlign` (boolean) | true → USING/RETURNING INTO 后的表达式列表按 `alignGroupId=“DML_USING”` 对齐首元素 | `DmlExecuteCG` (§11.5.9) | P1 |
| `parameterPerLine` (boolean) | true → `gap(param_i, param_i+1).forceNewline(true).indentDelta(parenIndent)`;<br> false → `gap(param_i, comma).newlineMode(FORBIDDEN)`, `gap(comma, param_i+1).newlineMode(OPTIONAL)` | `ParamListCG` (§23.3) | P1 |
| `parameterAlignMode` (enum: COMPACT/ALIGNED) | COMPACT → 每个参数独立行，无对齐组;<br> ALIGNED → 参数名/方向/类型三列对齐，分别设 `alignGroupId=“PARAM_NAME”` / `“PARAM_DIR”` / `“PARAM_TYPE”` | `ParamListCG` (§23.4) | P1 |
| `parameterNameRightAlign` (boolean) | true → 在 ALIGNED 模式下参数名列右对齐 (gap 使用 `rightAlign` 约束);<br> false → 左对齐 | `ParamListCG` (§23.4) | P2 |
| `namedParameterAlign` (boolean) | true → `=>` 前建立 `alignGroupId=“NAMED_PARAM”`, 所有 `=>` 同列对齐;<br> false → `=>` 前 1 空格 | `NamedParamCG` (§21.5) | P1 |
| `typeMemberAlign` (boolean) | true → TYPE 字段声明 `fieldName type` 中 fieldName 列接入 `alignGroupId=“TYPE_FIELD”` 对齐 | `TypeDeclCG` (§18.3a) | P1 |
| `cursorSelectFormat` (boolean) | true → CURSOR 体内的 SELECT 语句作为 `DmlSelectCG` 子块递归格式化，生成完整 JOIN/WHERE/子查询约束 | `CursorCG` (§20.4) | P1 |
| `forallFormat` (boolean) | true → FORALL 的非 DML 部分缩进控制，`INDICES OF` / `LIMIT` 子句独立行;<br> `gap(forallHeader, dmlStmt).forceNewline(true).indentDelta(+1)` | `ForallCG` (§20.5) | P1 |
| `caseExprFormat` (enum: COMPACT/EXPAND) | COMPACT → `gap(WHEN, expr).newlineMode(FORBIDDEN)`, WHEN 间 `newlineMode(OPTIONAL).breakPenalty(0.9)`;<br> EXPAND → 每个 WHEN 前 `forceNewline(true)`, ELSE 同 | `CaseExprCG` (§20.6) | P1 |
| `dmlDeleteFromHandling` (enum: KEEP/REMOVE) | KEEP → `DELETE FROM` 原样输出，FROM 前 1 空格;<br> REMOVE → `gap(DELETE, FROM).newlineMode(FORBIDDEN)`, StringAssembler 跳过 FROM token 不输出 | `DmlDeleteCG` (§11.7.4) | P1 |
| `dmlReturningIntoStyle` (enum: SINGLE_LINE/ALIGNED/EXPAND) | SINGLE_LINE → `gap(RETURNING, into_clause).newlineMode(FORBIDDEN)`;<br> ALIGNED → `RETURNING` 后变量对齐 `alignGroupId=“RETURNING”`;<br> EXPAND → `gap(RETURNING, var1).forceNewline(true).indentDelta(+1)`, 每变量一行 | `DmlInsertCG` / `DmlUpdateCG` / `DmlDeleteCG` (§11.7.5) | P1 |

### 13.3 参数对接模式

每个参数的对接遵循统一模式：

```
FormatOptions 参数
    ↓
ConstraintGenerator 读取 → 按参数值调整 GapConstraint.newlineMode / indentDelta
    ↓
ConstraintSolver 不需修改（只解约束，不看参数）
    ↓
StringAssembler 不需修改（只装配，不看参数）
```

### 13.4 括号间距控制

#### 13.4.1 问题

括号间距（parenthesis spacing）控制 `(` 和 `)` 前后的空白。当前仅在 §13.2 中有一行参数定义（`parenthesisSpacing: NONE/INSIDE/BOTH`），无约束规则、无上下文区分、无 SQL 示例。

**DataGrip 做法**：提供多维度独立控制，涵盖不同括号上下文：

| 上下文 | 控制点 | DataGrip 参数示例 |
|--------|--------|-------------------|
| 函数调用 | `func(...)` vs `func (...) ` | 声明/调用括号间距 |
| 表达式分组 | `(a+b)` vs `( a+b )` | 表达式括号间距 |
| 控制结构 | `IF(cond)` vs `IF (cond)` | 控制结构括号间距 |
| 类型声明 | `NUMBER(10)` vs `NUMBER (10)` | 类型括号间距 |
| 参数列表 | `(a, b)` vs `( a, b )` | 参数括号间距 |
| 空括号 | `()` vs `( )` | 空括号间距 |
| 逗号 | `(a,b)` vs `(a, b)` | 逗号后空格 |

#### 13.4.2 上下文类型

```java
enum ParenContextType {
    FUNCTION_CALL,      // my_func(a, b) 或 my_func (a, b)
    EXPRESSION,         // (a + b) * c
    CONTROL_FLOW,       // IF (cond) / FOR (i IN ...) / WHILE (cond)
    TYPE_DECL,          // NUMBER(10,2) / VARCHAR2(100)
    PARAM_LIST,         // (p1 IN NUMBER, p2 OUT VARCHAR2)
    CONSTRUCTOR,        // t_emp_obj(1, 'John')
    ARRAY_INDEX,        // v_arr(5)
    EMPTY_PARENS,       // FUNCTION f RETURN NUMBER; 中的 ()
}
```

**判定策略**：通过 ParseTree 父节点类型区分括号用途：

| ParseTree 父节点 | ParenContextType |
|-----------------|-----------------|
| `function_call` | FUNCTION_CALL |
| `expr` 中的 `(` `)` | EXPRESSION |
| `if_statement` / `loop_statement` / `while_statement` 中的 `(condition)` | CONTROL_FLOW |
| `datatype` 中的 `(precision, scale)` | TYPE_DECL |
| `parameter_spec` 中的 `(param_list)` | PARAM_LIST |
| `function_body` / `procedure_body` 后的 `()` | EMPTY_PARENS |

#### 13.4.3 格式化约束

每个 `(` 和 `)` 的间隙由上下文类型 + 参数联合决定：

```
约束模型：每个 `(` 和 `)` 周围有 4 个间隙（gap）可控制

  ┌─ gapBeforeOpen ─┐                  ┌─ gapAfterClose ─┐
  func              (         a, b     )                  ;
                    └─ gapAfterOpen ┘  └─ gapBeforeClose ┘
```

| 间隙 | 控制参数 | 默认值 |
|------|---------|--------|
| gapBeforeOpen | `parenOpenSpacing` + 上下文 | 0（`(` 前无空格） |
| gapAfterOpen | `parenInsideSpacing` + 上下文 | 0（`(` 后无空格） |
| gapBeforeClose | `parenInsideSpacing` + 上下文 | 0（`)` 前无空格） |
| gapAfterClose | `parenCloseSpacing` + 上下文 | 0（`)` 后无空格） |

#### 13.4.4 参数定义

| 参数 | 类型 | 默认值 | 上下文范围 | 说明 |
|------|------|--------|-----------|------|
| `parenOpenSpacingFunc` | boolean | false | FUNCTION_CALL / CONSTRUCTOR | `func(` vs `func (` |
| `parenOpenSpacingControl` | boolean | true | CONTROL_FLOW | `IF(` vs `IF (` |
| `parenOpenSpacingType` | boolean | false | TYPE_DECL | `NUMBER(` vs `NUMBER (` |
| `parenInsideSpacingFunc` | boolean | false | FUNCTION_CALL / CONSTRUCTOR | `(a,b)` vs `( a,b )` |
| `parenInsideSpacingExpr` | boolean | false | EXPRESSION | `(a+b)` vs `( a+b )` |
| `parenInsideSpacingControl` | boolean | false | CONTROL_FLOW | `(cond)` vs `( cond )` |
| `parenInsideSpacingType` | boolean | false | TYPE_DECL | `(10)` vs `( 10 )` |
| `parenInsideSpacingParam` | boolean | false | PARAM_LIST | `(a,b)` vs `( a,b )` |
| `parenEmptyParens` | boolean | false | EMPTY_PARENS | `()` vs `( )` |
| `parenCommaSpacing` | boolean | true | 所有上下文 | `(a,b)` vs `(a, b)` |

#### 13.4.5 格式化示例

```sql
-- 全部默认（like DataGrip 默认）
SELECT func(a, b) FROM t WHERE (a + b) > 10;
IF (v_count > 0) THEN
    v_result := NUMBER(10, 2);
END IF;

-- parenOpenSpacingFunc=true
SELECT func (a, b) FROM t WHERE (a + b) > 10;

-- parenInsideSpacingFunc=true
SELECT func( a, b ) FROM t WHERE (a + b) > 10;

-- parenOpenSpacingControl=false
IF(v_count > 0) THEN
    FOR i IN 1..10 LOOP
        NULL;
    END LOOP;
END IF;

-- parenInsideSpacingExpr=true
SELECT func(a, b) FROM t WHERE ( a + b ) > 10;

-- parenEmptyParens=true
FUNCTION get_count RETURN NUMBER IS BEGIN RETURN 0; END;

-- parenCommaSpacing=false
SELECT func(a,b) FROM t;
```

#### 13.4.6 约束生成

```java
void addParenConstraints(PlSqlBlock block, FormatOptions opts) {
    for (int i = 0; i < tokens.size(); i++) {
        if (!isParenToken(tokens.get(i))) continue;

        ParenContextType ctx = detectParenContext(i);
        boolean isOpen = "(".equals(tokens.get(i).text);
        boolean isClose = ")".equals(tokens.get(i).text);

        if (isOpen) {
            // gapBeforeOpen: ( 前空格
            int prev = prevVisible(i - 1);
            if (prev >= 0 && isOpenParenEnabled(ctx, opts)) {
                addConstraint(gap(prev, i).preferredSpaces(1));
            }
            // gapAfterOpen: ( 后空格
            int next = nextVisible(i + 1);
            if (next >= 0 && next < tokens.size() && isInsideEnabled(ctx, opts) && !isCloseParen(tokens.get(next))) {
                addConstraint(gap(i, next).preferredSpaces(1));
            }
        }
        if (isClose) {
            // gapBeforeClose: ) 前空格
            int prev = prevVisible(i - 1);
            if (prev >= 0 && isInsideEnabled(ctx, opts) && !isOpenParen(tokens.get(prev))) {
                addConstraint(gap(prev, i).preferredSpaces(1));
            }
        }

        // commaSpacing: 逗号后空格（跨上下文通用）
        if (",".equals(tokens.get(i).text) && opts.isParenCommaSpacing()) {
            int next = nextVisible(i + 1);
            if (next >= 0 && isInsideParen(i)) {
                addConstraint(gap(i, next).preferredSpaces(1));
            }
        }

        // emptyParens: () 间距
        if (isOpen && i + 2 < tokens.size() && ")".equals(tokens.get(i + 2).text)) {
            if (opts.isParenEmptyParens()) {
                addConstraint(gap(i, i + 2).preferredSpaces(1));
            }
        }
    }
}
```

#### 13.4.7 特殊场景

**类型声明中的括号**：
```sql
-- parenInsideSpacingType=false（默认）
CREATE TABLE t (col NUMBER(10, 2));

-- parenInsideSpacingType=true
CREATE TABLE t (col NUMBER( 10, 2 ));
```

**数组索引**：
```sql
v_val := v_arr(5);       -- 索引括号无空格
v_val := v_arr (5);      -- parenOpenSpacingFunc=true 时
```

**空函数参数**：
```sql
FUNCTION get_time RETURN TIMESTAMP IS BEGIN RETURN SYSTIMESTAMP; END;
-- 空括号 () 不受 parenInsideSpacing 影响，仅受 parenEmptyParens 控制
```

#### 13.4.8 兼容性

旧参数 `parenthesisSpacing` (NONE/INSIDE/BOTH) 保留为快捷映射：

| `parenthesisSpacing` | 映射到新参数 |
|---------------------|-------------|
| NONE | 所有 paren*Spacing = false |
| INSIDE | `parenInsideSpacing*` = true（上下文统一），`parenOpenSpacing`/`parenCommaSpacing` = false |
| BOTH | `parenOpenSpacingControl` = true + `parenCommaSpacing` = true + 所有 `parenInsideSpacing*` = true |

---

## 14. 实施路线（修订版）

### Phase 1-2：已完成

约束引擎核心框架已上线：块结构缩进、operator FORBIDDEN、`:=` 对齐、EG 断行、PostProcessor 管道。

### Phase 3：格式调整（当前阶段）

| 子任务 | 涉及文件 | 预期效果 |
|--------|---------|----------|
| 3.1 `;` 前 FORBIDDEN | `ConstraintGenerator.generate()` | `;` 不再单独成行 |
| 3.2 `\|\|` 后低惩罚断行 | `ConstraintGenerator.computeBreakPenalty()` | 拼接链在 `\|\|` 后优雅换行 |
| 3.3 DML 关键字断行 | `ConstraintGenerator` 新增 `addDmlConstraints()` | FROM/WHERE/ORDER 前 OPTIONAL 断行 |
| 3.4 EXCEPTION WHEN 缩进修正 | `ConstraintGenerator.walkBlock()` WHEN 处理 | WHEN 与 EXCEPTION 同级别 |
| 3.5 ELSE/ELSIF 新行 | `ConstraintGenerator.walkIfBlock()` | 支持 `elseOnNewLine` 参数 |
| 3.6 END 对齐 | `ConstraintGenerator.walkBlock()` | 支持 `endAlign` 参数 |
| 3.7 移除 `applyStatementFormatting` | `PlSqlFormatterEngine` | 删除桥接逻辑，消除重排序/`||` 拆分 |

### Phase 4：集成 + 测试

- DemoTest 回归 + QualityChecker 验证
- 对比 DataGrip 输出样

---

## 15. 风险

| 风险 | 程度 | 缓解 |
|------|------|------|
| `;` 前 FORBIDDEN 与行宽 DP 冲突（超长行无法断）| 低 | DP 在 `;` 之前的 OPTIONAL 间隙断行，而非 `;` 前。`;` 的 FORBIDDEN 仅保护 `;` 自身这一格间隙 |
| DML 关键字 OPTIONAL 断行与 `indentDelta` 冲突 | 低 | DML 断行只改变 breakPenalty，不改变 indentDelta，缩进由块结构决定 |
| EXCEPTION WHEN 修复影响回归 | 低 | 回归测试覆盖所有块类型 |
| ANTLR ParseTree 节点→tokenIdx 获取不够直观 | 低 | `TokenStream.getTokenIndex(ctx.start)` 可用 |
| PL/SQL 语法歧义导致部分 parse 失败 | 中 | ANTLR 默认错误恢复 + `isComplete` fallback |
| 方言差异 (MySQL/PG 无 PACKAGE) | 低 | ParseTree 中没有对应节点就不创建对应 block |
| 性能下降 | 低 | 对 10000 行包体做基准测试 |

---

## 16. `/` 斜杠分隔符

### 16.1 问题

SQL\*Plus / SQLcl 中 `/` 作为独立命令执行最近编译的 PL/SQL 块。格式化后 `/` 必须单独成行，不能与任何 token 同列。

```
-- 当前输出（错）
END demo_pkg ;
/ CREATE OR REPLACE PACKAGE BODY demo_pkg IS

-- 期望
END demo_pkg ;
/
CREATE OR REPLACE PACKAGE BODY demo_pkg IS
```

### 16.2 根因

`addSemicolonGapsInBlock` 中 `endTokenIdx` 范围判断 `en <= end` 过滤了 `;` 后到 `/` 的间隙约束。

```java
// addSemicolonGapsInBlock 现有逻辑
int end = Math.min(block.endTokenIdx, tokens.size() - 1);
int en = nextVisible(i + 1);
if (en <= end && en < tokens.size()) {  // en > end 时被跳过
    requireNewline(i, en, 0);
}
```

若 `;` 为块的 `endTokenIdx`（如 PACKAGE_SPEC/PACKAGE_BODY），其后 `/` 的 nextVisible 超出块范围，不会生成约束。

### 16.3 方案

在 `generate()` 末尾全局扫描 `/` 字符，对其前一个 channel-0 token 施加 REQUIRED newline：

```java
// 伪代码：generate() 末尾追加
for (int i = 0; i < tokens.size(); i++) {
    if ("/".equals(tokens.get(i).text) && tokens.get(i).channel == 0) {
        int prev = prevVisible(i - 1);
        if (prev >= 0 && ";".equals(tokens.get(prev).text)) {
            requireNewline(prev, i, 0);  // ; 到 / 必须换行
        }
    }
}
```

---

## 17. PostProcessor 修复

### 17.1 问题清单

| 问题 | 现状 | 修复方案 |
|------|------|----------|
| `@formatter:off/on` 保护不完整 | `protectComments` 插入 `FMT_PROTECTED` 占位符但永不恢复 | 所有处理完成后执行 `restoreProtected()`，扫描占位符替换回原始内容 |
| 关键词大小写覆盖标识符 | `applyKeywordCase` 对 `line.split()` 中**所有**字母单词都做转换 | 维护 PL/SQL 关键字集（约 300 词），仅命中关键字集的单词转换大小写 |
| CRLF 换行符转换错误 | `replace('\n','\r').replace("\r\r","\r")` → `\r\n` 变成 `\r` | 改为 `replace("\r\n", "\n").replace("\n", le)` |

### 17.2 关键字集方案

格式：`PlSqlPostProcessor` 内置 `Set<String> KEYWORDS`（大写），`convertLine()` 中检查 `word.toUpperCase()` 是否在集合内，是才转换：

```java
private static final Set<String> KEYWORDS = Set.of(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES",
    "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "INDEX",
    "ALTER", "DROP", "BEGIN", "END", "DECLARE", "EXCEPTION",
    "IF", "THEN", "ELSE", "ELSIF", "END IF",
    "LOOP", "END LOOP", "FOR", "WHILE", "EXIT", "CONTINUE",
    "CASE", "WHEN", "END CASE", "RETURN", "PRAGMA", "RAISE",
    "OPEN", "FETCH", "CLOSE", "PIPE", "ROW",
    "COMMIT", "ROLLBACK", "SAVEPOINT",
    "FUNCTION", "PROCEDURE", "PACKAGE", "PACKAGE BODY",
    "TRIGGER", "TYPE", "TYPE BODY",
    "IS", "AS", "OF", "REF", "RETURN",
    "NOT", "NULL", "DEFAULT", "PRIMARY", "KEY",
    "FOREIGN", "REFERENCES", "CONSTRAINT", "CHECK",
    "UNIQUE", "CASCADE", "ON", "DELETE", "UPDATE",
    "CONNECT", "BY", "START", "WITH", "ORDER",
    "GROUP", "HAVING", "UNION", "MINUS", "INTERSECT",
    "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "JOIN",
    "LIKE", "BETWEEN", "IN", "EXISTS", "ALL", "ANY",
    "DISTINCT", "UNIQUE", "AS", "AT"
    // 完整集合参考 Oracle SQL/PLSQL 关键字列表
);
```

### 17.3 保护恢复流程

```
protectComments(input)     ← 插入占位符
    ↓
applyKeywordCase(...)      ← 转换过程无视占位符内容
    ↓
handleBlankLines(...)      ← 压缩空行不触碰占位符
    ↓
trimTrailingWhitespace(...)← 清理行尾空格
    ↓
normalizeLineEnding(...)   ← 换行符统一
    ↓
restoreProtected(output)   ← 将占位符替换回原始 @formatter:off ... @formatter:on 文本
```

### 17.4 注释格式化

#### 17.4.1 问题

PL/SQL Developer / DataGrip 中，注释不仅是代码的说明文字，其格式化结果直接影响代码可读性。
当前设计仅通过 `@formatter:off/on` 保护注释内容不被修改（§17.3），但未定义注释本身的格式化策略。

**DataGrip 注释格式化维度**：

| 维度 | DataGrip 控制 |
|------|--------------|
| 行尾注释对齐 | 右对齐到固定列，或与周围注释列对齐 |
| 单行 `--` 注释缩进 | 与代码同级别缩进 / 独立缩进 |
| `--` 后空格 | `--comment` vs `-- comment` |
| 块注释 `/* */` 风格 | `/* text */` 单行 vs `/*\n * text\n */` 多行 |
| 块注释星号对齐 | 多行块注释中 `*` 列对齐 |
| 文档注释 `/** */` | 保护 `@param`/`@return` 等标签格式 |
| 注释前空行 | 注释前可选加空行 |
| 注释位置偏好 | 语句前单独一行 vs 语句尾部 |

#### 17.4.2 注释类型

`ConstraintGenerator` / `PostProcessor` 识别三种注释类型：

```java
enum CommentType {
    LINE_COMMENT,     // -- 单行注释
    BLOCK_COMMENT,    // /* ... */ 块注释
    DOC_COMMENT       // /** ... */ 文档注释
}
```

判定方式（基于 Token channel 和 text）：

| 标记 | 类型 |
|------|------|
| Token text 以 `--` 开头 | LINE_COMMENT |
| Token text 以 `/*` 开头且非 `/**` | BLOCK_COMMENT |
| Token text 以 `/**` 开头 | DOC_COMMENT |

#### 17.4.3 注释缩进策略

##### 策略模式

```sql
-- CODE_LEVEL（与代码同级别 — DataGrip 默认）
DECLARE
    v_count NUMBER;
    -- 初始化计数器
    v_count := 0;
    -- 循环处理
    FOR i IN 1..v_count LOOP
        NULL;
    END LOOP;
END;

-- FIXED（固定列缩进 — 如列 0）
DECLARE
    v_count NUMBER;
-- 初始化计数器
    v_count := 0;
-- 循环处理
    FOR i IN 1..v_count LOOP
        NULL;
    END LOOP;
END;

-- RELATIVE（相对当前缩进偏移 N 格）
DECLARE
    v_count NUMBER;
      -- 初始化计数器（偏移 2 格）
    v_count := 0;
      -- 循环处理
    FOR i IN 1..v_count LOOP
        NULL;
    END LOOP;
END;
```

##### 约束规则

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `commentIndentMode` | enum | CODE_LEVEL | 缩进模式：CODE_LEVEL / FIXED / RELATIVE |
| `commentIndentFixed` | int | 0 | FIXED 模式的绝对缩进列数 |
| `commentIndentRelative` | int | 0 | RELATIVE 模式的相对偏移格数 |
| `commentIndentBlock` | boolean | true | 块注释 `/* */` 是否跟随缩进策略 |

**约束生成**：
```java
void applyCommentIndent(PlSqlBlock block, FormatOptions opts) {
    CommentIndentMode mode = opts.getCommentIndentMode();
    for (int i = 0; i < tokens.size(); i++) {
        if (!isCommentToken(tokens.get(i))) continue;

        int prevVisible = prevVisible(i - 1);
        int indent = 0;

        switch (mode) {
            case CODE_LEVEL:
                indent = computeBlockIndent(i);  // 由块结构决定
                break;
            case FIXED:
                indent = opts.getCommentIndentFixed();
                break;
            case RELATIVE:
                indent = computeBlockIndent(i) + opts.getCommentIndentRelative();
                break;
        }

        if (prevVisible >= 0) {
            addConstraint(gap(prevVisible, i)
                .forceNewline(true).indent(indent));
        }
    }
}
```

#### 17.4.4 行尾注释对齐

##### 对齐模式

```sql
-- 不对齐
SELECT employee_id,  -- ID
       first_name,   -- first name
       last_name,    -- last
       long_column_name  -- long column
FROM employees;

-- 右对齐到同一列（ALIGN）
SELECT employee_id,       -- ID
       first_name,        -- first name
       last_name,         -- last
       long_column_name   -- long column
FROM employees;

-- 对齐到最远代码列 + 间距（FIXED_GAP）
SELECT employee_id,  -- ID
       first_name,   -- first name
       last_name,    -- last
       long_column_name  -- long column
FROM employees;
```

##### 约束策略

```java
enum TrailingCommentAlign {
    NONE,        // 不对齐，按原有空格
    ALIGN,       // 所有行尾注释右对齐到同一列
    FIXED_GAP    // 注释与行末代码之间保持固定间距
}
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `trailingCommentAlign` | enum | NONE | 行尾注释对齐模式 |
| `trailingCommentMinSpaces` | int | 2 | 行尾注释与代码间最小空格（FIXED_GAP 模式） |
| `trailingCommentMaxCol` | int | 80 | 行尾注释不超出的最大列号 |

**实现方式**：
```java
void alignTrailingComments(PlSqlBlock block, FormatOptions opts) {
    if (opts.getTrailingCommentAlign() == NONE) return;

    // 收集连续行中的行尾注释组
    List<List<TokenLine>> groups = groupConsecutiveLinesWithTrailingComments(block);

    for (List<TokenLine> group : groups) {
        // 找到组内最远的代码结束列
        int maxCodeEnd = group.stream()
            .mapToInt(line -> line.getLastCodeToken().column + line.getLastCodeToken().text.length())
            .max().orElse(0);

        int targetColumn = maxCodeEnd + opts.getTrailingCommentMinSpaces();

        if (opts.getTrailingCommentAlign() == ALIGN) {
            // 所有注释定位到同一 targetColumn
            for (TokenLine line : group) {
                int commentIdx = line.getCommentTokenIndex();
                GapConstraint g = gap(line.getLastCodeIdx(), commentIdx);
                g.preferredSpaces = targetColumn - line.getLastCodeCol();
                g.minSpaces = opts.getTrailingCommentMinSpaces();
            }
        }
        // FIXED_GAP: 每行各自保持最小间距
    }
}
```

#### 17.4.5 单行注释 `--` 格式化

##### `--` 后空格

```sql
-- 无空格（SPACE=NONE）
--comment without space

-- 一个空格（SPACE=SINGLE — 默认）
-- comment with one space

-- 两个空格（SPACE=DOUBLE）
--  comment with two spaces
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `lineCommentSpaceMode` | enum | SINGLE | `--` 后空格：NONE/SINGLE/DOUBLE |

##### `--` 注释折行

超长单行注释自动在单词边界折行：

```sql
-- 折行前
-- This is a very long comment that exceeds the maximum line width and should be wrapped to multiple lines for readability

-- 折行后
-- This is a very long comment that exceeds the maximum line width
-- and should be wrapped to multiple lines for readability
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `lineCommentWrap` | boolean | false | 单行注释超长时折行 |
| `lineCommentWrapIndent` | int | 2 | 折行续行缩进格数 |

#### 17.4.6 块注释 `/* */` 格式化

##### 单行 vs 多行

```sql
-- 单行（SINGLE_LINE）— 内容较短时
/* This is a short comment */

-- 多行（MULTI_LINE）— 内容较长或含换行
/*
 * This is a long comment
 * that spans multiple lines
 */

-- 紧凑多行（COMPACT）
/*
This is a compact multi-line comment
without leading asterisks
*/
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `blockCommentStyle` | enum | SINGLE_LINE | 块注释风格：SINGLE_LINE / MULTI_LINE / COMPACT |
| `blockCommentAsterisk` | boolean | true | 多行模式下每行前缀 `*` 对齐 |
| `blockCommentMinLines` | int | 3 | 超过此行数自动转为 MULTI_LINE |

##### 星号对齐

```sql
/*
** 不对齐
*    对齐
*/
```

当 `blockCommentAsterisk=true` 时，多行注释中的 `*` 右对齐到 `/*` 的下一列：

```java
/*
 * 第一行
 * 第二行
 * 最后一行
 */
```

**约束规则**：每行 `*` 前插入一个空格，`*` 与 `/*` 的 `*` 列对齐。

#### 17.4.7 文档注释 `/** */` 保护

文档注释（PL/SDoc 风格）通常具有结构化标签：

```sql
/**
 * Calculate employee bonus based on performance
 * @param p_emp_id    Employee ID
 * @param p_perf_score Performance score (0-100)
 * @param p_year      Bonus year
 * @return Calculated bonus amount
 */
FUNCTION calc_bonus(p_emp_id NUMBER, p_perf_score NUMBER, p_year NUMBER)
    RETURN NUMBER IS
```

**约束策略**：
- `/**` 和 `*/` 的对齐规则同 §17.4.6 块注释
- `@param` / `@return` / `@throws` / `@see` 等标签保持行首对齐
- 标签间可加空行（保留）
- 参数名 `p_emp_id` 可建立 AlignGroup 对齐到组内最大宽度

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `docCommentFormat` | boolean | true | 文档注释格式化 |
| `docCommentTagAlign` | boolean | true | `@param` 等标签参数对齐 |

#### 17.4.8 注释位置策略

注释可以放在语句前（单独一行）或语句尾：

```sql
-- 语句前（BEFORE — DataGrip 默认）
-- 查询员工信息
SELECT employee_id, first_name
FROM employees;

-- 语句尾（TRAILING）
SELECT employee_id, first_name  -- 查询员工信息
FROM employees;
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `commentPlacement` | enum | BEFORE | 注释位置偏好：BEFORE / TRAILING / KEEP |

**约束策略**：
- `BEFORE`：注释与后接语句之间 `REQUIRED newline`，语句前不留空行（可选）
- `TRAILING`：注释移动到语句尾部，与尾 token 保持 `trailingCommentMinSpaces` 间距
- `KEEP`：保持原始位置不变

#### 17.4.9 注释前空行

```sql
-- commentIndentBlankLine=true（默认）
SELECT 1 FROM dual;

-- 下一段注释前有空行
-- This is a new section
SELECT 2 FROM dual;

-- commentIndentBlankLine=false
SELECT 1 FROM dual;
-- This is a new section — 无空行
SELECT 2 FROM dual;
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `commentBlankLineBefore` | boolean | false | 注释前加空行（非代码前一行的注释） |

#### 17.4.10 新增参数汇总

| 参数 | 类型 | 默认值 | 说明 | 参考 |
|------|------|--------|------|------|
| `commentIndentMode` | enum | CODE_LEVEL | 注释缩进模式 | §17.4.3 |
| `commentIndentFixed` | int | 0 | FIXED 模式绝对缩进列 | §17.4.3 |
| `commentIndentRelative` | int | 0 | RELATIVE 模式偏移格数 | §17.4.3 |
| `trailingCommentAlign` | enum | NONE | 行尾注释对齐模式 | §17.4.4 |
| `trailingCommentMinSpaces` | int | 2 | 行尾注释最小间距 | §17.4.4 |
| `trailingCommentMaxCol` | int | 80 | 行尾注释最大列号 | §17.4.4 |
| `lineCommentSpaceMode` | enum | SINGLE | `--` 后空格数 | §17.4.5 |
| `lineCommentWrap` | boolean | false | 单行注释折行 | §17.4.5 |
| `lineCommentWrapIndent` | int | 2 | 注释折行续行缩进 | §17.4.5 |
| `blockCommentStyle` | enum | SINGLE_LINE | 块注释风格 | §17.4.6 |
| `blockCommentAsterisk` | boolean | true | 多行 `*` 对齐 | §17.4.6 |
| `blockCommentMinLines` | int | 3 | 自动多行阈值 | §17.4.6 |
| `docCommentFormat` | boolean | true | 文档注释格式化 | §17.4.7 |
| `docCommentTagAlign` | boolean | true | `@param` 标签对齐 | §17.4.7 |
| `commentPlacement` | enum | BEFORE | 注释位置偏好 | §17.4.8 |
| `commentBlankLineBefore` | boolean | false | 注释前空行 | §17.4.9 |

---

## 18. 未实现的块类型

### 18.1 类型定义对照

`PlSqlBlockType` 定义了 18 种，设计和实现覆盖情况：

| 块类型 | 文档提及 | 约束实现 | 状态 |
|--------|---------|----------|------|
| PACKAGE_SPEC | §4.2 | `walkBlock` | ✅ |
| PACKAGE_BODY | §4.2 | `walkBlock` | ✅ |
| PROCEDURE | §4.2 | `walkBlock` | ✅ |
| FUNCTION | §4.2 | `walkBlock` | ✅ |
| ANON_BLOCK | §4.2 | `walkBlock` | ✅ |
| IF | §4.2 | `walkIfBlock` | ✅ |
| CASE_BLOCK | §4.2 | `walkCaseBlock` | ✅ |
| FOR_LOOP | §4.2 (loop) | `walkLoopBlock` | ✅ |
| WHILE_LOOP | §4.2 (loop) | `walkLoopBlock` | ✅ |
| LOOP | 缺 | `walkLoopBlock` | ✅ 代码有，文档缺 |
| TYPE_SPEC | 缺 | 缺 | ⬜ |
| TYPE_BODY | 缺 | 缺 | ⬜ |
| TRIGGER | 缺 | 缺 | ⬜ |
| REPEAT_LOOP | 缺 | 缺 | ⬜ |

### 18.2 LOOP 块（补文档）

`walkLoopBlock` 已实现：

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

### 18.3 TYPE_SPEC / TYPE_BODY

**语法示例**：
```sql
CREATE OR REPLACE TYPE t_emp_obj AS OBJECT (
    emp_id    NUMBER,
    emp_name  VARCHAR2(100),
    MEMBER FUNCTION get_info RETURN VARCHAR2
);

CREATE OR REPLACE TYPE BODY t_emp_obj AS
    MEMBER FUNCTION get_info RETURN VARCHAR2 IS
    BEGIN
        RETURN emp_id || ' - ' || emp_name;
    END;
END;
```

**约束策略**（与 PACKAGE 类似）：

| 位置 | 约束 | 说明 |
|------|------|------|
| `IS` / `AS` → 成员声明 | `requireNewline(headerEnd, next, 1)` | 推一级缩进 |
| 成员声明内部 | 按 FUNCTION/PROCEDURE 子块处理 | 复用 `walkBlock` |
| `BEGIN` → 语句 | `requireNewline(beginIdx, next, 1)` | 语句区缩进 |
| `END` 前 | `addEndConstraint`（`findPrevSemicolon` pop） | 缩进弹出 |

### 18.3a TYPE_SPEC / TYPE_BODY 成员对齐

#### 字段声明对齐

RECORD / TABLE 类型的字段声明应支持对齐：

```sql
-- 期望输出
CREATE OR REPLACE TYPE t_emp_rec AS OBJECT (
    emp_id      NUMBER(6),
    emp_name    VARCHAR2(100),
    hire_date   DATE,
    salary      NUMBER(8,2),
    is_active   BOOLEAN DEFAULT TRUE
);
```

**约束策略**：字段列表以 `(` 开头、`)` 结束。字段间逗号分隔，每个字段独占一行（EXPAND 模式）。
字段名与类型之间空格对齐到组内最大字段名宽度。

| 位置 | 约束 | 说明 |
|------|------|------|
| `(` 后 → 第一个字段 | `requireNewline(openParen, next, 1)` | 推一级缩进 |
| 字段逗号 → 下一个字段 | `forceNewline(true).indentDelta(0)` | 每行一个字段 |
| 最后一个字段 → `)` | `requireNewline(prev, closeParen, -1)` | 弹出括号缩进 |
| 字段名间空格 | AlignGroup 对齐到最大宽度 | 类型声明列对齐 |

#### MEMBER / STATIC 函数声明

```sql
-- 期望输出
CREATE OR REPLACE TYPE t_emp_obj AS OBJECT (
    emp_id      NUMBER,
    MEMBER FUNCTION get_salary RETURN NUMBER,
    MEMBER PROCEDURE set_salary(p_salary NUMBER),
    STATIC FUNCTION create_emp(p_name VARCHAR2) RETURN t_emp_obj,
    CONSTRUCTOR FUNCTION t_emp_obj(p_id NUMBER) RETURN SELF AS RESULT,
    MAP MEMBER FUNCTION to_string RETURN VARCHAR2,
    ORDER MEMBER FUNCTION compare(other t_emp_obj) RETURN NUMBER
);
```

**约束策略**：MEMBER/STATIC/CONSTRUCTOR 函数声明的缩进与字段声明同级别。
每个声明独占一行。参数列表内部格式化复用 §23 的参数字格式化规则。

#### MAP/ORDER 成员函数

MAP 和 ORDER 成员函数是 Oracle 对象类型的特殊成员，`MAP MEMBER FUNCTION` 和 `ORDER MEMBER FUNCTION`
被视为独立的声明行，`MAP`/`ORDER` 关键字前一个 token 到该函数间不换行（FORBIDDEN）。

#### TYPE BODY 内部

```sql
-- 期望输出
CREATE OR REPLACE TYPE BODY t_emp_obj AS
    MEMBER FUNCTION get_salary RETURN NUMBER IS
    BEGIN
        RETURN SELF.salary;
    END;

    STATIC FUNCTION create_emp(p_name VARCHAR2) RETURN t_emp_obj IS
        v_emp t_emp_obj := t_emp_obj(0, p_name, SYSDATE, 0);
    BEGIN
        RETURN v_emp;
    END;
END;
```

成员函数体复用 `walkBlock` 流程（§22.1），与 PACKAGE BODY 的成员函数处理一致。

### 18.4 TRIGGER

**语法示例**：
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

**约束策略**：

| 位置 | 约束 | 说明 |
|------|------|------|
| 触发器头部各子句 | `FOR EACH ROW` 等单独行或紧凑，由 FormatOptions 控制 | `triggerFormat` 参数 |
| `DECLARE` → 声明 | `requireNewline(declStart, next, 1)` | 声明缩进 |
| `BEGIN` → 语句 | `requireNewline(beginIdx, next, 1)` | 语句缩进 |
| `EXCEPTION` → WHEN | 同 §12 策略 | 弹出缩进 |
| `END` 前 | `addEndConstraint` | 缩进弹出 |

**walkTriggerBlock 实现**：

```java
private void walkTriggerBlock(PlSqlBlock block) {
    // === 1. 触发器头部 ===
    // 从 CREATE TRIGGER name 到 DECLARE/BEGIN 之间的子句
    int headerEnd = findTriggerHeaderEnd(block);  // 定位 DECLARE 或 BEGIN
    if (headerEnd < 0) { addEndConstraint(block); return; }

    String triggerFormat = opts.getTriggerFormat(); // COMPACT / EXPAND
    boolean expand = "EXPAND".equals(triggerFormat);

    // 头部各子句：BEFORE/AFTER/INSTEAD OF、ON table、FOR EACH ROW、WHEN、FOLLOWS、REFERENCING
    int tok = block.startTokenIdx;
    while (tok < headerEnd) {
        int next = nextVisible(tok + 1);
        if (next >= headerEnd) break;

        if (isTriggerClauseStart(tokens.get(next))) {
            // 遇到子句起始关键字 (BEFORE/AFTER/FOR/WHEN/FOLLOWS/REFERENCING)
            GapConstraint g = gap(tok, next);
            if (expand) {
                g.forceNewline(true).indentDelta(1);   // EXPAND: 每子句独立行，缩进一级
            } else {
                g.newlineMode(OPTIONAL).breakPenalty(0.8); // COMPACT: 尽量同行
            }
            out.add(g);
        }
        tok = next;
    }

    // === 2. DECLARE / BEGIN / EXCEPTION / END ===

    // DECLARE 段（可选）
    int declIdx = block.declStartIdx;
    if (declIdx >= 0) {
        int next = nextVisible(declIdx + 1);
        if (next >= 0 && next < block.bodyStartIdx) {
            requireNewline(declIdx, next, 1);   // DECLARE 后缩进
        }
    }

    // BEGIN → 语句体
    int beginIdx = block.bodyStartIdx;
    if (beginIdx >= 0) {
        int next = nextVisible(beginIdx + 1);
        if (next >= 0 && next < tokens.size()) {
            requireNewline(beginIdx, next, 1);  // BEGIN 后缩进
        }
    }

    // EXCEPTION → WHEN（同 §12）
    int exceptIdx = block.exceptionStartIdx;
    if (exceptIdx >= 0) {
        int next = nextVisible(exceptIdx + 1);
        if (next >= 0) {
            GapConstraint g = gap(exceptIdx, next);
            g.forceNewline(true).indentDelta(0); // EXCEPTION 与 BEGIN 同级别
            out.add(g);
        }
    }

    // END − 缩进弹出
    addEndConstraint(block);
}

/** 检测 token 是否为触发器子句起始关键字 */
private boolean isTriggerClauseStart(TokenInfo t) {
    switch (t.upper) {
        case "BEFORE": case "AFTER": case "INSTEAD":
        case "ON": case "FOR": case "WHEN":
        case "FOLLOWS": case "PRECEDES":
        case "REFERENCING": case "ENABLE": case "DISABLE":
            return true;
        default:
            return false;
    }
}

/** 定位触发器头部的结束位置（DECLARE 或 BEGIN 之前） */
private int findTriggerHeaderEnd(PlSqlBlock block) {
    // 由 ParseTree 提供：DECLARE 或 BEGIN 所在 token 索引
    if (block.declStartIdx >= 0) return block.declStartIdx - 1;
    if (block.bodyStartIdx >= 0) return block.bodyStartIdx - 1;
    return block.endTokenIdx - 1;
}
```

**TRIGGER 参数**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `triggerFormat` | enum | EXPAND | 头部子句格式：COMPACT(一行)/EXPAND(每子句一行) |
| `triggerHeaderClauseOrder` | boolean | true | 强制 Oracle 标准子句顺序 |

### 18.5 REPEAT_LOOP

Oracle PL/SQL 原生不支持 REPEAT-UNTIL，但 ANSI SQL 标准中定义的 `REPEAT` 可在自定义语法子集中出现。约束策略与 `WHILE_LOOP` 一致，`UNTIL` 关键字等价于 `LOOP` 的结束边界。

---

## 19. 标签 `<<label>>`

### 19.1 标签语法

PL/SQL 允许在块和循环前加标签，用于 `EXIT` / `CONTINUE` / `GOTO` 跳转：

```sql
<<process_employees>>
DECLARE
    v_total NUMBER := 0;
BEGIN
    <<emp_loop>>
    FOR r IN (SELECT * FROM employees) LOOP
        v_total := v_total + r.salary;
        EXIT emp_loop WHEN r.salary IS NULL;
    END LOOP emp_loop;
END process_employees;
```

### 19.2 标签格式化规则

| 规则 | 说明 |
|------|------|
| `<<label>>` 单独一行，缩进与后接块/循环同级别 | 标签前换行，后换行 |
| 标签内容 `<<` 与 `>>` 之间不加空格 | FORBIDDEN 约束 |
| `END loop_label` 对齐（若标签存在） | 标签名对齐，与 endAlign 一致 |
| 标签前空行（由 `blankLineBeforeBlock` 控制） | 可配 |

### 19.3 约束生成

```java
// 伪代码：walkBlock 中检测标签
for (int i = block.startTokenIdx - 1; i >= 0; i--) {
    TokenInfo ti = tokens.get(i);
    if (ti.channel == 0 && "<<".equals(ti.text)) {
        int labelStart = i;
        // 标签 << 前换行
        requireNewline(prevVisible(labelStart - 1), labelStart, 0);
        // 标签后（>> 后）换行
        int labelEnd = findLabelEnd(labelStart);
        int kw = nextVisible(labelEnd + 1);
        if (kw >= 0) requireNewline(labelEnd, kw, 0);
        break;
    }
}
```

### 19.4 GOTO 与标签

`GOTO label_name;` 语句的 `GOTO` 前换行，保持与同级语句对齐。标签目标不要额外缩进。

---

## 20. 特殊 PL/SQL 语句格式化

### 20.1 语句类型总览

`Statement.Type` 定义了 26 种，但当前约束引擎只处理了块结构（IF/LOOP/CASE 通过块树遍历），其余语句的格式化策略如下：

| 语句类型 | 格式化策略 | 优先级 |
|----------|-----------|--------|
| `ASSIGNMENT` | 赋值 `:=` 左对齐（declarationAlign），右表达式正常缩进 | P1 |
| `CONCATENATION` | 拼接链在 `\|\|` 后优先换行（§9.3） | P1 |
| `DML` | (同普通语句，由约束引擎基本间距处理) | — |
| `EXECUTE_IMMEDIATE` | `EXECUTE IMMEDIATE` 保留一行，超长时在 `\|\|` 后换行 | P1 |
| `RETURN` | `RETURN` 前换行，表达式不断行或随 `\|\|` 换行 | P2 |
| `EXIT` / `CONTINUE` | `EXIT label WHEN condition;` 保持一行 | P2 |
| `RAISE` | `RAISE [exception_name];` 保持一行 | P2 |
| `PRAGMA` | 保持一行，注释除外 | P2 |
| `PIPE ROW` | `PIPE ROW(expr);` 保持一行 | P2 |
| `OPEN / FETCH / CLOSE` | 游标操作，`FOR` 和 `USING` 子句换行可选 | P2 |
| `COMMIT / ROLLBACK` | `COMMIT;` / `ROLLBACK [TO name];` 保持一行 | P2 |
| `NULL_STMT` | `NULL;` 保持一行 | P2 |
| `GOTO` | `GOTO label;` 保持一行 | P2 |
| `PROC_CALL` | 过程调用 `proc_name(args);` 保持一行或参数换行 | P2 |
| `BLOCK_STMT` | 已通过 `innerBlock` 块树处理 | — |

### 20.2 实现模式

所有特殊语句的格式化均通过 `ConstraintGenerator` 中的 OPTIONAL 约束和 breakPenalty 驱动，不单独写语句格式化器：

```
检测 Statement.startTokenIdx → Statement.endTokenIdx 范围
    ↓
扫描范围内关键字位置
    ↓
对应位置施加 breakPenalty / FORBIDDEN / REQUIRED
    ↓
(复用同一套 Solver + Assembler)
```

### 20.3 COLLECTION 方法调用

集合方法（`COUNT`, `FIRST`, `LAST`, `EXISTS`, `EXTEND`, `DELETE`, `TRIM`, `LIMIT`, `PRIOR`, `NEXT`）前为 `.` token：

```sql
v_count := employees.COUNT;
v_first := employees.FIRST;
IF employees.EXISTS(5) THEN ...
```

`addForbiddenOperatorConstraints` 规则已覆盖 `.` + identifier，保证 `.COUNT` 不被拆开。

### 20.4 CURSOR 声明格式化

#### 语法示例

```sql
-- 无参游标
CURSOR c_emp IS
    SELECT e.*, d.department_name
    FROM employees e
    JOIN departments d ON e.department_id = d.department_id;

-- 带参游标
CURSOR c_emp_dept(p_dept_id NUMBER, p_status VARCHAR2 DEFAULT 'ACTIVE') IS
    SELECT employee_id, first_name, last_name, salary
    FROM employees
    WHERE department_id = p_dept_id
      AND status = p_status;
```

#### 格式化约束

| 位置 | 约束 | 说明 |
|------|------|------|
| `CURSOR name (...)` → `IS` | `IS` 前空格 `preferredSpaces=1` | 保持同一行 |
| `IS` → `SELECT` 第一个 token | `requireNewline(isIdx, selectIdx, 1)` | SELECT 推一级缩进 |
| CURSOR 参数列表 `(...)` | 复用 §23 参数格式化规则 | 参数名/类型对齐 |
| `RETURN` 子句（若存在） | RETURN 前 `OPTIONAL + breakPenalty=0.3` | `CURSOR c RETURN type_name;` |
| SELECT 内部格式化 | 复用 §11.5 DML 子句树规则 | JOIN/WHERE/列清单等 |

#### `cursorSelectFormat` 参数

| 值 | 效果 |
|----|------|
| `true` | CURSOR 内 SELECT 完整格式化（JOIN 缩进、WHERE 对齐等） |
| `false` | CURSOR 内 SELECT 保持为一行或仅按 DP 断行 |

### 20.5 FORALL 语句格式化

#### 语法示例

```sql
-- 基本 FORALL
FORALL i IN 1..v_tab.COUNT
    UPDATE employees SET salary = salary * 1.1
    WHERE employee_id = v_tab(i).employee_id;

-- FORALL WITH SAVE EXCEPTIONS
FORALL i IN 1..v_ids.COUNT SAVE EXCEPTIONS
    INSERT INTO log_table VALUES (v_ids(i), SYSDATE, 'PROCESSED');

-- FORALL INDICES OF / VALUES OF（集合方法）
FORALL i IN INDICES OF v_tab BETWEEN 1 AND 10
    DELETE FROM employees WHERE employee_id = v_tab(i);
```

#### 格式化约束

| 位置 | 约束 | 说明 |
|------|------|------|
| `FORALL` 头部换行 | `FORALL i IN ...` 与 `SAVE EXCEPTIONS` 在同一行 | FORBIDDEN 保护 |
| `FORALL` → DML 语句 | `requireNewline(endOfHeader, dmlStart, 1)` | DML 推一级缩进 |
| DML 内部格式化 | 复用 §11.5 规则 | UPDATE/INSERT/DELETE 子句 |
| `SAVE EXCEPTIONS` | 前加空格 `preferredSpaces=1` | 与 FORALL 头部同行 |

### 20.6 CASE 表达式格式化

#### 问题

CASE 表达式（不同于 §22.2 的 CASE 语句 `CASE_BLOCK`）在赋值和表达式中出现：

```sql
-- CASE 表达式
v_status := CASE
    WHEN v_count > 100 THEN 'HIGH'
    WHEN v_count > 0 THEN 'MEDIUM'
    WHEN v_count = 0 THEN 'EMPTY'
    ELSE 'UNKNOWN'
END;

-- 作为函数参数
my_func(CASE WHEN x > 0 THEN x ELSE 0 END);
```

#### 与 CASE 语句的区别

| 维度 | CASE 语句 (§22.2) | CASE 表达式 |
|------|-------------------|------------|
| 结束方式 | `END CASE;` | `END` + 无关键字 |
| 上下文 | 独立语句 | 表达式（赋值/参数/条件） |
| 缩进弹出 | `END CASE` → 弹出 | `END` → 随外层缩进 |
| 紧凑模式 | 可紧凑可展开 | COMPACT 模式保持一行 |

#### 约束策略

| 模式 | 效果 |
|------|------|
| `COMPACT` | 整个 CASE 表达式保持在一行：`CASE WHEN ... THEN ... ELSE ... END` |
| `EXPAND` | 每个 WHEN/ELSE 独占一行，行首缩进一级 |

```java
// 伪代码：CASE 表达式检测与约束
void walkCaseExpr(PlSqlBlock block, FormatOptions opts) {
    int caseIdx = tokenIndex(block.getToken(CASE));
    int endIdx = tokenIndex(block.getToken(END));

    if (opts.getCaseExprFormat() == COMPACT) {
        // 所有间隙 FORBIDDEN 换行
        for (int i = caseIdx; i < endIdx; i++) {
            if (tokenAt(i).channel == 0) {
                addConstraint(gap(i, i+1).forceNewline(false));
            }
        }
    } else { // EXPAND
        // WHEN/ELSE 前换行，THEN 后保持
        for (WhenClause wc : block.whenClauses) {
            addConstraint(gap(prevVisible(wc.whenIdx), wc.whenIdx)
                .forceNewline(true).indentDelta(1));
            addConstraint(gap(wc.thenIdx, nextVisible(wc.thenIdx + 1))
                .forceNewline(false));  // THEN 后不换行
        }
        // ELSE 前换行
        if (block.elseIdx >= 0) {
            addConstraint(gap(prevVisible(block.elseIdx), block.elseIdx)
                .forceNewline(true).indentDelta(1));
        }
    }
}
```

### 20.7 SUBTYPE 声明

```sql
SUBTYPE name IS base_type;
-- SUBTYPE name IS base_type NOT NULL;
```

**约束策略**：保持一行，FORBIDDEN 所有间隙换行。

### 20.8 PRAGMA 格式化细化

```sql
PRAGMA AUTONOMOUS_TRANSACTION;
PRAGMA EXCEPTION_INIT(exception_name, -20001);
PRAGMA SERIALLY_REUSABLE;
PRAGMA INLINE(function_name, 'YES');
PRAGMA UDF;
PRAGMA DEPRECATE(procedure_name, 'Use new_proc instead');
PRAGMA RESTRICT_REFERENCES(default, RNDS, WNDS, RNPS, WNPS);
```

| PRAGMA 类型 | 约束策略 |
|-------------|----------|
| 无参 PRAGMA（AUTONOMOUS_TRANSACTION / SERIALLY_REUSABLE / UDF） | 保持一行 |
| 有参 PRAGMA（EXCEPTION_INIT / INLINE / DEPRECATE / RESTRICT_REFERENCES） | 括号内参数紧凑保持一行。`(` 和 `)` 内部不换行，逗号后空格保留 |

### 20.9 集合运算符

```sql
v_result := v_collection1 MULTISET UNION v_collection2;
v_result := v_collection1 MULTISET UNION ALL v_collection2;
v_result := v_collection1 MULTISET INTERSECT v_collection2;
v_result := v_collection1 MULTISET EXCEPT v_collection2;
```

**约束策略**：`MULTISET` 与 `UNION`/`INTERSECT`/`EXCEPT` 间 FORBIDDEN 换行，
运算符前后空格 `preferredSpaces=1`。超长时在运算符前 OPTIONAL 换行。

---

## 21. 非 PL/SQL 指令透传

### 21.1 需要透传的场景

SQL\*Plus / SQLcl / sqlcmd 命令和非 PL/SQL 语句不应被格式化引擎修改：

```sql
SET SERVEROUTPUT ON
SET DEFINE OFF
ALTER SESSION SET NLS_LANG = 'AMERICAN'

VARIABLE v_ref REFCURSOR

SELECT * FROM dual;         ← 独立 DML，走 SqlFormatter

CREATE OR REPLACE ...       ← PL/SQL，走约束引擎
```

### 21.2 透传策略

| 命令类型 | 处理方式 | 说明 |
|----------|---------|------|
| `SET xxx xxx` | 原样保留 | 跳过约束生成 |
| `VARIABLE xxx` | 原样保留 | 跳过约束生成 |
| `COLUMN xxx FORMAT xxx` | 原样保留 | 跳过约束生成 |
| `ALTER SESSION ...` | 原样保留 | 跳过约束生成 |
| `ALTER SYSTEM ...` | 原样保留 | 跳过约束生成 |
| 独立 DML / DDL | 交由 `SqlFormatter` 处理 | 独立语句层 |
| PL/SQL 块 | 约束引擎处理 | 主流程 |

### 21.3 判定方式

ANTLR ParseTree 中 `sql_script → (unit_statement ';'?)* EOF`，每个 `unit_statement` 的类型由 parser 规则确定：

- 无法匹配任何规则的 token 序列 → 标记为 `UNKNOWN`
- `UNKNOWN` 区块原样输出，不经过约束引擎

### 21.4 `%` 和 `.` 属性标记

已在 `addForbiddenOperatorConstraints` 中实现：

| 模式 | 规则 | 示例 |
|------|------|------|
| `%TYPE` | `.` 后 `TYPE`/`ROWTYPE` 零空格 | `emp%TYPE` |
| `%.` | `.` 与标识符之间零空格 | `employees.department_id` |
| `标识符.` | 标识符后 `.` 零空格 | `v_rec.emp_name` |

### 21.5 命名参数关联符号 `=>`

#### 语法场景

Oracle PL/SQL 支持命名参数传递，使用 `=>` 关联参数名与实参：

```sql
-- 过程调用
my_procedure(p_param1 => value1, p_param2 => value2);

-- 函数调用
v_result := my_func(p_name => 'John', p_age => 30, p_active => TRUE);

-- 对象构造函数
v_emp := t_emp_obj(emp_id => 100, emp_name => 'John');
```

#### `=>` 的 Token 解析

ANTLR 词法规则中 `=>` 被解析为两个独立 token：
- `ASSOCIATION_OPERATOR: '=';` → 单个 `=`
- `RIGHT_ARROW: '>';` → 单个 `>`

因此 token 流中 `=>` 表现为两个相邻 channel-0 token：`=` + `>`。必须保证不被拆开。

#### 格式化约束

| 约束 | 策略 |
|------|------|
| `=` 与 `>` 之间 | FORBIDDEN 换行，空格 0 |
| `=>` 前后 | 前后各 1 格空格 |
| 多参数对齐 | `FormatOptions.namedParameterAlign` 控制 |

#### 对齐模式

```sql
-- 不对齐（紧凑）
my_func(p_param1 => value1, p_param2 => value2, p_param3 => value3);

-- 参数每行一个 + => 对齐
my_func(
    p_param1 => value1,
    p_param2 => value2,
    p_param3 => value3
);

-- 参数每行一个 + 参数名右对齐
my_func(
    p_param1 => value1,
    p_param2 => value2,
    p_param3 => value3
);
```

#### 约束生成

```java
// 伪代码：=> 约束
void addNamedParamConstraints(PlSqlBlock block) {
    for (int i = 0; i < tokens.size() - 1; i++) {
        if ("=".equals(tokens.get(i).text) && ">".equals(tokens.get(i+1).text)) {
            // = 与 > 之间零空格
            addConstraint(gap(i, i+1).forceNewline(false).spaces(0, 0, 0));
            // 参数名与 = 之间不允许换行
            addConstraint(gap(prevVisible(i-1), i).forceNewline(false).preferredSpaces(1));
            // > 与值之间不允许换行
            int valIdx = nextVisible(i+2);
            if (valIdx >= 0) {
                addConstraint(gap(i+1, valIdx).forceNewline(false).preferredSpaces(1));
            }
        }
    }
}
```

#### 与函数调用参数格式化的协同

`=>` 格式化与 §23 参数声明格式化共享参数列表对齐框架：
- `parameterPerLine=true` 时，命名参数调用也每行一个
- `namedParameterAlign=true` 时，`=>` 所在列对齐
- `parameterNameRightAlign=true` 时，参数名右对齐到 `=>`

---

## 22. 块类型约束实现总表

### 22.1 `walkBlock` 通用流程

```
walkBlock(block)
├── addSemicolonGapsInBlock(block)     ← ; → newline
├── walkHeader(block)                  ← IS/BEGIN/EXCEPTION 约束
├── children dispatch:
│   ├── FUNCTION / PROCEDURE → walkBlock (递归)
│   ├── IF                 → walkIfBlock
│   ├── LOOP / FOR / WHILE → walkLoopBlock
│   ├── CASE               → walkCaseBlock
│   ├── TYPE_SPEC / TYPE_BODY → walkTypeBlock (待实现)
│   └── TRIGGER            → walkTriggerBlock (待实现)
└── popIndentAtBlockEnd(block)         ← ; 后缩进弹出
```

### 22.2 各块类型约束汇总

| 块类型 | 约束生成方法 | indentDelta 来源 |
|--------|-------------|-----------------|
| PACKAGE_SPEC | `walkBlock` | IS→+1, END→-1 |
| PACKAGE_BODY | `walkBlock` | IS→+1, BEGIN→+1, EXCEPTION→0, END→-1 |
| FUNCTION | `walkBlock` | IS→+1, BEGIN→+1, EXCEPTION→0, END→-1 |
| PROCEDURE | `walkBlock` | IS→+1, BEGIN→+1, EXCEPTION→0, END→-1 |
| ANON_BLOCK | `walkBlock` | (DECLARE|BEGIN)→+1, EXCEPTION→0, END→-1 |
| IF | `walkIfBlock` | THEN→+1, ELSIF→-1+1, ELSE→0, END→-1 |
| CASE_BLOCK | `walkCaseBlock` | WHEN THEN→+1, ELSE→0, END→-1 |
| LOOP / FOR / WHILE | `walkLoopBlock` | LOOP→+1, END→-1 |
| TYPE_SPEC | `walkTypeBlock` | IS/AS→+1, END→-1 |
| TYPE_BODY | `walkTypeBlock` | IS/AS→+1, BEGIN→+1, END→-1 |
| TRIGGER | `walkTriggerBlock` | DECLARE→+1, BEGIN→+1, EXCEPTION→0, END→-1 |

### 22.3 新增约束方法注册

在 `ConstraintGenerator.generate()` 中增加：

```java
private void walkBlock(PlSqlBlock block, int parentIndent) {
    // ... 现有 dispatch 逻辑 ...

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
        || block.type == PlSqlBlockType.WHILE_LOOP) { walkLoopBlock(block); return; }

    // 通用 walkBlock 逻辑（PACKAGE / FUNCTION / PROCEDURE / ANON_BLOCK）
    // ...
}

---

## 23. 参数声明格式化

### 23.1 适用场景

函数和过程的参数声明需要细致的格式化控制：

```sql
-- 未格式化
CREATE OR REPLACE PROCEDURE update_employee(p_emp_id IN NUMBER, p_first_name IN VARCHAR2 DEFAULT NULL, p_last_name IN VARCHAR2, p_salary IN NUMBER DEFAULT 0, p_commit IN BOOLEAN DEFAULT TRUE) IS
```

### 23.2 DataGrip 参数格式化风格

```sql
-- 每行一个参数（推荐）
CREATE OR REPLACE PROCEDURE update_employee(
    p_emp_id      IN            NUMBER,
    p_first_name  IN            VARCHAR2 DEFAULT NULL,
    p_last_name   IN            VARCHAR2,
    p_salary      IN            NUMBER DEFAULT 0,
    p_commit      IN            BOOLEAN DEFAULT TRUE
) IS

-- 紧凑格式
CREATE OR REPLACE PROCEDURE update_employee(p_emp_id IN NUMBER, p_first_name IN VARCHAR2 DEFAULT NULL, p_last_name IN VARCHAR2, p_salary IN NUMBER DEFAULT 0, p_commit IN BOOLEAN DEFAULT TRUE) IS
```

### 23.3 格式化约束

| 约束位置 | 约束 | 说明 |
|----------|------|------|
| `(` 后 → 第一个参数 | `requireNewline(openParen, firstParam, 1)` | 推一级缩进 |
| 参数逗号 → 下一个参数 | `forceNewline(true).indentDelta(0)` | 每行一个参数 |
| 最后一个参数 → `)` | `requireNewline(lastParam, closeParen, -1)` | 弹出括号缩进 |
| 参数名 → `IN`/`OUT`/`IN OUT` 方向 | 空格对齐到组内最大参数名宽度 | AlignGroup |
| 方向 → 类型名 | 空格对齐到组内最大方向宽度 | AlignGroup |
| 类型 → `DEFAULT` / `:=` | 空格对齐到组内最大类型宽度 | AlignGroup |

### 23.4 参数对齐模式

#### 完全对齐（ALIGNED）

```sql
CREATE OR REPLACE PROCEDURE update_employee(
    p_emp_id      IN            NUMBER,
    p_first_name  IN            VARCHAR2 DEFAULT NULL,
    p_last_name   IN            VARCHAR2,
    p_salary      IN            NUMBER DEFAULT 0,
    p_commit      IN            BOOLEAN DEFAULT TRUE
)
```

三列对齐：
1. 参数名右对齐（由 `parameterNameRightAlign` 控制）
2. 方向（IN/OUT/IN OUT/NOCCOPY）居中左对齐
3. 类型 + DEFAULT 左对齐

#### 紧凑对齐（COMPACT）

```sql
CREATE OR REPLACE PROCEDURE update_employee(
    p_emp_id IN NUMBER,
    p_first_name IN VARCHAR2 DEFAULT NULL,
    p_last_name IN VARCHAR2,
    p_salary IN NUMBER DEFAULT 0,
    p_commit IN BOOLEAN DEFAULT TRUE
)
```

仅参数名和类型间一个空格，方向与类型名间一个空格。

### 23.5 参数格式化参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `parameterPerLine` | boolean | false | 每个参数独占一行 |
| `parameterAlignMode` | enum | COMPACT | ALIGNED/COMPACT |
| `parameterNameRightAlign` | boolean | false | 参数名右对齐（仅 ALIGNED 模式） |
| `parameterDirectionAlign` | boolean | true | IN/OUT 方向列对齐 |
| `parameterDefaultAlign` | boolean | true | DEFAULT 列对齐 |
| `parameterParenthesisSpace` | boolean | false | `(` 后和 `)` 前加空格 |

### 23.6 实现位置

参数格式化实现在 `ConstraintGenerator` 中的 `walkParameterList(PlSqlBlock parent)` 方法，
检测 `(` 到匹配 `)` 的范围，识别参数分隔逗号并施加约束。

与 TYPE_SPEC/TYPE_BODY 的成员声明对齐共享 AlignGroup 基础设施。

---

## 24. 其余 PL/SQL 语句与子句补充

### 24.1 SAVEPOINT 变体

```sql
SAVEPOINT sp_name;
SAVEPOINT sp_name FOR UPDATE;
```

保持一行，FORBIDDEN 所有间隙换行。

### 24.2 COMMIT / ROLLBACK 变体

```sql
COMMIT;
COMMIT WRITE BATCH NOWAIT;
COMMIT COMMENT 'commit comment';

ROLLBACK;
ROLLBACK TO sp_name;
ROLLBACK WORK;
```

**约束策略**：COMMIT/ROLLBACK 及其变体（WRITE/COMMENT/FORCE 等）保持一行。
WRITE 子句内各选项间无换行。`TO sp_name` 中 `TO` 前 1 格空格，`TO` 与 `sp_name` 间 1 格空格。

### 24.3 LOG ERRORS 子句

DML 语句的 error logging 子句（可出现在 INSERT/UPDATE/MERGE/DELETE 中）：

```sql
INSERT INTO target_table
SELECT * FROM source_table
LOG ERRORS INTO err_log_table ('batch_1') REJECT LIMIT UNLIMITED;
```

**约束策略**：`LOG ERRORS INTO` 前施加 `OPTIONAL + breakPenalty=0.3`，续行缩进一级。

### 24.4 ACCESSIBLE BY 子句

Oracle 12c+ 新增单元可见性控制：

```sql
CREATE OR REPLACE PACKAGE pkg_internal IS
    FUNCTION public_api RETURN NUMBER;
    PRAGMA accessible_by (PROCEDURE pkg_outer.public_proc);
END;
```

**约束策略**：`ACCESSIBLE BY` / `ACCESSIBLE BY (PROCEDURE ...)` 保持一行。

### 24.5 DEFAULT COLLATION 子句

```sql
CREATE OR REPLACE PACKAGE pkg DEFAULT COLLATION USING_NLS_COMP IS
    ...
END;
```

**约束策略**：`DEFAULT COLLATION ...` 子句保持与 `IS`/`AS` 同行。

### 24.6 XML 类型函数

PL/SQL 中常见的 XML 处理：

```sql
v_xml := XMLTYPE('<root><item id="1"/></root>');
v_xml := XMLPARSE(DOCUMENT '<root><item/></root>');
v_xml := XMLSERIALIZE(CONTENT v_xml_data AS VARCHAR2(200));
```

**约束策略**：函数名与括号间零空格（FORBIDDEN）。括号内参数按逗号分隔保持一行或 OPTIONAL 换行。

### 24.7 JSON 函数

```sql
v_json := JSON_OBJECT('key1' VALUE val1, 'key2' VALUE val2);
v_json := JSON_ARRAY(val1, val2, val3);
v_json := JSON_OBJECTAGG('key' VALUE val);
```

**约束策略**：函数名与括号间零空格（FORBIDDEN）。`VALUE`/`KEY` 关键字前 1 格空格。
JSON 对象键值对超长时在逗号后 OPTIONAL 换行。

### 24.8 DBMS_SQL 模式识别

DBMS_SQL 是 Oracle 动态 SQL 的核心包，格式化时应识别常见模式：

```sql
v_cursor := DBMS_SQL.OPEN_CURSOR;
DBMS_SQL.PARSE(v_cursor, v_sql, DBMS_SQL.NATIVE);
DBMS_SQL.BIND_VARIABLE(v_cursor, ':p1', v_param1);
v_result := DBMS_SQL.EXECUTE(v_cursor);
DBMS_SQL.CLOSE_CURSOR(v_cursor);
```

**约束策略**：`DBMS_SQL.` 与后续函数名之间的 `.` 加 FORBIDDEN 约束。
函数调用参数按 §23 规则格式化。每条 DBMS_SQL 调用独占一行。
```

---

## 25. DDL 格式化策略

### 25.1 问题背景

当前设计将独立 DDL 交由 `SqlFormatter`（模板引擎）处理（§21.2），块内 DDL 未单独讨论。
但 DDL 的格式化复杂度和特殊性远超通用 SQL 模板引擎能处理的范畴。

**DataGrip 做法**：DDL 拥有独立的格式化规则集，与 DML 完全不同：
- 列定义支持多列对齐（列名/类型/NULL/约束/DEFAULT）
- 表级约束（OUT-OF-LINE）与列级约束（INLINE）区别处理
- 分区子句、存储子句、TABLESPACE 等有独立缩进规则
- ALTER TABLE 的 ADD/MODIFY/DROP 各变体有不同的格式化期望

### 25.1a DDL 块类型枚举

独立 DDL 语句在解析阶段归类为 `STATEMENT` 块，但其子类型通过 `PlSqlBlockType` 扩展区分：

```java
public enum PlSqlBlockType {
    // ... 已有 PL/SQL 块类型（§22）...

    // === DDL 类型（v2 新增）===
    DDL_CREATE_TABLE,           // CREATE TABLE [IF NOT EXISTS]
    DDL_CREATE_INDEX,           // CREATE [UNIQUE/BITMAP/CLUSTER] INDEX
    DDL_CREATE_VIEW,            // CREATE [OR REPLACE] VIEW
    DDL_CREATE_MVIEW,           // CREATE MATERIALIZED VIEW
    DDL_CREATE_MVIEW_LOG,       // CREATE MATERIALIZED VIEW LOG
    DDL_CREATE_TABLESPACE,      // CREATE [BIGFILE/SMALLFILE] TABLESPACE
    DDL_CREATE_USER,            // CREATE USER
    DDL_CREATE_ROLE,            // CREATE ROLE
    DDL_CREATE_PROFILE,         // CREATE PROFILE
    DDL_CREATE_DIRECTORY,       // CREATE DIRECTORY
    DDL_CREATE_SEQUENCE,        // CREATE SEQUENCE
    DDL_CREATE_SYNONYM,         // CREATE [PUBLIC] SYNONYM
    DDL_CREATE_DATABASE_LINK,   // CREATE DATABASE LINK
    DDL_CREATE_TYPE,            // CREATE [OR REPLACE] TYPE (非 PL/SQL 对象类型)
    DDL_ALTER_TABLE,            // ALTER TABLE
    DDL_ALTER_INDEX,            // ALTER INDEX
    DDL_ALTER_VIEW,             // ALTER VIEW
    DDL_ALTER_TABLESPACE,       // ALTER TABLESPACE
    DDL_ALTER_USER,             // ALTER USER
    DDL_ALTER_SEQUENCE,         // ALTER SEQUENCE
    DDL_ALTER_SESSION,          // ALTER SESSION (非严格 DDL, 但格式化规则同)
    DDL_DROP_TABLE,             // DROP TABLE
    DDL_DROP_INDEX,             // DROP INDEX
    DDL_DROP_VIEW,              // DROP VIEW
    DDL_DROP_SEQUENCE,          // DROP SEQUENCE
    DDL_TRUNCATE,               // TRUNCATE TABLE
    DDL_RENAME,                 // RENAME old TO new
    DDL_COMMENT,                // COMMENT ON TABLE/COLUMN
    DDL_GRANT,                  // GRANT
    DDL_REVOKE,                 // REVOKE
    DDL_FLASHBACK,              // FLASHBACK TABLE
    DDL_PURGE,                  // PURGE
    DDL_ANALYZE,                // ANALYZE TABLE/INDEX
}
```

**识别规则**：`ParseTreeModelBuilder` 在构建 `PlSqlModel` 时，遇到 `CREATE` / `ALTER` / `DROP` / `TRUNCATE` / `RENAME` / `COMMENT` / `GRANT` / `REVOKE` 起始的独立语句，通过 `SqlTypeClassifier`（复用 §21.2 模板引擎分类逻辑）判定具体 DDL 子类型，创建 `PlSqlBlock(type=DDL_xxx)`。

```
token序列: CREATE TABLE employees ( ... ) ;
              ↓
SqlTypeClassifier.classify() → SqlType.DDL_CREATE_TABLE
              ↓
PlSqlBlock(type=DDL_CREATE_TABLE, content="CREATE TABLE employees (...) ...")
              ↓
ConstraintGenerator → walkCreateTable(block)
```

### 25.2 整体设计原则

```
DDL 格式化分两种路径：

独立 DDL ──────────→ 约束引擎 DDL 子模块（新增）
   (CREATE TABLE/INDEX/VIEW/ALTER 等)

块内 DDL ──────────→ 约束引擎通用路径 + DDL 子模块标记
   (EXECUTE IMMEDIATE 中的 CREATE/ALTER 字符串)
```

> **设计决策**：独立 DDL 不再抛给 `SqlFormatter` 模板引擎，而是由约束引擎的 DDL 子模块处理。
> 与 §11.5 的"子查询委托给模板引擎"不同，DDL 的格式化规则（列对齐、约束布局等）与约束引擎的
> AlignGroup 模型天然契合，无需引入第二引擎。

### 25.3 CREATE TABLE — 列定义格式化

#### 25.3.1 列定义对齐

```sql
-- 不对齐（紧凑）
CREATE TABLE employees (
    employee_id NUMBER(6) NOT NULL,
    first_name VARCHAR2(20) NOT NULL,
    hire_date DATE,
    salary NUMBER(8,2)
);

-- 完全对齐（DataGrip 风格）
CREATE TABLE employees (
    employee_id    NUMBER(6)        NOT NULL,
    first_name     VARCHAR2(20)     NOT NULL,
    last_name      VARCHAR2(25)     NOT NULL,
    email          VARCHAR2(25)     NOT NULL,
    hire_date      DATE,
    salary         NUMBER(8,2),
    commission_pct NUMBER(2,2),
    manager_id     NUMBER(6),
    department_id  NUMBER(4)
);
```

#### 25.3.2 格式化约束

| 约束位置 | 约束 | 说明 |
|----------|------|------|
| `(` 后 → 第一列 | `requireNewline(openParen, firstCol, 1)` | 推一级缩进 |
| 列逗号 → 下一列 | `forceNewline(true).indentDelta(0)` | 每列独占一行 |
| 最后一列 → `)` | `requireNewline(lastCol, closeParen, -1)` | 弹出括号缩进 |
| 列名右对齐 | AlignGroup: 组内最大列名宽度 | `columnNameRightAlign=true` |
| 类型声明左对齐 | AlignGroup: 类型列从列名列+N格后对齐 | |
| NOT NULL / NULL | 类型列后对齐到组内最大类型宽度 | |
| DEFAULT value | DEFAULT 列后对齐到组内最大约束宽度 | |
| 列级约束 | 列定义末尾保持同一行（INLINE 风格） | |

#### 25.3.3 列定义约束参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `columnDefPerLine` | boolean | true | 每列独占一行 |
| `columnNameRightAlign` | boolean | false | 列名右对齐 |
| `columnTypeAlign` | boolean | true | 类型声明列对齐 |
| `columnNullAlign` | boolean | true | NOT NULL/NULL 列对齐 |
| `columnDefaultAlign` | boolean | true | DEFAULT 列对齐 |
| `columnCommentInline` | boolean | false | 列注释 `/* ... */` 行内尾随（vs 独立 COMMENT ON） |
| `columnIdentityAlign` | boolean | true | Identity 列 `GENERATED ... AS IDENTITY` 对齐到约束列 |

#### 25.3.4 特殊列类型

##### 虚拟列（Virtual Column）

```sql
CREATE TABLE employees (
    employee_id    NUMBER(6),
    first_name     VARCHAR2(20),
    last_name      VARCHAR2(25),
    full_name      VARCHAR2(46) GENERATED ALWAYS AS (first_name || ' ' || last_name) VIRTUAL,
    salary         NUMBER(8,2),
    commission_pct NUMBER(2,2),
    total_comp     NUMBER(8,2) GENERATED ALWAYS AS (salary + NVL(commission_pct, 0) * salary)
);
```

| 位置 | 约束 |
|------|------|
| `GENERATED ALWAYS AS` | 与列类型同行，前 1 空格 |
| `(expr)` | 括号内原始保留（虚拟表达式复杂时由 breakPenalty 折行） |
| `VIRTUAL` / `STORED` | 表达式 `)` 后 1 空格，保持同行 |

##### Identity 列

```sql
CREATE TABLE employees (
    employee_id    NUMBER(6)
        GENERATED BY DEFAULT AS IDENTITY (START WITH 1000 INCREMENT BY 1),
    -- 或 ALWAYS
    employee_id    NUMBER(6)
        GENERATED ALWAYS AS IDENTITY,
    -- 带完整序列选项
    employee_id    NUMBER(6)
        GENERATED BY DEFAULT ON NULL AS IDENTITY
        (START WITH 1000 INCREMENT BY 1 MAXVALUE 99999 CYCLE)
);
```

**约束策略**：
- Identity 子句换行缩进（列名/类型/IDENTITY 三列对齐模式下 indentDelta=1）
- 括号内序列选项：`START WITH` / `INCREMENT BY` / `MAXVALUE` / `MINVALUE` / `CYCLE` / `NOCYCLE` / `CACHE` / `NOCACHE`
  每项紧凑或跨行，由 `ddlSequencePerLine` 控制（同 §25.10 SEQUENCE 选项规则）

##### LOB 列存储

```sql
CREATE TABLE documents (
    doc_id       NUMBER(6),
    doc_content  CLOB,
    doc_metadata CLOB
)
LOB (doc_content, doc_metadata) STORE AS (
    TABLESPACE  lob_data
    STORAGE (INITIAL 64K NEXT 32K)
    CHUNK       8192
    ENABLE STORAGE IN ROW
);
```

| 位置 | 约束 |
|------|------|
| `LOB (columns)` | 列名 `,` 后 1 空格，无换行 |
| `STORE AS` | `)` 后 OPTIONAL 换行，缩进一级 |
| `TABLESPACE` / `STORAGE` / `CHUNK` / `ENABLE STORAGE IN ROW` | 每子句独立行 |
| `(col1, col2)` 多列 | 列名列表紧凑 |

##### 集合类型列（Nested Table / VARRAY）

```sql
CREATE TABLE project_team (
    project_id    NUMBER(6),
    team_members  employee_tab_t
)
NESTED TABLE team_members STORE AS team_members_nt;
```

集合类型列名与其类型同行（同普通列），`NESTED TABLE ... STORE AS` 换行独立。

##### 列注释内联

```sql
CREATE TABLE employees (
    employee_id    NUMBER(6)     /* 员工ID */,
    first_name     VARCHAR2(20)  /* 名 */,
    last_name      VARCHAR2(25)  /* 姓 */
);
```

`columnCommentInline=true` 时，注释右对齐到列对齐组的最大宽度后，以 `/* ... */` 形式内联尾随。
`columnCommentInline=false`（默认）时，注释作为独立 `COMMENT ON COLUMN` 语句（§25.10）。

### 25.4 约束格式化（CONSTRAINT）

#### 25.4.1 列级约束 vs 表级约束

```sql
-- 列级约束（INLINE — 与列定义同行）
CREATE TABLE t (
    emp_id  NUMBER(6) CONSTRAINT pk_t PRIMARY KEY,
    dept_id NUMBER(4) CONSTRAINT fk_t REFERENCES departments(dept_id)
);

-- 表级约束（OUT-OF-LINE — 列定义后独占一行）
CREATE TABLE t (
    emp_id    NUMBER(6),
    dept_id   NUMBER(4),
    hire_date DATE,
    CONSTRAINT pk_t PRIMARY KEY (emp_id),
    CONSTRAINT fk_t FOREIGN KEY (dept_id)
        REFERENCES departments(dept_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_salary CHECK (salary > 0),
    CONSTRAINT uq_email UNIQUE (email)
);
```

**约束策略**：
- 列级约束：保持与列定义同行，不换行（FORBIDDEN）
- 表级约束：OUT-OF-LINE 约束前换行，`CONSTRAINT` 关键字对齐缩进

#### 25.4.2 复合约束子句换行

```sql
-- 多列/复合约束换行
ALTER TABLE employees ADD CONSTRAINT fk_emp_dept
    FOREIGN KEY (department_id, manager_id)
    REFERENCES departments(department_id, manager_id)
    ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED;
```

| 子句 | breakPenalty | indentDelta |
|------|-------------|-------------|
| `FOREIGN KEY (...)` | 0.3 | 1 |
| `REFERENCES ...(...)` | 0.3 | 1 |
| `ON DELETE CASCADE/SET NULL` | 0.3 | 1 |
| `ON UPDATE CASCADE` | 0.3 | 1 |
| `DEFERRABLE / INITIALLY` | 0.2 | 1 |
| `ENABLE / DISABLE` | 0.2 | 1 |
| `VALIDATE / NOVALIDATE` | 0.2 | 1 |
| `USING INDEX (...)` | 0.3 | 1 |

#### 25.4.3 约束命名对齐

表级约束的 `CONSTRAINT` 关键字列对齐到同表内最大约束名宽度：

```sql
-- 对齐前
CREATE TABLE t (
    col1 NUMBER,
    CONSTRAINT pk_t PRIMARY KEY (col1),
    CONSTRAINT fk_t FOREIGN KEY (col2) REFERENCES t2(col2),
    CONSTRAINT ck_t CHECK (col3 > 0)
);

-- 对齐后
CREATE TABLE t (
    col1    NUMBER,
    CONSTRAINT pk_t  PRIMARY KEY (col1),
    CONSTRAINT fk_t  FOREIGN KEY (col2) REFERENCES t2(col2),
    CONSTRAINT ck_t  CHECK (col3 > 0)
);
```

#### 25.4.4 约束状态子句

约束的 `ENABLE/DISABLE` / `VALIDATE/NOVALIDATE` / `RELY/NORELY` 等状态子句在约束末尾标记：

```sql
ALTER TABLE employees ADD CONSTRAINT fk_dept
    FOREIGN KEY (department_id) REFERENCES departments(department_id)
    ENABLE VALIDATE;

ALTER TABLE employees MODIFY CONSTRAINT fk_dept
    RELY NOVALIDATE;

ALTER TABLE employees ADD CONSTRAINT pk_emp
    PRIMARY KEY (employee_id)
    USING INDEX (CREATE INDEX idx_pk_emp ON employees(employee_id))
    ENABLE NOVALIDATE;
```

| 状态组合 | breakPenalty | indentDelta |
|----------|-------------|-------------|
| `ENABLE` / `DISABLE` | 0.1 | 0 |
| `VALIDATE` / `NOVALIDATE` | 0.1 | 0 |
| `RELY` / `NORELY` | 0.1 | 0 |
| `USING INDEX (...)` | 0.3 | 1 |
| `EXCEPTIONS INTO ...` | 0.2 | 0 |

状态子句前 gap 的 `breakPenalty=0.1`（几乎不换行，除非极端超长），`USING INDEX` 前 breakPenalty=0.3（可选换行）。

#### 25.4.5 CHECK 表达式格式化

CHECK 约束中的表达式在列级约束中保持一行；在表级约束中超长时可折行：

```sql
-- 列级 CHECK — 一行
CREATE TABLE t (salary NUMBER(8,2) CONSTRAINT ck_sal CHECK (salary > 0));

-- 表级 CHECK — 复杂表达式可选换行
CREATE TABLE t (
    salary NUMBER(8,2),
    CONSTRAINT ck_sal CHECK (
        salary > 0
        AND salary < 100000
        AND (department_id IS NOT NULL OR employee_type = 'EXEC')
    )
);
```

| 位置 | 约束 |
|------|------|
| `CHECK` 与 `(` 间 | 1 空格，FORBIDDEN 换行 |
| `(expr)` 括号内 | 列级时 FORBIDDEN 换行；表级时 `breakPenalty=0.13`（§11.5.7.4 乘法规则） |
| 括号对齐 | 第一列对齐 `(` 内一级，`)` 对齐 `CONSTRAINT`（同 §25.4.1 OUT-OF-LINE） |

#### 25.4.6 EXCLUDE 约束（PostgreSQL）

```sql
CREATE TABLE t (
    period       TSRANGE,
    room_number  INT,
    EXCLUDE USING GIST (period WITH &&, room_number WITH =) 
        WHERE (room_number > 0)
        DEFERRABLE
);
```

| 位置 | 约束 |
|------|------|
| `EXCLUDE USING ...` | 同 `PRIMARY KEY` 等约束类型，前 1 空格 |
| `(col WITH operator, ...)` | 列级时保持一行；多列超长时 `commaPosition` 控制截断 |
| `WHERE (...)` | `)` 后 OPTIONAL 换行，缩进同 `EXCLUDE` |
| `DEFERRABLE` | 同 §25.4.4 状态子句规则 |

### 25.5 分区子句格式化

#### 25.5.1 RANGE / LIST / HASH 分区

```sql
CREATE TABLE sales (
    sale_id     NUMBER,
    sale_date   DATE,
    amount      NUMBER(10,2),
    region_id   NUMBER(4)
)
PARTITION BY RANGE (sale_date)
(
    PARTITION p_2023_q1 VALUES LESS THAN (DATE '2023-04-01'),
    PARTITION p_2023_q2 VALUES LESS THAN (DATE '2023-07-01'),
    PARTITION p_2023_q3 VALUES LESS THAN (DATE '2023-10-01'),
    PARTITION p_2023_q4 VALUES LESS THAN (DATE '2024-01-01'),
    PARTITION p_future  VALUES LESS THAN (MAXVALUE)
);

-- LIST 分区
PARTITION BY LIST (region_id) (
    PARTITION p_north VALUES (1, 2, 3),
    PARTITION p_south VALUES (4, 5, 6),
    PARTITION p_other VALUES (DEFAULT)
);

-- HASH 分区
PARTITION BY HASH (employee_id) PARTITIONS 4;
```

#### 25.5.2 子分区

```sql
CREATE TABLE sales (
    sale_id     NUMBER,
    sale_date   DATE,
    amount      NUMBER(10,2),
    region_id   NUMBER(4)
)
PARTITION BY RANGE (sale_date)
SUBPARTITION BY LIST (region_id)
(
    PARTITION p_2023_q1 VALUES LESS THAN (DATE '2023-04-01') (
        SUBPARTITION p_2023_q1_n VALUES (1, 2, 3),
        SUBPARTITION p_2023_q1_s VALUES (4, 5, 6)
    ),
    PARTITION p_2023_q2 VALUES LESS THAN (DATE '2023-07-01') (
        SUBPARTITION p_2023_q2_n VALUES (1, 2, 3),
        SUBPARTITION p_2023_q2_s VALUES (4, 5, 6)
    )
);
```

#### 25.5.3 格式化约束

| 位置 | 约束 |
|------|------|
| `PARTITION BY` 前 | 换行，与 CREATE TABLE 同级别 |
| `SUBPARTITION BY` 前 | 换行，缩进一级 |
| `PARTITION name` 前 | 换行，缩进一级 |
| `SUBPARTITION name` 前 | 换行，缩进两级 |
| 分区 `VALUES LESS THAN` | 分区同行 |
| `(` 和 `)` 对齐 | 与 `PARTITION BY` / `PARTITION name` 对齐 |

#### 25.5.4 间隔分区（Interval Partitioning, Oracle 11g+）

```sql
CREATE TABLE sales (
    sale_id     NUMBER,
    sale_date   DATE,
    amount      NUMBER(10,2)
)
PARTITION BY RANGE (sale_date)
INTERVAL (NUMTODSINTERVAL(1, 'MONTH'))
(
    PARTITION p_2023_q1 VALUES LESS THAN (DATE '2023-04-01'),
    PARTITION p_2023_q2 VALUES LESS THAN (DATE '2023-07-01')
);
```

| 位置 | 约束 |
|------|------|
| `INTERVAL (expr)` | `PARTITION BY` 后换行，`INTERVAL` 独立行，缩进一级 |
| `(expr)` 括号内 | 紧凑保持一行 |

#### 25.5.5 自动列表分区（Auto-List, Oracle 12c+）

```sql
CREATE TABLE sale_regions (
    region_id   NUMBER(4),
    region_name VARCHAR2(50),
    sale_amount NUMBER(10,2)
)
PARTITION BY LIST (region_id) AUTOMATIC
(
    PARTITION p_north VALUES (1, 2, 3),
    PARTITION p_south VALUES (4, 5, 6)
);
```

`AUTOMATIC` 关键字与 `PARTITION BY LIST` 同行。

#### 25.5.6 引用分区（Reference Partitioning, Oracle 11g+）

```sql
CREATE TABLE orders (
    order_id    NUMBER(6),
    order_date  DATE,
    customer_id NUMBER(6),
    CONSTRAINT pk_orders PRIMARY KEY (order_id)
)
PARTITION BY REFERENCE (pk_orders);
```

`PARTITION BY REFERENCE (constraint_name)` 一行，无分区定义括号。

#### 25.5.7 分区参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ddlPartitionPerLine` | boolean | true | 分区项每行一个 |
| `ddlPartitionIntervalIndent` | boolean | true | INTERVAL 子句独立行 |
| `ddlSubpartitionPerLine` | boolean | true | 子分区项每行一个 |

### 25.6 存储子句与物理属性

```sql
CREATE TABLE employees (
    employee_id NUMBER(6)
)
TABLESPACE users
STORAGE (
    INITIAL     64K
    NEXT        32K
    MINEXTENTS  1
    MAXEXTENTS  UNLIMITED
)
PCTFREE   10
PCTUSED   40
NOLOGGING
COMPRESS BASIC
CACHE;
```

**约束策略**：
- 存储子句 `STORAGE (...)` 内部每项一行，`(` 后缩进一级
- `TABLESPACE` / `PCTFREE` / `PCTUSED` / `NOLOGGING` / `COMPRESS` / `CACHE` 等子句前换行
- 每个物理属性子句各占一行，缩进与 CREATE TABLE 同级别

### 25.7 CREATE INDEX

```sql
-- 单列索引
CREATE INDEX idx_emp_dept ON employees(department_id);

-- 唯一索引
CREATE UNIQUE INDEX idx_emp_email ON employees(email);

-- 位图索引
CREATE BITMAP INDEX idx_emp_dept ON employees(department_id);

-- 函数索引
CREATE INDEX idx_emp_name ON employees(UPPER(last_name));

-- 复合索引 + 本地分区
CREATE UNIQUE INDEX idx_emp_unique ON employees(employee_id, department_id)
    LOCAL
    TABLESPACE users
    PCTFREE 5;

-- 非分区
CREATE INDEX idx_emp_hire ON employees(hire_date)
    GLOBAL PARTITION BY RANGE (hire_date) (
        PARTITION p_old VALUES LESS THAN (DATE '2020-01-01'),
        PARTITION p_mid VALUES LESS THAN (DATE '2023-01-01'),
        PARTITION p_new VALUES LESS THAN (MAXVALUE)
    );
```

**约束策略**：

| 位置 | 约束 |
|------|------|
| `CREATE [UNIQUE/BITMAP] INDEX` | 保持一行 |
| `ON table_name(columns)` | 索引名与 ON 之间 1 空格，ON 与表名之间 1 空格 |
| `LOCAL` / `GLOBAL` | 索引列 `)` 后换行，`LOCAL`/`GLOBAL` 缩进一级 |
| 分区子句 | 同 §25.5 规则 |
| 存储子句 | `TABLESPACE`/`PCTFREE` 等前换行 |

#### 25.7.1 索引选项扩展

```sql
-- 索引列排序
CREATE INDEX idx_emp_name ON employees(last_name ASC, first_name DESC);

-- NULL 排序
CREATE INDEX idx_emp_hire ON employees(hire_date DESC NULLS LAST);

-- 部分索引（PostgreSQL）
CREATE INDEX idx_active_emp ON employees(employee_id)
    WHERE status = 'ACTIVE';

-- 包含列索引（PostgreSQL 唯一索引）
CREATE UNIQUE INDEX idx_emp_email ON employees(email)
    INCLUDE (first_name, last_name);

-- ONLINE 索引重建
ALTER INDEX idx_emp_name REBUILD ONLINE;

-- 并行度与日志
CREATE INDEX idx_emp_dept ON employees(department_id)
    ONLINE
    PARALLEL 4
    NOLOGGING
    COMPUTE STATISTICS;

-- 不可见索引
CREATE INDEX idx_emp_dept ON employees(department_id) INVISIBLE;

-- 函数索引高级选项
CREATE INDEX idx_emp_name ON employees(
    UPPER(last_name) ASC,
    LOWER(first_name) DESC
) LOCAL COMPRESS 1;
```

| 选项 | 位置 | 换行策略 |
|------|------|----------|
| `ASC` / `DESC` | 列名后 | 与列名同行，FORBIDDEN 换行 |
| `NULLS FIRST` / `NULLS LAST` | 列排序后 | 与列同行 |
| `INCLUDE (col1, col2)` | 列列表 `)` 后 | OPTIONAL 换行，缩进一级 |
| `WHERE (predicate)` | 表名 `)` 后 | OPTIONAL 换行，缩进一级（PG 部分索引） |
| `ONLINE` | 表/列 `)` 后 | OPTIONAL 换行，缩进一级 |
| `PARALLEL n` | 所有关键字后 | OPTIONAL 换行，缩进一级 |
| `COMPUTE STATISTICS` | 列/ONLINE 后 | OPTIONAL 换行，缩进一级 |
| `COMPRESS n` | LOCAL 后 | 与 LOCAL 同行，OPTIONAL 前换行 |
| `INVISIBLE` / `VISIBLE` | 选项最后 | 最后标记，保持同行或换行 |

#### 25.7.2 索引参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ddlIndexOptionPerLine` | boolean | true | INDEX 选项每行一个 |
| `ddlIndexSortAscDesc` | boolean | false | 列名后显式标注 ASC/DESC |
| `ddlIndexNullsSort` | boolean | false | 索引列后显式标注 NULLS FIRST/LAST |

### 25.8 CREATE VIEW / MATERIALIZED VIEW

#### 25.8.1 普通视图

```sql
-- 简单视图，一行
CREATE OR REPLACE VIEW v_emp_names AS
    SELECT employee_id, first_name, last_name FROM employees;

-- 复杂视图，查询体缩进
CREATE OR REPLACE VIEW v_emp_dept_salary
    (emp_id, emp_name, dept_name, salary)
AS
    SELECT e.employee_id,
           e.first_name || ' ' || e.last_name,
           d.department_name,
           e.salary
    FROM employees e
    JOIN departments d ON e.department_id = d.department_id
    WHERE e.salary > 5000
    WITH CHECK OPTION;
```

#### 25.8.2 物化视图

```sql
CREATE MATERIALIZED VIEW LOG ON employees
    WITH ROWID, SEQUENCE (employee_id, salary)
    INCLUDING NEW VALUES;

CREATE MATERIALIZED VIEW mv_dept_summary
    TABLESPACE users
    BUILD IMMEDIATE
    REFRESH COMPLETE ON DEMAND
    ENABLE QUERY REWRITE
AS
    SELECT department_id,
           COUNT(*)       AS emp_count,
           SUM(salary)    AS total_salary,
           AVG(salary)    AS avg_salary
    FROM employees
    GROUP BY department_id;
```

#### 25.8.3 格式化约束

| 位置 | 约束 |
|------|------|
| `VIEW` 名 → `AS` | 列列表 `(...)` 可选，列名每行一个；`AS` 前换行 |
| `AS` → SELECT | `requireNewline(asIdx, selectIdx, 1)` |
| SELECT 内部 | 复用 §11.5 DML 子句树格式化规则 |
| `WITH CHECK OPTION` | 前换行，缩进与 SELECT 同级别 |
| 物化视图选项 | `BUILD IMMEDIATE/REFRESH/ENABLE` 等每选项一行，缩进一级 |

### 25.9 ALTER TABLE

#### 25.9.1 ADD 列

```sql
-- 单列
ALTER TABLE employees ADD middle_name VARCHAR2(50);

-- 多列
ALTER TABLE employees ADD (
    middle_name  VARCHAR2(50),
    suffix       VARCHAR2(10),
    CONSTRAINT ck_suffix CHECK (suffix IN ('JR', 'SR', 'III'))
);
```

多列 ADD 时复用 §25.3 CREATE TABLE 的列对齐规则。`(` 后缩进，`)` 前弹出。

#### 25.9.2 MODIFY 列

```sql
ALTER TABLE employees MODIFY (
    salary    NUMBER(12,2) NOT NULL,
    email     VARCHAR2(50) NOT NULL
);
```

与 ADD 同样的列对齐规则，但 MODIFY 不能加表级约束。

#### 25.9.3 DROP 列

```sql
-- 单列
ALTER TABLE employees DROP COLUMN middle_name;

-- 多列
ALTER TABLE employees DROP (
    middle_name,
    suffix
);

-- 带级联
ALTER TABLE employees DROP COLUMN department_id CASCADE CONSTRAINTS;
```

**约束策略**：多列 DROP 时，括号内列名每行一个，缩进一级。

#### 25.9.4 ALTER TABLE 约束操作

```sql
ALTER TABLE employees ADD CONSTRAINT fk_dept
    FOREIGN KEY (department_id)
    REFERENCES departments(department_id)
    ON DELETE CASCADE
    DEFERRABLE;

ALTER TABLE employees ENABLE CONSTRAINT fk_dept;

ALTER TABLE employees DROP CONSTRAINT fk_dept CASCADE;

ALTER TABLE employees MODIFY CONSTRAINT fk_dept RELY;
```

约束添加的换行规则同 §25.4.2。

#### 25.9.5 ALTER TABLE 分区操作

```sql
ALTER TABLE sales ADD PARTITION p_2024_q1
    VALUES LESS THAN (DATE '2024-04-01')
    TABLESPACE users;

ALTER TABLE sales MERGE PARTITIONS p_2023_q1, p_2023_q2 INTO PARTITION p_2023_h1;

ALTER TABLE sales TRUNCATE PARTITION p_2023_q1;

ALTER TABLE sales EXCHANGE PARTITION p_2023 WITH TABLE sales_2023;
```

分区名与子句间 1 格空格。子句超长时在关键字前 OPTIONAL 换行。

#### 25.9.6 其他 ALTER TABLE 操作

| 操作 | 语法 | 格式化规则 |
|------|------|-----------|
| RENAME TO | `ALTER TABLE t RENAME TO new_name;` | 一行 |
| RENAME COLUMN | `ALTER TABLE t RENAME COLUMN old TO new;` | 一行 |
| RENAME CONSTRAINT | `ALTER TABLE t RENAME CONSTRAINT old TO new;` | 一行 |
| SET UNUSED | `ALTER TABLE t SET UNUSED COLUMN col [CASCADE CONSTRAINTS];` | 一行 |
| DROP UNUSED | `ALTER TABLE t DROP UNUSED COLUMNS [CHECKPOINT n];` | 一行 |
| MOVE TABLE | `ALTER TABLE t MOVE [TABLESPACE ts] [STORAGE (...)] [COMPRESS];` | 物理属性规则同 §25.6，每选项一行 |
| MOVE PARTITION | `ALTER TABLE t MOVE PARTITION p [TABLESPACE ts] [STORAGE (...)];` | 同 MOVE TABLE |
| SPLIT PARTITION | `ALTER TABLE t SPLIT PARTITION p AT (val) INTO (PARTITION p1, PARTITION p2);` | `AT (val)` 一行，`INTO (...)` 分区定义同 §25.5 |
| MERGE PARTITIONS | `ALTER TABLE t MERGE PARTITIONS p1, p2 INTO PARTITION p3;` | 一行 |
| DROP PARTITION | `ALTER TABLE t DROP PARTITION p;` | 一行 |
| TRUNCATE PARTITION | `ALTER TABLE t TRUNCATE PARTITION p [DROP STORAGE];` | 一行 |
| RENAME PARTITION | `ALTER TABLE t RENAME PARTITION old TO new;` | 一行 |
| ADD SUBPARTITION | `ALTER TABLE t MODIFY PARTITION p ADD SUBPARTITION sp VALUES (val);` | 同 ADD PARTITION |
| SHRINK SPACE | `ALTER TABLE t SHRINK SPACE [CASCADE];` | 一行 |
| ENABLE ROW MOVEMENT | `ALTER TABLE t ENABLE ROW MOVEMENT;` | 一行 |
| MODIFY DEFAULT ATTRIBUTES | `ALTER TABLE t MODIFY DEFAULT ATTRIBUTES FOR PARTITION p [STORAGE (...)];` | 物理属性规则 |

```sql
-- 多选项 ALTER TABLE 示例
ALTER TABLE employees
    SET UNUSED COLUMN temp_data CASCADE CONSTRAINTS
/

ALTER TABLE employees MOVE
    TABLESPACE users
    STORAGE (
        INITIAL 128K
        NEXT    64K
    )
    COMPRESS FOR OLTP
/

ALTER TABLE sales SPLIT PARTITION p_2023_at ('2023-07-01')
    INTO (
        PARTITION p_2023_h1,
        PARTITION p_2023_h2
    )
    UPDATE INDEXES
/
```

### 25.10 其他 DDL 速查表

| DDL 类型 | 格式化规则 |
|----------|-----------|
| `DROP TABLE name [CASCADE CONSTRAINTS] [PURGE];` | 一行 |
| `DROP VIEW name [CASCADE CONSTRAINTS];` | 一行 |
| `DROP INDEX name [FORCE];` | 一行 |
| `DROP SEQUENCE name;` | 一行 |
| `DROP SYNONYM name [FORCE];` | 一行 |
| `DROP DATABASE LINK name;` | 一行 |
| `TRUNCATE TABLE name [{DROP \| REUSE} STORAGE];` | 一行 |
| `RENAME old_name TO new_name;` | 一行 |
| `COMMENT ON TABLE name IS 'text';` | 一行，字符串内不格式化 |
| `COMMENT ON COLUMN table.column IS 'text';` | 一行 |
| `CREATE SEQUENCE name ...` | `INCREMENT BY`/`START WITH`/`MAXVALUE`/`CYCLE` 等子句每项可选一行或紧凑，由 `ddlSequencePerLine` 控制 |
| `CREATE SYNONYM name FOR schema.object;` | `CREATE [PUBLIC] SYNONYM` 一行 |
| `CREATE DATABASE LINK name ...` | `CONNECT TO user IDENTIFIED BY pwd USING 'conn'` 保持一行 |
| `GRANT priv1, priv2, ... ON object TO user [WITH GRANT OPTION];` | 权限列表超长时逗号后换行 |
| `REVOKE priv1, priv2, ... ON object FROM user;` | 同 GRANT |
| `FLASHBACK TABLE name TO TIMESTAMP \| SCN \| BEFORE DROP;` | 一行 |
| `PURGE TABLE \| INDEX \| TABLESPACE name;` | 一行 |
| `ASSOCIATE STATISTICS WITH TABLE name;` | 一行 |
| `AUDIT option ON object BY user;` | 一行 |
| `NOAUDIT option ON object;` | 一行 |

### 25.10a CREATE / ALTER TABLESPACE

```sql
-- 创建表空间
CREATE TABLESPACE users
    DATAFILE 'users01.dbf' SIZE 100M
    AUTOEXTEND ON NEXT 10M MAXSIZE UNLIMITED
    EXTENT MANAGEMENT LOCAL AUTOALLOCATE
    SEGMENT SPACE MANAGEMENT AUTO;

-- 大文件表空间
CREATE BIGFILE TABLESPACE big_users
    DATAFILE 'big_users01.dbf' SIZE 1G;

-- 临时表空间
CREATE TEMPORARY TABLESPACE temp_users
    TEMPFILE 'temp_users01.dbf' SIZE 50M
    EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;

-- UNDO 表空间
CREATE UNDO TABLESPACE undo_users
    DATAFILE 'undo_users01.dbf' SIZE 200M;

-- ALTER TABLESPACE
ALTER TABLESPACE users
    ADD DATAFILE 'users02.dbf' SIZE 100M;

ALTER TABLESPACE users
    DROP DATAFILE 'users01.dbf';

ALTER TABLESPACE users
    RENAME TO users_archive;

ALTER TABLESPACE users
    READ ONLY;

ALTER TABLESPACE users
    READ WRITE;
```

| 位置 | 约束 |
|------|------|
| `DATAFILE` / `TEMPFILE` 路径 | 与 `'...' SIZE` 同行 |
| `AUTOEXTEND ON NEXT ... MAXSIZE` | 与 DATAFILE 同行或折行（breakPenalty=0.8） |
| `EXTENT MANAGEMENT` | 前换行，缩进一级 |
| `SEGMENT SPACE MANAGEMENT` | 前换行，缩进一级 |
| `ADD DATAFILE` / `DROP DATAFILE` / `RENAME TO` | 每操作一行 |
| `READ ONLY` / `READ WRITE` / `BEGIN BACKUP` / `END BACKUP` | 前换行 |

### 25.10b CREATE USER / PROFILE / ROLE / DIRECTORY

```sql
-- CREATE USER
CREATE USER app_user IDENTIFIED BY "Str0ng!Pass"
    DEFAULT TABLESPACE users
    TEMPORARY TABLESPACE temp
    QUOTA 100M ON users
    QUOTA UNLIMITED ON app_data
    PROFILE app_profile
    PASSWORD EXPIRE
    ACCOUNT UNLOCK;

-- CREATE PROFILE
CREATE PROFILE app_profile LIMIT
    SESSIONS_PER_USER          10
    CPU_PER_SESSION            UNLIMITED
    CPU_PER_CALL               3000
    CONNECT_TIME               45
    IDLE_TIME                  30
    FAILED_LOGIN_ATTEMPTS      5
    PASSWORD_LOCK_TIME         1
    PASSWORD_LIFE_TIME         180;

-- CREATE ROLE
CREATE ROLE app_admin_role NOT IDENTIFIED;
CREATE ROLE app_reader_role IDENTIFIED USING app_pkg;

-- CREATE DIRECTORY
CREATE DIRECTORY data_dir AS 'C:\app\data';
```

| 位置 | 约束 |
|------|------|
| `IDENTIFIED BY` | 与用户名同行 |
| `DEFAULT TABLESPACE` / `TEMPORARY TABLESPACE` | 前换行，缩进一级 |
| `QUOTA n ON tablespace` | 每 QUOTA 一行 |
| `PROFILE name` / `PASSWORD EXPIRE` / `ACCOUNT UNLOCK/LOCK` | 前换行 |
| `CREATE PROFILE ... LIMIT` 后资源参数 | 每参数一行，参数名列对齐 |
| `CREATE ROLE [NOT IDENTIFIED / IDENTIFIED USING]` | 一行 |

### 25.10c ALTER INDEX / VIEW / SEQUENCE

```sql
-- ALTER INDEX
ALTER INDEX idx_emp_name REBUILD;
ALTER INDEX idx_emp_name REBUILD ONLINE;
ALTER INDEX idx_emp_name REBUILD PARTITION p_2023;
ALTER INDEX idx_emp_name
    REBUILD TABLESPACE users
    COMPUTE STATISTICS;
ALTER INDEX idx_emp_name INVISIBLE;
ALTER INDEX idx_emp_name VISIBLE;
ALTER INDEX idx_emp_name MONITORING USAGE;
ALTER INDEX idx_emp_name RENAME TO idx_emp_new_name;

-- ALTER VIEW
ALTER VIEW v_emp_names COMPILE;
ALTER VIEW v_emp_names EDITIONABLE;
ALTER VIEW v_emp_names NONEDITIONABLE;

-- ALTER SEQUENCE
ALTER SEQUENCE seq_emp_id
    INCREMENT BY 10
    MAXVALUE 999999
    CYCLE
    CACHE 20;
```

| 操作 | 格式化规则 |
|------|-----------|
| `REBUILD [ONLINE] [PARTITION p]` | `REBUILD` 前换行，缩进一级；选项保持同行 |
| `REBUILD ... TABLESPACE / COMPUTE STATISTICS` | 每选项一行 |
| `INVISIBLE` / `VISIBLE` / `MONITORING USAGE` | 前换行 |
| `RENAME TO` | 前换行 |
| `ALTER VIEW ... COMPILE` | 一行 |
| `ALTER SEQUENCE` 选项 | 同 CREATE SEQUENCE（`ddlSequencePerLine` 控制） |

### 25.10d DDL 方言差异

DDL 语法在不同方言间差异显著，格式化时需要方言感知。

| 方言 | 特有关键字/构造 | 格式化影响 |
|------|---------------|-----------|
| Oracle | `TABLESPACE`、`STORAGE (...)`、`PCTFREE`/`PCTUSED`、`COMPRESS BASIC/OLTP`、`LOGGING`/`NOLOGGING`、`MONITORING` | 物理属性子句缩进一级，每项一行 |
| Oracle | `GENERATED ALWAYS AS IDENTITY` | Identity 列对齐 |
| Oracle | `PARTITION BY REFERENCE`、`INTERVAL`、`AUTOMATIC` | 分区类型识别 |
| Oracle | `FLASHBACK TABLE ... TO ... DROP` | 一行 |
| Oracle | `MATERIALIZED VIEW LOG` | MV LOG 选项⻬列 |
| MySQL | `ENGINE=InnoDB`、`AUTO_INCREMENT=n`、`CHARSET utf8` | 列定义 `)` 后单行紧凑 |
| MySQL | `PARTITION BY KEY`、`PARTITION BY LIST COLUMNS` | 分区关键字识别 |
| MySQL | `ON DUPLICATE KEY UPDATE` | INSERT 后换行 |
| MySQL | `ALTER TABLE ... ALTER COLUMN ... SET DEFAULT` | ALTER 变体识别 |
| MySQL | `ALTER TABLE ... ORDER BY col` | MySQL ⚠ 非标准 |
| PostgreSQL | `WITH (OIDS=FALSE)`、`TABLESPACE ts` | 表选项 `WITH (...)` 保持一行 |
| PostgreSQL | `PARTITION BY RANGE (col)`（声明式分区） | PG 12+ 分区与 Oracle 格式兼容 |
| PostgreSQL | `ON CONFLICT DO UPDATE/NOTHING` | INSERT 后换行 |
| PostgreSQL | `EXCLUDE USING ...` | 约束类型识别（§25.4.6） |
| PostgreSQL | `ALTER TABLE ... SET STATISTICS n` | ALTER 变体识别 |
| PostgreSQL | `ALTER TABLE ... CLUSTER ON idx` | ALTER 变体识别 |

```sql
-- MySQL DDL 示例
CREATE TABLE employees (
    employee_id INT AUTO_INCREMENT PRIMARY KEY,
    first_name  VARCHAR(20) NOT NULL,
    last_name   VARCHAR(25) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PostgreSQL DDL 示例
CREATE TABLE employees (
    employee_id INT GENERATED ALWAYS AS IDENTITY,
    first_name  VARCHAR(20) NOT NULL,
    last_name   VARCHAR(25) NOT NULL
) WITH (OIDS=FALSE) TABLESPACE users;

-- ALTER TABLE MySQL 特有操作
ALTER TABLE employees ALTER COLUMN salary SET DEFAULT 0;
ALTER TABLE employees ORDER BY last_name, first_name;
```

**方言识别策略**：`ddl_format_dialect` 默认跟随全局方言（§3a.2）。方言敏感度仅在 §3a.4 DDL 子模块中启用，不影响 PL/SQL 块格式化。

### 25.11 PL/SQL 块内 DDL

#### EXECUTE IMMEDIATE 中的 DDL

```sql
PROCEDURE p IS
    v_sql VARCHAR2(4000);
BEGIN
    v_sql := 'CREATE TABLE temp_' || v_table_name || ' (
        id        NUMBER(6),
        name      VARCHAR2(100),
        created   DATE
    )';
    EXECUTE IMMEDIATE v_sql;
END;
```

**约束策略**：
- DDL 字符串作为普通字符串拼接处理（§9 保护策略）
- 对字符串内的 DDL 格式化**不予处理**（运行时字符串）
- 如需对字符串内 DDL 格式化，属 IDE 级别的"字符串内 SQL 检测"功能，不在格式化引擎范围内

#### DBMS_UTILITY.EXEC_DDL_STATEMENT

```sql
DBMS_UTILITY.EXEC_DDL_STATEMENT('CREATE INDEX idx_temp ON temp_tab(col)');
```

同 `EXECUTE IMMEDIATE` 策略——字符串内 DDL 不处理。

### 25.12 DDL 格式化参数汇总

| 参数 | 类型 | 默认值 | 说明 | 参考 |
|------|------|--------|------|------|
| `columnDefPerLine` | boolean | true | CREATE TABLE 每列一行 | §25.3.3 |
| `columnNameRightAlign` | boolean | false | 列名右对齐 | §25.3.3 |
| `columnTypeAlign` | boolean | true | 类型声明列对齐 | §25.3.3 |
| `columnNullAlign` | boolean | true | NOT NULL/NULL 列对齐 | §25.3.3 |
| `columnDefaultAlign` | boolean | true | DEFAULT 列对齐 | §25.3.3 |
| `columnCommentInline` | boolean | false | 列注释行内尾随（vs 独立 COMMENT ON） | §25.3.4 |
| `columnIdentityAlign` | boolean | true | Identity 列 GENERATED 对齐 | §25.3.4 |
| `constraintOutOfLine` | boolean | true | 表级约束独立成行（OUT-OF-LINE） | §25.4.1 |
| `constraintNameAlign` | boolean | true | 约束名列对齐 | §25.4.3 |
| `constraintStatusAlign` | boolean | true | 约束状态子句 ENABLE/VALIDATE 对齐 | §25.4.4 |
| `ddlCheckExprExpand` | boolean | false | CHECK 表达式超长可折行 | §25.4.5 |
| `ddlPartitionPerLine` | boolean | true | 分区定义每行一个 | §25.5 |
| `ddlPartitionIntervalIndent` | boolean | true | INTERVAL 子句独立行 | §25.5.4 |
| `ddlSubpartitionPerLine` | boolean | true | 子分区每行一个 | §25.5.7 |
| `ddlStoragePerLine` | boolean | true | STORAGE 子句每项一行 | §25.6 |
| `ddlIndexOptionPerLine` | boolean | true | INDEX 选项每行一个 | §25.7.2 |
| `ddlIndexSortAscDesc` | boolean | false | 索引列显式 ASC/DESC | §25.7.2 |
| `ddlIndexNullsSort` | boolean | false | 索引列显式 NULLS FIRST/LAST | §25.7.2 |
| `ddlViewQueryIndent` | boolean | true | VIEW AS 后 SELECT 缩进 | §25.8 |
| `ddlSequencePerLine` | enum | COMPACT | SEQUENCE 子句紧凑/每行一个 | §25.10 |
| `ddlGrantPrivPerLine` | boolean | false | GRANT 权限每行一个 | §25.10 |
| `ddlAlterColumnPerLine` | boolean | true | ALTER TABLE ADD/MODIFY 多列时每列一行 | §25.9.1 |
| `ddlTbspOptionPerLine` | boolean | true | TABLESPACE 选项每行一个 | §25.10a |
| `ddlProfileParamPerLine` | boolean | true | PROFILE 资源参数每行一个 | §25.10b |
| `ddlUserOptionPerLine` | boolean | true | CREATE USER 选项每行一个 | §25.10b |

### 25.13 实现位置

DDL 格式化实现在 `ConstraintGenerator` 中的以下新增方法：

```java
class ConstraintGenerator {
    // DDL 派发入口（从 §22.3 generate() 路由）
    void dispatchDDL(PlSqlBlock block);

    // 各 DDL 类型
    void walkCreateTable(PlSqlBlock block);      // §25.3-25.6
    void walkCreateIndex(PlSqlBlock block);       // §25.7
    void walkCreateView(PlSqlBlock block);        // §25.8
    void walkAlterTable(PlSqlBlock block);        // §25.9
    void walkCreateTablespace(PlSqlBlock block);  // §25.10a
    void walkCreateUser(PlSqlBlock block);        // §25.10b
    void walkCreateProfile(PlSqlBlock block);     // §25.10b
    void walkAlterIndex(PlSqlBlock block);        // §25.10c
    void walkAlterSequence(PlSqlBlock block);     // §25.10c
    void walkOtherDDL(PlSqlBlock block);          // §25.10 速查表
}
```

派发逻辑：

```java
void generate(PlSqlBlock block, FormatOptions opts, List<GapConstraint> out) {
    switch (block.type) {
        case DDL_CREATE_TABLE:     walkCreateTable(block);     break;
        case DDL_CREATE_INDEX:     walkCreateIndex(block);     break;
        case DDL_CREATE_VIEW:
        case DDL_CREATE_MVIEW:     walkCreateView(block);      break;
        case DDL_ALTER_TABLE:      walkAlterTable(block);      break;
        case DDL_CREATE_TABLESPACE:walkCreateTablespace(block);break;
        case DDL_CREATE_USER:      walkCreateUser(block);      break;
        case DDL_CREATE_PROFILE:   walkCreateProfile(block);   break;
        case DDL_ALTER_INDEX:      walkAlterIndex(block);      break;
        case DDL_ALTER_SEQUENCE:   walkAlterSequence(block);   break;
        default:                   walkOtherDDL(block);        break;
    }
}
```

与 DML 共用 AlignGroup 对齐引擎和 GapConstraint 约束模型，不需要新增基础设施。
