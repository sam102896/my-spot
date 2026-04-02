$ErrorActionPreference = "Stop"

$root = Join-Path $PSScriptRoot "..\\.."
$backend = Join-Path $PSScriptRoot "start-backend.ps1"
$web = Join-Path $PSScriptRoot "start-web.ps1"

Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", $backend) -WorkingDirectory $root
Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", $web) -WorkingDirectory $root

Write-Host "Backend: http://localhost:3001"
Write-Host "Web: http://localhost:5173"

