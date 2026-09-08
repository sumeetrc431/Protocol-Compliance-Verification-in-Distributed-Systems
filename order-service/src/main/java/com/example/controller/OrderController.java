package com.example.controller;

import com.example.dto.OrderDto;
import com.example.model.Order;
import com.example.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // POST /orders - runs the whole order lifecycle and returns the final state
    @PostMapping
    public ResponseEntity<OrderDto.OrderResponse> createOrder(@RequestBody OrderDto.CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(order));
    }

    // GET /orders/{id} - 404 if no order with that id
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto.OrderResponse> getOrder(@PathVariable Long id) {
        return orderService.getOrder(id)
                .map(order -> ResponseEntity.ok(toResponse(order)))
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /orders - every order, used by verify.sh and for checking state after a demo
    @GetMapping
    public ResponseEntity<List<OrderDto.OrderResponse>> getAllOrders() {
        List<OrderDto.OrderResponse> orders = orderService.getAllOrders()
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }

    // GET /orders/customer/{email} - orders for one customer
    @GetMapping("/customer/{email}")
    public ResponseEntity<List<OrderDto.OrderResponse>> getOrdersByCustomer(@PathVariable String email) {
        List<OrderDto.OrderResponse> orders = orderService.getOrdersByCustomer(email)
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }

    // entity -> response DTO, so the JPA entity isn't exposed directly
    private OrderDto.OrderResponse toResponse(Order order) {
        OrderDto.OrderResponse response = new OrderDto.OrderResponse();
        response.setId(order.getId());
        response.setProductId(order.getProductId());
        response.setQuantity(order.getQuantity());
        response.setTotalAmount(order.getTotalAmount());
        response.setCustomerEmail(order.getCustomerEmail());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }
}
