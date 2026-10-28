package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CaseSensitivityTest {

    private final LifeScriptCompiler compiler = new LifeScriptCompiler();

    @Test
    void keywordsInAnyCase_namesKeepTheirSpelling() {
        String plan = """
                Plan: My_Week
                PERIOD: 2026-10-05 To 2026-10-11

                Settings:
                Morning: 08:00-12:00

                Availability:
                Monday: Flexible
                TUESDAY: Morning, Evening

                Energy Profile:
                Default:
                Morning: High

                Routines:
                Routine: Walk
                Time: 07:00
                Repeats: Weekdays
                Activities:
                walk: 30M

                Tasks:
                Task: Research
                Duration: 1H30M
                Priority: Critical
                Effort: High
                Deadline: 2026-10-07 17:00

                Task: Write_Report
                Duration: 1h
                Priority: HIGH
                Effort: moderate
                Dependencies: Research

                Events:
                Event: Dentist
                Date: 2026-10-07
                Time: Afternoon
                """;

        CompileResult result = compiler.compile(plan);

        assertEquals(List.of(), result.diagnostics());
        List<String> names = result.schedule().days().values().stream()
                .flatMap(List::stream).map(entry -> entry.name()).distinct().sorted().toList();
        assertEquals(List.of("Dentist", "Research", "Walk", "Write_Report"), names);
    }

    @Test
    void keywordDuplicatesInDifferentCase_areStillDuplicates() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                settings:
                Morning: 06:00-12:00
                morning: 07:00-12:00

                availability:
                Monday: flexible
                monday: flexible

                energy profile:
                default:
                Morning: high
                morning: low

                tasks:
                task: a
                Duration: 1h
                duration: 2h
                priority: high
                effort: high
                repeats: Monday, monday
                """;

        List<String> errors = errors(plan);

        assertEquals(List.of(
                "Line 6: Duplicate settings entry 'morning'",
                "Line 10: Duplicate availability day 'monday'",
                "Line 15: Duplicate energy entry 'morning'",
                "Line 20: Duplicate task property 'duration'",
                "Line 23: Duplicate day in repeats 'monday'"), errors);
    }

    @Test
    void dependencyDifferingOnlyInCase_suggestsTheDefinedName() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: Research
                duration: 1h
                priority: high
                effort: high

                task: b
                duration: 1h
                priority: high
                effort: high
                dependencies: research
                """;

        assertEquals(List.of("Line 17: Task b has non-existing dependency task research (did you mean 'Research'?)"),
                errors(plan));
    }
}
