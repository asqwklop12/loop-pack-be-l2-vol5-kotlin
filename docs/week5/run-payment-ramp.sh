#!/usr/bin/env bash
# 결제 부하를 10 → 100 → 1000 → ... 로 올리면서 단계마다 결과를 남긴다.
#
#   docs/week5/run-payment-ramp.sh                      기본 단계 (10 100 1000)
#   STAGES="10 100 1000 10000" docs/week5/run-payment-ramp.sh
#
# 단계마다 k6 요약을 $OUT_DIR/stage-<vus>.json 으로 떨어뜨린다.
#
# 주의 — VU 하나가 수 MB 를 쓴다. 10000 VU 부터는 부하 장치가 먼저 한계에
# 닿으므로, 그 단계의 숫자는 대상이 아니라 k6 를 재는 숫자일 수 있다.
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8099}"
MGMT_URL="${MGMT_URL:-http://localhost:8098}"
STAGES="${STAGES:-10 100 1000}"
DURATION="${DURATION:-30s}"
CONTROL_RATE="${CONTROL_RATE:-5}"
CARD_NO="${CARD_NO:-1234-5678-9814-1451}"
OUT_DIR="${OUT_DIR:-$(mktemp -d)}"
IDS_FILE="${IDS_FILE:-$OUT_DIR/product-ids.json}"
SCRIPT="$(cd "$(dirname "$0")" && pwd)/k6-payment-ramp.js"

mkdir -p "$OUT_DIR"

if [ ! -f "$IDS_FILE" ]; then
  echo "상품 id 를 뽑는다 → $IDS_FILE"
  docker exec docker-mysql-1 sh -c 'mysql -uroot -proot -D loopers -N -e "
    select id from product where status=\"SELLING\" and deleted_at is null and stock >= 50 limit 20000
  " 2>/dev/null' \
    | python3 -c 'import sys,json; print(json.dumps([int(l) for l in sys.stdin if l.strip()]))' \
    > "$IDS_FILE"
fi
echo "상품 $(python3 -c "import json;print(len(json.load(open('$IDS_FILE'))))")개"

if ! curl -sf -o /dev/null "$BASE_URL/api/v1/products?page=0&size=1"; then
  echo "대상이 응답하지 않는다: $BASE_URL" >&2
  exit 1
fi

# 응답 시간만 보면 왜 느려졌는지 모른다. 서버 쪽 포화 지표를 같이 찍는다.
#
# tomcat_threads_* 는 기본으로 노출되지 않는다. 대상 앱을
#   --server.tomcat.mbeanregistry.enabled=true
# 로 띄워야 한다. 안 켜면 지표가 "0" 이 아니라 아예 없고, 그걸 0 으로 읽으면
# "스레드는 한가했다" 는 거짓 결론이 나온다.
sample_server() {
  local out="$1"
  : > "$out"
  while :; do
    curl -s --max-time 2 "$MGMT_URL/actuator/prometheus" 2>/dev/null \
      | grep -E "^(tomcat_threads_busy|tomcat_threads_current|tomcat_threads_config_max|hikaricp_connections_active|hikaricp_connections_pending|hikaricp_connections_timeout_total)" \
      | awk -v t="$(date +%s)" '{print t" "$1" "$2}' >> "$out"
    sleep 1
  done
}

for vus in $STAGES; do
  echo
  echo "=============== VU $vus ($DURATION) ==============="
  sample_server "$OUT_DIR/server-$vus.txt" &
  sampler=$!

  VUS="$vus" DURATION="$DURATION" BASE_URL="$BASE_URL" CONTROL_RATE="$CONTROL_RATE" \
  CARD_NO="$CARD_NO" IDS_FILE="$IDS_FILE" OUT="$OUT_DIR/stage-$vus.json" \
    k6 run --quiet --no-color "$SCRIPT" 2>&1 | grep -vE "level=warning msg=\"Request Failed\"" | tail -30
  code=$?

  kill "$sampler" 2>/dev/null
  wait "$sampler" 2>/dev/null
  echo "단계 종료코드 $code — $OUT_DIR/stage-$vus.json"
  sleep 5
done

echo
echo "결과 디렉터리: $OUT_DIR"
