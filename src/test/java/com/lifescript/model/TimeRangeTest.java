package com.lifescript.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeRangeTest {

    private static TimeRange range(String start, String end) {
        TimeRange range = new TimeRange();
        range.setStartTime(LocalTime.parse(start));
        range.setEndTime(LocalTime.parse(end));
        return range;
    }

    @Test
    void regularRange() {
        TimeRange r = range("09:00", "12:30");
        assertTrue(r.isOrdered());
        assertFalse(r.endsAtMidnight());
        assertEquals(Duration.ofMinutes(210), r.duration());
    }

    @Test
    void endingAtMidnight_meansEndOfDay() {
        TimeRange r = range("22:00", "00:00");
        assertTrue(r.isOrdered());
        assertTrue(r.endsAtMidnight());
        assertEquals(Duration.ofHours(2), r.duration());
    }

    @Test
    void startingAtMidnight_isStartOfDay() {
        TimeRange r = range("00:00", "08:00");
        assertTrue(r.isOrdered());
        assertFalse(r.endsAtMidnight());
        assertEquals(Duration.ofHours(8), r.duration());
    }

    @Test
    void almostFullDay() {
        TimeRange r = range("00:00", "23:59");
        assertTrue(r.isOrdered());
        assertEquals(Duration.ofMinutes(23 * 60 + 59), r.duration());
    }

    @Test
    void midnightToMidnight_isNotOrdered() {
        assertFalse(range("00:00", "00:00").isOrdered());
    }

    @Test
    void reversedOrEqual_isNotOrdered() {
        assertFalse(range("12:00", "08:00").isOrdered());
        assertFalse(range("09:00", "09:00").isOrdered());
        assertFalse(range("22:00", "02:00").isOrdered());
    }
}
