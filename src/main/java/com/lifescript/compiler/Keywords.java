package com.lifescript.compiler;

import com.lifescript.model.EnergyLevel;
import com.lifescript.model.Priority;

import java.time.DayOfWeek;
import java.util.Locale;

// Keywords that stand for values (days, energy levels, priorities, named periods), in both directions.
// Keywords match in any case, so their text is compared and stored lowercased.
public final class Keywords {

    private Keywords() {
    }

    public static String normalize(String keyword) {
        return keyword.toLowerCase(Locale.ROOT);
    }

    // "Morning" -> "morning", the key time settings are stored under
    public static String parseNamedPeriod(String text) {
        return normalize(text);
    }

    public static DayOfWeek parseDay(String text) {
        return DayOfWeek.valueOf(text.toUpperCase(Locale.ROOT));
    }

    public static EnergyLevel parseEnergyLevel(String text) {
        return EnergyLevel.valueOf(text.toUpperCase(Locale.ROOT));
    }

    public static Priority parsePriority(String text) {
        return Priority.valueOf(text.toUpperCase(Locale.ROOT));
    }

    public static String format(DayOfWeek day) {
        return normalize(day.name());
    }

    public static String format(EnergyLevel level) {
        return normalize(level.name());
    }
}
