package com.lifescript.scheduler;

import com.lifescript.model.Plan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class TaskOccurrencesTest {

    private static LocalDateTime at(String text) {
        return LocalDateTime.parse(text);
    }

    private static TaskOccurrence find(List<TaskOccurrence> occurrences, String name) {
        return occurrences.stream().filter(o -> o.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void fullPlan_oneOffTasksOnce_repeatingTaskOncePerMatchingDate() throws IOException {
        Plan plan = build(load("fullPlan.ls"));

        List<TaskOccurrence> occurrences = TaskOccurrences.of(plan, SchedulerSettings.DEFAULTS);

        // research, write_report, then inbox_zero (weekdays) on mon-fri of 2026-10-05..11
        assertEquals(List.of("research", "write_report",
                        "inbox_zero", "inbox_zero", "inbox_zero", "inbox_zero", "inbox_zero"),
                occurrences.stream().map(TaskOccurrence::name).toList());
        assertNull(occurrences.get(0).date());
        assertEquals(LocalDate.parse("2026-10-05"), occurrences.get(2).date());
        assertEquals(LocalDate.parse("2026-10-09"), occurrences.get(6).date());

        assertEquals(at("2026-10-06T00:00"), occurrences.get(2).preferredEnd());
        assertEquals(at("2026-10-06T00:00"), occurrences.get(2).hardEnd());
    }

    @Test
    void fullPlan_deadlineWithBuffer_andOwnDeadlineWinsWhenEarlier() throws IOException {
        Plan plan = build(load("fullPlan.ls"));
        List<TaskOccurrence> occurrences = TaskOccurrences.of(plan, SchedulerSettings.DEFAULTS);

        // write_report: deadline 2026-10-09 17:00, so preferably done by 15:00.
        TaskOccurrence writeReport = find(occurrences, "write_report");
        assertEquals(at("2026-10-09T15:00"), writeReport.preferredEnd());
        assertEquals(at("2026-10-09T17:00"), writeReport.hardEnd());

        // research's own deadline (7th) is earlier than what write_report passes down (9th).
        TaskOccurrence research = find(occurrences, "research");
        assertEquals(at("2026-10-07T15:00"), research.preferredEnd());
        assertEquals(at("2026-10-07T17:00"), research.hardEnd());
        assertEquals("research", research.deadlineSetBy());
    }

    @Test
    void layeredDependencies_areComputedQuickly() {
        // Every task depends on both tasks of the previous layer: without caching, the work doubles per layer.
        StringBuilder plan = new StringBuilder("plan: p\nperiod: 2024-01-01 to 2024-01-07\n\navailability:\nmonday: flexible\n\ntasks:\n");
        int layers = 30;
        for (int l = 0; l < layers; l++) {
            String deps = l == 0 ? "" : "dependencies: t" + (l - 1) + "a, t" + (l - 1) + "b\n";
            String deadline = l == layers - 1 ? "deadline: 2024-01-07 17:00\n" : "";
            for (String side : List.of("a", "b")) {
                plan.append("task: t").append(l).append(side)
                        .append("\nduration: 15m\npriority: low\neffort: low\n").append(deps).append(deadline).append("\n");
            }
        }

        List<TaskOccurrence> occurrences = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> TaskOccurrences.of(build(plan.toString()), SchedulerSettings.DEFAULTS));

        assertEquals(2 * layers, occurrences.size());
    }

    @Test
    void limitsPassDownAChainOfDependencies() {
        // c (30m) is due 2026-10-09 17:00 and needs b (2h), which needs a (1h). Only c has a deadline.
        Plan plan = build("""
                plan: p
                period: 2026-10-05 to 2026-10-11

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: low
                effort: low

                task: b
                duration: 2h
                priority: low
                effort: low
                dependencies: a

                task: c
                duration: 30m
                priority: low
                effort: low
                deadline: 2026-10-09 17:00
                dependencies: b

                task: unrelated
                duration: 1h
                priority: low
                effort: low
                """);

        List<TaskOccurrence> occurrences = TaskOccurrences.of(plan, SchedulerSettings.DEFAULTS);

        // preferred: c by 15:00 -> b by 14:30 (c starts then) -> a by 12:30
        // hard:      c by 17:00 -> b by 16:30                  -> a by 14:30
        assertEquals(at("2026-10-09T15:00"), find(occurrences, "c").preferredEnd());
        assertEquals(at("2026-10-09T14:30"), find(occurrences, "b").preferredEnd());
        assertEquals(at("2026-10-09T12:30"), find(occurrences, "a").preferredEnd());
        assertEquals(at("2026-10-09T17:00"), find(occurrences, "c").hardEnd());
        assertEquals(at("2026-10-09T16:30"), find(occurrences, "b").hardEnd());
        assertEquals(at("2026-10-09T14:30"), find(occurrences, "a").hardEnd());

        assertNull(find(occurrences, "unrelated").preferredEnd());
        assertNull(find(occurrences, "unrelated").hardEnd());

        // The limits come from c's deadline, whichever task they were passed down to.
        assertEquals("c", find(occurrences, "a").deadlineSetBy());
        assertEquals("c", find(occurrences, "b").deadlineSetBy());
        assertEquals("c", find(occurrences, "c").deadlineSetBy());
        assertNull(find(occurrences, "unrelated").deadlineSetBy());
    }
}
