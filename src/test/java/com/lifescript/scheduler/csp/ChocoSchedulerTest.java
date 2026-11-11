package com.lifescript.scheduler.csp;

import com.lifescript.model.Plan;
import com.lifescript.model.TimeRange;
import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleEntry;
import com.lifescript.scheduler.SchedulerSettings;
import com.lifescript.scheduler.UnscheduledTask;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChocoSchedulerTest {

    private static final LocalDate MON = LocalDate.parse("2026-10-05");
    private static final LocalDateTime NOW = MON.atStartOfDay();

    private static Plan monday(String availability, String tasks) {
        return build("""
                plan: p
                period: 2026-10-05 to 2026-10-05

                availability:
                monday: %s

                tasks:
                %s
                """.formatted(availability, tasks));
    }

    private static List<String> taskNames(Schedule schedule) {
        return schedule.entriesOn(MON).stream().filter(e -> e.type() == EntryType.TASK).map(ScheduleEntry::name).toList();
    }

    private static ScheduleEntry task(String name, String start, String end) {
        return new ScheduleEntry(EntryType.TASK, name, new TimeRange(LocalTime.parse(start), LocalTime.parse(end)), null);
    }

    @Test
    void fullPlan_everyTaskIsPlaced_andProvenOptimal() throws IOException {
        Plan plan = build(load("fullPlan.ls"));
        ChocoScheduler choco = new ChocoScheduler();

        CspSolution solution = choco.solve(CspProblem.of(plan, NOW, SchedulerSettings.DEFAULTS));

        assertTrue(solution.optimal());
        assertEquals(List.of(), choco.schedule(plan, NOW).unscheduled());
    }

    @Test
    void whereGreedyBlocksItself_chocoPlacesBoth() {
        // Greedy puts 'a' first at 09:00 (high priority, earliest), leaving no 2h gap for 'b'.
        // Moving 'a' to 12:00 makes room, which only a scheduler that looks at both at once can see.
        Plan plan = monday("09:00-11:00, 12:00-13:00", """
                task: a
                duration: 1h
                priority: high
                effort: moderate

                task: b
                duration: 2h
                priority: medium
                effort: moderate
                """);

        assertEquals(List.of("b"), new GreedyScheduler().schedule(plan, NOW).unscheduled().stream()
                .map(UnscheduledTask::name).toList());
        Schedule choco = new ChocoScheduler().schedule(plan, NOW);
        assertEquals(List.of(), choco.unscheduled());
        assertEquals(List.of(task("b", "09:00", "11:00"), task("a", "12:00", "13:00")), choco.entriesOn(MON));
    }

    private static Plan mondayWithEnergy(String availability, String energy, String tasks) {
        return build("""
                plan: p
                period: 2026-10-05 to 2026-10-05

                availability:
                monday: %s

                energy profile:
                default:
                %s

                tasks:
                %s
                """.formatted(availability, energy, tasks));
    }

    @Test
    void energy_isMatchedForAllTasksTogether() {
        // 'x' (moderate effort) is equally off at 09:00 (high) and 10:00 (low), so greedy takes the earlier one,
        // the only good hour for 'y' (high effort). Looking at both, 'y' gets the high hour.
        Plan plan = mondayWithEnergy("09:00-11:00", "09:00-10:00: high\n10:00-11:00: low", """
                task: x
                duration: 1h
                priority: critical
                effort: moderate

                task: y
                duration: 1h
                priority: high
                effort: high
                """);

        assertEquals(List.of(task("x", "09:00", "10:00"), task("y", "10:00", "11:00")),
                new GreedyScheduler().schedule(plan, NOW).entriesOn(MON));
        assertEquals(List.of(task("y", "09:00", "10:00"), task("x", "10:00", "11:00")),
                new ChocoScheduler().schedule(plan, NOW).entriesOn(MON));
    }

    @Test
    void endingBeforeTheDeadlineBuffer_beatsABetterEnergyMatch() {
        // The only high-energy hour ends at 16:00, inside the 2h buffer before the 17:00 deadline.
        Plan plan = mondayWithEnergy("09:00-17:00", "09:00-15:00: low\n15:00-16:00: high\n16:00-17:00: low", """
                task: focus
                duration: 1h
                priority: high
                effort: high
                deadline: 2026-10-05 17:00
                """);

        Schedule schedule = new ChocoScheduler().schedule(plan, NOW);

        assertEquals(List.of(task("focus", "09:00", "10:00")), schedule.entriesOn(MON));
        assertEquals(List.of(), schedule.warnings());
    }

    @Test
    void endingInsideTheBuffer_whenNothingElseFits_isWarned_likeGreedy() {
        Plan plan = monday("09:00-12:00", """
                task: report
                duration: 2h
                priority: high
                effort: moderate
                deadline: 2026-10-05 11:30
                """);

        Schedule choco = new ChocoScheduler().schedule(plan, NOW);

        assertEquals(List.of(task("report", "09:00", "11:00")), choco.entriesOn(MON));
        assertEquals(new GreedyScheduler().schedule(plan, NOW).warnings(), choco.warnings());
        assertEquals("ends at 11:00, only 30m before its deadline at 11:30", choco.warnings().get(0).message());
    }

    @Test
    void strictPriorities_oneCriticalTaskBeatsTwoHighOnes() {
        Plan plan = monday("09:00-11:00", """
                task: big
                duration: 2h
                priority: critical
                effort: low

                task: small_1
                duration: 1h
                priority: high
                effort: low

                task: small_2
                duration: 1h
                priority: high
                effort: low
                """);

        Schedule schedule = new ChocoScheduler().schedule(plan, NOW);

        assertEquals(List.of("big"), taskNames(schedule));
        assertEquals(List.of(
                        new UnscheduledTask("small_1", null, "didn't fit together with the other tasks", null),
                        new UnscheduledTask("small_2", null, "didn't fit together with the other tasks", null)),
                schedule.unscheduled());
    }

    @Test
    void unscheduledTasks_sayWhy_likeTheGreedyScheduler() {
        Plan plan = monday("09:00-11:00", """
                task: huge
                duration: 3h
                priority: high
                effort: low

                task: after_huge
                duration: 1h
                priority: high
                effort: low
                dependencies: huge
                """);

        assertEquals(List.of(
                        new UnscheduledTask("huge", null, "no free slot long enough in the period", null),
                        new UnscheduledTask("after_huge", null, "depends on 'huge', which couldn't be scheduled", null)),
                new ChocoScheduler().schedule(plan, NOW).unscheduled());
    }
}
