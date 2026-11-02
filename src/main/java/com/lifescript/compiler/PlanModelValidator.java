package com.lifescript.compiler;

import com.lifescript.diagnostics.SourceLocation;
import com.lifescript.model.EnergyEntry;
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

// Clashes are reported at the item written later; the message names both.
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

    // Reported at the earlier period's line if written, else the later's (defaults are ordered).
    private void checkSettingsOrder(Plan plan) {
        Map<String, TimeRange> timeSettings = plan.timeSettings();
        Map<String, SourceLocation> locations = plan.timeSettingLocations();
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

    private void checkAvailabilityOverlaps(Plan plan) {
        for (Map.Entry<DayOfWeek, List<TimeRange>> day : plan.availability().entrySet()) {
            List<TimeRange> ranges = day.getValue();
            for (int i = 0; i < ranges.size(); i++) {
                for (int j = i + 1; j < ranges.size(); j++) {
                    if (ranges.get(i).overlaps(ranges.get(j))) {
                        diagnostics.error(plan.availabilityLocations().get(day.getKey()),
                                String.format("Availability on %s: %s overlaps %s",
                                        Keywords.format(day.getKey()), ranges.get(i), ranges.get(j)));
                    }
                }
            }
        }
    }

    // A day block may overlap the default: that's how it replaces part of it.
    private void checkEnergyOverlaps(Plan plan) {
        checkEnergyBlock("default", plan.defaultEnergyProfile());
        for (Map.Entry<DayOfWeek, List<EnergyEntry>> day : plan.energyProfileOverrides().entrySet()) {
            checkEnergyBlock(Keywords.format(day.getKey()), day.getValue());
        }
    }

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
                            blockName, a.range(), Keywords.format(a.level()), b.range(), Keywords.format(b.level()),
                            shared.startTime(), shared.endTime()));
                } else {
                    diagnostics.error(b.location(), String.format("Energy profile (%s): %s and %s overlap (both %s); remove one of them",
                            blockName, a.range(), b.range(), Keywords.format(a.level())));
                }
            }
        }
    }

    private void checkRoutineOverlaps(Plan plan) {
        Set<DayOfWeek> daysInPeriod = plan.period().weekdays();
        List<Routine> routines = plan.routines();

        for (int i = 0; i < routines.size(); i++) {
            for (int j = i + 1; j < routines.size(); j++) {
                Routine a = routines.get(i);
                Routine b = routines.get(j);
                if (!a.timeRange().overlaps(b.timeRange())) {
                    continue;
                }

                List<DayOfWeek> clashDays = new ArrayList<>();
                for (DayOfWeek day : DayOfWeek.values()) {
                    if (daysInPeriod.contains(day) && a.repeatDays().contains(day) && b.repeatDays().contains(day)) {
                        clashDays.add(day);
                    }
                }

                if (!clashDays.isEmpty()) {
                    diagnostics.error(b.location(), String.format("Routine '%s' (%s) overlaps routine '%s' (%s) on %s",
                            a.name(), a.timeRange(), b.name(), b.timeRange(),
                            clashDays.stream().map(Keywords::format).collect(Collectors.joining(", "))));
                }
            }
        }
    }

    private void checkEventOverlaps(Plan plan) {
        List<Event> events = plan.events();

        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);

            for (Routine routine : plan.routines()) {
                if (routine.occursOn(event.date()) && event.timeRange().overlaps(routine.timeRange())) {
                    diagnostics.error(event.location(), String.format("Event '%s' (%s) on %s overlaps routine '%s' (%s)",
                            event.name(), event.timeRange(), event.date(),
                            routine.name(), routine.timeRange()));
                }
            }

            for (int j = i + 1; j < events.size(); j++) {
                Event other = events.get(j);
                if (other.occursOn(event.date()) && event.timeRange().overlaps(other.timeRange())) {
                    diagnostics.error(other.location(), String.format("Event '%s' (%s) overlaps event '%s' (%s) on %s",
                            event.name(), event.timeRange(), other.name(), other.timeRange(),
                            event.date()));
                }
            }
        }
    }
}
