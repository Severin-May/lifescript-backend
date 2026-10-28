package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Severity;
import com.lifescript.diagnostics.Stage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.lifescript.TestPlans.errors;
import static com.lifescript.TestPlans.syntaxErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeScriptCompilerTest {

    private final LifeScriptCompiler compiler = new LifeScriptCompiler();

    private String load(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/lifescript/" + name)) {
            if (in == null) throw new IOException("Test resource not found: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void validPlan_hasNoErrors() throws IOException {
        List<String> errors = errors(load("fullPlan.ls"));

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void syntaxError_isADiagnosticMarkingExactlyTheOffendingToken() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration 1h
                priority: high
                effort: high
                """;

        Diagnostic first = compiler.compile(plan).diagnostics().get(0);

        // line 9, columns 10-11 ("1h"); the end column is exclusive
        assertEquals(Stage.SYNTAX, first.stage());
        assertEquals(List.of(9, 10, 9, 12),
                List.of(first.startLineNumber(), first.startColumn(), first.endLineNumber(), first.endColumn()));
        assertTrue(errors(plan).get(0).startsWith("Line 9: Syntax error - "), () -> errors(plan).get(0));
    }

    @Test
    void keywordUsedAsName_isReportedAsReservedWord_atTheKeyword() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: low
                duration: 1h
                priority: high
                effort: high

                events:
                event: date
                date: 2024-01-03
                time: morning
                """;

        Diagnostic first = compiler.compile(plan).diagnostics().get(0);

        assertEquals("'low' is a reserved word and can't be used as a name", first.message());
        // line 8, columns 7-9 ("low")
        assertEquals(List.of(8, 7, 8, 10),
                List.of(first.startLineNumber(), first.startColumn(), first.endLineNumber(), first.endColumn()));
        assertTrue(errors(plan).contains("Line 14: 'date' is a reserved word and can't be used as a name"),
                () -> "Got: " + errors(plan));
    }

    @Test
    void keywordUsedAsActivityName_afterTheFirstActivity_isReportedAsReservedWord() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: r
                time: 07:00
                repeats: daily
                activities:
                stretch: 10m
                time: 30m

                routine: s
                time: 09:00
                repeats: daily
                activities:
                walk: 10m
                repeats: 15m

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        Diagnostic first = compiler.compile(plan).diagnostics().get(0);

        // the "time" of "time: 30m" on line 13
        assertEquals(List.of(13, 1, 13, 5),
                List.of(first.startLineNumber(), first.startColumn(), first.endLineNumber(), first.endColumn()));
        assertEquals(List.of(
                "Line 13: 'time' is a reserved word and can't be used as a name",
                "Line 20: 'repeats' is a reserved word and can't be used as a name"), errors(plan));
    }

    @Test
    void eventTimeWithADuration_isAnOrdinarySyntaxError() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                events:
                event: dentist
                date: 2024-01-03
                time: 30m

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        assertTrue(errors(plan).get(0).startsWith("Line 10: Syntax error - "), () -> errors(plan).get(0));
    }

    @Test
    void validationErrors_markFromEntryStartToEndOfLine_cycleAtTheClosingTasksDependencies() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 0m
                priority: high
                effort: high
                dependencies: b

                task: b
                duration: 1h
                priority: high
                effort: high
                dependencies: a
                """;

        List<Diagnostic> diagnostics = compiler.compile(plan).diagnostics();

        // "duration: 0m" on line 9 (12 characters, so end column 13)
        assertEquals(new Diagnostic(Stage.VALIDATION, Severity.ERROR, "Duration '0m' must be greater than zero",
                9, 1, 9, 13), diagnostics.get(0));
        // the cycle is closed by b, at its "dependencies: a" on line 18 (15 characters)
        assertEquals(new Diagnostic(Stage.VALIDATION, Severity.ERROR, "Circular dependency detected: a → b → a",
                18, 1, 18, 16), diagnostics.get(1));
        assertEquals(2, diagnostics.size(), () -> "Got: " + diagnostics);
    }

    @Test
    void validPlan_isScheduled() throws IOException {
        CompileResult result = compiler.compile(load("fullPlan.ls"));

        assertEquals(List.of(), result.diagnostics());
        assertEquals(7, result.schedule().days().size());
    }

    @Test
    void invalidPlan_hasNoSchedule() {
        CompileResult result = compiler.compile("plan: p\n");

        assertFalse(result.diagnostics().isEmpty());
        assertNull(result.schedule());
    }

    @Test
    void schedulerCrash_isReportedAsInternalError() throws IOException {
        LifeScriptCompiler crashing = new LifeScriptCompiler(plan -> { throw new IllegalStateException("boom"); });

        CompileResult result = crashing.compile(load("fullPlan.ls"));

        assertEquals(Stage.INTERNAL, result.diagnostics().get(0).stage());
        assertNull(result.schedule());
    }

    @Test
    void missingContent_isReportedAsEmptyPlan() {
        // A missing "content" arrives as null; this used to crash.
        assertEquals(List.of("Plan is empty"), errors(null));
    }

    @Test
    void blankContent_isReportedAsEmptyPlan() {
        assertEquals(List.of("Plan is empty"), errors(""));
        assertEquals(List.of("Plan is empty"), errors("  \n\n  "));
    }

    @Test
    void planWithoutTrailingNewline_isValid() {
        // The closing """ on the last line means no final newline.
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high""";

        List<String> errors = errors(plan);

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

        List<String> errors = errors(plan);

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
    void routine_namedPeriodAsTime_isSyntaxError() {
        // A routine's time is a start time; named periods are ranges, so they no longer fit.
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: r
                time: morning
                repeats: daily
                activities:
                stretch: 15m

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = syntaxErrors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Syntax error")), () -> "Got: " + errors);
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

    @Test
    void plan_missingTasksSection_reportsError() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                settings:
                morning: 08:00-12:00
                """;

        List<String> errors = errors(plan);

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

        List<String> errors = errors(plan);

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
                deadline: 2024-13-40 17:00
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate section 'tasks'")));
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
                time: 07:00
                time: 19:00
                activities:
                stretch: 15m
                stretch: 10m

                routine: r1
                time: 19:00
                activities:
                jog: 20m

                tasks:
                task: a
                duration: 1h
                duration: 2h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

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

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Circular dependency detected")));
    }

    @Test
    void circularDependency_reachedAgainFromAnotherTask_isReportedOnce() {
        // Used to crash: c reaches a after the a -> b -> a cycle was already found.
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
                dependencies: b

                task: b
                duration: 1h
                priority: high
                effort: high
                dependencies: a

                task: c
                duration: 1h
                priority: high
                effort: high
                dependencies: a
                """;

        List<String> errors = errors(plan);

        assertEquals(1, errors.stream().filter(e -> e.contains("Circular dependency detected")).count(),
                () -> "Got: " + errors);
    }

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

    @Test
    void deadlineBeforePeriodStart_isReported() {
        String plan = """
                plan: p
                period: 2024-01-10 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                deadline: 2024-01-09 17:00
                """;

        List<String> errors = errors(plan);

        assertEquals(List.of("Line 12: Deadline '2024-01-09 17:00' is before the period starts (2024-01-10)"), errors);
    }

    @Test
    void deadlineWithoutTime_isReported() {
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
                deadline: 2024-01-20
                """;

        List<String> errors = errors(plan);

        assertEquals(List.of("Line 12: Deadline needs a time, e.g. 'deadline: 2024-01-20 17:00'"), errors);
    }

    @Test
    void deadlineInvalidTime_isReported_togetherWithInvalidDate() {
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
                deadline: 2024-01-20 25:00

                task: b
                duration: 1h
                priority: high
                effort: high
                deadline: 2024-02-30 24:30
                """;

        List<String> errors = errors(plan);

        assertEquals(List.of(
                "Line 12: Invalid time '25:00'",
                "Line 18: Invalid time '24:30'",
                "Line 18: Invalid deadline date '2024-02-30'"), errors);
    }

    @Test
    void deadlineOnPeriodStart_orAfterPeriodEnd_isFine() {
        String plan = """
                plan: p
                period: 2024-01-10 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                deadline: 2024-01-10 17:00

                task: b
                duration: 1h
                priority: high
                effort: high
                deadline: 2024-02-15 17:00
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
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

        List<String> errors = errors(plan);

        assertEquals(List.of("Line 12: Task a has non-existing dependency task ghost"), errors);
    }

    @Test
    void duplicateDependency_isReportedOnce() {
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

                task: b
                duration: 1h
                priority: high
                effort: high
                dependencies: a, a
                """;

        assertEquals(List.of("Line 17: Duplicate dependency 'a'"), errors(plan));
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

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate task name 'a'")));
    }

    @Test
    void duplicateTask_secondCopyIsNotValidated() {
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
                deadline: 2024-02-30 17:00
                """;

        List<String> errors = errors(plan);

        // Only the duplicate is reported; the second copy's bad deadline and missing fields are not.
        assertEquals(List.of("Line 13: Duplicate task name 'a'"), errors);
    }

    @Test
    void duplicateTask_cycleThroughFirstCopy_isStillDetected() {
        // The second "a" used to overwrite the first one's dependencies, hiding the a -> b -> a cycle.
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
                dependencies: b

                task: b
                duration: 1h
                priority: high
                effort: high
                dependencies: a

                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate task name 'a'")), () -> "Got: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Circular dependency detected")), () -> "Got: " + errors);
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

        List<String> errors = errors(plan);

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
                period: 2024-01-01 to 2024-01-31

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
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void repeatingTask_withDeadline_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: inbox
                duration: 20m
                priority: low
                effort: low
                repeats: weekdays
                deadline: 2024-01-10 17:00
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Repeating task inbox cannot have a deadline")));
    }

    @Test
    void repeatingTask_withDependencies_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: research
                duration: 1h
                priority: high
                effort: high

                task: inbox
                duration: 20m
                priority: low
                effort: low
                repeats: weekdays
                dependencies: research
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Repeating task inbox cannot have dependencies")));
    }

    @Test
    void dependingOnRepeatingTask_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: report
                duration: 1h
                priority: high
                effort: high
                dependencies: inbox

                task: inbox
                duration: 20m
                priority: low
                effort: low
                repeats: weekdays
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Task report cannot depend on repeating task inbox")));
    }

    @Test
    void event_missingMandatoryFields_reportsErrors() {
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

                events:
                event: dentist
                note: "no date or time"
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Event dentist is missing mandatory property: date")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Event dentist is missing mandatory property: time")));
    }

    @Test
    void event_invalidValues_areReported() {
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

                events:
                event: outside
                date: 2024-02-15
                time: 10:00-11:00

                event: bad_date
                date: 2024-01-32
                time: 10:00-11:00

                event: backwards
                date: 2024-01-10
                time: 11:00-10:00
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e ->
                e.contains("Event date '2024-02-15' must be within the period range '2024-01-01'-'2024-01-31'")), () -> "Got: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid event date '2024-01-32'")), () -> "Got: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Start time '11:00' must be before end time '10:00'")), () -> "Got: " + errors);
    }

    @Test
    void event_duplicates_areReported() {
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

                events:
                event: dentist
                date: 2024-01-10
                date: 2024-01-11
                time: 10:00-11:00

                event: dentist
                date: 2024-01-12
                time: afternoon
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate event name 'dentist'")), () -> "Got: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate event property 'date'")), () -> "Got: " + errors);
    }

    @Test
    void eventsSection_isNotMistakenForTasks() {
        // Regression: sectionKind used to label every unknown section as "tasks".
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

                events:
                event: e1
                date: 2024-01-10
                time: 10:00-11:00

                events:
                event: e2
                date: 2024-01-11
                time: 10:00-11:00
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate section 'events'")), () -> "Got: " + errors);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Duplicate section 'tasks'")), () -> "Got: " + errors);
    }

    @Test
    void taskStart_isNoLongerSupported() {
        // Tasks are always flexible; anything at a fixed time is a routine.
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
                start: 10:00
                """;

        List<String> errors = syntaxErrors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Syntax error")), () -> "Got: " + errors);
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

        List<String> errors = errors(plan);

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

        List<String> errors = errors(plan);

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

        List<String> errors = errors(plan);

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

        List<String> syntaxErrors = syntaxErrors(plan);
        assertFalse(syntaxErrors.isEmpty());
        assertTrue(syntaxErrors.stream().anyMatch(e -> e.contains("Syntax error")));

        List<String> compileErrors = errors(plan);
        assertEquals(syntaxErrors, compileErrors,
                "compile should stop at syntax errors without running semantic checks");
    }
}
