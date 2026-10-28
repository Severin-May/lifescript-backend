package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationValidationTest {

    @Test
    void taskDuration_tooLargeToRead_isReported() {
        // Used to crash with NumberFormatException instead of reporting an error.
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 99999999999999999999m
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertEquals(List.of("Line 9: Duration '99999999999999999999m' is too long (at most 24h)"), errors);
    }

    @Test
    void taskDuration_overOneDay_isReported_butExactlyOneDayIsFine() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 24h1m
                priority: high
                effort: high

                task: b
                duration: 24h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertEquals(List.of("Line 9: Duration '24h1m' is too long (at most 24h)"), errors);
    }

    @Test
    void durationMinutesAfterHours_over59_isReported_butMinutesOnTheirOwnAreFine() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h75m
                priority: high
                effort: high

                task: b
                duration: 1h59m
                priority: high
                effort: high

                task: c
                duration: 90m
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertEquals(List.of("Line 9: Minutes in '1h75m' must be at most 59 (write 2h15m)"), errors);
    }

    @Test
    void zeroDuration_isReported_forTasksAndActivities() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: r1
                time: 07:00
                repeats: daily
                activities:
                stretch: 0h
                jog: 20m

                tasks:
                task: a
                duration: 0m
                priority: high
                effort: high

                task: b
                duration: 0h30m
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertEquals(List.of(
                "Line 12: Duration '0h' must be greater than zero",
                "Line 17: Duration '0m' must be greater than zero"), errors);
    }

    @Test
    void activityDuration_tooLarge_isReported_withoutPastMidnightError() {
        // The past-midnight check is skipped: the oversized activity is the real problem.
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: r1
                time: 07:00
                repeats: daily
                activities:
                stretch: 15m
                jog: 9999999999999999h

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertEquals(List.of("Line 13: Duration '9999999999999999h' is too long (at most 24h)"), errors);
    }
}
