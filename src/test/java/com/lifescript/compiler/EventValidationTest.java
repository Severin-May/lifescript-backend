package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventValidationTest {

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
}
