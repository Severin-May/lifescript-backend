package com.lifescript.storage;

import java.util.UUID;

// Also thrown for another owner's plan, so ids of other people's plans can't be discovered.
public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(UUID id) {
        super("Plan " + id + " not found");
    }
}
