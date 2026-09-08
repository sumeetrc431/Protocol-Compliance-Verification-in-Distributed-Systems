package com.example.protocol;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class RawLogReader extends BaseLogReader {


    private static final Pattern EVENT_TYPE = Pattern.compile("eventType=([A-Z_]+)");

    private static final Pattern ORDER_ID = Pattern.compile("orderId=(\\d+)");

    private static final Pattern TIMESTAMP =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{4})");

    private static final Pattern SERVICE = Pattern.compile("service=([a-zA-Z0-9\\-]+)");

    @Override
    protected List<ProtocolEvent> readEvents(String filePath) throws IOException {
        List<ProtocolEvent> events = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String eventType = firstMatch(EVENT_TYPE, line);
                String orderId = firstMatch(ORDER_ID, line);

                // a line without both of these isn't an event we care about
                if (eventType.isEmpty() || orderId.isEmpty()) {
                    continue;
                }

                String rawTime = firstMatch(TIMESTAMP, line);
                String service = firstMatch(SERVICE, line);

                events.add(new ProtocolEvent(orderId, eventType, parseTimestamp(rawTime), service));
            }
        }

        return events;
    }

    // returns the first capture group match, or empty string if no match
    private String firstMatch(Pattern pattern, String line) {
        Matcher m = pattern.matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
}
