package com.example.protocol;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolVerifierTest {

    private final ProtocolVerifier verifier = new ProtocolVerifier();
    private final Protocol protocol = OrderProtocols.orderLifecycle();

    private static final String CREATED = "ORDER_CREATED";
    private static final String RESERVED = "INVENTORY_RESERVED";
    private static final String PAID = "PAYMENT_PROCESSED";
    private static final String COMPLETED = "ORDER_COMPLETED";
    private static final String FAILED = "ORDER_FAILED";


    private List<ProtocolEvent> trace(String... eventTypes) {
        return traceForOrder("test-order", eventTypes);
    }

    private List<ProtocolEvent> traceForOrder(String orderId, String... eventTypes) {
        List<ProtocolEvent> events = new ArrayList<>();
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        for (int i = 0; i < eventTypes.length; i++) {
            events.add(new ProtocolEvent(orderId, eventTypes[i], t.plusSeconds(i), "order-service"));
        }
        return events;
    }

    // ---------- COMPLIANT CASES ----------

    @Test
    void fullSuccessIsCompliant() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, RESERVED, PAID, COMPLETED));
        assertTrue(r.isValid());
        assertEquals("COMPLETED", r.getFinalState());
    }

    @Test
    void failAtInventoryIsCompliant() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, FAILED));
        assertTrue(r.isValid());
        assertEquals("FAILED", r.getFinalState());
    }

    @Test
    void failAtPaymentStepIsCompliant() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, RESERVED, FAILED));
        assertTrue(r.isValid());
        assertEquals("FAILED", r.getFinalState());
    }

    // ---------- VIOLATION CATEGORIES ----------

    @Test
    void completedBeforePaidIsMissingStep() {
        // the real order-6 case: completed while still only reserved
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, RESERVED, COMPLETED, PAID));
        assertFalse(r.isValid());
        assertEquals(ViolationType.MISSING_STEP, r.getViolationType());
    }

    @Test
    void paymentBeforeReserveIsMissingStep() {

        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, PAID, RESERVED));
        assertFalse(r.isValid());
        assertEquals(ViolationType.MISSING_STEP, r.getViolationType());
    }

    @Test
    void createdStraightToCompletedIsMissingStep() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, COMPLETED));
        assertFalse(r.isValid());
        assertEquals(ViolationType.MISSING_STEP, r.getViolationType());
    }

    @Test
    void reservedStraightToCompletedIsMissingStep() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, RESERVED, COMPLETED));
        assertFalse(r.isValid());
        assertEquals(ViolationType.MISSING_STEP, r.getViolationType());
    }

    @Test
    void stuckAtCreatedIsIncomplete() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED));
        assertFalse(r.isValid());
        assertEquals(ViolationType.INCOMPLETE, r.getViolationType());
        assertEquals("CREATED", r.getFinalState());
    }

    @Test
    void stuckAtReservedIsIncomplete() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, RESERVED));
        assertFalse(r.isValid());
        assertEquals(ViolationType.INCOMPLETE, r.getViolationType());
        assertEquals("INVENTORY_RESERVED", r.getFinalState());
    }

    @Test
    void duplicateReserveIsDuplicate() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, RESERVED, RESERVED, PAID, COMPLETED));
        assertFalse(r.isValid());
        assertEquals(ViolationType.DUPLICATE, r.getViolationType());
    }

    @Test
    void eventAfterCompletedIsAfterTerminal() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, RESERVED, PAID, COMPLETED, PAID));
        assertFalse(r.isValid());
        assertEquals(ViolationType.AFTER_TERMINAL, r.getViolationType());
    }

    @Test
    void emptyTraceIsNoEvents() {
        VerificationResult r = verifier.verify(protocol, "test-order", new ArrayList<>());
        assertFalse(r.isValid());
        assertEquals(ViolationType.NO_EVENTS, r.getViolationType());
    }

    @Test
    void failAfterPaymentIsOutOfOrder() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, RESERVED, PAID, FAILED));
        assertFalse(r.isValid());
        assertEquals(ViolationType.OUT_OF_ORDER, r.getViolationType());
    }

    @Test
    void wrongStartIsMissingStep() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(RESERVED, PAID));
        assertFalse(r.isValid());
        assertEquals(ViolationType.MISSING_STEP, r.getViolationType());
    }

    // EDGE  CASES

    @Test
    void unknownEventIsUnknownEvent() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, "SOMETHING_MADE_UP"));
        assertFalse(r.isValid());
        assertEquals(ViolationType.UNKNOWN_EVENT, r.getViolationType());
    }

    @Test
    void eventFromWrongOrderIsRejected() {
        List<ProtocolEvent> events = trace(CREATED, RESERVED);
        events.add(new ProtocolEvent("DIFFERENT-ORDER", PAID, LocalDateTime.of(2026, 1, 1, 10, 0, 5), "order-service"));
        VerificationResult r = verifier.verify(protocol, "test-order", events);
        assertFalse(r.isValid());
        assertEquals(ViolationType.UNKNOWN_EVENT, r.getViolationType());
    }

    @Test
    void tiedTimestampsLowerConfidence() {
        // two events with the exact same timestamp - order can't be trusted
        List<ProtocolEvent> events = new ArrayList<>();
        LocalDateTime same = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        events.add(new ProtocolEvent("test-order", CREATED, same, "order-service"));
        events.add(new ProtocolEvent("test-order", RESERVED, same, "order-service"));
        events.add(new ProtocolEvent("test-order", PAID, same.plusSeconds(1), "order-service"));
        events.add(new ProtocolEvent("test-order", COMPLETED, same.plusSeconds(2), "order-service"));
        VerificationResult r = verifier.verify(protocol, "test-order", events);
        // verdict is still produced, but flagged as not reliable
        assertFalse(r.isTimestampReliable());
        assertFalse(r.getNotes().isEmpty());
    }

    @Test
    void unparseableTimestampLowerConfidence() {
        List<ProtocolEvent> events = new ArrayList<>();
        events.add(new ProtocolEvent("test-order", CREATED, LocalDateTime.MIN, "order-service"));
        events.add(new ProtocolEvent("test-order", RESERVED, LocalDateTime.of(2026, 1, 1, 10, 0, 1), "order-service"));
        VerificationResult r = verifier.verify(protocol, "test-order", events);
        assertFalse(r.isTimestampReliable());
    }

    @Test
    void compliantTraceHasReliableTimestamps() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, RESERVED, PAID, COMPLETED));
        assertTrue(r.isTimestampReliable());
        assertTrue(r.getNotes().isEmpty());
    }

    // ---------- behaviour documentation ----------

    @Test
    void violationStopsAtFirstProblem() {
        VerificationResult r = verifier.verify(protocol, "test-order", trace(CREATED, COMPLETED, PAID, RESERVED));
        assertFalse(r.isValid());
        assertEquals(ViolationType.MISSING_STEP, r.getViolationType());
        assertEquals(1, r.getViolations().size());
    }
}
