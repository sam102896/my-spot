$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..\\..\\web")

if (!(Test-Path "node_modules")) {
  npm.cmd install --no-fund --no-audit
}

npm.cmd run dev

