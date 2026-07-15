@echo off
setlocal
set "APP_HOME=%~dp0"
set "JAVA17=D:\dev-tools\jdk17"
set "INPUT=%APP_HOME%logo\kylin_512x512.png"
set "OUTPUT=%APP_HOME%logo\kylin.ico"

if not exist "%JAVA17%\bin\javac.exe" (
    echo [ERROR] JDK 17 not found at %JAVA17%
    echo Set JAVA17=path\to\jdk17 at the top of this script.
    pause
    exit /b 1
)

echo Compiling IcoGenerator...
"%JAVA17%\bin\javac" -d "%TEMP%" "%APP_HOME%IcoGenerator.java" 2>&1
if errorlevel 1 (
    echo [ERROR] Compilation failed
    pause
    exit /b 1
)

echo Generating %OUTPUT%...
"%JAVA17%\bin\java" -cp "%TEMP%" IcoGenerator "%INPUT%" "%OUTPUT%"
if errorlevel 1 (
    echo [ERROR] Generation failed
    pause
    exit /b 1
)

del "%TEMP%\IcoGenerator.class" 2>nul
echo Done.
