package com.lifescript.api;

import com.lifescript.diagnostics.Diagnostic;

import java.util.List;

// The JSON returned by /api/validate and /api/compile:
//   { "valid": false, "diagnostics": [ { "stage": "VALIDATION", "severity": "ERROR", "message": "...",
//     "startLineNumber": 9, "startColumn": 1, "endLineNumber": 9, "endColumn": 13 } ] }
// A diagnostic not tied to a place (e.g. "Plan is empty") has all position fields 0.
public record CompileResponse(boolean valid, List<Diagnostic> diagnostics) {

    public static CompileResponse of(List<Diagnostic> diagnostics) {
        return new CompileResponse(diagnostics.isEmpty(), diagnostics);
    }
}
