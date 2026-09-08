package com.example.protocol;

// Builds the Protocol for the order lifecycle. State names match OrderStatus and event names match what OrderEventPublisher sends.
public class OrderProtocols {

    public static Protocol orderLifecycle() {
        Protocol p = new Protocol("START");

        p.addTransition("START", "ORDER_CREATED", "CREATED");

        p.addTransition("CREATED", "INVENTORY_RESERVED", "INVENTORY_RESERVED");
        p.addTransition("CREATED", "ORDER_FAILED", "FAILED");

        p.addTransition("INVENTORY_RESERVED", "PAYMENT_PROCESSED", "PAYMENT_PROCESSED");
        p.addTransition("INVENTORY_RESERVED", "ORDER_FAILED", "FAILED");

        p.addTransition("PAYMENT_PROCESSED", "ORDER_COMPLETED", "COMPLETED");

        p.addTerminalState("COMPLETED");
        p.addTerminalState("FAILED");

        return p;
    }
}
