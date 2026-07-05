# 格式化引擎架构设计

## 目录

1. [总体架构](#1-总体架构)
2. [数据模型分层设计](#2-数据模型分层设计)
3. [模块编排与时序](#3-模块编排与时序)
4. [约束生成器分层设计](#4-约束生成器分层设计)
5. [各阶段详细类设计](#5-各阶段详细类设计)
6. [迁移方案](#6-迁移方案)

---

## 1. 总体架构

```
Source SQL
     │
     ▼
┌─────────────────────────────────────────┐
│  Parser → TokenInfo[] + PlSqlBlock[]    │  解析层
└─────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────┐
│  ConstraintGenerator                    │  约束层
│  (20+ gen → List<ConstraintSpec>)       │
└─────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────┐
│  ConstraintSolver.solve()               │  求解层
│                                         │
│  Stage 1: StructuralResolver            │  结构解析
│    → StructuralFrame[]                  │
│                                         │
│  Stage 2: LineWidthResolver             │  断行优化
│    → 修改 StructuralFrame.newline/level │
│                                         │
│  Stage 3: AlignmentResolver             │  对齐解析
│    → AlignmentCover[]                   │
│                                         │
│  Stage 4: LayoutMerger                  │  合并输出
│    → FinalLayout[]                      │
└─────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────┐
│  StringAssembler                        │  输出层
│  (读 FinalLayout → String)             │
└─────────────────────────────────────────┘
     │
     ▼
Formatted SQL
```

### 分层原则

| 层 | 包 | 单位 | 语义 |
|----|-----|------|------|
| 约束层 | `layout.spec` | 约束规则 | "应该/必须/禁止换行" |
| 结构解析层 | `layout.plan` | 层级（level） | "这行在第几层嵌套" |
| 对齐层 | `layout.align` | 列（column） | "这行输出到第几列" |
| 断行层 | `layout.break` | 字符数（char） | "行宽是否超标" |
| 输出层 | `layout.output` | 字符数（char） | "输出多少个空格" |

---

## 2. 数据模型分层设计

### 2.1 约束规格层 — `ConstraintSpec`

*包: `layout.spec`*
*替代现有: `GapConstraint`*

```java
public class ConstraintSpec {
    private int fromTokenIdx;           // 起始 token 索引
    private int toTokenIdx;             // 目标 token 索引（gap 位置）

    // ── 换行策略 ──
    private NewlineMode newlineMode;    // FORBIDDEN / REQUIRED / OPTIONAL

    // ── 缩进变化 ──
    // REQUIRED 时：currentIndentLevel + indentDelta
    // OPTIONAL 时：DP 断行后 level + indentDelta
    private int indentDelta;

    // ── 空格控制（FORBIDDEN 或未换行时生效）──
    private int minSpaces;
    private int maxSpaces;
    private int preferredSpaces;

    // ── 对齐组（语义对齐用）──
    private String alignGroupId;

    // ── DP 断行惩罚值（OPTIONAL 时生效）──
    private double breakPenalty;
}
```

### 2.2 结构骨架层 — `StructuralFrame`

*包: `layout.plan`*
*替代现有: `GapResult` 中的 `indent/level` 部分*
*输出方: `StructuralResolver`（原 ConstraintSolver 主循环）*
*消费方: `LineWidthResolver`, `LayoutMerger`*

```java
/**
 * 结构骨架 — 约束求解器主循环的直接输出。
 *
 * 语义：基于语法结构，"这一行在第几层嵌套"。
 * 单位：层级（level），非字符数。
 * 不包含对齐调整，不包含 DP 断行。
 */
public class StructuralFrame {

    // ── 换行标志 ──
    // true  = 此 gap 处必须换行（由 REQ 约束决定）
    // false = 不换行
    // DP LineWidthResolver 可以将 OPT gap 的 newline 设为 true
    private boolean newline;

    // ── 缩进层级 ──
    // 换行后的缩进层级（纯嵌套层级，未乘以 indentSize）
    // 由 ConstraintSolver 主循环中 currentIndentLevel 设置
    // LineWidthResolver 打断时也设置此值
    private int indentLevel;

    // ── 空格数 ──
    // 不换行时，与前一个 token 之间的空格数
    // 由 FORBIDDEN 约束的 preferredSpaces 决定
    private int spaces;

    // ── 保存的上层层级 ──
    // endAlign 等需要回溯上一层级时使用
    private int savedLevel;

    // ===== 构造 =====
    public StructuralFrame() {
        this.newline = false;
        this.indentLevel = 0;
        this.spaces = 1;
        this.savedLevel = 0;
    }
}
```

### 2.3 对齐覆盖层 — `AlignmentCover`

*包: `layout.align`*
*替代现有: `GapResult` 中的 alignment 副作用*
*输出方: `AlignmentResolver`（原 applyAlignment）*
*消费方: `LayoutMerger`*

```java
/**
 * 对齐覆盖 — Alignment 阶段对特定 gap 的覆盖信息。
 *
 * 语义："如果该行在某个对齐组中，输出应基于目标列位置"。
 * 单位：列（column），绝对位置。
 * 仅在需要对齐的 gap 上设置，非对齐 gap 的 isActive() = false。
 */
public class AlignmentCover {

    // ── 对齐目标列 ──
    // 此 gap 的目标对齐列（绝对列位置，从行首算起）
    // 例: P_DELIMITER 对齐 P_STR → alignColumn = 26
    // -1 表示无对齐要求
    private int alignColumn;

    // ── 空格调整量 ──
    // 当 gap 不换行时，原始 spaces += spaceAdjust 以对齐到目标列
    private int spaceAdjust;

    // ===== 构造 =====
    public AlignmentCover() {
        this.alignColumn = -1;
        this.spaceAdjust = 0;
    }

    public boolean isActive() {
        return alignColumn >= 0;
    }

    public int getAlignColumn() { return alignColumn; }
    public void setAlignColumn(int col) { this.alignColumn = col; }
    public int getSpaceAdjust() { return spaceAdjust; }
    public void setSpaceAdjust(int adj) { this.spaceAdjust = adj; }
}
```

### 2.4 最终布局层 — `FinalLayout`

*包: `layout.plan`*
*替代现有: `GapResult` 全量*
*输出方: `LayoutMerger`*
*消费方: `StringAssembler`*

```java
/**
 * 最终布局 — 合并结构骨架 + 对齐覆盖 + DP 断行后的最终决策。
 *
 * 合并规则：
 *   newline = structural.newline
 *   indent  = 
 *     if alignment.isActive():
 *       max(structural.indentLevel * indentSize, alignment.alignColumn - linePrefix)
 *     else:
 *       structural.indentLevel * indentSize
 *   spaces  =
 *     if alignment.isActive() && !newline:
 *       max(1, structural.spaces + alignment.spaceAdjust)
 *     else:
 *       structural.spaces
 */
public class FinalLayout {
    private boolean newline;        // 最终换行决策
    private int indent;             // 最终行首缩进（字符数）
    private int spaces;             // 最终 token 间空格数

    // ===== 构造 =====
    public FinalLayout() {
        this.newline = false;
        this.indent = 0;
        this.spaces = 1;
    }

    // ===== 合并工厂方法 =====
    public static FinalLayout merge(
            StructuralFrame structural,
            AlignmentCover alignment,
            int indentSize) {

        FinalLayout result = new FinalLayout();
        result.newline = structural.isNewline();

        // 计算最终 indent
        int structIndent = structural.getIndentLevel() * indentSize;
        if (alignment.isActive()) {
            result.indent = Math.max(structIndent, alignment.getAlignColumn());
        } else {
            result.indent = structIndent;
        }

        // 计算最终 spaces
        if (alignment.isActive() && !result.newline) {
            result.spaces = Math.max(1, structural.getSpaces() + alignment.getSpaceAdjust());
        } else {
            result.spaces = structural.getSpaces();
        }

        return result;
    }

    // ===== DP 断行后修改 =====
    // LineWidthResolver 决定在某个 OPT gap 处断行时调用
    public void applyBreak(int newIndentLevel, int indentSize) {
        this.newline = true;
        this.indent = newIndentLevel * indentSize;
    }
}
```

### 2.5 数据模型对比

| | 当前 `GapResult` | 新设计分层 |
|---|-----------------|-----------|
| **字段数** | 4 个, 无分层 | 3 层 x 2-4 字段 |
| **indent 语义** | 混合层级/列 | 分开: `indentLevel`(层级) + `alignColumn`(列) |
| **谁设置 newline** | 主循环 + DP 混合 | `StructuralFrame.newline` |
| **对齐影响** | 直接改 indent/spaces | `AlignmentCover` 单独一层 |
| **输出读取** | 读 gap.indent | 读 FinalLayout.indent |

---

## 3. 模块编排与时序

### 3.1 当前（问题版本）

```
PlSqlFormatterEngine.format()
  │
  ├── ConstraintGenerator.generate()
  │     └── 20+ gen → GapConstraint[]
  │
  └── ConstraintSolver.solve()
        │
        ├── 主循环: 按约束设置 newline/indent/spaces
        │   indent = currentIndentLevel * indentSize
        │
        ├── applyAlignment()    ← 先执行
        │   └── 修改 indent = 绝对列 (破坏 DP 的输入)
        │
        └── applyLineWidth()    ← 后执行
              └── 读 indent 作为 lineWidth (拿到列值, 误判)
```

### 3.2 新设计（修正版）

```
PlSqlFormatterEngine.format()
  │
  ├── ConstraintGenerator.generate()
  │     └── gen() → List<ConstraintSpec>
  │
  └── ConstraintSolver.solve()
        │
        ├── [Stage 1] StructuralResolver
        │     遍历 ConstraintSpec，按 REQ/FORB/OPT+delta 处理
        │     输出: StructuralFrame[]
        │     核心: currentIndentLevel 追踪
        │     不涉及对齐、不涉及行宽
        │
        ├── [Stage 2] LineWidthResolver
        │     读 StructuralFrame[].indentLevel * indentSize 模拟行宽
        │     当超过 maxWidth，在最低惩罚 OPT gap 处断行
        │     输出: 修改 StructuralFrame[].newline 和 indentLevel
        │     不碰对齐相关字段，只读 indentLevel
        │
        ├── [Stage 3] AlignmentResolver
        │     读 StructuralFrame[] + TokenInfo[] 计算列位置
        │     按 alignGroupId 分组，计算最大列
        │     输出: AlignmentCover[]
        │     不修改 StructuralFrame
        │
        └── [Stage 4] LayoutMerger
              合并 StructuralFrame + AlignmentCover
              输出: FinalLayout[]
```

### 3.3 时序关键变化

| 序号 | 阶段 | 输入 | 输出 | 单位 |
|------|------|------|------|------|
| 1 | StructuralResolver | `ConstraintSpec[]` | `StructuralFrame[]` | 层级 |
| 2 | LineWidthResolver | `StructuralFrame[]` | 修改 `StructuralFrame[]` | 层级 |
| 3 | AlignmentResolver | `StructuralFrame[]` + `TokenInfo[]` | `AlignmentCover[]` | 列 |
| 4 | LayoutMerger | `StructuralFrame[]` + `AlignmentCover[]` | `FinalLayout[]` | 字符数 |

**为什么 Stage 2 必须在 Stage 3 之前？**

LineWidthResolver 的 lineWidth 计算基于层级缩进（`indentLevel * indentSize`），这是一个**稳定的结构度量**。Alignment 的列位置是**视觉微调**，不作为结构度量的基础。

如果 Alignment 在 DP 之前执行：
- alignColumn = 26（P_DELIMITER 对齐到 P_STR）
- DP 把 26 当 lineWidth 起点，累加 content 到 IS（总量 ~118）
- 紧接着 channel 1 原始空白 token（`\n        ` = 9 字符）导致 127 > 120 → 误断

如果 DP 在 Alignment 之前执行：
- DP 用 indentLevel=1 (1×4=4) 当 lineWidth 起点
- 累加 content 到 IS（总量 ~60），远小于 120 → 不断
- Alignment 再将 indent 从 4 覆盖为 26（视觉正确）

### 3.4 边界情况：Alignment 导致超宽

```
     VERY_LONG_PARAMETER_NAME           VARCHAR2,
     ↑ DP 用 level=1 (4)              ↑ alignment 对齐到 column 30
       实际 content 宽度 = 25+1+2+1+80 ≈ 109 < 120 → DP 不断
       对齐后: column=30, 同样内容 → 30+80 = 110 < 120 → OK
```

如果对齐后真的超宽（如 column=50, content=80, total=130 > 120）：
- **设计决策**：Alignment 导致的超宽不触发 DP 断行。对齐是视觉调整，若导致超宽由用户通过调整 maxWidth 或参数内容解决。不允许对齐影响结构断行。

---

## 4. 约束生成器分层设计

### 4.1 三层结构

当前架构按 SQL 语法域组织（block/ / dql/ / dml/ / ddl/），这是**语法结构维度**。新增**约束角色维度**分层，每个生成器同时归属语法域和约束角色：

```
约束角色（按优先级降序）
────────────────────────────────────
第一层：缩进骨架层 (Indent Generators)
  职责：定义 SQL 语法结构的缩进嵌套
  输出：REQUIRED + indentDelta（层级变化）
        结构化断行（每个块的开/结尾）
  影响：currentIndentLevel

第二层：词法间距层 (Token Generators)
  职责：定义 token 间的间距规则
  输出：FORBIDDEN（禁止拆分、空格数控制）
        OPTIONAL（允许断行、惩罚值）
  影响：newline（禁止）、spaces

第三层：语义对齐层 (Semantic Generators)
  职责：定义语义层面的对齐要求
  输出：alignGroupId（对齐组标签）
        breakPenalty（断行优先级微调）
  不影响结构层级
```

### 4.2 各层生成器清单

#### 第一层：缩进骨架

```
BodyBlockConstraintGen     → 块结构（PACKAGE/FUNCTION/PROCEDURE 的缩进）
IfConstraintGen            → IF/ELSIF/ELSE/END IF
LoopConstraintGen          → LOOP/FOR/WHILE + END LOOP
CaseConstraintGen          → CASE_BLOCK/WHEN/ELSE
TypeConstraintGen          → TYPE 定义体
TriggerConstraintGen       → TRIGGER 块结构
BlockConstraintGen         → 块精化（begin/end/空白行）
DqlConstraintGen           → SELECT/FROM/WHERE/GROUP/ORDER/HAVING
DmlConstraintGen           → INSERT/UPDATE/DELETE/MERGE 结构
DdlConstraintGen           → CREATE/ALTER/DROP 结构
ForallConstraintGen        → FORALL 头→DML 缩进（REQUIRED indent=+1）
CaseExprConstraintGen      → CASE 表达式 EXPAND 模式（WHEN 逐行）
DbmsSqlConstraintGen       → BIND_VARIABLE/DEFINE_COLUMN per-line（REQUIRED）
```

#### 第二层：词法间距

```
CommaConstraintGen         → 逗号位置（TRAILING v.s. LEADING）
ParenConstraintGen         → 括号间距
CommentConstraintGen       → 注释放置
OperatorConstraints         → 多字符运算符（|| := . %等）的禁止拆分
CaseExprConstraintGen      → CASE 表达式 COMPACT 模式（FORBIDDEN WHEN→expr）
DbmsSqlConstraintGen       → DBMS_SQL.xxx() 链调用（FORBIDDEN 句点/括号）
addOptionalGaps()           → 兜底：未被覆盖的 gap 加 OPTIONAL
addSemicolonGaps()          → 分号后换行
```

#### 第三层：语义对齐

```
ParamConstraintGen         → 参数名对齐（ALIGNED mode）
NamedParamConstraintGen    → 命名参数 => 对齐
BlockConstraintGen          → := 声明对齐
SelectColumnConstraintGen  → SELECT 列表列对齐
FromJoinConstraintGen      → JOIN ON 对齐
InsertConstraintGen         → INSERT 列/值对齐
UpdateConstraintGen         → UPDATE SET = 对齐
MergeConstraintGen         → MERGE SET = 对齐
BulkCollectConstraintGen   → BULK COLLECT INTO 对齐
CreateTableConstraintGen   → DDL 列名/类型/存储选项对齐
AlterTableConstraintGen    → ALTER 操作对齐
CteConstraintGen           → CTE 名称对齐
```

### 4.3 包的划分

```
layout/
├── spec/                    ← 约束规格层
│   ├── ConstraintSpec.java
│   └── NewlineMode.java (enum)
│
├── plan/                    ← 布局计划层
│   ├── StructuralFrame.java
│   ├── AlignmentCover.java
│   └── FinalLayout.java
│
├── solver/                  ← 求解器
│   ├── ConstraintSolver.java    ← 编排四阶段
│   ├── StructuralResolver.java  ← 原主循环
│   ├── LineWidthResolver.java   ← 原 applyLineWidth
│   ├── AlignmentResolver.java   ← 原 applyAlignment
│   └── LayoutMerger.java        ← 新增
│
├── gen/                     ← 约束生成器（组织方式不变，分层在代码注释标注）
│   ├── ConstraintGenerator.java
│   ├── block/               ← 缩进骨架
│   ├── dql/                 ← 缩进骨架 + 语义对齐
│   ├── dml/                 ← 缩进骨架 + 语义对齐
│   ├── ddl/                 ← 缩进骨架 + 语义对齐
│   ├── comments/            ← 词法间距
│   ├── ParamConstraintGen.java    ← 语义对齐
│   ├── NamedParamConstraintGen.java
│   ├── ParenConstraintGen.java    ← 词法间距
│   ├── CommaConstraintGen.java    ← 词法间距
│   ├── CaseExprConstraintGen.java ← 缩进骨架(EXPAND) + 词法间距(COMPACT)
│   ├── ForallConstraintGen.java   ← 缩进骨架
│   └── DbmsSqlConstraintGen.java  ← 缩进骨架(per-line) + 词法间距(DBMS_SQL.)
│
└── output/                  ← 输出生成器
    └── StringAssembler.java   ← 消费 FinalLayout[]
```

---

## 5. 各阶段详细类设计

### 5.1 ConstraintSpec（约束规格）

*包: `com.kylin.plsql.core.format.plsql.layout.spec`*

```java
package com.kylin.plsql.core.format.plsql.layout.spec;

public class ConstraintSpec {

    public enum NewlineMode {
        FORBIDDEN,   // 禁止换行
        REQUIRED,    // 必须换行（结构缩进变化）
        OPTIONAL     // 可选换行（DP 决定）
    }

    // ── 定位 ──
    private final int fromTokenIdx;
    private final int toTokenIdx;

    // ── 换行策略 ──
    private NewlineMode newlineMode = NewlineMode.OPTIONAL;

    // ── 缩进量（REQUIRED / OPTIONAL + DP 生效）──
    private int indentDelta;

    // ── 空格控制（FORBIDDEN / 未换行生效）──
    private int minSpaces = 1;
    private int maxSpaces = Integer.MAX_VALUE;
    private int preferredSpaces = 1;

    // ── 对齐组（第三层语义对齐用）──
    private String alignGroupId;

    // ── DP 惩罚值（OPTIONAL 生效）──
    private double breakPenalty = 1.0;

    // ═══════════════════════════════════════
    //  Fluent API
    // ═══════════════════════════════════════

    public ConstraintSpec(int from, int to) {
        this.fromTokenIdx = from;
        this.toTokenIdx = to;
    }

    public ConstraintSpec spaces(int min, int max, int pref) {
        this.minSpaces = min;
        this.maxSpaces = max;
        this.preferredSpaces = pref;
        return this;
    }

    public ConstraintSpec newline(NewlineMode mode) {
        this.newlineMode = mode;
        return this;
    }

    public ConstraintSpec indentDelta(int delta) {
        this.indentDelta = delta;
        return this;
    }

    public ConstraintSpec alignGroup(String groupId) {
        this.alignGroupId = groupId;
        return this;
    }

    public ConstraintSpec breakPenalty(double p) {
        this.breakPenalty = p;
        return this;
    }

    // ═══════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════

    public int getFromTokenIdx() { return fromTokenIdx; }
    public int getToTokenIdx() { return toTokenIdx; }
    public NewlineMode getNewlineMode() { return newlineMode; }
    public int getIndentDelta() { return indentDelta; }
    public int getMinSpaces() { return minSpaces; }
    public int getMaxSpaces() { return maxSpaces; }
    public int getPreferredSpaces() { return preferredSpaces; }
    public String getAlignGroupId() { return alignGroupId; }
    public double getBreakPenalty() { return breakPenalty; }
}
```

### 5.2 StructuralFrame（结构骨架）

*包: `com.kylin.plsql.core.format.plsql.layout.plan`*

```java
package com.kylin.plsql.core.format.plsql.layout.plan;

public class StructuralFrame {

    private boolean newline;
    private int indentLevel;
    private int spaces;
    private int savedLevel;

    public StructuralFrame() {
        this.newline = false;
        this.indentLevel = 0;
        this.spaces = 1;
        this.savedLevel = 0;
    }

    // ── getters ──
    public boolean isNewline() { return newline; }
    public int getIndentLevel() { return indentLevel; }
    public int getSpaces() { return spaces; }
    public int getSavedLevel() { return savedLevel; }

    // ── setters (仅 ConstraintSolver 包内调用) ──
    public void setNewline(boolean v) { this.newline = v; }
    public void setIndentLevel(int v) { this.indentLevel = v; }
    public void setSpaces(int v) { this.spaces = v; }
    public void setSavedLevel(int v) { this.savedLevel = v; }
}
```

### 5.3 AlignmentCover（对齐覆盖）

*包: `com.kylin.plsql.core.format.plsql.layout.align`*

```java
package com.kylin.plsql.core.format.plsql.layout.align;

public class AlignmentCover {

    private int alignColumn;     // -1 = 无对齐
    private int spaceAdjust;

    public AlignmentCover() {
        this.alignColumn = -1;
        this.spaceAdjust = 0;
    }

    public boolean isActive() { return alignColumn >= 0; }
    public int getAlignColumn() { return alignColumn; }
    public int getSpaceAdjust() { return spaceAdjust; }

    public void setAlignColumn(int col) { this.alignColumn = col; }
    public void setSpaceAdjust(int adj) { this.spaceAdjust = adj; }

    /** 计算当前列位置加上此覆盖后的最终输出列 */
    public int computeActualColumn(int currentColumn) {
        if (alignColumn > currentColumn) {
            return alignColumn;
        }
        return currentColumn + spaceAdjust;
    }
}
```

### 5.4 FinalLayout（最终布局）

*包: `com.kylin.plsql.core.format.plsql.layout.plan`*

```java
package com.kylin.plsql.core.format.plsql.layout.plan;

import com.kylin.plsql.core.format.plsql.layout.align.AlignmentCover;

public class FinalLayout {

    private boolean newline;
    private int indent;
    private int spaces;

    public FinalLayout() {
        this.newline = false;
        this.indent = 0;
        this.spaces = 1;
    }

    // ── getters ──
    public boolean isNewline() { return newline; }
    public int getIndent() { return indent; }
    public int getSpaces() { return spaces; }

    // ── merge ──
    public static FinalLayout merge(
            StructuralFrame frame,
            AlignmentCover align,
            int indentSize) {

        FinalLayout out = new FinalLayout();
        out.newline = frame.isNewline();
        int baseIndent = frame.getIndentLevel() * indentSize;
        out.indent = (align != null && align.isActive())
            ? Math.max(baseIndent, align.getAlignColumn())
            : baseIndent;
        out.spaces = (align != null && align.isActive() && !out.newline)
            ? Math.max(1, frame.getSpaces() + align.getSpaceAdjust())
            : frame.getSpaces();
        return out;
    }

    // ── DP 断行覆盖 ──
    // 由 LineWidthResolver 调用，表示在某个 OPT gap 处决定断行
    public void applyBreak(int indentLevel, int indentSize) {
        this.newline = true;
        this.indent = indentLevel * indentSize;
    }
}
```

### 5.5 StructuralResolver（结构解析器）

*包: `com.kylin.plsql.core.format.plsql.layout.solver`*

```java
package com.kylin.plsql.core.format.plsql.layout.solver;

/**
 * 结构解析器 — 替换 ConstraintSolver 主循环。
 *
 * 输入: List<ConstraintSpec>
 * 输出: StructuralFrame[]
 *
 * 职责:
 *   遍历排序后的 ConstraintSpec，
 *   按 REQ/FORB/OPT+delta 处理，
 *   追踪 currentIndentLevel，
 *   输出 StructuralFrame（层级度量，非字符数）。
 *
 * 不涉及:
 *   行宽计算（由 LineWidthResolver 负责）
 *   对齐列位（由 AlignmentResolver 负责）
 */
public class StructuralResolver {

    private final int indentSize;

    public StructuralResolver(int indentSize) {
        this.indentSize = indentSize;
    }

    public StructuralFrame[] resolve(
            List<ConstraintSpec> specs,
            int tokenCount) {

        StructuralFrame[] frames = initFrames(tokenCount);

        // 按 fromTokenIdx 排序
        List<ConstraintSpec> sorted = new ArrayList<>(specs);
        sorted.sort(Comparator.comparingInt(ConstraintSpec::getFromTokenIdx));

        int currentLevel = 0;
        int savedLevel = 0;

        for (ConstraintSpec c : sorted) {
            int toIdx = c.getToTokenIdx();
            if (toIdx < 0 || toIdx >= tokenCount) continue;

            StructuralFrame f = frames[toIdx];

            switch (c.getNewlineMode()) {
                case REQUIRED:
                    f.setNewline(true);
                    f.setSpaces(1);
                    if (c.getIndentDelta() > 0) {
                        savedLevel = currentLevel;
                        currentLevel += c.getIndentDelta();
                        f.setIndentLevel(currentLevel);
                    } else if (c.getIndentDelta() < 0) {
                        currentLevel = Math.max(0, currentLevel + c.getIndentDelta());
                        f.setIndentLevel(currentLevel);
                    } else {
                        f.setIndentLevel(currentLevel);
                    }
                    f.setSavedLevel(savedLevel);
                    break;

                case FORBIDDEN:
                    f.setNewline(false);
                    f.setSpaces(clamp(c.getPreferredSpaces(),
                        c.getMinSpaces(), c.getMaxSpaces()));
                    break;

                case OPTIONAL:
                    if (c.getIndentDelta() != 0) {
                        // 预计算 DP 断行后的层级
                        f.setIndentLevel(currentLevel + c.getIndentDelta());
                    }
                    // newline=false, spaces=1 (默认)
                    break;
            }
        }
        return frames;
    }

    private StructuralFrame[] initFrames(int count) {
        StructuralFrame[] frames = new StructuralFrame[count];
        for (int i = 0; i < count; i++) {
            frames[i] = new StructuralFrame();
        }
        return frames;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
```

### 5.6 LineWidthResolver（断行解析器）

*包: `com.kylin.plsql.core.format.plsql.layout.solver`*

```java
package com.kylin.plsql.core.format.plsql.layout.solver;

/**
 * 行宽解析器 — 替换 ConstraintSolver.applyLineWidth。
 *
 * 输入: ConstraintSpec[], 修改 StructuralFrame[]
 * 输出: void（修改 StructuralFrame[] 的 newline 和 indentLevel）
 *
 * 职责:
 *   读 StructuralFrame.indentLevel（层级度量），
 *   乘以 indentSize 作为 lineWidth 起点，
 *   当 lineWidth + tokenWidth > maxWidth，
 *   在最低惩罚 OPT gap 处设置断行。
 *
 * 关键设计:
 *   - 只读 indentLevel（层级），不读 alignment 列值
 *   - 不写对齐相关字段
 *   - 对 channel 1 的空白 token 跳过（不计入行宽）
 */
public class LineWidthResolver {

    private final List<TokenInfo> tokens;
    private final int indentSize;
    private final int maxWidth;

    public LineWidthResolver(
            List<TokenInfo> tokens,
            int indentSize,
            int maxWidth) {
        this.tokens = tokens;
        this.indentSize = indentSize;
        this.maxWidth = maxWidth;
    }

    public void resolve(
            StructuralFrame[] frames,
            List<ConstraintSpec> specs) {

        // 索引 OPTIONAL gap → toTokenIdx
        Map<Integer, ConstraintSpec> optionalGaps = new LinkedHashMap<>();
        for (ConstraintSpec c : specs) {
            if (c.getNewlineMode() == ConstraintSpec.NewlineMode.OPTIONAL) {
                optionalGaps.put(c.getToTokenIdx(), c);
            }
        }

        int lineStart = 0;
        StructuralFrame lineStartFrame = null;
        int lineWidth = 0;

        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);

            // 跳过空白 token（不计入行宽）
            if (ti.channel > 0) continue;

            // 检查结构换行
            StructuralFrame f = (i < frames.length) ? frames[i] : null;
            if (f != null && f.isNewline()) {
                lineStart = i;
                lineStartFrame = f;
                lineWidth = f.getIndentLevel() * indentSize;
            }

            // 检查行宽是否超标
            int tw = ti.text.length();
            if (lineWidth + tw > maxWidth) {
                // 查找此行内最低惩罚值的可选断点
                int bestIdx = -1;
                double bestPenalty = Double.MAX_VALUE;
                for (int j = lineStart; j < i; j++) {
                    ConstraintSpec opt = optionalGaps.get(j + 1);
                    if (opt != null && opt.getBreakPenalty() < bestPenalty) {
                        bestIdx = j;
                        bestPenalty = opt.getBreakPenalty();
                    }
                }
                if (bestIdx >= 0) {
                    StructuralFrame brk = frames[bestIdx + 1];
                    if (brk != null) {
                        int level = lineStartFrame != null
                            ? lineStartFrame.getIndentLevel()
                            : 0;
                        ConstraintSpec opt = optionalGaps.get(bestIdx + 1);
                        if (opt != null && opt.getIndentDelta() != 0) {
                            level += opt.getIndentDelta();
                        }
                        brk.setNewline(true);
                        brk.setIndentLevel(Math.max(0, level));
                        // 重新处理此行
                        i = bestIdx;
                        continue;
                    }
                }
            }

            // 累加行宽
            lineWidth += tw;
            if (i + 1 < tokens.size()) {
                StructuralFrame next = frames[i + 1];
                if (next != null && !next.isNewline()) {
                    lineWidth += next.getSpaces();
                }
            }
        }
    }
}
```

### 5.7 AlignmentResolver（对齐解析器）

*包: `com.kylin.plsql.core.format.plsql.layout.solver`*

```java
package com.kylin.plsql.core.format.plsql.layout.solver;

/**
 * 对齐解析器 — 替换 ConstraintSolver.applyAlignment。
 *
 * 输入: StructuralFrame[], ConstraintSpec[]
 * 输出: AlignmentCover[]
 *
 * 职责:
 *   按 alignGroupId 分组约束，
 *   计算每个 gap 的当前列位置，
 *   确定组内最大列位置，
 *   输出 AlignmentCover[]（对齐覆盖信息）。
 *
 * 关键设计:
 *   不修改 StructuralFrame[]。
 *   对齐覆盖在 LayoutMerger 中合并。
 */
public class AlignmentResolver {

    private final List<TokenInfo> tokens;

    public AlignmentResolver(List<TokenInfo> tokens) {
        this.tokens = tokens;
    }

    public AlignmentCover[] resolve(
            StructuralFrame[] frames,
            List<ConstraintSpec> specs) {

        int[] columns = computeColumns(frames);
        AlignmentCover[] covers = initCovers(frames.length);

        // 按 alignGroupId 分组
        Map<String, List<ConstraintSpec>> groups = new LinkedHashMap<>();
        for (ConstraintSpec c : specs) {
            if (c.getAlignGroupId() != null) {
                groups.computeIfAbsent(
                    c.getAlignGroupId(),
                    k -> new ArrayList<>()
                ).add(c);
            }
        }

        // 处理每个对齐组
        for (List<ConstraintSpec> group : groups.values()) {
            int maxPos = 0;
            int[] positions = new int[group.size()];
            for (int i = 0; i < group.size(); i++) {
                ConstraintSpec c = group.get(i);
                int toIdx = c.getToTokenIdx();
                int pos = (toIdx >= 0 && toIdx < columns.length)
                    ? columns[toIdx] : -1;
                positions[i] = pos;
                if (pos >= 0) maxPos = Math.max(maxPos, pos);
            }

            // 设置对齐覆盖
            for (int i = 0; i < group.size(); i++) {
                ConstraintSpec c = group.get(i);
                if (positions[i] < 0) continue;
                int padNeeded = maxPos - positions[i];
                if (padNeeded <= 0) continue;

                int toIdx = c.getToTokenIdx();
                AlignmentCover cover = covers[toIdx];
                StructuralFrame frame = frames[toIdx];

                if (frame != null && !frame.isNewline()) {
                    cover.setSpaceAdjust(padNeeded);
                } else if (frame != null && frame.isNewline()) {
                    // 计算当前 prev 列 + padNeeded
                    int maxCol = columns[toIdx] + padNeeded;
                    // 如果最终 indent 小于对齐列，对齐生效
                    int baseIndent = frame.getIndentLevel() * 4; // indentSize
                    if (maxCol > baseIndent) {
                        cover.setAlignColumn(maxCol);
                    }
                }
            }
        }
        return covers;
    }

    // ── 计算每个 token 的当前列位置 ──
    private int[] computeColumns(StructuralFrame[] frames) {
        int[] cols = new int[tokens.size()];
        int col = 0;
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            StructuralFrame f = (i < frames.length) ? frames[i] : null;
            if (f != null && f.isNewline()) {
                col = f.getIndentLevel() * 4; // indentSize
            }
            if (ti.channel == 1) {
                cols[i] = -1;
                continue;
            }
            cols[i] = col;
            col += ti.text.length();
            if (i + 1 < frames.length) {
                StructuralFrame g = frames[i + 1];
                if (g != null && !g.isNewline()) {
                    col += g.getSpaces();
                }
            }
        }
        return cols;
    }

    private AlignmentCover[] initCovers(int count) {
        AlignmentCover[] covers = new AlignmentCover[count];
        for (int i = 0; i < count; i++) {
            covers[i] = new AlignmentCover();
        }
        return covers;
    }
}
```

### 5.8 LayoutMerger（布局合并器）

*包: `com.kylin.plsql.core.format.plsql.layout.solver`*

```java
package com.kylin.plsql.core.format.plsql.layout.solver;

/**
 * 布局合并器 — 新增。
 *
 * 输入: StructuralFrame[], AlignmentCover[]
 * 输出: FinalLayout[]
 *
 * 职责:
 *   将结构骨架与对齐覆盖合并为最终布局。
 *   StringAssembler 直接消费 FinalLayout[]。
 */
public class LayoutMerger {

    private final int indentSize;

    public LayoutMerger(int indentSize) {
        this.indentSize = indentSize;
    }

    public FinalLayout[] merge(
            StructuralFrame[] frames,
            AlignmentCover[] covers) {

        int n = Math.max(frames.length, covers.length);
        FinalLayout[] results = new FinalLayout[n];
        for (int i = 0; i < n; i++) {
            StructuralFrame f = (i < frames.length) ? frames[i] : new StructuralFrame();
            AlignmentCover c = (i < covers.length) ? covers[i] : new AlignmentCover();
            results[i] = FinalLayout.merge(f, c, indentSize);
        }
        return results;
    }
}
```

### 5.9 ConstraintSolver（求解器编排器）

*包: `com.kylin.plsql.core.format.plsql.layout.solver`*

```java
package com.kylin.plsql.core.format.plsql.layout.solver;

/**
 * 求解器编排器 — 编排四个阶段的执行顺序。
 *
 * 替代现有 ConstraintSolver（包路径不同）。
 * 与 StringAssembler 协商接口。
 */
public class ConstraintSolver {

    private final int indentSize;

    // 各阶段的解析器
    private final StructuralResolver structuralResolver;
    private final LineWidthResolver lineWidthResolver;
    private final AlignmentResolver alignmentResolver;
    private final LayoutMerger merger;

    public ConstraintSolver(
            List<TokenInfo> tokens,
            int indentSize,
            int maxWidth) {
        this.indentSize = indentSize;
        this.structuralResolver = new StructuralResolver(indentSize);
        this.lineWidthResolver = new LineWidthResolver(tokens, indentSize, maxWidth);
        this.alignmentResolver = new AlignmentResolver(tokens);
        this.merger = new LayoutMerger(indentSize);
    }

    public FinalLayout[] solve(List<ConstraintSpec> specs, int tokenCount) {

        // Stage 1: 结构解析
        StructuralFrame[] frames = structuralResolver.resolve(specs, tokenCount);

        // Stage 2: 行宽断行
        if (maxWidth > 0) {
            lineWidthResolver.resolve(frames, specs);
        }

        // Stage 3: 对齐解析
        AlignmentCover[] covers = alignmentResolver.resolve(frames, specs);

        // Stage 4: 合并输出
        return merger.merge(frames, covers);
    }
}
```

### 5.10 PlSqlFormatterEngine（入口修改）

```java
// 修改后的 format 方法
public String format(PlSqlModel model) {
    ConstraintGenerator gen = new ConstraintGenerator(opts, tokens);
    List<ConstraintSpec> specs = gen.generate(model.topLevelBlocks);
    //          ↑ 返回类型从 GapConstraint 改为 ConstraintSpec

    ConstraintSolver solver = new ConstraintSolver(tokens,
        opts.getIndentSize(), opts.getMaxLineWidth());
    FinalLayout[] layout = solver.solve(specs, tokens.size());
    //        ↑ 返回类型从 GapResult[] 改为 FinalLayout[]

    StringAssembler assembler = new StringAssembler(opts, tokens);
    String base = assembler.assemble(layout);
    //                     ↑ 参数类型从 GapResult[] 改为 FinalLayout[]

    String result = PlSqlPostProcessor.process(base, opts);
    return result;
}
```

### 5.11 StringAssembler（输出生成器修改）

```java
// 修改后的 assemble 方法
public String assemble(FinalLayout[] layout) {
    StringBuilder out = new StringBuilder();
    boolean startOfLine = true;
    String lastText = null;

    for (int i = 0; i < tokens.size(); i++) {
        TokenInfo ti = tokens.get(i);
        FinalLayout f = (i < layout.length) ? layout[i] : null;

        // channel 1 处理不变...
        if (ti.channel == 1) { /* 同现有逻辑 */ continue; }

        if (startOfLine) {
            int indent = (f != null && f.isNewline()) ? f.getIndent() : 0;
            appendIndent(out, indent);
            startOfLine = false;
        } else if (f != null && f.isNewline()) {
            out.append('\n');
            appendIndent(out, f.getIndent());
        } else if (f != null) {
            if (f.getSpaces() > 0 && out.length() > 0 && !endsWithNewline(out)) {
                for (int s = 0; s < f.getSpaces(); s++) out.append(' ');
            }
        }
        // ... 剩余逻辑同现有
    }
    return out.toString().trim();
}
```

---

## 6. 迁移方案

### 6.1 分步迁移

```
Phase 1: 新增数据类（不删旧的）
  - 新建 ConstraintSpec（GapConstraint 拷贝 + 清理）
  - 新建 StructuralFrame / AlignmentCover / FinalLayout
  - 旧的 GapResult / GapConstraint 保留不动

Phase 2: 新增解析器类
  - 新建 StructuralResolver / LineWidthResolver
  - 新建 AlignmentResolver / LayoutMerger
  - 旧的 ConstraintSolver.solve() 保留不动

Phase 3: 并行运行验证
  - PlSqlFormatterEngine 同时运行新旧两套
  - 比较 GapResult[] 与 FinalLayout[] 的输出是否一致
  - 差异处优先修旧

Phase 4: 切换
  - StringAssembler 增加 FinalLayout[] 重载
  - PlSqlFormatterEngine 切换到新流程
  - 删除旧类
```

### 6.2 兼容性

| 旧类 | 新类 | 兼容策略 |
|------|------|---------|
| `GapConstraint` | `ConstraintSpec` | API 等价，先用新类 |
| `GapResult` | `StructuralFrame + AlignmentCover + FinalLayout` | 分三步拆解 |
| `ConstraintSolver.solve()` | `StructuralResolver + LineWidthResolver + AlignmentResolver + LayoutMerger` | 编排不变 |

---
