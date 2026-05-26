@echo off
REM Wrapper script to set JAVA_HOME and run Gradle build

setlocal enabledelayedexpansion

set "JAVA_HOME=C:\Users\Randy Cordero\java\jdk-17.0.10+7"

REM Verify Java exists
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: Java not found at %JAVA_HOME%
    exit /b 1
)

echo JAVA_HOME set to: %JAVA_HOME%
echo.

REM Run Gradle build
cd /d "%~dp0"
call .\gradlew.bat %*

