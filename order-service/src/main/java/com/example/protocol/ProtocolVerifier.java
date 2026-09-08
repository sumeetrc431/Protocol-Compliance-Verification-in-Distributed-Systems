package com.example.protocol;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Walks a single order's events in time order and checks each one is allowed from the current state. When it finds a problem it classifies it rather
// than just failing, so the evaluation can report what went wrong and where.




//(Functionality) The verifier never hardcodes event names or states — every judgement is made by asking the Protocol object, so it would check a different protocol without changes here.
public class ProtocolVerifier {

    public VerificationResult verify(Protocol protocol, String orderId, List<ProtocolEvent> events) {

        VerificationResult result = new VerificationResult(orderId);

        // ---- input validation ----

        // no events at all for this order
        if (events == null || events.isEmpty()) {
            result.setFinalState(protocol.getStartState());
            result.addViolation(new Violation(
                    ViolationType.NO_EVENTS,
                    "No events found for this order at all",
                    null,
                    protocol.getStartState()));
            return result;
        }

        // check every event actually belongs to this order. if grouping
        // upstream mixed orders, we must not silently verify a corrupt trace.
        for (ProtocolEvent event : events) {
            if (event.getOrderId() != null && !event.getOrderId().equals(orderId)) {
                result.setFinalState(protocol.getStartState());
                result.addViolation(new Violation(
                        ViolationType.UNKNOWN_EVENT,
                        "Trace for order '" + orderId + "' contains an event belonging to a different order '"
                                + event.getOrderId() + "'. The trace is not cleanly grouped, so no reliable "
                                + "verdict can be given.",
                        event.getEventType(),
                        protocol.getStartState()));
                return result;
            }
        }

        // ---- timestamp confidence check ----

        assessTimestampConfidence(events, result);

        // ---- the actual FSM walk ----

        String currentState = protocol.getStartState();
        Set<String> seenEvents = new HashSet<>();

        for (ProtocolEvent event : events) {
            String eventType = event.getEventType();
            String next = protocol.nextState(currentState, eventType);

            if (next == null) {
                // this event isn't legal from the current state. classify why.
                result.setFinalState(currentState);
                result.addViolation(classify(protocol, currentState, eventType, seenEvents));
                return result; // stop at the first violation (fail-fast, documented)
            }

            seenEvents.add(eventType);
            currentState = next;
        }

        result.setFinalState(currentState);

        // walked every event with no illegal transition. did we actually
        // finish in a terminal state? if not, the trace stopped partway.
        if (!protocol.isTerminal(currentState)) {
            result.addViolation(new Violation(
                    ViolationType.INCOMPLETE,
                    "Trace ended in state '" + currentState + "' which is not a final state. "
                            + "The order started but never finished.",
                    null,
                    currentState));
        }

        return result;
    }

    // work out which category an illegal event falls into. Order matters:
    // after-terminal and duplicate are checked before the ordering
    // categories because they're more specific explanations.
    private Violation classify(Protocol protocol, String currentState, String eventType, Set<String> seenEvents) {

        // 1. did the order already finish and something still arrived?
        if (protocol.isTerminal(currentState)) {
            return new Violation(
                    ViolationType.AFTER_TERMINAL,
                    "Event '" + eventType + "' arrived after the order had already finished in state '"
                            + currentState + "'. Nothing should happen after a final state.",
                    eventType,
                    currentState);
        }

        // 2. is this an event the protocol has never heard of at all?
        if (!protocol.knowsEvent(eventType)) {
            return new Violation(
                    ViolationType.UNKNOWN_EVENT,
                    "Event '" + eventType + "' is not part of this protocol at all. "
                            + "It may be corrupted data, or an event from a different system.",
                    eventType,
                    currentState);
        }

        // 3. have we already seen this exact event in this trace?
        if (seenEvents.contains(eventType)) {
            return new Violation(
                    ViolationType.DUPLICATE,
                    "Event '" + eventType + "' happened more than once. It should only occur once per order.",
                    eventType,
                    currentState);
        }

        // 4. is this event a step that comes LATER in the protocol? then we
        // skipped ahead past required steps to reach it (missing step).
        if (protocol.isFutureStep(currentState, eventType)) {
            return new Violation(
                    ViolationType.MISSING_STEP,
                    "Event '" + eventType + "' belongs to a later step, so one or more required steps were "
                            + "skipped to get here from state '" + currentState + "'. Expected one of: "
                            + protocol.allowedEventsFrom(currentState),
                    eventType,
                    currentState);
        }

        // 5. otherwise it's a real event but simply in the wrong place.
        return new Violation(
                ViolationType.OUT_OF_ORDER,
                "Event '" + eventType + "' is not allowed from state '" + currentState
                        + "'. Expected one of: " + protocol.allowedEventsFrom(currentState),
                eventType,
                currentState);
    }

    // The verdict is only as good as the ordering, and the ordering comes from timestamps. If two events share a timestamp, or one couldn't be parsed (LocalDateTime.MIN is the reader's sentinel for that), the sequence may be wrong.
    // We still return a verdict but mark it low confidence rather than failing the trace.
    private void assessTimestampConfidence(List<ProtocolEvent> events, VerificationResult result) {
        Set<LocalDateTime> seenTimes = new HashSet<>();
        for (ProtocolEvent e : events) {
            LocalDateTime t = e.getTimestamp();
            if (t == null || t.equals(LocalDateTime.MIN)) {
                result.setTimestampReliable(false);
                result.addNote("Event '" + e.getEventType() + "' has a missing or unparseable timestamp, "
                        + "so the ordering of this trace may not be reliable.");
            }
            if (t != null && !seenTimes.add(t)) {
                result.setTimestampReliable(false);
                result.addNote("Two events share the exact same timestamp (" + t + "), "
                        + "so their relative order cannot be determined for certain.");
            }
        }
    }
}
