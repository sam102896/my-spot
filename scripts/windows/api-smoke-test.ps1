$ErrorActionPreference = "Stop"

function Assert-True {
  param(
    $Condition,
    [string]$Message
  )
  $ok = $false
  if ($Condition -is [bool]) {
    $ok = $Condition
  } elseif ($null -eq $Condition) {
    $ok = $false
  } elseif ($Condition -is [System.Array]) {
    $ok = ($Condition.Length -gt 0)
  } else {
    $ok = $true
  }
  if (-not $ok) {
    throw "ASSERT_FAILED: $Message"
  }
}

function Curl-Json {
  param(
    [Parameter(Mandatory = $true)][ValidateSet("GET","POST")][string]$Method,
    [Parameter(Mandatory = $true)][string]$Url,
    [hashtable]$Headers = @{},
    [string]$BodyJson = $null
  )

  $args = @(
    "-sS",
    "-X", $Method,
    $Url,
    "-H", "Accept: application/json"
  )

  foreach ($k in $Headers.Keys) {
    $args += @("-H", ("{0}: {1}" -f $k, $Headers[$k]))
  }

  $raw = $null
  if (-not [string]::IsNullOrWhiteSpace($BodyJson)) {
    $args += @("-H", "Content-Type: application/json", "--data-binary", "@-")
    $raw = ($BodyJson | & curl.exe @args)
  } else {
    $raw = (& curl.exe @args)
  }
  if ($LASTEXITCODE -ne 0) {
    throw "curl failed ($LASTEXITCODE): $Method $Url"
  }
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return $null
  }
  try {
    return ($raw | ConvertFrom-Json)
  } catch {
    throw "Invalid JSON response from ${Url}: $raw"
  }
}

