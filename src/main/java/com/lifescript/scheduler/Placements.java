package com.lifescript.scheduler;

import com.lifescript.model.Durations;
import com.lifescript.model.Event;
import com.lifescript.model.Plan;
import com.lifescript.model.Routine;
import com.lifescript.model.TimeRange;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

// What every scheduler places tasks with: candidate starts, rests, breaks, fixed entries, and why a task can't go anywhere.
public final class Placements {

    private Placements() {
    }

    // The range's own start, then every step on the clock (seconds of the day).
    public static List<Integer> candidateStarts(TimeRange range, int duration, int step) {
        int rangeStart = range.startTime().toSecondOfDay();
        int lastStart = range.endSecondOfDay() - duration;
        List<Integer> starts = new ArrayList<>();
        if (rangeStart <= lastStart) {
            starts.add(rangeStart);
        }
        for (int start = (rangeStart / step + 1) * step; start <= lastStart; start += step) {
            starts.add(start);
        }
        return starts;
    }

    // 15:07 -> 15:15 with a 15m step, so tasks start at tidy times.
    public static LocalDateTime roundUpToStep(LocalDateTime now, Duration step) {
        long seconds = step.toSeconds();
        long rounded = (now.toLocalTime().toSecondOfDay() + seconds - 1) / seconds * seconds;
        return now.toLocalDate().atStartOfDay().plusSeconds(rounded);
    }

    public static Duration restAfter(TaskOccurrence occurrence, Plan plan) {
        Duration own = occurrence.task().breakTime();
        return own != null ? own : plan.breakTime();
    }

    // A task's own break must fit in free time too; the plan's break may run into unavailable time.
    public static int strictRestSeconds(TaskOccurrence occurrence, Plan plan) {
        return occurrence.task().breakTime() != null ? (int) restAfter(occurrence, plan).toSeconds() : 0;
    }

    // Empty without a break, or when the task ends at midnight.
    public static Optional<TimeRange> breakAfter(TimeRange task, Duration breakTime) {
        int start = task.endSecondOfDay();
        int end = (int) Math.min(TimeRange.SECONDS_PER_DAY, start + breakTime.toSeconds());
        return end > start ? Optional.of(TimeRange.ofSeconds(start, end)) : Optional.empty();
    }

    // Every date of the period, with its routines and events; modifiable, for tasks to be added.
    public static Map<LocalDate, List<ScheduleEntry>> fixedCommitments(Plan plan) {
        Map<LocalDate, List<ScheduleEntry>> days = new TreeMap<>();
        for (LocalDate date : plan.period().dates()) {
            List<ScheduleEntry> entries = new ArrayList<>();
            for (Routine routine : plan.routines()) {
                if (routine.occursOn(date)) {
                    entries.add(new ScheduleEntry(EntryType.ROUTINE, routine.name(), routine.timeRange(), null));
                }
            }
            for (Event event : plan.events()) {
                if (event.occursOn(date)) {
                    entries.add(new ScheduleEntry(EntryType.EVENT, event.name(), event.timeRange(), event.note()));
                }
            }
            days.put(date, entries);
        }
        return days;
    }

    public static String dependencyFailedReason(String dependency) {
        return "depends on '" + dependency + "', which couldn't be scheduled";
    }

    // Why no start at all was possible, e.g. "no free slot long enough after 'research' ends and ending by ...".
    public static String noSlotReason(TaskOccurrence occurrence, int strictRest) {
        List<String> dependencies = occurrence.task().dependencies();
        StringBuilder reason = new StringBuilder("no free slot long enough");
        if (strictRest > 0) {
            reason.append(" for it and its ").append(Durations.format(Duration.ofSeconds(strictRest))).append(" break");
        }
        if (occurrence.date() != null) {
            return reason.append(" on ").append(occurrence.date()).toString();
        }
        if (!dependencies.isEmpty()) {
            reason.append(" after ").append(dependencies.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", ")))
                    .append(dependencies.size() == 1 ? " ends" : " end");
        }
        if (occurrence.hardEnd() != null) {
            reason.append(dependencies.isEmpty() ? "" : " and").append(" ending by ")
                    .append(occurrence.hardEnd().toLocalDate()).append(' ').append(occurrence.hardEnd().toLocalTime());
            if (!occurrence.name().equals(occurrence.deadlineSetBy())) {
                reason.append(", so that '").append(occurrence.deadlineSetBy())
                        .append("' (which depends on it) can meet its deadline");
            }
        }
        if (dependencies.isEmpty() && occurrence.hardEnd() == null) {
            reason.append(" in the period");
        }
        return reason.toString();
    }

    // For a task that ends inside its deadline buffer, e.g. "ends at 15:30, only 1h30m before its deadline at 17:00".
    public static String closeToDeadlineWarning(Plan plan, TaskOccurrence occurrence, LocalDateTime end) {
        LocalDateTime deadline = plan.tasks().stream().filter(task -> task.name().equals(occurrence.deadlineSetBy()))
                .findFirst().orElseThrow().deadline();
        String deadlineText = deadline.toLocalDate().equals(end.toLocalDate())
                ? deadline.toLocalTime().toString() : deadline.toLocalDate() + " " + deadline.toLocalTime();
        String ends = "ends at " + end.toLocalTime();
        if (!occurrence.name().equals(occurrence.deadlineSetBy())) {
            return ends + ", which leaves little time for '" + occurrence.deadlineSetBy()
                    + "' (which depends on it) before its deadline at " + deadlineText;
        }
        Duration gap = Duration.between(end, deadline);
        return gap.isZero() ? "ends right at its deadline at " + deadlineText
                : ends + ", only " + Durations.format(gap) + " before its deadline at " + deadlineText;
    }
}
