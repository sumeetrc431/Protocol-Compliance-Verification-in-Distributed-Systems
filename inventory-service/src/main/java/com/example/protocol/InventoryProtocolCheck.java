package com.example.protocol;

import org.springframework.stereotype.Component;

// Reserving stock is only allowed when the order has just been created. A one-step version of the Protocol idea in order-service, this service
// only needs to check one transition, not walk a whole sequence.
@Component
public class InventoryProtocolCheck {

    // the only state inventory-service is allowed to see before reserving
    private static final String EXPECTED_STATE_BEFORE_RESERVE = "CREATED";

    public boolean isReserveAllowed(String orderStateFromCaller) {
        return EXPECTED_STATE_BEFORE_RESERVE.equals(orderStateFromCaller);
    }

    public String getExpectedState() {
        return EXPECTED_STATE_BEFORE_RESERVE;
    }
}
