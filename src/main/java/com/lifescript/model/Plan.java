package com.lifescript.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class Plan {
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Map<String, TimeRange> timeSettings;
    private Map<DayOfWeek, List<TimeRange>> availability;
    private Map<DayOfWeek, Map<TimeRange, EnergyLevel>> energyProfile;
    private List<Task> tasks;
    private List<Routine> routines;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Map<String, TimeRange> getTimeSettings() {
        return timeSettings;
    }

    public void setTimeSettings(Map<String, TimeRange> timeSettings) {
        this.timeSettings = timeSettings;
    }

    public Map<DayOfWeek, List<TimeRange>> getAvailability() {
        return availability;
    }

    public void setAvailability(Map<DayOfWeek, List<TimeRange>> availability) {
        this.availability = availability;
    }

    public Map<DayOfWeek, Map<TimeRange, EnergyLevel>> getEnergyProfile() {
        return energyProfile;
    }

    public void setEnergyProfile(Map<DayOfWeek, Map<TimeRange, EnergyLevel>> energyProfile) {
        this.energyProfile = energyProfile;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public List<Routine> getRoutines() {
        return routines;
    }

    public void setRoutines(List<Routine> routines) {
        this.routines = routines;
    }
}
