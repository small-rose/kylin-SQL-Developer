package com.kylin.plsql.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Maven 仓库驱动下载器。从 Maven Central 或阿里云镜像下载 JDBC 驱动 JAR，
 *  通过 URLClassLoader + DriverProxy 注册到 DriverManager，绕过跨 ClassLoader 安全检查。 */
public class DriverDownloader {
    private static final Logger log = LoggerFactory.getLogger(DriverDownloader.class);
    private static final File LIB_DIR = new File(System.getProperty("user.home"), ".kylin-sql/lib");
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";

    public static final Map<String, String> DEFAULT_GAV = Map.ofEntries(
        Map.entry("oracle",            "com.oracle.database.jdbc:ojdbc11:21.17.0.0"),
        Map.entry("oceanbase-oracle",  "com.oceanbase:oceanbase-client:2.4.18"),
        Map.entry("oceanbase-mysql",   "com.oceanbase:oceanbase-client:2.4.18"),
        Map.entry("mysql",             "com.mysql:mysql-connector-j:9.2.0"),
        Map.entry("mariadb",           "org.mariadb.jdbc:mariadb-java-client:3.5.1"),
        Map.entry("postgresql",        "org.postgresql:postgresql:42.7.5")
    );

    /** 持久 URLClassLoader：包含所有已下载的 JAR URL，父为系统 ClassLoader。 */
    private volatile URLClassLoader driverLoader;
    /** 已下载的 JAR 文件 URL 列表。 */
    private final List<URL> jarUrls = new ArrayList<>();
    /** 已成功解析的驱动类名缓存。 */
    private final Map<String, Boolean> resolvedDrivers = new ConcurrentHashMap<>();

    public DriverDownloader() {
        // 先加载本地已有 JAR
        rebuildClassLoader();
    }

    /** 获取 URLClassLoader，供 ConnectionManager 设线程上下文。 */
    public ClassLoader getClassLoader() {
        return driverLoader;
    }

    /** URLClassLoader 是否包含已下载的 JAR。不含时不应设为线程上下文（会阻止 HikariCP 加载 classpath 驱动）。 */
    public boolean hasJars() {
        return !jarUrls.isEmpty();
    }

    /** 返回已下载的 JAR URL 列表（供诊断用）。 */
    public List<URL> getJarUrls() {
        return new ArrayList<>(jarUrls);
    }

    /** 确保驱动可用，返回 true 表示加载成功。 */
    public boolean resolve(ConnectionInfo info) {
        String driverClass = resolveDriverClassName(info);
        if (driverClass == null || driverClass.isBlank()) return false;

        if (resolvedDrivers.containsKey(driverClass)) {
            log.debug("resolve {}: 已缓存", driverClass);
            return true;
        }

        if (isOnClasspath(driverClass)) {
            resolvedDrivers.put(driverClass, true);
            log.info("resolve {}: 在 classpath 中找到", driverClass);
            return true;
        }

        String gav = info.getMavenGav();
        if (gav == null || gav.isBlank()) gav = DEFAULT_GAV.get(info.getDbType());
        log.info("resolve {}: 开始下载, gav={}", driverClass, gav);
        if (gav != null && !gav.isBlank()) {
            if (tryDownloadAndLoad(gav, info.getMavenRepoUrl(), driverClass)) return true;
        }

        if (info.getCustomDriverJar() != null && !info.getCustomDriverJar().isBlank()) {
            File jarFile = new File(info.getCustomDriverJar());
            if (jarFile.exists() && loadFromJar(jarFile, driverClass)) return true;
        }

        return false;
    }

    private boolean tryDownloadAndLoad(String gav, String repoUrl, String driverClass) {
        try {
            File localJar = findLocal(gav);
            if (localJar == null) {
                try {
                    localJar = download(gav, repoUrl);
                } catch (Exception e) {
                    log.warn("Maven Central 下载失败 ({}), 尝试阿里云镜像", e.getMessage());
                    localJar = download(gav, "https://maven.aliyun.com/repository/public");
                }
            }
            if (localJar != null && loadFromJar(localJar, driverClass)) return true;
        } catch (Exception e) {
            log.warn("自动下载驱动失败 ({}): {}", gav, e.getMessage());
        }
        return false;
    }

