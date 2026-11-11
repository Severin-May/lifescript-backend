package com.lifescript.scheduler.csp;

import com.lifescript.model.Plan;
import com.lifescript.scheduler.GeneratedPlans;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleMetrics;
import com.lifescript.scheduler.ScheduleRules;
import com.lifescript.scheduler.SchedulerSettings;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.convert.DurationStyle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.load;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Greedy against the CSP schedulers on the same plans, for the thesis: examples per plan
// (target/scheduler-comparison-2s.md) and averages over many generated weeks (target/scheduler-averages-2s.md),
// named after the time limit.
// Not part of normal builds: ./mvnw test -Dgroups=benchmark -Dbenchmark.excluded=none
@Tag("benchmark")
class SchedulerComparisonTest {

    private static final List<String> SCHEDULERS = List.of("greedy", "choco", "hybrid");
    private static final List<String> FILES = List.of(
            "fullPlan.ls", "comparison/blocks-itself.ls", "comparison/energy-together.ls", "comparison/crowded-week.ls");
    private static final List<Integer> SIZES = List.of(10, 20, 40, 60);
    private static final long EXAMPLE_SEED = 42;
    private static final List<Long> SEEDS = List.of(1L, 2L, 3L, 4L, 5L);
    // Results under a time limit vary between runs, so each one is repeated.
    private static final int REPEATS = 3;

    // -Dbenchmark.timeLimit=4s (or 500ms, 1m); 2 s if not given.
    static final Duration TIME_LIMIT = DurationStyle.detectAndParse(System.getProperty("benchmark.timeLimit", "2s"));

    // The schedule, whether the solver proved it best (null for greedy), and how long it took.
    record Run(Schedule schedule, Boolean optimal, Duration time) {
    }

    private static Run run(String scheduler, Plan plan) {
        return run(scheduler, plan, TIME_LIMIT);
    }

    // Every schedule must keep the rules, whichever scheduler made it.
    static Run run(String scheduler, Plan plan, Duration timeLimit) {
        LocalDateTime now = plan.period().start().atStartOfDay();
        long start = System.nanoTime();
        Schedule schedule;
        Boolean optimal = null;
        if (scheduler.equals("greedy")) {
            schedule = new GreedyScheduler().schedule(plan, now);
        } else {
            CspProblem problem = CspProblem.of(plan, now, SchedulerSettings.DEFAULTS);
            CspSolution solution = scheduler.equals("choco")
                    ? new ChocoScheduler(SchedulerSettings.DEFAULTS, timeLimit).solve(problem)
                    : new HybridScheduler(SchedulerSettings.DEFAULTS, timeLimit).solve(problem, plan, now);
            schedule = CspSchedules.toSchedule(problem, solution);
            optimal = solution.optimal();
        }
        Duration time = Duration.ofNanos(System.nanoTime() - start);
        ScheduleRules.assertFollowsTheRules(plan, now, schedule);
        return new Run(schedule, optimal, time);
    }

    // One row per plan and scheduler; the time is the median of REPEATS runs after a warm-up run.
    @Test
    void examplesPerPlan() throws IOException {
        Map<String, String> plans = new LinkedHashMap<>();
        for (String file : FILES) {
            plans.put(file.replace("comparison/", "").replace(".ls", ""), load(file));
        }
        for (int size : SIZES) {
            plans.put("generated, " + size + " tasks", GeneratedPlans.week(size, EXAMPLE_SEED));
        }

        StringBuilder table = new StringBuilder("""
                | Plan | Scheduler | Placed | By priority (critical, high, medium, low) | Energy distance | Warnings | Time (median) | Proven optimal |
                |---|---|---|---|---|---|---|---|
                """);
        for (Map.Entry<String, String> entry : plans.entrySet()) {
            Plan plan = build(entry.getValue());
            ScheduleMetrics greedy = null;
            for (String scheduler : SCHEDULERS) {
                run(scheduler, plan);
                List<Run> runs = new ArrayList<>();
                for (int i = 0; i < REPEATS; i++) {
                    runs.add(run(scheduler, plan));
                }
                Run last = runs.get(runs.size() - 1);
                ScheduleMetrics m = ScheduleMetrics.of(plan, last.schedule());
                if (greedy == null) {
                    greedy = m;
                } else if (scheduler.equals("hybrid") || Boolean.TRUE.equals(last.optimal())) {
                    ScheduleMetrics g = greedy;
                    assertTrue(m.comparePlacedTo(g) >= 0, () -> entry.getKey() + ": " + scheduler
                            + " (optimal, or starting from greedy) can't place less by priority than greedy");
                }
                List<Duration> times = runs.stream().map(Run::time).sorted().toList();
                table.append(String.format("| %s | %s | %d/%d | %s | %d | %d | %s | %s |%n", entry.getKey(), scheduler,
                        m.placedCount(), m.totalCount(), m.placedByPriority(), m.energyDistance(), m.warnings(),
                        format(times.get(times.size() / 2)), optimal(last.optimal())));
            }
        }
        write("scheduler-comparison-" + label(TIME_LIMIT) + ".md", table.toString());
    }

