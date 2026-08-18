@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

set "ENV_PROFILE=test"
if not "%~1"=="" set "ENV_PROFILE=%~1"

set "SECRETS_FILE=secrets\vrgo-auth.%ENV_PROFILE%.local.properties"
set "CACHE_FILE=vrgo-token-cache-%ENV_PROFILE%.json"

if not defined VRGO_REFRESH_TOKEN (
  if not exist "%SECRETS_FILE%" (
    if not exist "secrets\vrgo-auth.local.properties" (
      if not exist "%CACHE_FILE%" (
        if not exist "vrgo-token-cache.json" (
          echo [ERROR] No VRGO refresh token configured for profile %ENV_PROFILE%.
          exit /b 1
        )
      )
    )
  )
)

rem Ordered suite only — do not use -Dtest= (alphabetical method order breaks DELETE-first flow).
call mvnw.cmd clean test -P%ENV_PROFILE% "-Dsurefire.suiteXmlFiles=src/test/resources/testng-boxset-binge.xml" %2 %3 %4 %5 %6 %7 %8 %9
exit /b %ERRORLEVEL%
