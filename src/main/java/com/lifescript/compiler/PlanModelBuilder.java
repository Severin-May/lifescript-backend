package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;
import com.lifescript.model.Plan;
import com.lifescript.model.Routine;
import com.lifescript.model.TimeRange;

import java.time.LocalDate;
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

    @Override
    public Void visitPlan(LifeScriptParser.PlanContext ctx) {
        plan.setName(ctx.IDENTIFIER().getText());
        visit(ctx.period());

        // Resolve named periods (morning/afternoon/evening) before building any
        // section that references them, regardless of where "settings" appears
        // in the file.
        plan.setTimeSettings(resolveTimeSettings(ctx));

        for (LifeScriptParser.PlanSectionContext section : ctx.planSection()) {
            if (section.settings() == null) {
                visit(section);
            }
        }

        return null;
    }

    @Override
    public Void visitPeriod(LifeScriptParser.PeriodContext ctx) {
        plan.setStartDate(LocalDate.parse(ctx.DATE(0).getText()));
        plan.setEndDate(LocalDate.parse(ctx.DATE(1).getText()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitRoutines(LifeScriptParser.RoutinesContext ctx) {
        List<Routine> routines = new ArrayList<>();
        for (LifeScriptParser.RoutineEntryContext entry : ctx.routineEntry()) {
            routines.add(buildRoutine(entry));
        }
        plan.setRoutines(routines);
        return null;
    }

    private Routine buildRoutine(LifeScriptParser.RoutineEntryContext ctx) {
        Routine routine = new Routine();
        routine.setName(ctx.IDENTIFIER().getText());

        for (LifeScriptParser.RoutinePropertyContext prop : ctx.routineProperty()) {
            if (prop.routineTime() != null) {
                routine.setTimeRange(resolveTimeRange(prop.routineTime()));
            }
        }

        return routine;
    }

    private TimeRange resolveTimeRange(LifeScriptParser.RoutineTimeContext ctx) {
        if (ctx.timeRange() != null) {
            return buildTimeRange(ctx.timeRange());
        }
        return plan.getTimeSettings().get(ctx.namedPeriod().getText());
    }

    private Map<String, TimeRange> resolveTimeSettings(LifeScriptParser.PlanContext ctx) {
        Map<String, TimeRange> timeSettings = new HashMap<>(DEFAULT_TIME_SETTINGS);

        for (LifeScriptParser.PlanSectionContext section : ctx.planSection()) {
            if (section.settings() != null) {
                for (LifeScriptParser.SettingsEntryContext entry : section.settings().settingsEntry()) {
                    timeSettings.put(entry.namedPeriod().getText(), buildTimeRange(entry.timeRange()));
                }
            }
        }

        return timeSettings;
    }

    private TimeRange buildTimeRange(LifeScriptParser.TimeRangeContext ctx) {
        TimeRange range = new TimeRange();
        range.setStartTime(LocalTime.parse(ctx.TIME_VAL(0).getText()));
        range.setEndTime(LocalTime.parse(ctx.TIME_VAL(1).getText()));
        return range;
    }

    private static TimeRange timeRange(LocalTime startTime, LocalTime endTime) {
        TimeRange range = new TimeRange();
        range.setStartTime(startTime);
        range.setEndTime(endTime);
        return range;
    }
}
