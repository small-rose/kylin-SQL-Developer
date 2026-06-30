package com.kylin.plsql.core.format;

import com.kylin.plsql.core.format.enums.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** 67-parameter formatting options model with profile management and serialization. */
public class FormatOptions {

    public enum KeywordCase { UPPER, LOWER, PRESERVE }

    // ── 通用 (5) ──
    private String dialect = "Oracle";
    private KeywordCase keywordCase = KeywordCase.UPPER;
    private int indentSize = 4;
    private int maxLineWidth = 120;
    private String lineEnding = "LF";

    // ── DQL (14 new + 5 existing = 19) ──
    private SelectColumnMode selectColumnMode = SelectColumnMode.ALIGN;
    private int selectColumnsPerRow;
    private boolean fromClauseNewline = true;
    private int fromClauseIndent;
    private boolean joinOnNewline = true;
    private boolean joinOnAlign = true;
    private WhereAndPosition whereAndPosition = WhereAndPosition.LINE_START;
    private int whereIndentSize;
    private CommaPosition commaPosition = CommaPosition.TRAILING;
    private SubqueryStyle subqueryStyle = SubqueryStyle.AUTO;
    private int subqueryThreshold = 80;
    private SelectColumnMode subquerySelectMode = SelectColumnMode.ALIGN;
    private boolean subqueryFromNewline = true;
    private CteFormat cteFormat = CteFormat.ONE_PER_LINE;
    private boolean setOperatorNewline = true;
    private boolean setOperatorAlign = true;
    private InListFormat inListFormat = InListFormat.COMPACT;
    private int inListColumnsPerRow = 5;
    private int inListThreshold = 10;

    // ── DML (11) ──
    private InsertColumnFormat insertColumnFormat = InsertColumnFormat.COMPACT;
    private int insertColumnsPerRow;
    private int insertValuesPerRow;
    private SubqueryStyle insertSubqueryStyle = SubqueryStyle.AUTO;
    private boolean updateSetAlign = true;
    private int updateSetColumnsPerRow;
    private CommaPosition updateSetCommaPosition = CommaPosition.TRAILING;
    private boolean deleteFromNewline = true;
    private boolean mergeIntoNewline = true;
    private boolean mergeWhenNewline = true;

    // ── DDL (10) ──
    private boolean columnDefAlign = true;
    private int columnDefColumnsPerRow;
    private KeywordCase columnDefTypeCase = KeywordCase.PRESERVE;
    private ConstraintFormat constraintFormat = ConstraintFormat.SEPARATE_LINE;
    private int constraintColumnsPerRow = 5;
    private StorageClauseFormat storageClauseFormat = StorageClauseFormat.COMPACT;
    private IndexColumnFormat indexColumnFormat = IndexColumnFormat.COMPACT;
    private int indexColumnsPerRow = 5;
    private PartitionFormat partitionFormat = PartitionFormat.COMPACT;
    private int partitionColumnsPerRow = 3;

    // ── PL/SQL (14) ──
    private boolean declarationAlign = true;
    private ParameterListMode parameterListMode = ParameterListMode.COMPACT;
    private int parameterColumnsPerRow = 3;
    private KeywordCase parameterDirectionCase = KeywordCase.UPPER;
    private KeywordCase parameterTypeCase = KeywordCase.PRESERVE;
    private boolean thenOnNewLine;
    private boolean loopOnNewLine;
    private boolean elseOnNewLine = true;
    private ExceptionAlign exceptionAlign = ExceptionAlign.INDENT;
    private boolean endAlign = true;
    private int plsqlIndentSize;
    private ParenthesisSpacing parenthesisSpacing = ParenthesisSpacing.NONE;
    private ForLoopFormat forLoopFormat = ForLoopFormat.COMPACT;
    private CaseExpressionFormat caseExpressionFormat = CaseExpressionFormat.EXPAND;
    private boolean intoVariableAlign = true;

    // ── 子查询位置控制 (3) ──
    private SubqueryStyle selectListSubqueryStyle = SubqueryStyle.AUTO;
    private SubqueryStyle whereSubqueryStyle = SubqueryStyle.AUTO;
    private SubqueryStyle fromSubqueryStyle = SubqueryStyle.EXPAND;

