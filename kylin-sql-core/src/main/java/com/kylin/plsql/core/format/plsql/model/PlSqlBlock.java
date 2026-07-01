package com.kylin.plsql.core.format.plsql.model;

import java.util.ArrayList;
import java.util.List;

public class PlSqlBlock {
    public final PlSqlBlockType type;
    public PlSqlBlock parent;
    public final List<PlSqlBlock> children;
    public final List<PlSqlBlock> allNested;

    public int startTokenIdx;
    public int endTokenIdx;
    public int headerEndTokenIdx;
    public String blockName;
    public String label;

    public int declStartIdx;
    public int declEndIdx;
    public int stmtStartIdx;
    public int stmtEndIdx;
    public int exceptStartIdx;
    public int exceptEndIdx;

    public final List<Declaration> declarations;
    public final List<Statement> statements;
    public ExceptionSection exceptionSection;
    public final List<IfBranch> ifBranches;
    public final List<CaseWhen> caseWhens;
    public int elseStmtStartIdx;

    public int nestingDepth;
    public int indentLevel;
    public int alignmentAnchor;

    public PlSqlBlock(PlSqlBlockType type) {
        this.type = type;
        this.children = new ArrayList<>();
        this.allNested = new ArrayList<>();
        this.declarations = new ArrayList<>();
        this.statements = new ArrayList<>();
        this.ifBranches = new ArrayList<>();
        this.caseWhens = new ArrayList<>();
        this.startTokenIdx = -1;
        this.endTokenIdx = -1;
        this.headerEndTokenIdx = -1;
        this.declStartIdx = -1;
        this.declEndIdx = -1;
        this.stmtStartIdx = -1;
        this.stmtEndIdx = -1;
        this.exceptStartIdx = -1;
        this.exceptEndIdx = -1;
        this.elseStmtStartIdx = -1;
        this.alignmentAnchor = -1;
    }

    public boolean hasDeclSection() {
        return type == PlSqlBlockType.PACKAGE_SPEC
            || type == PlSqlBlockType.PACKAGE_BODY
            || type == PlSqlBlockType.TYPE_SPEC
            || type == PlSqlBlockType.TYPE_BODY
            || type == PlSqlBlockType.PROCEDURE
            || type == PlSqlBlockType.FUNCTION
            || type == PlSqlBlockType.TRIGGER
            || type == PlSqlBlockType.ANON_BLOCK;
    }

    public boolean hasStmtSection() {
        return type == PlSqlBlockType.PACKAGE_BODY
            || type == PlSqlBlockType.TYPE_BODY
            || type == PlSqlBlockType.PROCEDURE
            || type == PlSqlBlockType.FUNCTION
            || type == PlSqlBlockType.TRIGGER
            || type == PlSqlBlockType.ANON_BLOCK;
    }

    public boolean hasExceptionSection() {
        return type == PlSqlBlockType.PROCEDURE
            || type == PlSqlBlockType.FUNCTION
            || type == PlSqlBlockType.TRIGGER
            || type == PlSqlBlockType.ANON_BLOCK;
    }

    public boolean isNamedBlock() {
        return type == PlSqlBlockType.PACKAGE_SPEC
            || type == PlSqlBlockType.PACKAGE_BODY
            || type == PlSqlBlockType.TYPE_SPEC
            || type == PlSqlBlockType.TYPE_BODY
            || type == PlSqlBlockType.PROCEDURE
            || type == PlSqlBlockType.FUNCTION
            || type == PlSqlBlockType.TRIGGER;
    }

    public void addChild(PlSqlBlock child) {
        children.add(child);
        allNested.add(child);
        child.parent = this;
    }
}
