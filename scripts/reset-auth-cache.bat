@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

set "ENV_PROFILE=test"
if not "%~1"=="" set "ENV_PROFILE=%~1"

if /I "%ENV_PROFILE%"=="test" set "CACHE_FILE=vrgo-token-cache-test.json"
if /I "%ENV_PROFILE%"=="dev" set "CACHE_FILE=vrgo-token-cache-dev.json"
if /I "%ENV_PROFILE%"=="load" set "CACHE_FILE=vrgo-token-cache-load.json"
if /I "%ENV_PROFILE%"=="stage" set "CACHE_FILE=vrgo-token-cache-stage.json"
if /I "%ENV_PROFILE%"=="stage2" set "CACHE_FILE=vrgo-token-cache-stage2.json"
if /I "%ENV_PROFILE%"=="prod" set "CACHE_FILE=vrgo-token-cache-prod.json"

if /I "%ENV_PROFILE%"=="test" set "GUEST_CACHE_FILE=vrgo-guest-token-cache-test.json"
if /I "%ENV_PROFILE%"=="dev" set "GUEST_CACHE_FILE=vrgo-guest-token-cache-dev.json"
if /I "%ENV_PROFILE%"=="load" set "GUEST_CACHE_FILE=vrgo-guest-token-cache-load.json"
if /I "%ENV_PROFILE%"=="stage" set "GUEST_CACHE_FILE=vrgo-guest-token-cache-stage.json"
if /I "%ENV_PROFILE%"=="stage2" set "GUEST_CACHE_FILE=vrgo-guest-token-cache-stage2.json"
if /I "%ENV_PROFILE%"=="prod" set "GUEST_CACHE_FILE=vrgo-guest-token-cache-prod.json"

if not defined CACHE_FILE set "CACHE_FILE=vrgo-token-cache-%ENV_PROFILE%.json"
if not defined GUEST_CACHE_FILE set "GUEST_CACHE_FILE=vrgo-guest-token-cache-%ENV_PROFILE%.json"

if exist "%CACHE_FILE%" (
  del /q "%CACHE_FILE%"
  echo Deleted %CACHE_FILE%
) else (
  echo No %CACHE_FILE% to delete
)

if exist "%GUEST_CACHE_FILE%" (
  del /q "%GUEST_CACHE_FILE%"
  echo Deleted %GUEST_CACHE_FILE%
) else (
  echo No %GUEST_CACHE_FILE% to delete
)

if exist "vrgo-token-cache.json" (
  del /q "vrgo-token-cache.json"
  echo Deleted legacy vrgo-token-cache.json
)

echo.
echo Next steps for profile %ENV_PROFILE%:
echo   1. Close ALL browser windows for that stack
echo   2. Incognito login - copy refresh_token from /v1/auth/token Response
echo   3. Paste into secrets\vrgo-auth.%ENV_PROFILE%.local.properties
echo      (or legacy secrets\vrgo-auth.local.properties)
echo   4. Run: scripts\refresh-token.bat -Denv=%ENV_PROFILE%
echo.

exit /b 0
