package com.lifescript.api;

import com.lifescript.storage.SavedPlan;

import java.time.Instant;
import java.util.UUID;

public record PlanResponse(UUID id, String name, String content, boolean valid, long version,
                           Instant createdAt, Instant updatedAt) {

    static PlanResponse from(SavedPlan plan) {
        return new PlanResponse(plan.getId(), plan.getName(), plan.getContent(), plan.isValid(), plan.getVersion(),
                plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
