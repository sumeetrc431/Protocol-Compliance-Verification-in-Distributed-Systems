package com.example.controller;

import com.example.model.Payment;
import com.example.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // POST /payments/process - order-service sends orderState so the protocol
    // check can run before anything is charged
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        String orderState = (String) request.get("orderState");
        String customerEmail = (String) request.get("customerEmail");
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        Map<String, Object> result = paymentService.processPayment(orderId, orderState, customerEmail, amount);
        return ResponseEntity.ok(result);
    }

    // GET /payments/order/{orderId} - 404 if the order was never charged
    @GetMapping("/order/{orderId}")
    public ResponseEntity<Payment> getPaymentByOrder(@PathVariable Long orderId) {
        return paymentService.getPaymentByOrder(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
