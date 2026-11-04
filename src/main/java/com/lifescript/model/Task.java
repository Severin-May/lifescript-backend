package com.lifescript.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// deadline and note: null if not given. repeatDays empty = happens once.
// breakTime: the rest after this task, replacing the plan's break there; null = the plan's break.
public record Task(String name, Duration duration, Priority priority, EnergyLevel effort, LocalDateTime deadline,
                   List<DayOfWeek> repeatDays, List<String> dependencies, String note, Duration breakTime) {

    public Task {
        repeatDays = List.copyOf(repeatDays);
        dependencies = List.copyOf(dependencies);
    }
}
