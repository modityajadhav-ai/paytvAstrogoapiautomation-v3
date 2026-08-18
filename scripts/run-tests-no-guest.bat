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

rem Logged-in suite only — skips VRSearchProxy / LearnAction guest tests and guest browser recovery.
rem Quote -D properties so PowerShell does not treat -Dsurefire as a switch.
call mvnw.cmd clean test -P%ENV_PROFILE% "-Dsurefire.suiteXmlFiles=src/test/resources/testng-no-guest.xml" "-Dvrgo.guest.tests.enabled=false" %2 %3 %4 %5 %6 %7 %8 %9
exit /b %ERRORLEVEL%
