package com.lifescript.scheduler;

import com.lifescript.model.Plan;
import com.lifescript.model.Priority;
import com.lifescript.model.Task;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// How good a schedule is, measured the same way for every scheduler. Per-priority counts are in Priority's
// order (critical first); a repeating task counts once per day.
public record ScheduleMetrics(int[] placed, int[] total, int energyDistance, int warnings) {

    public static ScheduleMetrics of(Plan plan, Schedule schedule) {
        Map<String, Task> tasks = new HashMap<>();
        plan.tasks().forEach(task -> tasks.put(task.name(), task));
        int[] placed = new int[Priority.values().length];
        int[] total = new int[Priority.values().length];
        for (TaskOccurrence occurrence : TaskOccurrences.of(plan, SchedulerSettings.DEFAULTS)) {
            total[occurrence.task().priority().ordinal()]++;
        }
        int energyDistance = 0;
        for (Map.Entry<LocalDate, List<ScheduleEntry>> day : schedule.days().entrySet()) {
            for (ScheduleEntry entry : day.getValue()) {
                if (entry.type() == EntryType.TASK) {
                    Task task = tasks.get(entry.name());
                    placed[task.priority().ordinal()]++;
                    energyDistance += EnergyMatch.fit(plan, day.getKey().getDayOfWeek(), entry.timeRange(), task.effort()).distance();
                }
            }
        }
        return new ScheduleMetrics(placed, total, energyDistance, schedule.warnings().size());
    }

    public int placedCount() {
        return Arrays.stream(placed).sum();
    }

    public int totalCount() {
        return Arrays.stream(total).sum();
    }

    // Like the CSP's strict weights: more critical tasks first, then more high ones, and so on.
    public int comparePlacedTo(ScheduleMetrics other) {
        return Arrays.compare(placed, other.placed);
    }

    // e.g. "1/1, 3/4, 2/3, 1/4" for critical, high, medium, low
    public String placedByPriority() {
        return java.util.stream.IntStream.range(0, placed.length)
                .mapToObj(i -> placed[i] + "/" + total[i]).collect(Collectors.joining(", "));
    }
}
