# SQL/PLSQL 格式化功能设计方案（v2.0）

## 一、总体架构

```
SqlFormatter.format(source, options, dialect)
  │
  ├── SqlDialect (接口)
  │     ├── OracleDialect
  │     ├── OceanBaseDialect (extends Oracle)
  │     ├── MySqlDialect
  │     ├── PostgreSqlDialect
  │     └── (预留: Db2, SQLite, H2, MariaDB, ...)
  │     └── 外部注册: DialectManager.register(customDialect)
  │
  ├── SqlTypeClassifier          ← 方言关键字集判断 SQL 类型
  │     └── 输出: SqlType (DQL/DML/DDL/PLSQL + 子类型)
  │
  ├── TemplateRegistry           ← SqlType → FormatTemplate
  │     ├── 每个模板包含: lineBreakBefore/After, indentIncrease/Decrease
  │     ├── 继承机制: 子类型继承父类型模板
  │     └── 所有模板引用 FormatOptions 参数做决策
  │
  ├── FormatContext              ← 运行时状态
  │     ├── currentIndent, parenDepth, subqueryStack
  │     ├── selectListAlign, declarationAlign
  │     ├── currentLineWidth, inXxx 标记
  │     └── 方言特殊子句跟踪（LIMIT, RETURNING, ON CONFLICT 等）
  │
  ├── TokenProcessor (主循环，递归)
  │     ├── 外层: 遍历 token 流，按模板断行/缩进/对齐
  │     ├── 子查询: 遇到 `(` + SELECT → SubqueryHandler 递归
  │     │     ├── INLINE: 合并为一行
  │     │     ├── EXPAND: 展开换行缩进
  │     │     └── AUTO: 根据 token 数 + maxLineWidth + 复杂度预判
  │     ├── 方言子句: 遇到特殊关键字 → 按 SpecialClause 规则处理
  │     └── maxLineWidth: 每个 token 后检查行宽 → 超限自动换行
  │
  └── PostProcessor
        ├── lineEnding 替换 (LF/CRLF)
        ├── trailingWhitespace 清理
        └── blankLine 处理 (PRESERVE/COLLAPSE/STRIP)
```

### 1.1 核心原则

| 原则 | 说明 |
|------|------|
| **模板化** | 每类 SQL 绑定一个 FormatTemplate，定义断行/缩进/对齐规则 |
| **参数化** | 所有行为由 FormatOptions 全部 40+ 参数驱动，UI 全可配 |
| **递归** | 子查询作为独立的格式化子任务，递归调用 TokenProcessor |
| **方言感知** | TokenProcessor 查 Dialect 的关键字集和特殊子句 |
| **容错** | ANTLR 解析异常 → 返回原始输入，不崩溃 |

---

## 二、SQL 类型分类引擎

### 2.1 SqlType 枚举

```java
public enum SqlType {
    // DQL
    DQL_SELECT,          // SELECT ...
    DQL_SELECT_JOIN,     // SELECT ... JOIN ... JOIN ...
    DQL_SELECT_UNION,    // SELECT ... UNION SELECT ...
    DQL_CTE,             // WITH ... AS (SELECT ...)
    DQL_SUBQUERY,        // (SELECT ...) 作为子查询

    // DML
    DML_INSERT,          // INSERT INTO ... (VALUES | SELECT)
    DML_UPDATE,          // UPDATE ... SET ... WHERE ...
    DML_DELETE,          // DELETE FROM ... WHERE ...
    DML_MERGE,           // MERGE INTO ... USING ... ON ...

    // DDL
    DDL_CREATE_TABLE,    // CREATE TABLE ...
    DDL_CREATE_INDEX,    // CREATE [UNIQUE] INDEX ...
    DDL_CREATE_VIEW,     // CREATE [OR REPLACE] VIEW ... AS ...
    DDL_ALTER_TABLE,     // ALTER TABLE ... ADD|MODIFY|DROP ...
    DDL_DROP,            // DROP TABLE|INDEX|VIEW|SEQUENCE ...
    DDL_TRUNCATE,        // TRUNCATE TABLE ...
    DDL_GRANT_REVOKE,    // GRANT|REVOKE ...
    DDL_OTHER,           // COMMENT, RENAME, PURGE, FLASHBACK ...

    // PL/SQL
    PLSQL_BLOCK,         // [DECLARE] BEGIN ... END;
    PLSQL_FUNCTION,      // CREATE [OR REPLACE] FUNCTION ...
    PLSQL_PROCEDURE,     // CREATE [OR REPLACE] PROCEDURE ...
    PLSQL_PACKAGE,       // CREATE [OR REPLACE] PACKAGE ...
    PLSQL_PACKAGE_BODY,  // CREATE [OR REPLACE] PACKAGE BODY ...
    PLSQL_TRIGGER,       // CREATE [OR REPLACE] TRIGGER ...
    PLSQL_TYPE,          // CREATE [OR REPLACE] TYPE ...

    // Other
    OTHER,               // EXPLAIN, CALL, LOCK, SET, etc.
    UNKNOWN;
}
```

### 2.2 SqlTypeClassifier 分类逻辑

扫描 token 直到第一个非注释、非括号 token，按以下规则判断：

```
1. token = SELECT
   ├── 前面有 WITH → DQL_CTE
   ├── 嵌套在 ( ... ) 内 → DQL_SUBQUERY
   └── 否则 → DQL_SELECT
   继续扫描后面是否出现: UNION/MINUS/INTERSECT → DQL_SELECT_UNION
                           JOIN → DQL_SELECT_JOIN

2. token = INSERT → DML_INSERT
3. token = UPDATE → DML_UPDATE
4. token = DELETE → DML_DELETE
5. token = MERGE  → DML_MERGE

6. token = CREATE → 看下一个 token
   ├── TABLE    → DDL_CREATE_TABLE
   ├── INDEX    → DDL_CREATE_INDEX
   ├── [OR REPLACE] VIEW → DDL_CREATE_VIEW
   ├── [OR REPLACE] [EDITIONABLE|NONEDITIONABLE] FUNCTION  → PLSQL_FUNCTION
   ├── [OR REPLACE] [EDITIONABLE|NONEDITIONABLE] PROCEDURE → PLSQL_PROCEDURE
   ├── [OR REPLACE] PACKAGE BODY → PLSQL_PACKAGE_BODY
   ├── [OR REPLACE] PACKAGE     → PLSQL_PACKAGE
   ├── [OR REPLACE] [OR REPLACE] TRIGGER → PLSQL_TRIGGER
   ├── [OR REPLACE] TYPE → PLSQL_TYPE
   ├── SEQUENCE  → DDL_OTHER
   └── 其他      → DDL_OTHER

7. token = ALTER   → DDL_ALTER_TABLE
8. token = DROP    → DDL_DROP
9. token = TRUNCATE → DDL_TRUNCATE
10. token = GRANT|REVOKE → DDL_GRANT_REVOKE

11. token = BEGIN|DECLARE → PLSQL_BLOCK

12. token = EXPLAIN|CALL|LOCK|SET|COMMIT|ROLLBACK → OTHER
```

方言影响：MySQL 的 `LIMIT` 不是 SQL 类型判断依据，只是特殊子句。PostgreSQL 的 `RETURNING` 同理。

---

## 三、FormatTemplate 模板体系

### 3.1 模板定义

```java
public class FormatTemplate {

    // ── 断行规则 ──
    Set<String> lineBreakBefore;     // 在该关键字前换行
    Set<String> lineBreakAfter;      // 在该关键字后换行

    // ── 缩进规则 ──
    Set<String> indentIncrease;      // 缩进 +1（BEGIN, THEN, LOOP...）
    Set<String> indentDecrease;      // 缩进 -1（END, ELSIF...）

    // ── 对齐规则 ──
    boolean selectColumnAlign;       // SELECT 列对齐
    boolean declarationAlign;        // PL/SQL 声明对齐
    boolean columnDefAlign;          // DDL 列定义对齐
    boolean joinOnAlign;             // ON 条件对齐
    boolean setAlign;                // UPDATE SET 等号对齐

    // ── 逗号规则 ──
    CommaPosition commaPosition;     // TRAILING / LEADING
    boolean commaBreak;              // 逗号后换行

    // ── 条件规则 ──
    boolean whereBreakBefore;        // WHERE 前换行
    WhereAndPosition whereAndPosition; // LINE_START / LINE_END
    boolean joinBreakBefore;         // JOIN 前换行
    boolean onBreakBefore;           // ON 前换行
    boolean fromBreakBefore;         // FROM 前换行
    boolean setOperatorBreakBefore;  // UNION/MINUS 前换行

    // ── PL/SQL 规则 ──
    boolean thenBreakBefore;         // THEN 在 IF/WHEN 后也要换行
    boolean loopBreakBefore;         // LOOP 在 FOR/WHILE 后也要换行
    boolean elseBreakBefore;         // ELSE 独立行
    boolean exceptionBreakBefore;    // EXCEPTION 前换行
    boolean endBreakBefore;          // END 前换行
    boolean returnBreakBefore;       // RETURN 前换行 (PL/SQL 函数)
    boolean forBreakBefore;          // FOR 前换行 (cursor FOR loop)

    // ── DDL 规则 ──
    boolean createBreakAfter;        // CREATE 后换行
    boolean tableBreakAfter;         // TABLE 后换行
    boolean parenthesisBreakAfter;   // ( 后换行
    boolean constraintBreakBefore;   // CONSTRAINT 前换行
    boolean referencesBreakBefore;   // REFERENCES 前换行
    boolean storageBreakBefore;      // TABLESPACE/STORAGE 前换行

    // ── DML 规则 ──
    boolean intoBreakAfter;          // INSERT INTO 后换行
    boolean valuesBreakBefore;       // VALUES 前换行
    boolean usingBreakBefore;        // MERGE USING 前换行
    boolean whenBreakBefore;         // WHEN MATCHED 前换行
}
```

### 3.2 模板继承体系

```
BaseTemplate (空集合，所有 false)
├── DQL_BASE
│   ├── DQL_SELECT
│   ├── DQL_SELECT_JOIN
│   ├── DQL_SELECT_UNION
│   ├── DQL_CTE
│   └── DQL_SUBQUERY (继承 DQL_SELECT，但 fromBreakBefore = false 等)
├── DML_BASE
│   ├── DML_INSERT
│   ├── DML_UPDATE
│   ├── DML_DELETE
│   └── DML_MERGE
├── DDL_BASE
│   ├── DDL_CREATE_TABLE
│   ├── DDL_CREATE_INDEX
│   ├── DDL_CREATE_VIEW
│   └── DDL_ALTER_TABLE
└── PLSQL_BASE
    ├── PLSQL_BLOCK
    ├── PLSQL_FUNCTION
    ├── PLSQL_PROCEDURE
    ├── PLSQL_PACKAGE
    ├── PLSQL_PACKAGE_BODY
    ├── PLSQL_TRIGGER
    └── PLSQL_TYPE
```

模板的 `lineBreakBefore`/`indentIncrease` 等集合初始化时先复制父模板，再添加/删除特有项。

### 3.3 模板示例

#### DQL_SELECT 模板

```java
lineBreakBefore: {
    FROM, WHERE, GROUP, ORDER, HAVING,
    UNION, MINUS, INTERSECT, EXCEPT,
    START, CONNECT, FETCH, OFFSET, LIMIT
}
lineBreakAfter:  { SELECT }
indentIncrease:  { SELECT, CASE, WHEN, BEGIN, THEN, LOOP }
indentDecrease:  { END, ELSIF, ELSE, EXCEPTION }
commaPosition:   FormatOptions.commaPosition  // 用户配置
commaBreak:      true  // 逗号后换行（当 selectColumnMode != COMPACT 时）
selectColumnAlign: true // 当 selectColumnMode == ALIGN 时
whereBreakBefore: true
whereAndPosition: FormatOptions.whereAndPosition
joinBreakBefore:  FormatOptions.joinOnNewline
onBreakBefore:    FormatOptions.joinOnNewline && FormatOptions.joinOnAlign
fromBreakBefore:  FormatOptions.fromClauseNewline
```

#### DDL_CREATE_TABLE 模板

```java
lineBreakBefore: {
    (, CONSTRAINT, REFERENCES, TABLESPACE, PCTFREE, PCTUSED,
    INITRANS, MAXTRANS, STORAGE, LOGGING, NOLOGGING,
    COMPRESS, NOCOMPRESS, MONITORING, NOMONITORING,
    PARTITION, SUBPARTITION, RANGE, HASH, LIST
}
lineBreakAfter:  { CREATE, TABLE, (, CONSTRAINT, PARTITION }
indentIncrease:  { (, CONSTRAINT }
indentDecrease:  { ) }
columnDefAlign:  FormatOptions.columnDefAlign
constraintBreakBefore: FormatOptions.constraintFormat == SEPARATE_LINE ? true : false
storageBreakBefore: FormatOptions.storageClauseFormat == LINE_BREAK ? true : false
```

#### PLSQL_FUNCTION 模板

```java
lineBreakBefore: {
    DECLARE, BEGIN, EXCEPTION, END,
    IF, THEN, ELSE, ELSIF, END IF,
    LOOP, END LOOP, FOR, WHILE,
    CASE, WHEN, ELSE, END CASE,
    RETURN, EXIT, CONTINUE, NULL,
    OPEN, FETCH, CLOSE,
    RAISE, PRAGMA, COMMIT, ROLLBACK,
    SAVEPOINT, EXECUTE, IMMEDIATE, INTO,
    IS, AS
}
lineBreakAfter:  { IS, AS, DECLARE, BEGIN }
indentIncrease:  { BEGIN, DECLARE, LOOP, THEN, CASE, WHEN, IF }
indentDecrease:  { END, ELSIF, ELSE, EXCEPTION, END IF, END LOOP, END CASE }
declarationAlign: FormatOptions.declarationAlign
thenBreakBefore:  FormatOptions.thenOnNewLine
loopBreakBefore:  FormatOptions.loopOnNewLine
elseBreakBefore:  FormatOptions.elseOnNewLine
```

---

## 四、FormatOptions 完整参数表（全部 40+ 项，100% UI 可配置）

### 4.1 通用（5）

| 参数 | 键名 | 类型 | 默认值 | UI 控件 |
|------|------|------|--------|---------|
| 方言 | `dialect` | String | Oracle | 下拉框（Oracle/OceanBase/MySQL/PostgreSQL + 自定义） |
| 关键字大小写 | `keywordCase` | UPPER/LOWER/PRESERVE | UPPER | 下拉框 |
| 缩进空格数 | `indentSize` | int | 4 | 微调框 1-8 |
| 最大行宽 | `maxLineWidth` | int | 120 | 微调框 0-999（0=不限） |
| 换行符 | `lineEnding` | LF/CRLF | LF | 下拉框 |

### 4.2 DQL（19）

| 参数 | 键名 | 类型 | 默认值 | UI 控件 |
|------|------|------|--------|---------|
| SELECT 列模式 | `selectColumnMode` | ALIGN/COMPACT/ONE_PER_LINE | ALIGN | 下拉框 |
| SELECT 没列一行几个 | `selectColumnsPerRow` | int | 0(不限) | 微调框（COMPACT 模式生效） |
| FROM 前换行 | `fromClauseNewline` | boolean | true | 复选框 |
| FROM 额外缩进 | `fromClauseIndent` | int | 0 | 微调框 |
| JOIN 换行 | `joinOnNewline` | boolean | true | 复选框 |
| ON 条件对齐 | `joinOnAlign` | boolean | true | 复选框 |
| AND/OR 位置 | `whereAndPosition` | LINE_START/LINE_END | LINE_START | 下拉框 |
| WHERE 条件缩进 | `whereIndentSize` | int | (同 indentSize) | 微调框 |
| 逗号位置 | `commaPosition` | TRAILING/LEADING | TRAILING | 下拉框 |
| 子查询风格 | `subqueryStyle` | INLINE/EXPAND/AUTO | AUTO | 下拉框 |
| 子查询展开阈值 | `subqueryThreshold` | int | 80 | 微调框（AUTO 模式） |
| 子查询内 SELECT 列模式 | `subquerySelectMode` | ALIGN/COMPACT/ONE_PER_LINE | ALIGN | 下拉框 |
| 子查询内 FROM 换行 | `subqueryFromNewline` | boolean | true | 复选框 |
| CTE 格式 | `cteFormat` | COMPACT/ONE_PER_LINE/ALIGN | ONE_PER_LINE | 下拉框 |
| 集合操作符前换行 | `setOperatorNewline` | boolean | true | 复选框 |
| 集合操作两侧对齐 | `setOperatorAlign` | boolean | true | 复选框 |
| IN 列表格式 | `inListFormat` | COMPACT/ONE_PER_LINE | COMPACT | 下拉框 |
| IN 列表每行几个值 | `inListColumnsPerRow` | int | 5 | 微调框（COMPACT 模式） |
| IN 列表自动换行阈值 | `inListThreshold` | int | 10 | 微调框 |

### 4.3 DML（11）

| 参数 | 键名 | 类型 | 默认值 | UI 控件 |
|------|------|------|--------|---------|
| INSERT 列格式 | `insertColumnFormat` | COMPACT/ONE_PER_LINE | COMPACT | 下拉框 |
| INSERT 值格式 | `insertValueFormat` | COMPACT/ONE_PER_LINE | COMPACT | 下拉框 |
| INSERT 列每行几个 | `insertColumnsPerRow` | int | 0(不限) | 微调框 |
| INSERT 值每行几个 | `insertValuesPerRow` | int | 0(不限) | 微调框 |
| INSERT 子查询风格 | `insertSubqueryStyle` | INLINE/EXPAND/AUTO | AUTO | 下拉框 |
| UPDATE SET 对齐 | `updateSetAlign` | boolean | true | 复选框 |
| UPDATE SET 每行几个 | `updateSetColumnsPerRow` | int | 0(不限) | 微调框 |
| UPDATE SET 逗号位置 | `updateSetCommaPosition` | TRAILING/LEADING | TRAILING | 下拉框 |
| DELETE FROM 换行 | `deleteFromNewline` | boolean | true | 复选框 |
| MERGE INTO 换行 | `mergeIntoNewline` | boolean | true | 复选框 |
| MERGE WHEN 换行 | `mergeWhenNewline` | boolean | true | 复选框 |

### 4.4 DDL（10）

| 参数 | 键名 | 类型 | 默认值 | UI 控件 |
|------|------|------|--------|---------|
| 列定义对齐 | `columnDefAlign` | boolean | true | 复选框 |
| 列定义每行几个 | `columnDefColumnsPerRow` | int | 0(每列一行) | 微调框 |
| 列定义类型大小写 | `columnDefTypeCase` | UPPER/LOWER/PRESERVE | PRESERVE | 下拉框 |
| 约束格式 | `constraintFormat` | INLINE/SEPARATE_LINE | SEPARATE_LINE | 下拉框 |
| 约束列每行几个 | `constraintColumnsPerRow` | int | 5 | 微调框 |
| 存储子句格式 | `storageClauseFormat` | COMPACT/LINE_BREAK | COMPACT | 下拉框 |
| 索引列格式 | `indexColumnFormat` | COMPACT/ONE_PER_LINE | COMPACT | 下拉框 |
| 索引列每行几个 | `indexColumnsPerRow` | int | 5 | 微调框 |
| 分区格式 | `partitionFormat` | COMPACT/EXPAND | COMPACT | 下拉框 |
| 分区列每行几个 | `partitionColumnsPerRow` | int | 3 | 微调框 |

### 4.5 PL/SQL（14）

| 参数 | 键名 | 类型 | 默认值 | UI 控件 |
|------|------|------|--------|---------|
| 声明对齐 | `declarationAlign` | boolean | true | 复选框 |
| 参数列表模式 | `parameterListMode` | COMPACT/ONE_PER_LINE | COMPACT | 下拉框 |
| 参数每行几个 | `parameterColumnsPerRow` | int | 3 | 微调框（COMPACT 模式） |
| 参数 IN/OUT 大小写 | `parameterDirectionCase` | UPPER/LOWER/PRESERVE | UPPER | 下拉框 |
| 参数类型大小写 | `parameterTypeCase` | UPPER/LOWER/PRESERVE | PRESERVE | 下拉框 |
| THEN 换行 | `thenOnNewLine` | boolean | false | 复选框 |
| LOOP 换行 | `loopOnNewLine` | boolean | false | 复选框 |
| ELSE 独立行 | `elseOnNewLine` | boolean | true | 复选框 |
| EXCEPTION 对齐 | `exceptionAlign` | INDENT/OUTDENT | INDENT | 下拉框 |
| END 对齐 | `endAlign` | boolean | true | 复选框 |
| 括号间距 | `parenthesisSpacing` | NONE/INSIDE/BOTH | NONE | 下拉框 |
| FOR LOOP 格式 | `forLoopFormat` | COMPACT/EXPAND | COMPACT | 下拉框 |
| CASE 格式 | `caseExpressionFormat` | COMPACT/EXPAND | EXPAND | 下拉框 |
| INTO 变量对齐 | `intoVariableAlign` | boolean | true | 复选框 |

### 4.6 子查询自定义控制（3）

这是你特别提到的，按子查询出现位置独立控制：

| 参数 | 键名 | 说明 | 默认值 |
|------|------|------|--------|
| SELECT 列中子查询 | `selectListSubqueryStyle` | INLINE/EXPAND/AUTO | AUTO |
| WHERE/条件中子查询 | `whereSubqueryStyle` | INLINE/EXPAND/AUTO | AUTO |
| FROM 子句子查询（派生表） | `fromSubqueryStyle` | INLINE/EXPAND/AUTO | AUTO |

这三个参数互不影响。例如：`FROM` 中的子查询通常想展开（让人看到数据源结构），而 `WHERE IN (SELECT ...)` 中短子查询可以内联。

### 4.7 注释与空白（5）

| 参数 | 键名 | 类型 | 默认值 | UI 控件 |
|------|------|------|--------|---------|
| 注释保留 | `commentPreserve` | PRESERVE/STRIP | PRESERVE | 下拉框 |
| 注释缩进 | `commentIndent` | boolean | true | 复选框 |
| 空行处理 | `blankLineHandling` | PRESERVE/COLLAPSE/STRIP | COLLAPSE | 下拉框 |
| 行尾空格清理 | `trailingWhitespaceTrim` | boolean | true | 复选框 |
| 块开括号前空行 | `blankLineBeforeBlock` | boolean | false | 复选框 |

### 4.8 "每行N列"设计说明

部分参数采用 `columnsPerRow` 后缀设计，支持更精细的行长度控制：

```
columnsPerRow = 0: 不按列数限制（仍然受 maxLineWidth 约束）
columnsPerRow = 5: 每行最多放5个值/列，然后换行
columnsPerRow = 8: 每行最多放8个值/列

适用场景举例:
  - IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    → inListColumnsPerRow=5 时:
      IN (1, 2, 3, 4, 5,
          6, 7, 8, 9, 10)

  - CREATE TABLE t (a NUMBER, b VARCHAR2(10), c DATE, d NUMBER)
    → columnDefColumnsPerRow=0(默认，每列一行):
      CREATE TABLE t (
          a NUMBER,
          b VARCHAR2(10),
          c DATE,
          d NUMBER
      )
    → columnDefColumnsPerRow=2:
      CREATE TABLE t (
          a NUMBER,       b VARCHAR2(10),
          c DATE,         d NUMBER
      )

  - INSERT INTO t VALUES (1, 'a', SYSDATE, 100, 200, 300)
    → insertValuesPerRow=4:
      INSERT INTO t VALUES (1, 'a', SYSDATE, 100,
                            200, 300)
```

`columnsPerRow` 和 `maxLineWidth` 同时生效，**先到者先换行**。

---

## 五、子查询智能处理

### 5.1 检测时机

当 TokenProcessor 遇到 `(` 时，检查下一个有效 token：

```
( + SELECT / WITH → 标记为子查询子序列
( + 非 SELECT     → 普通括号，不特殊处理
```

### 5.2 括号匹配

```java
class SubqueryHandler {
    // 找到匹配的 )
    int findMatchingParen(List<Token> tokens, int openIndex) {
        int depth = 1;
        for (int i = openIndex + 1; i < tokens.size(); i++) {
            switch (tokens.get(i).getType()) {
                case LPAREN -> depth++;
                case RPAREN -> depth--;
                case EOF -> return -1;
            }
            if (depth == 0) return i;
        }
        return -1;
    }

    // 格式化子查询
    String formatSubquery(List<Token> subTokens, SqlType type,
                          FormatOptions opts, FormatTemplate tmpl, int indent) {

        int subqueryStyle = getStyle(type, opts); // 根据子查询位置决定
        // selectListSubqueryStyle / whereSubqueryStyle / fromSubqueryStyle

        if (subqueryStyle == INLINE) {
            return formatInline(subTokens, opts);
        }
        if (subqueryStyle == EXPAND) {
            return formatExpanded(subTokens, opts, tmpl, indent + 1);
        }
        // AUTO: 预判复杂度
        if (shouldExpand(subTokens, opts)) {
            return formatExpanded(subTokens, opts, tmpl, indent + 1);
        }
        return formatInline(subTokens, opts);
    }

    // AUTO 模式判断
    boolean shouldExpand(List<Token> tokens, FormatOptions opts) {
        // 1. 字符数超过阈值
        int totalChars = tokens.stream().mapToInt(t -> t.getText().length()).sum();
        if (totalChars > opts.getSubqueryThreshold()) return true;

        // 2. 包含复杂子句
        String joined = String.join(" ", tokens.stream().map(Token::getText).toList());
        String upper = joined.toUpperCase();
        if (upper.contains("JOIN") || upper.contains("GROUP BY")
            || upper.contains("HAVING") || upper.contains("ORDER BY")
            || upper.contains("UNION") || upper.contains("WHERE")) return true;

        // 3. 列数 > 1
        // (估算: SELECT 到 FROM 之间的逗号数)
        // 简单启发式: 如果有 FROM 之前的逗号数 > 0

        // 默认: 不展开
        return false;
    }

    // 位置感知: 同一个子查询，在 FROM 子句中默认展开
    // 在 WHERE IN 中默认内联（短）/ 展开（长）
    private int getStyle(SqlType parentType, FormatOptions opts) {
        return switch (parentType) {
            case DQL_SELECT -> opts.getSelectListSubqueryStyle();
            case DQL_SUBQUERY -> opts.getWhereSubqueryStyle(); // 条件子查询
            case DQL_SELECT_JOIN -> opts.getFromSubqueryStyle(); // FROM 子查询
            case DML_INSERT -> opts.getInsertSubqueryStyle();
            default -> opts.getSubqueryStyle(); // 通用
        };
    }
}
```

### 5.3 输出对比

```sql
-- SELECT 列中的子查询（selectListSubqueryStyle=INLINE）
SELECT e.id, (SELECT name FROM projects WHERE id = e.project_id) AS project_name
FROM employees e;

-- WHERE 中的子查询（whereSubqueryStyle=EXPAND）
SELECT * FROM employees
WHERE dept_id IN (
    SELECT dept_id
    FROM departments
    WHERE location = 'Tokyo'
);

-- FROM 中的派生表（fromSubqueryStyle=EXPAND）
SELECT a.id, a.total
FROM (
    SELECT id, SUM(amount) AS total
    FROM orders
    WHERE status = 'COMPLETED'
    GROUP BY id
) a
WHERE a.total > 1000;
```

---

## 六、方言体系

### 6.1 SqlDialect 接口

```java
public interface SqlDialect {
    String getName();
    String quoteIdentifier(String name);           // Oracle: "name", MySQL: `name`, PG: "name"
    List<SpecialClause> getSpecialClauses();        // LIMIT, RETURNING, ON DUPLICATE KEY...
    FormatOptions getDefaultOptions();              // 方言默认格式化偏好

    // 模板层关键字集
    Set<String> getLineBreakBeforeKeywords();       // 模板 lineBreakBefore 的方言补充
    Set<String> getLineBreakAfterKeywords();        // 模板 lineBreakAfter 的方言补充
    Set<String> getDqlKeywords();                   // DQL 识别用
    Set<String> getDdlKeywords();                   // DDL 识别用
    Set<String> getPlsqlKeywords();                 // PL/SQL 识别用

    // 方言特有标识符规则
    boolean isReservedWord(String word);            // 判断是否关键字（用于决定是否加引号）

    record SpecialClause(String keyword, int position) {
        static int BEFORE = 0;  // 在该关键字前换行
        static int AFTER  = 1;  // 在该关键字后换行
    }
}
```

### 6.2 四种内置方言

| 特性 | Oracle | OceanBase | MySQL | PostgreSQL |
|------|--------|-----------|-------|------------|
| 标识符引用 | `"name"` | `"name"` | `` `name` `` | `"name"` |
| 特殊子句 | — | — | `LIMIT n OFFSET m` | `LIMIT n OFFSET m` |
| | | | `ON DUPLICATE KEY` | `ON CONFLICT DO` |
| | | | | `RETURNING` |
| | | | | `DO UPDATE SET` |
| DQL 额外 | `CONNECT BY` `START WITH` | 同 Oracle | — | `DISTINCT ON` |
| PL/SQL | 完整支持 | 同 Oracle | 不支持 | `$$PLSQL` |
| 默认列对齐 | ALIGN | ALIGN | COMPACT | ALIGN |
| 默认缩进 | 4 | 4 | 2 | 4 |

### 6.3 方言扩展机制（外部注册）

```java
// 外部调用方可以注册自定义方言
DialectManager.register(new Db2Dialect());
DialectManager.register(new H2Dialect());
DialectManager.register(new MariaDBDialect());
DialectManager.register(new SQLiteDialect());
DialectManager.register(new SnowflakeDialect());
DialectManager.register(new RedshiftDialect());
DialectManager.register(new BigQueryDialect());

// 注册后可通过名称使用
SqlDialect dialect = DialectManager.forName("DB2");
```

自定义方言只需要实现 `SqlDialect` 接口的 8 个方法，不需要修改格式化引擎。

---

## 七、SettingsDialog UI 设计（参考 DataGrip 布局）

### 7.1 整体布局

```
┌── Settings (模态) ────────────────────────────────────────────────────┐
│                                                                         │
│  ┌────────────┬────────────────────────────────────────────────────────┐ │
│  │            │  SQL 格式化                                             │ │
│  │  外观与行为  │  ┌─ 方言: [Oracle            ▼] ───────────────────┐  │ │
│  │  ├── 外观    │  │ Profile: [默认 (Oracle)    ▼] [▼保存] [导入] [导出] │  │ │
│  │  │  • 主题   │  └──────────────────────────────────────────────────┘  │ │
│  │  │          │                                                        │ │
│  │  ├── 编辑器  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │  • 自动保存│  │ [通用] [DQL] [DML] [DDL] [PL/SQL] [注释/空白]     │  │ │
│  │  │          │  │                                                    │  │ │
│  │  ├── SQL    │  │  左侧配置面板 ← → 右侧预览                        │  │ │
│  │  │  • 格式化 │  │                                                    │  │ │
│  │  │          │  │  ┌──────────────┐  ┌────────────────────┐         │  │ │
│  │  ├── 数据库  │  │  │ 缩进空格数: 4 │  │ SELECT id,          │         │  │ │
│  │  │  • 连接   │  │  │ 关键字大小写:  │  │        name          │         │  │ │
│  │  │  • 元数据 │  │  │ [UPPER    ▼] │  │   FROM employees    │         │  │ │
│  │  │          │  │  │ 最大行宽: 120 │  │  WHERE status = 'A'  │         │  │ │
│  │  │          │  │  │ 换行符: [LF ▼]│  │  ORDER BY id;       │         │  │ │
│  │  │          │  │  │              │  │                      │         │  │ │
│  │  │          │  │  └──────────────┘  └────────────────────┘         │  │ │
│  │  └──────────┘  └──────────────────────────────────────────────────┘  │ │
│  │                                                                      │ │
│  │                                     [恢复默认]       [应用] [取消]    │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

### 7.2 左侧树结构

```
Settings
├── 外观 (Appearance & Behavior)
│   ├── 主题 (Theme) ← 从外观移到设置树
│   └── ...
├── 编辑器 (Editor)
│   └── 自动保存 (Auto Save) ← 从外观移到设置树
├── SQL (SQL)
│   ├── 格式化 (Formatting) ← current "SQL 格式化" tab
│   │   ├── 通用 (General)
│   │   ├── 查询 (DQL)
│   │   ├── 数据操作 (DML)
│   │   ├── 数据定义 (DDL)
│   │   ├── PL/SQL
│   │   └── 注释/空白 (Comments)
│   └── ...
├── 数据库 (Database)
│   ├── 连接 (Connections)
│   ├── 元数据 (Metadata)
│   └── ...
```

### 7.3 右侧面板分区

每个格式化分类（通用/DQL/DML/DDL/PLSQL/注释）独立为 `JPanel`，点击左侧树节点时切换。

**通用面板：**

```
┌─ 通用 ──────────────────────────────────────────────┐
│                                                       │
│  换行:                                                │
│    □ 在长行处自动换行   最大行宽: [120 ▲ ▼]             │
│    □ 空行折叠                                           │
│                                                       │
│  缩进:                                                │
│    缩进空格数: [4 ▲ ▼]                                 │
│                                                       │
│  大小写:                                              │
│    关键字: [UPPER ▼]                                   │
│                                                       │
│  换行符: [LF ▼]                                       │
│                                                       │
└───────────────────────────────────────────────────────┘
```

**DQL 面板（最复杂，分组呈现）：**

```
┌─ DQL ──────────────────────────────────────────────┐
│ ┌─ SELECT 列 ─────────────────────────────────────┐ │
│ │  列模式: [ALIGN                       ▼]         │ │
│ │  COMPACT 时每行: [0(不限) ▲ ▼] 个列              │ │
│ │  逗号位置: [行尾(TRAILING) ▼]                    │ │
│ │  □ SELECT 列内子查询展开                          │ │
│ └──────────────────────────────────────────────────┘ │
│ ┌─ FROM/JOIN ─────────────────────────────────────┐ │
│ │  □ FROM 前换行                                    │ │
│ │  □ JOIN 前换行                                    │ │
│ │  □ ON 条件对齐                                    │ │
│ │  FROM 额外缩进: [0 ▲ ▼]                           │ │
│ │  FROM 中派生表: [展开(EXPAND)           ▼]        │ │
│ └──────────────────────────────────────────────────┘ │
│ ┌─ WHERE ──────────────────────────────────────────┐ │
│ │  AND/OR 位置: [行首(LINE_START) ▼]               │ │
│ │  条件缩进: [相同(indentSize) ▼]                   │ │
│ │  子查询: [自动(AUTO) ▼]                          │ │
│ └──────────────────────────────────────────────────┘ │
│ ┌─ 集合操作 ───────────────────────────────────────┐ │
│ │  □ UNION/MINUS 前换行                             │ │
│ │  □ UNION 两侧对齐                                  │ │
│ └──────────────────────────────────────────────────┘ │
│ ┌─ IN 列表 ────────────────────────────────────────┐ │
│ │  列表格式: [紧凑(COMPACT) ▼]                      │ │
│ │  每行: [5 ▲ ▼] 个值后换行                          │ │
│ └──────────────────────────────────────────────────┘ │
│ ┌─ CTE ────────────────────────────────────────────┐ │
│ │  CTE 格式: [每行一个(ONE_PER_LINE) ▼]             │ │
│ └──────────────────────────────────────────────────┘ │
│                                                      │
│ ── 预览 ──────────────────────────────────────────  │
│ (实时更新的 JTextArea，只读，Monospaced 字体)        │
│ SELECT  id                                           │
│        ,name                                         │
│   FROM  employees                                    │
│  WHERE  status = 'A'                                 │
│                                                      │
└──────────────────────────────────────────────────────┘
```

**DDL 面板：**

```
┌─ DDL ──────────────────────────────────────────────┐
│ ┌─ CREATE TABLE ──────────────────────────────────┐ │
│ │  □ 列定义对齐                                     │ │
│ │  每个: [0(仅按行宽) ▲ ▼] 列后换行                  │ │
│ │  类型大小写: [保持不变(PRESERVE) ▼]                │ │
│ │  约束格式: [独立行(SEPARATE_LINE) ▼]               │ │
│ │  约束内每: [5 ▲ ▼] 列后换行                        │ │
│ │  存储子句: [紧凑(COMPACT) ▼]                       │ │
│ └──────────────────────────────────────────────────┘ │
│ ┌─ INDEX ──────────────────────────────────────────┐ │
│ │  索引列模式: [紧凑(COMPACT) ▼]                    │ │
│ │  每: [5 ▲ ▼] 列后换行                              │ │
│ └──────────────────────────────────────────────────┘ │
│ ┌─ 分区 ───────────────────────────────────────────┐ │
│ │  分区格式: [紧凑(COMPACT) ▼]                      │ │
│ │  每: [3 ▲ ▼] 分区后换行                            │ │
│ └──────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

**PL/SQL 面板：**

```
┌─ PL/SQL ──────────────────────────────────────────┐
│ ┌─ 声明/参数 ─────────────────────────────────────┐ │
│ │  □ 声明对齐 (:= 纵向对齐)                         │ │
│ │  参数列表: [紧凑(COMPACT) ▼]                      │ │
│ │  每行: [3 ▲ ▼] 个参数                              │ │
│ │  参数 IN/OUT 大小写: [UPPER ▼]                    │ │
│ │  参数类型大小写: [PRESERVE ▼]                      │ │
│ │  □ INTO 变量对齐                                   │ │
│ └──────────────────────────────────────────────────┘ │
│ ┌─ 控制结构 ───────────────────────────────────────┐ │
│ │  □ THEN 换行                                      │ │
│ │  □ LOOP 换行                                      │ │
│ │  □ ELSE 独立行                                     │ │
│ │  □ END 对齐                                       │ │
│ │  EXCEPTION: [缩进(INDENT) ▼]                      │ │
│ │  FOR LOOP: [紧凑(COMPACT) ▼]                      │ │
│ │  CASE: [展开(EXPAND) ▼]                           │ │
│ └──────────────────────────────────────────────────┘ │
│ ┌─ 括号间距 ───────────────────────────────────────┐ │
│ │  括号内空格: [无(NONE) ▼]                         │ │
│ └──────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

### 7.4 预览实时更新

- 每次用户修改任一选项 → 预览区域即时重新格式化预览文本
- 预览文本为固定的标准 SQL（可选择：SELECT/INSERT/CREATE TABLE/PLSQL BLOCK）
- 左下角显示当前方言

---

## 八、实施计划

### Phase 1: 基础结构搭建

| 任务 | 文件 | 行数 |
|------|------|------|
| FormatOptions 扩展（40+ 参数 + toMap/fromMap + fromIni/export/import） | `FormatOptions.java` | ~200 |
| FormatTemplate + TemplateRegistry | 2 个新文件 | ~150 |
| SqlType 枚举 + SqlTypeClassifier | 2 个新文件 | ~150 |
| TokenProcessor 重构（基于模板的断行/缩进/对齐） | 重写 `SqlFormatter.java` | ~400 |

### Phase 2: 子查询与方言

| 任务 | 文件 | 行数 |
|------|------|------|
| SubqueryHandler（检测 + 括号匹配 + AUTO 判断 + 递归格式化） | 新文件 | ~200 |
| SqlDialect 扩展 + 四种方言全部参数 | `SqlDialect.java` + 4 个 Dialect | ~200 |
| 方言扩展注册机制 | `DialectManager.java` | ~30 |

### Phase 3: DQL/DML 全对齐

| 任务 | 行数 |
|------|------|
| selectColumnMode ALIGN/COMPACT/ONE_PER_LINE | ~100 |
| from/join/on/where 全部选项 | ~150 |
| commaPosition LEADING | ~50 |
| INSERT/UPDATE/DELETE/MERGE 模板 | ~200 |
| IN 列表格式 + columnsPerRow | ~80 |

### Phase 4: DDL/PLSQL 全对齐

| 任务 | 行数 |
|------|------|
| DDL CREATE TABLE 列对齐 + constraint/storage | ~150 |
| DDL INDEX/PARTITION 格式 | ~80 |
| PLSQL declarationAlign/parameterListMode | ~150 |
| PLSQL 控制结构对齐 | ~150 |

### Phase 5: SettingsDialog UI

| 任务 | 行数 |
|------|------|
| 左侧树重构 + 节点选择路由 | ~100 |
| 通用面板 | ~50 |
| DQL 面板（最复杂，分组） | ~200 |
| DML 面板 | ~100 |
| DDL 面板 | ~100 |
| PL/SQL 面板 | ~150 |
| 注释面板 | ~50 |
| Profile 工具栏（保存/导入/导出） | ~80 |
| 实时预览 | ~80 |
| 预览模板选择 | ~30 |
| 设置持久化（WorkspaceState） | ~50 |

### Phase 6: 收尾

| 任务 | 行数 |
|------|------|
| MainFrame 集成路由 | ~50 |
| 回归测试 | ~200 |
| 边界情况处理 | ~100 |

**总计估算：约 3000-3500 行新代码**

---

## 九、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 子查询位置独立控制 | 3 个参数（SELECT列/WHERE/FROM）| 不同位置的子查询对可读性要求不同 |
| columnsPerRow 与 maxLineWidth 双约束 | 同时生效，先到先换行 | 兼容"按列数换行"和"按行宽换行"两类需求 |
| 方言通过 DialectManager 注册 | 插件式 SPI | 不修改内核即可支持新方言 |
| Template 继承父类 | 子类型模板继承父类型 | 减少重复，维护方便，DQL_SELECT_JOIN 只需在 DQL_SELECT 基础上增加 JOIN 规则 |
| 设置面板左侧树 + 右侧配置 | DataGrip 风格 | 分类清晰，大量参数不会让用户迷失 |
| preview in settings | 实时预览 | 所见即所得，降低配置门槛 |
