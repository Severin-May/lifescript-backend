package com.lifescript.compiler;

import com.lifescript.diagnostics.SourceLocation;
import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.Activity;
import com.lifescript.model.EnergyEntry;
import com.lifescript.model.EnergyLevel;
import com.lifescript.model.Event;
import com.lifescript.model.Plan;
import com.lifescript.model.PlanPeriod;
import com.lifescript.model.Priority;
import com.lifescript.model.Routine;
import com.lifescript.model.Task;
import com.lifescript.model.TimeRange;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanModelBuilder {

    private static final Map<String, TimeRange> DEFAULT_TIME_SETTINGS = Map.of(
            "morning", new TimeRange(LocalTime.of(6, 0), LocalTime.of(12, 0)),
            "afternoon", new TimeRange(LocalTime.of(12, 0), LocalTime.of(18, 0)),
            "evening", new TimeRange(LocalTime.of(18, 0), LocalTime.of(22, 0))
    );

    public Plan build(LifeScriptParser.PlanContext ctx) {
        PlanPeriod period = new PlanPeriod(Literals.parseDate(ctx.period().DATE_VAL(0).getText()),
                Literals.parseDate(ctx.period().DATE_VAL(1).getText()));

        // Settings may come after the sections that use them.
        Map<String, TimeRange> timeSettings = resolveTimeSettings(ctx);

        Map<String, SourceLocation> timeSettingLocations = Map.of();
        Map<DayOfWeek, List<TimeRange>> availability = Map.of();
        Map<DayOfWeek, SourceLocation> availabilityLocations = Map.of();
        List<EnergyEntry> defaultEnergyProfile = List.of();
        Map<DayOfWeek, List<EnergyEntry>> energyProfileOverrides = Map.of();
        List<Task> tasks = List.of();
        List<Routine> routines = List.of();
        List<Event> events = List.of();

        for (LifeScriptParser.PlanSectionContext section : ctx.planSection()) {
            if (section.routines() != null) {
                routines = buildRoutines(section.routines());
            } else if (section.tasks() != null) {
                tasks = buildTasks(section.tasks());
            } else if (section.settings() != null) {
                timeSettingLocations = buildTimeSettingLocations(section.settings());
            } else if (section.availability() != null) {
                availability = buildAvailability(section.availability(), timeSettings);
                availabilityLocations = buildAvailabilityLocations(section.availability());
            } else if (section.energyProfile() != null) {
                defaultEnergyProfile = buildDefaultEnergyProfile(section.energyProfile(), timeSettings);
                energyProfileOverrides = buildEnergyProfileOverrides(section.energyProfile(), timeSettings);
            } else if (section.events() != null) {
                events = buildEvents(section.events(), timeSettings);
            }
        }

        return new Plan(ctx.IDENTIFIER().getText(), period, timeSettings, timeSettingLocations, availability,
                availabilityLocations, defaultEnergyProfile, energyProfileOverrides, tasks, routines, events);
    }

    private List<Routine> buildRoutines(LifeScriptParser.RoutinesContext ctx) {
        List<Routine> routines = new ArrayList<>();
        for (LifeScriptParser.RoutineEntryContext entry : ctx.routineEntry()) {
            routines.add(buildRoutine(entry));
        }
        return routines;
    }

    private Routine buildRoutine(LifeScriptParser.RoutineEntryContext ctx) {
        LocalTime startTime = null;
        List<DayOfWeek> repeatDays = List.of();
        List<Activity> activities = List.of();

        for (LifeScriptParser.RoutinePropertyContext prop : ctx.routineProperty()) {
            if (prop.routineTime() != null) {
                startTime = Literals.parseTime(prop.routineTime().TIME_VAL().getText());
            } else if (prop.repeats() != null) {
                repeatDays = buildRepeatDays(prop.repeats().repeatPattern());
            } else if (prop.routineActivities() != null) {
                activities = buildActivities(prop.routineActivities());
            }
        }

        return new Routine(ctx.IDENTIFIER().getText(), startTime, repeatDays, activities,
                DiagnosticCollector.locationOf(ctx));
    }

    private List<Activity> buildActivities(LifeScriptParser.RoutineActivitiesContext ctx) {
        List<Activity> activities = new ArrayList<>();
        for (LifeScriptParser.ActivityEntryContext entry : ctx.activityEntry()) {
            activities.add(new Activity(entry.IDENTIFIER().getText(),
                    Literals.parseDuration(entry.DURATION_VAL().getText())));
        }
        return activities;
    }

    private List<Event> buildEvents(LifeScriptParser.EventsContext ctx, Map<String, TimeRange> timeSettings) {
        List<Event> events = new ArrayList<>();
        for (LifeScriptParser.EventContext entry : ctx.event()) {
            events.add(buildEvent(entry, timeSettings));
        }
        return events;
    }

    private Event buildEvent(LifeScriptParser.EventContext ctx, Map<String, TimeRange> timeSettings) {
        LocalDate date = null;
        TimeRange timeRange = null;
        String note = null;

        for (LifeScriptParser.EventPropertyContext prop : ctx.eventProperty()) {
            if (prop.eventDate() != null) {
                date = Literals.parseDate(prop.eventDate().DATE_VAL().getText());
            } else if (prop.eventTime() != null) {
                LifeScriptParser.EventTimeContext time = prop.eventTime();
                timeRange = resolveTimeRange(time.namedPeriod(), time.timeRange(), timeSettings);
            } else if (prop.eventNote() != null) {
                note = Literals.parseString(prop.eventNote().STRING().getText());
            }
        }

        return new Event(ctx.IDENTIFIER().getText(), date, timeRange, note, DiagnosticCollector.locationOf(ctx));
    }

    // Days not listed are off (empty list).
    private Map<DayOfWeek, List<TimeRange>> buildAvailability(LifeScriptParser.AvailabilityContext ctx,
                                                              Map<String, TimeRange> timeSettings) {
        Map<DayOfWeek, List<TimeRange>> availability = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            availability.put(day, new ArrayList<>());
        }

        for (LifeScriptParser.AvailabilityEntryContext entry : ctx.availabilityEntry()) {
            DayOfWeek day = Keywords.parseDay(entry.dayOfWeek().getText());
            availability.put(day, buildAvailabilityValue(entry.availabilityValue(), timeSettings));
        }

        return availability;
    }

    private List<TimeRange> buildAvailabilityValue(LifeScriptParser.AvailabilityValueContext ctx,
                                                   Map<String, TimeRange> timeSettings) {
        List<TimeRange> ranges = new ArrayList<>();

        if (ctx.FLEXIBLE() != null) {
            // flexible = from the start of morning to the end of evening
            ranges.add(new TimeRange(timeSettings.get("morning").startTime(),
                    timeSettings.get("evening").endTime()));
        } else if (ctx.namedPeriodList() != null) {
            for (LifeScriptParser.NamedPeriodContext period : ctx.namedPeriodList().namedPeriod()) {
                ranges.add(timeSettings.get(Keywords.parseNamedPeriod(period.getText())));
            }
        } else {
            for (LifeScriptParser.TimeRangeContext range : ctx.timeRangeList().timeRange()) {
                ranges.add(Literals.parseTimeRange(range.getText()));
            }
        }

        ranges.sort(Comparator.comparing(TimeRange::startTime));
        return ranges;
    }

    private List<EnergyEntry> buildDefaultEnergyProfile(LifeScriptParser.EnergyProfileContext ctx,
                                                        Map<String, TimeRange> timeSettings) {
        for (LifeScriptParser.EnergyProfileEntryContext entry : ctx.energyProfileEntry()) {
            if (entry.DEFAULT() != null) {
                return buildEnergyEntries(entry, timeSettings);
            }
        }
        return new ArrayList<>();
    }

    private Map<DayOfWeek, List<EnergyEntry>> buildEnergyProfileOverrides(
            LifeScriptParser.EnergyProfileContext ctx, Map<String, TimeRange> timeSettings) {
        Map<DayOfWeek, List<EnergyEntry>> overrides = new EnumMap<>(DayOfWeek.class);
        for (LifeScriptParser.EnergyProfileEntryContext entry : ctx.energyProfileEntry()) {
            if (entry.dayOfWeek() != null) {
                overrides.put(Keywords.parseDay(entry.dayOfWeek().getText()), buildEnergyEntries(entry, timeSettings));
            }
        }
        return overrides;
    }

    private List<EnergyEntry> buildEnergyEntries(LifeScriptParser.EnergyProfileEntryContext ctx,
                                                 Map<String, TimeRange> timeSettings) {
        List<EnergyEntry> entries = new ArrayList<>();
        for (LifeScriptParser.EnergyEntryContext entry : ctx.energyEntry()) {
            TimeRange range = resolveTimeRange(entry.namedPeriod(), entry.timeRange(), timeSettings);
            entries.add(new EnergyEntry(range, Keywords.parseEnergyLevel(entry.energyLevel().getText()),
                    DiagnosticCollector.locationOf(entry)));
        }
        return entries;
    }

    private List<Task> buildTasks(LifeScriptParser.TasksContext ctx) {
        List<Task> tasks = new ArrayList<>();
        for (LifeScriptParser.TaskContext entry : ctx.task()) {
            tasks.add(buildTask(entry));
        }
        return tasks;
    }

    private Task buildTask(LifeScriptParser.TaskContext ctx) {
        Duration duration = null;
        Priority priority = null;
        EnergyLevel effort = null;
        LocalDateTime deadline = null;
        List<DayOfWeek> repeatDays = List.of();
        List<String> dependencies = List.of();
        String note = null;

        for (LifeScriptParser.TaskPropertyContext prop : ctx.taskProperty()) {
            if (prop.taskDuration() != null) {
                duration = Literals.parseDuration(prop.taskDuration().DURATION_VAL().getText());
            } else if (prop.taskPriority() != null) {
                priority = Keywords.parsePriority(prop.taskPriority().priorityLevel().getText());
            } else if (prop.taskEffort() != null) {
                effort = Keywords.parseEnergyLevel(prop.taskEffort().energyLevel().getText());
            } else if (prop.taskDeadline() != null) {
                LifeScriptParser.TaskDeadlineContext deadlineCtx = prop.taskDeadline();
                deadline = LocalDateTime.of(Literals.parseDate(deadlineCtx.DATE_VAL().getText()),
                        Literals.parseTime(deadlineCtx.TIME_VAL().getText()));
            } else if (prop.repeats() != null) {
                repeatDays = buildRepeatDays(prop.repeats().repeatPattern());
            } else if (prop.taskDependencies() != null) {
                dependencies = prop.taskDependencies().IDENTIFIER().stream().map(TerminalNode::getText).toList();
            } else if (prop.taskNote() != null) {
                note = Literals.parseString(prop.taskNote().STRING().getText());
            }
        }

        return new Task(ctx.IDENTIFIER().getText(), duration, priority, effort, deadline, repeatDays, dependencies, note);
    }

    private List<DayOfWeek> buildRepeatDays(LifeScriptParser.RepeatPatternContext ctx) {
        if (ctx.DAILY() != null) {
            return new ArrayList<>(List.of(DayOfWeek.values()));
        }
        if (ctx.WEEKDAYS() != null) {
            return new ArrayList<>(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        }
        if (ctx.WEEKENDS() != null) {
            return new ArrayList<>(List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        }

        List<DayOfWeek> days = new ArrayList<>();
        for (LifeScriptParser.DayOfWeekContext day : ctx.dayList().dayOfWeek()) {
            days.add(Keywords.parseDay(day.getText()));
        }
        return days;
    }

    private static TimeRange resolveTimeRange(LifeScriptParser.NamedPeriodContext namedPeriod,
                                              LifeScriptParser.TimeRangeContext timeRange,
                                              Map<String, TimeRange> timeSettings) {
        return namedPeriod != null
                ? timeSettings.get(Keywords.parseNamedPeriod(namedPeriod.getText()))
                : Literals.parseTimeRange(timeRange.getText());
    }

    private Map<String, TimeRange> resolveTimeSettings(LifeScriptParser.PlanContext ctx) {
        Map<String, TimeRange> timeSettings = new HashMap<>(DEFAULT_TIME_SETTINGS);

        for (LifeScriptParser.PlanSectionContext section : ctx.planSection()) {
            if (section.settings() != null) {
                for (LifeScriptParser.SettingsEntryContext entry : section.settings().settingsEntry()) {
                    timeSettings.put(Keywords.parseNamedPeriod(entry.namedPeriod().getText()), Literals.parseTimeRange(entry.timeRange().getText()));
                }
            }
        }

        return timeSettings;
    }

    private Map<String, SourceLocation> buildTimeSettingLocations(LifeScriptParser.SettingsContext ctx) {
        Map<String, SourceLocation> locations = new HashMap<>();
        for (LifeScriptParser.SettingsEntryContext entry : ctx.settingsEntry()) {
            locations.put(Keywords.parseNamedPeriod(entry.namedPeriod().getText()), DiagnosticCollector.locationOf(entry));
        }
        return locations;
    }

    private Map<DayOfWeek, SourceLocation> buildAvailabilityLocations(LifeScriptParser.AvailabilityContext ctx) {
        Map<DayOfWeek, SourceLocation> locations = new EnumMap<>(DayOfWeek.class);
        for (LifeScriptParser.AvailabilityEntryContext entry : ctx.availabilityEntry()) {
            locations.put(Keywords.parseDay(entry.dayOfWeek().getText()), DiagnosticCollector.locationOf(entry));
        }
        return locations;
    }
}
