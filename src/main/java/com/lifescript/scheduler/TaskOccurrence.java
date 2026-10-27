package com.lifescript.scheduler;

import com.lifescript.model.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;

// date: the only day a repeating copy may go on; null for a one-off task.
// End limits: null = none; they include limits passed down from dependents.
public record TaskOccurrence(Task task, LocalDate date, LocalDateTime preferredEnd, LocalDateTime hardEnd) {

    public String name() {
        return task.getName();
    }
}
