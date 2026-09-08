package com.example.protocol;

// One protocol violation found in a trace. Holds what kind it is
public class Violation {

    private ViolationType type;
    private String message;
    private String offendingEvent; // the event that broke the protocol
    private String stateAtFailure; // the state we were in when it broke

    public Violation(ViolationType type, String message, String offendingEvent, String stateAtFailure) {
        this.type = type;
        this.message = message;
        this.offendingEvent = offendingEvent;
        this.stateAtFailure = stateAtFailure;
    }

    public ViolationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getOffendingEvent() {
        return offendingEvent;
    }

    public String getStateAtFailure() {
        return stateAtFailure;
    }

    @Override
    public String toString() {
        return type + ": " + message;
    }
}
