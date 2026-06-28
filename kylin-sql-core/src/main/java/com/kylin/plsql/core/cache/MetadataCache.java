package com.kylin.plsql.core.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MetadataCache {
    private static final Logger log = LoggerFactory.getLogger(MetadataCache.class);
    private static volatile MetadataCache instance;

    private final Path baseDir;
    private final Gson gson;
    private final Map<String, CachedConnection> memory = new ConcurrentHashMap<>();

    // ── Data classes for serialization ──

    public static class CachedColumn {
        public String name;
        public String type;
        public int size;
        public boolean nullable;
    }

    public static class CachedConnection {
        public String name;
        public String dbProduct;
        public long cachedAt;
        public Map<String, Map<String, List<String>>> objects = new LinkedHashMap<>(); // schema → type → [names]
        public Map<String, List<CachedColumn>> columns = new LinkedHashMap<>();        // "schema.table" → columns
        public Map<String, String> ddlCache = new LinkedHashMap<>();                   // "schema.table" → DDL text
    }

    // ── Singleton ──

    public static MetadataCache getInstance() {
        if (instance == null) {
            synchronized (MetadataCache.class) {
                if (instance == null) {
                    instance = new MetadataCache();
                }
            }
        }
        return instance;
    }

    private MetadataCache() {
        String home = System.getProperty("user.home", ".");
        baseDir = Paths.get(home, ".kylin-sql", "cache");
        gson = new GsonBuilder().setPrettyPrinting().create();
        try { Files.createDirectories(baseDir); } catch (IOException e) {
            log.warn("\u521B\u5EFA\u7F13\u5B58\u76EE\u5F55\u5931\u8D25: {}", e.getMessage());
        }
    }

    // ── Internal helpers ──

    private Path connDir(String connName) {
        return baseDir.resolve(Integer.toHexString(connName.hashCode()));
    }

    private Path cacheFile(String connName) {
        return connDir(connName).resolve("_cache.json");
    }

    private CachedConnection load(String connName) {
        CachedConnection cc = memory.get(connName);
        if (cc != null) return cc;
        File f = cacheFile(connName).toFile();
        if (f.exists()) {
            try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                cc = gson.fromJson(r, CachedConnection.class);
            } catch (IOException e) {
                log.debug("\u8BFB\u53D6\u7F13\u5B58\u5931\u8D25 {}: {}", connName, e.getMessage());
            }
        }
        if (cc == null) {
            cc = new CachedConnection();
            cc.name = connName;
        }
        memory.put(connName, cc);
        return cc;
    }

    private void save(String connName) {
        CachedConnection cc = memory.get(connName);
        if (cc == null) return;
        cc.cachedAt = System.currentTimeMillis();
        try {
            Files.createDirectories(connDir(connName));
            try (Writer w = new OutputStreamWriter(new FileOutputStream(cacheFile(connName).toFile()), StandardCharsets.UTF_8)) {
                gson.toJson(cc, w);
            }
        } catch (IOException e) {
            log.warn("\u4FDD\u5B58\u7F13\u5B58\u5931\u8D25 {}: {}", connName, e.getMessage());
        }
    }

    // ── Public API ──

    /** Check if schemas metadata is cached for a connection */
    public boolean hasMetadata(String connName) {
        return !load(connName).objects.isEmpty();
    }

    public String getDbProduct(String connName) {
        return load(connName).dbProduct;
    }

    public List<String> getSchemas(String connName) {
        return new ArrayList<>(load(connName).objects.keySet());
    }

    public List<String> getObjects(String connName, String schema, String type) {
        var byType = load(connName).objects.get(schema);
        return byType != null ? byType.get(type) : null;
    }

    public void putSchemas(String connName, String dbProduct, Collection<String> schemas) {
        CachedConnection cc = load(connName);
        cc.dbProduct = dbProduct;
        for (String s : schemas) cc.objects.putIfAbsent(s, new LinkedHashMap<>());
        save(connName);
    }

    public void putObjects(String connName, String schema, String type, List<String> objects) {
        CachedConnection cc = load(connName);
        cc.objects.computeIfAbsent(schema, k -> new LinkedHashMap<>()).put(type, objects);
        save(connName);
    }

    public List<CachedColumn> getColumns(String connName, String schema, String table) {
        return load(connName).columns.get(schema + "." + table);
    }

    public void putColumns(String connName, String schema, String table, List<CachedColumn> cols) {
        load(connName).columns.put(schema + "." + table, cols);
        save(connName);
    }

    public String getDDL(String connName, String schema, String table) {
        return load(connName).ddlCache.get(schema + "." + table);
    }

    public void putDDL(String connName, String schema, String table, String ddl) {
        load(connName).ddlCache.put(schema + "." + table, ddl);
        save(connName);
    }

    /** Remove cached data for a connection (called on refresh) */
    public void clearConnection(String connName) {
        memory.remove(connName);
        try { Files.deleteIfExists(cacheFile(connName)); } catch (IOException ignored) {}
        try { Files.deleteIfExists(connDir(connName)); } catch (IOException ignored) {}
    }
}