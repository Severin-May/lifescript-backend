package com.lifescript.scheduler.greedy;

import com.lifescript.model.Event;
import com.lifescript.model.Plan;
import com.lifescript.model.Routine;
import com.lifescript.model.TimeRange;
import com.lifescript.scheduler.EnergyFit;
import com.lifescript.scheduler.EnergyMatch;
import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.FreeTime;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleEntry;
import com.lifescript.scheduler.ScheduleNote;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.SchedulerSettings;
import com.lifescript.scheduler.TaskOccurrence;
import com.lifescript.scheduler.TaskOccurrences;
import com.lifescript.scheduler.UnscheduledTask;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// Places tasks one at a time, in TaskOrder, each in the best slot still free, and never moves them again.
// Best slot = closest energy match to the task's effort, then most time at exactly its effort level,
// then earliest (see EnergyFit). A task first tries to end by its
// preferred end (with the deadline buffer); if it can't, by its hard end (the deadline), with a note.
// Every task keeps the break time free on both sides of it.
public class GreedyScheduler implements Scheduler {

    private static final int SECONDS_PER_DAY = 24 * 60 * 60;
    private static final int STEP_SECONDS = 15 * 60;

    private final SchedulerSettings settings;

    public GreedyScheduler() {
        this(SchedulerSettings.DEFAULTS);
    }

    public GreedyScheduler(SchedulerSettings settings) {
        this.settings = settings;
    }

    @Override
    public Schedule schedule(Plan plan) {
        Map<LocalDate, List<TimeRange>> free = FreeTime.of(plan, settings.breakTime());
        Map<LocalDate, List<ScheduleEntry>> days = fixedCommitments(plan);
        List<UnscheduledTask> unscheduled = new ArrayList<>();
        List<ScheduleNote> notes = new ArrayList<>();

        // Only one-off tasks can be dependencies, so these are keyed by task name.
        Map<String, LocalDateTime> endOf = new HashMap<>();
        Set<String> failed = new HashSet<>();

        for (TaskOccurrence occurrence : TaskOrder.of(TaskOccurrences.of(plan, settings))) {
            List<String> dependencies = occurrence.task().getDependencies();
            String failedDependency = dependencies.stream().filter(failed::contains).findFirst().orElse(null);
            if (failedDependency != null) {
                unscheduled.add(new UnscheduledTask(occurrence.name(), occurrence.date(),
                        "depends on " + failedDependency + ", which could not be scheduled"));
                failed.add(occurrence.name());
                continue;
            }

            LocalDateTime earliestStart = dependencies.stream().map(endOf::get)
                    .max(LocalDateTime::compareTo).orElse(null);

            Placement placement = bestPlacement(plan, free, occurrence, earliestStart, occurrence.preferredEnd());
            boolean withoutBuffer = false;
            if (placement == null && occurrence.hardEnd() != null && !occurrence.hardEnd().equals(occurrence.preferredEnd())) {
                placement = bestPlacement(plan, free, occurrence, earliestStart, occurrence.hardEnd());
                withoutBuffer = placement != null;
            }

            if (placement == null) {
                unscheduled.add(new UnscheduledTask(occurrence.name(), occurrence.date(), reason(occurrence, dependencies)));
                failed.add(occurrence.name());
                continue;
            }

            days.get(placement.date()).add(new ScheduleEntry(EntryType.TASK, occurrence.name(), placement.time()));
            // Reserve the task plus its break on both sides, so the next task can't start or end right against it.
            free.put(placement.date(), FreeTime.minus(free.get(placement.date()),
                    placement.time().paddedBy(settings.breakTime())));
            endOf.put(occurrence.name(), endDateTime(placement.date(), placement.time()));
            if (withoutBuffer) {
                notes.add(new ScheduleNote(occurrence.name(), placement.date(),
                        "only fits by ending less than " + formatDuration(settings.deadlineBuffer())
                                + " before its deadline"));
            }
        }

        return new Schedule(days, unscheduled, notes);
    }

    private record Placement(LocalDate date, TimeRange time) {
    }

