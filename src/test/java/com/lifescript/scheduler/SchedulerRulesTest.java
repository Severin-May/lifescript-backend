package com.lifescript.scheduler;

import com.lifescript.model.Plan;
import com.lifescript.scheduler.csp.ChocoScheduler;
import com.lifescript.scheduler.csp.HybridScheduler;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.load;

// Every scheduler, on every plan, keeps the rules (see ScheduleRules).
class SchedulerRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-05T00:00");

    private static final Map<String, Scheduler> SCHEDULERS = Map.of(
            "greedy", new GreedyScheduler(),
            "choco", new ChocoScheduler(),
            "hybrid", new HybridScheduler());

    private static String plan(String settings, String availability, String rest) {
        return """
                plan: p
                period: 2026-10-05 to 2026-10-11

                %s

                availability:
                %s

                %s
                """.formatted(settings, availability, rest);
    }

    private static Map<String, String> plans() throws IOException {
        return Map.of(
                "fullPlan.ls", load("fullPlan.ls"),
                "breaks, the plan's and a task's own", plan("settings:\nbreak: 10m", "monday: 09:00-12:00\ntuesday: 09:00-10:00", """
                        tasks:
                        task: a
                        duration: 1h
                        priority: high
                        effort: high
                        break: 30m

                        task: b
                        duration: 45m
                        priority: medium
                        effort: low

                        task: c
                        duration: 1h
                        priority: low
                        effort: moderate
                        """),
                "dependencies with a deadline", plan("", "monday: 09:00-12:00\ntuesday: 09:00-12:00\nwednesday: 09:00-12:00", """
                        tasks:
                        task: research
                        duration: 2h
                        priority: high
                        effort: high

                        task: draft
                        duration: 2h
                        priority: medium
                        effort: moderate
                        dependencies: research

                        task: submit
                        duration: 30m
                        priority: critical
                        effort: low
                        deadline: 2026-10-07 17:00
                        dependencies: draft
                        """),
                "repeating tasks, routines and events", plan("", "monday: flexible\ntuesday: flexible\nwednesday: 08:00-10:00", """
                        routines:
                        routine: lunch
                        time: 12:00
                        repeats: daily
                        activities:
                        eat: 45m

                        events:
                        event: dentist
                        date: 2026-10-06
                        time: 14:00-15:30

                        tasks:
                        task: gym
                        duration: 1h
                        priority: medium
                        effort: high
                        repeats: monday, tuesday, wednesday

                        task: report
                        duration: 3h
                        priority: high
                        effort: high
                        """),
                "more tasks than fit", plan("", "monday: 09:00-11:00", """
                        tasks:
                        task: a
                        duration: 1h
                        priority: low
                        effort: low

                        task: b
                        duration: 1h
                        priority: critical
                        effort: low

                        task: c
                        duration: 1h
                        priority: high
                        effort: low

                        task: d
                        duration: 1h
                        priority: medium
                        effort: low
                        dependencies: a
                        """));
    }

    static Stream<Arguments> everySchedulerOnEveryPlan() throws IOException {
        List<Arguments> cases = new ArrayList<>();
        for (Map.Entry<String, String> plan : plans().entrySet()) {
            for (Map.Entry<String, Scheduler> scheduler : SCHEDULERS.entrySet()) {
                cases.add(Arguments.of(scheduler.getKey(), plan.getKey(), scheduler.getValue(), plan.getValue()));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("everySchedulerOnEveryPlan")
    void keepsTheRules(String schedulerName, String planName, Scheduler scheduler, String source) {
        Plan plan = build(source);

        ScheduleRules.assertFollowsTheRules(plan, NOW, scheduler.schedule(plan, NOW));
    }
}
