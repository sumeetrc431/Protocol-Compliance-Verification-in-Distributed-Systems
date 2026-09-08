package com.example.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class LogReaderFormatTest {


    private String resource(String name) {

        return getClass().getClassLoader().getResource(name).getPath();
    }

    @Test
    void csvReaderProducesExpectedTrace() throws Exception {
        Map<String, List<ProtocolEvent>> result = new CsvLogReader().readGroupedByOrder(resource("sample-log.csv"));
        assertSequenceForOrder9(result);
    }

    @Test
    void jsonReaderProducesExpectedTrace() throws Exception {
        Map<String, List<ProtocolEvent>> result = new JsonLogReader().readGroupedByOrder(resource("sample-log.json"));
        assertSequenceForOrder9(result);
    }

    @Test
    void rawReaderProducesExpectedTrace() throws Exception {
        Map<String, List<ProtocolEvent>> result = new RawLogReader().readGroupedByOrder(resource("sample-log.txt"));
        assertSequenceForOrder9(result);
    }

    @Test
    void allThreeReadersProduceIdenticalEventSequences() throws Exception {
        List<ProtocolEvent> fromCsv = new CsvLogReader().readGroupedByOrder(resource("sample-log.csv")).get("9");
        List<ProtocolEvent> fromJson = new JsonLogReader().readGroupedByOrder(resource("sample-log.json")).get("9");
        List<ProtocolEvent> fromRaw = new RawLogReader().readGroupedByOrder(resource("sample-log.txt")).get("9");

        // same number of events
        assertEquals(fromCsv.size(), fromJson.size());
        assertEquals(fromCsv.size(), fromRaw.size());

        // same event types in the same order across all three
        for (int i = 0; i < fromCsv.size(); i++) {
            String csvType = fromCsv.get(i).getEventType();
            String jsonType = fromJson.get(i).getEventType();
            String rawType = fromRaw.get(i).getEventType();
            assertEquals(csvType, jsonType, "CSV and JSON differ at position " + i);
            assertEquals(csvType, rawType, "CSV and RAW differ at position " + i);
        }
    }

    @Test
    void verifierGivesSameVerdictRegardlessOfFormat() throws Exception {
        Protocol protocol = OrderProtocols.orderLifecycle();
        ProtocolVerifier verifier = new ProtocolVerifier();

        List<ProtocolEvent> fromCsv = new CsvLogReader().readGroupedByOrder(resource("sample-log.csv")).get("9");
        List<ProtocolEvent> fromJson = new JsonLogReader().readGroupedByOrder(resource("sample-log.json")).get("9");
        List<ProtocolEvent> fromRaw = new RawLogReader().readGroupedByOrder(resource("sample-log.txt")).get("9");

        VerificationResult csvResult = verifier.verify(protocol, "9", fromCsv);
        VerificationResult jsonResult = verifier.verify(protocol, "9", fromJson);
        VerificationResult rawResult = verifier.verify(protocol, "9", fromRaw);

        // this trace is compliant (created, reserved, failed) - all three agree
        assertTrue(csvResult.isValid());
        assertTrue(jsonResult.isValid());
        assertTrue(rawResult.isValid());

        assertEquals(csvResult.getFinalState(), jsonResult.getFinalState());
        assertEquals(csvResult.getFinalState(), rawResult.getFinalState());
    }

    // helper: the trace for order 9 should be exactly these three events, inthis order, regardless of how they were scrambled in the source file
    private void assertSequenceForOrder9(Map<String, List<ProtocolEvent>> result) {
        List<ProtocolEvent> events = result.get("9");
        assertEquals(3, events.size());
        assertEquals("ORDER_CREATED", events.get(0).getEventType());
        assertEquals("INVENTORY_RESERVED", events.get(1).getEventType());
        assertEquals("ORDER_FAILED", events.get(2).getEventType());
    }
}
