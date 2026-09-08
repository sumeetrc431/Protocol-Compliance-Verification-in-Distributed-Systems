package com.example.protocol;

import java.util.ArrayList;
import java.util.List;

// Result of checking one order's trace. A boolean would say whether it passed but not why, where or how far the verdict can be trusted, which is what the evaluation needs.
public class VerificationResult {

    private boolean valid;
    private String orderId;
    private String finalState;
    private List<Violation> violations = new ArrayList<>();

    // whether the event ordering (and therefore the verdict) can be trusted.
    // false if timestamps were missing, unparseable or tied. The verdict is still produced but this flags that it rests on uncertain ordering.
    private boolean timestampReliable = true;
    private List<String> notes = new ArrayList<>();

    public VerificationResult(String orderId) {
        this.orderId = orderId;
        this.valid = true; // assume valid until we find a problem
    }

    public void addViolation(Violation violation) {
        this.valid = false;
        this.violations.add(violation);
    }

    public boolean isValid() {
        return valid;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getFinalState() {
        return finalState;
    }

    public void setFinalState(String finalState) {
        this.finalState = finalState;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    // the first violation's type or null if the trace is valid. handy because the walk stops at the first violation, so there's usually one.
    public ViolationType getViolationType() {
        if (violations.isEmpty()) {
            return null;
        }
        return violations.get(0).getType();
    }

    // just the violation messages as plain strings
    public List<String> getErrors() {
        List<String> messages = new ArrayList<>();
        for (Violation v : violations) {
            messages.add(v.getMessage());
        }
        return messages;
    }

    // ---- timestamp confidence ----

    public boolean isTimestampReliable() {
        return timestampReliable;
    }

    public void setTimestampReliable(boolean timestampReliable) {
        this.timestampReliable = timestampReliable;
    }

    public void addNote(String note) {
        this.notes.add(note);
    }

    public List<String> getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (valid) {
            sb.append("Order ").append(orderId).append(" is VALID, ended in state ").append(finalState);
        } else {
            sb.append("Order ").append(orderId).append(" is INVALID (")
              .append(getViolationType()).append("). ").append(getErrors());
        }
        if (!timestampReliable) {
            sb.append(" [low confidence: timestamp ordering uncertain]");
        }
        return sb.toString();
    }
}
