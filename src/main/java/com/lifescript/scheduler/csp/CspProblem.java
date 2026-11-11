package com.lifescript.scheduler.csp;

import com.lifescript.model.Plan;
import com.lifescript.model.Priority;
import com.lifescript.model.TimeRange;
import com.lifescript.scheduler.AvailableTime;
import com.lifescript.scheduler.EnergyFit;
import com.lifescript.scheduler.EnergyMatch;
import com.lifescript.scheduler.Placements;
import com.lifescript.scheduler.SchedulerSettings;
import com.lifescript.scheduler.TaskOccurrence;
import com.lifescript.scheduler.TaskOccurrences;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The scheduling problem as any CSP solver needs it, built once from a plan. Times are minutes from the
// start of the period's first day, so a start of 1500 is 01:00 on the second day.
public record CspProblem(Plan plan, LocalDate firstDay, List<Item> items) {

    public static final int MINUTES_PER_DAY = 24 * 60;

    // starts: every allowed start, ascending; empty if the task can't go anywhere. costs[k]: how good starts[k] is
    // (lower is better, see cost). rest: minutes kept free after the task. dependencies: items that must end first.
    public record Item(TaskOccurrence occurrence, int[] starts, int[] costs, int duration, int rest, int strictRest,
                       long weight, List<Integer> dependencies) {
    }

    private record Candidate(int start, int cost) {
    }

    public CspProblem {
        items = List.copyOf(items);
    }

    public static CspProblem of(Plan plan, LocalDateTime now, SchedulerSettings settings) {
        LocalDate firstDay = plan.period().start();
        Map<LocalDate, List<TimeRange>> available = AvailableTime.of(plan, Placements.roundUpToStep(now, settings.startStep()));
        List<TaskOccurrence> occurrences = TaskOccurrences.of(plan, settings);
        Map<Priority, Long> weights = strictWeights(occurrences);

        // Only one-off tasks can be dependencies, so a name is enough to find one.
        Map<String, Integer> oneOffIndex = new HashMap<>();
        for (int i = 0; i < occurrences.size(); i++) {
            if (occurrences.get(i).date() == null) {
                oneOffIndex.put(occurrences.get(i).name(), i);
            }
        }

        List<Item> items = new ArrayList<>();
        for (TaskOccurrence occurrence : occurrences) {
            int duration = (int) occurrence.task().duration().toMinutes();
            int strictRest = Placements.strictRestSeconds(occurrence, plan) / 60;
            List<Candidate> candidates = candidates(plan, occurrence, available, firstDay, duration, strictRest, settings);
            items.add(new Item(occurrence, candidates.stream().mapToInt(Candidate::start).toArray(),
                    candidates.stream().mapToInt(Candidate::cost).toArray(), duration,
                    (int) Placements.restAfter(occurrence, plan).toMinutes(), strictRest,
                    weights.get(occurrence.task().priority()),
                    occurrence.task().dependencies().stream().map(oneOffIndex::get).toList()));
        }
        return new CspProblem(plan, firstDay, items);
    }

    public LocalDate dateOf(int minute) {
        return firstDay.plusDays(minute / MINUTES_PER_DAY);
    }

    public TimeRange timeOf(int start, int duration) {
        int minuteOfDay = start % MINUTES_PER_DAY;
        return TimeRange.ofSeconds(minuteOfDay * 60, (minuteOfDay + duration) * 60);
    }

