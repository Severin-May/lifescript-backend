package com.lifescript.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeRangeTest {

    private static TimeRange range(String start, String end) {
        return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
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
    void intersection_partialContainedAndIdentical() {
        assertEquals(range("11:00", "12:00"), range("09:00", "12:00").intersection(range("11:00", "13:00")));
        assertEquals(range("11:00", "12:00"), range("11:00", "13:00").intersection(range("09:00", "12:00")));
        assertEquals(range("10:00", "11:00"), range("09:00", "17:00").intersection(range("10:00", "11:00")));
        assertEquals(range("09:00", "12:00"), range("09:00", "12:00").intersection(range("09:00", "12:00")));
    }

    @Test
    void intersection_endingAtMidnight() {
        // 00:00 as an end is the latest end.
        assertEquals(range("23:00", "23:30"), range("22:00", "00:00").intersection(range("23:00", "23:30")));
        assertEquals(range("23:00", "23:30"), range("23:00", "23:30").intersection(range("22:00", "00:00")));
        assertEquals(range("22:00", "00:00"), range("20:00", "00:00").intersection(range("22:00", "00:00")));
    }

    @Test
    void minus_noOverlapOrTouching_leavesRangeUnchanged() {
        assertEquals(List.of(range("09:00", "12:00")), range("09:00", "12:00").minus(range("13:00", "14:00")));
        assertEquals(List.of(range("09:00", "12:00")), range("09:00", "12:00").minus(range("12:00", "14:00")));
    }

    @Test
    void minus_cutsStartMiddleOrEnd() {
        // middle: two pieces
        assertEquals(List.of(range("08:00", "10:00"), range("10:30", "22:00")),
                range("08:00", "22:00").minus(range("10:00", "10:30")));
        // start
        assertEquals(List.of(range("10:00", "12:00")), range("09:00", "12:00").minus(range("08:00", "10:00")));
        // end
        assertEquals(List.of(range("09:00", "11:00")), range("09:00", "12:00").minus(range("11:00", "13:00")));
    }

    @Test
    void minus_coveredCompletely_leavesNothing() {
        assertEquals(List.of(), range("09:00", "12:00").minus(range("09:00", "12:00")));
        assertEquals(List.of(), range("09:00", "12:00").minus(range("08:00", "13:00")));
    }

    @Test
    void minus_endingAtMidnight() {
        // 00:00 as an end is end of day.
        assertEquals(List.of(range("22:00", "23:00"), range("23:30", "00:00")),
                range("22:00", "00:00").minus(range("23:00", "23:30")));
        assertEquals(List.of(range("20:00", "22:00")), range("20:00", "00:00").minus(range("22:00", "00:00")));
        assertEquals(List.of(), range("22:00", "23:00").minus(range("21:00", "00:00")));
    }

    @Test
    void ofSeconds_fromSecondsSinceMidnight_24hBecomesEndOfDay() {
        assertEquals(range("09:00", "10:30"), TimeRange.ofSeconds(9 * 3600, 10 * 3600 + 1800));
        assertEquals(range("22:00", "00:00"), TimeRange.ofSeconds(22 * 3600, 24 * 3600));
    }

    @Test
    void paddedBy_widensBothSides_withinTheDay() {
        assertEquals(range("09:50", "11:10"), range("10:00", "11:00").paddedBy(Duration.ofMinutes(10)));
        // clipped at the start and end of the day; an end of 00:00 stays end of day
        assertEquals(range("00:00", "00:40"), range("00:05", "00:30").paddedBy(Duration.ofMinutes(10)));
        assertEquals(range("23:20", "00:00"), range("23:30", "23:55").paddedBy(Duration.ofMinutes(10)));
        assertEquals(range("21:50", "00:00"), range("22:00", "00:00").paddedBy(Duration.ofMinutes(10)));
        assertEquals(range("10:00", "11:00"), range("10:00", "11:00").paddedBy(Duration.ZERO));
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
