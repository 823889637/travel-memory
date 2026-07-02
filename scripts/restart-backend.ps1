$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$serverDir = Join-Path $root "travel-memory-server"
$logsDir = Join-Path $root "logs"
$javaHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$localMaven = Join-Path $root ".tools\apache-maven-3.9.16\bin\mvn.cmd"

New-Item -ItemType Directory -Force $logsDir | Out-Null

$backendProcesses = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq "Listen" } |
    Select-Object -ExpandProperty OwningProcess -Unique

foreach ($processId in $backendProcesses) {
    if ($processId -and $processId -ne 0) {
        Stop-Process -Id $processId -Force
    }
}

if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
}

if (Test-Path $localMaven) {
    $mavenCommand = $localMaven
} else {
    $mavenCommand = "mvn"
}

Start-Process `
    -FilePath $mavenCommand `
    -ArgumentList "spring-boot:run" `
    -WorkingDirectory $serverDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logsDir "backend.log") `
    -RedirectStandardError (Join-Path $logsDir "backend-error.log")

Write-Host "Backend restarted on http://localhost:8080"
Write-Host "Logs: $logsDir\backend.log"
