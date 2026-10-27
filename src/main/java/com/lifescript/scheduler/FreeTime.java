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

public final class FreeTime {

    private FreeTime() {
    }

    // Routines and events also take breakTime on both sides.
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

    public static List<TimeRange> minus(List<TimeRange> free, TimeRange taken) {
        List<TimeRange> result = new ArrayList<>();
        for (TimeRange range : free) {
            result.addAll(range.minus(taken));
        }
        return result;
    }
}
