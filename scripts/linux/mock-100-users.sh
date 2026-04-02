#!/usr/bin/env bash
set -euo pipefail

USER_COUNT="${1:-100}"
ROUNDS="${2:-200}"
BASE_URL="${SPOT_BASE_URL:-http://localhost:3001}"
ADMIN_KEY="${SPOT_DEV_ADMIN_KEY:-dev-admin-key}"

assert_non_empty() {
  local v="$1"
  local msg="$2"
  if [ -z "$v" ]; then
    echo "ASSERT_FAILED: $msg" >&2
    exit 1
  fi
}

json_get() {
  local key="$1"
  python3 - <<PY
import json,sys
raw=sys.stdin.read().strip()
obj=json.loads(raw) if raw else None
if isinstance(obj, dict):
  v=obj.get("$key")
  print("" if v is None else v)
else:
  print("")
PY
}

curl_json() {
  local method="$1"
  local url="$2"
  shift 2
  curl -sS -X "$method" -H "Accept: application/json" "$@" "$url"
}

wait_health() {
  local deadline=$((SECONDS + 60))
  while [ $SECONDS -lt $deadline ]; do
    if curl -sS "$BASE_URL/actuator/health" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status",""))' 2>/dev/null | grep -q "^UP$"; then
      return 0
    fi
    sleep 0.5
  done
  echo "Backend not healthy: $BASE_URL" >&2
  exit 1
}

safe_post() {
  local url="$1"
  local device="$2"
  local data="$3"
  local extra_header="${4:-}"
  local tries="${5:-2}"

  local i
  for ((i = 0; i <= tries; i++)); do
    if [ -n "$extra_header" ]; then
      if out="$(curl_json POST "$url" -H "X-Device-Id: $device" -H "$extra_header" -H "Content-Type: application/json" --data-binary @- <<<"$data")"; then
        echo "$out"
        return 0
      fi
    else
      if out="$(curl_json POST "$url" -H "X-Device-Id: $device" -H "Content-Type: application/json" --data-binary @- <<<"$data")"; then
        echo "$out"
        return 0
      fi
    fi
    sleep 0.4
  done
  return 1
}

echo "== Mock Online Users =="
echo "BaseUrl: $BASE_URL"
echo "Users: $USER_COUNT"
echo "Rounds: $ROUNDS"

wait_health
echo "[OK] Backend UP"

password="Passw0rd!"

emails=()
tokens=()
devices=()

btc_indexes=()
eth_indexes=()

for ((i = 1; i <= USER_COUNT; i++)); do
  email
  email=$(printf "u%04d@example.com" "$i")
  device
  device=$(printf "mock-%04d" "$i")

  curl_json POST "$BASE_URL/api/public/auth/register" \
    -H "X-Device-Id: $device" \
    -H "Content-Type: application/json" \
    --data-binary @- <<<"{\"email\":\"$email\",\"password\":\"$password\"}" >/dev/null 2>&1 || true

  otp_json="$(safe_post "$BASE_URL/api/public/auth/login/otp" "$device" "{\"identifier\":\"$email\",\"password\":\"$password\"}" "" 3)"
  otp="$(printf "%s" "$otp_json" | json_get otp)"
  assert_non_empty "$otp" "otp empty for $email"

  login_json="$(safe_post "$BASE_URL/api/public/auth/login" "$device" "{\"identifier\":\"$email\",\"otp\":\"$otp\"}" "" 3)"
  token="$(printf "%s" "$login_json" | json_get token)"
  assert_non_empty "$token" "token empty for $email"

  kyc_json="$(curl_json POST "$BASE_URL/api/account/kyc" \
    -H "Authorization: Bearer $token" \
    -H "X-Device-Id: $device" \
    -H "Content-Type: application/json" \
    --data-binary @- <<<"{\"name\":\"User$(printf "%04d" "$i")\"}")"
  _="$(printf "%s" "$kyc_json" | json_get kycStatus)"

  emails+=("$email")
  tokens+=("$token")
  devices+=("$device")

  curl_json POST "$BASE_URL/api/public/admin/deposits/simulate" \
    -H "X-Admin-Key: $ADMIN_KEY" \
    -H "X-Device-Id: mock-admin" \
    -H "Content-Type: application/json" \
    --data-binary @- <<<"{\"identifier\":\"$email\",\"asset\":\"USDT\",\"amount\":\"500\"}" >/dev/null

  if ((i % 2 == 0)); then
    curl_json POST "$BASE_URL/api/public/admin/deposits/simulate" \
      -H "X-Admin-Key: $ADMIN_KEY" \
      -H "X-Device-Id: mock-admin" \
      -H "Content-Type: application/json" \
      --data-binary @- <<<"{\"identifier\":\"$email\",\"asset\":\"BTC\",\"amount\":\"0.01\"}" >/dev/null
    btc_indexes+=("$((i - 1))")
  else
    curl_json POST "$BASE_URL/api/public/admin/deposits/simulate" \
      -H "X-Admin-Key: $ADMIN_KEY" \
      -H "X-Device-Id: mock-admin" \
      -H "Content-Type: application/json" \
      --data-binary @- <<<"{\"identifier\":\"$email\",\"asset\":\"ETH\",\"amount\":\"0.2\"}" >/dev/null
    eth_indexes+=("$((i - 1))")
  fi

  if ((i % 10 == 0)); then
    echo "[INIT] $i/$USER_COUNT users ready"
  fi
