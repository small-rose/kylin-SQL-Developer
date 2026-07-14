# 启动流程

应用提供两个启动脚本，Linux/macOS 用 `kylin-sql.sh`，Windows 用 `kylin-sql.bat`，两者逻辑完全对应。

---

## Linux / macOS / Kylin（kylin-sql.sh）

### Java 检测顺序

| 优先级 | 来源 | 路径 |
|--------|------|------|
| 1 | 内置 JDK | `$APP_HOME/jdk/bin/java` |
| 2 | 内置 JRE | `$APP_HOME/jre/bin/java` |
| 3 | `JAVA_HOME` 环境变量 | `$JAVA_HOME/bin/java` |
| 4 | 系统 `PATH` | 遍历 PATH 下每个目录的 `java` |

版本解析：`java -version 2>&1` → awk 提取主版本号，记录最高版本。

### 版本校验

- `MIN_VER = 17`，低于 17 报错退出
- 多个 Java 时自动选版本最高的
- 找不到 Java 时提示 `Install JDK 17+ or set JAVA_HOME`

### 启动命令

```bash
exec "$BEST_JAVA" $JVM_OPTIONS \
    -Dkylin.sql.home="$APP_HOME" \
    -Duser.home="$HOME" \
    -Dfile.encoding=UTF-8 \
    -cp "$APP_HOME/lib/*" \
    com.kylin.plsql.ui.KylinPlSqlApp
```

### 环境变量

- `LANG` / `LC_ALL` → `zh_CN.UTF-8`
- `JVM_OPTIONS` 可覆盖（默认 `-Xms256m -Xmx1024m -Dawt.useSystemAAFontSettings=on -Dsun.java2d.dpiaware=true`）

---

## Windows（kylin-sql.bat）

### Java 检测顺序

| 优先级 | 来源 | 路径 |
|--------|------|------|
| 1 | 内置 JDK | `%APP_HOME%\jdk\bin\java.exe` |
| 2 | 内置 JRE | `%APP_HOME%\jre\bin\java.exe` |
| 3 | `JAVA_HOME` 环境变量 | `%JAVA_HOME%\bin\java.exe` |
| 4 | 系统 `PATH` | `where java` 遍历所有路径 |

版本解析：`java -version` 重定向到临时文件 → `findstr /C:"version"` 取版本字符串 → 按 `.` 切分取主版本号，记录最高版本。

### 版本校验

- `MIN_VER = 17`，低于 17 报错退出（脚本无显式检查，运行时依赖 JVM 报错）
- 多个 Java 时自动选版本最高的
- 找不到 Java 时弹提示框并暂停

### 启动命令

```bat
"!BEST_JAVA!" %JVM_OPTIONS% ^
    -Dkylin.sql.home="%APP_HOME%" ^
    -Duser.home="%USERPROFILE%" ^
    -cp "%APP_HOME%\lib\*" ^
    com.kylin.plsql.ui.KylinPlSqlApp
```

### 特殊处理

- `where java` 返回的路径可能带尾部空格，`trim` 子程序去掉
- 启动失败时打印退出码并暂停

---

## JVM 选项（通用）

| 选项 | 说明 |
|------|------|
| `-Xms256m -Xmx1024m` | 堆内存 256M~1024M |
| `-Dawt.useSystemAAFontSettings=on` | 字体抗锯齿 |
| `-Dsun.java2d.dpiaware=true` | DPI 感知 |
| `-Dfile.encoding=UTF-8` | 文件编码 |
| `-Dkylin.sql.home` | 应用根目录 |
| `-Duser.home` | 用户主目录（配置文件存储位置） |
