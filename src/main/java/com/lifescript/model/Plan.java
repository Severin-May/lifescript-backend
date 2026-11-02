package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// timeSettingLocations: only entries written in the file. availabilityLocations: only days listed in the file.
public record Plan(String name, PlanPeriod period,
                   Map<String, TimeRange> timeSettings, Map<String, SourceLocation> timeSettingLocations,
                   Map<DayOfWeek, List<TimeRange>> availability, Map<DayOfWeek, SourceLocation> availabilityLocations,
                   List<EnergyEntry> defaultEnergyProfile, Map<DayOfWeek, List<EnergyEntry>> energyProfileOverrides,
                   List<Task> tasks, List<Routine> routines, List<Event> events) {

    // Copied so the plan can't be changed afterwards, including the lists inside the maps.
    // Day maps stay EnumMaps (Monday to Sunday order), others HashMaps, so iteration order is unchanged.
    public Plan {
        timeSettings = Collections.unmodifiableMap(new HashMap<>(timeSettings));
        timeSettingLocations = Collections.unmodifiableMap(new HashMap<>(timeSettingLocations));
        availability = copyListsByDay(availability);
        availabilityLocations = copyByDay(availabilityLocations);
        defaultEnergyProfile = List.copyOf(defaultEnergyProfile);
        energyProfileOverrides = copyListsByDay(energyProfileOverrides);
        tasks = List.copyOf(tasks);
        routines = List.copyOf(routines);
        events = List.copyOf(events);
    }

    private static <V> Map<DayOfWeek, V> copyByDay(Map<DayOfWeek, V> byDay) {
        Map<DayOfWeek, V> copy = new EnumMap<>(DayOfWeek.class);
        copy.putAll(byDay);
        return Collections.unmodifiableMap(copy);
    }

    private static <V> Map<DayOfWeek, List<V>> copyListsByDay(Map<DayOfWeek, List<V>> byDay) {
        Map<DayOfWeek, List<V>> copy = new EnumMap<>(DayOfWeek.class);
        byDay.forEach((day, list) -> copy.put(day, List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }

    // The day's block first, then the default, then MODERATE.
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
}
