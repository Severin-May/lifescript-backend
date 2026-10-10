package com.lifescript.model;

import java.time.LocalDate;

// A one-off commitment at a fixed date and time. Like a routine, it always happens
// and ignores availability; unlike a routine, it does not repeat.
public class Event {
    private String name;
    private LocalDate date;
    private TimeRange timeRange;
    private String note;

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

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(TimeRange timeRange) {
        this.timeRange = timeRange;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
