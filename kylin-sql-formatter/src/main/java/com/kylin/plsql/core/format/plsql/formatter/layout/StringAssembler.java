package com.kylin.plsql.core.format.plsql.formatter.layout;

import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.plsql.model.TokenInfo;
import com.kylin.plsql.core.format.plsql.formatter.layout.plan.FinalLayout;
import java.util.*;

public class StringAssembler {

    private final FormatOptions opts;
    private final List<TokenInfo> tokens;
    private final String source;
    private BitSet offRegion;
    private int[] charStart;

    public StringAssembler(FormatOptions opts, List<TokenInfo> tokens) {
        this(opts, tokens, null);
    }

    public StringAssembler(FormatOptions opts, List<TokenInfo> tokens, String source) {
        this.opts = opts;
        this.tokens = tokens;
        this.source = source;
    }

    public String assemble(FinalLayout[] layout) {
        if (source != null) {
            this.offRegion = scanOffRegions();
            if (!offRegion.isEmpty()) {
                this.charStart = computeCharOffsets();
            }
        }

        StringBuilder out = new StringBuilder();
        boolean startOfLine = true;

        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            FinalLayout f = (i < layout.length) ? layout[i] : null;

            if (ti.channel == 1) {
                if (offRegion != null && offRegion.get(i)) continue;
                if (opts.isCommentPreserve()
                    && (ti.text.startsWith("--") || ti.text.startsWith("/*"))) {
                    boolean trailing = isTrailingComment(i);
                    if (trailing) {
                        if (!endsWithNewline(out)) out.append(' ');
                        String text = ti.text;
                        if (text.endsWith("\n")) {
                            text = text.substring(0, text.length() - 1);
                            if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
                        }
                        out.append(text);
                    } else {
                        if (!endsWithNewline(out)) out.append('\n');
                        int indent = 0;
                        for (int j = i + 1; j < tokens.size() && j < layout.length; j++) {
                            if (tokens.get(j).channel == 0 && layout[j].isNewline()) {
                                indent = layout[j].getIndent();
                                break;
                            }
                        }
                        String text = ti.text;
                        if (text.startsWith("--") && text.endsWith("\n")) {
                            text = text.substring(0, text.length() - 1);
                            if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
                        }
                        if (text.startsWith("/*") && text.contains("\n")) {
                            String[] lines = text.split("\n", -1);
                            for (int li = 0; li < lines.length; li++) {
                                if (li > 0) {
                                    out.append('\n');
                                    appendIndent(out, indent + 1);
                                    String line = lines[li].replaceAll("^\\s+", "");
                                    out.append(line);
                                } else {
                                    appendIndent(out, indent);
                                    out.append(lines[li]);
                                }
                            }
                        } else {
                            appendIndent(out, indent);
                            out.append(text);
                        }
                        out.append('\n');
                        startOfLine = true;
                    }
                }
                continue;
            }

            if (offRegion != null && offRegion.get(i)) {
                int blockStart = i;
                int blockEnd = i;
                while (blockEnd + 1 < tokens.size() && offRegion.get(blockEnd + 1)) blockEnd++;

                int start;
                if (blockStart > 0) {
                    int prevEnd = charStart[blockStart - 1] + tokens.get(blockStart - 1).text.length();
                    start = prevEnd;
                } else {
                    start = charStart[blockStart];
                }
                int end = charStart[blockEnd] + tokens.get(blockEnd).text.length();
                out.append(source, start, end);
                if (end > 0 && source.charAt(end - 1) == '\n') startOfLine = true;
                i = blockEnd;
                continue;
            }

            if (f == null) {
                out.append(ti.text);
                continue;
            }

            if (startOfLine) {
                int indent = f.isNewline() ? f.getIndent() : 0;
                if (!f.isNewline() && out.length() > 0) {
                    if (f.getSpaces() > 0 && !endsWithNewline(out)) {
                        for (int s = 0; s < f.getSpaces(); s++) out.append(' ');
                    }
                }
                appendIndent(out, indent);
                startOfLine = false;
            } else if (f.isNewline()) {
                out.append('\n');
                appendIndent(out, f.getIndent());
            } else if (f.getSpaces() > 0 && out.length() > 0) {
                if (!endsWithNewline(out)) {
                    for (int s = 0; s < f.getSpaces(); s++) out.append(' ');
                }
            }

            out.append(ti.text);
        }

        return out.toString().trim();
    }

    private BitSet scanOffRegions() {
        BitSet off = new BitSet(tokens.size());
        boolean inOff = false;
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            if (ti.channel == 1) {
                String lower = ti.text.toLowerCase();
                if (lower.contains("@formatter:off")) {
                    inOff = true;
                    continue;
                }
                if (lower.contains("@formatter:on")) {
                    inOff = false;
                    continue;
                }
            }
            if (inOff) off.set(i);
        }
        return off;
    }

    private int[] computeCharOffsets() {
        Map<Integer, Integer> lineStart = new HashMap<>();
        lineStart.put(1, 0);
        int line = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
                lineStart.put(line, i + 1);
            }
        }

        int[] offsets = new int[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo ti = tokens.get(i);
            int sol = lineStart.getOrDefault(ti.line, 0);
            offsets[i] = sol + ti.column;
        }
        return offsets;
    }

    private boolean isTrailingComment(int idx) {
        TokenInfo comment = tokens.get(idx);
        for (int j = idx - 1; j >= 0; j--) {
            if (tokens.get(j).channel == 0) {
                return tokens.get(j).line == comment.line;
            }
        }
        return false;
    }

    private boolean endsWithNewline(StringBuilder sb) {
        return sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n';
    }

    private void appendIndent(StringBuilder out, int size) {
        for (int i = 0; i < size; i++) out.append(' ');
    }

}