    // For each size: SEEDS different weeks, each scheduled REPEATS times by every scheduler.
    @Test
    void averagesOnGeneratedWeeks() throws IOException {
        StringBuilder table = new StringBuilder(String.format("""
                %d weeks per size (seeds %s), each scheduled %d times; CSP time limit %s.

                | Tasks | Scheduler | Placed (avg) | By priority, avg (critical, high, medium, low) | By priority vs greedy (better / equal / worse) | Energy distance avg (min-max) | Time avg | Proven optimal |
                |---|---|---|---|---|---|---|---|
                """, SEEDS.size(), SEEDS, REPEATS, label(TIME_LIMIT)));
        // Java runs slowly until its JIT compiler has warmed up, which would hurt the first runs.
        Plan warmUp = build(GeneratedPlans.week(20, 99));
        SCHEDULERS.forEach(scheduler -> run(scheduler, warmUp));
        for (int size : SIZES) {
            Map<String, Stats> stats = new LinkedHashMap<>();
            SCHEDULERS.forEach(scheduler -> stats.put(scheduler, new Stats()));
            for (long seed : SEEDS) {
                Plan plan = build(GeneratedPlans.week(size, seed));
                ScheduleMetrics greedy = ScheduleMetrics.of(plan, run("greedy", plan).schedule());
                for (String scheduler : SCHEDULERS) {
                    for (int i = 0; i < REPEATS; i++) {
                        Run run = run(scheduler, plan);
                        stats.get(scheduler).add(ScheduleMetrics.of(plan, run.schedule()), greedy, run);
                    }
                }
            }
            stats.forEach((scheduler, s) -> table.append(s.row(size, scheduler)));
        }
        write("scheduler-averages-" + label(TIME_LIMIT) + ".md", table.toString());
    }

    private static final class Stats {
        private int runs;
        private final double[] placed = new double[4];
        private final double[] total = new double[4];
        private int better;
        private int equal;
        private int worse;
        private int energySum;
        private int energyMin = Integer.MAX_VALUE;
        private int energyMax;
        private long nanos;
        private int optimal;
        private boolean csp;

        void add(ScheduleMetrics metrics, ScheduleMetrics greedy, Run run) {
            runs++;
            for (int p = 0; p < placed.length; p++) {
                placed[p] += metrics.placed()[p];
                total[p] += metrics.total()[p];
            }
            int vsGreedy = metrics.comparePlacedTo(greedy);
            if (vsGreedy > 0) better++;
            else if (vsGreedy == 0) equal++;
            else worse++;
            energySum += metrics.energyDistance();
            energyMin = Math.min(energyMin, metrics.energyDistance());
            energyMax = Math.max(energyMax, metrics.energyDistance());
            nanos += run.time().toNanos();
            csp = run.optimal() != null;
            if (Boolean.TRUE.equals(run.optimal())) optimal++;
        }

        String row(int size, String scheduler) {
            StringBuilder byPriority = new StringBuilder();
            for (int p = 0; p < placed.length; p++) {
                byPriority.append(p == 0 ? "" : ", ").append(String.format("%.1f/%.1f", placed[p] / runs, total[p] / runs));
            }
            double placedAvg = (placed[0] + placed[1] + placed[2] + placed[3]) / runs;
            double totalAvg = (total[0] + total[1] + total[2] + total[3]) / runs;
            return String.format("| %d | %s | %.1f/%.1f | %s | %s | %.1f (%d-%d) | %s | %s |%n", size, scheduler,
                    placedAvg, totalAvg, byPriority, scheduler.equals("greedy") ? "-" : better + " / " + equal + " / " + worse,
                    (double) energySum / runs, energyMin, energyMax, format(Duration.ofNanos(nanos / runs)),
                    csp ? optimal + "/" + runs : "-");
        }
    }

    // e.g. "2s" or "500ms"
    private static String label(Duration limit) {
        return limit.toMillis() % 1000 == 0 ? limit.toSeconds() + "s" : limit.toMillis() + "ms";
    }

    private static String optimal(Boolean optimal) {
        return optimal == null ? "-" : optimal ? "yes" : "no (time limit)";
    }

    private static void write(String file, String table) throws IOException {
        System.out.println(table);
        Files.writeString(Path.of("target", file), table);
    }

    private static String format(Duration time) {
        return time.toMillis() >= 10 ? time.toMillis() + " ms" : String.format("%.1f ms", time.toNanos() / 1e6);
    }
}
