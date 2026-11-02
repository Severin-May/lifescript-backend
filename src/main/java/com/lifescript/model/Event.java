package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

import java.time.LocalDate;

// note: null if not given.
public record Event(String name, LocalDate date, TimeRange timeRange, String note, SourceLocation location)
        implements FixedCommitment {

    @Override
    public boolean occursOn(LocalDate date) {
        return date.equals(this.date);
    }
}
