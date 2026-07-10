package com.kylin.plsql.ui.component.center;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldParser;
import org.fife.ui.rsyntaxtextarea.folding.FoldType;

import javax.swing.text.BadLocationException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class PlSqlFoldParser implements FoldParser {

    @Override
    public List<Fold> getFolds(RSyntaxTextArea textArea) {
        List<Fold> topLevelFolds = new ArrayList<>();
        int lineCount = textArea.getLineCount();
        if (lineCount < 2) return topLevelFolds;

        String[] lines = new String[lineCount];
        for (int i = 0; i < lineCount; i++) {
            try {
                int start = textArea.getLineStartOffset(i);
                int end = textArea.getLineEndOffset(i);
                lines[i] = textArea.getText(start, end - start);
            } catch (BadLocationException e) {
                lines[i] = "";
            }
        }

        List<Region> regions = new ArrayList<>();
        addCommentRegions(lines, textArea, regions);
        addKeywordRegions(lines, textArea, regions);
        addParenRegions(lines, textArea, regions);

        regions.sort((a, b) -> {
            if (a.start != b.start) return Integer.compare(a.start, b.start);
            return Integer.compare(b.end, a.end);
        });

        Deque<Fold> stack = new ArrayDeque<>();
        for (Region r : regions) {
            while (!stack.isEmpty() && stack.peek().getEndOffset() <= r.start) {
                stack.pop();
            }
            try {
                if (!stack.isEmpty() && stack.peek().getEndOffset() >= r.end) {
                    Fold child = stack.peek().createChild(r.type, r.start);
                    child.setEndOffset(r.end);
                    stack.push(child);
                } else {
                    Fold fold = new Fold(r.type, textArea, r.start);
                    fold.setEndOffset(r.end);
                    topLevelFolds.add(fold);
                    stack.push(fold);
                }
            } catch (BadLocationException ignored) {
            }
        }
        return topLevelFolds;
    }

    private void addCommentRegions(String[] lines, RSyntaxTextArea textArea, List<Region> regions) {
        int lineCount = lines.length;
        for (int i = 0; i < lineCount; i++) {
            if (lines[i].contains("/*")) {
                int commentStart = i;
                while (i < lineCount && !lines[i].contains("*/")) i++;
                if (i < lineCount && i > commentStart) {
                    try {
                        int startOff = textArea.getLineStartOffset(commentStart);
                        int endOff = textArea.getLineEndOffset(i);
                        regions.add(new Region(startOff, endOff, FoldType.COMMENT));
                    } catch (BadLocationException ignored) {}
                }
            }
        }
    }

    private void addKeywordRegions(String[] lines, RSyntaxTextArea textArea, List<Region> regions) {
        int lineCount = lines.length;
        Deque<Integer> startStack = new ArrayDeque<>();
        for (int i = 0; i < lineCount; i++) {
            String trimmed = lines[i].trim();
            String upper = trimmed.toUpperCase();
            if (upper.isEmpty()) continue;

            try {
                if (upper.equals("BEGIN")
                        || (upper.startsWith("IF ") && !upper.contains("END IF"))
                        || ((upper.startsWith("LOOP") || upper.endsWith("LOOP")) && !upper.contains("END LOOP"))
                        || upper.equals("CASE") || upper.startsWith("CASE ")
                        || upper.equals("DECLARE")
                        || upper.startsWith("CREATE ")) {
                    startStack.push(textArea.getLineStartOffset(i));
                } else if (upper.startsWith("END") || upper.equals("END;")) {
                    if (startStack.isEmpty()) continue;
                    String endWord = upper.replace(";", "").trim();
                    if (endWord.equals("END") || endWord.equals("END IF")
                            || endWord.equals("END LOOP") || endWord.equals("END CASE")) {
                        int startOff = startStack.pop();
                        int endOff = textArea.getLineStartOffset(i);
                        if (endOff > startOff + 1) {
                            regions.add(new Region(startOff, endOff, FoldType.CODE));
                        }
                    }
                }
            } catch (BadLocationException ignored) {
                break;
            }
        }
    }

    private void addParenRegions(String[] lines, RSyntaxTextArea textArea, List<Region> regions) {
        int lineCount = lines.length;
        Deque<Integer> parenStack = new ArrayDeque<>();
        boolean inString = false;
        boolean inBlockComment = false;

        for (int i = 0; i < lineCount; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;

            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);

                if (inString) {
                    if (c == '\'') inString = false;
                    continue;
                }
                if (inBlockComment) {
                    if (j + 1 < line.length() && c == '*' && line.charAt(j + 1) == '/') {
                        inBlockComment = false;
                        j++;
                    }
                    continue;
                }
                if (c == '\'') { inString = true; continue; }
                if (j + 1 < line.length()) {
                    if (c == '-' && line.charAt(j + 1) == '-') break;
                    if (c == '/' && line.charAt(j + 1) == '*') {
                        inBlockComment = true;
                        j++;
                        continue;
                    }
                }
                if (c == '(') {
                    try {
                        parenStack.push(textArea.getLineStartOffset(i) + j);
                    } catch (BadLocationException ignored) {}
                } else if (c == ')') {
                    if (!parenStack.isEmpty()) {
                        int startOff = parenStack.pop();
                        try {
                            int endOff = textArea.getLineStartOffset(i) + j + 1;
                            int startLine = textArea.getLineOfOffset(startOff);
                            if (i > startLine) {
                                regions.add(new Region(startOff, endOff, FoldType.CODE));
                            }
                        } catch (BadLocationException ignored) {}
                    }
                }
            }
        }
    }

    private static class Region {
        final int start;
        final int end;
        final int type;
        Region(int start, int end, int type) {
            this.start = start;
            this.end = end;
            this.type = type;
        }
    }
}
