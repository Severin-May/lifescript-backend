package com.lifescript.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Task {

    private String name;
    private Duration duration;
    private Priority priority;
    private EnergyLevel effort;
    // null = no deadline
    private LocalDateTime deadline;
    // Empty = happens once.
    private List<DayOfWeek> repeatDays = new ArrayList<>();
    private List<String> dependencies = new ArrayList<>();
    private String note;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public EnergyLevel getEffort() {
        return effort;
    }

    public void setEffort(EnergyLevel effort) {
        this.effort = effort;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public List<DayOfWeek> getRepeatDays() {
        return repeatDays;
    }

    public void setRepeatDays(List<DayOfWeek> repeatDays) {
        this.repeatDays = repeatDays;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
