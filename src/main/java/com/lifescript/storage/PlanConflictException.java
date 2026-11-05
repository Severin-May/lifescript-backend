package com.lifescript.storage;

import java.util.UUID;

// The plan was saved elsewhere since the client loaded it.
public class PlanConflictException extends RuntimeException {

    public PlanConflictException(UUID id, long expectedVersion) {
        super("Plan " + id + " was changed since version " + expectedVersion);
    }
}
