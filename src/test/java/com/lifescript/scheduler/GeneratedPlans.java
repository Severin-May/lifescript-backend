package com.lifescript.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Random but reproducible weeks for comparing schedulers: the same seed gives the same plan text.
public final class GeneratedPlans {

    private static final String[] DURATIONS = {"30m", "45m", "1h", "1h30m", "2h", "3h"};
    private static final String[] EFFORTS = {"high", "moderate", "low"};
    private static final String[] DEADLINE_DAYS = {"2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10"};
    private static final String[] REPEAT_DAYS = {"monday, wednesday", "tuesday, thursday", "monday, wednesday, friday", "weekdays"};

    private GeneratedPlans() {
    }

    // About 44h of free time; tasks average about 1.5h, so 10 tasks are a light week and 60 a heavily overloaded one.
    public static String week(int taskCount, long seed) {
        Random random = new Random(seed);
        StringBuilder tasks = new StringBuilder();
        List<String> oneOff = new ArrayList<>();
        for (int i = 1; i <= taskCount; i++) {
            String name = String.format("task_%02d", i);
            tasks.append("    task: ").append(name).append('\n')
                    .append("        duration: ").append(pick(random, DURATIONS)).append('\n')
                    .append("        priority: ").append(priority(random)).append('\n')
                    .append("        effort: ").append(pick(random, EFFORTS)).append('\n');
            // A repeating task can't have a deadline or dependencies, and nothing may depend on it.
            if (random.nextDouble() < 0.1) {
                tasks.append("        repeats: ").append(pick(random, REPEAT_DAYS)).append('\n');
            } else {
                if (random.nextDouble() < 0.3) {
                    tasks.append("        deadline: ").append(pick(random, DEADLINE_DAYS)).append(" 17:00\n");
                }
                if (!oneOff.isEmpty() && random.nextDouble() < 0.25) {
                    tasks.append("        dependencies: ").append(oneOff.get(random.nextInt(oneOff.size()))).append('\n');
                }
                oneOff.add(name);
            }
            tasks.append('\n');
        }
        return """
                plan: generated_%d_tasks
                period: 2026-10-05 to 2026-10-11

                settings:
                    break: 10m

                availability:
                    monday: 09:00-12:00, 13:00-18:00
                    tuesday: 09:00-12:00, 13:00-18:00
                    wednesday: 09:00-12:00, 13:00-18:00
                    thursday: 09:00-12:00, 13:00-18:00
                    friday: 09:00-12:00, 13:00-18:00
                    saturday: 10:00-14:00

                energy profile:
                    default:
                        09:00-12:00: high
                        13:00-15:00: low
                        15:00-18:00: moderate

                routines:
                    routine: standup
                        time: 09:00
                        repeats: weekdays
                        activities:
                            meeting: 15m

                tasks:
                %s""".formatted(taskCount, tasks);
    }

    private static String pick(Random random, String[] options) {
        return options[random.nextInt(options.length)];
    }

    // About 10% critical, 30% high, 35% medium, 25% low.
    private static String priority(Random random) {
        double r = random.nextDouble();
        return r < 0.10 ? "critical" : r < 0.40 ? "high" : r < 0.75 ? "medium" : "low";
    }
}
