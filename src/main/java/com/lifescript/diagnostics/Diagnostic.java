package com.lifescript.diagnostics;

import java.util.List;

// Positions use Monaco marker fields: 1-based, endColumn exclusive. fixes is empty when there is none.
public record Diagnostic(Stage stage, Severity severity, String message,
                         int startLineNumber, int startColumn, int endLineNumber, int endColumn,
                         List<Fix> fixes) {

    public Diagnostic {
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
    }

    public Diagnostic(Stage stage, Severity severity, String message,
                      int startLineNumber, int startColumn, int endLineNumber, int endColumn) {
        this(stage, severity, message, startLineNumber, startColumn, endLineNumber, endColumn, List.of());
    }

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
