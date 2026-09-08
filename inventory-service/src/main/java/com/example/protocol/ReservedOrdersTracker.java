package com.example.protocol;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

// Keeps track of which orderIds already reserved stock, so the same order can't reserve twice.
@Component
public class ReservedOrdersTracker {

    // in-memory only, so this resets when the service restarts
    private Set<Long> alreadyReserved = new HashSet<>();

    public boolean hasAlreadyReserved(Long orderId) {
        return alreadyReserved.contains(orderId);
    }

    public void markAsReserved(Long orderId) {
        alreadyReserved.add(orderId);
    }
}
