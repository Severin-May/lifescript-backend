package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

import java.time.LocalDate;

public interface FixedCommitment {

    String name();

    TimeRange timeRange();

    boolean occursOn(LocalDate date);

    SourceLocation location();
}
