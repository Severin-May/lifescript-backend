package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class SemanticValidator extends LifeScriptParserBaseVisitor<Void> {
    private final List<String> errors = new ArrayList<>();

    List<String> getErrors() {
        return this.errors;
    }

    boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    @Override
    public Void visitTask(LifeScriptParser.TaskContext ctx) {
        String taskName = ctx.STRING().getText();

        boolean hasDuration = false;
        boolean hasPriority = false;
        boolean hasEffort = false;

        for (LifeScriptParser.TaskPropertyContext prop : ctx.taskProperty()) {
            if (prop.taskDuration() != null) hasDuration = true;
            if (prop.taskPriority() != null) hasPriority = true;
            if (prop.taskEffort() != null) hasEffort = true;
        }

        int line = ctx.getStart().getLine();
        if (!hasDuration) {
            errors.add(String.format("Line %d: Task %s is missing mandatory property: duration", line, taskName));
        }
        if (!hasPriority) {
            errors.add(String.format("Line %d: Task %s is missing mandatory property: priority", line, taskName));
        }
        if (!hasEffort) {
            errors.add(String.format("Line %d: Task %s is missing mandatory property: effort", line, taskName));
        }

        return visitChildren(ctx);
    }

    @Override
    public Void visitRoutineEntry(LifeScriptParser.RoutineEntryContext ctx) {
        String routineName = ctx.STRING().getText();

        boolean hasTime = false;
        boolean hasActivities = false;

        for (LifeScriptParser.RoutinePropertyContext prop : ctx.routineProperty()) {
            if (prop.routineTime() != null) hasTime = true;
            if (prop.routineActivities() != null) hasActivities = true;
        }

        int line = ctx.getStart().getLine();
        if (!hasTime) {
            errors.add(String.format("Line %d: Routine %s is missing mandatory property: time", line, routineName));
        }
        if (!hasActivities) {
            errors.add(String.format("Line %d: Routine %s is missing mandatory property: activities", line, routineName));
        }

        return visitChildren(ctx);
    }

    boolean validateTime(String time, int line) {
        String[] parts = time.split(":");
        int hours = Integer.parseInt(parts[0]);
        int mins = Integer.parseInt(parts[1]);

        boolean isValid = true;
        if  (hours > 23) {
            errors.add(String.format("Line %d: Invalid hour '%d' in time '%s' — must be between 0 and 23", line, hours, time));
            isValid = false;
        }
        if (mins > 59) {
            errors.add(String.format("Line %d: Invalid minute '%d' in time '%s' — must be between 0 and 59", line, mins, time));
            isValid = false;
        }

        return isValid;
    }

    @Override
    public Void visitTaskStart(LifeScriptParser.TaskStartContext ctx) {
        int line = ctx.getStart().getLine();
        validateTime(ctx.TIME_VAL().getText(), line);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTimeRange(LifeScriptParser.TimeRangeContext ctx) {
        int line = ctx.getStart().getLine();
        boolean startValid = validateTime(ctx.TIME_VAL(0).getText(), line);
        boolean endValid = validateTime(ctx.TIME_VAL(1).getText(), line);

        if (startValid && endValid) {
            LocalTime start = LocalTime.parse(ctx.TIME_VAL(0).getText());
            LocalTime end = LocalTime.parse(ctx.TIME_VAL(1).getText());

            if (!start.isBefore(end)) {
                errors.add(String.format("Line %d: Start time '%s' must be before end time '%s'",
                        line, ctx.TIME_VAL(0).getText(), ctx.TIME_VAL(1).getText()));
            }
        }
        return visitChildren(ctx);
    }


    @Override
    public Void visitPeriod(LifeScriptParser.PeriodContext ctx) {
        LocalDate startDate = LocalDate.parse(ctx.DATE(0).getText());
        LocalDate endDate = LocalDate.parse(ctx.DATE(1).getText());

        int line = ctx.getStart().getLine();
        if (!startDate.isBefore(endDate)) {
            errors.add(String.format("Line %d: Start date '%s' must be before end date '%s'",
                    line, startDate.toString(), endDate.toString()));
        }
        return visitChildren(ctx);
    }
}
