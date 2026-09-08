package com.example.protocol;

import org.springframework.stereotype.Component;

// Same idea as InventoryProtocolCheck but for payment-service.

@Component
public class PaymentProtocolCheck {

    private static final String EXPECTED_STATE_BEFORE_PAYMENT = "INVENTORY_RESERVED";

    public boolean isPaymentAllowed(String orderStateFromCaller) {
        return EXPECTED_STATE_BEFORE_PAYMENT.equals(orderStateFromCaller);
    }

    public String getExpectedState() {
        return EXPECTED_STATE_BEFORE_PAYMENT;
    }
}
