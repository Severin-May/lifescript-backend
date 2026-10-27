package com.lifescript.scheduler;

import java.time.LocalDate;

public record ScheduleNote(String name, LocalDate date, String message) {
}
