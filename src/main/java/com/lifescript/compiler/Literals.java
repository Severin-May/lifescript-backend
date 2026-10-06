package com.lifescript.compiler;

import com.lifescript.model.TimeRange;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

// Single definition of how LifeScript literal text becomes Java values.
// Shared by SemanticValidator and PlanModelBuilder so they can't drift apart.
public final class Literals {

    private Literals() {
    }

    // "1h30m", "2h", "45m"
    public static Duration parseDuration(String text) {
        int hIndex = text.indexOf('h');

        if (hIndex < 0) {
            long minutes = Long.parseLong(text.substring(0, text.length() - 1));
            return Duration.ofMinutes(minutes);
        }

        long hours = Long.parseLong(text.substring(0, hIndex));
        Duration duration = Duration.ofHours(hours);

        String rest = text.substring(hIndex + 1);
        if (!rest.isEmpty()) {
            long minutes = Long.parseLong(rest.substring(0, rest.length() - 1));
            duration = duration.plusMinutes(minutes);
        }

        return duration;
    }

    // "2026-10-05"; throws DateTimeParseException for dates that don't exist (e.g. 2024-02-30)
    public static LocalDate parseDate(String text) {
        return LocalDate.parse(text);
    }

    // "09:00"
    public static LocalTime parseTime(String text) {
        return LocalTime.parse(text);
    }

    // "09:00-12:00"
    public static TimeRange parseTimeRange(String text) {
        int dash = text.indexOf('-');
        TimeRange range = new TimeRange();
        range.setStartTime(parseTime(text.substring(0, dash)));
        range.setEndTime(parseTime(text.substring(dash + 1)));
        return range;
    }

    // "monday"
    public static DayOfWeek parseDay(String text) {
        return DayOfWeek.valueOf(text.toUpperCase());
    }
}