done

echo "[INIT] deposits submitted, waiting confirm..."
sleep 8

place_limit() {
  local idx="$1"
  local pair="$2"
  local side="$3"
  local price="$4"
  local qty="$5"

  local token="${tokens[$idx]}"
  local device="${devices[$idx]}"
  local idem
  idem="$(python3 -c 'import uuid; print(uuid.uuid4())')"

  curl_json POST "$BASE_URL/api/trade/order" \
    -H "Authorization: Bearer $token" \
    -H "X-Device-Id: $device" \
    -H "X-Idempotency-Key: $idem" \
    -H "Content-Type: application/json" \
    --data-binary @- <<<"{\"pair\":\"$pair\",\"side\":\"$side\",\"type\":\"LIMIT\",\"price\":\"$price\",\"qty\":\"$qty\"}"
}

btc_base=65000
eth_base=2000

for ((r = 1; r <= ROUNDS; r++)); do
  use_btc=$((RANDOM % 2))

  if ((use_btc == 0)); then
    if [ "${#btc_indexes[@]}" -eq 0 ]; then
      continue
    fi
    seller_idx="${btc_indexes[$((RANDOM % ${#btc_indexes[@]}))]}"
    buyer_idx=$((RANDOM % USER_COUNT))
    pair="BTCUSDT"
    price=$((btc_base + (RANDOM % 41) - 20))
    qty="0.0002"
  else
    if [ "${#eth_indexes[@]}" -eq 0 ]; then
      continue
    fi
    seller_idx="${eth_indexes[$((RANDOM % ${#eth_indexes[@]}))]}"
    buyer_idx=$((RANDOM % USER_COUNT))
    pair="ETHUSDT"
    price=$((eth_base + (RANDOM % 21) - 10))
    qty="0.01"
  fi

  if [ "${emails[$seller_idx]}" = "${emails[$buyer_idx]}" ]; then
    continue
  fi

  sell_json="$(place_limit "$seller_idx" "$pair" "SELL" "$price" "$qty")"
  buy_json="$(place_limit "$buyer_idx" "$pair" "BUY" "$price" "$qty")"

  sell_id="$(printf "%s" "$sell_json" | json_get id)"
  buy_id="$(printf "%s" "$buy_json" | json_get id)"
  assert_non_empty "$sell_id" "sell order failed round $r"
  assert_non_empty "$buy_id" "buy order failed round $r"

  if ((r % 10 == 0)); then
    echo "[ROUND] $r/$ROUNDS lastPair=$pair price=$price qty=$qty"
  fi
done

echo "== MOCK DONE =="

