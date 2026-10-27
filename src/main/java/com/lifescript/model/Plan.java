package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Plan {
    private String name;
    private PlanPeriod period;
    private Map<String, TimeRange> timeSettings = new HashMap<>();
    // Where each settings entry was written; only the ones in the file (defaults have none).
    private Map<String, SourceLocation> timeSettingLocations = new HashMap<>();
    private Map<DayOfWeek, List<TimeRange>> availability = new EnumMap<>(DayOfWeek.class);
    // Where each day's availability line was written; only days listed in the file.
    private Map<DayOfWeek, SourceLocation> availabilityLocations = new EnumMap<>(DayOfWeek.class);
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

    public PlanPeriod getPeriod() {
        return period;
    }

    public void setPeriod(PlanPeriod period) {
        this.period = period;
    }

    public Map<String, TimeRange> getTimeSettings() {
        return timeSettings;
    }

    public void setTimeSettings(Map<String, TimeRange> timeSettings) {
        this.timeSettings = timeSettings;
    }

    public Map<String, SourceLocation> getTimeSettingLocations() {
        return timeSettingLocations;
    }

    public void setTimeSettingLocations(Map<String, SourceLocation> timeSettingLocations) {
        this.timeSettingLocations = timeSettingLocations;
    }

    public Map<DayOfWeek, List<TimeRange>> getAvailability() {
        return availability;
    }

    public void setAvailability(Map<DayOfWeek, List<TimeRange>> availability) {
        this.availability = availability;
    }

    public Map<DayOfWeek, SourceLocation> getAvailabilityLocations() {
        return availabilityLocations;
    }

    public void setAvailabilityLocations(Map<DayOfWeek, SourceLocation> availabilityLocations) {
        this.availabilityLocations = availabilityLocations;
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
