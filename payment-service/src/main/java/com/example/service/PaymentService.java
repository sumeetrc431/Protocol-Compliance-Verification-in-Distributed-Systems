package com.example.service;

import com.example.model.Payment;
import com.example.model.PaymentStatus;
import com.example.protocol.PaymentProtocolCheck;
import com.example.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProtocolCheck protocolCheck;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, PaymentProtocolCheck protocolCheck) {
        this.paymentRepository = paymentRepository;
        this.protocolCheck = protocolCheck;
    }

    // Charges an order. Takes orderState so the protocol and duplicate checks
    // can run before anything is charged.
    @Transactional
    public Map<String, Object> processPayment(Long orderId, String orderState, String customerEmail, BigDecimal amount) {

        // protocol check - payment should only happen after inventory was reserved
        if (!protocolCheck.isPaymentAllowed(orderState)) {
            log.info("Protocol violation - payment attempted for orderId={} but order state was '{}', expected '{}'",
                    orderId, orderState, protocolCheck.getExpectedState());
            return Map.of(
                    "success", false,
                    "transactionId", "",
                    "protocolViolation", true,
                    "message", "PROTOCOL_VIOLATION: expected order state '" + protocolCheck.getExpectedState()
                            + "' but got '" + orderState + "'"
            );
        }

        // duplicate check - has this order already been charged before?
        Optional<Payment> existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()) {
            log.info("Protocol violation - orderId={} was already charged once, rejecting duplicate payment", orderId);
            return Map.of(
                    "success", false,
                    "transactionId", "",
                    "protocolViolation", true,
                    "message", "PROTOCOL_VIOLATION: orderId " + orderId + " was already charged"
            );
        }

        // both protocol checks passed, so the payment can go ahead
        log.info("Processing payment: orderId={} customer={} amount={}", orderId, customerEmail, amount);

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setCustomerEmail(customerEmail);
        payment.setAmount(amount);

        // simulated gateway, not a real payment provider
        boolean success = simulatePaymentGateway(amount);

        if (success) {
            String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionId(txnId);
            paymentRepository.save(payment);

            log.info("Payment successful: orderId={} transactionId={} amount={}", orderId, txnId, amount);
            return Map.of("success", true, "transactionId", txnId, "protocolViolation", false, "message", "Payment processed");
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Simulated payment gateway decline");
            paymentRepository.save(payment);

            log.info("Payment failed: orderId={} customer={} amount={}", orderId, customerEmail, amount);
            return Map.of("success", false, "transactionId", "", "protocolViolation", false, "message", "Payment declined");
        }
    }

    public Optional<Payment> getPaymentByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    // No real gateway - declines over 5000 and fails roughly 10% otherwise, so the
    // testbed produces both successful and failed orders.
    private boolean simulatePaymentGateway(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("5000")) > 0) {
            return false;
        }
        return Math.random() > 0.1;
    }
}
