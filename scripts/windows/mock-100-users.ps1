param(
  [int]$UserCount = 100,
  [int]$Rounds = 200,
  [string]$BaseUrl = "",
  [string]$AdminKey = ""
)

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
    [string]$Url,
    [int]$TimeoutSeconds = 45
  )
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    try {
      $res = Curl-Json -Method GET -Url "$Url/actuator/health"
      if ($res -and $res.status -eq "UP") {
        return
      }
    } catch {
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Backend not healthy within ${TimeoutSeconds}s: $Url"
}

function Safe-Post {
  param(
    [string]$Url,
    [hashtable]$Headers,
    [hashtable]$Body,
    [int]$Retry = 1,
    [int]$RetryDelayMs = 400
  )
  $bodyJson = ($Body | ConvertTo-Json -Compress)
  for ($i = 0; $i -le $Retry; $i++) {
    try {
      return Curl-Json -Method POST -Url $Url -Headers $Headers -BodyJson $bodyJson
    } catch {
      if ($i -ge $Retry) {
        throw
      }
      Start-Sleep -Milliseconds $RetryDelayMs
    }
  }
  return $null
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  $BaseUrl = $env:SPOT_BASE_URL
}
if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  $BaseUrl = "http://localhost:3001"
}

if ([string]::IsNullOrWhiteSpace($AdminKey)) {
  $AdminKey = $env:SPOT_DEV_ADMIN_KEY
}
if ([string]::IsNullOrWhiteSpace($AdminKey)) {
  $AdminKey = "dev-admin-key"
}

Write-Host "== Mock Online Users =="
Write-Host "BaseUrl: $BaseUrl"
Write-Host "Users: $UserCount"
Write-Host "Rounds: $Rounds"

Wait-Health -Url $BaseUrl
Write-Host "[OK] Backend UP"

$password = "Passw0rd!"
$tokens = New-Object System.Collections.Generic.List[object]
$btcUsers = New-Object System.Collections.Generic.List[object]
$ethUsers = New-Object System.Collections.Generic.List[object]

for ($i = 1; $i -le $UserCount; $i++) {
  $email = ("u{0:D4}@example.com" -f $i)
  $deviceId = ("mock-{0:D4}" -f $i)

  try {
    Safe-Post -Url "$BaseUrl/api/public/auth/register" -Headers @{ "X-Device-Id" = $deviceId } -Body @{ email=$email; password=$password } -Retry 0 | Out-Null
  } catch {
  }

  $otpRes = Safe-Post -Url "$BaseUrl/api/public/auth/login/otp" -Headers @{ "X-Device-Id" = $deviceId } -Body @{ identifier=$email; password=$password } -Retry 2
  $otp = $otpRes.otp
  Assert-True (-not [string]::IsNullOrWhiteSpace($otp)) "otp empty for $email"

  $loginRes = Safe-Post -Url "$BaseUrl/api/public/auth/login" -Headers @{ "X-Device-Id" = $deviceId } -Body @{ identifier=$email; otp=$otp } -Retry 2
  $token = $loginRes.token
  Assert-True (-not [string]::IsNullOrWhiteSpace($token)) "token empty for $email"

  $authHeaders = @{ Authorization = "Bearer $token"; "X-Device-Id" = $deviceId }
  Safe-Post -Url "$BaseUrl/api/account/kyc" -Headers $authHeaders -Body @{ name=("User{0:D4}" -f $i) } -Retry 1 | Out-Null

  $tokens.Add([pscustomobject]@{ email=$email; token=$token; deviceId=$deviceId })

  $adminHeaders = @{ "X-Admin-Key" = $AdminKey; "X-Device-Id" = "mock-admin" }
  Safe-Post -Url "$BaseUrl/api/public/admin/deposits/simulate" -Headers $adminHeaders -Body @{ identifier=$email; asset="USDT"; amount="500" } -Retry 1 | Out-Null

  if (($i % 2) -eq 0) {
    Safe-Post -Url "$BaseUrl/api/public/admin/deposits/simulate" -Headers $adminHeaders -Body @{ identifier=$email; asset="BTC"; amount="0.01" } -Retry 1 | Out-Null
    $btcUsers.Add($tokens[$tokens.Count - 1])
  } else {
    Safe-Post -Url "$BaseUrl/api/public/admin/deposits/simulate" -Headers $adminHeaders -Body @{ identifier=$email; asset="ETH"; amount="0.2" } -Retry 1 | Out-Null
    $ethUsers.Add($tokens[$tokens.Count - 1])
  }

  if (($i % 10) -eq 0) {
    Write-Host ("[INIT] {0}/{1} users ready" -f $i, $UserCount)
  }
}

Write-Host "[INIT] deposits submitted, waiting confirm..."
Start-Sleep -Seconds 8

function Place-Limit {
  param(
    [object]$User,
    [string]$Pair,
    [string]$Side,
    [string]$Price,
    [string]$Qty
  )
  $headers = @{
    Authorization = ("Bearer {0}" -f $User.token)
    "X-Device-Id" = $User.deviceId
    "X-Idempotency-Key" = [guid]::NewGuid().ToString()
  }
  return Safe-Post -Url "$BaseUrl/api/trade/order" -Headers $headers -Body @{ pair=$Pair; side=$Side; type="LIMIT"; price=$Price; qty=$Qty } -Retry 1
}

function Pick-Random {
  param([System.Collections.Generic.List[object]]$List)
  $idx = Get-Random -Minimum 0 -Maximum $List.Count
  return $List[$idx]
}

$btcBasePrice = 65000
$ethBasePrice = 2000

for ($r = 1; $r -le $Rounds; $r++) {
  $useBtc = ((Get-Random -Minimum 0 -Maximum 2) -eq 0)

  if ($useBtc) {
    $seller = Pick-Random -List $btcUsers
    $buyer = Pick-Random -List $tokens
    $price = ($btcBasePrice + (Get-Random -Minimum -20 -Maximum 21)).ToString()
    $qty = "0.0002"
    $pair = "BTCUSDT"
  } else {
    $seller = Pick-Random -List $ethUsers
    $buyer = Pick-Random -List $tokens
    $price = ($ethBasePrice + (Get-Random -Minimum -10 -Maximum 11)).ToString()
    $qty = "0.01"
    $pair = "ETHUSDT"
  }

  if ($seller.email -eq $buyer.email) {
    continue
  }

  $sell = Place-Limit -User $seller -Pair $pair -Side "SELL" -Price $price -Qty $qty
  $buy = Place-Limit -User $buyer -Pair $pair -Side "BUY" -Price $price -Qty $qty

  Assert-True ($sell.id) "sell order failed round $r"
  Assert-True ($buy.id) "buy order failed round $r"

  if (($r % 10) -eq 0) {
    Write-Host ("[ROUND] {0}/{1} lastPair={2} price={3} qty={4}" -f $r, $Rounds, $pair, $price, $qty)
  }
}

$t1 = Curl-Json -Method GET -Url "$BaseUrl/api/public/market/trades?pair=BTCUSDT&limit=5"
$t2 = Curl-Json -Method GET -Url "$BaseUrl/api/public/market/trades?pair=ETHUSDT&limit=5"
Write-Host ("[DONE] Recent BTC trades: {0}" -f ($t1 | Measure-Object).Count)
Write-Host ("[DONE] Recent ETH trades: {0}" -f ($t2 | Measure-Object).Count)
Write-Host "== MOCK DONE =="