    // Every date in the period, starting with the routines and events on it (they always happen,
    // even outside availability).
    private static Map<LocalDate, List<ScheduleEntry>> fixedCommitments(Plan plan) {
        Map<LocalDate, List<ScheduleEntry>> days = new TreeMap<>();
        for (LocalDate date : plan.getPeriod().dates()) {
            List<ScheduleEntry> entries = new ArrayList<>();
            for (Routine routine : plan.getRoutines()) {
                if (routine.occursOn(date)) {
                    entries.add(new ScheduleEntry(EntryType.ROUTINE, routine.getName(), routine.getTimeRange()));
                }
            }
            for (Event event : plan.getEvents()) {
                if (event.occursOn(date)) {
                    entries.add(new ScheduleEntry(EntryType.EVENT, event.getName(), event.getTimeRange()));
                }
            }
            days.put(date, entries);
        }
        return days;
    }

    // The best valid slot, or null if there is none. Candidates are checked in time order, so on an
    // equally good fit the first one found (the earliest) is kept.
    private static Placement bestPlacement(Plan plan, Map<LocalDate, List<TimeRange>> free, TaskOccurrence occurrence,
                                           LocalDateTime earliestStart, LocalDateTime latestEnd) {
        int duration = (int) occurrence.task().getDuration().toSeconds();
        List<LocalDate> dates = occurrence.date() != null ? List.of(occurrence.date()) : List.copyOf(free.keySet());

        Placement best = null;
        EnergyFit bestFit = null;
        for (LocalDate date : dates) {
            for (TimeRange range : free.get(date)) {
                for (int start : candidateStarts(range, duration)) {
                    int end = start + duration;
                    TimeRange time = TimeRange.ofSeconds(start, end);
                    if (earliestStart != null && date.atTime(time.startTime()).isBefore(earliestStart)) {
                        continue;
                    }
                    if (latestEnd != null && endDateTime(date, time).isAfter(latestEnd)) {
                        continue;
                    }
                    EnergyFit fit = EnergyMatch.fit(plan, date.getDayOfWeek(), time, occurrence.task().getEffort());
                    if (best == null || fit.isBetterThan(bestFit)) {
                        best = new Placement(date, time);
                        bestFit = fit;
                    }
                }
            }
        }
        return best;
    }

    // Where a task may start within a free range: the range's exact start, then every clock quarter-hour
    // after it (07:35, 07:45, 08:00, ...), as long as the whole task still fits in the range.
    private static List<Integer> candidateStarts(TimeRange range, int duration) {
        int rangeStart = range.startTime().toSecondOfDay();
        int lastStart = range.endSecondOfDay() - duration;
        List<Integer> starts = new ArrayList<>();
        if (rangeStart <= lastStart) {
            starts.add(rangeStart);
        }
        for (int start = (rangeStart / STEP_SECONDS + 1) * STEP_SECONDS; start <= lastStart; start += STEP_SECONDS) {
            starts.add(start);
        }
        return starts;
    }

    // 2h, 1h30m, 45m (the way durations are written in LifeScript)
    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours == 0) return minutes + "m";
        return minutes == 0 ? hours + "h" : hours + "h" + minutes + "m";
    }

    // An end of 00:00 is midnight at the end of `date`, i.e. the start of the next day.
    private static LocalDateTime endDateTime(LocalDate date, TimeRange time) {
        return time.endsAtMidnight() ? date.plusDays(1).atStartOfDay() : date.atTime(time.endTime());
    }

    // e.g. "no free slot long enough on 2026-10-11" or "no free slot long enough after research ends
    // and ending by 2026-10-09 17:00"
    private static String reason(TaskOccurrence occurrence, List<String> dependencies) {
        StringBuilder reason = new StringBuilder("no free slot long enough");
        if (occurrence.date() != null) {
            return reason.append(" on ").append(occurrence.date()).toString();
        }
        if (!dependencies.isEmpty()) {
            reason.append(" after ").append(String.join(", ", dependencies))
                    .append(dependencies.size() == 1 ? " ends" : " end");
        }
        if (occurrence.hardEnd() != null) {
            reason.append(dependencies.isEmpty() ? "" : " and").append(" ending by ")
                    .append(occurrence.hardEnd().toLocalDate()).append(' ').append(occurrence.hardEnd().toLocalTime());
        }
        if (dependencies.isEmpty() && occurrence.hardEnd() == null) {
            reason.append(" in the period");
        }
        return reason.toString();
    }
}
