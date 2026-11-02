package com.lifescript.scheduler.greedy;

import com.lifescript.model.Event;
import com.lifescript.model.Plan;
import com.lifescript.model.Routine;
import com.lifescript.model.TimeRange;
import com.lifescript.scheduler.EnergyFit;
import com.lifescript.scheduler.EnergyMatch;
import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.AvailableTime;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleEntry;
import com.lifescript.scheduler.ScheduleWarning;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.SchedulerSettings;
import com.lifescript.scheduler.TaskOccurrence;
import com.lifescript.scheduler.TaskOccurrences;
import com.lifescript.scheduler.UnscheduledTask;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class GreedyScheduler implements Scheduler {

    private final SchedulerSettings settings;

    public GreedyScheduler() {
        this(SchedulerSettings.DEFAULTS);
    }

    public GreedyScheduler(SchedulerSettings settings) {
        this.settings = settings;
    }

    @Override
    public Schedule schedule(Plan plan, LocalDateTime now) {
        Map<LocalDate, List<TimeRange>> available =
                AvailableTime.of(plan, settings.breakTime(), roundUpToStep(now));
        Map<LocalDate, List<ScheduleEntry>> days = fixedCommitments(plan);
        List<UnscheduledTask> unscheduled = new ArrayList<>();
        List<ScheduleWarning> warnings = new ArrayList<>();

        // Only one-off tasks can be dependencies.
        Map<String, LocalDateTime> endOf = new HashMap<>();
        Set<String> failed = new HashSet<>();

        for (TaskOccurrence occurrence : TaskOrder.of(TaskOccurrences.of(plan, settings))) {
            List<String> dependencies = occurrence.task().dependencies();
            String failedDependency = dependencies.stream().filter(failed::contains).findFirst().orElse(null);
            if (failedDependency != null) {
                unscheduled.add(new UnscheduledTask(occurrence.name(), occurrence.date(),
                        "depends on '" + failedDependency + "', which couldn't be scheduled", occurrence.task().note()));
                failed.add(occurrence.name());
                continue;
            }

            LocalDateTime earliestStart = dependencies.stream().map(endOf::get)
                    .max(LocalDateTime::compareTo).orElse(null);

            Placement placement = bestPlacement(plan, available, occurrence, earliestStart, occurrence.preferredEnd());
            boolean withoutBuffer = false;
            if (placement == null && occurrence.hardEnd() != null && !occurrence.hardEnd().equals(occurrence.preferredEnd())) {
                placement = bestPlacement(plan, available, occurrence, earliestStart, occurrence.hardEnd());
                withoutBuffer = placement != null;
            }

            if (placement == null) {
                unscheduled.add(new UnscheduledTask(occurrence.name(), occurrence.date(), reason(occurrence, dependencies),
                        occurrence.task().note()));
                failed.add(occurrence.name());
                continue;
            }

            days.get(placement.date()).add(new ScheduleEntry(EntryType.TASK, occurrence.name(), placement.time(),
                    occurrence.task().note()));
            // Padded on both sides because tasks aren't placed in time order.
            available.put(placement.date(), AvailableTime.minus(available.get(placement.date()),
                    placement.time().paddedBy(settings.breakTime())));
            endOf.put(occurrence.name(), endDateTime(placement.date(), placement.time()));
            if (withoutBuffer) {
                warnings.add(new ScheduleWarning(occurrence.name(), placement.date(),
                        "only fits by ending less than " + formatDuration(settings.deadlineBuffer())
                                + " before its deadline"));
            }
        }

        return new Schedule(days, unscheduled, warnings);
    }

    private record Placement(LocalDate date, TimeRange time) {
    }

    private static Map<LocalDate, List<ScheduleEntry>> fixedCommitments(Plan plan) {
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

    // Candidates come in time order, so of equally good fits the earliest is kept.
    private Placement bestPlacement(Plan plan, Map<LocalDate, List<TimeRange>> available, TaskOccurrence occurrence,
                                    LocalDateTime earliestStart, LocalDateTime latestEnd) {
        int duration = (int) occurrence.task().duration().toSeconds();
        int step = (int) settings.startStep().toSeconds();
        List<LocalDate> dates = occurrence.date() != null ? List.of(occurrence.date()) : List.copyOf(available.keySet());

        Placement best = null;
        EnergyFit bestFit = null;
        for (LocalDate date : dates) {
            for (TimeRange range : available.get(date)) {
                for (int start : candidateStarts(range, duration, step)) {
                    int end = start + duration;
                    TimeRange time = TimeRange.ofSeconds(start, end);
                    if (earliestStart != null && date.atTime(time.startTime()).isBefore(earliestStart)) {
                        continue;
                    }
                    if (latestEnd != null && endDateTime(date, time).isAfter(latestEnd)) {
                        continue;
                    }
                    EnergyFit fit = EnergyMatch.fit(plan, date.getDayOfWeek(), time, occurrence.task().effort());
                    if (best == null || fit.isBetterThan(bestFit)) {
                        best = new Placement(date, time);
                        bestFit = fit;
                    }
                }
            }
        }
        return best;
    }

    // The range's own start, then every step on the clock.
    private static List<Integer> candidateStarts(TimeRange range, int duration, int step) {
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
    private LocalDateTime roundUpToStep(LocalDateTime now) {
        long step = settings.startStep().toSeconds();
        long rounded = (now.toLocalTime().toSecondOfDay() + step - 1) / step * step;
        return now.toLocalDate().atStartOfDay().plusSeconds(rounded);
    }

    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours == 0) return minutes + "m";
        return minutes == 0 ? hours + "h" : hours + "h" + minutes + "m";
    }

    private static LocalDateTime endDateTime(LocalDate date, TimeRange time) {
        return time.endsAtMidnight() ? date.plusDays(1).atStartOfDay() : date.atTime(time.endTime());
    }

    private static String reason(TaskOccurrence occurrence, List<String> dependencies) {
        StringBuilder reason = new StringBuilder("no free slot long enough");
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
        }
        if (dependencies.isEmpty() && occurrence.hardEnd() == null) {
            reason.append(" in the period");
        }
        return reason.toString();
    }
}
