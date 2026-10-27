package com.lifescript.compiler;

import com.lifescript.diagnostics.SourceLocation;
import com.lifescript.model.EnergyEntry;
import com.lifescript.model.EnergyLevel;
import com.lifescript.model.Event;
import com.lifescript.model.Plan;
import com.lifescript.model.Routine;
import com.lifescript.model.TimeRange;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Checks that need the built Plan: named periods resolved through settings, routine times
// computed from their activities, and relations between sections. Runs after PlanModelBuilder,
// so it can assume the input passed SemanticValidator. Each problem is reported at the line the
// Plan recorded for it; when two things clash, at the one written later (the message names both).
public class PlanModelValidator {

    private static final List<String> PERIOD_ORDER = List.of("morning", "afternoon", "evening");

    private final DiagnosticCollector diagnostics;

    public PlanModelValidator(DiagnosticCollector diagnostics) {
        this.diagnostics = diagnostics;
    }

    public void validate(Plan plan) {
        checkSettingsOrder(plan);
        checkAvailabilityOverlaps(plan);
        checkEnergyOverlaps(plan);
        checkRoutineOverlaps(plan);
        checkEventOverlaps(plan);
    }

    // morning must end before afternoon starts, and afternoon before evening. This also keeps
    // flexible (morning start to evening end) a valid range. Reported at the earlier period's settings
    // line if it was written, otherwise at the later one's (defaults are in order, so at least one was).
    private void checkSettingsOrder(Plan plan) {
        Map<String, TimeRange> timeSettings = plan.getTimeSettings();
        Map<String, SourceLocation> locations = plan.getTimeSettingLocations();
        for (int i = 0; i < PERIOD_ORDER.size() - 1; i++) {
            String earlierName = PERIOD_ORDER.get(i);
            String laterName = PERIOD_ORDER.get(i + 1);
            TimeRange earlier = timeSettings.get(earlierName);
            TimeRange later = timeSettings.get(laterName);

            if (earlier.endsAtMidnight() || earlier.endTime().isAfter(later.startTime())) {
                SourceLocation location = locations.getOrDefault(earlierName, locations.get(laterName));
                diagnostics.error(location, String.format("Settings: %s (%s) must end before %s starts (%s)",
                        earlierName, earlier, laterName, later.startTime()));
            }
        }
    }

    // Reported at the day's availability line (only listed days have ranges, so it always exists).
    private void checkAvailabilityOverlaps(Plan plan) {
        for (Map.Entry<DayOfWeek, List<TimeRange>> day : plan.getAvailability().entrySet()) {
            List<TimeRange> ranges = day.getValue();
            for (int i = 0; i < ranges.size(); i++) {
                for (int j = i + 1; j < ranges.size(); j++) {
                    if (ranges.get(i).overlaps(ranges.get(j))) {
                        diagnostics.error(plan.getAvailabilityLocations().get(day.getKey()),
                                String.format("Availability on %s: %s overlaps %s",
                                        dayName(day.getKey()), ranges.get(i), ranges.get(j)));
                    }
                }
            }
        }
    }

    // Within one block the energy at a given time must be unambiguous. A day block overlapping
    // the default block is fine: that is how a day block replaces part of the default.
    private void checkEnergyOverlaps(Plan plan) {
        checkEnergyBlock("default", plan.getDefaultEnergyProfile());
        for (Map.Entry<DayOfWeek, List<EnergyEntry>> day : plan.getEnergyProfileOverrides().entrySet()) {
            checkEnergyBlock(dayName(day.getKey()), day.getValue());
        }
    }

    // Any overlap is an error. With different levels the energy in the shared time is unclear;
    // with the same level one entry is redundant. Also catches two lines that resolve to the same
    // range (e.g. "morning" and "06:00-12:00").
    private void checkEnergyBlock(String blockName, List<EnergyEntry> block) {
        for (int i = 0; i < block.size(); i++) {
            for (int j = i + 1; j < block.size(); j++) {
                EnergyEntry a = block.get(i);
                EnergyEntry b = block.get(j);
                if (!a.range().overlaps(b.range())) {
                    continue;
                }

                if (a.level() != b.level()) {
                    TimeRange shared = a.range().intersection(b.range());
                    diagnostics.error(b.location(), String.format("Energy profile (%s): %s is %s but %s is %s, "
                                    + "so the energy level from %s to %s is unclear",
                            blockName, a.range(), levelName(a.level()), b.range(), levelName(b.level()),
                            shared.startTime(), shared.endTime()));
                } else {
                    diagnostics.error(b.location(), String.format("Energy profile (%s): %s and %s overlap (both %s); remove one of them",
                            blockName, a.range(), b.range(), levelName(a.level())));
                }
            }
        }
    }

    private static String levelName(EnergyLevel level) {
        return level.name().toLowerCase();
    }

    // Reported once per pair, listing every weekday in the period on which both happen and clash.
    private void checkRoutineOverlaps(Plan plan) {
        Set<DayOfWeek> daysInPeriod = plan.getPeriod().weekdays();
        List<Routine> routines = plan.getRoutines();

        for (int i = 0; i < routines.size(); i++) {
            for (int j = i + 1; j < routines.size(); j++) {
                Routine a = routines.get(i);
                Routine b = routines.get(j);
                if (!a.getTimeRange().overlaps(b.getTimeRange())) {
                    continue;
                }

                List<DayOfWeek> clashDays = new ArrayList<>();
                for (DayOfWeek day : DayOfWeek.values()) {
                    if (daysInPeriod.contains(day) && a.getRepeatDays().contains(day) && b.getRepeatDays().contains(day)) {
                        clashDays.add(day);
                    }
                }

                if (!clashDays.isEmpty()) {
                    diagnostics.error(b.getLocation(), String.format("Routine %s (%s) overlaps routine %s (%s) on %s",
                            a.getName(), a.getTimeRange(), b.getName(), b.getTimeRange(),
                            clashDays.stream().map(PlanModelValidator::dayName).collect(Collectors.joining(", "))));
                }
            }
        }
    }

    private void checkEventOverlaps(Plan plan) {
        List<Event> events = plan.getEvents();

        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);

            for (Routine routine : plan.getRoutines()) {
                if (routine.occursOn(event.getDate()) && event.getTimeRange().overlaps(routine.getTimeRange())) {
                    diagnostics.error(event.getLocation(), String.format("Event %s (%s) on %s overlaps routine %s (%s)",
                            event.getName(), event.getTimeRange(), event.getDate(),
                            routine.getName(), routine.getTimeRange()));
                }
            }

            for (int j = i + 1; j < events.size(); j++) {
                Event other = events.get(j);
                if (other.occursOn(event.getDate()) && event.getTimeRange().overlaps(other.getTimeRange())) {
                    diagnostics.error(other.getLocation(), String.format("Event %s (%s) overlaps event %s (%s) on %s",
                            event.getName(), event.getTimeRange(), other.getName(), other.getTimeRange(),
                            event.getDate()));
                }
            }
        }
    }

    private static String dayName(DayOfWeek day) {
        return day.name().toLowerCase();
    }
}
