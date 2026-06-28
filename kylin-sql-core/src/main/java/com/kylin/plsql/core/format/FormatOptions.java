package com.kylin.plsql.core.format;

import java.util.LinkedHashMap;
import java.util.Map;

public class FormatOptions {

    public enum KeywordCase { UPPER, LOWER, PRESERVE }

    // ── 通用 ──
    private KeywordCase keywordCase = KeywordCase.UPPER;
    private int indentSize = 4;
    private int maxLineWidth = 120;
    private String lineEnding = "LF";

    // ── DML ──
    private SelectColumnMode selectColumnMode = SelectColumnMode.ALIGN;
    private boolean fromClauseNewline = true;
    private boolean joinOnNewline = true;
    private boolean joinOnAlign = true;
    private WhereAndPosition whereAndPosition = WhereAndPosition.LINE_START;
    private CommaPosition commaPosition = CommaPosition.TRAILING;
    private boolean insertColumnModeCompact = true;
    private boolean updateSetAlign = true;

    // ── PL/SQL ──
    private boolean thenOnNewLine = false;
    private boolean loopOnNewLine = false;
    private boolean elseOnNewLine = true;
    private ExceptionAlign exceptionAlign = ExceptionAlign.INDENT;
    private boolean declarationAlign = true;
    private ParameterListMode parameterListMode = ParameterListMode.COMPACT;
    private ParenthesisSpacing parenthesisSpacing = ParenthesisSpacing.NONE;

    // ── DDL ──
    private boolean columnDefAlign = true;
    private StorageClauseFormat storageClauseFormat = StorageClauseFormat.COMPACT;

    // ── Profile 管理 ──
    private String activeProfile = "默认 (Oracle)";
    private final Map<String, FormatOptions> profiles = new LinkedHashMap<>();

    public FormatOptions() {
        initDefaults();
    }

    // ══════════════════════════════════════════════
    //  Profile 方法
    // ══════════════════════════════════════════════

    public void initDefaults() {
        profiles.clear();

        FormatOptions def = new FormatOptions(false);
        def.keywordCase = KeywordCase.UPPER;
        def.indentSize = 4;
        def.maxLineWidth = 120;
        def.lineEnding = "LF";
        def.selectColumnMode = SelectColumnMode.ALIGN;
        def.fromClauseNewline = true;
        def.joinOnNewline = true;
        def.joinOnAlign = true;
        def.whereAndPosition = WhereAndPosition.LINE_START;
        def.commaPosition = CommaPosition.TRAILING;
        def.insertColumnModeCompact = true;
        def.updateSetAlign = true;
        def.thenOnNewLine = false;
        def.loopOnNewLine = false;
        def.elseOnNewLine = true;
        def.exceptionAlign = ExceptionAlign.INDENT;
        def.declarationAlign = true;
        def.parameterListMode = ParameterListMode.COMPACT;
        def.parenthesisSpacing = ParenthesisSpacing.NONE;
        def.columnDefAlign = true;
        def.storageClauseFormat = StorageClauseFormat.COMPACT;
        profiles.put("默认 (Oracle)", def);

        FormatOptions compact = new FormatOptions(false);
        compact.keywordCase = KeywordCase.UPPER;
        compact.indentSize = 2;
        compact.maxLineWidth = 100;
        compact.lineEnding = "LF";
        compact.fromClauseNewline = true;
        compact.joinOnNewline = true;
        compact.whereAndPosition = WhereAndPosition.LINE_START;
        compact.commaPosition = CommaPosition.TRAILING;
        compact.insertColumnModeCompact = true;
        compact.thenOnNewLine = true;
        compact.loopOnNewLine = true;
        compact.elseOnNewLine = true;
        compact.parameterListMode = ParameterListMode.COMPACT;
        profiles.put("紧凑型", compact);

        FormatOptions wide = new FormatOptions(false);
        wide.keywordCase = KeywordCase.UPPER;
        wide.indentSize = 4;
        wide.maxLineWidth = 0;
        wide.lineEnding = "LF";
        wide.fromClauseNewline = false;
        wide.joinOnNewline = false;
        wide.whereAndPosition = WhereAndPosition.LINE_END;
        wide.commaPosition = CommaPosition.TRAILING;
        wide.insertColumnModeCompact = true;
        wide.thenOnNewLine = false;
        wide.loopOnNewLine = false;
        wide.elseOnNewLine = true;
        wide.parameterListMode = ParameterListMode.ONE_PER_LINE;
        profiles.put("宽排版", wide);

        if (!profiles.containsKey(activeProfile)) {
            activeProfile = "默认 (Oracle)";
        }
    }

