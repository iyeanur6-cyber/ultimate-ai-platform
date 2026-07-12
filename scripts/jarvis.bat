@echo off
REM Ultimate AI Platform Launcher
REM Configures the terminal and launches the UltimateAI engine

REM Set UTF-8 code page for proper CLI rendering
chcp 65001 >nul 2>&1

set ULTIMATE_DIR=%~dp0
REM আপনার নতুন বিল্ড করা জার ফাইলের নাম অনুযায়ী এখানে চেঞ্জ করে নিন
set JAR=%ULTIMATE_DIR%ultimate-server-1.0.0.jar

if not exist "%JAR%" (
    echo.
    echo [ERROR] UltimateAI binary not found: ultimate-server-1.0.0.jar
    echo Please ensure the jar is in the same folder as this launcher.
    pause
    exit /b 1
)

echo Initializing UltimateAI Engine...

REM Launching engine with terminal optimization
java ^
  -Djline.terminal=windows ^
  -Djline.windows.conin.force=true ^
  -jar "%JAR%" %*
