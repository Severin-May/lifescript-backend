package com.lifescript.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

public class TimeRange {
    private LocalTime startTime;
    private LocalTime endTime;

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    // "00:00" as an end time means midnight at the end of the day, e.g. 22:00-00:00.
    public boolean endsAtMidnight() {
        return LocalTime.MIDNIGHT.equals(endTime);
    }

    // Start strictly before end, treating an end of 00:00 as end of day.
    // 00:00-00:00 is not ordered (a full day is written 00:00-23:59).
    public boolean isOrdered() {
        if (endsAtMidnight()) {
            return !LocalTime.MIDNIGHT.equals(startTime);
        }
        return startTime.isBefore(endTime);
    }

    // Only meaningful for ordered ranges: 22:00-00:00 is 2h, not -22h.
    public Duration duration() {
        Duration duration = Duration.between(startTime, endTime);
        return endsAtMidnight() ? duration.plusDays(1) : duration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeRange)) return false;
        TimeRange other = (TimeRange) o;
        return Objects.equals(startTime, other.startTime) && Objects.equals(endTime, other.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime);
    }
}
