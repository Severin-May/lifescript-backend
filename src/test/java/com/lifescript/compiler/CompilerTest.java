package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerTest {

    private final Compiler compiler = new Compiler();

    // Loads a LifeScript file from src/test/resources/lifescript/.
    private String load(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/lifescript/" + name)) {
            if (in == null) throw new IOException("Test resource not found: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void validPlan_hasNoErrors() throws IOException {
        List<String> errors = compiler.fullCompile(load("fullPlan.ls"));

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void task_missingMandatoryFields_reportsErrors() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                tasks:
                task: a
                note: "just a note"
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("missing mandatory property: duration")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("missing mandatory property: priority")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("missing mandatory property: effort")));
    }

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

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("missing mandatory property: time")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("missing mandatory property: activities")));
    }

    @Test
    void plan_missingTasksSection_reportsError() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                settings:
                morning: 08:00-12:00
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("missing mandatory property: tasks")));
    }

    @Test
    void plan_missingAvailabilitySection_reportsError() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("missing mandatory property: availability")));
    }

    @Test
    void duplicateSection_isFlaggedAndContentsSkipped() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high

                tasks:
                task: b
                deadline: 2024-13-40
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("duplicate 'tasks' section")));
        assertFalse(errors.stream().anyMatch(e -> e.contains("Invalid deadline date")),
                () -> "Contents of the duplicate section should be skipped, but got: " + errors);
    }

    @Test
    void duplicateKeys_areReportedAcrossSections() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                settings:
                morning: 08:00-12:00
                morning: 09:00-10:00

                availability:
                monday: flexible
                monday: 09:00-12:00

                energy profile:
                default:
                morning: high
                default:
                afternoon: low

                routines:
                routine: r1
                time: morning
                time: evening
                activities:
                stretch: 15m
                stretch: 10m

                routine: r1
                time: evening
                activities:
                jog: 20m

                tasks:
                task: a
                duration: 1h
                duration: 2h
                priority: high
                effort: high
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate settings entry 'morning'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate availability day 'monday'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate energy profile entry 'default'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate routine name 'r1'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate activity name 'stretch'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate routine property 'time'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate task property 'duration'")));
    }

    @Test
    void circularDependency_isDetected() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                dependencies: b

                task: b
                duration: 1h
                priority: high
                effort: high
                dependencies: a
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Circular dependency detected")));
    }

    @Test
    void nonExistingDependency_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                dependencies: ghost
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Task a has non-existing dependency task ghost")));
    }

    @Test
    void duplicateTaskName_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high

                task: a
                duration: 2h
                priority: low
                effort: low
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate task name a")));
    }

    @Test
    void availabilityDayOutsidePeriod_isReported() {
        // 2024-01-01 is a Monday, so the period covers Monday to Wednesday only.
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-03

                availability:
                monday: flexible
                friday: 09:00-17:00

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Availability day of week 'friday' must be within the period range")));
        assertFalse(errors.stream().anyMatch(e -> e.contains("'monday' must be within the period range")));
    }

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
                deadline: 2024-02-30
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid date '2024-13-40'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid deadline date '2024-02-30'")));
    }

    @Test
    void invalidTimes_areReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: 09:00-25:00
                tuesday: 09:61-10:00

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                start: 24:00
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid time '25:00'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid time '09:61'")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid time '24:00'")));
        assertFalse(errors.stream().anyMatch(e -> e.contains("Syntax error")));
    }

    @Test
    void midnightBoundaries_areAccepted() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: 22:00-00:00
                tuesday: 00:00-08:00
                wednesday: 00:00-23:59

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                start: 00:00
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void midnightToMidnight_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: 00:00-00:00

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e ->
                e.contains("Start time '00:00' must be before end time '00:00'")));
        assertFalse(errors.stream().anyMatch(e -> e.contains("to go past midnight")));
    }

    @Test
    void overnightRange_isReportedWithMidnightHint() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: 23:00-02:00

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e ->
                e.contains("Start time '23:00' must be before end time '02:00' (to go past midnight, end at 00:00 and continue on the next day)")));
    }

    @Test
    void invalidTimeRange_startNotBeforeEnd_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                settings:
                morning: 12:00-08:00

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = compiler.fullCompile(plan);

        assertTrue(errors.stream().anyMatch(e ->
                e.contains("Start time '12:00' must be before end time '08:00'")));
    }

    @Test
    void syntaxError_isReported() {
        String plan = """
                plan p
                period: 2024-01-01 to 2024-01-31

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> syntaxErrors = compiler.syntaxValidate(plan);
        assertFalse(syntaxErrors.isEmpty());
        assertTrue(syntaxErrors.stream().anyMatch(e -> e.contains("Syntax error")));

        List<String> fullCompileErrors = compiler.fullCompile(plan);
        assertEquals(syntaxErrors, fullCompileErrors,
                "fullCompile should short-circuit on syntax errors without running semantic checks");
    }
}
