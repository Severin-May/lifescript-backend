package com.lifescript.scheduler;

import com.lifescript.model.Plan;
import com.lifescript.model.Task;
import com.lifescript.model.TimeRange;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// The rules every schedule must follow, whichever algorithm made it. Checks the result only, not how it was found.
public final class ScheduleRules {

    private ScheduleRules() {
    }

    // A one-off task's key has no date; a repeating copy's key has its day.
    private record Key(String name, LocalDate date) {
    }

    private record Placed(LocalDate date, ScheduleEntry entry) {
        LocalDateTime start() {
            return date.atTime(entry.timeRange().startTime());
        }

        LocalDateTime end() {
            return entry.timeRange().endOn(date);
        }
    }

    public static void assertFollowsTheRules(Plan plan, LocalDateTime now, Schedule schedule) {
        Map<String, Task> tasks = new HashMap<>();
        plan.tasks().forEach(task -> tasks.put(task.name(), task));
        Map<Key, Placed> placed = placedTasks(schedule, tasks);

        everyOccurrenceIsPlacedOrUnscheduledOnce(plan, schedule, placed);
        tasksAreInFreeTime(plan, now, placed, tasks);
        tasksKeepTheirRestFromTheNext(plan, placed.values(), tasks);
        breaksFollowTheirTasks(plan, schedule, tasks);
        dependenciesEndFirst(placed, tasks);
    }

    private static Map<Key, Placed> placedTasks(Schedule schedule, Map<String, Task> tasks) {
        Map<Key, Placed> placed = new HashMap<>();
        schedule.days().forEach((date, entries) -> entries.stream().filter(e -> e.type() == EntryType.TASK).forEach(entry -> {
            Task task = tasks.get(entry.name());
            Key key = new Key(entry.name(), task.repeatDays().isEmpty() ? null : date);
            if (placed.put(key, new Placed(date, entry)) != null) {
                fail("Placed twice: " + key);
            }
        }));
        return placed;
    }

    private static void everyOccurrenceIsPlacedOrUnscheduledOnce(Plan plan, Schedule schedule, Map<Key, Placed> placed) {
        Set<Key> expected = new HashSet<>();
        for (TaskOccurrence occurrence : TaskOccurrences.of(plan, SchedulerSettings.DEFAULTS)) {
            expected.add(new Key(occurrence.name(), occurrence.date()));
        }
        List<Key> seen = new ArrayList<>(placed.keySet());
        schedule.unscheduled().forEach(u -> seen.add(new Key(u.name(), u.date())));
        assertEquals(expected.size(), seen.size(), () -> "Placed or unscheduled more than once: " + seen);
        assertEquals(expected, new HashSet<>(seen), "Every task (each copy of a repeating one) placed or unscheduled");
    }

    // Inside one free range (availability minus routines and events with their breaks, not before now);
    // a task's own break must be free too.
    private static void tasksAreInFreeTime(Plan plan, LocalDateTime now, Map<Key, Placed> placed, Map<String, Task> tasks) {
        Map<LocalDate, List<TimeRange>> free =
                AvailableTime.of(plan, Placements.roundUpToStep(now, SchedulerSettings.DEFAULTS.startStep()));
        for (Placed task : placed.values()) {
            Duration ownBreak = Optional.ofNullable(tasks.get(task.entry().name()).breakTime()).orElse(Duration.ZERO);
            int start = task.entry().timeRange().startTime().toSecondOfDay();
            long end = task.entry().timeRange().endSecondOfDay() + ownBreak.toSeconds();
            boolean inside = free.get(task.date()).stream()
                    .anyMatch(range -> range.startTime().toSecondOfDay() <= start && end <= range.endSecondOfDay());
            assertTrue(inside, () -> task.entry().name() + " on " + task.date() + " " + task.entry().timeRange()
                    + " is not inside free time " + free.get(task.date()));
        }
    }

    // Two tasks are at least the earlier one's rest apart (its own break, otherwise the plan's).
    private static void tasksKeepTheirRestFromTheNext(Plan plan, Collection<Placed> placed, Map<String, Task> tasks) {
        List<Placed> inOrder = placed.stream().sorted(Comparator.comparing(Placed::start)).toList();
        for (int i = 1; i < inOrder.size(); i++) {
            Placed earlier = inOrder.get(i - 1);
            Placed later = inOrder.get(i);
            LocalDateTime freeFrom = earlier.end().plus(restAfter(plan, tasks.get(earlier.entry().name())));
            assertTrue(!later.start().isBefore(freeFrom), () -> later.entry().name() + " at " + later.start()
                    + " is too close to " + earlier.entry().name() + " ending " + earlier.end());
        }
    }

    // Exactly one BREAK right after each task that has a rest, cut at midnight; no others.
    private static void breaksFollowTheirTasks(Plan plan, Schedule schedule, Map<String, Task> tasks) {
        schedule.days().forEach((date, entries) -> {
            List<ScheduleEntry> expected = new ArrayList<>();
            for (ScheduleEntry entry : entries) {
                if (entry.type() == EntryType.TASK) {
                    Placements.breakAfter(entry.timeRange(), restAfter(plan, tasks.get(entry.name())))
                            .ifPresent(rest -> expected.add(new ScheduleEntry(EntryType.BREAK, entry.name(), rest, null)));
                }
            }
            List<ScheduleEntry> actual = entries.stream().filter(e -> e.type() == EntryType.BREAK).toList();
            assertEquals(new HashSet<>(expected), new HashSet<>(actual), "Breaks on " + date);
        });
    }

    private static void dependenciesEndFirst(Map<Key, Placed> placed, Map<String, Task> tasks) {
        for (Map.Entry<Key, Placed> entry : placed.entrySet()) {
            for (String dependency : tasks.get(entry.getKey().name()).dependencies()) {
                Placed before = placed.get(new Key(dependency, null));
                assertTrue(before != null, () -> entry.getKey().name() + " is placed but its dependency " + dependency + " isn't");
                assertTrue(!before.end().isAfter(entry.getValue().start()),
                        () -> entry.getKey().name() + " starts before its dependency " + dependency + " ends");
            }
        }
    }

    private static Duration restAfter(Plan plan, Task task) {
        return task.breakTime() != null ? task.breakTime() : plan.breakTime();
    }
}
