package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

import java.time.LocalDate;

// Something at a fixed time that always happens: a routine or an event.
// The scheduler places tasks around these, and they ignore availability.
public interface FixedCommitment {

    String getName();

    TimeRange getTimeRange();

    boolean occursOn(LocalDate date);

    // Where it was written in the plan, for reporting problems with it.
    SourceLocation getLocation();
}
