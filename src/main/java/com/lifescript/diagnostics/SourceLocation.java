package com.lifescript.diagnostics;

// 1-based, endColumn exclusive, like Diagnostic.
public record SourceLocation(int line, int startColumn, int endColumn) {
}
