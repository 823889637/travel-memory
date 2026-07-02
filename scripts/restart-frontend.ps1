$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$webDir = Join-Path $root "travel-memory-web"
$logsDir = Join-Path $root "logs"

New-Item -ItemType Directory -Force $logsDir | Out-Null

$frontendProcesses = Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq "Listen" } |
    Select-Object -ExpandProperty OwningProcess -Unique

foreach ($processId in $frontendProcesses) {
    if ($processId -and $processId -ne 0) {
        Stop-Process -Id $processId -Force
    }
}

Start-Process `
    -FilePath "npm" `
    -ArgumentList "run dev" `
    -WorkingDirectory $webDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logsDir "frontend.log") `
    -RedirectStandardError (Join-Path $logsDir "frontend-error.log")

Write-Host "Frontend restarted on http://localhost:5173"
Write-Host "Logs: $logsDir\frontend.log"
