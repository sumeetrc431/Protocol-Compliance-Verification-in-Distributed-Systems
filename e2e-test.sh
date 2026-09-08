#!/usr/bin/env bash
# Full end-to-end test: drives a real order through the whole system and
# confirms it flows gateway -> order-service -> inventory -> payment -> Kafka
# -> Fluentd -> Splunk. Portable to macOS bash 3.2.
#
# Usage: ./e2e-test.sh
set -uo pipefail

GW=http://localhost:8080          # api-gateway
SPLUNK_USER=admin
SPLUNK_PASS=Admin1234!
FAILED=0
pass() { printf "  \033[32mok\033[0m %s\n" "$1"; }
fail() { printf "  \033[31mXX\033[0m %s\n" "$1"; FAILED=1; }
hdr()  { printf "\n\033[1m%s\033[0m\n" "$1"; }

hdr "1. All services healthy (through the gateway's own port + direct)"
for pair in "api-gateway 8080" "order-service 8081" "inventory-service 8082" "payment-service 8083"; do
  svc=${pair% *}; port=${pair#* }
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${port}/actuator/health")
  [ "$code" = "200" ] && pass "$svc healthy" || fail "$svc health -> $code"
done

hdr "2. Inventory seed data visible via gateway"
prods=$(curl -s "$GW/api/inventory/products")
echo "$prods" | grep -q "PROD-001" && pass "products seeded (PROD-001 present)" || fail "seed products missing: $prods"

hdr "3. Create an order via the gateway (exercises inventory + payment + Kafka)"
resp=$(curl -s -w '\n%{http_code}' -X POST "$GW/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","quantity":1,"totalAmount":999.99,"customerEmail":"test@example.com"}')
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
if [ "$code" = "201" ] || [ "$code" = "200" ]; then pass "order created (HTTP $code)"; else fail "create order -> $code: $body"; fi
echo "    response: $body"
ORDER_ID=$(echo "$body" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')

hdr "4. Read the order back via the gateway"
if [ -n "${ORDER_ID:-}" ]; then
  got=$(curl -s -o /dev/null -w '%{http_code}' "$GW/api/orders/$ORDER_ID")
  [ "$got" = "200" ] && pass "GET /api/orders/$ORDER_ID -> 200" || fail "read order -> $got"
else
  fail "no order id parsed from create response"
fi

hdr "5. Kafka received the order event"
if docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 \
      --topic order-events --from-beginning --timeout-ms 8000 2>/dev/null | grep -q "orderId"; then
  pass "order-events topic has messages"
else
  fail "no messages on order-events topic (check order-service logs)"
fi

hdr "6. Logs reached Splunk (via REST search)"
# Give Fluentd a moment to flush its buffer to Splunk HEC.
sleep 8
search='search index=main sourcetype=_json | stats count by source'
result=$(curl -sk -u "$SPLUNK_USER:$SPLUNK_PASS" \
  https://localhost:8089/services/search/jobs/export \
  --data-urlencode "search=$search" \
  -d earliest_time=-15m -d output_mode=csv 2>/dev/null)
if echo "$result" | grep -q "order-service"; then
  pass "order-service logs found in Splunk (index=main)"
  echo "$result" | sed 's/^/    /'
else
  fail "no service logs found in Splunk yet"
  echo "    (raw: $(echo "$result" | head -3))"
fi

hdr "Result"
if [ "$FAILED" = "0" ]; then
  printf "\033[32mEND-TO-END PASSED.\033[0m Explore in Splunk UI (http://localhost:8000):\n"
  printf "    index=main sourcetype=_json | stats count by source\n"
  printf "    index=main sourcetype=_json \"Published Kafka event\"\n"
else
  printf "\033[31mSome end-to-end checks failed.\033[0m Inspect: docker compose logs <service>\n"
  exit 1
fi
