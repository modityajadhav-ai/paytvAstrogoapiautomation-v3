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
          echo   CI: set VRGO_REFRESH_TOKEN_%ENV_PROFILE% or VRGO_REFRESH_TOKEN
          echo   Local: copy secrets\vrgo-auth.%ENV_PROFILE%.local.properties.example
          echo          to secrets\vrgo-auth.%ENV_PROFILE%.local.properties
          exit /b 1
        )
      )
    )
  )
)

rem Ordered suite — do not use -Dtest= (alphabetical method order breaks CW/WA flows).
rem Use "test" not "clean test" — Windows locks target\excel-reports\*.xlsx if Excel has them open.
call mvnw.cmd test -P%ENV_PROFILE% "-Dsurefire.suiteXmlFiles=src/test/resources/testng-cw-wa-fav.xml" %2 %3 %4 %5 %6 %7 %8 %9
exit /b %ERRORLEVEL%
