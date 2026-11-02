package com.lifescript.scheduler;

import com.lifescript.model.FixedCommitment;
import com.lifescript.model.Plan;
import com.lifescript.model.TimeRange;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AvailableTime {

    private AvailableTime() {
    }

    // Routines and events also take breakTime on both sides. Nothing before notBefore is available.
    public static Map<LocalDate, List<TimeRange>> of(Plan plan, Duration breakTime, LocalDateTime notBefore) {
        List<FixedCommitment> commitments = new ArrayList<>(plan.getRoutines());
        commitments.addAll(plan.getEvents());

        Map<LocalDate, List<TimeRange>> available = new TreeMap<>();
        for (LocalDate date : plan.getPeriod().dates()) {
            List<TimeRange> ranges = plan.getAvailability().getOrDefault(date.getDayOfWeek(), List.of());
            for (FixedCommitment commitment : commitments) {
                if (commitment.occursOn(date)) {
                    ranges = minus(ranges, commitment.getTimeRange().paddedBy(breakTime));
                }
            }
            if (date.isBefore(notBefore.toLocalDate())) {
                ranges = List.of();
            } else if (date.equals(notBefore.toLocalDate()) && !notBefore.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                ranges = minus(ranges, new TimeRange(LocalTime.MIDNIGHT, notBefore.toLocalTime()));
            }
            available.put(date, new ArrayList<>(ranges));
        }
        return available;
    }

    public static List<TimeRange> minus(List<TimeRange> available, TimeRange taken) {
        List<TimeRange> result = new ArrayList<>();
        for (TimeRange range : available) {
            result.addAll(range.minus(taken));
        }
        return result;
    }
}
