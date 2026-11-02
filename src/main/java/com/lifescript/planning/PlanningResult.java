package com.lifescript.planning;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.model.Plan;
import com.lifescript.scheduler.Schedule;

import java.util.List;

// plan and schedule are null when there are diagnostics.
public record PlanningResult(List<Diagnostic> diagnostics, Plan plan, Schedule schedule) {

    public static PlanningResult failed(List<Diagnostic> diagnostics) {
        return new PlanningResult(diagnostics, null, null);
    }
}
