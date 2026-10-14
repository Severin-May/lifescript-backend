package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs the full pipeline, so each input must pass SemanticValidator first;
// the errors checked here come from PlanModelValidator.
class PlanModelValidatorTest {

    private final Compiler compiler = new Compiler();

    // 2026-10-05 is a Monday, so the default period covers one full week.
    private List<String> compile(String period, String sections) {
        String plan = """
                plan: p
                period: %s

                %s

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """.formatted(period, sections);
        return compiler.fullCompile(plan);
    }

    private List<String> compile(String sections) {
        return compile("2026-10-05 to 2026-10-11", sections);
    }

    private static void assertNoErrors(List<String> errors) {
        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    // --- settings ---

    @Test
    void settings_outOfOrder_isReported() {
        List<String> errors = compile("""
                settings:
                morning: 06:00-13:00
                evening: 16:00-22:00

                availability:
                monday: 09:00-10:00
                """);

        assertEquals(List.of(
                "Settings: morning (06:00-13:00) must end before afternoon starts (12:00)",
                "Settings: afternoon (12:00-18:00) must end before evening starts (16:00)"), errors);
    }

    @Test
    void settings_touchingPeriods_areFine() {
        assertNoErrors(compile("""
                settings:
                morning: 06:00-12:00
                afternoon: 12:00-18:00
                evening: 18:00-00:00

                availability:
                monday: flexible
                """));
    }

    // --- availability ---

    @Test
    void availability_overlap_isReported() {
        // A line is either named periods or time ranges (not mixed), so overlaps come from time ranges;
        // named periods can only overlap if settings do, which checkSettingsOrder reports.
        List<String> errors = compile("""
                availability:
                monday: 09:00-12:00, 10:00-13:00, 08:00-17:00
                tuesday: 09:00-12:00, 12:00-14:00
                """);

        // ranges are sorted by start time; tuesday's ranges only touch, so only monday is reported
        assertEquals(List.of(
                "Availability on monday: 08:00-17:00 overlaps 09:00-12:00",
                "Availability on monday: 08:00-17:00 overlaps 10:00-13:00",
                "Availability on monday: 09:00-12:00 overlaps 10:00-13:00"), errors);
    }

    // --- energy profile ---

    @Test
    void energy_overlapWithinBlock_isReported() {
        List<String> errors = compile("""
                availability:
                monday: flexible

                energy profile:
                default:
                morning: high
                09:00-10:00: low
                friday:
                20:00-00:00: low
                23:00-23:30: moderate
                """);

        assertEquals(List.of(
                "Energy profile (default): 06:00-12:00 overlaps 09:00-10:00",
                "Energy profile (friday): 20:00-00:00 overlaps 23:00-23:30"), errors);
    }

    @Test
    void energy_dayBlockOverlappingDefault_isFine() {
        assertNoErrors(compile("""
                availability:
                monday: flexible

                energy profile:
                default:
                morning: high
                friday:
                09:00-10:00: low
                """));
    }

    // --- routines ---

    @Test
    void routines_overlap_isReportedOncePerPair_withSharedDays() {
        List<String> errors = compile("""
                availability:
                monday: flexible

                routines:
                routine: morning_routine
                time: 07:00
                repeats: daily
                activities:
                stretch: 15m
                shower: 20m

                routine: gym
                time: 07:15
                repeats: monday, wednesday
                activities:
                workout: 45m

                routine: reading
                time: 07:35
                repeats: daily
                activities:
                read: 30m
                """);

        // gym overlaps both others on its two days; reading only touches morning_routine (07:35)
        assertEquals(List.of(
                "Routine morning_routine (07:00-07:35) overlaps routine gym (07:15-08:00) on monday, wednesday",
                "Routine gym (07:15-08:00) overlaps routine reading (07:35-08:05) on monday, wednesday"), errors);
    }

    @Test
    void routines_overlapOnDayOutsidePeriod_isNotReported() {
        // Monday to Wednesday only: the two routines would only clash on friday.
        List<String> errors = compile("2026-10-05 to 2026-10-07", """
                availability:
                monday: flexible

                routines:
                routine: a
                time: 09:00
                repeats: monday, friday
                activities:
                x: 1h

                routine: b
                time: 09:30
                repeats: friday
                activities:
                y: 1h
                """);

        assertNoErrors(errors);
    }

    // --- events ---

    @Test
    void event_overlappingRoutineOnThatDay_isReported() {
        // 2026-10-07 is a Wednesday.
        List<String> errors = compile("""
                availability:
                monday: flexible

                routines:
                routine: team_sync
                time: 10:00
                repeats: monday, wednesday
                activities:
                standup: 30m

                events:
                event: dentist
                date: 2026-10-07
                time: 10:00-11:00

                event: lunch
                date: 2026-10-08
                time: 10:00-11:00
                """);

        // lunch is on a Thursday, when team_sync doesn't happen
        assertEquals(List.of(
                "Event dentist (10:00-11:00) on 2026-10-07 overlaps routine team_sync (10:00-10:30)"), errors);
    }

    @Test
    void events_overlappingOnSameDate_isReported() {
        List<String> errors = compile("""
                availability:
                monday: flexible

                events:
                event: dentist
                date: 2026-10-07
                time: 10:00-11:00

                event: call
                date: 2026-10-07
                time: 10:30-11:30

                event: dinner
                date: 2026-10-08
                time: 10:30-11:30
                """);

        assertEquals(List.of(
                "Event dentist (10:00-11:00) overlaps event call (10:30-11:30) on 2026-10-07"), errors);
    }

    @Test
    void fullPlan_hasNoModelErrors() throws Exception {
        try (var in = getClass().getResourceAsStream("/lifescript/fullPlan.ls")) {
            assertNoErrors(compiler.fullCompile(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
        }
    }
}
