package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.scheduler.Schedule;

import java.util.List;

// schedule is null when there are diagnostics.
public record CompileResult(List<Diagnostic> diagnostics, Schedule schedule) {

    public static CompileResult failed(List<Diagnostic> diagnostics) {
        return new CompileResult(diagnostics, null);
    }
}