    public boolean isOnClasspath(String driverClass) {
        try {
            Class.forName(driverClass);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** 从 Maven 仓库下载 JAR 到本地缓存。 */
    public File download(String gav, String repoUrl) throws IOException {
        String[] parts = gav.split(":");
        if (parts.length < 3) throw new IllegalArgumentException("GAV 格式错误: " + gav);
        String jarName = parts[1] + "-" + parts[2] + ".jar";
        String path = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + jarName;

        LIB_DIR.mkdirs();
        File localFile = new File(LIB_DIR, jarName);

        String baseUrl = (repoUrl != null && !repoUrl.isBlank()) ? repoUrl : MAVEN_CENTRAL;
        if (!baseUrl.endsWith("/")) baseUrl += "/";
        URL url = new URL(baseUrl + path);

        log.info("下载驱动: {} -> {}", url, localFile);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        int responseCode = conn.getResponseCode();
        log.info("HTTP 响应: code={}, type={}", responseCode, conn.getContentType());
        if (responseCode < 200 || responseCode >= 300) {
            try (InputStream err = conn.getErrorStream()) {
                if (err != null) {
                    byte[] buf = err.readAllBytes();
                    log.warn("HTTP 错误体(前200字节): {}", new String(buf, 0, Math.min(buf.length, 200), StandardCharsets.UTF_8));
                }
            }
            throw new IOException("HTTP " + responseCode + ": " + conn.getResponseMessage());
        }
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (!isValidJar(localFile)) {
            try (RandomAccessFile raf = new RandomAccessFile(localFile, "r")) {
                byte[] header = new byte[Math.min((int)raf.length(), 200)];
                raf.readFully(header);
                log.warn("无效 JAR，文件头: {}", new String(header, StandardCharsets.UTF_8).replace('\n', ' ').replace('\r', ' '));
            } catch (Exception ignored) {}
            localFile.delete();
            throw new IOException("下载的文件不是有效的 JAR 文件");
        }
        log.info("下载完成: {} ({} bytes)", localFile, localFile.length());
        return localFile;
    }

    private boolean isValidJar(File file) {
        if (!file.exists() || file.length() < 100) return false;
        try (ZipFile zf = new ZipFile(file)) {
            return zf.entries().hasMoreElements();
        } catch (ZipException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public File findLocal(String gav) {
        String[] parts = gav.split(":");
        if (parts.length < 3) return null;
        File f = new File(LIB_DIR, parts[1] + "-" + parts[2] + ".jar");
        return f.exists() ? f : null;
    }

    /** 从 JAR 文件中加载驱动类。将 JAR 加入 URLClassLoader，通过 DriverProxy 注册到 DriverManager。 */
    private boolean loadFromJar(File jarFile, String driverClass) {
        try {
            addJar(jarFile);
            Class<?> cls = Class.forName(driverClass, true, driverLoader);
            Driver driver = (Driver) cls.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverProxy(driver));
            resolvedDrivers.put(driverClass, true);
            log.info("驱动加载并注册成功: {} <- {}", driverClass, jarFile);
            return true;
        } catch (Exception e) {
            log.warn("驱动加载失败 ({}): {}", driverClass, e.getMessage());
            return false;
        }
    }

    /** 将 JAR 加入持久 ClassLoader 并重建。 */
    private void addJar(File jarFile) throws Exception {
        URL jarUrl = jarFile.toURI().toURL();
        if (!jarUrls.contains(jarUrl)) {
            jarUrls.add(jarUrl);
        }
        rebuildClassLoader();
    }

    /** 重建 URLClassLoader（包含所有已添加的 JAR）。 */
    private synchronized void rebuildClassLoader() {
        URL[] urls = jarUrls.toArray(new URL[0]);
        driverLoader = new URLClassLoader(urls, getClass().getClassLoader());
        log.debug("URLClassLoader 已重建: {} 个 JAR", urls.length);
    }

    private String resolveDriverClassName(ConnectionInfo info) {
        if (info.getCustomDriverClass() != null && !info.getCustomDriverClass().isBlank())
            return info.getCustomDriverClass();
        try {
            var coord = com.kylin.plsql.core.db.type.DbTypeCoordinator.forConnection(info);
            return coord.resolveDriverClassName(info);
        } catch (Exception e) {
            return null;
        }
    }
}
