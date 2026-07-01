package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import java.util.*;

public class StringAssembler {

    private final FormatOptions opts;
    private final List<TokenInfo> tokens;

    public StringAssembler(FormatOptions opts, List<TokenInfo> tokens) {
        this.opts = opts;
        this.tokens = tokens;
    }

    public String assemble(GapResult[] gaps) {
        StringBuilder out = new StringBuilder();
        boolean startOfLine = true;
        String lastText = null;

        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            GapResult g = (i < gaps.length) ? gaps[i] : null;

            if (ti.channel == 1) {
                if (opts.isCommentPreserve()
                    && (ti.text.startsWith("--") || ti.text.startsWith("/*"))) {
                    if (!endsWithNewline(out)) out.append('\n');
                    out.append(ti.text).append('\n');
                    startOfLine = true;
                }
                continue;
            }

            if (startOfLine) {
                int indent = (g != null && g.newline) ? g.indent : 0;
                appendIndent(out, indent);
                startOfLine = false;
            } else if (g != null && g.newline) {
                out.append('\n');
                appendIndent(out, g.indent);
                lastText = null;
            } else if (g != null) {
                // Use the gap result's space count (may be 0 for FORBIDDEN)
                if (g.spaces > 0 && out.length() > 0 && !endsWithNewline(out)) {
                    for (int s = 0; s < g.spaces; s++) out.append(' ');
                }
            } else {
                // No gap result: heuristic default spacing
                boolean needSpace = !ti.text.equals(".") && !ti.text.equals(")")
                    && !ti.text.equals(",") && !ti.text.equals(";") && !ti.text.equals("(")
                    && !ti.text.equals("%")
                    && !isMultiCharContinuation(lastText, ti.text);
                if (needSpace && out.length() > 0 && !endsWithNewline(out)) {
                    out.append(' ');
                }
            }

            out.append(ti.text);
            lastText = ti.text;
        }

        return out.toString().trim();
    }

    private boolean endsWithNewline(StringBuilder sb) {
        return sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n';
    }

    private boolean isMultiCharContinuation(String prev, String curr) {
        if (prev == null) return false;
        String pair = prev + curr;
        return pair.equals("||") || pair.equals("<<") || pair.equals(">>")
            || pair.equals("<=") || pair.equals(">=") || pair.equals("=>")
            || pair.equals("!=") || pair.equals("<>") || pair.equals("^=") || pair.equals("~=");
    }

    private void appendIndent(StringBuilder out, int size) {
        for (int i = 0; i < size; i++) out.append(' ');
    }
}
