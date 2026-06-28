#!/bin/bash
# Kylin SQL Developer - Linux/Kylin startup script
# Encoding: UTF-8

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
BEST_JAVA=
BEST_VER=0
JVM_OPTIONS="-Xms256m -Xmx512m -Dawt.useSystemAAFontSettings=on -Dsun.java2d.dpiaware=true"

check_java() {
    local java_exe="$1"
    [ -x "$java_exe" ] || return 1

    local ver
    ver=$("$java_exe" -version 2>&1 | awk -F '["_.]' '/version/ {if($2=="1") print $3; else print $2; exit}')
    [ -z "$ver" ] && return 1

    if [ "$ver" -gt "$BEST_VER" ] 2>/dev/null; then
        BEST_JAVA="$java_exe"
        BEST_VER=$ver
    fi
}

# 1. Bundled JDK/JRE
check_java "$APP_HOME/jdk/bin/java"
check_java "$APP_HOME/jre/bin/java"

# 2. JAVA_HOME
[ -n "$JAVA_HOME" ] && check_java "$JAVA_HOME/bin/java"

# 3. PATH (all matches, pick highest version)
if command -v java &>/dev/null; then
    while IFS= read -r jpath; do
        check_java "$jpath"
    done < <(which -a java 2>/dev/null || command -v java 2>/dev/null)
fi

if [ -z "$BEST_JAVA" ]; then
    echo "[ERROR] Java not found. Install JDK 17+ or set JAVA_HOME." >&2
    exit 1
fi

echo "Using: $BEST_JAVA (version $BEST_VER)"

export LANG=zh_CN.UTF-8
export LC_ALL=zh_CN.UTF-8

: "${JVM_OPTIONS:="-Xms256m -Xmx1024m -Dawt.useSystemAAFontSettings=on -Dsun.java2d.dpiaware=true"}"

exec "$BEST_JAVA" \
    $JVM_OPTIONS \
    -Dkylin.sql.home="$APP_HOME" \
    -Duser.home="$HOME" \
    -Dfile.encoding=UTF-8 \
    -cp "$APP_HOME/lib/*" \
    com.kylin.plsql.ui.KylinPlSqlApp
