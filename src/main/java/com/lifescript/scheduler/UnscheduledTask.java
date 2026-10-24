package com.lifescript.scheduler;

import java.time.LocalDate;

// A task the scheduler could not place, and why, e.g. "no free slot long enough".
// date is the day of a repeating task's copy (e.g. inbox_zero on 2026-10-08); null for a one-off task.
public record UnscheduledTask(String name, LocalDate date, String reason) {
}
