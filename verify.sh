#!/usr/bin/env bash
# End-to-end verification for the Dockerized microservices + Splunk logging.
# Portable to macOS's built-in bash 3.2 (no associative arrays).
# Usage: ./verify.sh
set -uo pipefail

pass() { printf "  \033[32mok\033[0m %s\n" "$1"; }
fail() { printf "  \033[31mXX\033[0m %s\n" "$1"; FAILED=1; }
hdr()  { printf "\n\033[1m%s\033[0m\n" "$1"; }
FAILED=0

hdr "1. Config validation"
docker compose config >/dev/null 2>&1 && pass "docker-compose.yml is valid" || fail "docker-compose.yml invalid"

hdr "2. Container status"
for c in zookeeper kafka splunk fluentd order-service inventory-service payment-service api-gateway; do
  state=$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null | tr -d '\n')
  [ -z "$state" ] && state="missing"
  if [ "$state" = "running" ]; then pass "$c running"; else fail "$c is '$state'"; fi
done

hdr "3. Service health endpoints"
# "name port" pairs, space separated
for pair in "api-gateway 8080" "order-service 8081" "inventory-service 8082" "payment-service 8083"; do
  svc=${pair% *}; port=${pair#* }
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${port}/actuator/health" 2>/dev/null || echo 000)
  if [ "$code" = "200" ]; then pass "$svc /actuator/health -> 200"; else fail "$svc /actuator/health -> $code"; fi
done

hdr "4. Generate traffic (so there are logs to check)"
curl -s "http://localhost:8082/inventory/products"      >/dev/null 2>&1   # direct to service
curl -s "http://localhost:8080/api/inventory/products"  >/dev/null 2>&1   # via gateway
curl -s "http://localhost:8081/actuator/health"         >/dev/null 2>&1
pass "sent sample requests"

hdr "5. Splunk HEC reachable"
hec=$(curl -sk -o /dev/null -w '%{http_code}' "https://localhost:8088/services/collector/health" 2>/dev/null || echo 000)
if [ "$hec" = "200" ]; then pass "Splunk HEC healthy (8088)"; else fail "Splunk HEC -> $hec"; fi

hdr "6. Splunk Web reachable"
web=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8000" 2>/dev/null || echo 000)
if [ "$web" = "200" ] || [ "$web" = "303" ]; then pass "Splunk Web up (8000)"; else fail "Splunk Web -> $web"; fi

hdr "Result"
if [ "$FAILED" = "0" ]; then
  printf "\033[32mAll checks passed.\033[0m Open http://localhost:8000 (admin / Admin1234!) and run:\n"
  printf "    index=main sourcetype=_json | stats count by source\n"
else
  printf "\033[31mSome checks failed.\033[0m Inspect with: docker compose logs <service>\n"
  exit 1
fi
