package com.lifescript.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Plan {
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Map<String, TimeRange> timeSettings = new HashMap<>();
    private Map<DayOfWeek, List<TimeRange>> availability = new EnumMap<>(DayOfWeek.class);
    // Lists, not maps keyed by range: every line of the file is kept, so two lines covering the
    // same time stay visible to PlanModelValidator instead of one silently replacing the other.
    private List<EnergyEntry> defaultEnergyProfile = new ArrayList<>();
    private Map<DayOfWeek, List<EnergyEntry>> energyProfileOverrides = new EnumMap<>(DayOfWeek.class);
    private List<Task> tasks = new ArrayList<>();
    private List<Routine> routines = new ArrayList<>();
    private List<Event> events = new ArrayList<>();

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

    public List<EnergyEntry> getDefaultEnergyProfile() {
        return defaultEnergyProfile;
    }

    public void setDefaultEnergyProfile(List<EnergyEntry> defaultEnergyProfile) {
        this.defaultEnergyProfile = defaultEnergyProfile;
    }

    public Map<DayOfWeek, List<EnergyEntry>> getEnergyProfileOverrides() {
        return energyProfileOverrides;
    }

    public void setEnergyProfileOverrides(Map<DayOfWeek, List<EnergyEntry>> energyProfileOverrides) {
        this.energyProfileOverrides = energyProfileOverrides;
    }

    // Energy at a given moment: the day's own block wins, then the default block,
    // then MODERATE when no entry covers the time (or there is no energy profile).
    public EnergyLevel energyAt(DayOfWeek day, LocalTime time) {
        EnergyLevel level = levelAt(energyProfileOverrides.getOrDefault(day, List.of()), time);
        if (level != null) {
            return level;
        }
        level = levelAt(defaultEnergyProfile, time);
        return level != null ? level : EnergyLevel.MODERATE;
    }

    private static EnergyLevel levelAt(List<EnergyEntry> entries, LocalTime time) {
        for (EnergyEntry entry : entries) {
            if (entry.range().contains(time)) {
                return entry.level();
            }
        }
        return null;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public List<Routine> getRoutines() {
        return routines;
    }

    public void setRoutines(List<Routine> routines) {
        this.routines = routines;
    }
}
