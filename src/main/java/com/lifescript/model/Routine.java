package com.lifescript.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Routine {
    private String name;
    private LocalTime startTime;
    // Days the routine repeats on. Mandatory in LifeScript, so never empty after validation
    // (unlike Task, where empty means once).
    private List<DayOfWeek> repeatDays = new ArrayList<>();
    private List<Activity> activities = new ArrayList<>();

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

    // Total of all activities: this is how long the routine lasts.
    public Duration getDuration() {
        return activities.stream().map(Activity::getDuration).reduce(Duration.ZERO, Duration::plus);
    }

    // From the start time until the activities are done. A routine ending exactly at
    // midnight gets an end of 00:00, which TimeRange treats as end of day.
    public TimeRange getTimeRange() {
        TimeRange range = new TimeRange();
        range.setStartTime(startTime);
        range.setEndTime(startTime.plus(getDuration()));
        return range;
    }
}
