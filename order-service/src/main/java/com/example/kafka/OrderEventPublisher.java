package com.example.kafka;

import com.example.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@Profile("docker")
public class OrderEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String ORDER_EVENTS_TOPIC = "order-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void publishOrderCreated(Order order) {
        publishEvent("ORDER_CREATED", order);
    }

    @Override
    public void publishInventoryReserved(Order order) { publishEvent("INVENTORY_RESERVED", order); }

    @Override
    public void publishPaymentProcessed(Order order) { publishEvent("PAYMENT_PROCESSED", order); }

    @Override
    public void publishOrderCompleted(Order order) { publishEvent("ORDER_COMPLETED", order); }

    @Override
    public void publishOrderFailed(Order order, String reason) {
        Map<String, Object> event = buildBaseEvent("ORDER_FAILED", order);
        event.put("failureReason", reason);
        sendEvent(String.valueOf(order.getId()), event);
    }

    private void publishEvent(String eventType, Order order) {
        Map<String, Object> event = buildBaseEvent(eventType, order);
        sendEvent(String.valueOf(order.getId()), event);
    }

    private Map<String, Object> buildBaseEvent(String eventType, Order order) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("orderId", order.getId());
        event.put("productId", order.getProductId());
        event.put("quantity", order.getQuantity());
        event.put("totalAmount", order.getTotalAmount());
        event.put("customerEmail", order.getCustomerEmail());
        event.put("orderStatus", order.getStatus());
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("service", "order-service");
        return event;
    }

    private void sendEvent(String key, Map<String, Object> event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(ORDER_EVENTS_TOPIC, key, payload);
            log.info("Published Kafka event: eventType={} orderId={}", event.get("eventType"), event.get("orderId"));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka event for orderId={}", event.get("orderId"), e);
        }
    }
}
