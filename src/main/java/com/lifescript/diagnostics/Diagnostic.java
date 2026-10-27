package com.lifescript.diagnostics;

// One problem found in a plan. The position uses the same fields as a Monaco editor marker, so the
// frontend can pass it straight to setModelMarkers: lines and columns start at 1, and endColumn is
// exclusive (the column just after the last marked character).
public record Diagnostic(Stage stage, Severity severity, String message,
                         int startLineNumber, int startColumn, int endLineNumber, int endColumn) {

    // For problems that aren't tied to a place in the file, e.g. "Plan is empty" or an internal error:
    // all position fields are 0, and the frontend shows them in the output panel only (no editor marker).
    public static Diagnostic withoutPosition(Stage stage, String message) {
        return new Diagnostic(stage, Severity.ERROR, message, 0, 0, 0, 0);
    }

    public boolean hasPosition() {
        return startLineNumber > 0;
    }

    // The classic compiler form for lists and logs: "Line 9: Duration '0m' must be greater than zero".
    public String format() {
        return hasPosition() ? "Line " + startLineNumber + ": " + message : message;
    }
}
