package com.example.protocol;

import java.time.LocalDateTime;

// One event from a log, independent of the format it was read from.
public class ProtocolEvent {

    private String orderId;
    private String eventType;
    private LocalDateTime timestamp;
    private String service;

    public ProtocolEvent(String orderId, String eventType, LocalDateTime timestamp, String service) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.service = service;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getService() {
        return service;
    }

    @Override
    public String toString() {
        return eventType + " (orderId=" + orderId + ", time=" + timestamp + ")";
    }
}