    // ── 注释与空白 (5) ──
    private boolean commentPreserve = true;
    private boolean commentIndent = true;
    private BlankLineHandling blankLineHandling = BlankLineHandling.COLLAPSE;
    private boolean trailingWhitespaceTrim = true;
    private boolean blankLineBeforeBlock;

    // ── Profile 管理 ──
    private String activeProfile = "\u9ED8\u8BA4 (Oracle)";
    private final Map<String, FormatOptions> profiles = new LinkedHashMap<>();

    public FormatOptions() { initDefaults(); }

    // ══════════════════════════════════════════════
    //  Profile
    // ══════════════════════════════════════════════

    public void initDefaults() {
        profiles.clear();

        FormatOptions def = new FormatOptions(false);
        def.dialect = "Oracle"; def.keywordCase = KeywordCase.UPPER;
        def.indentSize = 4; def.maxLineWidth = 120; def.lineEnding = "LF";
        def.selectColumnMode = SelectColumnMode.ALIGN; def.selectColumnsPerRow = 0;
        def.fromClauseNewline = true; def.fromClauseIndent = 0;
        def.joinOnNewline = true; def.joinOnAlign = true;
        def.whereAndPosition = WhereAndPosition.LINE_START; def.whereIndentSize = 0;
        def.commaPosition = CommaPosition.TRAILING;
        def.subqueryStyle = SubqueryStyle.AUTO; def.subqueryThreshold = 80;
        def.subquerySelectMode = SelectColumnMode.ALIGN; def.subqueryFromNewline = true;
        def.cteFormat = CteFormat.ONE_PER_LINE;
        def.setOperatorNewline = true; def.setOperatorAlign = true;
        def.inListFormat = InListFormat.COMPACT; def.inListColumnsPerRow = 5; def.inListThreshold = 10;
        def.insertColumnFormat = InsertColumnFormat.COMPACT;
        def.insertColumnsPerRow = 0; def.insertValuesPerRow = 0;
        def.insertSubqueryStyle = SubqueryStyle.AUTO;
        def.updateSetAlign = true; def.updateSetColumnsPerRow = 0;
        def.updateSetCommaPosition = CommaPosition.TRAILING;
        def.deleteFromNewline = true; def.mergeIntoNewline = true; def.mergeWhenNewline = true;
        def.columnDefAlign = true; def.columnDefColumnsPerRow = 0;
        def.columnDefTypeCase = KeywordCase.PRESERVE;
        def.constraintFormat = ConstraintFormat.SEPARATE_LINE; def.constraintColumnsPerRow = 5;
        def.storageClauseFormat = StorageClauseFormat.COMPACT;
        def.indexColumnFormat = IndexColumnFormat.COMPACT; def.indexColumnsPerRow = 5;
        def.partitionFormat = PartitionFormat.COMPACT; def.partitionColumnsPerRow = 3;
        def.declarationAlign = true; def.parameterListMode = ParameterListMode.COMPACT;
        def.parameterColumnsPerRow = 3;
        def.parameterDirectionCase = KeywordCase.UPPER; def.parameterTypeCase = KeywordCase.PRESERVE;
        def.thenOnNewLine = false; def.loopOnNewLine = false; def.elseOnNewLine = true;
        def.exceptionAlign = ExceptionAlign.INDENT; def.endAlign = true;
        def.plsqlIndentSize = 0;
        def.parenthesisSpacing = ParenthesisSpacing.NONE;
        def.forLoopFormat = ForLoopFormat.COMPACT; def.caseExpressionFormat = CaseExpressionFormat.EXPAND;
        def.intoVariableAlign = true;
        def.selectListSubqueryStyle = SubqueryStyle.AUTO;
        def.whereSubqueryStyle = SubqueryStyle.AUTO; def.fromSubqueryStyle = SubqueryStyle.EXPAND;
        def.commentPreserve = true; def.commentIndent = true;
        def.blankLineHandling = BlankLineHandling.COLLAPSE;
        def.trailingWhitespaceTrim = true; def.blankLineBeforeBlock = false;
        profiles.put("\u9ED8\u8BA4 (Oracle)", def);

        FormatOptions compact = new FormatOptions(false);
        compact.dialect = "Oracle"; compact.keywordCase = KeywordCase.UPPER;
        compact.indentSize = 2; compact.maxLineWidth = 100;
        compact.fromClauseNewline = true; compact.joinOnNewline = true;
        compact.commaPosition = CommaPosition.TRAILING;
        compact.thenOnNewLine = true; compact.loopOnNewLine = true; compact.elseOnNewLine = true;
        compact.parameterListMode = ParameterListMode.COMPACT;
        profiles.put("\u7D27\u51D1\u578B", compact);

        FormatOptions wide = new FormatOptions(false);
        wide.dialect = "Oracle"; wide.keywordCase = KeywordCase.UPPER;
        wide.indentSize = 4; wide.maxLineWidth = 0;
        wide.fromClauseNewline = false; wide.joinOnNewline = false;
        wide.whereAndPosition = WhereAndPosition.LINE_END;
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
        this.joinOnNewline = src.joinOnNewline;
        this.joinOnAlign = src.joinOnAlign;
        this.whereAndPosition = src.whereAndPosition;
        this.whereIndentSize = src.whereIndentSize;
        this.commaPosition = src.commaPosition;
        this.subqueryStyle = src.subqueryStyle;
        this.subqueryThreshold = src.subqueryThreshold;
        this.subquerySelectMode = src.subquerySelectMode;
        this.subqueryFromNewline = src.subqueryFromNewline;
        this.cteFormat = src.cteFormat;
        this.setOperatorNewline = src.setOperatorNewline;
        this.setOperatorAlign = src.setOperatorAlign;
        this.inListFormat = src.inListFormat;
        this.inListColumnsPerRow = src.inListColumnsPerRow;
        this.inListThreshold = src.inListThreshold;
        this.insertColumnFormat = src.insertColumnFormat;
        this.insertColumnsPerRow = src.insertColumnsPerRow;
        this.insertValuesPerRow = src.insertValuesPerRow;
        this.insertSubqueryStyle = src.insertSubqueryStyle;
        this.updateSetAlign = src.updateSetAlign;
        this.updateSetColumnsPerRow = src.updateSetColumnsPerRow;
        this.updateSetCommaPosition = src.updateSetCommaPosition;
        this.deleteFromNewline = src.deleteFromNewline;
        this.mergeIntoNewline = src.mergeIntoNewline;
        this.mergeWhenNewline = src.mergeWhenNewline;
        this.columnDefAlign = src.columnDefAlign;
        this.columnDefColumnsPerRow = src.columnDefColumnsPerRow;
        this.columnDefTypeCase = src.columnDefTypeCase;
        this.constraintFormat = src.constraintFormat;
        this.constraintColumnsPerRow = src.constraintColumnsPerRow;
        this.storageClauseFormat = src.storageClauseFormat;
        this.indexColumnFormat = src.indexColumnFormat;
        this.indexColumnsPerRow = src.indexColumnsPerRow;
        this.partitionFormat = src.partitionFormat;
        this.partitionColumnsPerRow = src.partitionColumnsPerRow;
        this.declarationAlign = src.declarationAlign;
        this.parameterListMode = src.parameterListMode;
        this.parameterColumnsPerRow = src.parameterColumnsPerRow;
        this.parameterDirectionCase = src.parameterDirectionCase;
        this.parameterTypeCase = src.parameterTypeCase;
        this.thenOnNewLine = src.thenOnNewLine;
        this.loopOnNewLine = src.loopOnNewLine;
        this.elseOnNewLine = src.elseOnNewLine;
        this.exceptionAlign = src.exceptionAlign;
        this.endAlign = src.endAlign;
        this.plsqlIndentSize = src.plsqlIndentSize;
        this.parenthesisSpacing = src.parenthesisSpacing;
        this.forLoopFormat = src.forLoopFormat;
        this.caseExpressionFormat = src.caseExpressionFormat;
        this.intoVariableAlign = src.intoVariableAlign;
        this.selectListSubqueryStyle = src.selectListSubqueryStyle;
        this.whereSubqueryStyle = src.whereSubqueryStyle;
        this.fromSubqueryStyle = src.fromSubqueryStyle;
        this.commentPreserve = src.commentPreserve;
        this.commentIndent = src.commentIndent;
        this.blankLineHandling = src.blankLineHandling;
        this.trailingWhitespaceTrim = src.trailingWhitespaceTrim;
        this.blankLineBeforeBlock = src.blankLineBeforeBlock;
        this.activeProfile = src.activeProfile;
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
        m.put("dialect", dialect); m.put("keywordCase", keywordCase.name());
        m.put("indentSize", String.valueOf(indentSize));
        m.put("maxLineWidth", String.valueOf(maxLineWidth)); m.put("lineEnding", lineEnding);
        m.put("selectColumnMode", selectColumnMode.name());
        m.put("selectColumnsPerRow", String.valueOf(selectColumnsPerRow));
        m.put("fromClauseNewline", String.valueOf(fromClauseNewline));
        m.put("fromClauseIndent", String.valueOf(fromClauseIndent));
        m.put("joinOnNewline", String.valueOf(joinOnNewline));
        m.put("joinOnAlign", String.valueOf(joinOnAlign));
        m.put("whereAndPosition", whereAndPosition.name());
        m.put("whereIndentSize", String.valueOf(whereIndentSize));
        m.put("commaPosition", commaPosition.name());
        m.put("subqueryStyle", subqueryStyle.name());
        m.put("subqueryThreshold", String.valueOf(subqueryThreshold));
        m.put("subquerySelectMode", subquerySelectMode.name());
        m.put("subqueryFromNewline", String.valueOf(subqueryFromNewline));
        m.put("cteFormat", cteFormat.name());
        m.put("setOperatorNewline", String.valueOf(setOperatorNewline));
        m.put("setOperatorAlign", String.valueOf(setOperatorAlign));
        m.put("inListFormat", inListFormat.name());
        m.put("inListColumnsPerRow", String.valueOf(inListColumnsPerRow));
        m.put("inListThreshold", String.valueOf(inListThreshold));
        m.put("insertColumnFormat", insertColumnFormat.name());
        m.put("insertColumnsPerRow", String.valueOf(insertColumnsPerRow));
        m.put("insertValuesPerRow", String.valueOf(insertValuesPerRow));
        m.put("insertSubqueryStyle", insertSubqueryStyle.name());
        m.put("updateSetAlign", String.valueOf(updateSetAlign));
        m.put("updateSetColumnsPerRow", String.valueOf(updateSetColumnsPerRow));
        m.put("updateSetCommaPosition", updateSetCommaPosition.name());
        m.put("deleteFromNewline", String.valueOf(deleteFromNewline));
        m.put("mergeIntoNewline", String.valueOf(mergeIntoNewline));
        m.put("mergeWhenNewline", String.valueOf(mergeWhenNewline));
        m.put("columnDefAlign", String.valueOf(columnDefAlign));
        m.put("columnDefColumnsPerRow", String.valueOf(columnDefColumnsPerRow));
        m.put("columnDefTypeCase", columnDefTypeCase.name());
        m.put("constraintFormat", constraintFormat.name());
        m.put("constraintColumnsPerRow", String.valueOf(constraintColumnsPerRow));
        m.put("storageClauseFormat", storageClauseFormat.name());
        m.put("indexColumnFormat", indexColumnFormat.name());
        m.put("indexColumnsPerRow", String.valueOf(indexColumnsPerRow));
        m.put("partitionFormat", partitionFormat.name());
        m.put("partitionColumnsPerRow", String.valueOf(partitionColumnsPerRow));
        m.put("declarationAlign", String.valueOf(declarationAlign));
        m.put("parameterListMode", parameterListMode.name());
        m.put("parameterColumnsPerRow", String.valueOf(parameterColumnsPerRow));
        m.put("parameterDirectionCase", parameterDirectionCase.name());
        m.put("parameterTypeCase", parameterTypeCase.name());
        m.put("thenOnNewLine", String.valueOf(thenOnNewLine));
        m.put("loopOnNewLine", String.valueOf(loopOnNewLine));
        m.put("elseOnNewLine", String.valueOf(elseOnNewLine));
        m.put("exceptionAlign", exceptionAlign.name());
        m.put("endAlign", String.valueOf(endAlign));
        m.put("plsqlIndentSize", String.valueOf(plsqlIndentSize));
        m.put("parenthesisSpacing", parenthesisSpacing.name());
        m.put("forLoopFormat", forLoopFormat.name());
        m.put("caseExpressionFormat", caseExpressionFormat.name());
        m.put("intoVariableAlign", String.valueOf(intoVariableAlign));
        m.put("selectListSubqueryStyle", selectListSubqueryStyle.name());
        m.put("whereSubqueryStyle", whereSubqueryStyle.name());
        m.put("fromSubqueryStyle", fromSubqueryStyle.name());
        m.put("commentPreserve", String.valueOf(commentPreserve));
        m.put("commentIndent", String.valueOf(commentIndent));
        m.put("blankLineHandling", blankLineHandling.name());
        m.put("trailingWhitespaceTrim", String.valueOf(trailingWhitespaceTrim));
        m.put("blankLineBeforeBlock", String.valueOf(blankLineBeforeBlock));
        m.put("activeProfile", activeProfile);
        return m;
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
        if (m.containsKey("joinOnNewline")) o.joinOnNewline = Boolean.parseBoolean(m.get("joinOnNewline"));
        if (m.containsKey("joinOnAlign")) o.joinOnAlign = Boolean.parseBoolean(m.get("joinOnAlign"));
        if (m.containsKey("whereAndPosition")) o.whereAndPosition = WhereAndPosition.valueOf(m.get("whereAndPosition"));
        if (m.containsKey("whereIndentSize")) o.whereIndentSize = Integer.parseInt(m.get("whereIndentSize"));
        if (m.containsKey("commaPosition")) o.commaPosition = CommaPosition.valueOf(m.get("commaPosition"));
        if (m.containsKey("subqueryStyle")) o.subqueryStyle = SubqueryStyle.valueOf(m.get("subqueryStyle"));
        if (m.containsKey("subqueryThreshold")) o.subqueryThreshold = Integer.parseInt(m.get("subqueryThreshold"));
        if (m.containsKey("subquerySelectMode")) o.subquerySelectMode = SelectColumnMode.valueOf(m.get("subquerySelectMode"));
        if (m.containsKey("subqueryFromNewline")) o.subqueryFromNewline = Boolean.parseBoolean(m.get("subqueryFromNewline"));
        if (m.containsKey("cteFormat")) o.cteFormat = CteFormat.valueOf(m.get("cteFormat"));
        if (m.containsKey("setOperatorNewline")) o.setOperatorNewline = Boolean.parseBoolean(m.get("setOperatorNewline"));
        if (m.containsKey("setOperatorAlign")) o.setOperatorAlign = Boolean.parseBoolean(m.get("setOperatorAlign"));
        if (m.containsKey("inListFormat")) o.inListFormat = InListFormat.valueOf(m.get("inListFormat"));
        if (m.containsKey("inListColumnsPerRow")) o.inListColumnsPerRow = Integer.parseInt(m.get("inListColumnsPerRow"));
        if (m.containsKey("inListThreshold")) o.inListThreshold = Integer.parseInt(m.get("inListThreshold"));
        if (m.containsKey("insertColumnFormat")) o.insertColumnFormat = InsertColumnFormat.valueOf(m.get("insertColumnFormat"));
        if (m.containsKey("insertColumnsPerRow")) o.insertColumnsPerRow = Integer.parseInt(m.get("insertColumnsPerRow"));
        if (m.containsKey("insertValuesPerRow")) o.insertValuesPerRow = Integer.parseInt(m.get("insertValuesPerRow"));
        if (m.containsKey("insertSubqueryStyle")) o.insertSubqueryStyle = SubqueryStyle.valueOf(m.get("insertSubqueryStyle"));
        if (m.containsKey("updateSetAlign")) o.updateSetAlign = Boolean.parseBoolean(m.get("updateSetAlign"));
        if (m.containsKey("updateSetColumnsPerRow")) o.updateSetColumnsPerRow = Integer.parseInt(m.get("updateSetColumnsPerRow"));
        if (m.containsKey("updateSetCommaPosition")) o.updateSetCommaPosition = CommaPosition.valueOf(m.get("updateSetCommaPosition"));
        if (m.containsKey("deleteFromNewline")) o.deleteFromNewline = Boolean.parseBoolean(m.get("deleteFromNewline"));
        if (m.containsKey("mergeIntoNewline")) o.mergeIntoNewline = Boolean.parseBoolean(m.get("mergeIntoNewline"));
        if (m.containsKey("mergeWhenNewline")) o.mergeWhenNewline = Boolean.parseBoolean(m.get("mergeWhenNewline"));
        if (m.containsKey("columnDefAlign")) o.columnDefAlign = Boolean.parseBoolean(m.get("columnDefAlign"));
        if (m.containsKey("columnDefColumnsPerRow")) o.columnDefColumnsPerRow = Integer.parseInt(m.get("columnDefColumnsPerRow"));
        if (m.containsKey("columnDefTypeCase")) o.columnDefTypeCase = KeywordCase.valueOf(m.get("columnDefTypeCase"));
        if (m.containsKey("constraintFormat")) o.constraintFormat = ConstraintFormat.valueOf(m.get("constraintFormat"));
        if (m.containsKey("constraintColumnsPerRow")) o.constraintColumnsPerRow = Integer.parseInt(m.get("constraintColumnsPerRow"));
        if (m.containsKey("storageClauseFormat")) o.storageClauseFormat = StorageClauseFormat.valueOf(m.get("storageClauseFormat"));
        if (m.containsKey("indexColumnFormat")) o.indexColumnFormat = IndexColumnFormat.valueOf(m.get("indexColumnFormat"));
        if (m.containsKey("indexColumnsPerRow")) o.indexColumnsPerRow = Integer.parseInt(m.get("indexColumnsPerRow"));
        if (m.containsKey("partitionFormat")) o.partitionFormat = PartitionFormat.valueOf(m.get("partitionFormat"));
        if (m.containsKey("partitionColumnsPerRow")) o.partitionColumnsPerRow = Integer.parseInt(m.get("partitionColumnsPerRow"));
        if (m.containsKey("declarationAlign")) o.declarationAlign = Boolean.parseBoolean(m.get("declarationAlign"));
        if (m.containsKey("parameterListMode")) o.parameterListMode = ParameterListMode.valueOf(m.get("parameterListMode"));
        if (m.containsKey("parameterColumnsPerRow")) o.parameterColumnsPerRow = Integer.parseInt(m.get("parameterColumnsPerRow"));
        if (m.containsKey("parameterDirectionCase")) o.parameterDirectionCase = KeywordCase.valueOf(m.get("parameterDirectionCase"));
        if (m.containsKey("parameterTypeCase")) o.parameterTypeCase = KeywordCase.valueOf(m.get("parameterTypeCase"));
        if (m.containsKey("thenOnNewLine")) o.thenOnNewLine = Boolean.parseBoolean(m.get("thenOnNewLine"));
        if (m.containsKey("loopOnNewLine")) o.loopOnNewLine = Boolean.parseBoolean(m.get("loopOnNewLine"));
        if (m.containsKey("elseOnNewLine")) o.elseOnNewLine = Boolean.parseBoolean(m.get("elseOnNewLine"));
        if (m.containsKey("exceptionAlign")) o.exceptionAlign = ExceptionAlign.valueOf(m.get("exceptionAlign"));
        if (m.containsKey("endAlign")) o.endAlign = Boolean.parseBoolean(m.get("endAlign"));
        if (m.containsKey("plsqlIndentSize")) o.plsqlIndentSize = Integer.parseInt(m.get("plsqlIndentSize"));
        if (m.containsKey("parenthesisSpacing")) o.parenthesisSpacing = ParenthesisSpacing.valueOf(m.get("parenthesisSpacing"));
        if (m.containsKey("forLoopFormat")) o.forLoopFormat = ForLoopFormat.valueOf(m.get("forLoopFormat"));
        if (m.containsKey("caseExpressionFormat")) o.caseExpressionFormat = CaseExpressionFormat.valueOf(m.get("caseExpressionFormat"));
        if (m.containsKey("intoVariableAlign")) o.intoVariableAlign = Boolean.parseBoolean(m.get("intoVariableAlign"));
        if (m.containsKey("selectListSubqueryStyle")) o.selectListSubqueryStyle = SubqueryStyle.valueOf(m.get("selectListSubqueryStyle"));
        if (m.containsKey("whereSubqueryStyle")) o.whereSubqueryStyle = SubqueryStyle.valueOf(m.get("whereSubqueryStyle"));
        if (m.containsKey("fromSubqueryStyle")) o.fromSubqueryStyle = SubqueryStyle.valueOf(m.get("fromSubqueryStyle"));
        if (m.containsKey("commentPreserve")) o.commentPreserve = Boolean.parseBoolean(m.get("commentPreserve"));
        if (m.containsKey("commentIndent")) o.commentIndent = Boolean.parseBoolean(m.get("commentIndent"));
        if (m.containsKey("blankLineHandling")) o.blankLineHandling = BlankLineHandling.valueOf(m.get("blankLineHandling"));
        if (m.containsKey("trailingWhitespaceTrim")) o.trailingWhitespaceTrim = Boolean.parseBoolean(m.get("trailingWhitespaceTrim"));
        if (m.containsKey("blankLineBeforeBlock")) o.blankLineBeforeBlock = Boolean.parseBoolean(m.get("blankLineBeforeBlock"));
        if (m.containsKey("activeProfile")) o.activeProfile = m.get("activeProfile");
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

    // ── 通用 getters/setters ──
    public KeywordCase getKeywordCase() { return keywordCase; }
    public void setKeywordCase(KeywordCase v) { this.keywordCase = v; }
    public int getIndentSize() { return indentSize; }
    public void setIndentSize(int v) { this.indentSize = v; }
    public int getMaxLineWidth() { return maxLineWidth; }
    public void setMaxLineWidth(int v) { this.maxLineWidth = v; }
    public String getLineEnding() { return lineEnding; }
    public void setLineEnding(String v) { this.lineEnding = v; }

    // ── DQL ──
    public SelectColumnMode getSelectColumnMode() { return selectColumnMode; }
    public void setSelectColumnMode(SelectColumnMode v) { this.selectColumnMode = v; }
    public boolean isAlignSelectColumns() { return selectColumnMode == SelectColumnMode.ALIGN; }
    public void setAlignSelectColumns(boolean v) { this.selectColumnMode = v ? SelectColumnMode.ALIGN : SelectColumnMode.COMPACT; }
    public int getSelectColumnsPerRow() { return selectColumnsPerRow; }
    public void setSelectColumnsPerRow(int v) { this.selectColumnsPerRow = v; }
    public boolean isFromClauseNewline() { return fromClauseNewline; }
    public void setFromClauseNewline(boolean v) { this.fromClauseNewline = v; }
    public int getFromClauseIndent() { return fromClauseIndent; }
    public void setFromClauseIndent(int v) { this.fromClauseIndent = v; }
    public boolean isJoinOnNewline() { return joinOnNewline; }
    public void setJoinOnNewline(boolean v) { this.joinOnNewline = v; }
    public boolean isJoinOnAlign() { return joinOnAlign; }
    public void setJoinOnAlign(boolean v) { this.joinOnAlign = v; }
    public WhereAndPosition getWhereAndPosition() { return whereAndPosition; }
    public void setWhereAndPosition(WhereAndPosition v) { this.whereAndPosition = v; }
    public int getWhereIndentSize() { return whereIndentSize; }
    public void setWhereIndentSize(int v) { this.whereIndentSize = v; }
    public CommaPosition getCommaPosition() { return commaPosition; }
    public void setCommaPosition(CommaPosition v) { this.commaPosition = v; }
    public SubqueryStyle getSubqueryStyle() { return subqueryStyle; }
    public void setSubqueryStyle(SubqueryStyle v) { this.subqueryStyle = v; }
    public int getSubqueryThreshold() { return subqueryThreshold; }
    public void setSubqueryThreshold(int v) { this.subqueryThreshold = v; }
    public SelectColumnMode getSubquerySelectMode() { return subquerySelectMode; }
    public void setSubquerySelectMode(SelectColumnMode v) { this.subquerySelectMode = v; }
    public boolean isSubqueryFromNewline() { return subqueryFromNewline; }
    public void setSubqueryFromNewline(boolean v) { this.subqueryFromNewline = v; }
    public CteFormat getCteFormat() { return cteFormat; }
    public void setCteFormat(CteFormat v) { this.cteFormat = v; }
    public boolean isSetOperatorNewline() { return setOperatorNewline; }
    public void setSetOperatorNewline(boolean v) { this.setOperatorNewline = v; }
    public boolean isSetOperatorAlign() { return setOperatorAlign; }
    public void setSetOperatorAlign(boolean v) { this.setOperatorAlign = v; }
    public InListFormat getInListFormat() { return inListFormat; }
    public void setInListFormat(InListFormat v) { this.inListFormat = v; }
    public int getInListColumnsPerRow() { return inListColumnsPerRow; }
    public void setInListColumnsPerRow(int v) { this.inListColumnsPerRow = v; }
    public int getInListThreshold() { return inListThreshold; }
    public void setInListThreshold(int v) { this.inListThreshold = v; }

    // ── DML ──
    public InsertColumnFormat getInsertColumnFormat() { return insertColumnFormat; }
    public void setInsertColumnFormat(InsertColumnFormat v) { this.insertColumnFormat = v; }
    public int getInsertColumnsPerRow() { return insertColumnsPerRow; }
    public void setInsertColumnsPerRow(int v) { this.insertColumnsPerRow = v; }
    public int getInsertValuesPerRow() { return insertValuesPerRow; }
    public void setInsertValuesPerRow(int v) { this.insertValuesPerRow = v; }
    public SubqueryStyle getInsertSubqueryStyle() { return insertSubqueryStyle; }
    public void setInsertSubqueryStyle(SubqueryStyle v) { this.insertSubqueryStyle = v; }
    public boolean isUpdateSetAlign() { return updateSetAlign; }
    public void setUpdateSetAlign(boolean v) { this.updateSetAlign = v; }
    public int getUpdateSetColumnsPerRow() { return updateSetColumnsPerRow; }
    public void setUpdateSetColumnsPerRow(int v) { this.updateSetColumnsPerRow = v; }
    public CommaPosition getUpdateSetCommaPosition() { return updateSetCommaPosition; }
    public void setUpdateSetCommaPosition(CommaPosition v) { this.updateSetCommaPosition = v; }
    public boolean isDeleteFromNewline() { return deleteFromNewline; }
    public void setDeleteFromNewline(boolean v) { this.deleteFromNewline = v; }
    public boolean isMergeIntoNewline() { return mergeIntoNewline; }
    public void setMergeIntoNewline(boolean v) { this.mergeIntoNewline = v; }
    public boolean isMergeWhenNewline() { return mergeWhenNewline; }
    public void setMergeWhenNewline(boolean v) { this.mergeWhenNewline = v; }

    // ── DDL ──
    public boolean isColumnDefAlign() { return columnDefAlign; }
    public void setColumnDefAlign(boolean v) { this.columnDefAlign = v; }
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

    // ── PL/SQL ──
    public boolean isDeclarationAlign() { return declarationAlign; }
    public void setDeclarationAlign(boolean v) { this.declarationAlign = v; }
    public ParameterListMode getParameterListMode() { return parameterListMode; }
    public void setParameterListMode(ParameterListMode v) { this.parameterListMode = v; }
    public int getParameterColumnsPerRow() { return parameterColumnsPerRow; }
    public void setParameterColumnsPerRow(int v) { this.parameterColumnsPerRow = v; }
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

    // ── 子查询位置控制 ──
    public SubqueryStyle getSelectListSubqueryStyle() { return selectListSubqueryStyle; }
    public void setSelectListSubqueryStyle(SubqueryStyle v) { this.selectListSubqueryStyle = v; }
    public SubqueryStyle getWhereSubqueryStyle() { return whereSubqueryStyle; }
    public void setWhereSubqueryStyle(SubqueryStyle v) { this.whereSubqueryStyle = v; }
    public SubqueryStyle getFromSubqueryStyle() { return fromSubqueryStyle; }
    public void setFromSubqueryStyle(SubqueryStyle v) { this.fromSubqueryStyle = v; }

    // ── 注释与空白 ──
    public boolean isCommentPreserve() { return commentPreserve; }
    public void setCommentPreserve(boolean v) { this.commentPreserve = v; }
    public boolean isCommentIndent() { return commentIndent; }
    public void setCommentIndent(boolean v) { this.commentIndent = v; }
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
