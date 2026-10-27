package com.lifescript.diagnostics;

// Where something was written in the plan: one line, from startColumn up to (not including) endColumn,
// counted from 1 like Diagnostic. Model objects keep one so later checks can report problems at the right place.
public record SourceLocation(int line, int startColumn, int endColumn) {
}
