#Requires -Version 7.2
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $RootDir ".env"
$ComposeDir = Join-Path $RootDir "infrastructure" "compose"

if (-not (Test-Path $EnvFile)) {
    Write-Error "Copy .env.example to .env before starting local infrastructure."
}

Push-Location $ComposeDir
try {
    docker compose --env-file $EnvFile up -d
    Write-Host "Local infrastructure started. Inspect health with:"
    Write-Host "  docker compose ps"
    Write-Host "  docker compose logs -f"
} finally {
    Pop-Location
}
