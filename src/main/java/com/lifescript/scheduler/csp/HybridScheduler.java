package com.lifescript.scheduler.csp;

import com.lifescript.model.Plan;
import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleEntry;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.SchedulerSettings;
import com.lifescript.scheduler.TaskOccurrence;
import com.lifescript.scheduler.greedy.GreedyScheduler;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Greedy builds a schedule in milliseconds, then Choco improves it for the rest of the time limit
// (a construction heuristic followed by an improvement phase). Never worse than greedy by Choco's goals.
public class HybridScheduler implements Scheduler {

    private final SchedulerSettings settings;
    private final GreedyScheduler greedy;
    private final ChocoScheduler choco;

    public HybridScheduler() {
        this(SchedulerSettings.DEFAULTS, Duration.ofSeconds(2));
    }

    public HybridScheduler(SchedulerSettings settings, Duration timeLimit) {
        this.settings = settings;
        this.greedy = new GreedyScheduler(settings);
        this.choco = new ChocoScheduler(settings, timeLimit);
    }

    @Override
    public Schedule schedule(Plan plan, LocalDateTime now) {
        CspProblem problem = CspProblem.of(plan, now, settings);
        return CspSchedules.toSchedule(problem, solve(problem, plan, now));
    }

    public CspSolution solve(CspProblem problem, Plan plan, LocalDateTime now) {
        int[] greedyStarts = startsOf(problem, greedy.schedule(plan, now));
        CspProblem withGreedyStarts = problem.withExtraStarts(greedyStarts);
        return choco.solve(withGreedyStarts, greedyStarts);
    }

    // Where greedy placed each item, in the CSP's minutes; a repeating copy is found by its day.
    static int[] startsOf(CspProblem problem, Schedule schedule) {
        Map<String, Integer> startOf = new HashMap<>();
        for (Map.Entry<LocalDate, List<ScheduleEntry>> day : schedule.days().entrySet()) {
            int dayOffset = (int) (day.getKey().toEpochDay() - problem.firstDay().toEpochDay()) * CspProblem.MINUTES_PER_DAY;
            for (ScheduleEntry entry : day.getValue()) {
                if (entry.type() == EntryType.TASK) {
                    startOf.put(key(entry.name(), day.getKey()), dayOffset + entry.timeRange().startTime().toSecondOfDay() / 60);
                    startOf.putIfAbsent(key(entry.name(), null), dayOffset + entry.timeRange().startTime().toSecondOfDay() / 60);
                }
            }
        }
        List<CspProblem.Item> items = problem.items();
        int[] starts = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            TaskOccurrence occurrence = items.get(i).occurrence();
            starts[i] = startOf.getOrDefault(key(occurrence.name(), occurrence.date()), CspSolution.NOT_PLACED);
        }
        return starts;
    }

    private static String key(String name, LocalDate date) {
        return date == null ? name : name + "@" + date;
    }
}
