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
          echo   Local: secrets\vrgo-auth.%ENV_PROFILE%.local.properties
          exit /b 1
        )
      )
    )
  )
)

rem Ordered suite — preserves @Test(priority) / dependsOnMethods in ContinueWatchScenarios.
call mvnw.cmd test -P%ENV_PROFILE% "-Dsurefire.suiteXmlFiles=src/test/resources/testng-continue-watch-scenarios.xml" %2 %3 %4 %5 %6 %7 %8 %9
exit /b %ERRORLEVEL%
