package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanStructureValidationTest {

    @Test
    void plan_missingTasksSection_reportsError() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
    void eventsSection_isNotMistakenForTasks() {
        // Regression: sectionKind used to label every unknown section as "tasks".
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

                events:
                event: e1
                date: 2024-01-03
                time: 10:00-11:00

                events:
                event: e2
                date: 2024-01-04
                time: 10:00-11:00
                """;

        List<String> errors = errors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate section 'events'")), () -> "Got: " + errors);
        assertFalse(errors.stream().anyMatch(e -> e.contains("Duplicate section 'tasks'")), () -> "Got: " + errors);
    }
}
