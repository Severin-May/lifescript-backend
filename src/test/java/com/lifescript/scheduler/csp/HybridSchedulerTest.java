package com.lifescript.scheduler.csp;

import com.lifescript.model.Plan;
import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.GeneratedPlans;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleEntry;
import com.lifescript.scheduler.ScheduleMetrics;
import com.lifescript.scheduler.SchedulerSettings;
import com.lifescript.scheduler.UnscheduledTask;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridSchedulerTest {

    private static final HybridScheduler HYBRID = new HybridScheduler(SchedulerSettings.DEFAULTS, Duration.ofMillis(500));

    private static LocalDateTime startOf(Plan plan) {
        return plan.period().start().atStartOfDay();
    }

    private static List<String> nameAndDay(List<UnscheduledTask> unscheduled) {
        return unscheduled.stream().map(u -> u.name() + " " + u.date()).sorted().toList();
    }

    private static Map<LocalDate, List<ScheduleEntry>> tasksOnly(Schedule schedule) {
        return schedule.days().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                day -> day.getValue().stream().filter(e -> e.type() == EntryType.TASK).toList()));
    }

    @Test
    void greedysSchedule_convertedToCspStartsAndBack_isUnchanged() throws IOException {
        // crowded-week has greedy starts off the quarter-hour grid (e.g. 15:55 right after a task and its rest).
        Plan plan = build(load("comparison/crowded-week.ls"));
        CspProblem problem = CspProblem.of(plan, startOf(plan), SchedulerSettings.DEFAULTS);
        Schedule greedy = new GreedyScheduler().schedule(plan, startOf(plan));

        int[] starts = HybridScheduler.startsOf(problem, greedy);
        Schedule back = CspSchedules.toSchedule(problem.withExtraStarts(starts), new CspSolution(starts, false, Duration.ZERO));

        assertEquals(tasksOnly(greedy), tasksOnly(back));
        // The same tasks left out; the reasons are worded differently, so only names and days are compared.
        assertEquals(nameAndDay(greedy.unscheduled()), nameAndDay(back.unscheduled()));
    }

    @Test
    void improvesWhereGreedyBlocksItself() throws IOException {
        Plan plan = build(load("comparison/blocks-itself.ls"));

        assertEquals(List.of(), HYBRID.schedule(plan, startOf(plan)).unscheduled());
    }

    @Test
    void isNeverWorseThanGreedy_byPriority() throws IOException {
        for (String source : List.of(load("comparison/crowded-week.ls"), GeneratedPlans.week(20, 42), GeneratedPlans.week(40, 42))) {
            Plan plan = build(source);
            ScheduleMetrics greedy = ScheduleMetrics.of(plan, new GreedyScheduler().schedule(plan, startOf(plan)));
            ScheduleMetrics hybrid = ScheduleMetrics.of(plan, HYBRID.schedule(plan, startOf(plan)));

            assertTrue(hybrid.comparePlacedTo(greedy) >= 0,
                    () -> plan.name() + ": hybrid " + hybrid.placedByPriority() + " vs greedy " + greedy.placedByPriority());
        }
    }
}
