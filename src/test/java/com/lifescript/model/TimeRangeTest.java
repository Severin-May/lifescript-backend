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
    void contains_includesStart_excludesEnd() {
        TimeRange r = range("09:00", "12:00");
        assertTrue(r.contains(LocalTime.parse("09:00")));
        assertTrue(r.contains(LocalTime.parse("11:59")));
        assertFalse(r.contains(LocalTime.parse("12:00")));
        assertFalse(r.contains(LocalTime.parse("08:59")));
    }

    @Test
    void contains_endingAtMidnight_coversRestOfDay() {
        TimeRange r = range("22:00", "00:00");
        assertTrue(r.contains(LocalTime.parse("22:00")));
        assertTrue(r.contains(LocalTime.parse("23:59")));
        assertFalse(r.contains(LocalTime.parse("21:59")));
        assertFalse(r.contains(LocalTime.parse("00:00")));
    }

    @Test
    void overlaps_partialAndContained() {
        assertTrue(range("09:00", "12:00").overlaps(range("11:00", "13:00")));
        assertTrue(range("11:00", "13:00").overlaps(range("09:00", "12:00")));   // symmetric
        assertTrue(range("09:00", "17:00").overlaps(range("10:00", "11:00")));   // contained
        assertTrue(range("09:00", "12:00").overlaps(range("09:00", "12:00")));   // identical
    }

    @Test
    void overlaps_touchingOrApart_isFalse() {
        assertFalse(range("09:00", "12:00").overlaps(range("12:00", "14:00")));
        assertFalse(range("12:00", "14:00").overlaps(range("09:00", "12:00")));
        assertFalse(range("09:00", "10:00").overlaps(range("11:00", "12:00")));
    }

    @Test
    void overlaps_endingAtMidnight() {
        assertTrue(range("22:00", "00:00").overlaps(range("23:00", "23:30")));
        assertTrue(range("23:00", "23:30").overlaps(range("22:00", "00:00")));
        assertTrue(range("20:00", "00:00").overlaps(range("22:00", "00:00")));
        assertFalse(range("20:00", "22:00").overlaps(range("22:00", "00:00")));   // touching at 22:00
        assertFalse(range("00:00", "08:00").overlaps(range("22:00", "00:00")));   // start of day vs end of day
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
