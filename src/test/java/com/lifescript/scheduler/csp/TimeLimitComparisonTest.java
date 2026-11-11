package com.lifescript.scheduler.csp;

import com.lifescript.model.Plan;
import com.lifescript.scheduler.GeneratedPlans;
import com.lifescript.scheduler.ScheduleMetrics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static com.lifescript.TestPlans.build;

// How the CSP schedulers' results improve with more time, to choose the app's time limit from data.
// Writes target/scheduler-time-limits.md. About 10 minutes:
// ./mvnw test -Dgroups=benchmark-time -Dbenchmark.excluded=none
@Tag("benchmark-time")
class TimeLimitComparisonTest {

    private static final List<Integer> SIZES = List.of(20, 40, 60);
    private static final List<Duration> LIMITS = List.of(
            Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10));
    private static final List<Long> SEEDS = List.of(1L, 2L, 3L, 4L, 5L);
    private static final List<String> SCHEDULERS = List.of("choco", "hybrid");

    @Test
    void qualityByTimeLimit() throws IOException {
        StringBuilder table = new StringBuilder(String.format("""
                %d weeks per size (seeds %s), one run per time limit.

                | Tasks | Time limit | Scheduler | By priority vs greedy (better / equal / worse) | Placed by priority, avg (critical, high, medium, low) | Energy distance avg | Proven optimal |
                |---|---|---|---|---|---|---|
                """, SEEDS.size(), SEEDS));
        // Java runs slowly until its JIT compiler has warmed up, which would cut the first short limits.
        Plan warmUp = build(GeneratedPlans.week(20, 99));
        SCHEDULERS.forEach(scheduler -> SchedulerComparisonTest.run(scheduler, warmUp, Duration.ofSeconds(2)));
        for (int size : SIZES) {
            List<Plan> plans = SEEDS.stream().map(seed -> build(GeneratedPlans.week(size, seed))).toList();
            List<ScheduleMetrics> greedy = plans.stream()
                    .map(plan -> ScheduleMetrics.of(plan, SchedulerComparisonTest.run("greedy", plan, Duration.ZERO).schedule()))
                    .toList();
            table.append(String.format("| %d | - | greedy | - | %s | %.1f | - |%n", size,
                    averageByPriority(greedy), greedy.stream().mapToInt(ScheduleMetrics::energyDistance).average().orElse(0)));
            for (Duration limit : LIMITS) {
                for (String scheduler : SCHEDULERS) {
                    int better = 0, equal = 0, worse = 0, optimal = 0;
                    ScheduleMetrics[] results = new ScheduleMetrics[plans.size()];
                    for (int i = 0; i < plans.size(); i++) {
                        SchedulerComparisonTest.Run run = SchedulerComparisonTest.run(scheduler, plans.get(i), limit);
                        results[i] = ScheduleMetrics.of(plans.get(i), run.schedule());
                        int vsGreedy = results[i].comparePlacedTo(greedy.get(i));
                        if (vsGreedy > 0) better++;
                        else if (vsGreedy == 0) equal++;
                        else worse++;
                        if (Boolean.TRUE.equals(run.optimal())) optimal++;
                    }
                    table.append(String.format("| %d | %s | %s | %d / %d / %d | %s | %.1f | %d/%d |%n", size, format(limit),
                            scheduler, better, equal, worse, averageByPriority(List.of(results)),
                            List.of(results).stream().mapToInt(ScheduleMetrics::energyDistance).average().orElse(0),
                            optimal, plans.size()));
                }
            }
        }
        System.out.println(table);
        Files.writeString(Path.of("target", "scheduler-time-limits.md"), table.toString());
    }

    // e.g. "5.4, 11.0, 8.6, 3.2": tasks placed per priority, averaged over the weeks
    private static String averageByPriority(List<ScheduleMetrics> results) {
        StringBuilder text = new StringBuilder();
        for (int p = 0; p < 4; p++) {
            int priority = p;
            double average = results.stream().mapToInt(m -> m.placed()[priority]).average().orElse(0);
            text.append(p == 0 ? "" : ", ").append(String.format("%.1f", average));
        }
        return text.toString();
    }

    private static String format(Duration limit) {
        return limit.toMillis() < 1000 ? limit.toMillis() + " ms" : limit.toSeconds() + " s";
    }
}
