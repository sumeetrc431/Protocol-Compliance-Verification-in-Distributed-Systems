package com.example.protocol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// A protocol as a state machine: states and which events move between them.
public class Protocol {

    private String startState;
    private Set<String> terminalStates = new HashSet<>();

    // outer key = current state, inner map = eventType -> next state
    private Map<String, Map<String, String>> transitions = new HashMap<>();

    public Protocol(String startState) {
        this.startState = startState;
    }

    public void addTransition(String fromState, String eventType, String toState) {
        if (!transitions.containsKey(fromState)) {
            transitions.put(fromState, new HashMap<>());
        }
        transitions.get(fromState).put(eventType, toState);
    }

    public void addTerminalState(String state) {
        terminalStates.add(state);
    }

    public String getStartState() {
        return startState;
    }

    public boolean isTerminal(String state) {
        return terminalStates.contains(state);
    }

    // returns null if this event isn't allowed from this state
    public String nextState(String currentState, String eventType) {
        Map<String, String> options = transitions.get(currentState);
        if (options == null) {
            return null;
        }
        return options.get(eventType);
    }

    // used for error messages - what events could legally happen from here
    public Set<String> allowedEventsFrom(String currentState) {
        Map<String, String> options = transitions.get(currentState);
        if (options == null) {
            return new HashSet<>();
        }
        return options.keySet();
    }

    // all states reachable from the given state, including the state itself.Used by isFutureStep below.
    public Set<String> reachableStatesFrom(String state) {
        Set<String> seen = new HashSet<>();
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        stack.push(state);
        while (!stack.isEmpty()) {
            String s = stack.pop();
            if (seen.contains(s)) {
                continue;
            }
            seen.add(s);
            Map<String, String> options = transitions.get(s);
            if (options != null) {
                for (String to : options.values()) {
                    stack.push(to);
                }
            }
        }
        return seen;
    }

    // Is this event legal from any state strictly later than the current one?
    // This is how the verifier separates MISSING_STEP (skipped ahead) from OUT_OF_ORDER (just wrong), without hardcoding any event names.
    public boolean isFutureStep(String currentState, String eventType) {
        Set<String> laterStates = reachableStatesFrom(currentState);
        laterStates.remove(currentState); // strictly later
        for (String state : laterStates) {
            Map<String, String> options = transitions.get(state);
            if (options != null && options.containsKey(eventType)) {
                return true;
            }
        }
        return false;
    }

    // does this protocol use this event anywhere at all?
    public boolean knowsEvent(String eventType) {
        for (Map<String, String> options : transitions.values()) {
            if (options.containsKey(eventType)) {
                return true;
            }
        }
        return false;
    }
}
