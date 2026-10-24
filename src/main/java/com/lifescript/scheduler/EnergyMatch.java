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

// How well a task's effort fits the energy at a time. Shared by all scheduling algorithms.
// fit() is the one public entry point; levelDuring() and distance() are its building blocks,
// package-private so tests can check the rules directly.
public final class EnergyMatch {

    private EnergyMatch() {
    }

    // How good `time` on `day` is for a task with `effort`.
    public static EnergyFit fit(Plan plan, DayOfWeek day, TimeRange time, EnergyLevel effort) {
        Map<EnergyLevel, Integer> seconds = secondsPerLevel(plan, day, time);
        return new EnergyFit(distance(effort, majorityLevel(seconds, effort)), seconds.getOrDefault(effort, 0));
    }

    // The energy a task with `effort` gets during `time` on `day`: the level covering most of that time.
    // On a tie, the level that is the worse match for the task wins, so a slot never counts as better
    // than it is: half high, half low counts as low for a high-effort task, and as high for a low-effort
    // one (which then leaves that high-energy time to demanding tasks). If still tied, the lower level.
    static EnergyLevel levelDuring(Plan plan, DayOfWeek day, TimeRange time, EnergyLevel effort) {
        return majorityLevel(secondsPerLevel(plan, day, time), effort);
    }

    // How many seconds of `time` are at each energy level.
    private static Map<EnergyLevel, Integer> secondsPerLevel(Plan plan, DayOfWeek day, TimeRange time) {
        // Split the time where any energy entry starts or ends; each piece then has one level.
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

    // How far apart a task's effort and the energy are: 0 = same level, 1 = one step, 2 = high vs low.
    // Works both ways, so a low-effort task in high-energy time is a poor match too: it uses up time
    // a demanding task could have had.
    static int distance(EnergyLevel effort, EnergyLevel energy) {
        return Math.abs(effort.ordinal() - energy.ordinal());
    }
}
