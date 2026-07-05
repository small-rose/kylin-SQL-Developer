package com.kylin.plsql.core.format;

import com.kylin.plsql.core.format.enums.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class FormatOptions {

    public enum KeywordCase { UPPER, LOWER, PRESERVE }

    // ── 通用 (5) ──
    private String dialect = "Oracle";
    private KeywordCase keywordCase = KeywordCase.UPPER;
    private int indentSize = 4;
    private int maxLineWidth = 120;
    private String lineEnding = "LF";

    // ── DQL (24) ── §9.7 + §10 + §11 + §9.6.8
    private SelectColumnMode selectColumnMode = SelectColumnMode.ALIGN;
    private int selectColumnsPerRow;
    private boolean fromClauseNewline = true;
    private int fromClauseIndent = 0;
    private boolean dmlJoinIndent = true;           // NEW V3 §9.7
    private boolean dmlJoinOnNewLine;                // NEW V3 §9.7
    private boolean joinOnAlign = true;
    private WhereAndPosition dmlWhereAndPosition = WhereAndPosition.LINE_START;
    private int whereIndentSize = 1;
    private CommaPosition commaPosition = CommaPosition.TRAILING;
    private InListFormat dmlInClauseExpand = InListFormat.COMPACT;
    private int dmlInClauseThreshold = 5;
    private int dmlInColumnsPerRow = 5;
    private SubqueryStyle dmlSubqueryFormat = SubqueryStyle.AUTO;
    private int subqueryThreshold = 80;
    private SelectColumnMode subquerySelectMode = SelectColumnMode.ALIGN;
    private boolean subqueryFromNewline = true;
    private CteFormat cteFormat = CteFormat.ONE_PER_LINE;
    private CommaPosition cteCommaPosition = CommaPosition.TRAILING;
    private boolean setOperatorNewline = true;
    private boolean setOperatorColumnAlign = true;
    private boolean cursorSelectFormat = true;

    // ── DML (19) ── §§12-15
    private InsertColumnFormat insertColumnFormat = InsertColumnFormat.COMPACT;
    private int insertColumnsPerRow;
    private int insertValuesPerRow;
    private boolean dmlInsertColumnNewline;
    private boolean dmlValuesNewline;
    private boolean dmlValuesExpand;
    private boolean dmlInsertAllIndent = true;
    private DmlReturningIntoStyle dmlReturningIntoStyle = DmlReturningIntoStyle.SINGLE_LINE;
    private boolean dmlIntoAlign = true;
    private boolean dmlUpdateSetPerLine = true;
    private boolean dmlUpdateSetAlign;                // V3: default false
    private int dmlUpdateColumnsPerRow;
    private CommaPosition updateSetCommaPosition = CommaPosition.TRAILING;
    private DmlDeleteFromHandling dmlDeleteFromHandling = DmlDeleteFromHandling.KEEP;
    private boolean mergeIntoNewline = true;
    private boolean mergeUsingNewline = true;
    private boolean mergeOnNewline = true;
    private boolean mergeWhenNewline = true;
    private boolean mergeUpdateSetAlign = true;
    private boolean dmlBulkCollectAlign = true;
    private boolean dmlUsingAlign = true;
    private SubqueryStyle insertSubqueryStyle = SubqueryStyle.AUTO;

    // ── DDL (18) ── §§16-21
    private boolean ddlColumnAlign = true;
    private int columnDefColumnsPerRow;
    private KeywordCase columnDefTypeCase = KeywordCase.PRESERVE;
    private ConstraintFormat constraintFormat = ConstraintFormat.SEPARATE_LINE;
    private int constraintColumnsPerRow = 5;
    private StorageClauseFormat storageClauseFormat = StorageClauseFormat.LINE_BREAK;
    private IndexColumnFormat indexColumnFormat = IndexColumnFormat.COMPACT;
    private int indexColumnsPerRow = 5;
    private PartitionFormat partitionFormat = PartitionFormat.EXPAND;
    private int partitionColumnsPerRow = 3;
    private boolean ddlIndexOptionPerLine = true;
    private boolean ddlConstraintStatePerLine = true;
    private boolean ddlAlterColumnPerLine = true;
    private boolean ddlAlterAddDropPerLine = true;
    private boolean ddlTbspOptionPerLine = true;
    private boolean ddlSeqOptionPerLine = true;
    private boolean ddlUserOptionPerLine = true;
    private boolean ddlGrantPrivPerLine;
    private boolean ddlFlashbackOptionPerLine = true;
    private boolean ddlFlashbackCompact;
    private DdlAnalyzeFormat ddlAnalyzeFormat = DdlAnalyzeFormat.COMPACT;
    private boolean ddlAnalyzePartitionPerLine;

    // ── PL/SQL (24) ── §§22-25
    private boolean declarationAlign = true;
    private ParameterListMode parameterListMode = ParameterListMode.COMPACT;
    private int parameterColumnsPerRow = 3;
    private boolean parameterPerLine = true;
    private ParameterAlignMode parameterAlignMode = ParameterAlignMode.ALIGNED;
    private boolean parameterNameRightAlign;
    private KeywordCase parameterDirectionCase = KeywordCase.UPPER;
    private KeywordCase parameterTypeCase = KeywordCase.PRESERVE;
    private boolean thenOnNewLine;
    private boolean loopOnNewLine;
    private boolean elseOnNewLine = true;
    private ExceptionAlign exceptionAlign = ExceptionAlign.INDENT;
    private boolean endAlign = true;
    /** 是否将 BEGIN/EXCEPTION/END 关键字缩进至与函数/PROCEDURE 声明同级（而非嵌套更深），默认 true 匹配 demo.txt 风格 */
    private boolean beginOutdent = true;
    /** 分号前是否不留空格，默认 true */
    private boolean semicolonCompact = true;
    private int plsqlIndentSize;
    private ParenthesisSpacing parenthesisSpacing = ParenthesisSpacing.NONE;
    private ForLoopFormat forLoopFormat = ForLoopFormat.COMPACT;
    private CaseExpressionFormat caseExpressionFormat = CaseExpressionFormat.EXPAND;
    private boolean intoVariableAlign = true;
    private boolean namedParameterAlign = true;
    private TriggerFormat triggerFormat = TriggerFormat.EXPAND;
    private boolean typeMemberAlign = true;
    private boolean forallFormat = true;
    private boolean dbmsSqlFormat = true;
    private boolean dbmsSqlBindPerLine = true;
    private boolean dbmsSqlColumnPerLine = true;

    // ── 子查询位置控制 (10) ── §9.6.8
    private SubqueryStyle selectListSubqueryStyle = SubqueryStyle.INLINE;
    private SubqueryStyle whereSubqueryStyle = SubqueryStyle.EXPAND;
    private SubqueryStyle fromSubqueryStyle = SubqueryStyle.EXPAND;
    private SubqueryStyle havingSubqueryStyle = SubqueryStyle.EXPAND;
    private SubqueryStyle onClauseSubqueryStyle = SubqueryStyle.INLINE;
    private SubqueryStyle existsSubqueryStyle = SubqueryStyle.INLINE;
    private SubqueryStyle lateralSubqueryStyle = SubqueryStyle.EXPAND;
    private SubqueryStyle scalarSubqueryStyle = SubqueryStyle.INLINE;
    private SubqueryStyle defaultSubqueryStyle = SubqueryStyle.EXPAND;
    private int subqueryMaxDepth = 10;

    // ── 注释与空白 (11) ── §7.9
    private boolean commentPreserve = true;
    private CommentIndent commentIndent = CommentIndent.CODE_LEVEL;
    private boolean commentSingleSpace = true;
    private boolean commentWrap;
    private TrailingCommentAlign trailingCommentAlign = TrailingCommentAlign.NONE;
    private int trailingCommentMinSpaces = 3;
    private BlockCommentStyle blockCommentStyle = BlockCommentStyle.PRESERVE;
    private boolean docCommentPreserve = true;
    private boolean docCommentParamAlign = true;
    private CommentPlacement commentPlacement = CommentPlacement.KEEP;
    private boolean blankLineBeforeComment;
    private BlankLineHandling blankLineHandling = BlankLineHandling.COLLAPSE;
    private boolean trailingWhitespaceTrim = true;
    private boolean blankLineBeforeBlock;

    // ── Profile ──
    private String activeProfile = "\u9ED8\u8BA4 (Oracle)";
    private final Map<String, FormatOptions> profiles = new LinkedHashMap<>();

    // ════════════════════════════════════
    //  Backward-compat aliases:
    //  V2 names → V3 names
    // ════════════════════════════════════
    @Deprecated private boolean joinOnNewline = true;  // V2 name, superseded by dmlJoinOnNewLine
    private boolean whereAndPositionCompat = true;      // used lazily
    private boolean updateSetAlignCompat = true;        // V3: dmlUpdateSetAlign default false
    @Deprecated private boolean inListFormatCompat = true;
    @Deprecated private boolean subqueryStyleCompat = true;

    public FormatOptions() { initDefaults(); }

    // ════════════════════════════════════
    //  Profile
    // ════════════════════════════════════

    public void initDefaults() {
        profiles.clear();

        FormatOptions def = new FormatOptions(false);
        def.dialect = "Oracle"; def.keywordCase = KeywordCase.UPPER;
        def.indentSize = 4; def.maxLineWidth = 120; def.lineEnding = "LF";
        def.selectColumnMode = SelectColumnMode.ALIGN; def.selectColumnsPerRow = 0;
        def.fromClauseNewline = true; def.fromClauseIndent = 0;
        def.dmlJoinIndent = false; def.dmlJoinOnNewLine = false; def.joinOnAlign = true;
        def.dmlWhereAndPosition = WhereAndPosition.LINE_START; def.whereIndentSize = 0;
        def.commaPosition = CommaPosition.TRAILING;
        def.dmlInClauseExpand = InListFormat.COMPACT; def.dmlInClauseThreshold = 5; def.dmlInColumnsPerRow = 5;
        def.dmlSubqueryFormat = SubqueryStyle.AUTO; def.subqueryThreshold = 80;
        def.subquerySelectMode = SelectColumnMode.ALIGN; def.subqueryFromNewline = true;
        def.cteFormat = CteFormat.ONE_PER_LINE; def.cteCommaPosition = CommaPosition.TRAILING;
        def.setOperatorNewline = true; def.setOperatorColumnAlign = true;
        def.cursorSelectFormat = true;
        def.insertColumnFormat = InsertColumnFormat.COMPACT;
        def.insertColumnsPerRow = 0; def.insertValuesPerRow = 0;
        def.dmlInsertColumnNewline = false; def.dmlValuesNewline = false; def.dmlValuesExpand = false;
        def.dmlInsertAllIndent = true;
        def.dmlReturningIntoStyle = DmlReturningIntoStyle.SINGLE_LINE; def.dmlIntoAlign = true;
        def.dmlUpdateSetPerLine = true; def.dmlUpdateSetAlign = false; def.dmlUpdateColumnsPerRow = 0;
        def.updateSetCommaPosition = CommaPosition.TRAILING;
        def.dmlDeleteFromHandling = DmlDeleteFromHandling.KEEP;
        def.mergeIntoNewline = true; def.mergeUsingNewline = true; def.mergeOnNewline = true;
        def.mergeWhenNewline = true; def.mergeUpdateSetAlign = true;
        def.dmlBulkCollectAlign = true; def.dmlUsingAlign = true;
        def.insertSubqueryStyle = SubqueryStyle.AUTO;
        def.ddlColumnAlign = true; def.columnDefColumnsPerRow = 0;
        def.columnDefTypeCase = KeywordCase.PRESERVE;
        def.constraintFormat = ConstraintFormat.SEPARATE_LINE; def.constraintColumnsPerRow = 5;
        def.storageClauseFormat = StorageClauseFormat.LINE_BREAK;
        def.indexColumnFormat = IndexColumnFormat.COMPACT; def.indexColumnsPerRow = 5;
        def.partitionFormat = PartitionFormat.EXPAND; def.partitionColumnsPerRow = 3;
        def.ddlIndexOptionPerLine = true; def.ddlConstraintStatePerLine = true;
        def.ddlAlterColumnPerLine = true; def.ddlAlterAddDropPerLine = true;
        def.ddlTbspOptionPerLine = true; def.ddlSeqOptionPerLine = true;
        def.ddlUserOptionPerLine = true; def.ddlGrantPrivPerLine = false;
        def.ddlFlashbackOptionPerLine = true; def.ddlFlashbackCompact = false;
        def.ddlAnalyzeFormat = DdlAnalyzeFormat.COMPACT; def.ddlAnalyzePartitionPerLine = false;
        def.declarationAlign = true; def.parameterListMode = ParameterListMode.COMPACT;
        def.parameterColumnsPerRow = 3;
        def.parameterPerLine = true; def.parameterAlignMode = ParameterAlignMode.ALIGNED;
        def.parameterNameRightAlign = false;
        def.parameterDirectionCase = KeywordCase.UPPER; def.parameterTypeCase = KeywordCase.PRESERVE;
        def.thenOnNewLine = false; def.loopOnNewLine = false; def.elseOnNewLine = true;
        def.exceptionAlign = ExceptionAlign.INDENT; def.endAlign = true;
        def.beginOutdent = true; def.semicolonCompact = true;
        def.plsqlIndentSize = 0;
        def.parenthesisSpacing = ParenthesisSpacing.NONE;
        def.forLoopFormat = ForLoopFormat.COMPACT; def.caseExpressionFormat = CaseExpressionFormat.EXPAND;
        def.intoVariableAlign = true; def.namedParameterAlign = true;
        def.triggerFormat = TriggerFormat.EXPAND; def.typeMemberAlign = true;
        def.forallFormat = true; def.dbmsSqlFormat = true;
        def.dbmsSqlBindPerLine = true; def.dbmsSqlColumnPerLine = true;
        def.selectListSubqueryStyle = SubqueryStyle.INLINE;
        def.whereSubqueryStyle = SubqueryStyle.EXPAND; def.fromSubqueryStyle = SubqueryStyle.EXPAND;
        def.havingSubqueryStyle = SubqueryStyle.EXPAND;
        def.onClauseSubqueryStyle = SubqueryStyle.INLINE; def.existsSubqueryStyle = SubqueryStyle.INLINE;
        def.lateralSubqueryStyle = SubqueryStyle.EXPAND; def.scalarSubqueryStyle = SubqueryStyle.INLINE;
        def.defaultSubqueryStyle = SubqueryStyle.EXPAND; def.subqueryMaxDepth = 10;
        def.commentPreserve = true; def.commentIndent = CommentIndent.CODE_LEVEL;
        def.commentSingleSpace = true; def.commentWrap = false;
        def.trailingCommentAlign = TrailingCommentAlign.NONE; def.trailingCommentMinSpaces = 3;
        def.blockCommentStyle = BlockCommentStyle.PRESERVE;
        def.docCommentPreserve = true; def.docCommentParamAlign = true;
        def.commentPlacement = CommentPlacement.KEEP; def.blankLineBeforeComment = false;
        def.blankLineHandling = BlankLineHandling.COLLAPSE;
        def.trailingWhitespaceTrim = true; def.blankLineBeforeBlock = false;
        // backward compat
        def.joinOnNewline = true;
        profiles.put("\u9ED8\u8BA4 (Oracle)", def);

        FormatOptions compact = new FormatOptions(false);
        compact.dialect = "Oracle"; compact.keywordCase = KeywordCase.UPPER;
        compact.indentSize = 2; compact.maxLineWidth = 100;
        compact.fromClauseNewline = true; compact.dmlJoinOnNewLine = true;
        compact.commaPosition = CommaPosition.TRAILING;
        compact.thenOnNewLine = true; compact.loopOnNewLine = true; compact.elseOnNewLine = true;
        compact.parameterListMode = ParameterListMode.COMPACT;
        profiles.put("\u7D27\u51D1\u578B", compact);

        FormatOptions wide = new FormatOptions(false);
        wide.dialect = "Oracle"; wide.keywordCase = KeywordCase.UPPER;
        wide.indentSize = 4; wide.maxLineWidth = 0;
        wide.fromClauseNewline = false; wide.dmlJoinOnNewLine = false;
        wide.dmlWhereAndPosition = WhereAndPosition.LINE_END;
        wide.commaPosition = CommaPosition.TRAILING;
        wide.thenOnNewLine = false; wide.loopOnNewLine = false; wide.elseOnNewLine = true;
        wide.parameterListMode = ParameterListMode.ONE_PER_LINE;
        profiles.put("\u5BBD\u6392\u7248", wide);

        if (!profiles.containsKey(activeProfile)) activeProfile = "\u9ED8\u8BA4 (Oracle)";
    }

    public void switchTo(String name) {
        FormatOptions target = profiles.get(name);
        if (target == null) return;
        copyFrom(target);
        activeProfile = name;
    }
    public void saveAs(String name) { profiles.put(name, snapshot()); activeProfile = name; }
    public void deleteProfile(String name) {
        if ("\u9ED8\u8BA4 (Oracle)".equals(name)) return;
        profiles.remove(name);
        if (activeProfile.equals(name)) { activeProfile = "\u9ED8\u8BA4 (Oracle)"; switchTo(activeProfile); }
    }
    public FormatOptions snapshot() {
        FormatOptions c = new FormatOptions(false); c.copyFrom(this); return c;
    }

    public void copyFrom(FormatOptions src) {
        this.dialect = src.dialect;
        this.keywordCase = src.keywordCase;
        this.indentSize = src.indentSize;
        this.maxLineWidth = src.maxLineWidth;
        this.lineEnding = src.lineEnding;
        this.selectColumnMode = src.selectColumnMode;
        this.selectColumnsPerRow = src.selectColumnsPerRow;
        this.fromClauseNewline = src.fromClauseNewline;
        this.fromClauseIndent = src.fromClauseIndent;
        this.dmlJoinIndent = src.dmlJoinIndent;
        this.dmlJoinOnNewLine = src.dmlJoinOnNewLine;
        this.joinOnAlign = src.joinOnAlign;
        this.dmlWhereAndPosition = src.dmlWhereAndPosition;
        this.whereIndentSize = src.whereIndentSize;
        this.commaPosition = src.commaPosition;
        this.dmlInClauseExpand = src.dmlInClauseExpand;
        this.dmlInClauseThreshold = src.dmlInClauseThreshold;
        this.dmlInColumnsPerRow = src.dmlInColumnsPerRow;
        this.dmlSubqueryFormat = src.dmlSubqueryFormat;
        this.subqueryThreshold = src.subqueryThreshold;
        this.subquerySelectMode = src.subquerySelectMode;
        this.subqueryFromNewline = src.subqueryFromNewline;
        this.cteFormat = src.cteFormat;
        this.cteCommaPosition = src.cteCommaPosition;
        this.setOperatorNewline = src.setOperatorNewline;
        this.setOperatorColumnAlign = src.setOperatorColumnAlign;
        this.cursorSelectFormat = src.cursorSelectFormat;
        this.insertColumnFormat = src.insertColumnFormat;
        this.insertColumnsPerRow = src.insertColumnsPerRow;
        this.insertValuesPerRow = src.insertValuesPerRow;
        this.dmlInsertColumnNewline = src.dmlInsertColumnNewline;
        this.dmlValuesNewline = src.dmlValuesNewline;
        this.dmlValuesExpand = src.dmlValuesExpand;
        this.dmlInsertAllIndent = src.dmlInsertAllIndent;
        this.dmlReturningIntoStyle = src.dmlReturningIntoStyle;
        this.dmlIntoAlign = src.dmlIntoAlign;
        this.dmlUpdateSetPerLine = src.dmlUpdateSetPerLine;
        this.dmlUpdateSetAlign = src.dmlUpdateSetAlign;
        this.dmlUpdateColumnsPerRow = src.dmlUpdateColumnsPerRow;
        this.updateSetCommaPosition = src.updateSetCommaPosition;
        this.dmlDeleteFromHandling = src.dmlDeleteFromHandling;
        this.mergeIntoNewline = src.mergeIntoNewline;
        this.mergeUsingNewline = src.mergeUsingNewline;
        this.mergeOnNewline = src.mergeOnNewline;
        this.mergeWhenNewline = src.mergeWhenNewline;
        this.mergeUpdateSetAlign = src.mergeUpdateSetAlign;
        this.dmlBulkCollectAlign = src.dmlBulkCollectAlign;
        this.dmlUsingAlign = src.dmlUsingAlign;
        this.insertSubqueryStyle = src.insertSubqueryStyle;
        this.ddlColumnAlign = src.ddlColumnAlign;
        this.columnDefColumnsPerRow = src.columnDefColumnsPerRow;
        this.columnDefTypeCase = src.columnDefTypeCase;
        this.constraintFormat = src.constraintFormat;
        this.constraintColumnsPerRow = src.constraintColumnsPerRow;
        this.storageClauseFormat = src.storageClauseFormat;
        this.indexColumnFormat = src.indexColumnFormat;
        this.indexColumnsPerRow = src.indexColumnsPerRow;
        this.partitionFormat = src.partitionFormat;
        this.partitionColumnsPerRow = src.partitionColumnsPerRow;
        this.ddlIndexOptionPerLine = src.ddlIndexOptionPerLine;
        this.ddlConstraintStatePerLine = src.ddlConstraintStatePerLine;
        this.ddlAlterColumnPerLine = src.ddlAlterColumnPerLine;
        this.ddlAlterAddDropPerLine = src.ddlAlterAddDropPerLine;
        this.ddlTbspOptionPerLine = src.ddlTbspOptionPerLine;
        this.ddlSeqOptionPerLine = src.ddlSeqOptionPerLine;
        this.ddlUserOptionPerLine = src.ddlUserOptionPerLine;
        this.ddlGrantPrivPerLine = src.ddlGrantPrivPerLine;
        this.ddlFlashbackOptionPerLine = src.ddlFlashbackOptionPerLine;
        this.ddlFlashbackCompact = src.ddlFlashbackCompact;
        this.ddlAnalyzeFormat = src.ddlAnalyzeFormat;
        this.ddlAnalyzePartitionPerLine = src.ddlAnalyzePartitionPerLine;
        this.declarationAlign = src.declarationAlign;
        this.parameterListMode = src.parameterListMode;
        this.parameterColumnsPerRow = src.parameterColumnsPerRow;
        this.parameterPerLine = src.parameterPerLine;
        this.parameterAlignMode = src.parameterAlignMode;
        this.parameterNameRightAlign = src.parameterNameRightAlign;
        this.parameterDirectionCase = src.parameterDirectionCase;
        this.parameterTypeCase = src.parameterTypeCase;
        this.thenOnNewLine = src.thenOnNewLine;
        this.loopOnNewLine = src.loopOnNewLine;
        this.elseOnNewLine = src.elseOnNewLine;
        this.exceptionAlign = src.exceptionAlign;
        this.endAlign = src.endAlign;
        this.beginOutdent = src.beginOutdent;
        this.semicolonCompact = src.semicolonCompact;
        this.plsqlIndentSize = src.plsqlIndentSize;
        this.parenthesisSpacing = src.parenthesisSpacing;
        this.forLoopFormat = src.forLoopFormat;
        this.caseExpressionFormat = src.caseExpressionFormat;
        this.intoVariableAlign = src.intoVariableAlign;
        this.namedParameterAlign = src.namedParameterAlign;
        this.triggerFormat = src.triggerFormat;
        this.typeMemberAlign = src.typeMemberAlign;
        this.forallFormat = src.forallFormat;
        this.dbmsSqlFormat = src.dbmsSqlFormat;
        this.dbmsSqlBindPerLine = src.dbmsSqlBindPerLine;
        this.dbmsSqlColumnPerLine = src.dbmsSqlColumnPerLine;
        this.selectListSubqueryStyle = src.selectListSubqueryStyle;
        this.whereSubqueryStyle = src.whereSubqueryStyle;
        this.fromSubqueryStyle = src.fromSubqueryStyle;
        this.havingSubqueryStyle = src.havingSubqueryStyle;
        this.onClauseSubqueryStyle = src.onClauseSubqueryStyle;
        this.existsSubqueryStyle = src.existsSubqueryStyle;
        this.lateralSubqueryStyle = src.lateralSubqueryStyle;
        this.scalarSubqueryStyle = src.scalarSubqueryStyle;
        this.defaultSubqueryStyle = src.defaultSubqueryStyle;
        this.subqueryMaxDepth = src.subqueryMaxDepth;
        this.commentPreserve = src.commentPreserve;
        this.commentIndent = src.commentIndent;
        this.commentSingleSpace = src.commentSingleSpace;
        this.commentWrap = src.commentWrap;
        this.trailingCommentAlign = src.trailingCommentAlign;
        this.trailingCommentMinSpaces = src.trailingCommentMinSpaces;
        this.blockCommentStyle = src.blockCommentStyle;
        this.docCommentPreserve = src.docCommentPreserve;
        this.docCommentParamAlign = src.docCommentParamAlign;
        this.commentPlacement = src.commentPlacement;
        this.blankLineBeforeComment = src.blankLineBeforeComment;
        this.blankLineHandling = src.blankLineHandling;
        this.trailingWhitespaceTrim = src.trailingWhitespaceTrim;
        this.blankLineBeforeBlock = src.blankLineBeforeBlock;
        this.activeProfile = src.activeProfile;
        this.joinOnNewline = src.joinOnNewline;
        this.profiles.clear();
        for (var e : src.profiles.entrySet()) {
            FormatOptions p = new FormatOptions(false);
            p.copyFrom(e.getValue());
            this.profiles.put(e.getKey(), p);
        }
    }

    // ══════════════════════════════════════════════
    //  序列化
    // ══════════════════════════════════════════════

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        putS(m, "dialect", dialect); putS(m, "keywordCase", keywordCase.name());
        putS(m, "indentSize", indentSize); putS(m, "maxLineWidth", maxLineWidth);
        putS(m, "lineEnding", lineEnding);
        putS(m, "selectColumnMode", selectColumnMode.name());
        putS(m, "selectColumnsPerRow", selectColumnsPerRow);
        putS(m, "fromClauseNewline", fromClauseNewline); putS(m, "fromClauseIndent", fromClauseIndent);
        putS(m, "dmlJoinIndent", dmlJoinIndent); putS(m, "dmlJoinOnNewLine", dmlJoinOnNewLine);
        putS(m, "joinOnAlign", joinOnAlign);
        putS(m, "dmlWhereAndPosition", dmlWhereAndPosition.name());
        putS(m, "whereIndentSize", whereIndentSize);
        putS(m, "commaPosition", commaPosition.name());
        putS(m, "dmlInClauseExpand", dmlInClauseExpand.name());
        putS(m, "dmlInClauseThreshold", dmlInClauseThreshold);
        putS(m, "dmlInColumnsPerRow", dmlInColumnsPerRow);
        putS(m, "dmlSubqueryFormat", dmlSubqueryFormat.name());
        putS(m, "subqueryThreshold", subqueryThreshold);
        putS(m, "subquerySelectMode", subquerySelectMode.name());
        putS(m, "subqueryFromNewline", subqueryFromNewline);
        putS(m, "cteFormat", cteFormat.name()); putS(m, "cteCommaPosition", cteCommaPosition.name());
        putS(m, "setOperatorNewline", setOperatorNewline);
        putS(m, "setOperatorColumnAlign", setOperatorColumnAlign);
        putS(m, "cursorSelectFormat", cursorSelectFormat);
        putS(m, "insertColumnFormat", insertColumnFormat.name());
        putS(m, "insertColumnsPerRow", insertColumnsPerRow);
        putS(m, "insertValuesPerRow", insertValuesPerRow);
        putS(m, "dmlInsertColumnNewline", dmlInsertColumnNewline);
        putS(m, "dmlValuesNewline", dmlValuesNewline); putS(m, "dmlValuesExpand", dmlValuesExpand);
        putS(m, "dmlInsertAllIndent", dmlInsertAllIndent);
        putS(m, "dmlReturningIntoStyle", dmlReturningIntoStyle.name());
        putS(m, "dmlIntoAlign", dmlIntoAlign);
        putS(m, "dmlUpdateSetPerLine", dmlUpdateSetPerLine);
        putS(m, "dmlUpdateSetAlign", dmlUpdateSetAlign);
        putS(m, "dmlUpdateColumnsPerRow", dmlUpdateColumnsPerRow);
        putS(m, "updateSetCommaPosition", updateSetCommaPosition.name());
        putS(m, "dmlDeleteFromHandling", dmlDeleteFromHandling.name());
        putS(m, "mergeIntoNewline", mergeIntoNewline);
        putS(m, "mergeUsingNewline", mergeUsingNewline); putS(m, "mergeOnNewline", mergeOnNewline);
        putS(m, "mergeWhenNewline", mergeWhenNewline);
        putS(m, "mergeUpdateSetAlign", mergeUpdateSetAlign);
        putS(m, "dmlBulkCollectAlign", dmlBulkCollectAlign); putS(m, "dmlUsingAlign", dmlUsingAlign);
        putS(m, "insertSubqueryStyle", insertSubqueryStyle.name());
        putS(m, "ddlColumnAlign", ddlColumnAlign);
        putS(m, "columnDefColumnsPerRow", columnDefColumnsPerRow);
        putS(m, "columnDefTypeCase", columnDefTypeCase.name());
        putS(m, "constraintFormat", constraintFormat.name());
        putS(m, "constraintColumnsPerRow", constraintColumnsPerRow);
        putS(m, "storageClauseFormat", storageClauseFormat.name());
        putS(m, "indexColumnFormat", indexColumnFormat.name());
        putS(m, "indexColumnsPerRow", indexColumnsPerRow);
        putS(m, "partitionFormat", partitionFormat.name());
        putS(m, "partitionColumnsPerRow", partitionColumnsPerRow);
        putS(m, "ddlIndexOptionPerLine", ddlIndexOptionPerLine);
        putS(m, "ddlConstraintStatePerLine", ddlConstraintStatePerLine);
        putS(m, "ddlAlterColumnPerLine", ddlAlterColumnPerLine);
        putS(m, "ddlAlterAddDropPerLine", ddlAlterAddDropPerLine);
        putS(m, "ddlTbspOptionPerLine", ddlTbspOptionPerLine);
        putS(m, "ddlSeqOptionPerLine", ddlSeqOptionPerLine);
        putS(m, "ddlUserOptionPerLine", ddlUserOptionPerLine);
        putS(m, "ddlGrantPrivPerLine", ddlGrantPrivPerLine);
        putS(m, "ddlFlashbackOptionPerLine", ddlFlashbackOptionPerLine);
        putS(m, "ddlFlashbackCompact", ddlFlashbackCompact);
        putS(m, "ddlAnalyzeFormat", ddlAnalyzeFormat.name());
        putS(m, "ddlAnalyzePartitionPerLine", ddlAnalyzePartitionPerLine);
        putS(m, "declarationAlign", declarationAlign);
        putS(m, "parameterListMode", parameterListMode.name());
        putS(m, "parameterColumnsPerRow", parameterColumnsPerRow);
        putS(m, "parameterPerLine", parameterPerLine);
        putS(m, "parameterAlignMode", parameterAlignMode.name());
        putS(m, "parameterNameRightAlign", parameterNameRightAlign);
        putS(m, "parameterDirectionCase", parameterDirectionCase.name());
        putS(m, "parameterTypeCase", parameterTypeCase.name());
        putS(m, "thenOnNewLine", thenOnNewLine); putS(m, "loopOnNewLine", loopOnNewLine);
        putS(m, "elseOnNewLine", elseOnNewLine);
        putS(m, "exceptionAlign", exceptionAlign.name());
        putS(m, "endAlign", endAlign); putS(m, "beginOutdent", beginOutdent);
        putS(m, "semicolonCompact", semicolonCompact);
        putS(m, "plsqlIndentSize", plsqlIndentSize);
        putS(m, "parenthesisSpacing", parenthesisSpacing.name());
        putS(m, "forLoopFormat", forLoopFormat.name());
        putS(m, "caseExpressionFormat", caseExpressionFormat.name());
        putS(m, "intoVariableAlign", intoVariableAlign);
        putS(m, "namedParameterAlign", namedParameterAlign);
        putS(m, "triggerFormat", triggerFormat.name());
        putS(m, "typeMemberAlign", typeMemberAlign);
        putS(m, "forallFormat", forallFormat);
        putS(m, "dbmsSqlFormat", dbmsSqlFormat);
        putS(m, "dbmsSqlBindPerLine", dbmsSqlBindPerLine);
        putS(m, "dbmsSqlColumnPerLine", dbmsSqlColumnPerLine);
        putS(m, "selectListSubqueryStyle", selectListSubqueryStyle.name());
        putS(m, "whereSubqueryStyle", whereSubqueryStyle.name());
        putS(m, "fromSubqueryStyle", fromSubqueryStyle.name());
        putS(m, "havingSubqueryStyle", havingSubqueryStyle.name());
        putS(m, "onClauseSubqueryStyle", onClauseSubqueryStyle.name());
        putS(m, "existsSubqueryStyle", existsSubqueryStyle.name());
        putS(m, "lateralSubqueryStyle", lateralSubqueryStyle.name());
        putS(m, "scalarSubqueryStyle", scalarSubqueryStyle.name());
        putS(m, "defaultSubqueryStyle", defaultSubqueryStyle.name());
        putS(m, "subqueryMaxDepth", subqueryMaxDepth);
        putS(m, "commentPreserve", commentPreserve);
        putS(m, "commentIndent", commentIndent.name());
        putS(m, "commentSingleSpace", commentSingleSpace);
        putS(m, "commentWrap", commentWrap);
        putS(m, "trailingCommentAlign", trailingCommentAlign.name());
        putS(m, "trailingCommentMinSpaces", trailingCommentMinSpaces);
        putS(m, "blockCommentStyle", blockCommentStyle.name());
        putS(m, "docCommentPreserve", docCommentPreserve);
        putS(m, "docCommentParamAlign", docCommentParamAlign);
        putS(m, "commentPlacement", commentPlacement.name());
        putS(m, "blankLineBeforeComment", blankLineBeforeComment);
        putS(m, "blankLineHandling", blankLineHandling.name());
        putS(m, "trailingWhitespaceTrim", trailingWhitespaceTrim);
        putS(m, "blankLineBeforeBlock", blankLineBeforeBlock);
        putS(m, "activeProfile", activeProfile);
        // backward compat serialization keys
        putS(m, "joinOnNewline", joinOnNewline);
        putS(m, "whereAndPosition", dmlWhereAndPosition.name());
        putS(m, "subqueryStyle", dmlSubqueryFormat.name());
        putS(m, "inListFormat", dmlInClauseExpand.name());
        putS(m, "inListThreshold", dmlInClauseThreshold);
        putS(m, "inListColumnsPerRow", dmlInColumnsPerRow);
        putS(m, "setOperatorAlign", setOperatorColumnAlign);
        putS(m, "columnDefAlign", ddlColumnAlign);
        putS(m, "updateSetAlign", dmlUpdateSetAlign);
        putS(m, "updateSetColumnsPerRow", dmlUpdateColumnsPerRow);
        return m;
    }

    private static void putS(Map<String, String> m, String k, Object v) {
        m.put(k, String.valueOf(v));
    }

    public static FormatOptions fromMap(Map<String, String> m) {
        FormatOptions o = new FormatOptions(false);
        if (m.containsKey("dialect")) o.dialect = m.get("dialect");
        if (m.containsKey("keywordCase")) o.keywordCase = KeywordCase.valueOf(m.get("keywordCase"));
        if (m.containsKey("indentSize")) o.indentSize = Integer.parseInt(m.get("indentSize"));
        if (m.containsKey("maxLineWidth")) o.maxLineWidth = Integer.parseInt(m.get("maxLineWidth"));
        if (m.containsKey("lineEnding")) o.lineEnding = m.get("lineEnding");
        if (m.containsKey("selectColumnMode")) o.selectColumnMode = SelectColumnMode.valueOf(m.get("selectColumnMode"));
        if (m.containsKey("selectColumnsPerRow")) o.selectColumnsPerRow = Integer.parseInt(m.get("selectColumnsPerRow"));
        if (m.containsKey("fromClauseNewline")) o.fromClauseNewline = Boolean.parseBoolean(m.get("fromClauseNewline"));
        if (m.containsKey("fromClauseIndent")) o.fromClauseIndent = Integer.parseInt(m.get("fromClauseIndent"));
        if (m.containsKey("dmlJoinIndent")) o.dmlJoinIndent = Boolean.parseBoolean(m.get("dmlJoinIndent"));
        if (m.containsKey("dmlJoinOnNewLine")) o.dmlJoinOnNewLine = Boolean.parseBoolean(m.get("dmlJoinOnNewLine"));
        if (m.containsKey("joinOnNewline")) o.dmlJoinOnNewLine = Boolean.parseBoolean(m.get("joinOnNewline"));
        if (m.containsKey("joinOnAlign")) o.joinOnAlign = Boolean.parseBoolean(m.get("joinOnAlign"));
        if (m.containsKey("dmlWhereAndPosition") || m.containsKey("whereAndPosition")) {
            String v = m.containsKey("dmlWhereAndPosition") ? m.get("dmlWhereAndPosition") : m.get("whereAndPosition");
            o.dmlWhereAndPosition = WhereAndPosition.valueOf(v);
        }
        if (m.containsKey("whereIndentSize")) o.whereIndentSize = Integer.parseInt(m.get("whereIndentSize"));
        if (m.containsKey("commaPosition")) o.commaPosition = CommaPosition.valueOf(m.get("commaPosition"));
        if (m.containsKey("dmlInClauseExpand") || m.containsKey("inListFormat")) {
            String v = m.containsKey("dmlInClauseExpand") ? m.get("dmlInClauseExpand") : m.get("inListFormat");
            o.dmlInClauseExpand = InListFormat.valueOf(v);
        }
        if (m.containsKey("dmlInClauseThreshold") || m.containsKey("inListThreshold")) {
            String v = m.containsKey("dmlInClauseThreshold") ? m.get("dmlInClauseThreshold") : m.get("inListThreshold");
            o.dmlInClauseThreshold = Integer.parseInt(v);
        }
        if (m.containsKey("dmlInColumnsPerRow") || m.containsKey("inListColumnsPerRow")) {
            String v = m.containsKey("dmlInColumnsPerRow") ? m.get("dmlInColumnsPerRow") : m.get("inListColumnsPerRow");
            o.dmlInColumnsPerRow = Integer.parseInt(v);
        }
        if (m.containsKey("dmlSubqueryFormat") || m.containsKey("subqueryStyle")) {
            String v = m.containsKey("dmlSubqueryFormat") ? m.get("dmlSubqueryFormat") : m.get("subqueryStyle");
            o.dmlSubqueryFormat = SubqueryStyle.valueOf(v);
        }
        if (m.containsKey("subqueryThreshold")) o.subqueryThreshold = Integer.parseInt(m.get("subqueryThreshold"));
        if (m.containsKey("subquerySelectMode")) o.subquerySelectMode = SelectColumnMode.valueOf(m.get("subquerySelectMode"));
        if (m.containsKey("subqueryFromNewline")) o.subqueryFromNewline = Boolean.parseBoolean(m.get("subqueryFromNewline"));
        if (m.containsKey("cteFormat")) o.cteFormat = CteFormat.valueOf(m.get("cteFormat"));
        if (m.containsKey("cteCommaPosition")) o.cteCommaPosition = CommaPosition.valueOf(m.get("cteCommaPosition"));
        if (m.containsKey("setOperatorNewline")) o.setOperatorNewline = Boolean.parseBoolean(m.get("setOperatorNewline"));
        if (m.containsKey("setOperatorColumnAlign") || m.containsKey("setOperatorAlign")) {
            String v = m.containsKey("setOperatorColumnAlign") ? m.get("setOperatorColumnAlign") : m.get("setOperatorAlign");
            o.setOperatorColumnAlign = Boolean.parseBoolean(v);
        }
        if (m.containsKey("cursorSelectFormat")) o.cursorSelectFormat = Boolean.parseBoolean(m.get("cursorSelectFormat"));
        if (m.containsKey("insertColumnFormat")) o.insertColumnFormat = InsertColumnFormat.valueOf(m.get("insertColumnFormat"));
        if (m.containsKey("insertColumnsPerRow")) o.insertColumnsPerRow = Integer.parseInt(m.get("insertColumnsPerRow"));
        if (m.containsKey("insertValuesPerRow")) o.insertValuesPerRow = Integer.parseInt(m.get("insertValuesPerRow"));
        if (m.containsKey("dmlInsertColumnNewline")) o.dmlInsertColumnNewline = Boolean.parseBoolean(m.get("dmlInsertColumnNewline"));
        if (m.containsKey("dmlValuesNewline")) o.dmlValuesNewline = Boolean.parseBoolean(m.get("dmlValuesNewline"));
        if (m.containsKey("dmlValuesExpand")) o.dmlValuesExpand = Boolean.parseBoolean(m.get("dmlValuesExpand"));
        if (m.containsKey("dmlInsertAllIndent")) o.dmlInsertAllIndent = Boolean.parseBoolean(m.get("dmlInsertAllIndent"));
        if (m.containsKey("dmlReturningIntoStyle")) o.dmlReturningIntoStyle = DmlReturningIntoStyle.valueOf(m.get("dmlReturningIntoStyle"));
        if (m.containsKey("dmlIntoAlign")) o.dmlIntoAlign = Boolean.parseBoolean(m.get("dmlIntoAlign"));
        if (m.containsKey("dmlUpdateSetPerLine")) o.dmlUpdateSetPerLine = Boolean.parseBoolean(m.get("dmlUpdateSetPerLine"));
        if (m.containsKey("dmlUpdateSetAlign") || m.containsKey("updateSetAlign")) {
            String v = m.containsKey("dmlUpdateSetAlign") ? m.get("dmlUpdateSetAlign") : m.get("updateSetAlign");
            o.dmlUpdateSetAlign = Boolean.parseBoolean(v);
        }
        if (m.containsKey("dmlUpdateColumnsPerRow") || m.containsKey("updateSetColumnsPerRow")) {
            String v = m.containsKey("dmlUpdateColumnsPerRow") ? m.get("dmlUpdateColumnsPerRow") : m.get("updateSetColumnsPerRow");
            o.dmlUpdateColumnsPerRow = Integer.parseInt(v);
        }
        if (m.containsKey("updateSetCommaPosition")) o.updateSetCommaPosition = CommaPosition.valueOf(m.get("updateSetCommaPosition"));
        if (m.containsKey("dmlDeleteFromHandling")) o.dmlDeleteFromHandling = DmlDeleteFromHandling.valueOf(m.get("dmlDeleteFromHandling"));
        if (m.containsKey("mergeIntoNewline")) o.mergeIntoNewline = Boolean.parseBoolean(m.get("mergeIntoNewline"));
        if (m.containsKey("mergeUsingNewline")) o.mergeUsingNewline = Boolean.parseBoolean(m.get("mergeUsingNewline"));
        if (m.containsKey("mergeOnNewline")) o.mergeOnNewline = Boolean.parseBoolean(m.get("mergeOnNewline"));
        if (m.containsKey("mergeWhenNewline")) o.mergeWhenNewline = Boolean.parseBoolean(m.get("mergeWhenNewline"));
        if (m.containsKey("mergeUpdateSetAlign")) o.mergeUpdateSetAlign = Boolean.parseBoolean(m.get("mergeUpdateSetAlign"));
        if (m.containsKey("dmlBulkCollectAlign")) o.dmlBulkCollectAlign = Boolean.parseBoolean(m.get("dmlBulkCollectAlign"));
        if (m.containsKey("dmlUsingAlign")) o.dmlUsingAlign = Boolean.parseBoolean(m.get("dmlUsingAlign"));
        if (m.containsKey("insertSubqueryStyle")) o.insertSubqueryStyle = SubqueryStyle.valueOf(m.get("insertSubqueryStyle"));
        if (m.containsKey("ddlColumnAlign") || m.containsKey("columnDefAlign")) {
            String v = m.containsKey("ddlColumnAlign") ? m.get("ddlColumnAlign") : m.get("columnDefAlign");
            o.ddlColumnAlign = Boolean.parseBoolean(v);
        }
        if (m.containsKey("columnDefColumnsPerRow")) o.columnDefColumnsPerRow = Integer.parseInt(m.get("columnDefColumnsPerRow"));
        if (m.containsKey("columnDefTypeCase")) o.columnDefTypeCase = KeywordCase.valueOf(m.get("columnDefTypeCase"));
        if (m.containsKey("constraintFormat")) o.constraintFormat = ConstraintFormat.valueOf(m.get("constraintFormat"));
        if (m.containsKey("constraintColumnsPerRow")) o.constraintColumnsPerRow = Integer.parseInt(m.get("constraintColumnsPerRow"));
        if (m.containsKey("storageClauseFormat")) o.storageClauseFormat = StorageClauseFormat.valueOf(m.get("storageClauseFormat"));
        if (m.containsKey("indexColumnFormat")) o.indexColumnFormat = IndexColumnFormat.valueOf(m.get("indexColumnFormat"));
        if (m.containsKey("indexColumnsPerRow")) o.indexColumnsPerRow = Integer.parseInt(m.get("indexColumnsPerRow"));
        if (m.containsKey("partitionFormat")) o.partitionFormat = PartitionFormat.valueOf(m.get("partitionFormat"));
        if (m.containsKey("partitionColumnsPerRow")) o.partitionColumnsPerRow = Integer.parseInt(m.get("partitionColumnsPerRow"));
        if (m.containsKey("ddlIndexOptionPerLine")) o.ddlIndexOptionPerLine = Boolean.parseBoolean(m.get("ddlIndexOptionPerLine"));
        if (m.containsKey("ddlConstraintStatePerLine")) o.ddlConstraintStatePerLine = Boolean.parseBoolean(m.get("ddlConstraintStatePerLine"));
        if (m.containsKey("ddlAlterColumnPerLine")) o.ddlAlterColumnPerLine = Boolean.parseBoolean(m.get("ddlAlterColumnPerLine"));
        if (m.containsKey("ddlAlterAddDropPerLine")) o.ddlAlterAddDropPerLine = Boolean.parseBoolean(m.get("ddlAlterAddDropPerLine"));
        if (m.containsKey("ddlTbspOptionPerLine")) o.ddlTbspOptionPerLine = Boolean.parseBoolean(m.get("ddlTbspOptionPerLine"));
        if (m.containsKey("ddlSeqOptionPerLine")) o.ddlSeqOptionPerLine = Boolean.parseBoolean(m.get("ddlSeqOptionPerLine"));
        if (m.containsKey("ddlUserOptionPerLine")) o.ddlUserOptionPerLine = Boolean.parseBoolean(m.get("ddlUserOptionPerLine"));
        if (m.containsKey("ddlGrantPrivPerLine")) o.ddlGrantPrivPerLine = Boolean.parseBoolean(m.get("ddlGrantPrivPerLine"));
        if (m.containsKey("ddlFlashbackOptionPerLine")) o.ddlFlashbackOptionPerLine = Boolean.parseBoolean(m.get("ddlFlashbackOptionPerLine"));
        if (m.containsKey("ddlFlashbackCompact")) o.ddlFlashbackCompact = Boolean.parseBoolean(m.get("ddlFlashbackCompact"));
        if (m.containsKey("ddlAnalyzeFormat")) o.ddlAnalyzeFormat = DdlAnalyzeFormat.valueOf(m.get("ddlAnalyzeFormat"));
        if (m.containsKey("ddlAnalyzePartitionPerLine")) o.ddlAnalyzePartitionPerLine = Boolean.parseBoolean(m.get("ddlAnalyzePartitionPerLine"));
        if (m.containsKey("declarationAlign")) o.declarationAlign = Boolean.parseBoolean(m.get("declarationAlign"));
        if (m.containsKey("parameterListMode")) o.parameterListMode = ParameterListMode.valueOf(m.get("parameterListMode"));
        if (m.containsKey("parameterColumnsPerRow")) o.parameterColumnsPerRow = Integer.parseInt(m.get("parameterColumnsPerRow"));
        if (m.containsKey("parameterPerLine")) o.parameterPerLine = Boolean.parseBoolean(m.get("parameterPerLine"));
        if (m.containsKey("parameterAlignMode")) o.parameterAlignMode = ParameterAlignMode.valueOf(m.get("parameterAlignMode"));
        if (m.containsKey("parameterNameRightAlign")) o.parameterNameRightAlign = Boolean.parseBoolean(m.get("parameterNameRightAlign"));
        if (m.containsKey("parameterDirectionCase")) o.parameterDirectionCase = KeywordCase.valueOf(m.get("parameterDirectionCase"));
        if (m.containsKey("parameterTypeCase")) o.parameterTypeCase = KeywordCase.valueOf(m.get("parameterTypeCase"));
        if (m.containsKey("thenOnNewLine")) o.thenOnNewLine = Boolean.parseBoolean(m.get("thenOnNewLine"));
        if (m.containsKey("loopOnNewLine")) o.loopOnNewLine = Boolean.parseBoolean(m.get("loopOnNewLine"));
        if (m.containsKey("elseOnNewLine")) o.elseOnNewLine = Boolean.parseBoolean(m.get("elseOnNewLine"));
        if (m.containsKey("exceptionAlign")) o.exceptionAlign = ExceptionAlign.valueOf(m.get("exceptionAlign"));
        if (m.containsKey("endAlign")) o.endAlign = Boolean.parseBoolean(m.get("endAlign"));
        if (m.containsKey("beginOutdent")) o.beginOutdent = Boolean.parseBoolean(m.get("beginOutdent"));
        if (m.containsKey("semicolonCompact")) o.semicolonCompact = Boolean.parseBoolean(m.get("semicolonCompact"));
        if (m.containsKey("plsqlIndentSize")) o.plsqlIndentSize = Integer.parseInt(m.get("plsqlIndentSize"));
        if (m.containsKey("parenthesisSpacing")) o.parenthesisSpacing = ParenthesisSpacing.valueOf(m.get("parenthesisSpacing"));
        if (m.containsKey("forLoopFormat")) o.forLoopFormat = ForLoopFormat.valueOf(m.get("forLoopFormat"));
        if (m.containsKey("caseExpressionFormat")) o.caseExpressionFormat = CaseExpressionFormat.valueOf(m.get("caseExpressionFormat"));
        if (m.containsKey("intoVariableAlign")) o.intoVariableAlign = Boolean.parseBoolean(m.get("intoVariableAlign"));
        if (m.containsKey("namedParameterAlign")) o.namedParameterAlign = Boolean.parseBoolean(m.get("namedParameterAlign"));
        if (m.containsKey("triggerFormat")) o.triggerFormat = TriggerFormat.valueOf(m.get("triggerFormat"));
        if (m.containsKey("typeMemberAlign")) o.typeMemberAlign = Boolean.parseBoolean(m.get("typeMemberAlign"));
        if (m.containsKey("forallFormat")) o.forallFormat = Boolean.parseBoolean(m.get("forallFormat"));
        if (m.containsKey("dbmsSqlFormat")) o.dbmsSqlFormat = Boolean.parseBoolean(m.get("dbmsSqlFormat"));
        if (m.containsKey("dbmsSqlBindPerLine")) o.dbmsSqlBindPerLine = Boolean.parseBoolean(m.get("dbmsSqlBindPerLine"));
        if (m.containsKey("dbmsSqlColumnPerLine")) o.dbmsSqlColumnPerLine = Boolean.parseBoolean(m.get("dbmsSqlColumnPerLine"));
        if (m.containsKey("selectListSubqueryStyle")) o.selectListSubqueryStyle = SubqueryStyle.valueOf(m.get("selectListSubqueryStyle"));
        if (m.containsKey("whereSubqueryStyle")) o.whereSubqueryStyle = SubqueryStyle.valueOf(m.get("whereSubqueryStyle"));
        if (m.containsKey("fromSubqueryStyle")) o.fromSubqueryStyle = SubqueryStyle.valueOf(m.get("fromSubqueryStyle"));
        if (m.containsKey("havingSubqueryStyle")) o.havingSubqueryStyle = SubqueryStyle.valueOf(m.get("havingSubqueryStyle"));
        if (m.containsKey("onClauseSubqueryStyle")) o.onClauseSubqueryStyle = SubqueryStyle.valueOf(m.get("onClauseSubqueryStyle"));
        if (m.containsKey("existsSubqueryStyle")) o.existsSubqueryStyle = SubqueryStyle.valueOf(m.get("existsSubqueryStyle"));
        if (m.containsKey("lateralSubqueryStyle")) o.lateralSubqueryStyle = SubqueryStyle.valueOf(m.get("lateralSubqueryStyle"));
        if (m.containsKey("scalarSubqueryStyle")) o.scalarSubqueryStyle = SubqueryStyle.valueOf(m.get("scalarSubqueryStyle"));
        if (m.containsKey("defaultSubqueryStyle")) o.defaultSubqueryStyle = SubqueryStyle.valueOf(m.get("defaultSubqueryStyle"));
        if (m.containsKey("subqueryMaxDepth")) o.subqueryMaxDepth = Integer.parseInt(m.get("subqueryMaxDepth"));
        if (m.containsKey("commentPreserve")) o.commentPreserve = Boolean.parseBoolean(m.get("commentPreserve"));
        if (m.containsKey("commentIndent")) o.commentIndent = CommentIndent.valueOf(m.get("commentIndent"));
        if (m.containsKey("commentSingleSpace")) o.commentSingleSpace = Boolean.parseBoolean(m.get("commentSingleSpace"));
        if (m.containsKey("commentWrap")) o.commentWrap = Boolean.parseBoolean(m.get("commentWrap"));
        if (m.containsKey("trailingCommentAlign")) o.trailingCommentAlign = TrailingCommentAlign.valueOf(m.get("trailingCommentAlign"));
        if (m.containsKey("trailingCommentMinSpaces")) o.trailingCommentMinSpaces = Integer.parseInt(m.get("trailingCommentMinSpaces"));
        if (m.containsKey("blockCommentStyle")) o.blockCommentStyle = BlockCommentStyle.valueOf(m.get("blockCommentStyle"));
        if (m.containsKey("docCommentPreserve")) o.docCommentPreserve = Boolean.parseBoolean(m.get("docCommentPreserve"));
        if (m.containsKey("docCommentParamAlign")) o.docCommentParamAlign = Boolean.parseBoolean(m.get("docCommentParamAlign"));
        if (m.containsKey("commentPlacement")) o.commentPlacement = CommentPlacement.valueOf(m.get("commentPlacement"));
        if (m.containsKey("blankLineBeforeComment")) o.blankLineBeforeComment = Boolean.parseBoolean(m.get("blankLineBeforeComment"));
        if (m.containsKey("blankLineHandling")) o.blankLineHandling = BlankLineHandling.valueOf(m.get("blankLineHandling"));
        if (m.containsKey("trailingWhitespaceTrim")) o.trailingWhitespaceTrim = Boolean.parseBoolean(m.get("trailingWhitespaceTrim"));
        if (m.containsKey("blankLineBeforeBlock")) o.blankLineBeforeBlock = Boolean.parseBoolean(m.get("blankLineBeforeBlock"));
        if (m.containsKey("activeProfile")) o.activeProfile = m.get("activeProfile");
        // backward compat deserialization aliases
        if (m.containsKey("joinOnNewline") && !m.containsKey("dmlJoinOnNewLine"))
            o.dmlJoinOnNewLine = Boolean.parseBoolean(m.get("joinOnNewline"));
        return o;
    }

    public Map<String, Map<String, String>> profilesToMap() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (var e : profiles.entrySet()) result.put(e.getKey(), e.getValue().toMap());
        return result;
    }

    public void profilesFromMap(Map<String, Map<String, String>> data) {
        profiles.clear();
        if (data == null) { initDefaults(); return; }
        for (var e : data.entrySet()) profiles.put(e.getKey(), fromMap(e.getValue()));
        if (!profiles.containsKey(activeProfile)) activeProfile = "\u9ED8\u8BA4 (Oracle)";
    }

    public String getActiveProfile() { return activeProfile; }
    public void setActiveProfile(String v) { this.activeProfile = v; }
    public String getDialect() { return dialect; }
    public void setDialect(String v) { this.dialect = v; }
    public Map<String, FormatOptions> getProfiles() { return profiles; }

    // ── 通用 ──
    public KeywordCase getKeywordCase() { return keywordCase; }
    public void setKeywordCase(KeywordCase v) { this.keywordCase = v; }
    public int getIndentSize() { return indentSize; }
    public void setIndentSize(int v) { this.indentSize = v; }
    public int getMaxLineWidth() { return maxLineWidth; }
    public void setMaxLineWidth(int v) { this.maxLineWidth = v; }
    public String getLineEnding() { return lineEnding; }
    public void setLineEnding(String v) { this.lineEnding = v; }

    // ── DQL ──
    /** @deprecated use {@link #getSelectColumnMode()} */
    @Deprecated public boolean isAlignSelectColumns() { return selectColumnMode == SelectColumnMode.ALIGN; }
    /** @deprecated use {@link #setSelectColumnMode(SelectColumnMode)} */
    @Deprecated public void setAlignSelectColumns(boolean v) { this.selectColumnMode = v ? SelectColumnMode.ALIGN : SelectColumnMode.COMPACT; }
    public SelectColumnMode getSelectColumnMode() { return selectColumnMode; }
    public void setSelectColumnMode(SelectColumnMode v) { this.selectColumnMode = v; }
    public int getSelectColumnsPerRow() { return selectColumnsPerRow; }
    public void setSelectColumnsPerRow(int v) { this.selectColumnsPerRow = v; }
    public boolean isFromClauseNewline() { return fromClauseNewline; }
    public void setFromClauseNewline(boolean v) { this.fromClauseNewline = v; }
    public int getFromClauseIndent() { return fromClauseIndent; }
    public void setFromClauseIndent(int v) { this.fromClauseIndent = v; }
    public boolean isDmlJoinIndent() { return dmlJoinIndent; }
    public void setDmlJoinIndent(boolean v) { this.dmlJoinIndent = v; }
    /** JOIN 前换行 (V3 §9.7) */
    public boolean isDmlJoinOnNewLine() { return dmlJoinOnNewLine; }
    public void setDmlJoinOnNewLine(boolean v) { this.dmlJoinOnNewLine = v; }
    /** @deprecated use {@link #isDmlJoinOnNewLine()} */
    @Deprecated public boolean isJoinOnNewline() { return dmlJoinOnNewLine; }
    /** @deprecated use {@link #setDmlJoinOnNewLine(boolean)} */
    @Deprecated public void setJoinOnNewline(boolean v) { this.dmlJoinOnNewLine = v; }
    public boolean isJoinOnAlign() { return joinOnAlign; }
    public void setJoinOnAlign(boolean v) { this.joinOnAlign = v; }
    /** V3 §9.7: dmlWhereAndPosition */
    public WhereAndPosition getDmlWhereAndPosition() { return dmlWhereAndPosition; }
    public void setDmlWhereAndPosition(WhereAndPosition v) { this.dmlWhereAndPosition = v; }
    /** @deprecated use {@link #getDmlWhereAndPosition()} */
    @Deprecated public WhereAndPosition getWhereAndPosition() { return dmlWhereAndPosition; }
    /** @deprecated use {@link #setDmlWhereAndPosition(WhereAndPosition)} */
    @Deprecated public void setWhereAndPosition(WhereAndPosition v) { this.dmlWhereAndPosition = v; }
    public int getWhereIndentSize() { return whereIndentSize; }
    public void setWhereIndentSize(int v) { this.whereIndentSize = v; }
    public CommaPosition getCommaPosition() { return commaPosition; }
    public void setCommaPosition(CommaPosition v) { this.commaPosition = v; }
    public InListFormat getDmlInClauseExpand() { return dmlInClauseExpand; }
    public void setDmlInClauseExpand(InListFormat v) { this.dmlInClauseExpand = v; }
    /** @deprecated use {@link #getDmlInClauseExpand()} */
    @Deprecated public InListFormat getInListFormat() { return dmlInClauseExpand; }
    /** @deprecated use {@link #setDmlInClauseExpand(InListFormat)} */
    @Deprecated public void setInListFormat(InListFormat v) { this.dmlInClauseExpand = v; }
    public int getDmlInClauseThreshold() { return dmlInClauseThreshold; }
    public void setDmlInClauseThreshold(int v) { this.dmlInClauseThreshold = v; }
    /** @deprecated use {@link #getDmlInClauseThreshold()} */
    @Deprecated public int getInListThreshold() { return dmlInClauseThreshold; }
    /** @deprecated use {@link #setDmlInClauseThreshold(int)} */
    @Deprecated public void setInListThreshold(int v) { this.dmlInClauseThreshold = v; }
    public int getDmlInColumnsPerRow() { return dmlInColumnsPerRow; }
    public void setDmlInColumnsPerRow(int v) { this.dmlInColumnsPerRow = v; }
    /** @deprecated use {@link #getDmlInColumnsPerRow()} */
    @Deprecated public int getInListColumnsPerRow() { return dmlInColumnsPerRow; }
    /** @deprecated use {@link #setDmlInColumnsPerRow(int)} */
    @Deprecated public void setInListColumnsPerRow(int v) { this.dmlInColumnsPerRow = v; }
    public SubqueryStyle getDmlSubqueryFormat() { return dmlSubqueryFormat; }
    public void setDmlSubqueryFormat(SubqueryStyle v) { this.dmlSubqueryFormat = v; }
    /** @deprecated use {@link #getDmlSubqueryFormat()} */
    @Deprecated public SubqueryStyle getSubqueryStyle() { return dmlSubqueryFormat; }
    /** @deprecated use {@link #setDmlSubqueryFormat(SubqueryStyle)} */
    @Deprecated public void setSubqueryStyle(SubqueryStyle v) { this.dmlSubqueryFormat = v; }
    public int getSubqueryThreshold() { return subqueryThreshold; }
    public void setSubqueryThreshold(int v) { this.subqueryThreshold = v; }
    public SelectColumnMode getSubquerySelectMode() { return subquerySelectMode; }
    public void setSubquerySelectMode(SelectColumnMode v) { this.subquerySelectMode = v; }
    public boolean isSubqueryFromNewline() { return subqueryFromNewline; }
    public void setSubqueryFromNewline(boolean v) { this.subqueryFromNewline = v; }
    public CteFormat getCteFormat() { return cteFormat; }
    public void setCteFormat(CteFormat v) { this.cteFormat = v; }
    public CommaPosition getCteCommaPosition() { return cteCommaPosition; }
    public void setCteCommaPosition(CommaPosition v) { this.cteCommaPosition = v; }
    public boolean isSetOperatorNewline() { return setOperatorNewline; }
    public void setSetOperatorNewline(boolean v) { this.setOperatorNewline = v; }
    public boolean isSetOperatorColumnAlign() { return setOperatorColumnAlign; }
    public void setSetOperatorColumnAlign(boolean v) { this.setOperatorColumnAlign = v; }
    /** @deprecated use {@link #isSetOperatorColumnAlign()} */
    @Deprecated public boolean isSetOperatorAlign() { return setOperatorColumnAlign; }
    /** @deprecated use {@link #setSetOperatorColumnAlign(boolean)} */
    @Deprecated public void setSetOperatorAlign(boolean v) { this.setOperatorColumnAlign = v; }
    public boolean isCursorSelectFormat() { return cursorSelectFormat; }
    public void setCursorSelectFormat(boolean v) { this.cursorSelectFormat = v; }

    // ── DML ──
    public InsertColumnFormat getInsertColumnFormat() { return insertColumnFormat; }
    public void setInsertColumnFormat(InsertColumnFormat v) { this.insertColumnFormat = v; }
    public int getInsertColumnsPerRow() { return insertColumnsPerRow; }
    public void setInsertColumnsPerRow(int v) { this.insertColumnsPerRow = v; }
    public int getInsertValuesPerRow() { return insertValuesPerRow; }
    public void setInsertValuesPerRow(int v) { this.insertValuesPerRow = v; }
    public boolean isDmlInsertColumnNewline() { return dmlInsertColumnNewline; }
    public void setDmlInsertColumnNewline(boolean v) { this.dmlInsertColumnNewline = v; }
    public boolean isDmlValuesNewline() { return dmlValuesNewline; }
    public void setDmlValuesNewline(boolean v) { this.dmlValuesNewline = v; }
    public boolean isDmlValuesExpand() { return dmlValuesExpand; }
    public void setDmlValuesExpand(boolean v) { this.dmlValuesExpand = v; }
    public boolean isDmlInsertAllIndent() { return dmlInsertAllIndent; }
    public void setDmlInsertAllIndent(boolean v) { this.dmlInsertAllIndent = v; }
    public DmlReturningIntoStyle getDmlReturningIntoStyle() { return dmlReturningIntoStyle; }
    public void setDmlReturningIntoStyle(DmlReturningIntoStyle v) { this.dmlReturningIntoStyle = v; }
    public boolean isDmlIntoAlign() { return dmlIntoAlign; }
    public void setDmlIntoAlign(boolean v) { this.dmlIntoAlign = v; }
    public boolean isDmlUpdateSetPerLine() { return dmlUpdateSetPerLine; }
    public void setDmlUpdateSetPerLine(boolean v) { this.dmlUpdateSetPerLine = v; }
    public boolean isDmlUpdateSetAlign() { return dmlUpdateSetAlign; }
    public void setDmlUpdateSetAlign(boolean v) { this.dmlUpdateSetAlign = v; }
    /** @deprecated use {@link #isDmlUpdateSetAlign()} */
    @Deprecated public boolean isUpdateSetAlign() { return dmlUpdateSetAlign; }
    /** @deprecated use {@link #setDmlUpdateSetAlign(boolean)} */
    @Deprecated public void setUpdateSetAlign(boolean v) { this.dmlUpdateSetAlign = v; }
    public int getDmlUpdateColumnsPerRow() { return dmlUpdateColumnsPerRow; }
    public void setDmlUpdateColumnsPerRow(int v) { this.dmlUpdateColumnsPerRow = v; }
    /** @deprecated use {@link #getDmlUpdateColumnsPerRow()} */
    @Deprecated public int getUpdateSetColumnsPerRow() { return dmlUpdateColumnsPerRow; }
    /** @deprecated use {@link #setDmlUpdateColumnsPerRow(int)} */
    @Deprecated public void setUpdateSetColumnsPerRow(int v) { this.dmlUpdateColumnsPerRow = v; }
    public CommaPosition getUpdateSetCommaPosition() { return updateSetCommaPosition; }
    public void setUpdateSetCommaPosition(CommaPosition v) { this.updateSetCommaPosition = v; }
    public DmlDeleteFromHandling getDmlDeleteFromHandling() { return dmlDeleteFromHandling; }
    public void setDmlDeleteFromHandling(DmlDeleteFromHandling v) { this.dmlDeleteFromHandling = v; }
    /** @deprecated use {@link #getDmlDeleteFromHandling()} */
    @Deprecated public boolean isDeleteFromNewline() { return dmlDeleteFromHandling == DmlDeleteFromHandling.KEEP; }
    /** @deprecated use {@link #setDmlDeleteFromHandling(DmlDeleteFromHandling)} */
    @Deprecated public void setDeleteFromNewline(boolean v) { this.dmlDeleteFromHandling = v ? DmlDeleteFromHandling.KEEP : DmlDeleteFromHandling.REMOVE; }
    public boolean isMergeIntoNewline() { return mergeIntoNewline; }
    public void setMergeIntoNewline(boolean v) { this.mergeIntoNewline = v; }
    public boolean isMergeUsingNewline() { return mergeUsingNewline; }
    public void setMergeUsingNewline(boolean v) { this.mergeUsingNewline = v; }
    public boolean isMergeOnNewline() { return mergeOnNewline; }
    public void setMergeOnNewline(boolean v) { this.mergeOnNewline = v; }
    public boolean isMergeWhenNewline() { return mergeWhenNewline; }
    public void setMergeWhenNewline(boolean v) { this.mergeWhenNewline = v; }
    public boolean isMergeUpdateSetAlign() { return mergeUpdateSetAlign; }
    public void setMergeUpdateSetAlign(boolean v) { this.mergeUpdateSetAlign = v; }
    public boolean isDmlBulkCollectAlign() { return dmlBulkCollectAlign; }
    public void setDmlBulkCollectAlign(boolean v) { this.dmlBulkCollectAlign = v; }
    public boolean isDmlUsingAlign() { return dmlUsingAlign; }
    public void setDmlUsingAlign(boolean v) { this.dmlUsingAlign = v; }
    public SubqueryStyle getInsertSubqueryStyle() { return insertSubqueryStyle; }
    public void setInsertSubqueryStyle(SubqueryStyle v) { this.insertSubqueryStyle = v; }

    // ── DDL ──
    public boolean isDdlColumnAlign() { return ddlColumnAlign; }
    public void setDdlColumnAlign(boolean v) { this.ddlColumnAlign = v; }
    /** @deprecated use {@link #isDdlColumnAlign()} */
    @Deprecated public boolean isColumnDefAlign() { return ddlColumnAlign; }
    /** @deprecated use {@link #setDdlColumnAlign(boolean)} */
    @Deprecated public void setColumnDefAlign(boolean v) { this.ddlColumnAlign = v; }
    public int getColumnDefColumnsPerRow() { return columnDefColumnsPerRow; }
    public void setColumnDefColumnsPerRow(int v) { this.columnDefColumnsPerRow = v; }
    public KeywordCase getColumnDefTypeCase() { return columnDefTypeCase; }
    public void setColumnDefTypeCase(KeywordCase v) { this.columnDefTypeCase = v; }
    public ConstraintFormat getConstraintFormat() { return constraintFormat; }
    public void setConstraintFormat(ConstraintFormat v) { this.constraintFormat = v; }
    public int getConstraintColumnsPerRow() { return constraintColumnsPerRow; }
    public void setConstraintColumnsPerRow(int v) { this.constraintColumnsPerRow = v; }
    public StorageClauseFormat getStorageClauseFormat() { return storageClauseFormat; }
    public void setStorageClauseFormat(StorageClauseFormat v) { this.storageClauseFormat = v; }
    public IndexColumnFormat getIndexColumnFormat() { return indexColumnFormat; }
    public void setIndexColumnFormat(IndexColumnFormat v) { this.indexColumnFormat = v; }
    public int getIndexColumnsPerRow() { return indexColumnsPerRow; }
    public void setIndexColumnsPerRow(int v) { this.indexColumnsPerRow = v; }
    public PartitionFormat getPartitionFormat() { return partitionFormat; }
    public void setPartitionFormat(PartitionFormat v) { this.partitionFormat = v; }
    public int getPartitionColumnsPerRow() { return partitionColumnsPerRow; }
    public void setPartitionColumnsPerRow(int v) { this.partitionColumnsPerRow = v; }
    public boolean isDdlIndexOptionPerLine() { return ddlIndexOptionPerLine; }
    public void setDdlIndexOptionPerLine(boolean v) { this.ddlIndexOptionPerLine = v; }
    public boolean isDdlConstraintStatePerLine() { return ddlConstraintStatePerLine; }
    public void setDdlConstraintStatePerLine(boolean v) { this.ddlConstraintStatePerLine = v; }
    public boolean isDdlAlterColumnPerLine() { return ddlAlterColumnPerLine; }
    public void setDdlAlterColumnPerLine(boolean v) { this.ddlAlterColumnPerLine = v; }
    public boolean isDdlAlterAddDropPerLine() { return ddlAlterAddDropPerLine; }
    public void setDdlAlterAddDropPerLine(boolean v) { this.ddlAlterAddDropPerLine = v; }
    public boolean isDdlTbspOptionPerLine() { return ddlTbspOptionPerLine; }
    public void setDdlTbspOptionPerLine(boolean v) { this.ddlTbspOptionPerLine = v; }
    public boolean isDdlSeqOptionPerLine() { return ddlSeqOptionPerLine; }
    public void setDdlSeqOptionPerLine(boolean v) { this.ddlSeqOptionPerLine = v; }
    public boolean isDdlUserOptionPerLine() { return ddlUserOptionPerLine; }
    public void setDdlUserOptionPerLine(boolean v) { this.ddlUserOptionPerLine = v; }
    public boolean isDdlGrantPrivPerLine() { return ddlGrantPrivPerLine; }
    public void setDdlGrantPrivPerLine(boolean v) { this.ddlGrantPrivPerLine = v; }
    public boolean isDdlFlashbackOptionPerLine() { return ddlFlashbackOptionPerLine; }
    public void setDdlFlashbackOptionPerLine(boolean v) { this.ddlFlashbackOptionPerLine = v; }
    public boolean isDdlFlashbackCompact() { return ddlFlashbackCompact; }
    public void setDdlFlashbackCompact(boolean v) { this.ddlFlashbackCompact = v; }
    public DdlAnalyzeFormat getDdlAnalyzeFormat() { return ddlAnalyzeFormat; }
    public void setDdlAnalyzeFormat(DdlAnalyzeFormat v) { this.ddlAnalyzeFormat = v; }
    public boolean isDdlAnalyzePartitionPerLine() { return ddlAnalyzePartitionPerLine; }
    public void setDdlAnalyzePartitionPerLine(boolean v) { this.ddlAnalyzePartitionPerLine = v; }

    // ── PL/SQL ──
    public boolean isDeclarationAlign() { return declarationAlign; }
    public void setDeclarationAlign(boolean v) { this.declarationAlign = v; }
    public ParameterListMode getParameterListMode() { return parameterListMode; }
    public void setParameterListMode(ParameterListMode v) { this.parameterListMode = v; }
    public int getParameterColumnsPerRow() { return parameterColumnsPerRow; }
    public void setParameterColumnsPerRow(int v) { this.parameterColumnsPerRow = v; }
    public boolean isParameterPerLine() { return parameterPerLine; }
    public void setParameterPerLine(boolean v) { this.parameterPerLine = v; }
    public ParameterAlignMode getParameterAlignMode() { return parameterAlignMode; }
    public void setParameterAlignMode(ParameterAlignMode v) { this.parameterAlignMode = v; }
    public boolean isParameterNameRightAlign() { return parameterNameRightAlign; }
    public void setParameterNameRightAlign(boolean v) { this.parameterNameRightAlign = v; }
    public KeywordCase getParameterDirectionCase() { return parameterDirectionCase; }
    public void setParameterDirectionCase(KeywordCase v) { this.parameterDirectionCase = v; }
    public KeywordCase getParameterTypeCase() { return parameterTypeCase; }
    public void setParameterTypeCase(KeywordCase v) { this.parameterTypeCase = v; }
    public boolean isThenOnNewLine() { return thenOnNewLine; }
    public void setThenOnNewLine(boolean v) { this.thenOnNewLine = v; }
    public boolean isLoopOnNewLine() { return loopOnNewLine; }
    public void setLoopOnNewLine(boolean v) { this.loopOnNewLine = v; }
    public boolean isElseOnNewLine() { return elseOnNewLine; }
    public void setElseOnNewLine(boolean v) { this.elseOnNewLine = v; }
    public ExceptionAlign getExceptionAlign() { return exceptionAlign; }
    public void setExceptionAlign(ExceptionAlign v) { this.exceptionAlign = v; }
    public boolean isEndAlign() { return endAlign; }
    public void setEndAlign(boolean v) { this.endAlign = v; }
    /** 将 BEGIN/EXCEPTION/END 缩进至与函数声明同级（默认 true，即 demo.txt 风格） */
    public boolean isBeginOutdent() { return beginOutdent; }
    public void setBeginOutdent(boolean v) { this.beginOutdent = v; }
    /** 分号前不留空格（默认 true） */
    public boolean isSemicolonCompact() { return semicolonCompact; }
    public void setSemicolonCompact(boolean v) { this.semicolonCompact = v; }
    public int getPlsqlIndentSize() { return plsqlIndentSize; }
    public void setPlsqlIndentSize(int v) { this.plsqlIndentSize = v; }
    public ParenthesisSpacing getParenthesisSpacing() { return parenthesisSpacing; }
    public void setParenthesisSpacing(ParenthesisSpacing v) { this.parenthesisSpacing = v; }
    public ForLoopFormat getForLoopFormat() { return forLoopFormat; }
    public void setForLoopFormat(ForLoopFormat v) { this.forLoopFormat = v; }
    public CaseExpressionFormat getCaseExpressionFormat() { return caseExpressionFormat; }
    public void setCaseExpressionFormat(CaseExpressionFormat v) { this.caseExpressionFormat = v; }
    public boolean isIntoVariableAlign() { return intoVariableAlign; }
    public void setIntoVariableAlign(boolean v) { this.intoVariableAlign = v; }
    public boolean isNamedParameterAlign() { return namedParameterAlign; }
    public void setNamedParameterAlign(boolean v) { this.namedParameterAlign = v; }
    public TriggerFormat getTriggerFormat() { return triggerFormat; }
    public void setTriggerFormat(TriggerFormat v) { this.triggerFormat = v; }
    public boolean isTypeMemberAlign() { return typeMemberAlign; }
    public void setTypeMemberAlign(boolean v) { this.typeMemberAlign = v; }
    public boolean isForallFormat() { return forallFormat; }
    public void setForallFormat(boolean v) { this.forallFormat = v; }
    public boolean isDbmsSqlFormat() { return dbmsSqlFormat; }
    public void setDbmsSqlFormat(boolean v) { this.dbmsSqlFormat = v; }
    public boolean isDbmsSqlBindPerLine() { return dbmsSqlBindPerLine; }
    public void setDbmsSqlBindPerLine(boolean v) { this.dbmsSqlBindPerLine = v; }
    public boolean isDbmsSqlColumnPerLine() { return dbmsSqlColumnPerLine; }
    public void setDbmsSqlColumnPerLine(boolean v) { this.dbmsSqlColumnPerLine = v; }

    // ── 子查询位置控制 ──
    public SubqueryStyle getSelectListSubqueryStyle() { return selectListSubqueryStyle; }
    public void setSelectListSubqueryStyle(SubqueryStyle v) { this.selectListSubqueryStyle = v; }
    public SubqueryStyle getWhereSubqueryStyle() { return whereSubqueryStyle; }
    public void setWhereSubqueryStyle(SubqueryStyle v) { this.whereSubqueryStyle = v; }
    public SubqueryStyle getFromSubqueryStyle() { return fromSubqueryStyle; }
    public void setFromSubqueryStyle(SubqueryStyle v) { this.fromSubqueryStyle = v; }
    public SubqueryStyle getHavingSubqueryStyle() { return havingSubqueryStyle; }
    public void setHavingSubqueryStyle(SubqueryStyle v) { this.havingSubqueryStyle = v; }
    public SubqueryStyle getOnClauseSubqueryStyle() { return onClauseSubqueryStyle; }
    public void setOnClauseSubqueryStyle(SubqueryStyle v) { this.onClauseSubqueryStyle = v; }
    public SubqueryStyle getExistsSubqueryStyle() { return existsSubqueryStyle; }
    public void setExistsSubqueryStyle(SubqueryStyle v) { this.existsSubqueryStyle = v; }
    public SubqueryStyle getLateralSubqueryStyle() { return lateralSubqueryStyle; }
    public void setLateralSubqueryStyle(SubqueryStyle v) { this.lateralSubqueryStyle = v; }
    public SubqueryStyle getScalarSubqueryStyle() { return scalarSubqueryStyle; }
    public void setScalarSubqueryStyle(SubqueryStyle v) { this.scalarSubqueryStyle = v; }
    public SubqueryStyle getDefaultSubqueryStyle() { return defaultSubqueryStyle; }
    public void setDefaultSubqueryStyle(SubqueryStyle v) { this.defaultSubqueryStyle = v; }
    public int getSubqueryMaxDepth() { return subqueryMaxDepth; }
    public void setSubqueryMaxDepth(int v) { this.subqueryMaxDepth = v; }

    // ── 注释与空白 ──
    public boolean isCommentPreserve() { return commentPreserve; }
    public void setCommentPreserve(boolean v) { this.commentPreserve = v; }
    public CommentIndent getCommentIndent() { return commentIndent; }
    public void setCommentIndent(CommentIndent v) { this.commentIndent = v; }
    /** @deprecated use {@link #getCommentIndent()} */
    @Deprecated public boolean isCommentIndent() { return commentIndent == CommentIndent.CODE_LEVEL; }
    public boolean isCommentSingleSpace() { return commentSingleSpace; }
    public void setCommentSingleSpace(boolean v) { this.commentSingleSpace = v; }
    public boolean isCommentWrap() { return commentWrap; }
    public void setCommentWrap(boolean v) { this.commentWrap = v; }
    public TrailingCommentAlign getTrailingCommentAlign() { return trailingCommentAlign; }
    public void setTrailingCommentAlign(TrailingCommentAlign v) { this.trailingCommentAlign = v; }
    public int getTrailingCommentMinSpaces() { return trailingCommentMinSpaces; }
    public void setTrailingCommentMinSpaces(int v) { this.trailingCommentMinSpaces = v; }
    public BlockCommentStyle getBlockCommentStyle() { return blockCommentStyle; }
    public void setBlockCommentStyle(BlockCommentStyle v) { this.blockCommentStyle = v; }
    public boolean isDocCommentPreserve() { return docCommentPreserve; }
    public void setDocCommentPreserve(boolean v) { this.docCommentPreserve = v; }
    public boolean isDocCommentParamAlign() { return docCommentParamAlign; }
    public void setDocCommentParamAlign(boolean v) { this.docCommentParamAlign = v; }
    public CommentPlacement getCommentPlacement() { return commentPlacement; }
    public void setCommentPlacement(CommentPlacement v) { this.commentPlacement = v; }
    public boolean isBlankLineBeforeComment() { return blankLineBeforeComment; }
    public void setBlankLineBeforeComment(boolean v) { this.blankLineBeforeComment = v; }
    public BlankLineHandling getBlankLineHandling() { return blankLineHandling; }
    public void setBlankLineHandling(BlankLineHandling v) { this.blankLineHandling = v; }
    public boolean isTrailingWhitespaceTrim() { return trailingWhitespaceTrim; }
    public void setTrailingWhitespaceTrim(boolean v) { this.trailingWhitespaceTrim = v; }
    public boolean isBlankLineBeforeBlock() { return blankLineBeforeBlock; }
    public void setBlankLineBeforeBlock(boolean v) { this.blankLineBeforeBlock = v; }

    // ── Backward compat ──
    @Deprecated
    public boolean isInsertColumnModeCompact() { return insertColumnFormat == InsertColumnFormat.COMPACT; }
    @Deprecated
    public void setInsertColumnModeCompact(boolean v) { this.insertColumnFormat = v ? InsertColumnFormat.COMPACT : InsertColumnFormat.ONE_PER_LINE; }

    // ── helpers ──
    private FormatOptions(boolean init) {}
    public String exportProfile(String name, boolean compact) {
        FormatOptions p = profiles.get(name);
        if (p == null) return "{}";
        return toMapString(p, compact);
    }
    public void importProfile(String name, String json) {
        FormatOptions opts = fromMapString(json);
        if (opts != null) profiles.put(name, opts);
    }

    private String toMapString(FormatOptions opts, boolean compact) {
        var m = opts.toMap(); var sb = new StringBuilder("{"); boolean first = true;
        for (var e : m.entrySet()) {
            if (!first) sb.append(",");
            if (!compact) sb.append("\n  ");
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
            first = false;
        }
        if (!compact) sb.append("\n"); sb.append("}");
        return sb.toString();
    }

    private static FormatOptions fromMapString(String json) {
        if (json == null || json.isBlank()) return null;
        Map<String, String> m = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) json = json.substring(1, json.length() - 1).trim();
        int i = 0;
        while (i < json.length()) {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;
            if (json.charAt(i) == '"') {
                int end = json.indexOf('"', i + 1);
                if (end < 0) break;
                String key = json.substring(i + 1, end); i = end + 1;
                while (i < json.length() && (json.charAt(i) == ':' || Character.isWhitespace(json.charAt(i)))) i++;
                if (i >= json.length()) break;
                if (json.charAt(i) == '"') {
                    end = json.indexOf('"', i + 1);
                    if (end < 0) break;
                    m.put(key, json.substring(i + 1, end)); i = end + 1;
                }
                while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) i++;
            } else i++;
        }
        return m.isEmpty() ? null : fromMap(m);
    }
}
