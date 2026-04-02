$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..\\..")

mvn -q -pl perf -am package

java -Djmh.ignoreLock=true -jar .\\perf\\target\\my-spot-perf.jar $args
