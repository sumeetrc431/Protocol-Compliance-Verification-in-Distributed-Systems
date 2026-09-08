package com.example.protocol;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Reads a CSV exported from Splunk, using the columns produced by this search

//   For the splunk query
public class CsvLogReader extends BaseLogReader {

    @Override
    protected List<ProtocolEvent> readEvents(String filePath) throws IOException {
        List<ProtocolEvent> events = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return events; // empty file
            }

            String[] headers = splitCsvLine(headerLine);
            int timeCol = findColumn(headers, "_time");
            int orderIdCol = findColumn(headers, "orderId");
            int eventTypeCol = findColumn(headers, "eventType");
            int serviceCol = findColumn(headers, "service");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] cols = splitCsvLine(line);
                if (orderIdCol < 0 || eventTypeCol < 0
                        || orderIdCol >= cols.length || eventTypeCol >= cols.length) {
                    continue;
                }

                String orderId = cols[orderIdCol].trim();
                String eventType = cols[eventTypeCol].trim();

                // skip rows without the fields we need (e.g. parse-error rows)
                if (orderId.isEmpty() || eventType.isEmpty()) {
                    continue;
                }

                String service = (serviceCol >= 0 && serviceCol < cols.length) ? cols[serviceCol].trim() : "";
                String rawTime = (timeCol >= 0 && timeCol < cols.length) ? cols[timeCol].trim() : "";

                events.add(new ProtocolEvent(orderId, eventType, parseTimestamp(rawTime), service));
            }
        }

        return events;
    }

    private int findColumn(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    // CSV splitter that respects quoted fields (Splunk quotes the timestamp)
    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());

        return fields.toArray(new String[0]);
    }
}
