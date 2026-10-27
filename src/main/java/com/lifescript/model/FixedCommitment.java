package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

import java.time.LocalDate;

public interface FixedCommitment {

    String getName();

    TimeRange getTimeRange();

    boolean occursOn(LocalDate date);

    SourceLocation getLocation();
}
