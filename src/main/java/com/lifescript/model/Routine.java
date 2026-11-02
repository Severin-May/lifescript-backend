package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// repeatDays is never empty after validation: repeats is mandatory for routines.
public record Routine(String name, LocalTime startTime, List<DayOfWeek> repeatDays, List<Activity> activities,
                      SourceLocation location) implements FixedCommitment {

    public Routine {
        repeatDays = List.copyOf(repeatDays);
        activities = List.copyOf(activities);
    }

    public Duration duration() {
        return activities.stream().map(Activity::duration).reduce(Duration.ZERO, Duration::plus);
    }

    @Override
    public boolean occursOn(LocalDate date) {
        return repeatDays.contains(date.getDayOfWeek());
    }

    // Ending exactly at midnight gives 00:00, which TimeRange reads as end of day.
    @Override
    public TimeRange timeRange() {
        return new TimeRange(startTime, startTime.plus(duration()));
    }
}
