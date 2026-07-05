package com.kylin.plsql.core.format.plsql.formatter.layout.ddl;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.formatter.layout.spec.ConstraintSpec;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public final class TablespaceUserConstraintGen {

    private TablespaceUserConstraintGen() {}

    static void walkCreateTablespace(FormatOptions opts, List<TokenInfo> tokens,
                                      List<ConstraintSpec> constraints,
                                      Map<String, ConstraintSpec> gapMap,
                                      int start, int end) {
        int tbspKw = DdlUtil.findKeyword(tokens, start, end, "TABLESPACE");
        if (tbspKw < 0) return;
        int nameEnd = DdlUtil.nextVisible(tokens, tbspKw + 1, end);
        if (nameEnd < 0) return;
        int datafileIdx = DdlUtil.nextVisibleKw(tokens, nameEnd + 1, end, "DATAFILE");
        if (datafileIdx > 0) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, datafileIdx,
                ConstraintSpec.NewlineMode.REQUIRED, 0);
            String gid = opts.isDdlTbspOptionPerLine() ? "ddlTbspOpt_" + DdlUtil.nextAlignGroup(gapMap) : null;
            DdlUtil.formatOptionsPerLine(tokens, constraints, gapMap, datafileIdx, end, gid,
                Set.of("DATAFILE","SIZE","AUTOEXTEND","NEXT","MAXSIZE",
                       "EXTENT","SEGMENT","LOGGING","NOLOGGING","ONLINE","OFFLINE",
                       "PERMANENT","TEMPORARY","UNDO","MINIMUM","BLOCKSIZE",
                       "ENCRYPTION","DEFAULT","COMPRESS"));
        } else {
            int firstOpt = DdlUtil.nextVisible(tokens, nameEnd + 1, end);
            if (firstOpt > nameEnd && firstOpt <= end) {
                DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, firstOpt,
                    ConstraintSpec.NewlineMode.REQUIRED, 0);
            }
        }
    }

    static void walkAlterTablespace(FormatOptions opts, List<TokenInfo> tokens,
                                     List<ConstraintSpec> constraints,
                                     Map<String, ConstraintSpec> gapMap,
                                     int start, int end) {
        int tbspKw = DdlUtil.findKeyword(tokens, start, end, "TABLESPACE");
        if (tbspKw < 0) return;
        int nameEnd = DdlUtil.nextVisible(tokens, tbspKw + 1, end);
        if (nameEnd < 0) return;
        int firstOpt = DdlUtil.nextVisible(tokens, nameEnd + 1, end);
        if (firstOpt < 0 || firstOpt > end) return;

        DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, firstOpt,
            ConstraintSpec.NewlineMode.REQUIRED, 0);
    }

    static void walkAlterSequence(FormatOptions opts, List<TokenInfo> tokens,
                                   List<ConstraintSpec> constraints,
                                   Map<String, ConstraintSpec> gapMap,
                                   int start, int end) {
        int seqKw = DdlUtil.findKeyword(tokens, start, end, "SEQUENCE");
        if (seqKw < 0) return;
        int nameEnd = DdlUtil.nextVisible(tokens, seqKw + 1, end);
        if (nameEnd < 0) return;
        int firstOpt = DdlUtil.nextVisible(tokens, nameEnd + 1, end);
        if (firstOpt < 0 || firstOpt > end) return;

        if (opts.isDdlSeqOptionPerLine()) {
            String gid = "ddlSeqOpt_" + DdlUtil.nextAlignGroup(gapMap);
            DdlUtil.formatOptionsPerLine(tokens, constraints, gapMap, nameEnd, end, gid,
                Set.of("INCREMENT","START","MAXVALUE","MINVALUE","CYCLE","NOCYCLE",
                       "CACHE","NOCACHE","ORDER","NOORDER"));
        } else {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, firstOpt,
                ConstraintSpec.NewlineMode.REQUIRED, 0);
        }
    }

    static void walkCreateUser(FormatOptions opts, List<TokenInfo> tokens,
                                List<ConstraintSpec> constraints,
                                Map<String, ConstraintSpec> gapMap,
                                int start, int end) {
        int userKw = DdlUtil.findKeyword(tokens, start, end, "USER");
        if (userKw < 0) return;
        int nameEnd = DdlUtil.nextVisible(tokens, userKw + 1, end);
        if (nameEnd < 0) return;
        int firstOpt = DdlUtil.nextVisible(tokens, nameEnd + 1, end);
        if (firstOpt < 0 || firstOpt > end) return;

        DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, firstOpt,
            ConstraintSpec.NewlineMode.REQUIRED, 0);
        String gid = opts.isDdlUserOptionPerLine() ? "ddlUserOpt_" + DdlUtil.nextAlignGroup(gapMap) : null;
        if (gid != null) {
            DdlUtil.formatOptionsPerLine(tokens, constraints, gapMap, firstOpt, end, gid,
                Set.of("IDENTIFIED","DEFAULT","TEMPORARY","QUOTA","PROFILE",
                       "PASSWORD","EXPIRE","LOCK","UNLOCK","ACCOUNT"));
        }
    }

    static void walkAlterUser(FormatOptions opts, List<TokenInfo> tokens,
                               List<ConstraintSpec> constraints,
                               Map<String, ConstraintSpec> gapMap,
                               int start, int end) {
        int userKw = DdlUtil.findKeyword(tokens, start, end, "USER");
        if (userKw < 0) return;
        int nameEnd = DdlUtil.nextVisible(tokens, userKw + 1, end);
        if (nameEnd < 0) return;

        String gid = opts.isDdlUserOptionPerLine() ? "ddlUserOpt_" + DdlUtil.nextAlignGroup(gapMap) : null;
        int firstOpt = DdlUtil.nextVisible(tokens, nameEnd + 1, end);
        if (firstOpt < 0 || firstOpt > end) return;

        if (gid != null) {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, firstOpt,
                ConstraintSpec.NewlineMode.REQUIRED, 0);
            DdlUtil.formatOptionsPerLine(tokens, constraints, gapMap, firstOpt, end, gid,
                Set.of("IDENTIFIED","DEFAULT","TEMPORARY","QUOTA","PROFILE",
                       "ACCOUNT","PASSWORD","EXPIRE","LOCK","UNLOCK"));
        } else {
            DdlUtil.addDdlGap(tokens, constraints, gapMap, nameEnd, firstOpt,
                ConstraintSpec.NewlineMode.REQUIRED, 0);
        }
    }

    static void walkCreateProfile(FormatOptions opts, List<TokenInfo> tokens,
                                    List<ConstraintSpec> constraints,
                                    Map<String, ConstraintSpec> gapMap,
                                    int start, int end) {
        int profileKw = DdlUtil.findKeyword(tokens, start, end, "PROFILE");
        if (profileKw < 0) return;
        int nameIdx = DdlUtil.nextVisible(tokens, profileKw + 1, end);
        if (nameIdx < 0) return;
        int limitIdx = DdlUtil.nextVisibleKw(tokens, nameIdx + 1, end, "LIMIT");
        if (limitIdx < 0) return;
        int firstParam = DdlUtil.nextVisible(tokens, limitIdx + 1, end);
        if (firstParam < 0 || firstParam > end) return;

        DdlUtil.addDdlGap(tokens, constraints, gapMap, limitIdx, firstParam,
            ConstraintSpec.NewlineMode.REQUIRED, 0);

        String gid = "ddlProfileParam_" + DdlUtil.nextAlignGroup(gapMap);
        DdlUtil.formatOptionsPerLine(tokens, constraints, gapMap, firstParam, end, gid,
            Set.of("SESSIONS","CPU_PER","CONNECT","IDLE","FAILED","PASSWORD",
                   "COMPOSITE","PRIVATE","LOGICAL","READS","EXECUTE"));
    }
}
