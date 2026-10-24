package com.lifescript.scheduler;

import com.lifescript.model.FixedCommitment;
import com.lifescript.model.Plan;
import com.lifescript.model.TimeRange;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// When tasks can go: on each date, that weekday's availability minus the routines and events on that date.
// Shared by all scheduling algorithms. Routines and events ignore availability, so one outside it
// (e.g. a 07:00 routine before an 08:00 start) simply cuts nothing.
public final class FreeTime {

    private FreeTime() {
    }

    // Every date in the period mapped to its free ranges in time order (empty if none). Each routine and
    // event also takes `breakTime` on both sides, so no task starts or ends right against it.
    // Returns new, modifiable lists, so an algorithm can use them as its own working copy.
    public static Map<LocalDate, List<TimeRange>> of(Plan plan, Duration breakTime) {
        List<FixedCommitment> commitments = new ArrayList<>(plan.getRoutines());
        commitments.addAll(plan.getEvents());

        Map<LocalDate, List<TimeRange>> free = new TreeMap<>();
        for (LocalDate date : plan.getPeriod().dates()) {
            List<TimeRange> ranges = plan.getAvailability().getOrDefault(date.getDayOfWeek(), List.of());
            for (FixedCommitment commitment : commitments) {
                if (commitment.occursOn(date)) {
                    ranges = minus(ranges, commitment.getTimeRange().paddedBy(breakTime));
                }
            }
            free.put(date, new ArrayList<>(ranges));
        }
        return free;
    }

    // The free ranges with `taken` cut out of them, still in time order.
    // Also used by algorithms to reserve the time of a task they have just placed.
    public static List<TimeRange> minus(List<TimeRange> free, TimeRange taken) {
        List<TimeRange> result = new ArrayList<>();
        for (TimeRange range : free) {
            result.addAll(range.minus(taken));
        }
        return result;
    }
}
