package com.lifescript.scheduler;

import java.time.LocalDate;

// date: null for a one-off task.
public record UnscheduledTask(String name, LocalDate date, String reason) {
}
