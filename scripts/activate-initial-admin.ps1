[CmdletBinding()]
param(
    [switch]$Local,
    [string]$JarPath = (Join-Path $PSScriptRoot '..\travel-memory-server\target\app.jar'),
    [string]$DatasourceUrl = ${env:SPRING_DATASOURCE_URL},
    [string]$DatasourceUsername = ${env:SPRING_DATASOURCE_USERNAME}
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ConvertTo-PlainText {
    param([System.Security.SecureString]$SecureValue)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

$username = (Read-Host 'Administrator username').Trim()
$password = Read-Host 'Administrator password' -AsSecureString
$confirmation = Read-Host 'Confirm password' -AsSecureString
$plainPassword = $null
$plainConfirmation = $null
$plainDatabasePassword = $null
$databasePassword = $null
$payload = $null
$previousDatabasePassword = $env:SPRING_DATASOURCE_PASSWORD

try {
    if ([string]::IsNullOrWhiteSpace($username)) {
        throw 'Username is required.'
    }

    $plainPassword = ConvertTo-PlainText $password
    $plainConfirmation = ConvertTo-PlainText $confirmation
    if (-not [string]::Equals($plainPassword, $plainConfirmation, [System.StringComparison]::Ordinal)) {
        throw 'Passwords do not match.'
    }

    $payload = "$username`n$plainPassword`n"
    if ($Local) {
        if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
            throw 'Java 17 is required for local activation.'
        }
        if (-not (Test-Path $JarPath)) {
            throw "Backend jar not found at $JarPath. Run mvn package -DskipTests in travel-memory-server first."
        }

        if ([string]::IsNullOrWhiteSpace($DatasourceUsername)) {
            $DatasourceUsername = 'travel_user'
        }
        if ([string]::IsNullOrWhiteSpace($DatasourceUrl)) {
            $DatasourceUrl = 'jdbc:mysql://localhost:3306/travel_memory?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
        }
        if ([string]::IsNullOrWhiteSpace($previousDatabasePassword)) {
            $databasePassword = Read-Host 'Local database password' -AsSecureString
            $plainDatabasePassword = ConvertTo-PlainText $databasePassword
            $env:SPRING_DATASOURCE_PASSWORD = $plainDatabasePassword
        }

        $payload | & java -jar $JarPath `
            --spring.main.web-application-type=none `
            --app.bootstrap-admin.enabled=true `
            --spring.datasource.url=$DatasourceUrl `
            --spring.datasource.username=$DatasourceUsername
    } else {
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
            throw 'Docker Desktop with Docker Compose is required. Use -Local to connect directly to local MySQL.'
        }
        & docker compose version | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Docker Compose v2 is required. Start Docker Desktop and try again.'
        }
        $payload | & docker compose exec -T backend java -jar /app/app.jar `
            --spring.main.web-application-type=none `
            --app.bootstrap-admin.enabled=true
    }

    if ($LASTEXITCODE -ne 0) {
        throw 'Initial administrator activation failed. Check the backend container logs and database migration state.'
    }
} finally {
    $payload = $null
    $plainPassword = $null
    $plainConfirmation = $null
    $plainDatabasePassword = $null
    if ($Local) {
        if ($null -eq $previousDatabasePassword) {
            Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
        } else {
            $env:SPRING_DATASOURCE_PASSWORD = $previousDatabasePassword
        }
    }
    if ($null -ne $password) { $password.Clear() }
    if ($null -ne $confirmation) { $confirmation.Clear() }
    if ($null -ne $databasePassword) { $databasePassword.Clear() }
}
