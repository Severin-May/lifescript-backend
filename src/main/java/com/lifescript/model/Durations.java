package com.lifescript.model;

import java.time.Duration;

// Shared by the compiler and the schedulers, so their messages write durations the same way.
public final class Durations {

    private Durations() {
    }

    // 95 minutes -> "1h35m", the way durations are written in LifeScript
    public static String format(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours == 0) return minutes + "m";
        return minutes == 0 ? hours + "h" : hours + "h" + minutes + "m";
    }
}
