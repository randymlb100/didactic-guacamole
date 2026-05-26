@echo off
REM Build script for LotteryNet Android App
REM Sets up Java environment and runs Gradle build

setlocal enabledelayedexpansion

echo [*] LotteryNet Android App - Build Script
echo.

REM Check if Java is in PATH
where java >nul 2>&1
if not errorlevel 1 (
    echo [OK] Java found in PATH
    java -version
    goto :BUILD
)

REM Check if Java is in default locations
if exist "C:\Users\Randy Cordero\java\jdk-17\bin\java.exe" (
    set "JAVA_HOME=C:\Users\Randy Cordero\java\jdk-17"
    echo [OK] Found Java in user directory: !JAVA_HOME!
    goto :BUILD
)

if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    echo [OK] Found Java: !JAVA_HOME!
    goto :BUILD
)

echo [ERROR] Java not found and download still in progress...
echo.
echo Please wait for the Java download to complete, or:
echo 1. Visit: https://github.com/adoptium/temurin17-binaries/releases
echo 2. Download: OpenJDK17U-jdk_x64_windows_hotspot_17.0.10_7.zip
echo 3. Extract to: C:\Users\Randy Cordero\java\jdk-17
echo 4. Run this script again
echo.
exit /b 1

:BUILD
echo.
echo [*] Starting Gradle build...
echo.

cd /d "%~dp0"
call gradlew.bat clean build

if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. Check output above.
    exit /b 1
) else (
    echo.
    echo [SUCCESS] Build completed!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    exit /b 0
)
