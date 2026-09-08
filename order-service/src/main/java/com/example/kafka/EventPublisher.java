package com.example.kafka;

import com.example.model.Order;

public interface EventPublisher {
    void publishOrderCreated(Order order);
    void publishInventoryReserved(Order order);
    void publishPaymentProcessed(Order order);
    void publishOrderCompleted(Order order);
    void publishOrderFailed(Order order, String reason);
}
