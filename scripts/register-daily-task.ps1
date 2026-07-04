# Registers a Windows Task Scheduler job to run tests every day at 7:00 AM (local time).
# Run once in an elevated PowerShell window:
#   Set-ExecutionPolicy -Scope Process Bypass
#   .\scripts\register-daily-task.ps1

param(
    [string]$TaskName = 'API-Automation-Daily-7AM',
    [string]$Time = '07:00'
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ScriptPath = Join-Path $ProjectRoot 'scripts\run-daily-tests-and-email.ps1'

if (-not (Test-Path $ScriptPath)) {
    throw "Script not found: $ScriptPath"
}

$action = New-ScheduledTaskAction `
    -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`"" `
    -WorkingDirectory $ProjectRoot

$trigger = New-ScheduledTaskTrigger -Daily -At $Time

$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Hours 3)

$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Force | Out-Null

Write-Host "Scheduled task '$TaskName' registered — runs daily at $Time (local time)." -ForegroundColor Green
Write-Host "Configure SMTP in scripts\.env (copy from scripts\.env.example) before the first run."
