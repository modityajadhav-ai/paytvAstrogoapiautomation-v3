@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if /I "%~1"=="headed" set VRGO_BROWSER_HEADED=true
if /I "%VRGO_BROWSER_HEADED%"=="true" (
  echo Verifying VRGO browser auto-recovery ^(VISIBLE browser^)...
  set "HEADED_MVN=-Dvrgo.auth.browser.headed=true"
) else (
  echo Verifying VRGO browser auto-recovery ^(headless^)...
  echo Tip: scripts\verify-browser-recovery.bat headed
  echo   OR in CMD: set VRGO_BROWSER_HEADED=true
  echo   OR in PowerShell: $env:VRGO_BROWSER_HEADED="true"
  set "HEADED_MVN="
)
echo.

echo Main class: com.automation.api.auth.VrgoBrowserRecoveryVerifier
echo.

call mvnw.cmd -q test-compile exec:java@browser-recovery-verify %HEADED_MVN%

exit /b %ERRORLEVEL%
