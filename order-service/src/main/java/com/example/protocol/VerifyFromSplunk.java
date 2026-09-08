package com.example.protocol;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Ties everything together. Reads a log export (CSV, JSON or raw), groups the events by order, and runs each order's trace through the verifier.
// Prints a report showing which orders followed the protocol and which didn't, including what CATEGORY each violation is.

public class VerifyFromSplunk {


    private static final String DEFAULT_LOG_FILE =
            "C:\\Users\\Sumeet\\Downloads\\verifier-corpus\\csv\\corpus-all.csv";

    public static void main(String[] args) throws Exception {

        String path = (args.length >= 1) ? args[0] : DEFAULT_LOG_FILE;
        System.out.println("Reading log export: " + path);


        LogReader reader = pickReader(path);
        System.out.println("Using reader: " + reader.getClass().getSimpleName());
        System.out.println();

        Protocol protocol = OrderProtocols.orderLifecycle();
        Map<String, List<ProtocolEvent>> eventsByOrder = reader.readGroupedByOrder(path);

        // sort the orders numerically so the report reads in order
        Map<String, List<ProtocolEvent>> sorted = new TreeMap<>(
                (a, b) -> Integer.compare(safeInt(a), safeInt(b)));
        sorted.putAll(eventsByOrder);

        ProtocolVerifier verifier = new ProtocolVerifier();

        int compliant = 0;
        int violations = 0;
        Map<ViolationType, Integer> categoryCounts = new HashMap<>();

        System.out.println("=================================================================");
        System.out.println("PROTOCOL VERIFICATION REPORT");
        System.out.println("Checking " + eventsByOrder.size() + " orders against the order lifecycle protocol");
        System.out.println("=================================================================");
        System.out.println();

        for (Map.Entry<String, List<ProtocolEvent>> entry : sorted.entrySet()) {
            String orderId = entry.getKey();
            List<ProtocolEvent> events = entry.getValue();

            VerificationResult result = verifier.verify(protocol, orderId, events);

            StringBuilder sequence = new StringBuilder();
            for (int i = 0; i < events.size(); i++) {
                if (i > 0) sequence.append(" -> ");
                sequence.append(events.get(i).getEventType());
            }

            if (result.isValid()) {
                System.out.println("Order " + orderId + "  [COMPLIANT]");
                System.out.println("   sequence: " + sequence);
                if (!result.isTimestampReliable()) {
                    System.out.println("   note:     verdict confidence is LOW (timestamp ordering uncertain)");
                }
                compliant++;
            } else {
                ViolationType type = result.getViolationType();
                System.out.println("Order " + orderId + "  [VIOLATION - " + type + "]");
                System.out.println("   sequence: " + sequence);
                for (Violation v : result.getViolations()) {
                    System.out.println("   reason:   " + v.getMessage());
                }
                if (!result.isTimestampReliable()) {
                    System.out.println("   note:     verdict confidence is LOW (timestamp ordering uncertain)");
                }
                violations++;
                categoryCounts.merge(type, 1, Integer::sum);
            }
            System.out.println();
        }

        System.out.println("=================================================================");
        System.out.println("SUMMARY: " + compliant + " compliant, " + violations + " violation(s)");
        if (!categoryCounts.isEmpty()) {
            System.out.println("Violations by category:");
            for (Map.Entry<ViolationType, Integer> e : categoryCounts.entrySet()) {
                System.out.println("   " + e.getKey() + ": " + e.getValue());
            }
        }
        System.out.println("=================================================================");
    }

    // choose a reader based on the file extension
    private static LogReader pickReader(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".json")) {
            return new JsonLogReader();
        } else if (lower.endsWith(".txt") || lower.endsWith(".log")) {
            return new RawLogReader();
        } else {
            // default to CSV
            return new CsvLogReader();
        }
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
