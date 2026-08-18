@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

set "ENV_PROFILE=test"
if not "%~1"=="" set "ENV_PROFILE=%~1"

set "HEADED_ARG="
if /I "%~2"=="headed" set "HEADED_ARG=-Dvrgo.auth.browser.headed=true"
if /I "%~1"=="headed" (
  set "ENV_PROFILE=test"
  set "HEADED_ARG=-Dvrgo.auth.browser.headed=true"
)

if /I "%VRGO_BROWSER_HEADED%"=="true" set "HEADED_ARG=-Dvrgo.auth.browser.headed=true"

echo Verifying VRGO guest browser recovery for profile %ENV_PROFILE%...
echo Main class: com.automation.api.auth.VrgoGuestBrowserRecoveryVerifier
echo.

call mvnw.cmd -q test-compile exec:java@guest-recovery-verify -Denv=%ENV_PROFILE% %HEADED_ARG%

exit /b %ERRORLEVEL%
