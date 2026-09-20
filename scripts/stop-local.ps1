#Requires -Version 7.2
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $RootDir ".env"
$ComposeDir = Join-Path $RootDir "infrastructure" "compose"

Push-Location $ComposeDir
try {
    docker compose --env-file $EnvFile down
} finally {
    Pop-Location
}
