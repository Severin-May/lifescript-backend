package com.lifescript.model;

import java.time.Duration;
import java.time.LocalTime;

// Immutable, so one instance can safely be shared (e.g. the "morning" range from settings is used
// by availability, energy and events) and used as a map key. equals/hashCode compare start and end.
public record TimeRange(LocalTime startTime, LocalTime endTime) {

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

    // start <= time < end, with an end of 00:00 meaning end of day (22:00-00:00 contains 23:30).
    public boolean contains(LocalTime time) {
        if (time.isBefore(startTime)) {
            return false;
        }
        return endsAtMidnight() || time.isBefore(endTime);
    }

    // True if the two ranges share any time. Ranges that only touch (09:00-12:00 and 12:00-14:00) don't overlap.
    // An end of 00:00 means end of day, so 22:00-00:00 overlaps 23:00-23:30.
    public boolean overlaps(TimeRange other) {
        return startsBeforeEndOf(other) && other.startsBeforeEndOf(this);
    }

    private boolean startsBeforeEndOf(TimeRange other) {
        return other.endsAtMidnight() || startTime.isBefore(other.endTime);
    }

    // Only meaningful for ordered ranges: 22:00-00:00 is 2h, not -22h.
    public Duration duration() {
        Duration duration = Duration.between(startTime, endTime);
        return endsAtMidnight() ? duration.plusDays(1) : duration;
    }

    // Same format as written in LifeScript, e.g. "06:00-12:00".
    @Override
    public String toString() {
        return startTime + "-" + endTime;
    }
}
