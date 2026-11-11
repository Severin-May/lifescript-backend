package com.lifescript.scheduler.greedy;

import com.lifescript.model.Plan;
import com.lifescript.model.TimeRange;
import com.lifescript.scheduler.EnergyFit;
import com.lifescript.scheduler.EnergyMatch;
import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.AvailableTime;
import com.lifescript.scheduler.Placements;
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
import java.util.Optional;
import java.util.Set;

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
                AvailableTime.of(plan, Placements.roundUpToStep(now, settings.startStep()));
        Map<LocalDate, List<ScheduleEntry>> days = Placements.fixedCommitments(plan);
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
                        Placements.dependencyFailedReason(failedDependency), occurrence.task().note()));
                failed.add(occurrence.name());
                continue;
            }

            LocalDateTime earliestStart = dependencies.stream().map(endOf::get)
                    .max(LocalDateTime::compareTo).orElse(null);
            Duration rest = Placements.restAfter(occurrence, plan);
            int strictRest = Placements.strictRestSeconds(occurrence, plan);

            Placement placement = bestPlacement(plan, available, occurrence, earliestStart, occurrence.preferredEnd(), strictRest);
            boolean withoutBuffer = false;
            if (placement == null && occurrence.hardEnd() != null && !occurrence.hardEnd().equals(occurrence.preferredEnd())) {
                placement = bestPlacement(plan, available, occurrence, earliestStart, occurrence.hardEnd(), strictRest);
                withoutBuffer = placement != null;
            }

            if (placement == null) {
                unscheduled.add(new UnscheduledTask(occurrence.name(), occurrence.date(), Placements.noSlotReason(occurrence, strictRest),
                        occurrence.task().note()));
                failed.add(occurrence.name());
                continue;
            }

            days.get(placement.date()).add(new ScheduleEntry(EntryType.TASK, occurrence.name(), placement.time(),
                    occurrence.task().note()));
            Optional<TimeRange> restTime = Placements.breakAfter(placement.time(), rest);
            if (restTime.isPresent()) {
                days.get(placement.date()).add(new ScheduleEntry(EntryType.BREAK, occurrence.name(), restTime.get(), null));
            }
            // The plan's break before it too, because tasks aren't placed in time order.
            available.put(placement.date(), AvailableTime.minus(available.get(placement.date()),
                    reserved(placement.time(), plan.breakTime(), rest)));
            endOf.put(occurrence.name(), placement.time().endOn(placement.date()));
            if (withoutBuffer) {
                warnings.add(new ScheduleWarning(occurrence.name(), placement.date(),
                        Placements.closeToDeadlineWarning(plan, occurrence, endOf.get(occurrence.name()))));
            }
        }

        return new Schedule(days, unscheduled, warnings);
    }

    private record Placement(LocalDate date, TimeRange time) {
    }

    private static TimeRange reserved(TimeRange task, Duration before, Duration after) {
        int start = (int) Math.max(0, task.startTime().toSecondOfDay() - before.toSeconds());
        int end = (int) Math.min(TimeRange.SECONDS_PER_DAY, task.endSecondOfDay() + after.toSeconds());
        return TimeRange.ofSeconds(start, end);
    }

    // Candidates come in time order, so of equally good fits the earliest is kept.
    // strictRest: seconds after the task that must be free as well; the deadline and energy only see the task.
    private Placement bestPlacement(Plan plan, Map<LocalDate, List<TimeRange>> available, TaskOccurrence occurrence,
                                    LocalDateTime earliestStart, LocalDateTime latestEnd, int strictRest) {
        int duration = (int) occurrence.task().duration().toSeconds();
        int step = (int) settings.startStep().toSeconds();
        List<LocalDate> dates = occurrence.date() != null ? List.of(occurrence.date()) : List.copyOf(available.keySet());

        Placement best = null;
        EnergyFit bestFit = null;
        for (LocalDate date : dates) {
            for (TimeRange range : available.get(date)) {
                for (int start : Placements.candidateStarts(range, duration + strictRest, step)) {
                    int end = start + duration;
                    TimeRange time = TimeRange.ofSeconds(start, end);
                    if (earliestStart != null && date.atTime(time.startTime()).isBefore(earliestStart)) {
                        continue;
                    }
                    if (latestEnd != null && time.endOn(date).isAfter(latestEnd)) {
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
}
