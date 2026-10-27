package com.lifescript.scheduler;

import com.lifescript.model.EnergyEntry;
import com.lifescript.model.EnergyLevel;
import com.lifescript.model.Plan;
import com.lifescript.model.TimeRange;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class EnergyMatch {

    private EnergyMatch() {
    }

    public static EnergyFit fit(Plan plan, DayOfWeek day, TimeRange time, EnergyLevel effort) {
        Map<EnergyLevel, Integer> seconds = secondsPerLevel(plan, day, time);
        return new EnergyFit(distance(effort, majorityLevel(seconds, effort)), seconds.getOrDefault(effort, 0));
    }

    // The level covering most of the time. On a tie, the worse match for this task wins (then the
    // lower level), so a slot never looks better than it is.
    static EnergyLevel levelDuring(Plan plan, DayOfWeek day, TimeRange time, EnergyLevel effort) {
        return majorityLevel(secondsPerLevel(plan, day, time), effort);
    }

    private static Map<EnergyLevel, Integer> secondsPerLevel(Plan plan, DayOfWeek day, TimeRange time) {
        // Split where any energy entry starts or ends, so each piece has one level.
        int start = time.startTime().toSecondOfDay();
        int end = time.endSecondOfDay();
        TreeSet<Integer> cuts = new TreeSet<>(List.of(start, end));
        addCuts(cuts, plan.getDefaultEnergyProfile(), start, end);
        addCuts(cuts, plan.getEnergyProfileOverrides().getOrDefault(day, List.of()), start, end);

        Map<EnergyLevel, Integer> secondsPerLevel = new EnumMap<>(EnergyLevel.class);
        Integer pieceStart = cuts.first();
        for (Integer pieceEnd : cuts.tailSet(start, false)) {
            EnergyLevel level = plan.energyAt(day, LocalTime.ofSecondOfDay(pieceStart));
            secondsPerLevel.merge(level, pieceEnd - pieceStart, Integer::sum);
            pieceStart = pieceEnd;
        }
        return secondsPerLevel;
    }

    private static EnergyLevel majorityLevel(Map<EnergyLevel, Integer> secondsPerLevel, EnergyLevel effort) {
        Comparator<EnergyLevel> rank = Comparator
                .<EnergyLevel>comparingInt(level -> secondsPerLevel.getOrDefault(level, 0))
                .thenComparingInt(level -> distance(effort, level))
                .thenComparingInt(EnergyLevel::ordinal);   // declared HIGH, MODERATE, LOW: higher ordinal = lower level
        return Arrays.stream(EnergyLevel.values()).max(rank).orElseThrow();
    }

    private static void addCuts(TreeSet<Integer> cuts, List<EnergyEntry> entries, int start, int end) {
        for (EnergyEntry entry : entries) {
            for (int cut : new int[]{entry.range().startTime().toSecondOfDay(), entry.range().endSecondOfDay()}) {
                if (cut > start && cut < end) {
                    cuts.add(cut);
                }
            }
        }
    }

    // Symmetric: a low-effort task in high energy is a poor match too.
    static int distance(EnergyLevel effort, EnergyLevel energy) {
        return Math.abs(effort.ordinal() - energy.ordinal());
    }
}
