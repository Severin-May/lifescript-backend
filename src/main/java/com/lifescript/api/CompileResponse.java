package com.lifescript.api;

import com.lifescript.diagnostics.Diagnostic;

import java.util.List;

public record CompileResponse(boolean valid, List<Diagnostic> diagnostics) {

    public static CompileResponse of(List<Diagnostic> diagnostics) {
        return new CompileResponse(diagnostics.isEmpty(), diagnostics);
    }
}
