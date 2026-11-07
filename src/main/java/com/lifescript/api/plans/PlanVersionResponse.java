package com.lifescript.api.plans;

import com.lifescript.storage.SavedPlanVersion;

import java.time.Instant;

public record PlanVersionResponse(long version, String name, String content, boolean valid, Instant savedAt) {

    static PlanVersionResponse from(SavedPlanVersion saved) {
        return new PlanVersionResponse(saved.getVersion(), saved.getName(), saved.getContent(), saved.isValid(),
                saved.getSavedAt());
    }
}
