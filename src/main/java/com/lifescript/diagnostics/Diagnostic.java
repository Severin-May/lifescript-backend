package com.lifescript.diagnostics;

// Positions use Monaco marker fields: 1-based, endColumn exclusive.
public record Diagnostic(Stage stage, Severity severity, String message,
                         int startLineNumber, int startColumn, int endLineNumber, int endColumn) {

    // Not tied to a place (e.g. "Plan is empty"): all positions are 0.
    public static Diagnostic withoutPosition(Stage stage, String message) {
        return new Diagnostic(stage, Severity.ERROR, message, 0, 0, 0, 0);
    }

    public boolean hasPosition() {
        return startLineNumber > 0;
    }

    public String format() {
        return hasPosition() ? "Line " + startLineNumber + ": " + message : message;
    }
}
