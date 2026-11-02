package com.lifescript.compiler;

import com.lifescript.model.TimeRange;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

public final class Literals {

    private Literals() {
    }

    // A duration as written: "1h30m" has hours (1) and minutes (30), "90m" has minutes only.
    public record DurationLiteral(boolean hasHours, long hours, long minutes) {

        // Throws IllegalArgumentException for values too large to represent.
        public Duration toDuration() {
            try {
                return Duration.ofHours(hours).plusMinutes(minutes);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Duration too large: " + hours + "h" + minutes + "m", e);
            }
        }
    }

    // Throws IllegalArgumentException (NumberFormatException) for numbers too long to read.
    public static DurationLiteral parseDurationLiteral(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        int hIndex = value.indexOf('h');
        if (hIndex < 0) {
            return new DurationLiteral(false, 0, Long.parseLong(value.substring(0, value.length() - 1)));
        }
        String rest = value.substring(hIndex + 1);
        long minutes = rest.isEmpty() ? 0 : Long.parseLong(rest.substring(0, rest.length() - 1));
        return new DurationLiteral(true, Long.parseLong(value.substring(0, hIndex)), minutes);
    }

    // Throws IllegalArgumentException for values too large to represent.
    public static Duration parseDuration(String text) {
        return parseDurationLiteral(text).toDuration();
    }

    // 95 minutes -> "1h35m", the way durations are written in LifeScript
    public static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours == 0) return minutes + "m";
        return minutes == 0 ? hours + "h" : hours + "h" + minutes + "m";
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
}
