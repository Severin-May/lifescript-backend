package com.lifescript.scheduler;

import com.lifescript.model.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;

// One thing to place: a one-off task, or one copy of a repeating task on one date.
//   date          - the only date a repeating task's copy may go on; null for a one-off task
//   preferredEnd  - should end by then (deadline minus the buffer); null = no limit
//   hardEnd       - must end by then (the deadline itself); null = no limit
// Both ends already include limits passed down from tasks that depend on this one.
public record TaskOccurrence(Task task, LocalDate date, LocalDateTime preferredEnd, LocalDateTime hardEnd) {

    public String name() {
        return task.getName();
    }
}
