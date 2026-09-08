package com.example.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JsonLogReader extends BaseLogReader {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected List<ProtocolEvent> readEvents(String filePath) throws IOException {
        List<ProtocolEvent> events = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                JsonNode node;
                try {
                    node = mapper.readTree(line);
                } catch (Exception e) {
                    // not valid JSON on this line, skip it
                    continue;
                }

                // Splunk's JSON export nests the real fields under "result",so if that's there we look inside it
                if (node.has("result")) {
                    node = node.get("result");
                }

                String orderId = textOrEmpty(node, "orderId");
                String eventType = textOrEmpty(node, "eventType");

                if (orderId.isEmpty() || eventType.isEmpty()) {
                    continue;
                }

                String service = textOrEmpty(node, "service");
                String rawTime = textOrEmpty(node, "_time");
                if (rawTime.isEmpty()) {

                    rawTime = textOrEmpty(node, "timestamp");
                }

                events.add(new ProtocolEvent(orderId, eventType, parseTimestamp(rawTime), service));
            }
        }

        return events;
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText().trim();
    }
}
