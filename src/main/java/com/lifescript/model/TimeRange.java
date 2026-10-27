package com.lifescript.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public record TimeRange(LocalTime startTime, LocalTime endTime) {

    private static final int SECONDS_PER_DAY = 24 * 60 * 60;

    // An end of 00:00 means midnight at the end of the day.
    public boolean endsAtMidnight() {
        return LocalTime.MIDNIGHT.equals(endTime);
    }

    // 00:00-00:00 is not ordered.
    public boolean isOrdered() {
        if (endsAtMidnight()) {
            return !LocalTime.MIDNIGHT.equals(startTime);
        }
        return startTime.isBefore(endTime);
    }

    public boolean contains(LocalTime time) {
        if (time.isBefore(startTime)) {
            return false;
        }
        return endsAtMidnight() || time.isBefore(endTime);
    }

    // Touching ranges don't overlap.
    public boolean overlaps(TimeRange other) {
        return startsBeforeEndOf(other) && other.startsBeforeEndOf(this);
    }

    private boolean startsBeforeEndOf(TimeRange other) {
        return other.endsAtMidnight() || startTime.isBefore(other.endTime);
    }

    // Only meaningful when the ranges overlap.
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

    // Clipped to the day.
    public TimeRange paddedBy(Duration padding) {
        int pad = (int) padding.toSeconds();
        int start = Math.max(0, startTime.toSecondOfDay() - pad);
        int end = Math.min(SECONDS_PER_DAY, endSecondOfDay() + pad);
        return ofSeconds(start, end);
    }

    // An end of 86400 (24:00) becomes 00:00.
    public static TimeRange ofSeconds(int startSecond, int endSecond) {
        return new TimeRange(LocalTime.ofSecondOfDay(startSecond), LocalTime.ofSecondOfDay(endSecond % SECONDS_PER_DAY));
    }

    // An end of 00:00 counts as 24:00.
    public int endSecondOfDay() {
        return endsAtMidnight() ? SECONDS_PER_DAY : endTime.toSecondOfDay();
    }

    // Only meaningful for ordered ranges.
    public Duration duration() {
        Duration duration = Duration.between(startTime, endTime);
        return endsAtMidnight() ? duration.plusDays(1) : duration;
    }

    @Override
    public String toString() {
        return startTime + "-" + endTime;
    }
}
