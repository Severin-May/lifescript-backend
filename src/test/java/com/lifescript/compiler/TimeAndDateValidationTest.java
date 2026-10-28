package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeAndDateValidationTest {

    @Test
    void invalidDates_areReported() {
        String plan = """
                plan: p
                period: 2024-13-40 to 2024-02-01

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                deadline: 2024-02-30 17:00
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid date '2024-13-40'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid deadline date '2024-02-30'")));
    }

    @Test
    void invalidTimes_areReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

                availability:
                monday: 09:00-25:00
                tuesday: 09:61-10:00
                wednesday: 08:00-24:00

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid time '25:00'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid time '09:61'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid time '24:00'")));
        assertFalse(errors.stream().anyMatch(e -> e.contains("Syntax error")));
    }

    @Test
    void midnightBoundaries_areAccepted() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

                availability:
                monday: 22:00-00:00
                tuesday: 00:00-08:00
                wednesday: 00:00-23:59

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void midnightToMidnight_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

                availability:
                monday: 00:00-00:00

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e ->
                e.contains("Start time '00:00' must be before end time '00:00'")));
        assertFalse(errors.stream().anyMatch(e -> e.contains("to go past midnight")));
    }

    @Test
    void overnightRange_isReportedWithMidnightHint() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

                availability:
                monday: 23:00-02:00

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e ->
                e.contains("Start time '23:00' must be before end time '02:00' (to go past midnight, end at 00:00 and continue on the next day)")));
    }

    @Test
    void invalidTimeRange_startNotBeforeEnd_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

                settings:
                morning: 12:00-08:00

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e ->
                e.contains("Start time '12:00' must be before end time '08:00'")));
    }

    private static String planWithPeriod(String period) {
        return """
                plan: p
                period: %s

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """.formatted(period);
    }

    @Test
    void period_longerThanAWeek_isReported() {
        assertEquals(List.of("Line 2: Period can be at most 7 days (this one is 8 days)"),
                errors(planWithPeriod("2026-10-05 to 2026-10-12")));
    }

    @Test
    void period_ofSevenDays_orOneDay_isFine() {
        assertEquals(List.of(), errors(planWithPeriod("2026-10-05 to 2026-10-11")));
        assertEquals(List.of(), errors(planWithPeriod("2026-10-05 to 2026-10-05")));
    }

    @Test
    void period_endingBeforeItStarts_isReported() {
        assertEquals(List.of("Line 2: Start date '2026-10-07' must not be after end date '2026-10-05'"),
                errors(planWithPeriod("2026-10-07 to 2026-10-05")));
    }
}
