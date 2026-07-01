package com.kylin.plsql.core.format.engine;

import com.kylin.plsql.core.format.FormatOptions;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/** Runtime context stack tracking current SQL construct during formatting. */
public class FormatContext {
    private final Deque<SqlContext> stack = new ArrayDeque<>();
    private final FormatOptions options;

    public int selectListAlign = -1;
    public boolean expectColumn;
    public int whereIndent;
    public int whereIndentSet;
    public int fromIndentSet;

    public FormatContext(FormatOptions options) {
        this.options = options;
        push(SqlContext.NONE);
    }

    public SqlContext current() { return stack.peek(); }
    public void push(SqlContext ctx) { stack.push(ctx); }
    public SqlContext pop() { return stack.pop(); }
    public boolean isIn(SqlContext ctx) { return stack.contains(ctx); }
    public boolean isDirectly(SqlContext ctx) { return current() == ctx; }

    public boolean isSelectList() { return isDirectly(SqlContext.SELECT_LIST); }
    public boolean isWhere() { return isDirectly(SqlContext.WHERE_CLAUSE); }
    public boolean isFrom() { return isDirectly(SqlContext.FROM_CLAUSE); }
    public boolean isInList() { return isDirectly(SqlContext.IN_LIST); }
    public boolean isPlsql() { return isIn(SqlContext.PLSQL_BLOCK); }
    public boolean isParamList() { return isDirectly(SqlContext.PARAM_LIST); }
    public boolean isInsertValsOrCols() {
        return isDirectly(SqlContext.INSERT_COLS) || isDirectly(SqlContext.INSERT_VALS);
    }

    public int getEffectiveIndentSize() {
        int base = options.getIndentSize();
        if (isPlsql() && options.getPlsqlIndentSize() > 0) {
            base = options.getPlsqlIndentSize();
        }
        return base;
    }

    public int getWhereWidth() {
        return options.getWhereIndentSize() > 0 ? options.getWhereIndentSize() : getEffectiveIndentSize();
    }

    public int getFromExtra() {
        return options.getFromClauseIndent() * getEffectiveIndentSize();
    }

    public int getMaxColumns() {
        if (isSelectList() && options.getSelectColumnsPerRow() > 0) return options.getSelectColumnsPerRow();
        if (isDirectly(SqlContext.INSERT_COLS) && options.getInsertColumnsPerRow() > 0) return options.getInsertColumnsPerRow();
        if (isDirectly(SqlContext.INSERT_VALS) && options.getInsertValuesPerRow() > 0) return options.getInsertValuesPerRow();
        if (isDirectly(SqlContext.SET_CLAUSE) && options.getUpdateSetColumnsPerRow() > 0) return options.getUpdateSetColumnsPerRow();
        if (isDirectly(SqlContext.DDL_COL_DEFS) && options.getColumnDefColumnsPerRow() > 0) return options.getColumnDefColumnsPerRow();
        if (isDirectly(SqlContext.CONSTRAINT_LIST) && options.getConstraintColumnsPerRow() > 0) return options.getConstraintColumnsPerRow();
        if (isDirectly(SqlContext.INDEX_COLS) && options.getIndexColumnsPerRow() > 0) return options.getIndexColumnsPerRow();
        if (isDirectly(SqlContext.PARAM_LIST) && options.getParameterColumnsPerRow() > 0) return options.getParameterColumnsPerRow();
        if (isInList() && options.getInListColumnsPerRow() > 0) return options.getInListColumnsPerRow();
        return 0;
    }

    public void updateFromKeyword(String upper) {
        if (Set.of("SELECT", "SELECT_LIST").contains(upper)) {
            push(SqlContext.SELECT_LIST);
            selectListAlign = -1; expectColumn = false;
        } else if ("FROM".equals(upper)) {
            push(SqlContext.FROM_CLAUSE);
            expectColumn = false;
        } else if ("WHERE".equals(upper)) {
            push(SqlContext.WHERE_CLAUSE);
            expectColumn = false;
        } else if ("GROUP".equals(upper)) {
            push(SqlContext.GROUP_BY);
            expectColumn = false;
        } else if ("HAVING".equals(upper)) {
            push(SqlContext.HAVING_CLAUSE);
            expectColumn = false;
        } else if ("ORDER".equals(upper)) {
            push(SqlContext.ORDER_BY);
            expectColumn = false;
        } else if ("INSERT".equals(upper)) {
            push(SqlContext.NONE);
        } else if ("INTO".equals(upper) && isDirectly(SqlContext.NONE)) {
            push(SqlContext.INSERT_COLS);
        } else if ("VALUES".equals(upper)) {
            push(SqlContext.INSERT_VALS);
        } else if ("UPDATE".equals(upper)) {
            push(SqlContext.NONE);
        } else if ("SET".equals(upper)) {
            push(SqlContext.SET_CLAUSE);
        } else if ("DELETE".equals(upper)) {
            push(SqlContext.NONE);
        } else if ("MERGE".equals(upper)) {
            push(SqlContext.MERGE_CLAUSE);
        } else if ("UNION".equals(upper) || "MINUS".equals(upper) || "INTERSECT".equals(upper)) {
            push(SqlContext.SET_OPERATOR);
        } else if ("CONSTRAINT".equals(upper)) {
            push(SqlContext.CONSTRAINT_LIST);
        } else if ("CREATE".equals(upper) || "TABLE".equals(upper)) {
            push(SqlContext.DDL_COL_DEFS);
        } else if ("BEGIN".equals(upper) || "DECLARE".equals(upper)) {
            push(SqlContext.PLSQL_BLOCK);
        } else if ("EXCEPTION".equals(upper)) {
            push(SqlContext.PLSQL_EXCEPTION);
        } else if ("LOOP".equals(upper)) {
            push(SqlContext.FOR_LOOP);
        } else if ("CASE".equals(upper)) {
            push(SqlContext.CASE_EXPR);
        } else if ("INDEX".equals(upper)) {
            push(SqlContext.INDEX_COLS);
        }
    }
}
