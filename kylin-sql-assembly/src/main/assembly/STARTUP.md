# 启动脚本流程（kylin-sql.sh）

## Java 检测顺序

```
check_java() ─── 解析 java -version 获取主版本号，记录最高版本
```

| 优先级 | 来源 | 路径 |
|--------|------|------|
| 1 | 内置 JDK | `$APP_HOME/jdk/bin/java` |
| 2 | 内置 JRE | `$APP_HOME/jre/bin/java` |
| 3 | `JAVA_HOME` 环境变量 | `$JAVA_HOME/bin/java` |
| 4 | 系统 `PATH` | 遍历 PATH 下每个目录的 `java` |

## 版本校验

- `MIN_VER = 17`，低于 17 报错退出
- 多个 Java 时自动选版本最高的

## 启动

```bash
exec "$BEST_JAVA" $JVM_OPTIONS \
    -Dkylin.sql.home="$APP_HOME" \
    -Duser.home="$HOME" \
    -Dfile.encoding=UTF-8 \
    -cp "$APP_HOME/lib/*" \
    com.kylin.plsql.ui.KylinPlSqlApp
```

## 环境变量

- `LANG` / `LC_ALL` → `zh_CN.UTF-8`
- `JVM_OPTIONS` 可覆盖（默认 `-Xms256m -Xmx1024m`）
