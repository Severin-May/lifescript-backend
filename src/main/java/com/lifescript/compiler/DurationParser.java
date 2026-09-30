package com.lifescript.compiler;

import java.time.Duration;

public final class DurationParser {

    private DurationParser() {
    }

    public static Duration parse(String durationVal) {
        int hIndex = durationVal.indexOf('h');

        if (hIndex < 0) {
            long minutes = Long.parseLong(durationVal.substring(0, durationVal.length() - 1));
            return Duration.ofMinutes(minutes);
        }

        long hours = Long.parseLong(durationVal.substring(0, hIndex));
        Duration duration = Duration.ofHours(hours);

        String rest = durationVal.substring(hIndex + 1);
        if (!rest.isEmpty()) {
            long minutes = Long.parseLong(rest.substring(0, rest.length() - 1));
            duration = duration.plusMinutes(minutes);
        }

        return duration;
    }
}
