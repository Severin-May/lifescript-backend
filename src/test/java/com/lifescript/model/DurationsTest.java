package com.lifescript.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationsTest {

    @Test
    void format_writesDurationsTheLifeScriptWay() {
        assertEquals("20m", Durations.format(Duration.ofMinutes(20)));
        assertEquals("2h", Durations.format(Duration.ofHours(2)));
        assertEquals("1h35m", Durations.format(Duration.ofMinutes(95)));
        assertEquals("0m", Durations.format(Duration.ZERO));
    }
}
