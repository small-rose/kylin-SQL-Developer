package com.kylin.plsql.core.db.services;

import com.kylin.plsql.core.db.ConnectionInfo;
import com.kylin.plsql.core.db.type.DbTypeSpec;

import java.util.*;

/**
 * JDBC 行为抽象基类 / Abstract base for JDBC connection behavior.
 * <p>
 * 每个数据库类型一个子类，覆盖差异方法。基类提供合理的默认值和通用模板。
 * One subclass per database type, override only the methods that differ.
 * <p>
 * 扩展方式 / Extension: {@code class XxxJdbcService extends JdbcService}
 * <ul>
 *   <li>必覆：{@link #spec()}, {@link #buildProtocolUrl(ConnectionInfo)}</li>
 *   <li>可选覆：{@link #parseJdbcUrl(String)}, 各种 getter</li>
 * </ul>
 */
public abstract class JdbcService {

    /** 返回关联的 DbTypeSpec / Associated type descriptor. */
    public abstract DbTypeSpec spec();

    // ── URL 构建 / URL Building ──

    /**
     * 构建完整 JDBC URL（入口）/ Build full JDBC URL (entry point).
     * <p>
     * 流程：useUrl 短路 → buildProtocolUrl() → appendParams()。
     * Pipeline: shortcut for user-provided URL → protocol URL → append params.
     */
    public String buildUrl(ConnectionInfo info) {
        if (info.isUseUrl() && info.getJdbcUrl() != null && !info.getJdbcUrl().isBlank()) {
            return info.getJdbcUrl();
        }
        String url = buildProtocolUrl(info);
        return appendParams(url, info);
    }

    /** 协议部分 URL 构建（子类实现）/ Protocol-specific URL (subclasses override). */
    protected abstract String buildProtocolUrl(ConnectionInfo info);

    /**
     * 追加 compatibleOjdbcVersion + 自定义 JDBC 参数 / Append version compat + custom params.
     * <p>
     * 默认用查询字符串风格（?key=value）。Oracle 等有专用属性机制的需覆写。
     * Default: query-string style. Override for driver-specific property mechanisms.
     */
    protected String appendParams(String url, ConnectionInfo info) {
        StringBuilder sb = new StringBuilder(url);
        boolean hasQuery = url.contains("?");

        Optional<String> compatVer = getCompatibleOjdbcVersion();
        if (compatVer.isPresent()) {
            sb.append(hasQuery ? '&' : '?');
            sb.append("compatibleOjdbcVersion=").append(compatVer.get());
            hasQuery = true;
        }

        if (info.getJdbcParams() != null) {
            for (Map.Entry<String, String> e : info.getJdbcParams().entrySet()) {
                if (e.getKey() != null && !e.getKey().isBlank()) {
                    sb.append(hasQuery ? '&' : '?');
                    sb.append(e.getKey()).append('=').append(e.getValue() != null ? e.getValue() : "");
                    hasQuery = true;
                }
            }
        }
        return sb.toString();
    }

    // ── URL 解析（反向）/ URL Parsing (reverse) ──

    /** JDBC URL 解析结果 / Parsed result from a JDBC URL. */
    public static class ParsedUrl {
        public final String host;
        public final int port;
        public final String serviceName;

        public ParsedUrl(String host, int port, String serviceName) {
            this.host = host;
            this.port = port;
            this.serviceName = serviceName;
        }
    }

    /**
     * 通用 "host:port/service" 格式的 URL 解析器 / Shared parser for "host:port/service" format.
     * <p>
     * 适用于 jdbc:xxx://host:port/db 格式。Oracle @host:port/service 格式各自实现。
     * Suitable for {@code ://host:port/db} style. Oracle's {@code @host:port/service} handled separately.
     */
    protected static ParsedUrl parseHostPortDb(String url, int prefixLen) {
        try {
            String rest = url.substring(prefixLen);
            String[] path = rest.split("/");
            String host = "";
            int port = 2881;
            String svc = "";
            if (path.length >= 1) {
                String[] hp = path[0].split(":");
                if (hp.length >= 1) host = hp[0];
                if (hp.length >= 2) port = Integer.parseInt(hp[1].replaceAll("[^0-9]", ""));
            }
            if (path.length >= 2) {
                svc = path[1].contains("?") ? path[1].substring(0, path[1].indexOf('?')) : path[1];
            }
            return new ParsedUrl(host, port, svc);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 JDBC URL 解析 host/port/serviceName（默认不支持） */
    public ParsedUrl parseJdbcUrl(String url) { return null; }

    // ── 连接行为 / Connection Behavior ──

    /** 连接测试查询（默认 SELECT 1，Oracle 语系需 FROM DUAL） */
    public String getConnectionTestQuery()                    { return "SELECT 1"; }
    /** 初始化失败超时毫秒数（0=不设置）/ Connection fail timeout in ms. */
    public int getInitFailTimeout()                           { return 0; }
    /** 是否支持 HikariCP 原生 setSchema() / Native schema setting support. */
    public boolean supportsSetSchema()                        { return false; }
    /** OceanBase Oracle 兼容版本（默认无）/ Compatible ojdbc version. */
    public Optional<String> getCompatibleOjdbcVersion()       { return Optional.empty(); }
    /** 类型默认 JDBC 参数（可基于 info 中的 dbVersion 版本差异化） */
    public Map<String, String> getDefaultJdbcParams(ConnectionInfo info) { return Collections.emptyMap(); }

    /**
     * 驱动类名解析 / Resolve driver class name.
     * <p>
     * 优先级 / Priority：customDriverClass > URL 检测 > spec.getDriverClassName()
     */
    public String resolveDriverClassName(ConnectionInfo info) {
        if (info.getCustomDriverClass() != null && !info.getCustomDriverClass().isBlank()) {
            return info.getCustomDriverClass();
        }
        if (info.isUseUrl() && info.getJdbcUrl() != null && !info.getJdbcUrl().isBlank()) {
            String fromUrl = detectDriverFromUrl(info.getJdbcUrl());
            if (!fromUrl.isEmpty()) return fromUrl;
        }
        return spec().getDriverClassName();
    }

    /** URL → 驱动类名检测 / Detect driver class from URL prefix. */
    private static String detectDriverFromUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:oceanbase:"))    return "com.oceanbase.jdbc.Driver";
        if (lower.startsWith("jdbc:postgresql:"))   return "org.postgresql.Driver";
        if (lower.startsWith("jdbc:oracle:"))       return "oracle.jdbc.OracleDriver";
        if (lower.startsWith("jdbc:mysql:"))        return "com.mysql.cj.jdbc.Driver";
        if (lower.startsWith("jdbc:mariadb:"))      return "org.mariadb.jdbc.Driver";
        if (lower.startsWith("jdbc:sqlserver:"))    return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        if (lower.startsWith("jdbc:db2:"))          return "com.ibm.db2.jcc.DB2Driver";
        if (lower.startsWith("jdbc:h2:"))           return "org.h2.Driver";
        if (lower.startsWith("jdbc:sqlite:"))       return "org.sqlite.JDBC";
        return "";
    }
}