function Wait-Health {
  param(
    [string]$BaseUrl,
    [int]$TimeoutSeconds = 30
  )
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    try {
      $res = Curl-Json -Method GET -Url "$BaseUrl/actuator/health"
      if ($res -and $res.status -eq "UP") {
        return
      }
    } catch {
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Backend not healthy within ${TimeoutSeconds}s: $BaseUrl"
}

$base = $env:SPOT_BASE_URL
if ([string]::IsNullOrWhiteSpace($base)) {
  $base = "http://localhost:3001"
}

$devAdminKey = $env:SPOT_DEV_ADMIN_KEY
if ([string]::IsNullOrWhiteSpace($devAdminKey)) {
  $devAdminKey = "dev-admin-key"
}

$deviceAlice = "smoke-alice"
$deviceBob = "smoke-bob"

Write-Host "== Smoke Test =="
Write-Host "BaseUrl: $base"

Wait-Health -BaseUrl $base -TimeoutSeconds 45
Write-Host "[OK] /actuator/health"

$pairs = Curl-Json -Method GET -Url "$base/api/public/market/pairs"
Assert-True ($null -ne $pairs) "pairs should not be null"
Assert-True ($pairs.Count -ge 1) "pairs should have >=1"
Write-Host "[OK] market pairs"

function Login-TwoStep {
  param(
    [string]$Identifier,
    [string]$Password,
    [string]$DeviceId
  )
  $otp = (Curl-Json -Method POST -Url "$base/api/public/auth/login/otp" -Headers @{ "X-Device-Id" = $DeviceId } -BodyJson (@{ identifier=$Identifier; password=$Password } | ConvertTo-Json -Compress)).otp
  Assert-True (-not [string]::IsNullOrWhiteSpace($otp)) "otp should not be empty for $Identifier"
  $token = (Curl-Json -Method POST -Url "$base/api/public/auth/login" -Headers @{ "X-Device-Id" = $DeviceId } -BodyJson (@{ identifier=$Identifier; otp=$otp } | ConvertTo-Json -Compress)).token
  Assert-True (-not [string]::IsNullOrWhiteSpace($token)) "token should not be empty for $Identifier"
  return $token
}

$aliceToken = Login-TwoStep -Identifier "alice@example.com" -Password "Passw0rd!" -DeviceId $deviceAlice
Write-Host "[OK] alice login"

$bobToken = Login-TwoStep -Identifier "bob@example.com" -Password "Passw0rd!" -DeviceId $deviceBob
Write-Host "[OK] bob login"

$aliceMe = Curl-Json -Method GET -Url "$base/api/account/me" -Headers @{ Authorization = "Bearer $aliceToken"; "X-Device-Id" = $deviceAlice }
Assert-True ($aliceMe.id) "me.id should exist"
Write-Host "[OK] /api/account/me"

$addr = Curl-Json -Method GET -Url "$base/api/account/deposit/address?asset=USDT" -Headers @{ Authorization = "Bearer $aliceToken"; "X-Device-Id" = $deviceAlice }
Assert-True ($addr.address) "deposit address should exist"
Write-Host "[OK] deposit address"

$sim = Curl-Json -Method POST -Url "$base/api/public/admin/deposits/simulate" -Headers @{ "X-Admin-Key" = $devAdminKey; "X-Device-Id" = "smoke-admin" } -BodyJson (@{ identifier="alice@example.com"; asset="USDT"; amount="50" } | ConvertTo-Json -Compress)
Assert-True ($sim.id) "simulate deposit should return id"
Write-Host "[OK] simulate deposit created: $($sim.id)"

Start-Sleep -Seconds 7

$wallets1 = Curl-Json -Method GET -Url "$base/api/account/wallets" -Headers @{ Authorization = "Bearer $aliceToken"; "X-Device-Id" = $deviceAlice }
Assert-True (($wallets1 | Measure-Object).Count -ge 1) "wallets should have >=1"
Write-Host "[OK] wallets fetched"

$pairSymbol = "ETHUSDT"

$bobSell = Curl-Json -Method POST -Url "$base/api/trade/order" -Headers @{
  Authorization = "Bearer $bobToken"
  "X-Device-Id" = $deviceBob
  "X-Idempotency-Key" = [guid]::NewGuid().ToString()
} -BodyJson (@{ pair=$pairSymbol; side="SELL"; type="LIMIT"; price="2000"; qty="0.01" } | ConvertTo-Json -Compress)
Assert-True ($bobSell.id) "bob sell order should return id"
Write-Host "[OK] bob limit sell: $($bobSell.id) status=$($bobSell.status)"

$aliceBuy = Curl-Json -Method POST -Url "$base/api/trade/order" -Headers @{
  Authorization = "Bearer $aliceToken"
  "X-Device-Id" = $deviceAlice
  "X-Idempotency-Key" = [guid]::NewGuid().ToString()
} -BodyJson (@{ pair=$pairSymbol; side="BUY"; type="MARKET"; qty="0.01" } | ConvertTo-Json -Compress)
Assert-True ($aliceBuy.id) "alice market buy should return id"
Write-Host "[OK] alice market buy: $($aliceBuy.id) status=$($aliceBuy.status)"

Start-Sleep -Seconds 1

$trades = Curl-Json -Method GET -Url "$base/api/public/market/trades?pair=$pairSymbol&limit=5"
Assert-True (($trades | Measure-Object).Count -ge 1) "recent trades should have >=1"
Write-Host "[OK] recent trades"

$bobOpen = Curl-Json -Method GET -Url "$base/api/trade/open-orders?limit=50" -Headers @{ Authorization = "Bearer $bobToken"; "X-Device-Id" = $deviceBob }
foreach ($o in $bobOpen) {
  if ($o.id -eq $bobSell.id) {
    Curl-Json -Method POST -Url "$base/api/trade/order/$($o.id)/cancel" -Headers @{ Authorization = "Bearer $bobToken"; "X-Device-Id" = $deviceBob } | Out-Null
    Write-Host "[OK] bob canceled remaining order: $($o.id)"
  }
}

$withdraw = Curl-Json -Method POST -Url "$base/api/account/withdraw" -Headers @{ Authorization = "Bearer $aliceToken"; "X-Device-Id" = $deviceAlice } -BodyJson (@{ asset="USDT"; address="ADDR-USDT-EXTERNAL"; amount="1"; fundPassword="123456" } | ConvertTo-Json -Compress)
Assert-True ($withdraw.id) "withdraw should return id"
Write-Host "[OK] withdraw submitted: $($withdraw.id) status=$($withdraw.status)"

Start-Sleep -Seconds 12

$withdrawals = Curl-Json -Method GET -Url "$base/api/account/withdrawals?limit=20" -Headers @{ Authorization = "Bearer $aliceToken"; "X-Device-Id" = $deviceAlice }
$w = $withdrawals | Where-Object { $_.id -eq $withdraw.id } | Select-Object -First 1
Assert-True ($w -ne $null) "withdraw record should exist"
Assert-True (($w.status -eq "DONE") -or ($w.status -eq "PROCESSING") -or ($w.status -eq "PENDING")) "withdraw status should be valid"
Write-Host "[OK] withdraw status: $($w.status)"

Write-Host "== ALL PASSED =="

