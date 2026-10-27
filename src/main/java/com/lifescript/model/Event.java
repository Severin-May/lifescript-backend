package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

import java.time.LocalDate;

// A one-off commitment at a fixed date and time. Like a routine, it always happens
// and ignores availability; unlike a routine, it does not repeat.
public class Event implements FixedCommitment {
    private String name;
    // Where the "event:" line was written.
    private SourceLocation location;
    private LocalDate date;
    private TimeRange timeRange;
    private String note;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    @Override
    public TimeRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(TimeRange timeRange) {
        this.timeRange = timeRange;
    }

    @Override
    public boolean occursOn(LocalDate date) {
        return date.equals(this.date);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }
}
