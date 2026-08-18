@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

echo Installing Playwright Chromium for VRGO browser auto-login...
call mvnw.cmd -q dependency:resolve
call mvnw.cmd -q exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

if errorlevel 1 (
  echo Playwright install failed. Ensure Maven can download com.microsoft.playwright:playwright
  exit /b 1
)

echo Done. Configure per-environment secrets, e.g. secrets\vrgo-auth.test.local.properties
echo   vrgo.auth.username / vrgo.auth.password for browser auto-recovery
exit /b 0
