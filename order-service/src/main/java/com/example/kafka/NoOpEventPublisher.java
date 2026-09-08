package com.example.kafka;

import com.example.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!docker")
public class NoOpEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpEventPublisher.class);

    @Override
    public void publishOrderCreated(Order order) {
        log.info("[EVENT] ORDER_CREATED orderId={} productId={}", order.getId(), order.getProductId());
    }

    @Override
    public void publishInventoryReserved(Order order) {
        log.info("[EVENT] INVENTORY_RESERVED orderId={}", order.getId());
    }

    @Override
    public void publishPaymentProcessed(Order order) {
        log.info("[EVENT] PAYMENT_PROCESSED orderId={}", order.getId());
    }

    @Override
    public void publishOrderCompleted(Order order) {
        log.info("[EVENT] ORDER_COMPLETED orderId={}", order.getId());
    }

    @Override
    public void publishOrderFailed(Order order, String reason) {
        log.info("[EVENT] ORDER_FAILED orderId={} reason={}", order.getId(), reason);
    }
}
