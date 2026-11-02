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

public final class TaskOccurrences {

    private TaskOccurrences() {
    }

    public static List<TaskOccurrence> of(Plan plan, SchedulerSettings settings) {
        Map<String, List<Task>> dependents = new HashMap<>();
        for (Task task : plan.tasks()) {
            for (String dependency : task.dependencies()) {
                dependents.computeIfAbsent(dependency, name -> new ArrayList<>()).add(task);
            }
        }

        Map<String, LocalDateTime> preferredEnds = new HashMap<>();
        Map<String, LocalDateTime> hardEnds = new HashMap<>();
        List<TaskOccurrence> occurrences = new ArrayList<>();
        for (Task task : plan.tasks()) {
            if (task.repeatDays().isEmpty()) {
                occurrences.add(new TaskOccurrence(task, null,
                        latestEnd(task, settings.deadlineBuffer(), dependents, preferredEnds),
                        latestEnd(task, Duration.ZERO, dependents, hardEnds)));
                continue;
            }
            for (LocalDate date : plan.period().dates()) {
                if (task.repeatDays().contains(date.getDayOfWeek())) {
                    LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
                    occurrences.add(new TaskOccurrence(task, date, endOfDay, endOfDay));
                }
            }
        }
        return occurrences;
    }

    // A dependency must end before its dependent starts: the dependent's limit minus its duration.
    // Terminates because SemanticValidator rejects dependency cycles. `computed` keeps each task's result,
    // otherwise layered dependencies are recomputed once per path (exponential); null is a valid result.
    private static LocalDateTime latestEnd(Task task, Duration buffer, Map<String, List<Task>> dependents,
                                           Map<String, LocalDateTime> computed) {
        if (computed.containsKey(task.name())) {
            return computed.get(task.name());
        }
        LocalDateTime limit = minus(task.deadline(), buffer);
        for (Task dependent : dependents.getOrDefault(task.name(), List.of())) {
            limit = earlier(limit, minus(latestEnd(dependent, buffer, dependents, computed), dependent.duration()));
        }
        computed.put(task.name(), limit);
        return limit;
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
