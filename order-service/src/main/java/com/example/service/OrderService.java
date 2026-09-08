package com.example.service;

import com.example.dto.OrderDto;
import com.example.kafka.EventPublisher;
import com.example.model.Order;
import com.example.model.OrderStatus;
import com.example.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final EventPublisher eventPublisher;

    @Value("${services.inventory.url}")
    private String inventoryServiceUrl;

    @Value("${services.payment.url}")
    private String paymentServiceUrl;

    @Autowired
    public OrderService(OrderRepository orderRepository, RestTemplate restTemplate, EventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
    }

    // drives the whole lifecycle: create, reserve, pay, complete - publishing an
    // event at each step. Any failure short-circuits to FAILED.
    @Transactional
    public Order createOrder(OrderDto.CreateOrderRequest request) {
        log.info("Creating order: productId={} quantity={} customer={}", request.getProductId(), request.getQuantity(), request.getCustomerEmail());

        // saved first so the order has an id before any event is published
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setTotalAmount(request.getTotalAmount());
        order.setCustomerEmail(request.getCustomerEmail());
        order.setStatus(OrderStatus.CREATED);
        order = orderRepository.save(order);

        eventPublisher.publishOrderCreated(order);
        log.info("Order created: orderId={}", order.getId());

        // Step 1: Reserve inventory (REST call to inventory-service)
        boolean inventoryReserved = reserveInventory(order);
        if (!inventoryReserved) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            eventPublisher.publishOrderFailed(order, "Inventory reservation failed");
            log.info("Order failed - inventory unavailable: orderId={}", order.getId());
            return order;
        }

        order.setStatus(OrderStatus.INVENTORY_RESERVED);
        orderRepository.save(order);
        eventPublisher.publishInventoryReserved(order);
        log.info("Inventory reserved for orderId={}", order.getId());

        // Step 2: Process payment (REST call to payment-service)
        boolean paymentProcessed = processPayment(order);
        if (!paymentProcessed) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            eventPublisher.publishOrderFailed(order, "Payment processing failed");
            log.info("Order failed - payment declined: orderId={}", order.getId());
            return order;
        }

        order.setStatus(OrderStatus.PAYMENT_PROCESSED);
        orderRepository.save(order);
        eventPublisher.publishPaymentProcessed(order);
        log.info("Payment processed for orderId={}", order.getId());

        // Step 3: Complete the order
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
        eventPublisher.publishOrderCompleted(order);
        log.info("Order completed: orderId={}", order.getId());

        return order;
    }

    public Optional<Order> getOrder(Long id) {
        return orderRepository.findById(id);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public List<Order> getOrdersByCustomer(String email) {
        return orderRepository.findByCustomerEmail(email);
    }

    // REST call to inventory-service; false means don't continue the lifecycle
    private boolean reserveInventory(Order order) {
        try {
            // sending orderId and the order's current status now, so inventory-service can check the protocol before reserving.
            Map<String, Object> request = new HashMap<>();
            request.put("orderId", order.getId());
            request.put("orderState", order.getStatus().toString());
            request.put("productId", order.getProductId());
            request.put("quantity", order.getQuantity());

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    inventoryServiceUrl + "/inventory/reserve",
                    request,
                    Map.class);

            if (response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                boolean reserved = Boolean.TRUE.equals(body.get("reserved"));
                boolean protocolViolation = Boolean.TRUE.equals(body.get("protocolViolation"));

                if (protocolViolation) {
                    log.error("PROTOCOL VIOLATION on inventory call for orderId={}: {}",
                            order.getId(), body.get("message"));
                }

                log.info("Inventory response for orderId={}: reserved={} message={}",
                        order.getId(), reserved, body.get("message"));
                return reserved;
            }
            return false;
        } catch (Exception e) {
            log.error("Inventory service call failed for orderId={}: {}", order.getId(), e.getMessage());
            return false;
        }
    }

    // REST call to payment-service; false means don't continue the lifecycle
    private boolean processPayment(Order order) {
        try {
            // same idea here- sending orderId + current status so
            // payment-service can check the order actually went through and inventory reservation before we let it charge anything
            Map<String, Object> request = new HashMap<>();
            request.put("orderId", order.getId());
            request.put("orderState", order.getStatus().toString());
            request.put("customerEmail", order.getCustomerEmail());
            request.put("amount", order.getTotalAmount());

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    paymentServiceUrl + "/payments/process",
                    request,
                    Map.class);

            if (response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                boolean success = Boolean.TRUE.equals(body.get("success"));
                boolean protocolViolation = Boolean.TRUE.equals(body.get("protocolViolation"));

                if (protocolViolation) {
                    log.error("PROTOCOL VIOLATION on payment call for orderId={}: {}",
                            order.getId(), body.get("message"));
                }

                log.info("Payment response for orderId={}: success={} transactionId={}",
                        order.getId(), success, body.get("transactionId"));
                return success;
            }
            return false;
        } catch (Exception e) {
            log.error("Payment service call failed for orderId={}: {}", order.getId(), e.getMessage());
            return false;
        }
    }
}
