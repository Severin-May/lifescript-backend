package com.lifescript.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static java.time.DayOfWeek.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanPeriodTest {

    // 2026-10-05 is a Monday.
    private static PlanPeriod period(String start, String end) {
        return new PlanPeriod(LocalDate.parse(start), LocalDate.parse(end));
    }

    @Test
    void contains_includesBothEnds() {
        PlanPeriod p = period("2026-10-05", "2026-10-11");
        assertTrue(p.contains(LocalDate.parse("2026-10-05")));
        assertTrue(p.contains(LocalDate.parse("2026-10-08")));
        assertTrue(p.contains(LocalDate.parse("2026-10-11")));
        assertFalse(p.contains(LocalDate.parse("2026-10-04")));
        assertFalse(p.contains(LocalDate.parse("2026-10-12")));
    }

    @Test
    void weekdays_shortPeriod_onlyTheDaysItCovers() {
        assertEquals(EnumSet.of(MONDAY, TUESDAY, WEDNESDAY), period("2026-10-05", "2026-10-07").weekdays());
        // Saturday to Monday wraps around the week.
        assertEquals(EnumSet.of(SATURDAY, SUNDAY, MONDAY), period("2026-10-10", "2026-10-12").weekdays());
    }

    @Test
    void weekdays_weekOrLonger_allSevenDays() {
        assertEquals(EnumSet.allOf(DayOfWeek.class), period("2026-10-05", "2026-10-11").weekdays());
        assertEquals(EnumSet.allOf(DayOfWeek.class), period("2026-01-01", "2026-12-31").weekdays());
    }

    @Test
    void dates_everyDateInOrder_bothEndsIncluded() {
        assertEquals(List.of(LocalDate.parse("2026-10-30"), LocalDate.parse("2026-10-31"), LocalDate.parse("2026-11-01")),
                period("2026-10-30", "2026-11-01").dates());
    }

    @Test
    void toString_sameFormatAsLifeScript() {
        assertEquals("2026-10-05 to 2026-10-11", period("2026-10-05", "2026-10-11").toString());
    }
}
