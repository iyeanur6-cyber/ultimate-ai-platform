@echo off
REM Jarvis AI Platform Launcher for Windows
REM This script configures the terminal correctly

REM Set UTF-8 code page
chcp 65001 >nul 2>&1

set JARVIS_DIR=%~dp0
set JAR=%JARVIS_DIR%jarvis-server-0.1.0.jar

if not exist "%JAR%" (
    echo.
    echo Jarvis JAR not found.
    echo Download from:
    echo https://github.com/sujankim/jarvis-ai-platform/releases
    echo.
    echo Place jarvis-server-0.1.0.jar in the same
    echo folder as this script.
    pause
    exit /b 1
)

echo Starting Jarvis AI Platform...

REM -Djline.terminal=windows
REM Tells JLine to use Windows Console API
REM This fixes backspace, arrow keys, etc.
java ^
  -Djline.terminal=windows ^
  -Djline.windows.conin.force=true ^
  -jar "%JAR%" %*