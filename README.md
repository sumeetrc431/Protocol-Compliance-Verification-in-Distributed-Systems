A verifier that checks whether distributed transactions followed their intended protocol, using only the logs a system already produces.

In a microservice architecture, a single business operation spans several services in an agreed sequence - reserve stock, take payment, complete the order. That sequence is rarely written down in a form anything can check, so services can drift from it as they change. The result is subtle: an order marked complete before it was paid for, with no error raised and the evidence scattered across several services' logs.

This project models the sequence as a finite state machine and checks recorded behaviour against it offline. Each transaction is reconstructed from exported logs, walked through the machine, and reported as compliant or as a specific kind of violation — a skipped step, a duplicate event, an event after the transaction had already finished, and so on. The verifier reads CSV, JSON and plain-text logs, and requires no changes to the running system.

Includes a four-service testbed (API gateway, order, inventory, payment) with Kafka event publication and a Fluentd-to-Splunk logging pipeline, containerised with Docker Compose.
