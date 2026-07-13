[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-DotEnv {
    param([string]$Path)

    $result = @{}
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line -match '^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $value = $matches[2].Trim()
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $result[$matches[1]] = $value
        }
    }
    return $result
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $root '.env'
$serverDirectory = Join-Path $root 'travel-memory-server'

if (-not (Test-Path $envFile)) {
    throw "Missing $envFile. Create it from .env.example and set the local database credentials."
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw 'Maven is required to start the local backend.'
}
if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 8080 is already in use. Stop the existing backend first, then run this script again.'
}

$previous = @{}
$localEnv = Read-DotEnv $envFile

try {
    foreach ($entry in $localEnv.GetEnumerator()) {
        $previous[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }

    Push-Location $serverDirectory
    try {
        & mvn spring-boot:run
    } finally {
        Pop-Location
    }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
