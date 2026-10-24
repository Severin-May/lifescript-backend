package com.lifescript.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// Immutable, so one instance can safely be shared (e.g. the "morning" range from settings is used
// by availability, energy and events) and used as a map key. equals/hashCode compare start and end.
public record TimeRange(LocalTime startTime, LocalTime endTime) {

    private static final int SECONDS_PER_DAY = 24 * 60 * 60;

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

    // The part both ranges cover: 09:00-12:00 and 11:00-13:00 share 11:00-12:00.
    // Only meaningful when the ranges overlap. An end of 00:00 (end of day) is the latest possible end.
    public TimeRange intersection(TimeRange other) {
        LocalTime start = startTime.isAfter(other.startTime) ? startTime : other.startTime;
        LocalTime end;
        if (endsAtMidnight()) {
            end = other.endTime;
        } else if (other.endsAtMidnight()) {
            end = endTime;
        } else {
            end = endTime.isBefore(other.endTime) ? endTime : other.endTime;
        }
        return new TimeRange(start, end);
    }

    // This range with `other` cut out: 0, 1 or 2 pieces, in time order.
    // 08:00-22:00 minus 10:00-10:30 = 08:00-10:00 and 10:30-22:00. Touching ranges don't cut anything.
    public List<TimeRange> minus(TimeRange other) {
        if (!overlaps(other)) {
            return List.of(this);
        }
        List<TimeRange> pieces = new ArrayList<>(2);
        if (startTime.isBefore(other.startTime)) {
            pieces.add(new TimeRange(startTime, other.startTime));
        }
        if (other.endSecondOfDay() < endSecondOfDay()) {
            pieces.add(new TimeRange(other.endTime, endTime));
        }
        return pieces;
    }

    // This range widened by `padding` on both sides, but not past the start or end of the day.
    // 10:00-11:00 padded by 10m = 09:50-11:10; 23:30-23:55 padded by 10m = 23:20-00:00 (end of day).
    public TimeRange paddedBy(Duration padding) {
        int pad = (int) padding.toSeconds();
        int start = Math.max(0, startTime.toSecondOfDay() - pad);
        int end = Math.min(SECONDS_PER_DAY, endSecondOfDay() + pad);
        return ofSeconds(start, end);
    }

    // A range from seconds since midnight, e.g. (32400, 37800) = 09:00-10:30. An end of 86400 (24:00)
    // becomes 00:00, which means end of day. The counterpart of startTime().toSecondOfDay() and endSecondOfDay().
    public static TimeRange ofSeconds(int startSecond, int endSecond) {
        return new TimeRange(LocalTime.ofSecondOfDay(startSecond), LocalTime.ofSecondOfDay(endSecond % SECONDS_PER_DAY));
    }

    // The end as seconds since the start of the day, with an end of 00:00 counted as 24:00.
    public int endSecondOfDay() {
        return endsAtMidnight() ? SECONDS_PER_DAY : endTime.toSecondOfDay();
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
