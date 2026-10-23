#!/usr/bin/env bash
# 같은 부하에서 PG 상태만 바꿔 가며 잰다. 비교가 목적이므로 VU 와 시간은 고정한다.
#
#   docs/week5/run-fault-injection.sh
#   STAGES="100 1000" docs/week5/run-fault-injection.sh
#
# PG 상태 세 가지
#   healthy  정상
#   slow     docker pause   — 커넥션은 살아 있고 응답만 안 온다. read timeout 3초
#   down     docker stop    — 연결 거부. 즉시 실패
#
# slow 가 down 보다 아플 것으로 본다. 즉시 실패는 스레드를 잡지 않지만
# 타임아웃은 3초 동안 스레드와 DB 커넥션을 함께 묶기 때문이다.
# 이 스크립트는 그 예상을 확인하거나 뒤집기 위한 것이다.
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8099}"
MGMT_URL="${MGMT_URL:-http://localhost:8098}"
PG_CONTAINER="${PG_CONTAINER:-pg-simulator}"
PG_COMPOSE="${PG_COMPOSE:-docker/pg-compose.yml}"
STAGES="${STAGES:-100 1000 10000}"
MODES="${MODES:-healthy slow down}"
DURATION="${DURATION:-30s}"
CONTROL_RATE="${CONTROL_RATE:-5}"
OUT_DIR="${OUT_DIR:-$(mktemp -d)}"
IDS_FILE="${IDS_FILE:-$OUT_DIR/product-ids.json}"
SCRIPT="$(cd "$(dirname "$0")" && pwd)/k6-payment-ramp.js"

mkdir -p "$OUT_DIR"

if [ ! -f "$IDS_FILE" ]; then
  docker exec docker-mysql-1 sh -c 'mysql -uroot -proot -D loopers -N -e "
    select id from product where status=\"SELLING\" and deleted_at is null and stock >= 50 limit 20000
  " 2>/dev/null' \
    | python3 -c 'import sys,json; print(json.dumps([int(l) for l in sys.stdin if l.strip()]))' \
    > "$IDS_FILE"
fi

restore_pg() {
  docker unpause "$PG_CONTAINER" >/dev/null 2>&1
  docker start "$PG_CONTAINER" >/dev/null 2>&1
  for _ in $(seq 1 30); do
    if curl -sf -o /dev/null --max-time 2 "http://localhost:8082/actuator/health"; then return 0; fi
    sleep 1
  done
  echo "PG 가 돌아오지 않는다" >&2
  return 1
}
trap restore_pg EXIT

break_pg() {
  case "$1" in
    slow) docker pause "$PG_CONTAINER" >/dev/null ;;
    down) docker stop "$PG_CONTAINER" >/dev/null ;;
  esac
}

sample_server() {
  : > "$1"
  while :; do
    curl -s --max-time 2 "$MGMT_URL/actuator/prometheus" 2>/dev/null \
      | grep -E "^(tomcat_threads_busy_threads|tomcat_threads_current_threads|hikaricp_connections_active|hikaricp_connections_pending|hikaricp_connections_timeout_total)" \
      | awk -v t="$(date +%s)" '{print t" "$1" "$2}' >> "$1"
    sleep 1
  done
}

for vus in $STAGES; do
  for mode in $MODES; do
    restore_pg || exit 1
    sleep 3
    break_pg "$mode"

    echo
    echo "=============== VU $vus · PG $mode ($DURATION) ==============="
    sample_server "$OUT_DIR/server-$vus-$mode.txt" &
    sampler=$!

    VUS="$vus" DURATION="$DURATION" BASE_URL="$BASE_URL" CONTROL_RATE="$CONTROL_RATE" \
    IDS_FILE="$IDS_FILE" OUT="$OUT_DIR/stage-$vus-$mode.json" \
      k6 run --quiet --no-color "$SCRIPT" 2>&1 \
      | grep -vE "level=warning msg=\"Request Failed\"" | tail -6

    kill "$sampler" 2>/dev/null
    wait "$sampler" 2>/dev/null
    echo "→ $OUT_DIR/stage-$vus-$mode.json"
  done
done

restore_pg
echo
echo "결과 디렉터리: $OUT_DIR"
