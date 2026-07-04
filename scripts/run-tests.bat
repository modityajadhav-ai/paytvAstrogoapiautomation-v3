@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if not defined VRGO_REFRESH_TOKEN (
  if not exist "secrets\vrgo-auth.local.properties" (
    if not exist "vrgo-token-cache.json" (
      echo [ERROR] No VRGO refresh token configured.
      echo   Jenkins/GitLab: set masked variable VRGO_REFRESH_TOKEN
      echo   Local dev: copy secrets\vrgo-auth.local.properties.example to secrets\vrgo-auth.local.properties
      exit /b 1
    )
  )
)

call mvnw.cmd clean test -Ptest %*
exit /b %ERRORLEVEL%
