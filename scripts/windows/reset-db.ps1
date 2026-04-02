$ErrorActionPreference = "Stop"
$dataDir = Join-Path $PSScriptRoot "..\\..\\backend\\data"

if (Test-Path $dataDir) {
  Remove-Item -Recurse -Force $dataDir
  Write-Host "Removed: $dataDir"
} else {
  Write-Host "No data dir: $dataDir"
}

