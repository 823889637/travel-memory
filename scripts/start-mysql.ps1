$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$mysqlIni = Join-Path $root ".mysql\my.ini"
$logsDir = Join-Path $root "logs"
$logStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$mysqlLog = Join-Path $logsDir "mysql.log"
$mysqlErrorLog = Join-Path $logsDir "mysql-error.log"
$mysqlRunLog = Join-Path $logsDir "mysql-$logStamp.log"
$mysqlRunErrorLog = Join-Path $logsDir "mysql-error-$logStamp.log"

New-Item -ItemType Directory -Force $logsDir | Out-Null

if (-not (Test-Path $mysqlIni)) {
    throw "MySQL config not found: $mysqlIni"
}

$existingMysql = Get-NetTCPConnection -LocalPort 3306 -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq "Listen" } |
    Select-Object -First 1

if ($existingMysql) {
    Write-Host "MySQL is already running on 127.0.0.1:3306"
    Write-Host "PID: $($existingMysql.OwningProcess)"
    exit 0
}

$existingMysqldProcesses = Get-Process -Name "mysqld" -ErrorAction SilentlyContinue
if ($existingMysqldProcesses) {
    Write-Host "Found mysqld process(es), but port 3306 is not listening."
    Write-Host "They may be holding the project data files. Stop them first, then run this script again."
    Write-Host "PID(s): $($existingMysqldProcesses.Id -join ', ')"
    exit 1
}

$mysqldCommand = Get-Command mysqld -ErrorAction SilentlyContinue
if ($mysqldCommand) {
    $mysqldPath = $mysqldCommand.Source
} else {
    $knownPaths = @(
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe"
    )
    $mysqldPath = $knownPaths | Where-Object { Test-Path $_ } | Select-Object -First 1
}

if (-not $mysqldPath) {
    throw "mysqld.exe not found. Please install MySQL or add mysqld to PATH."
}

"Starting MySQL with config: $mysqlIni" | Out-File -FilePath $mysqlRunLog -Encoding utf8

$processInfo = New-Object System.Diagnostics.ProcessStartInfo
$processInfo.FileName = $mysqldPath
$processInfo.Arguments = "--defaults-file=`"$mysqlIni`" --log-error=`"$mysqlRunErrorLog`""
$processInfo.WorkingDirectory = $root
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true

$mysqlProcess = New-Object System.Diagnostics.Process
$mysqlProcess.StartInfo = $processInfo
$started = $mysqlProcess.Start()

if (-not $started) {
    throw "Failed to start MySQL process."
}

Start-Sleep -Seconds 3

$startedMysql = Get-NetTCPConnection -LocalPort 3306 -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq "Listen" } |
    Select-Object -First 1

if ($startedMysql) {
    Write-Host "MySQL started on 127.0.0.1:3306"
    Write-Host "PID: $($startedMysql.OwningProcess)"
    Write-Host "Logs: $mysqlRunLog"
} else {
    Write-Host "MySQL start command was sent, but port 3306 is not listening yet."
    Write-Host "Check logs:"
    Write-Host $mysqlRunLog
    Write-Host $mysqlRunErrorLog
}
