package com.lifescript.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class Task {

    private String name;
    private int duration;
    private Priority priority;
    private EnergyLevel effort;
    private LocalDate deadline;
    private LocalTime start;
    private List<DayOfWeek> repeatDays;
    private List<String> dependencies;
    private String note;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
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

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public LocalTime getStart() {
        return start;
    }

    public void setStart(LocalTime start) {
        this.start = start;
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
