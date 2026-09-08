# Log-Based Protocol Verification for Java Microservices

This MSc project contains a small Java microservices testbed and an offline protocol verifier.

The testbed creates and processes orders through inventory and payment services. During processing, the order service publishes lifecycle events to Kafka. Service logs are sent through Fluentd to Splunk. The verifier reads exported logs and checks whether each order followed the expected lifecycle.

## Services

| Service | Port | Purpose |
|---|---:|---|
| API Gateway | 8080 | Entry point for the service APIs |
| Order Service | 8081 | Creates orders, coordinates inventory and payment, and publishes lifecycle events |
| Inventory Service | 8082 | Stores products and reserves or releases stock |
| Payment Service | 8083 | Processes payments and simulates payment failures |
| Kafka | 9092 | Holds the `order-events` lifecycle-event topic |
| Fluentd | 24224 | Forwards structured container logs to Splunk |
| Splunk | 8000 | Stores and searches the forwarded logs |
| Splunk HEC | 8088 | Receives logs from Fluentd |
| Zookeeper | Internal | Supports the Kafka container |

## Prerequisites

- Docker Desktop running
- At least 4 GB of memory allocated to Docker
- Java 11 and Maven 3.8+ only if running Maven commands outside Docker

## Start the system

Open a terminal in the project root:

```bash
cd "C:\Users\Sumeet\Downloads\microservices-final"
docker compose up -d --build
```

Check that the containers have started:

```bash
docker compose ps
```

The application services, Kafka and Splunk should eventually show as `healthy`. The first start can take a few minutes because Docker may need to build images and initialise Splunk.

To follow logs from a service:

```bash
docker compose logs -f order-service
```

Press `Ctrl + C` to stop following the logs without stopping the containers.

## Splunk setup

Open [http://localhost:8000](http://localhost:8000) and sign in with:

```text
Username: admin
Password: Admin1234!
```

The current Fluentd configuration sends logs to Splunk's default `main` index.

The HEC token in these two files must match:

- `docker-compose.yml` — `SPLUNK_HEC_TOKEN`
- `fluentd/fluent.conf` — `hec_token`

After changing either token, recreate the relevant containers:

```bash
docker compose down
docker compose up -d --build
```

## Create test orders

### Successful order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "PROD-001",
    "quantity": 1,
    "totalAmount": 999.99,
    "customerEmail": "test@example.com"
  }'
```

A successful order should follow this lifecycle:

```text
ORDER_CREATED
→ INVENTORY_RESERVED
→ PAYMENT_PROCESSED
→ ORDER_COMPLETED
```

### Payment failure

Amounts above `5000` are deliberately declined by the simulated payment gateway.

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "PROD-001",
    "quantity": 1,
    "totalAmount": 9999.00,
    "customerEmail": "test@example.com"
  }'
```

This produces an `ORDER_FAILED` lifecycle event.

### Inventory failure

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "PROD-999",
    "quantity": 1,
    "totalAmount": 50.00,
    "customerEmail": "test@example.com"
  }'
```

This attempts to reserve a product that does not exist.

## Useful API requests

```bash
# List orders through the gateway
curl http://localhost:8080/api/orders

# List products through the gateway
curl http://localhost:8080/api/inventory/products

# Check the health of the order service
curl http://localhost:8081/actuator/health
```

## Splunk searches

Open **Search & Reporting** in Splunk and use the following queries.

### All structured service logs

```spl
index=main sourcetype=_json
```

### Lifecycle events for the verifier

```spl
index=main sourcetype=_json service=order-service "Published Kafka event:"
| rex field=message "eventType=(?<eventType>[A-Z_]+)\s+orderId=(?<orderId>\d+)"
| table _time orderId eventType service
| sort 0 orderId _time
```

Export this result as CSV when using it as verifier input.

### Events for one order

```spl
index=main sourcetype=_json service=order-service "Published Kafka event:"
| rex field=message "eventType=(?<eventType>[A-Z_]+)\s+orderId=(?<orderId>\d+)"
| search orderId=1
| table _time orderId eventType service
| sort 0 _time
```

### Payment-service warnings and errors

```spl
index=main sourcetype=_json service=payment-service (level=WARN OR level=ERROR)
| table _time level message
| sort 0 _time
```

### Count lifecycle events by type

```spl
index=main sourcetype=_json service=order-service "Published Kafka event:"
| rex field=message "eventType=(?<eventType>[A-Z_]+)\s+orderId=(?<orderId>\d+)"
| stats count by eventType
```

## Running the verifier

The verifier is located in:

```text
order-service/src/main/java/com/example/protocol/
```

It accepts CSV, JSON and raw log exports. It groups events by `orderId`, sorts each trace by timestamp, and checks it against the order lifecycle state machine.

Run it from the project root with a log-export path:

```bash
mvn -pl order-service exec:java ^
  -Dexec.mainClass=com.example.protocol.VerifyFromSplunk ^
  -Dexec.args="C:\path\to\export.csv"
```

For PowerShell or Git Bash, replace `^` with `\`, or place the command on one line.

The report shows:

- compliant orders;
- the observed event sequence;
- the first detected violation and its category;
- a low-confidence note if timestamps are missing, invalid or tied.

## Kafka lifecycle events

The order service is the publisher of lifecycle events on the `order-events` topic.

| Event | Meaning |
|---|---|
| `ORDER_CREATED` | A new order was saved |
| `INVENTORY_RESERVED` | Stock was reserved |
| `PAYMENT_PROCESSED` | Payment succeeded |
| `ORDER_COMPLETED` | The order completed successfully |
| `ORDER_FAILED` | The order failed during inventory or payment processing |

To inspect the topic from the running Kafka container:

```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --from-beginning
```

## Project structure

```text
microservices-final/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── fluentd/
│   ├── Dockerfile
│   └── fluent.conf
├── api-gateway/
├── order-service/
│   └── src/main/java/com/example/protocol/
│       └── Verifier, protocol model and log readers
├── inventory-service/
└── payment-service/
```

## Stop the system

```bash
# Stop containers and retain Docker volumes
docker compose down

# Stop containers and delete Docker volumes
docker compose down -v
```

`docker compose down -v` removes the stored Splunk data, so use it only when a clean environment is needed.