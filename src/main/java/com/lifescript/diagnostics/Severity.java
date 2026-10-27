package com.lifescript.diagnostics;

// Same names as Monaco's MarkerSeverity, so the frontend can map them directly.
// Everything is an ERROR for now; WARNING is for problems that shouldn't stop compiling.
public enum Severity {
    ERROR,
    WARNING
}
