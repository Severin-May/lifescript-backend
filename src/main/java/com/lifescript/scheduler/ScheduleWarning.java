package com.lifescript.scheduler;

import java.time.LocalDate;

public record ScheduleWarning(String name, LocalDate date, String message) {
}
