package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutineValidationTest {

    @Test
    void routine_missingMandatoryFields_reportsErrors() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                routines:
                routine: r
                repeats: daily

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("missing mandatory property: time")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("missing mandatory property: activities")));
    }

    @Test
    void routine_missingRepeats_reportsError() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: r
                time: 07:00
                activities:
                stretch: 15m

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Routine r is missing mandatory property: repeats")));
    }

    @Test
    void routine_runningPastMidnight_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: late
                time: 23:30
                repeats: daily
                activities:
                read: 45m
                stretch: 20m

                routine: until_midnight
                time: 23:00
                repeats: daily
                activities:
                read: 1h

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e ->
                e.contains("Routine late starting at 23:30 lasts 1h5m and would run past midnight")), () -> "Got: " + errors);
        assertFalse(errors.stream().anyMatch(e -> e.contains("until_midnight")), () -> "Got: " + errors);
    }

    @Test
    void routine_invalidStartTime_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: r
                time: 25:00
                repeats: daily
                activities:
                stretch: 15m

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid time '25:00'")), () -> "Got: " + errors);
        assertFalse(errors.stream().anyMatch(e -> e.contains("past midnight")));
    }

    @Test
    void duplicateDayInRepeats_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: r
                time: 07:00
                repeats: monday, wednesday, monday
                activities:
                stretch: 15m

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                repeats: friday, friday
                """;

        List<String> errors = errors(plan);

        assertEquals(2, errors.stream().filter(e -> e.contains("Duplicate day in repeats 'monday'")
                || e.contains("Duplicate day in repeats 'friday'")).count(), () -> "Got: " + errors);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Duplicate day in repeats 'wednesday'")));
    }
}
