package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.PlSqlBlock;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class DdlConstraintGen {

    private DdlConstraintGen() {}

    public static void addDdlConstraints(FormatOptions opts, List<TokenInfo> tokens,
                                          List<ConstraintSpec> constraints,
                                          Map<String, ConstraintSpec> gapMap,
                                          List<PlSqlBlock> topLevelBlocks) {
        for (PlSqlBlock block : topLevelBlocks) {
            processBlock(opts, tokens, constraints, gapMap, block);
            for (PlSqlBlock child : block.children) {
                processBlock(opts, tokens, constraints, gapMap, child);
            }
        }
    }

    private static void processBlock(FormatOptions opts, List<TokenInfo> tokens,
                                      List<ConstraintSpec> constraints,
                                      Map<String, ConstraintSpec> gapMap,
                                      PlSqlBlock block) {
        int start = Math.max(0, block.startTokenIdx);
        int end = Math.min(tokens.size() - 1, block.endTokenIdx);
        if (start >= end) return;

        String firstKw = DdlUtil.firstKeyword(tokens, start, end);
        if (firstKw == null) return;

        switch (firstKw) {
            case "CREATE":   walkCreate(opts, tokens, constraints, gapMap, start, end); break;
            case "ALTER":    walkAlter(opts, tokens, constraints, gapMap, start, end); break;
            case "DROP":     OtherDdlConstraintGen.walkDrop(tokens, constraints, gapMap, start, end); break;
            case "TRUNCATE": OtherDdlConstraintGen.walkTruncate(tokens, constraints, gapMap, start, end); break;
            case "RENAME":   OtherDdlConstraintGen.walkRename(tokens, constraints, gapMap, start, end); break;
            case "COMMENT":  OtherDdlConstraintGen.walkComment(tokens, constraints, gapMap, start, end); break;
            case "GRANT":    PrivilegeConstraintGen.walkGrant(opts, tokens, constraints, gapMap, start, end); break;
            case "REVOKE":   PrivilegeConstraintGen.walkRevoke(opts, tokens, constraints, gapMap, start, end); break;
            case "FLASHBACK": AnalyzeFlashbackConstraintGen.walkFlashback(opts, tokens, constraints, gapMap, start, end); break;
            case "PURGE":    AnalyzeFlashbackConstraintGen.walkPurge(tokens, constraints, gapMap, start, end); break;
            case "ANALYZE":  AnalyzeFlashbackConstraintGen.walkAnalyze(opts, tokens, constraints, gapMap, start, end); break;
        }
    }

    private static void walkCreate(FormatOptions opts, List<TokenInfo> tokens,
                                    List<ConstraintSpec> constraints,
                                    Map<String, ConstraintSpec> gapMap,
                                    int start, int end) {
        String objType = DdlUtil.secondKeyword(tokens, start, end);
        if (objType == null) return;
        switch (objType) {
            case "TABLE":        CreateTableConstraintGen.walkCreateTable(opts, tokens, constraints, gapMap, start, end); break;
            case "INDEX":        CreateIndexConstraintGen.walkCreateIndex(opts, tokens, constraints, gapMap, start, end); break;
            case "VIEW":         CreateViewConstraintGen.walkCreateView(opts, tokens, constraints, gapMap, start, end); break;
            case "MATERIALIZED": CreateViewConstraintGen.walkCreateMView(opts, tokens, constraints, gapMap, start, end); break;
            case "TABLESPACE":   TablespaceUserConstraintGen.walkCreateTablespace(opts, tokens, constraints, gapMap, start, end); break;
            case "USER":         TablespaceUserConstraintGen.walkCreateUser(opts, tokens, constraints, gapMap, start, end); break;
            case "PROFILE":      TablespaceUserConstraintGen.walkCreateProfile(opts, tokens, constraints, gapMap, start, end); break;
            case "SEQUENCE":     CreateSequenceConstraintGen.walkCreateSequence(opts, tokens, constraints, gapMap, start, end); break;
            case "OR":           walkCreateOrReplace(opts, tokens, constraints, gapMap, start, end); break;
        }
    }

    private static void walkCreateOrReplace(FormatOptions opts, List<TokenInfo> tokens,
                                              List<ConstraintSpec> constraints,
                                              Map<String, ConstraintSpec> gapMap,
                                              int start, int end) {
        int replaceIdx = DdlUtil.nextVisibleKw(tokens, start + 1, end, "REPLACE");
        if (replaceIdx < 0) return;
        String objType = DdlUtil.nextKeyword(tokens, replaceIdx + 1, end);
        if (objType == null) return;
        switch (objType) {
            case "VIEW":
                CreateViewConstraintGen.walkCreateView(opts, tokens, constraints, gapMap, start, end);
                break;
            case "PROCEDURE":
            case "FUNCTION":
            case "PACKAGE":
            case "TRIGGER":
            case "TYPE":
            case "DIRECTORY":
            case "SYNONYM":
            case "DATABASE":
                DdlUtil.forceSimpleLayout(tokens, constraints, gapMap, start, end);
                break;
        }
    }

    private static void walkAlter(FormatOptions opts, List<TokenInfo> tokens,
                                   List<ConstraintSpec> constraints,
                                   Map<String, ConstraintSpec> gapMap,
                                   int start, int end) {
        String objType = DdlUtil.secondKeyword(tokens, start, end);
        if (objType == null) return;
        switch (objType) {
            case "TABLE":      AlterTableConstraintGen.walkAlterTable(opts, tokens, constraints, gapMap, start, end); break;
            case "INDEX":      AlterTableConstraintGen.walkAlterIndex(opts, tokens, constraints, gapMap, start, end); break;
            case "TABLESPACE": TablespaceUserConstraintGen.walkAlterTablespace(opts, tokens, constraints, gapMap, start, end); break;
            case "SEQUENCE":   TablespaceUserConstraintGen.walkAlterSequence(opts, tokens, constraints, gapMap, start, end); break;
            case "USER":       TablespaceUserConstraintGen.walkAlterUser(opts, tokens, constraints, gapMap, start, end); break;
            case "SESSION":
            case "SYSTEM":
            case "DATABASE":
            case "PLUGGABLE":
                DdlUtil.forceSimpleLayout(tokens, constraints, gapMap, start, end);
                break;
        }
    }
}
