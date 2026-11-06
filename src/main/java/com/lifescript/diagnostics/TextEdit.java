package com.lifescript.diagnostics;

// Replaces the range with text; an empty range inserts. Positions as in Diagnostic.
public record TextEdit(int startLineNumber, int startColumn, int endLineNumber, int endColumn, String text) {
}
