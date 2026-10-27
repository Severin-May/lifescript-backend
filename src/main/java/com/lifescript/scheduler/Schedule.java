package com.lifescript.scheduler;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record Schedule(Map<LocalDate, List<ScheduleEntry>> days, List<UnscheduledTask> unscheduled,
                       List<ScheduleNote> notes) {

    // Stored sorted and unmodifiable, whatever order entries were added in.
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

    public List<ScheduleEntry> entriesOn(LocalDate date) {
        return days.getOrDefault(date, List.of());
    }
}
