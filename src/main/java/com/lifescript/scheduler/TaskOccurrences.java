package com.lifescript.scheduler;

import com.lifescript.model.Plan;
import com.lifescript.model.Task;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

        Map<String, EffectiveDeadline> preferredDeadlines = new HashMap<>();
        Map<String, EffectiveDeadline> hardDeadlines = new HashMap<>();
        List<TaskOccurrence> occurrences = new ArrayList<>();
        for (Task task : plan.tasks()) {
            if (task.repeatDays().isEmpty()) {
                // The buffer shifts every deadline equally, so both are set by the same task.
                EffectiveDeadline hard = effectiveDeadline(task, Duration.ZERO, dependents, hardDeadlines);
                occurrences.add(new TaskOccurrence(task, null,
                        effectiveDeadline(task, settings.deadlineBuffer(), dependents, preferredDeadlines).time(),
                        hard.time(), hard.setBy()));
                continue;
            }
            for (LocalDate date : plan.period().dates()) {
                if (task.repeatDays().contains(date.getDayOfWeek())) {
                    LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
                    occurrences.add(new TaskOccurrence(task, date, endOfDay, endOfDay, null));
                }
            }
        }
        return occurrences;
    }

    // The effective deadline: the earliest of the task's own deadline and, for each task depending on it,
    // that task's effective deadline minus its duration (it must end before its dependent starts).
    // Terminates because SemanticValidator rejects dependency cycles. `computed` keeps each task's result,
    // otherwise layered dependencies are recomputed once per path (exponential).
    // An explicit stack rather than recursion, so a long chain of dependents can't overflow: a task waits on the
    // stack until all its dependents are computed.
    private static EffectiveDeadline effectiveDeadline(Task start, Duration buffer, Map<String, List<Task>> dependents,
                                                       Map<String, EffectiveDeadline> computed) {
        Deque<Task> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            Task task = stack.peek();
            if (computed.containsKey(task.name())) {
                stack.pop();
                continue;
            }
            List<Task> waitingFor = dependents.getOrDefault(task.name(), List.of()).stream()
                    .filter(dependent -> !computed.containsKey(dependent.name())).toList();
            if (!waitingFor.isEmpty()) {
                waitingFor.forEach(stack::push);
                continue;
            }
            stack.pop();
            computed.put(task.name(), fromOwnAndDependents(task, buffer, dependents, computed));
        }
        return computed.get(start.name());
    }

    // All the task's dependents must already be computed.
    private static EffectiveDeadline fromOwnAndDependents(Task task, Duration buffer, Map<String, List<Task>> dependents,
                                                          Map<String, EffectiveDeadline> computed) {
        EffectiveDeadline deadline = task.deadline() == null
                ? EffectiveDeadline.NONE : new EffectiveDeadline(task.deadline().minus(buffer), task.name());
        for (Task dependent : dependents.getOrDefault(task.name(), List.of())) {
            deadline = earlier(deadline, computed.get(dependent.name()).minus(dependent.duration()));
        }
        return deadline;
    }

    // time: null = no deadline. setBy: the task whose own deadline it comes from.
    private record EffectiveDeadline(LocalDateTime time, String setBy) {

        static final EffectiveDeadline NONE = new EffectiveDeadline(null, null);

        EffectiveDeadline minus(Duration duration) {
            return time == null ? this : new EffectiveDeadline(time.minus(duration), setBy);
        }
    }

    // On a tie the first one wins, so a task's own deadline beats an equal one passed down.
    private static EffectiveDeadline earlier(EffectiveDeadline a, EffectiveDeadline b) {
        if (a.time() == null) return b;
        if (b.time() == null) return a;
        return b.time().isBefore(a.time()) ? b : a;
    }
}
