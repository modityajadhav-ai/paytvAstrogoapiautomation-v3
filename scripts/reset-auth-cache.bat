@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if exist "vrgo-token-cache.json" (
  del /q "vrgo-token-cache.json"
  echo Deleted vrgo-token-cache.json
) else (
  echo No vrgo-token-cache.json to delete
)

echo.
echo Next steps:
echo   1. Close ALL browser windows for web.vrgo.test
echo   2. Incognito login - copy refresh_token from /v1/auth/token Response
echo   3. Paste into secrets\vrgo-auth.local.properties
echo   4. Run scripts\refresh-token.bat to verify
echo.

exit /b 0
