package com.kylin.plsql.core.service;

import java.io.*;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ImportService {

    public static class ImportConfig {
        public char delimiter = ',';
        public char quote = '"';
        public Charset charset = Charset.forName("UTF-8");
        public boolean hasHeader = true;
        public int skipLines = 0;
    }

    public static class ImportResult {
        public final int successRows;
        public final int failRows;
        public final String error;

        public ImportResult(int successRows, int failRows, String error) {
            this.successRows = successRows;
            this.failRows = failRows;
            this.error = error;
        }
    }

    public List<String> parseHeader(File file, ImportConfig cfg) throws IOException {
        List<String> lines = readLines(file, cfg, 1);
        if (lines.isEmpty()) return new ArrayList<>();
        return splitLine(lines.get(0), cfg.delimiter, cfg.quote);
    }

    public List<List<String>> preview(File file, ImportConfig cfg, int maxRows) throws IOException {
        List<String> lines = readLines(file, cfg, maxRows + (cfg.hasHeader ? 1 : 0));
        int start = cfg.hasHeader ? 1 : 0;
        List<List<String>> result = new ArrayList<>();
        for (int i = start; i < lines.size() && result.size() < maxRows; i++) {
            result.add(splitLine(lines.get(i), cfg.delimiter, cfg.quote));
        }
        return result;
    }

    public ImportResult execute(Connection conn, String schema, String table,
                                File file, ImportConfig cfg,
                                List<String> tableColumns, List<Integer> columnMapping) {
        int success = 0, fail = 0;
        try {
            List<String> lines = readLines(file, cfg, Integer.MAX_VALUE);
            int start = (cfg.hasHeader ? 1 : 0) + cfg.skipLines;
            String fullName = schema != null && !schema.isEmpty() ? schema + "." + table : table;
            String cols = String.join(",", tableColumns);
            String placeholders = String.join(",", java.util.Collections.nCopies(tableColumns.size(), "?"));
            String sql = "INSERT INTO " + fullName + " (" + cols + ") VALUES (" + placeholders + ")";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = start; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.isEmpty()) continue;
                    List<String> values = splitLine(line, cfg.delimiter, cfg.quote);
                    ps.clearParameters();
                    for (int j = 0; j < columnMapping.size(); j++) {
                        int colIdx = columnMapping.get(j);
                        if (colIdx >= 0 && colIdx < values.size()) {
                            ps.setString(j + 1, values.get(colIdx));
                        } else {
                            ps.setNull(j + 1, java.sql.Types.VARCHAR);
                        }
                    }
                    ps.addBatch();
                    if ((i - start + 1) % 500 == 0) {
                        int[] results = ps.executeBatch();
                        for (int r : results) { if (r > 0) success++; else fail++; }
                    }
                }
                int[] results = ps.executeBatch();
                for (int r : results) { if (r > 0) success++; else fail++; }
            }
        } catch (Exception e) {
            return new ImportResult(success, fail, e.getMessage());
        }
        return new ImportResult(success, fail, null);
    }

    /** 将预解析的行数据批量 INSERT 到目标表（用于 JSON 等非 CSV 格式）。 */
    public ImportResult execute(Connection conn, String schema, String table,
                                List<List<Object>> rows, List<String> tableColumns) {
        int success = 0, fail = 0;
        try {
            String cols = String.join(",", tableColumns);
            String placeholders = String.join(",", java.util.Collections.nCopies(tableColumns.size(), "?"));
            String fullName = schema != null && !schema.isEmpty() ? schema + "." + table : table;
            String sql = "INSERT INTO " + fullName + " (" + cols + ") VALUES (" + placeholders + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (List<Object> row : rows) {
                    ps.clearParameters();
                    for (int j = 0; j < tableColumns.size(); j++) {
                        Object v = j < row.size() ? row.get(j) : null;
                        if (v != null) {
                            ps.setObject(j + 1, v);
                        } else {
                            ps.setNull(j + 1, java.sql.Types.VARCHAR);
                        }
                    }
                    ps.addBatch();
                }
                int[] results = ps.executeBatch();
                for (int r : results) { if (r > 0) success++; else fail++; }
            }
        } catch (Exception e) {
            return new ImportResult(success, fail, e.getMessage());
        }
        return new ImportResult(success, fail, null);
    }

    private List<String> readLines(File file, ImportConfig cfg, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), cfg.charset))) {
            String line;
            while ((line = br.readLine()) != null && lines.size() < maxLines) {
                lines.add(line);
            }
        }
        return lines;
    }

    private List<String> splitLine(String line, char delimiter, char quote) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == quote) {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == quote) {
                    cur.append(quote);
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                fields.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString().trim());
        return fields;
    }

    // === JSON 解析 ===

    private static final Gson GSON = new Gson();

    /** 解析 JSON 文件的列头（第一个对象的 key 列表）。 */
    public List<String> parseJsonHeader(File file) throws IOException {
        List<Map<String, Object>> data = readJson(file);
        if (data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(data.get(0).keySet());
    }

    /** 预览 JSON 文件的前 N 行数据。 */
    public List<List<String>> previewJson(File file, int maxRows) throws IOException {
        List<Map<String, Object>> data = readJson(file);
        List<String> headers = data.isEmpty() ? new ArrayList<>() : new ArrayList<>(data.get(0).keySet());
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(maxRows, data.size()); i++) {
            List<String> row = new ArrayList<>();
            for (String h : headers) {
                Object v = data.get(i).get(h);
                row.add(v != null ? v.toString() : "");
            }
            result.add(row);
        }
        return result;
    }

    /** 将 JSON 文件解析为行数据列表（每行为 Object 列表，按列头顺序）。 */
    public List<List<Object>> parseJsonRows(File file) throws IOException {
        List<Map<String, Object>> data = readJson(file);
        List<String> headers = data.isEmpty() ? new ArrayList<>() : new ArrayList<>(data.get(0).keySet());
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> obj : data) {
            List<Object> row = new ArrayList<>();
            for (String h : headers) row.add(obj.get(h));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> readJson(File file) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, new TypeToken<List<Map<String, Object>>>(){}.getType());
        }
    }
}
