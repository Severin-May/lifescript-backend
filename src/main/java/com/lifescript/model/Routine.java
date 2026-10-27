package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Routine implements FixedCommitment {
    private String name;
    private SourceLocation location;
    private LocalTime startTime;
    // Never empty after validation: repeats is mandatory for routines.
    private List<DayOfWeek> repeatDays = new ArrayList<>();
    private List<Activity> activities = new ArrayList<>();

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<DayOfWeek> getRepeatDays() {
        return repeatDays;
    }

    public void setRepeatDays(List<DayOfWeek> repeatDays) {
        this.repeatDays = repeatDays;
    }

    public List<Activity> getActivities() {
        return activities;
    }

    public void setActivities(List<Activity> activities) {
        this.activities = activities;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public Duration getDuration() {
        return activities.stream().map(Activity::getDuration).reduce(Duration.ZERO, Duration::plus);
    }

    @Override
    public boolean occursOn(LocalDate date) {
        return repeatDays.contains(date.getDayOfWeek());
    }

    // Ending exactly at midnight gives 00:00, which TimeRange reads as end of day.
    @Override
    public TimeRange getTimeRange() {
        return new TimeRange(startTime, startTime.plus(getDuration()));
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }
}
