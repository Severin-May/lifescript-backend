package com.lifescript.scheduler;

import java.time.LocalDate;

// date: null for a one-off task. note: the plan's note, null if none.
public record UnscheduledTask(String name, LocalDate date, String reason, String note) {
}
