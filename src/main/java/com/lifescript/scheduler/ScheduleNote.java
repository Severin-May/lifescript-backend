package com.lifescript.scheduler;

import java.time.LocalDate;

// Something the user should know about a task that was scheduled, e.g. that it only fit by ending
// less than 2h before its deadline. date is the day the task was placed on.
public record ScheduleNote(String name, LocalDate date, String message) {
}
