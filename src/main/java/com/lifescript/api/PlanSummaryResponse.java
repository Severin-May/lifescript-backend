package com.lifescript.api;

import com.lifescript.storage.SavedPlan;

import java.time.Instant;
import java.util.UUID;

// For the list of plans: everything but the text.
public record PlanSummaryResponse(UUID id, String name, boolean valid, long version, Instant updatedAt) {

    static PlanSummaryResponse from(SavedPlan plan) {
        return new PlanSummaryResponse(plan.getId(), plan.getName(), plan.isValid(), plan.getVersion(), plan.getUpdatedAt());
    }
}
