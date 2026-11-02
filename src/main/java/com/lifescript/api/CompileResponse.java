package com.lifescript.api;

import com.lifescript.compiler.CompileResult;
import com.lifescript.planning.PlanningResult;
import com.lifescript.diagnostics.Diagnostic;

import java.util.List;

public record CompileResponse(boolean valid, List<Diagnostic> diagnostics, ScheduleResponse schedule) {

    // Validation only, so never a schedule.
    public static CompileResponse of(CompileResult result) {
        return new CompileResponse(result.diagnostics().isEmpty(), result.diagnostics(), null);
    }

    public static CompileResponse of(PlanningResult result) {
        ScheduleResponse schedule = result.schedule() == null ? null : ScheduleResponse.from(result.schedule());
        return new CompileResponse(result.diagnostics().isEmpty(), result.diagnostics(), schedule);
    }
}
