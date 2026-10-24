package com.lifescript.scheduler.greedy;

import com.lifescript.model.Plan;
import com.lifescript.scheduler.SchedulerSettings;
import com.lifescript.scheduler.TaskOccurrence;
import com.lifescript.scheduler.TaskOccurrences;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.build;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskOrderTest {

    // A plan whose tasks section is `tasks`; 2026-10-05 is a Monday.
    private static List<String> order(String tasks) {
        Plan plan = build("""
                plan: p
                period: 2026-10-05 to 2026-10-11

                availability:
                monday: flexible

                tasks:
                %s
                """.formatted(tasks));
        return TaskOrder.of(TaskOccurrences.of(plan, SchedulerSettings.DEFAULTS)).stream()
                .map(o -> o.date() == null ? o.name() : o.name() + "@" + o.date().getDayOfWeek())
                .toList();
    }

    @Test
    void earliestDeadlineFirst_tasksWithoutDeadlineLast() {
        assertEquals(List.of("soon", "later", "none"), order("""
                task: none
                duration: 1h
                priority: critical
                effort: low

                task: later
                duration: 1h
                priority: low
                effort: low
                deadline: 2026-10-09 17:00

                task: soon
                duration: 1h
                priority: low
                effort: low
                deadline: 2026-10-06 17:00
                """));
    }

    @Test
    void sameDeadline_higherPriorityFirst_thenFileOrder() {
        assertEquals(List.of("important", "first_low", "second_low"), order("""
                task: first_low
                duration: 1h
                priority: low
                effort: low
                deadline: 2026-10-08 12:00

                task: important
                duration: 1h
                priority: critical
                effort: low
                deadline: 2026-10-08 12:00

                task: second_low
                duration: 1h
                priority: low
                effort: low
                deadline: 2026-10-08 12:00
                """));
    }

    @Test
    void dependencyComesFirst_evenWithLowerPriorityAndNoDeadlines() {
        // No deadlines at all, so nothing passes down; the explicit dependency rule decides.
        assertEquals(List.of("other", "prepare", "present"), order("""
                task: present
                duration: 1h
                priority: critical
                effort: low
                dependencies: prepare

                task: other
                duration: 1h
                priority: high
                effort: low

                task: prepare
                duration: 1h
                priority: low
                effort: low
                """));
    }

    @Test
    void repeatingCopies_competeWithTasksDueOnTheirDay() {
        // daily_review's monday copy is due by the end of monday, before a task due tuesday 17:00;
        // its tuesday copy (end of tuesday) comes after that task.
        List<String> order = order("""
                task: report
                duration: 1h
                priority: low
                effort: low
                deadline: 2026-10-06 17:00

                task: daily_review
                duration: 15m
                priority: low
                effort: low
                repeats: monday, tuesday
                """);

        assertEquals(List.of("daily_review@MONDAY", "report", "daily_review@TUESDAY"), order);
    }
}
