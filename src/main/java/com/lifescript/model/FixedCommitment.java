package com.lifescript.model;

import java.time.LocalDate;

// Something at a fixed time that always happens: a routine or an event.
// The scheduler places tasks around these, and they ignore availability.
public interface FixedCommitment {

    String getName();

    TimeRange getTimeRange();

    boolean occursOn(LocalDate date);
}
