# Running the microservices on Docker with Splunk logging

This stack builds all four Spring Boot services, runs them on Docker, and ships
their logs to Splunk through Fluentd (Docker `fluentd` log driver → Fluentd →
Splunk HEC).

```
service (JSON stdout, "docker" profile)
   └─ docker fluentd log driver :24224
        └─ fluentd  (fluent-plugin-splunk-hec)
             └─ Splunk HEC :8088  →  index=main
```

## Prerequisites
- Docker Desktop / Docker Engine with the Compose plugin (`docker compose`).
- ~6 GB free RAM (Splunk + Kafka are the heavy parts).

## 1. Build and start everything
```bash
cd microservices-final
docker compose build
docker compose up -d
```
First run pulls Splunk/Kafka images and builds four Maven images, so allow a few
minutes. Watch startup with:
```bash
docker compose ps
docker compose logs -f splunk        # wait for "Ansible playbook complete"
```

## 2. Verify
```bash
chmod +x verify.sh
./verify.sh
```
This checks container status, the four `/actuator/health` endpoints, Splunk HEC,
and the Splunk Web UI, then generates a little traffic.

Manual checks:
```bash
curl http://localhost:8080/actuator/health   # api-gateway
curl http://localhost:8081/actuator/health   # order-service
curl http://localhost:8082/actuator/health   # inventory-service
curl http://localhost:8083/actuator/health   # payment-service
```

## 3. Check logs in Splunk
Open http://localhost:8000 — login `admin` / `Admin1234!`.

Search & Reporting → run:
```spl
index=main sourcetype=_json | stats count by source
```
You should see one row per container (`order-service`, `inventory-service`,
`payment-service`, `api-gateway`). More queries:
```spl
index=main sourcetype=_json service=order-service | table _time, level, logger, message
index=main sourcetype=_json level=ERROR
index=main sourcetype=_json | timechart count by service
```

## How logging is wired
- Each service runs with `--spring.profiles.active=docker`; `logback-spring.xml`
  emits **JSON** (LogstashEncoder) in that profile, plain text otherwise.
- `docker-compose.yml` attaches the `fluentd` log driver (`x-logging` anchor) to
  every service with `tag={{.Name}}` and `fluentd-async=true`.
- `fluentd/fluent.conf` parses the JSON, tags the source, and forwards to Splunk
  HEC (`index main`, `sourcetype _json`).

## Optional: dedicated "microservices" index
`index main` works out of the box. For a separate index:
```bash
docker exec -it splunk /opt/splunk/bin/splunk add index microservices \
  -auth admin:Admin1234!
```
Then set `index microservices` in `fluentd/fluent.conf` and
`docker compose restart fluentd`.

## Teardown
```bash
docker compose down          # keep volumes
docker compose down -v       # also wipe Splunk data + buffers
```

## Notes
- The runtime image installs `curl` so the compose healthchecks work.
- HEC token is `your-hec-token-here` (set in both the `splunk` service and
  `fluentd/fluent.conf`); change both together if you rotate it.
- Services use in-memory H2, so no external database is required.
