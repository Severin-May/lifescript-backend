package com.lifescript.scheduler;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// The result of scheduling: a calendar of entries per date, the tasks that didn't fit,
// and notes about tasks that did fit but the user should know about.
public record Schedule(Map<LocalDate, List<ScheduleEntry>> days, List<UnscheduledTask> unscheduled,
                       List<ScheduleNote> notes) {

    // Always stores a sorted, unmodifiable copy: dates in order, and each day's entries by start
    // time, whatever order the algorithm added them in.
    public Schedule {
        Map<LocalDate, List<ScheduleEntry>> sorted = new TreeMap<>();
        for (Map.Entry<LocalDate, List<ScheduleEntry>> day : days.entrySet()) {
            List<ScheduleEntry> entries = new ArrayList<>(day.getValue());
            entries.sort(Comparator.comparing(entry -> entry.timeRange().startTime()));
            sorted.put(day.getKey(), List.copyOf(entries));
        }
        days = Collections.unmodifiableMap(sorted);
        unscheduled = List.copyOf(unscheduled);
        notes = List.copyOf(notes);
    }

    // The entries on one date, in time order (empty if the date has none).
    public List<ScheduleEntry> entriesOn(LocalDate date) {
        return days.getOrDefault(date, List.of());
    }
}
