@echo off
setlocal enabledelayedexpansion

set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"
set BEST_JAVA=
set BEST_VER=0
set "JVM_OPTIONS=-Xms256m -Xmx512m -Dawt.useSystemAAFontSettings=on -Dsun.java2d.dpiaware=true"

if exist "%APP_HOME%\jdk\bin\java.exe" call :check "%APP_HOME%\jdk\bin\java.exe"
if exist "%APP_HOME%\jre\bin\java.exe" call :check "%APP_HOME%\jre\bin\java.exe"

if not "%JAVA_HOME%"=="" call :check "%JAVA_HOME%\bin\java.exe"

for /f "delims=" %%j in ('where java 2^>nul') do (
    set "JPATH=%%j"
    call :trim "!JPATH!"
    if not "!JPATH!"=="" call :check "!JPATH!"
)

if "%BEST_JAVA%"=="" (
    echo [ERROR] Java not found. Please install JDK 17+ or set JAVA_HOME.
    pause
    exit /b 1
)

REM strip trailing spaces from BEST_JAVA
set "_BJ=!BEST_JAVA!"
:strip_loop
if "!_BJ:~-1!"==" " set "_BJ=!_BJ:~0,-1!" & goto strip_loop
set BEST_JAVA=!_BJ!

if "%JVM_OPTIONS%"=="" set "JVM_OPTIONS=-Xms256m -Xmx1024m -Dawt.useSystemAAFontSettings=on -Dsun.java2d.dpiaware=true"

set "JAVAW=!BEST_JAVA:java.exe=javaw.exe!"
start "" "!JAVAW!" %JVM_OPTIONS% -Dkylin.sql.home="%APP_HOME%" -Duser.home="%USERPROFILE%" -cp "%APP_HOME%\lib\*" com.kylin.plsql.ui.KylinPlSqlApp
exit /b 0

:trim
set "_T=%~1"
:trim_loop
if "!_T:~-1!"==" " set "_T=!_T:~0,-1!" & goto trim_loop
set "JPATH=!_T!"
exit /b

:check
set "JAVA_EXE=%~1"
if "%JAVA_EXE%"=="" exit /b
if not exist "%JAVA_EXE%" exit /b

"%JAVA_EXE%" -version 2>"%TEMP%\kylin-jver.txt"
set "VER_STR="
for /f "tokens=3" %%v in ('findstr /C:"version" "%TEMP%\kylin-jver.txt"') do set "VER_STR=%%v"
del "%TEMP%\kylin-jver.txt" 2>nul
if "!VER_STR!"=="" exit /b
set "VER_STR=!VER_STR:"=!"
for /f "tokens=1 delims=." %%a in ("!VER_STR!") do set "MAJOR=%%a"
if "!MAJOR!"=="" exit /b
if !MAJOR! GTR !BEST_VER! ( set BEST_JAVA=!JAVA_EXE! & set BEST_VER=!MAJOR! )
exit /b
