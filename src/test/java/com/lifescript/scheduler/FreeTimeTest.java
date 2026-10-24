package com.lifescript.scheduler;

import com.lifescript.model.Plan;
import com.lifescript.model.TimeRange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.load;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FreeTimeTest {

    private static TimeRange range(String start, String end) {
        return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
    }

    private static LocalDate date(String text) {
        return LocalDate.parse(text);
    }

    @Test
    void fullPlan_availabilityMinusRoutinesAndEvents_withoutBreaks() throws IOException {
        // fullPlan.ls: 2026-10-05 (Mon) to 2026-10-11 (Sun); morning 08:00-12:00, afternoon 13:00-17:00,
        // evening 18:00-22:00. Routines: morning_routine 07:00-07:35 daily (outside all availability),
        // evening_walk 19:00-19:45 weekdays, team_sync 10:00-10:30 mon+wed.
        // Events: dentist wed 14:00-15:00 (outside availability), team_offsite fri 13:00-17:00.
        Plan plan = build(load("fullPlan.ls"));

        Map<LocalDate, List<TimeRange>> free = FreeTime.of(plan, Duration.ZERO);

        assertEquals(List.of(range("08:00", "10:00"), range("10:30", "19:00"), range("19:45", "22:00")),
                free.get(date("2026-10-05")));   // mon: flexible minus team_sync and evening_walk
        assertEquals(List.of(range("09:00", "12:00"), range("14:00", "18:00")),
                free.get(date("2026-10-06")));   // tue: evening_walk is outside availability
        assertEquals(List.of(range("08:00", "10:00"), range("10:30", "12:00"),
                        range("18:00", "19:00"), range("19:45", "22:00")),
                free.get(date("2026-10-07")));   // wed: morning, evening minus team_sync and evening_walk
        assertEquals(List.of(range("13:00", "17:00")),
                free.get(date("2026-10-08")));   // thu: afternoon, nothing in it
        assertEquals(List.of(range("09:00", "13:00")),
                free.get(date("2026-10-09")));   // fri: team_offsite takes the afternoon
        assertEquals(List.of(), free.get(date("2026-10-10")));   // sat: not available
        assertEquals(List.of(), free.get(date("2026-10-11")));   // sun: not available
        assertEquals(7, free.size());
    }

    @Test
    void fullPlan_withBreaks_routinesAndEventsTakeTenMinutesOnEachSide() throws IOException {
        Plan plan = build(load("fullPlan.ls"));

        Map<LocalDate, List<TimeRange>> free = FreeTime.of(plan, Duration.ofMinutes(10));

        // mon: team_sync 10:00-10:30 takes 09:50-10:40, evening_walk 19:00-19:45 takes 18:50-19:55
        assertEquals(List.of(range("08:00", "09:50"), range("10:40", "18:50"), range("19:55", "22:00")),
                free.get(date("2026-10-05")));
        // fri: team_offsite 13:00-17:00 takes 12:50-17:10
        assertEquals(List.of(range("09:00", "12:50")), free.get(date("2026-10-09")));
        // tue: evening_walk 18:50-19:55 with its break is still outside 14:00-18:00
        assertEquals(List.of(range("09:00", "12:00"), range("14:00", "18:00")), free.get(date("2026-10-06")));
    }

    @Test
    void minus_cutsATakenRangeOutOfADaysFreeTime() {
        List<TimeRange> free = List.of(range("08:00", "12:00"), range("14:00", "18:00"));

        assertEquals(List.of(range("08:00", "09:00"), range("11:00", "12:00"), range("14:00", "18:00")),
                FreeTime.minus(free, range("09:00", "11:00")));
    }
}
