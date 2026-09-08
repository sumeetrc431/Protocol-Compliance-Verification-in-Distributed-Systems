package com.example.protocol;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public abstract class BaseLogReader implements LogReader {

    private static final DateTimeFormatter SPLUNK_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    protected abstract List<ProtocolEvent> readEvents(String source) throws java.io.IOException;

    @Override
    public Map<String, List<ProtocolEvent>> readGroupedByOrder(String source) throws java.io.IOException {
        List<ProtocolEvent> allEvents = readEvents(source);

        Map<String, List<ProtocolEvent>> byOrder = new HashMap<>();
        for (ProtocolEvent e : allEvents) {
            if (!byOrder.containsKey(e.getOrderId())) {
                byOrder.put(e.getOrderId(), new ArrayList<>());
            }
            byOrder.get(e.getOrderId()).add(e);
        }

        // sort each order's events by time so the verifier walks them in the,order they actually happened
        for (List<ProtocolEvent> events : byOrder.values()) {
            events.sort(Comparator.comparing(ProtocolEvent::getTimestamp));
        }

        return byOrder;
    }


    protected LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return LocalDateTime.MIN;
        }
        raw = raw.trim();
        try {
            return OffsetDateTime.parse(raw, SPLUNK_TIME).toLocalDateTime();
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(raw).toLocalDateTime();
            } catch (Exception e2) {
                try {
                    // last try( a plain local date-time with no offset)
                    return LocalDateTime.parse(raw);
                } catch (Exception e3) {
                    System.out.println("WARNING: could not parse timestamp '" + raw + "'");
                    return LocalDateTime.MIN;
                }
            }
        }
    }
}
