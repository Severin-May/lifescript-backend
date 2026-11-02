package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.model.Plan;

import java.util.List;

// plan is null when there are diagnostics.
public record CompileResult(List<Diagnostic> diagnostics, Plan plan) {

    public static CompileResult failed(List<Diagnostic> diagnostics) {
        return new CompileResult(diagnostics, null);
    }
}