    public void switchTo(String name) {
        FormatOptions target = profiles.get(name);
        if (target == null) return;
        copyFrom(target);
        activeProfile = name;
    }

    public void saveAs(String name) {
        profiles.put(name, snapshot());
        activeProfile = name;
    }

    public void deleteProfile(String name) {
        if ("默认 (Oracle)".equals(name)) return;
        profiles.remove(name);
        if (activeProfile.equals(name)) {
            activeProfile = "默认 (Oracle)";
            switchTo(activeProfile);
        }
    }

    public String exportProfile(String name, boolean compact) {
        FormatOptions p = profiles.get(name);
        if (p == null) return "{}";
        return toMapString(p, compact);
    }

    public void importProfile(String name, String json) {
        FormatOptions opts = fromMapString(json);
        if (opts != null) {
            profiles.put(name, opts);
        }
    }

    public FormatOptions snapshot() {
        FormatOptions c = new FormatOptions(false);
        c.copyFrom(this);
        return c;
    }

    public void copyFrom(FormatOptions src) {
        this.keywordCase = src.keywordCase;
        this.indentSize = src.indentSize;
        this.maxLineWidth = src.maxLineWidth;
        this.lineEnding = src.lineEnding;
        this.selectColumnMode = src.selectColumnMode;
        this.fromClauseNewline = src.fromClauseNewline;
        this.joinOnNewline = src.joinOnNewline;
        this.joinOnAlign = src.joinOnAlign;
        this.whereAndPosition = src.whereAndPosition;
        this.commaPosition = src.commaPosition;
        this.insertColumnModeCompact = src.insertColumnModeCompact;
        this.updateSetAlign = src.updateSetAlign;
        this.thenOnNewLine = src.thenOnNewLine;
        this.loopOnNewLine = src.loopOnNewLine;
        this.elseOnNewLine = src.elseOnNewLine;
        this.exceptionAlign = src.exceptionAlign;
        this.declarationAlign = src.declarationAlign;
        this.parameterListMode = src.parameterListMode;
        this.parenthesisSpacing = src.parenthesisSpacing;
        this.columnDefAlign = src.columnDefAlign;
        this.storageClauseFormat = src.storageClauseFormat;
    }

