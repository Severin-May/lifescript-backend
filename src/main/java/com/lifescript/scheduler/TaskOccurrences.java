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

public final class TaskOccurrences {

    private TaskOccurrences() {
    }

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

    // A dependency must end before its dependent starts: the dependent's limit minus its duration.
    private static final class Limits {
        private final Map<String, List<Task>> dependents;
        private final Function<Task, LocalDateTime> ownLimit;
        private final Map<String, LocalDateTime> cache = new HashMap<>();

        Limits(Map<String, List<Task>> dependents, Function<Task, LocalDateTime> ownLimit) {
            this.dependents = dependents;
            this.ownLimit = ownLimit;
        }

        // Terminates because SemanticValidator rejects dependency cycles.
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

    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }
}
