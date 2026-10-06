package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;
import com.lifescript.model.EnergyLevel;
import com.lifescript.model.Plan;
import com.lifescript.model.Priority;
import com.lifescript.model.Routine;
import com.lifescript.model.Task;
import com.lifescript.model.TimeRange;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanModelBuilder extends LifeScriptParserBaseVisitor<Void> {

    private static final Map<String, TimeRange> DEFAULT_TIME_SETTINGS = Map.of(
            "morning", timeRange(LocalTime.of(6, 0), LocalTime.of(12, 0)),
            "afternoon", timeRange(LocalTime.of(12, 0), LocalTime.of(18, 0)),
            "evening", timeRange(LocalTime.of(18, 0), LocalTime.of(22, 0))
    );

    private final Plan plan = new Plan();

    public Plan getPlan() {
        return plan;
    }

    // Single entry point: the only method that writes to `plan`. Every section
    // is built by a build* method that returns its result, and anything a
    // builder depends on (e.g. time settings) is passed in explicitly.
    @Override
    public Void visitPlan(LifeScriptParser.PlanContext ctx) {
        plan.setName(ctx.IDENTIFIER().getText());
        plan.setStartDate(Literals.parseDate(ctx.period().DATE(0).getText()));
        plan.setEndDate(Literals.parseDate(ctx.period().DATE(1).getText()));

        // Resolve named periods (morning/afternoon/evening) before building any
        // section that references them, regardless of where "settings" appears
        // in the file.
        Map<String, TimeRange> timeSettings = resolveTimeSettings(ctx);
        plan.setTimeSettings(timeSettings);

        for (LifeScriptParser.PlanSectionContext section : ctx.planSection()) {
            if (section.routines() != null) {
                plan.setRoutines(buildRoutines(section.routines(), timeSettings));
            } else if (section.tasks() != null) {
                plan.setTasks(buildTasks(section.tasks()));
            }
        }

        return null;
    }

    private List<Routine> buildRoutines(LifeScriptParser.RoutinesContext ctx,
                                        Map<String, TimeRange> timeSettings) {
        List<Routine> routines = new ArrayList<>();
        for (LifeScriptParser.RoutineEntryContext entry : ctx.routineEntry()) {
            routines.add(buildRoutine(entry, timeSettings));
        }
        return routines;
    }

    private Routine buildRoutine(LifeScriptParser.RoutineEntryContext ctx,
                                 Map<String, TimeRange> timeSettings) {
        Routine routine = new Routine();
        routine.setName(ctx.IDENTIFIER().getText());

        for (LifeScriptParser.RoutinePropertyContext prop : ctx.routineProperty()) {
            if (prop.routineTime() != null) {
                routine.setTimeRange(resolveTimeRange(prop.routineTime(), timeSettings));
            }
        }

        return routine;
    }

    private TimeRange resolveTimeRange(LifeScriptParser.RoutineTimeContext ctx,
                                       Map<String, TimeRange> timeSettings) {
        if (ctx.timeRange() != null) {
            return Literals.parseTimeRange(ctx.timeRange().getText());
        }
        return timeSettings.get(ctx.namedPeriod().getText());
    }

    private List<Task> buildTasks(LifeScriptParser.TasksContext ctx) {
        List<Task> tasks = new ArrayList<>();
        for (LifeScriptParser.TaskContext entry : ctx.task()) {
            tasks.add(buildTask(entry));
        }
        return tasks;
    }

    private Task buildTask(LifeScriptParser.TaskContext ctx) {
        Task task = new Task();
        task.setName(ctx.IDENTIFIER().getText());

        for (LifeScriptParser.TaskPropertyContext prop : ctx.taskProperty()) {
            if (prop.taskDuration() != null) {
                task.setDuration(Literals.parseDuration(prop.taskDuration().DURATION_VAL().getText()));
            } else if (prop.taskPriority() != null) {
                task.setPriority(Priority.valueOf(prop.taskPriority().priorityLevel().getText().toUpperCase()));
            } else if (prop.taskEffort() != null) {
                task.setEffort(EnergyLevel.valueOf(prop.taskEffort().energyLevel().getText().toUpperCase()));
            } else if (prop.taskDeadline() != null) {
                task.setDeadline(Literals.parseDate(prop.taskDeadline().DATE().getText()));
            } else if (prop.taskStart() != null) {
                task.setStart(Literals.parseTime(prop.taskStart().TIME_VAL().getText()));
            } else if (prop.repeats() != null) {
                task.setRepeatDays(buildRepeatDays(prop.repeats().repeatPattern()));
            } else if (prop.taskDependencies() != null) {
                List<String> dependencies = new ArrayList<>();
                for (TerminalNode name : prop.taskDependencies().IDENTIFIER()) {
                    dependencies.add(name.getText());
                }
                task.setDependencies(dependencies);
            } else if (prop.taskNote() != null) {
                String quoted = prop.taskNote().STRING().getText();
                task.setNote(quoted.substring(1, quoted.length() - 1));
            }
        }

        return task;
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
            days.add(Literals.parseDay(day.getText()));
        }
        return days;
    }

    private Map<String, TimeRange> resolveTimeSettings(LifeScriptParser.PlanContext ctx) {
        Map<String, TimeRange> timeSettings = new HashMap<>(DEFAULT_TIME_SETTINGS);

        for (LifeScriptParser.PlanSectionContext section : ctx.planSection()) {
            if (section.settings() != null) {
                for (LifeScriptParser.SettingsEntryContext entry : section.settings().settingsEntry()) {
                    timeSettings.put(entry.namedPeriod().getText(), Literals.parseTimeRange(entry.timeRange().getText()));
                }
            }
        }

        return timeSettings;
    }

    private static TimeRange timeRange(LocalTime startTime, LocalTime endTime) {
        TimeRange range = new TimeRange();
        range.setStartTime(startTime);
        range.setEndTime(endTime);
        return range;
    }
}
