$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "restart-backend.ps1")
& (Join-Path $PSScriptRoot "restart-frontend.ps1")

Write-Host "Travel Memory frontend and backend restart commands have been sent."
