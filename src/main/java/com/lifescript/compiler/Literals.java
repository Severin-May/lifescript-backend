package com.lifescript.compiler;

import com.lifescript.model.EnergyLevel;
import com.lifescript.model.Priority;
import com.lifescript.model.TimeRange;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

public final class Literals {

    private Literals() {
    }

    // Throws IllegalArgumentException for values too large to represent.
    public static Duration parseDuration(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        try {
            int hIndex = value.indexOf('h');

            if (hIndex < 0) {
                long minutes = Long.parseLong(value.substring(0, value.length() - 1));
                return Duration.ofMinutes(minutes);
            }

            long hours = Long.parseLong(value.substring(0, hIndex));
            Duration duration = Duration.ofHours(hours);

            String rest = value.substring(hIndex + 1);
            if (!rest.isEmpty()) {
                long minutes = Long.parseLong(rest.substring(0, rest.length() - 1));
                duration = duration.plusMinutes(minutes);
            }

            return duration;
        } catch (ArithmeticException e) {
            // Fits in a long but overflows Duration.
            throw new IllegalArgumentException("Duration too large: " + text, e);
        }
    }

    public static LocalDate parseDate(String text) {
        return LocalDate.parse(text);
    }

    public static LocalTime parseTime(String text) {
        return LocalTime.parse(text);
    }

    public static TimeRange parseTimeRange(String text) {
        int dash = text.indexOf('-');
        return new TimeRange(parseTime(text.substring(0, dash)), parseTime(text.substring(dash + 1)));
    }

    public static String parseString(String text) {
        return text.substring(1, text.length() - 1);
    }

    // "Morning" -> "morning", the key time settings are stored under
    public static String parseNamedPeriod(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    public static DayOfWeek parseDay(String text) {
        return DayOfWeek.valueOf(text.toUpperCase());
    }

    public static EnergyLevel parseEnergyLevel(String text) {
        return EnergyLevel.valueOf(text.toUpperCase());
    }

    public static Priority parsePriority(String text) {
        return Priority.valueOf(text.toUpperCase());
    }
}
