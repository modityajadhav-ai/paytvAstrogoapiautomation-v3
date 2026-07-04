# Runs the full API suite and emails Extent + Excel reports.
# Configure SMTP and recipients via environment variables (see scripts/.env.example).
#
# Usage:
#   .\scripts\run-daily-tests-and-email.ps1
#   .\scripts\run-daily-tests-and-email.ps1 -Environment stage
#
# Register for 7:00 AM daily (run PowerShell as Administrator once):
#   .\scripts\register-daily-task.ps1

param(
    [ValidateSet('test', 'dev', 'stage', 'load', 'prod')]
    [string]$Environment = 'test'
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $ProjectRoot

# Optional local config file (not committed)
$EnvFile = Join-Path $ProjectRoot 'scripts\.env'
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') {
            [System.Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim(), 'Process')
        }
    }
}

if (-not $env:JAVA_HOME) {
    Write-Warning 'JAVA_HOME is not set. Point it to JDK 17 before running tests.'
}

Write-Host "Running API tests (env=$Environment)..." -ForegroundColor Cyan
& .\mvnw.cmd -B -ntp clean test "-P$Environment" "-Dallure.report.skip=true"
$TestExitCode = $LASTEXITCODE

$extentReport = Join-Path $ProjectRoot 'target\extent-reports\ExtentReport.html'
$excelDir = Join-Path $ProjectRoot 'target\excel-reports'
$excelReport = Get-ChildItem -Path $excelDir -Filter 'TestReport_*.xlsx' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

$smtpServer   = $env:SMTP_SERVER
$smtpPort     = if ($env:SMTP_PORT) { [int]$env:SMTP_PORT } else { 587 }
$smtpUser     = $env:SMTP_USERNAME
$smtpPassword = $env:SMTP_PASSWORD
$smtpFrom     = $env:SMTP_FROM
$recipients   = $env:REPORT_RECIPIENTS

if (-not $smtpServer -or -not $smtpFrom -or -not $recipients) {
    Write-Warning 'SMTP_SERVER, SMTP_FROM, or REPORT_RECIPIENTS not set — skipping email.'
    exit $TestExitCode
}

$status = if ($TestExitCode -eq 0) { 'PASSED' } else { 'FAILED' }
$date   = Get-Date -Format 'yyyy-MM-dd HH:mm'
$body   = @"
API Automation — Daily Report
Environment: $Environment
Machine: $env:COMPUTERNAME
Date: $date
Status: $status

See attached Extent HTML and Excel reports for details.
"@

$message = New-Object System.Net.Mail.MailMessage
$message.From = $smtpFrom
foreach ($addr in ($recipients -split '[,;]')) {
    $trimmed = $addr.Trim()
    if ($trimmed) { [void]$message.To.Add($trimmed) }
}
$message.Subject = "[API Tests] $status — $Environment — $date"
$message.Body = $body
$message.IsBodyHtml = $false

if (Test-Path $extentReport) {
    [void]$message.Attachments.Add((New-Object System.Net.Mail.Attachment($extentReport)))
}
if ($excelReport) {
    [void]$message.Attachments.Add((New-Object System.Net.Mail.Attachment($excelReport.FullName)))
}

$client = New-Object System.Net.Mail.SmtpClient($smtpServer, $smtpPort)
$client.EnableSsl = $true
if ($smtpUser -and $smtpPassword) {
    $client.Credentials = New-Object System.Net.NetworkCredential($smtpUser, $smtpPassword)
}

try {
    Write-Host 'Sending email report...' -ForegroundColor Cyan
    $client.Send($message)
    Write-Host 'Email sent.' -ForegroundColor Green
}
finally {
    $message.Dispose()
    $client.Dispose()
}

exit $TestExitCode
