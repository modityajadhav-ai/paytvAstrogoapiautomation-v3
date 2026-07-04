@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

call mvnw.cmd -q test-compile exec:java -Dexec.mainClass=com.automation.api.auth.VrgoTokenManager %*

exit /b %ERRORLEVEL%