    // ══════════════════════════════════════════════
    //  序列化
    // ══════════════════════════════════════════════

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("keywordCase", keywordCase.name());
        m.put("indentSize", String.valueOf(indentSize));
        m.put("maxLineWidth", String.valueOf(maxLineWidth));
        m.put("lineEnding", lineEnding);
        m.put("selectColumnMode", selectColumnMode.name());
        m.put("fromClauseNewline", String.valueOf(fromClauseNewline));
        m.put("joinOnNewline", String.valueOf(joinOnNewline));
        m.put("joinOnAlign", String.valueOf(joinOnAlign));
        m.put("whereAndPosition", whereAndPosition.name());
        m.put("commaPosition", commaPosition.name());
        m.put("insertColumnModeCompact", String.valueOf(insertColumnModeCompact));
        m.put("updateSetAlign", String.valueOf(updateSetAlign));
        m.put("thenOnNewLine", String.valueOf(thenOnNewLine));
        m.put("loopOnNewLine", String.valueOf(loopOnNewLine));
        m.put("elseOnNewLine", String.valueOf(elseOnNewLine));
        m.put("exceptionAlign", exceptionAlign.name());
        m.put("declarationAlign", String.valueOf(declarationAlign));
        m.put("parameterListMode", parameterListMode.name());
        m.put("parenthesisSpacing", parenthesisSpacing.name());
        m.put("columnDefAlign", String.valueOf(columnDefAlign));
        m.put("storageClauseFormat", storageClauseFormat.name());
        m.put("activeProfile", activeProfile);
        return m;
    }

    public static FormatOptions fromMap(Map<String, String> m) {
        FormatOptions o = new FormatOptions(false);
        if (m.containsKey("keywordCase")) o.keywordCase = KeywordCase.valueOf(m.get("keywordCase"));
        if (m.containsKey("indentSize")) o.indentSize = Integer.parseInt(m.get("indentSize"));
        if (m.containsKey("maxLineWidth")) o.maxLineWidth = Integer.parseInt(m.get("maxLineWidth"));
        if (m.containsKey("lineEnding")) o.lineEnding = m.get("lineEnding");
        if (m.containsKey("selectColumnMode")) o.selectColumnMode = SelectColumnMode.valueOf(m.get("selectColumnMode"));
        if (m.containsKey("fromClauseNewline")) o.fromClauseNewline = Boolean.parseBoolean(m.get("fromClauseNewline"));
        if (m.containsKey("joinOnNewline")) o.joinOnNewline = Boolean.parseBoolean(m.get("joinOnNewline"));
        if (m.containsKey("joinOnAlign")) o.joinOnAlign = Boolean.parseBoolean(m.get("joinOnAlign"));
        if (m.containsKey("whereAndPosition")) o.whereAndPosition = WhereAndPosition.valueOf(m.get("whereAndPosition"));
        if (m.containsKey("commaPosition")) o.commaPosition = CommaPosition.valueOf(m.get("commaPosition"));
        if (m.containsKey("insertColumnModeCompact")) o.insertColumnModeCompact = Boolean.parseBoolean(m.get("insertColumnModeCompact"));
        if (m.containsKey("updateSetAlign")) o.updateSetAlign = Boolean.parseBoolean(m.get("updateSetAlign"));
        if (m.containsKey("thenOnNewLine")) o.thenOnNewLine = Boolean.parseBoolean(m.get("thenOnNewLine"));
        if (m.containsKey("loopOnNewLine")) o.loopOnNewLine = Boolean.parseBoolean(m.get("loopOnNewLine"));
        if (m.containsKey("elseOnNewLine")) o.elseOnNewLine = Boolean.parseBoolean(m.get("elseOnNewLine"));
        if (m.containsKey("exceptionAlign")) o.exceptionAlign = ExceptionAlign.valueOf(m.get("exceptionAlign"));
        if (m.containsKey("declarationAlign")) o.declarationAlign = Boolean.parseBoolean(m.get("declarationAlign"));
        if (m.containsKey("parameterListMode")) o.parameterListMode = ParameterListMode.valueOf(m.get("parameterListMode"));
        if (m.containsKey("parenthesisSpacing")) o.parenthesisSpacing = ParenthesisSpacing.valueOf(m.get("parenthesisSpacing"));
        if (m.containsKey("columnDefAlign")) o.columnDefAlign = Boolean.parseBoolean(m.get("columnDefAlign"));
        if (m.containsKey("storageClauseFormat")) o.storageClauseFormat = StorageClauseFormat.valueOf(m.get("storageClauseFormat"));
        if (m.containsKey("activeProfile")) o.activeProfile = m.get("activeProfile");
        return o;
    }

    public Map<String, Map<String, String>> profilesToMap() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (var e : profiles.entrySet()) {
            result.put(e.getKey(), e.getValue().toMap());
        }
        return result;
    }

    public void profilesFromMap(Map<String, Map<String, String>> data) {
        profiles.clear();
        if (data == null) { initDefaults(); return; }
        for (var e : data.entrySet()) {
            profiles.put(e.getKey(), fromMap(e.getValue()));
        }
        if (!profiles.containsKey(activeProfile)) {
            activeProfile = "默认 (Oracle)";
        }
    }

    public String getActiveProfile() { return activeProfile; }
    public void setActiveProfile(String name) { this.activeProfile = name; }

    public Map<String, FormatOptions> getProfiles() { return profiles; }

    // ══════════════════════════════════════════════
    //  getters / setters
    // ══════════════════════════════════════════════

    public KeywordCase getKeywordCase() { return keywordCase; }
    public void setKeywordCase(KeywordCase v) { this.keywordCase = v; }

    public int getIndentSize() { return indentSize; }
    public void setIndentSize(int v) { this.indentSize = v; }

    public int getMaxLineWidth() { return maxLineWidth; }
    public void setMaxLineWidth(int v) { this.maxLineWidth = v; }

    public String getLineEnding() { return lineEnding; }
    public void setLineEnding(String v) { this.lineEnding = v; }

    public SelectColumnMode getSelectColumnMode() { return selectColumnMode; }
    public void setSelectColumnMode(SelectColumnMode v) { this.selectColumnMode = v; }

    public boolean isFromClauseNewline() { return fromClauseNewline; }
    public void setFromClauseNewline(boolean v) { this.fromClauseNewline = v; }

    public boolean isJoinOnNewline() { return joinOnNewline; }
    public void setJoinOnNewline(boolean v) { this.joinOnNewline = v; }

    public boolean isJoinOnAlign() { return joinOnAlign; }
    public void setJoinOnAlign(boolean v) { this.joinOnAlign = v; }

    public WhereAndPosition getWhereAndPosition() { return whereAndPosition; }
    public void setWhereAndPosition(WhereAndPosition v) { this.whereAndPosition = v; }

    public CommaPosition getCommaPosition() { return commaPosition; }
    public void setCommaPosition(CommaPosition v) { this.commaPosition = v; }

    public boolean isInsertColumnModeCompact() { return insertColumnModeCompact; }
    public void setInsertColumnModeCompact(boolean v) { this.insertColumnModeCompact = v; }

    public boolean isUpdateSetAlign() { return updateSetAlign; }
    public void setUpdateSetAlign(boolean v) { this.updateSetAlign = v; }

    public boolean isThenOnNewLine() { return thenOnNewLine; }
    public void setThenOnNewLine(boolean v) { this.thenOnNewLine = v; }

    public boolean isLoopOnNewLine() { return loopOnNewLine; }
    public void setLoopOnNewLine(boolean v) { this.loopOnNewLine = v; }

    public boolean isElseOnNewLine() { return elseOnNewLine; }
    public void setElseOnNewLine(boolean v) { this.elseOnNewLine = v; }

    public ExceptionAlign getExceptionAlign() { return exceptionAlign; }
    public void setExceptionAlign(ExceptionAlign v) { this.exceptionAlign = v; }

    public boolean isDeclarationAlign() { return declarationAlign; }
    public void setDeclarationAlign(boolean v) { this.declarationAlign = v; }

    public ParameterListMode getParameterListMode() { return parameterListMode; }
    public void setParameterListMode(ParameterListMode v) { this.parameterListMode = v; }

    public ParenthesisSpacing getParenthesisSpacing() { return parenthesisSpacing; }
    public void setParenthesisSpacing(ParenthesisSpacing v) { this.parenthesisSpacing = v; }

    public boolean isColumnDefAlign() { return columnDefAlign; }
    public void setColumnDefAlign(boolean v) { this.columnDefAlign = v; }

    public StorageClauseFormat getStorageClauseFormat() { return storageClauseFormat; }
    public void setStorageClauseFormat(StorageClauseFormat v) { this.storageClauseFormat = v; }

    public boolean isAlignSelectColumns() {
        return selectColumnMode == SelectColumnMode.ALIGN;
    }

    public void setAlignSelectColumns(boolean v) {
        this.selectColumnMode = v ? SelectColumnMode.ALIGN : SelectColumnMode.COMPACT;
    }

    // ── internal helpers ──

    private FormatOptions(boolean init) {
        // no-op constructor (skip initDefaults to avoid recursion)
    }

    private String toMapString(FormatOptions opts, boolean compact) {
        var m = opts.toMap();
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var e : m.entrySet()) {
            if (!first) sb.append(",");
            if (!compact) sb.append("\n  ");
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
            first = false;
        }
        if (!compact) sb.append("\n");
        sb.append("}");
        return sb.toString();
    }

    private static FormatOptions fromMapString(String json) {
        // minimal JSON parser for flat key-value map
        if (json == null || json.isBlank()) return null;
        Map<String, String> m = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1).trim();
        }
        // simple key:"value" pair extraction
        int i = 0;
        while (i < json.length()) {
            // skip whitespace/newline
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;
            if (json.charAt(i) == '"') {
                int end = json.indexOf('"', i + 1);
                if (end < 0) break;
                String key = json.substring(i + 1, end);
                i = end + 1;
                // colon
                while (i < json.length() && (json.charAt(i) == ':' || Character.isWhitespace(json.charAt(i)))) i++;
                if (i >= json.length()) break;
                if (json.charAt(i) == '"') {
                    end = json.indexOf('"', i + 1);
                    if (end < 0) break;
                    String val = json.substring(i + 1, end);
                    m.put(key, val);
                    i = end + 1;
                }
                // skip comma
                while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) i++;
            } else {
                i++;
            }
        }
        return m.isEmpty() ? null : fromMap(m);
    }
}
