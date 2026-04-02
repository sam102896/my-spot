$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..\\..\\backend")
mvn -q spring-boot:run

