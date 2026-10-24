package com.lifescript.scheduler;

import com.lifescript.model.Plan;
import com.lifescript.model.Task;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Turns the plan's tasks into the occurrences to place, with their time limits.
// These are rules every algorithm shares; only the order of placing them is algorithm-specific.
public final class TaskOccurrences {

    private TaskOccurrences() {
    }

    // In file order; a repeating task's copies in date order. One-off tasks get their limits from their
    // own deadline and from the tasks that depend on them; a repeating task's copy must end by the end
    // of its date (repeating tasks have no deadline and no dependencies, see SemanticValidator).
    public static List<TaskOccurrence> of(Plan plan, SchedulerSettings settings) {
        Map<String, List<Task>> dependents = new HashMap<>();
        for (Task task : plan.getTasks()) {
            for (String dependency : task.getDependencies()) {
                dependents.computeIfAbsent(dependency, name -> new ArrayList<>()).add(task);
            }
        }

        Limits preferred = new Limits(dependents, task -> minus(task.getDeadline(), settings.deadlineBuffer()));
        Limits hard = new Limits(dependents, Task::getDeadline);

        List<TaskOccurrence> occurrences = new ArrayList<>();
        for (Task task : plan.getTasks()) {
            if (task.getRepeatDays().isEmpty()) {
                occurrences.add(new TaskOccurrence(task, null, preferred.latestEnd(task), hard.latestEnd(task)));
                continue;
            }
            for (LocalDate date : plan.getPeriod().dates()) {
                if (task.getRepeatDays().contains(date.getDayOfWeek())) {
                    LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
                    occurrences.add(new TaskOccurrence(task, date, endOfDay, endOfDay));
                }
            }
        }
        return occurrences;
    }

    // Latest end of each task: its own limit, or earlier if a task depending on it needs it done first.
    // A dependency must end before its dependent starts, so its limit is the dependent's limit minus
    // the dependent's duration. Example: write_report (1h30m) must end by 15:00, so research must end by 13:30.
    private static final class Limits {
        private final Map<String, List<Task>> dependents;
        private final Function<Task, LocalDateTime> ownLimit;
        private final Map<String, LocalDateTime> cache = new HashMap<>();

        Limits(Map<String, List<Task>> dependents, Function<Task, LocalDateTime> ownLimit) {
            this.dependents = dependents;
            this.ownLimit = ownLimit;
        }

        // null = no limit. Recursion ends because SemanticValidator rejects dependency cycles.
        LocalDateTime latestEnd(Task task) {
            if (cache.containsKey(task.getName())) {
                return cache.get(task.getName());
            }
            LocalDateTime limit = ownLimit.apply(task);
            for (Task dependent : dependents.getOrDefault(task.getName(), List.of())) {
                limit = earlier(limit, minus(latestEnd(dependent), dependent.getDuration()));
            }
            cache.put(task.getName(), limit);
            return limit;
        }
    }

    private static LocalDateTime minus(LocalDateTime time, Duration duration) {
        return time == null ? null : time.minus(duration);
    }

    // The earlier of two limits, where null means "no limit".
    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }
}