    // The same problem with more allowed starts, e.g. where another scheduler placed the tasks: greedy also
    // starts a task right after the previous one and its rest, which is often off the quarter-hour grid.
    // extraStarts[i]: a start for item i, or CspSolution.NOT_PLACED for none. Assumes each start is allowed.
    public CspProblem withExtraStarts(int[] extraStarts) {
        List<Item> extended = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int extra = extraStarts[i];
            if (extra == CspSolution.NOT_PLACED || Arrays.stream(item.starts()).anyMatch(s -> s == extra)) {
                extended.add(item);
                continue;
            }
            LocalDate date = dateOf(extra);
            TimeRange time = timeOf(extra, item.duration());
            List<Candidate> candidates = new ArrayList<>();
            for (int k = 0; k < item.starts().length; k++) {
                candidates.add(new Candidate(item.starts()[k], item.costs()[k]));
            }
            candidates.add(new Candidate(extra, cost(plan, item.occurrence(), date, time, time.endOn(date), extra)));
            candidates.sort(Comparator.comparingInt(Candidate::start));
            extended.add(new Item(item.occurrence(), candidates.stream().mapToInt(Candidate::start).toArray(),
                    candidates.stream().mapToInt(Candidate::cost).toArray(), item.duration(), item.rest(),
                    item.strictRest(), item.weight(), item.dependencies()));
        }
        return new CspProblem(plan, firstDay, extended);
    }

    // The same candidates as the greedy scheduler: on its own day for a repeating copy, the task and its own
    // break inside one free range, ending by its hard deadline.
    private static List<Candidate> candidates(Plan plan, TaskOccurrence occurrence, Map<LocalDate, List<TimeRange>> available,
                                              LocalDate firstDay, int duration, int strictRest, SchedulerSettings settings) {
        int step = (int) settings.startStep().toSeconds();
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<LocalDate, List<TimeRange>> day : available.entrySet()) {
            LocalDate date = day.getKey();
            if (occurrence.date() != null && !occurrence.date().equals(date)) {
                continue;
            }
            int dayOffset = (int) (date.toEpochDay() - firstDay.toEpochDay()) * MINUTES_PER_DAY;
            for (TimeRange range : day.getValue()) {
                for (int startSecond : Placements.candidateStarts(range, (duration + strictRest) * 60, step)) {
                    LocalDateTime end = date.atStartOfDay().plusSeconds(startSecond).plusMinutes(duration);
                    if (occurrence.hardEnd() == null || !end.isAfter(occurrence.hardEnd())) {
                        int start = dayOffset + startSecond / 60;
                        TimeRange time = TimeRange.ofSeconds(startSecond, startSecond + duration * 60);
                        candidates.add(new Candidate(start, cost(plan, occurrence, date, time, end, start)));
                    }
                }
            }
        }
        return candidates;
    }

    // In this order of importance, as the greedy scheduler decides: ending before the deadline buffer, then the
    // energy level matching the effort, then more time at exactly that level, then earlier. Each part stays below
    // one step of the part before it (distance 0-2 < 3, at most 96 quarters a day < 97, at most 168 hours in a
    // 7-day period < 169), so for one task a better part always wins.
    static int cost(Plan plan, TaskOccurrence occurrence, LocalDate date, TimeRange time, LocalDateTime end, int start) {
        int pastBuffer = occurrence.preferredEnd() != null && end.isAfter(occurrence.preferredEnd()) ? 1 : 0;
        EnergyFit fit = EnergyMatch.fit(plan, date.getDayOfWeek(), time, occurrence.task().effort());
        int quartersNotMatching = (int) Math.ceil((time.duration().toSeconds() - fit.matchingSeconds()) / 900.0);
        int hoursFromStart = start / 60;
        return ((pastBuffer * 3 + fit.distance()) * 97 + quartersNotMatching) * 169 + hoursFromStart;
    }

    // Strict: one task of a priority is worth more than all tasks of the lower priorities together.
    private static Map<Priority, Long> strictWeights(List<TaskOccurrence> occurrences) {
        Map<Priority, Long> weights = new EnumMap<>(Priority.class);
        long lowerTotal = 0;
        // Priority is declared CRITICAL, HIGH, MEDIUM, LOW, so walk it backwards.
        Priority[] priorities = Priority.values();
        for (int i = priorities.length - 1; i >= 0; i--) {
            Priority priority = priorities[i];
            long weight = lowerTotal + 1;
            weights.put(priority, weight);
            long count = occurrences.stream().filter(o -> o.task().priority() == priority).count();
            lowerTotal = Math.addExact(lowerTotal, Math.multiplyExact(count, weight));
        }
        return weights;
    }
}
