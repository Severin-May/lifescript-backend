package com.lifescript.scheduler.greedy;

import com.lifescript.model.Priority;
import com.lifescript.scheduler.TaskOccurrence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TaskOrder {

    private TaskOrder() {
    }

    static List<TaskOccurrence> of(List<TaskOccurrence> occurrences) {
        Comparator<TaskOccurrence> rank = Comparator
                .comparing(TaskOccurrence::preferredEnd, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                .thenComparing(o -> o.task().getPriority(), Comparator.<Priority>naturalOrder())
                .thenComparing(occurrences::indexOf);

        // Without deadlines, only this keeps dependencies first.
        List<TaskOccurrence> remaining = new ArrayList<>(occurrences);
        List<TaskOccurrence> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (!remaining.isEmpty()) {
            TaskOccurrence next = remaining.stream()
                    .filter(o -> done.containsAll(o.task().getDependencies()))
                    .min(rank)
                    .orElseThrow(() -> new IllegalStateException("Dependency cycle; SemanticValidator should have rejected it"));
            remaining.remove(next);
            ordered.add(next);
            done.add(next.name());
        }
        return ordered;
    }
}
