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
        for (Task task : plan.getTasks()) {
            for (String dependency : task.getDependencies()) {
                dependents.computeIfAbsent(dependency, name -> new ArrayList<>()).add(task);
            }
        }

        List<TaskOccurrence> occurrences = new ArrayList<>();
        for (Task task : plan.getTasks()) {
            if (task.getRepeatDays().isEmpty()) {
                occurrences.add(new TaskOccurrence(task, null,
                        latestEnd(task, settings.deadlineBuffer(), dependents),
                        latestEnd(task, Duration.ZERO, dependents)));
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
    // Terminates because SemanticValidator rejects dependency cycles.
    private static LocalDateTime latestEnd(Task task, Duration buffer, Map<String, List<Task>> dependents) {
        LocalDateTime limit = minus(task.getDeadline(), buffer);
        for (Task dependent : dependents.getOrDefault(task.getName(), List.of())) {
            limit = earlier(limit, minus(latestEnd(dependent, buffer, dependents), dependent.getDuration()));
        }
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
