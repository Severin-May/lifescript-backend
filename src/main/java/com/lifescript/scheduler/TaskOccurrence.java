package com.lifescript.scheduler;

import com.lifescript.model.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;

// date: the only day a repeating copy may go on; null for a one-off task.
// End limits: the effective deadline with and without the buffer; null = none.
// deadlineSetBy: whose own deadline they come from (this task or one depending on it); null if none.
public record TaskOccurrence(Task task, LocalDate date, LocalDateTime preferredEnd, LocalDateTime hardEnd,
                             String deadlineSetBy) {

    public String name() {
        return task.name();
    }
}
