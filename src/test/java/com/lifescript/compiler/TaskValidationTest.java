package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskValidationTest {

    @Test
    void task_missingMandatoryFields_reportsErrors() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

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
    void deadlineBeforePeriodStart_isReported() {
        String plan = """
                plan: p
                period: 2024-01-10 to 2024-01-16

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
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-10 to 2024-01-16

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
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
    void circularDependency_isDetected() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
    void duplicateTaskName_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
    void repeatingTask_withDeadline_isReported() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
}
