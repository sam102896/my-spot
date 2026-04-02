#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${SPOT_BASE_URL:-http://localhost:3001}"
ADMIN_KEY="${SPOT_DEV_ADMIN_KEY:-dev-admin-key}"

DEVICE_ALICE="smoke-alice"
DEVICE_BOB="smoke-bob"

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
  if v is None:
    print("")
  else:
    print(v)
else:
  print("")
PY
}

json_len() {
  python3 - <<PY
import json,sys
raw=sys.stdin.read().strip()
obj=json.loads(raw) if raw else None
if isinstance(obj, list):
  print(len(obj))
else:
  print(0)
PY
}

curl_json() {
  local method="$1"
  local url="$2"
  shift 2
  curl -sS -X "$method" -H "Accept: application/json" "$@" "$url"
}

wait_health() {
  local deadline=$((SECONDS + 45))
  while [ $SECONDS -lt $deadline ]; do
    if curl -sS "$BASE_URL/actuator/health" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status",""))' 2>/dev/null | grep -q "^UP$"; then
      return 0
    fi
    sleep 0.5
  done
  echo "Backend not healthy: $BASE_URL" >&2
  exit 1
}

login_two_step() {
  local identifier="$1"
  local password="$2"
  local device="$3"

  local otp_json
  otp_json="$(curl_json POST "$BASE_URL/api/public/auth/login/otp" \
    -H "X-Device-Id: $device" \
    -H "Content-Type: application/json" \
    --data-binary @- <<EOF
{"identifier":"$identifier","password":"$password"}
EOF
  )"
  local otp
  otp="$(printf "%s" "$otp_json" | json_get otp)"
  assert_non_empty "$otp" "otp should not be empty for $identifier"

  local login_json
  login_json="$(curl_json POST "$BASE_URL/api/public/auth/login" \
    -H "X-Device-Id: $device" \
    -H "Content-Type: application/json" \
    --data-binary @- <<EOF
{"identifier":"$identifier","otp":"$otp"}
EOF
  )"
  local token
  token="$(printf "%s" "$login_json" | json_get token)"
  assert_non_empty "$token" "token should not be empty for $identifier"
  echo "$token"
}

echo "== Smoke Test =="
echo "BaseUrl: $BASE_URL"

wait_health
echo "[OK] /actuator/health"

pairs_json="$(curl_json GET "$BASE_URL/api/public/market/pairs")"
pairs_len="$(printf "%s" "$pairs_json" | json_len)"
if [ "$pairs_len" -lt 1 ]; then
  echo "ASSERT_FAILED: pairs should have >= 1" >&2
  exit 1
fi
echo "[OK] market pairs"

alice_token="$(login_two_step "alice@example.com" "Passw0rd!" "$DEVICE_ALICE")"
echo "[OK] alice login"

bob_token="$(login_two_step "bob@example.com" "Passw0rd!" "$DEVICE_BOB")"
echo "[OK] bob login"

me_json="$(curl_json GET "$BASE_URL/api/account/me" -H "Authorization: Bearer $alice_token" -H "X-Device-Id: $DEVICE_ALICE")"
me_id="$(printf "%s" "$me_json" | json_get id)"
assert_non_empty "$me_id" "me.id should exist"
echo "[OK] /api/account/me"

addr_json="$(curl_json GET "$BASE_URL/api/account/deposit/address?asset=USDT" -H "Authorization: Bearer $alice_token" -H "X-Device-Id: $DEVICE_ALICE")"
addr="$(printf "%s" "$addr_json" | json_get address)"
assert_non_empty "$addr" "deposit address should exist"
echo "[OK] deposit address"

sim_json="$(curl_json POST "$BASE_URL/api/public/admin/deposits/simulate" \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -H "X-Device-Id: smoke-admin" \
  -H "Content-Type: application/json" \
  --data-binary @- <<EOF
{"identifier":"alice@example.com","asset":"USDT","amount":"50"}
EOF
)"
sim_id="$(printf "%s" "$sim_json" | json_get id)"
assert_non_empty "$sim_id" "simulate deposit should return id"
echo "[OK] simulate deposit created: $sim_id"

sleep 7

wallets_json="$(curl_json GET "$BASE_URL/api/account/wallets" -H "Authorization: Bearer $alice_token" -H "X-Device-Id: $DEVICE_ALICE")"
wallets_len="$(printf "%s" "$wallets_json" | json_len)"
if [ "$wallets_len" -lt 1 ]; then
  echo "ASSERT_FAILED: wallets should have >= 1" >&2
  exit 1
fi
echo "[OK] wallets fetched"

PAIR="ETHUSDT"

bob_sell_json="$(curl_json POST "$BASE_URL/api/trade/order" \
  -H "Authorization: Bearer $bob_token" \
  -H "X-Device-Id: $DEVICE_BOB" \
  -H "X-Idempotency-Key: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
  -H "Content-Type: application/json" \
  --data-binary @- <<EOF
{"pair":"$PAIR","side":"SELL","type":"LIMIT","price":"2000","qty":"0.01"}
EOF
)"
bob_sell_id="$(printf "%s" "$bob_sell_json" | json_get id)"
assert_non_empty "$bob_sell_id" "bob sell order should return id"
echo "[OK] bob limit sell: $bob_sell_id"

alice_buy_json="$(curl_json POST "$BASE_URL/api/trade/order" \
  -H "Authorization: Bearer $alice_token" \
  -H "X-Device-Id: $DEVICE_ALICE" \
  -H "X-Idempotency-Key: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
  -H "Content-Type: application/json" \
  --data-binary @- <<EOF
{"pair":"$PAIR","side":"BUY","type":"MARKET","qty":"0.01"}
EOF
)"
alice_buy_id="$(printf "%s" "$alice_buy_json" | json_get id)"
assert_non_empty "$alice_buy_id" "alice market buy should return id"
echo "[OK] alice market buy: $alice_buy_id"

sleep 1

trades_json="$(curl_json GET "$BASE_URL/api/public/market/trades?pair=$PAIR&limit=5")"
trades_len="$(printf "%s" "$trades_json" | json_len)"
if [ "$trades_len" -lt 1 ]; then
  echo "ASSERT_FAILED: recent trades should have >= 1" >&2
  exit 1
fi
echo "[OK] recent trades"

withdraw_json="$(curl_json POST "$BASE_URL/api/account/withdraw" \
  -H "Authorization: Bearer $alice_token" \
  -H "X-Device-Id: $DEVICE_ALICE" \
  -H "Content-Type: application/json" \
  --data-binary @- <<EOF
{"asset":"USDT","address":"ADDR-USDT-EXTERNAL","amount":"1","fundPassword":"123456"}
EOF
)"
withdraw_id="$(printf "%s" "$withdraw_json" | json_get id)"
assert_non_empty "$withdraw_id" "withdraw should return id"
echo "[OK] withdraw submitted: $withdraw_id"

sleep 12

withdrawals_json="$(curl_json GET "$BASE_URL/api/account/withdrawals?limit=20" -H "Authorization: Bearer $alice_token" -H "X-Device-Id: $DEVICE_ALICE")"
echo "[OK] withdrawals fetched"

echo "== ALL PASSED =="

