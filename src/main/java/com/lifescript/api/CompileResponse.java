package com.lifescript.api;

import com.lifescript.compiler.CompileResult;
import com.lifescript.diagnostics.Diagnostic;

import java.util.List;

public record CompileResponse(boolean valid, List<Diagnostic> diagnostics, ScheduleResponse schedule) {

    public static CompileResponse of(List<Diagnostic> diagnostics) {
        return new CompileResponse(diagnostics.isEmpty(), diagnostics, null);
    }

    public static CompileResponse of(CompileResult result) {
        ScheduleResponse schedule = result.schedule() == null ? null : ScheduleResponse.from(result.schedule());
        return new CompileResponse(result.diagnostics().isEmpty(), result.diagnostics(), schedule);
    }
}
