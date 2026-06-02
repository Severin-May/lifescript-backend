package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class SemanticValidator extends LifeScriptParserBaseVisitor<Void> {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private final List<String> errors = new ArrayList<>();
    private final Set<String> taskNames = new HashSet<>();
    private final Map<String, List<String>> dependencyGraph = new HashMap<>();

    private static final String WHITE = "WHITE";
    private static final String GRAY = "GRAY";
    private static final String BLACK = "BLACK";

    List<String> getErrors() {
        return this.errors;
    }

    boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    @Override
    public Void visitPlan(LifeScriptParser.PlanContext ctx) {
        String planName = ctx.STRING().getText();

        boolean hasTasks = false;
        for (LifeScriptParser.PlanSectionContext prop : ctx.planSection()) {
            if (prop.tasks() != null) hasTasks = true;
        }

        int line = ctx.getStart().getLine();
        if (!hasTasks) {
            errors.add(String.format("Line %d: Plan %s is missing mandatory property: tasks", line, planName));
        }

        return visitChildren(ctx);
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

    private boolean detectTaskCycle(String task, Map<String, String> colors, List<String> path) {
        if (GRAY.equals(colors.get(task))) {
            int cycleStart = path.indexOf(task);
            List<String> cycle = path.subList(cycleStart, path.size());
            errors.add(String.format("Circular dependency detected: %s → %s",
                    String.join(" → ", cycle), task));
            return true;
        }
        if (BLACK.equals(colors.get(task))) return false;

        colors.put(task, GRAY);
        path.add(task);

        for (String dep : dependencyGraph.getOrDefault(task, List.of())) {
            if (detectTaskCycle(dep, colors, path)) return true;
        }

        path.remove(path.size() - 1);
        colors.put(task, BLACK);
        return false;
    }

    @Override
    public Void visitTasks(LifeScriptParser.TasksContext ctx) {

        for (LifeScriptParser.TaskContext task: ctx.task()) {
            String taskName = task.STRING().getText();
            if (!taskNames.add(taskName)) {
                int line = task.getStart().getLine();
                errors.add(String.format("Line %d: Duplicate task name %s", line, taskName));
            }
        }

        for (LifeScriptParser.TaskContext task: ctx.task()) {
            String taskName = task.STRING().getText();
            List<String> deps = new ArrayList<>();

            for (LifeScriptParser.TaskPropertyContext prop : task.taskProperty()) {
                if (prop.taskDependencies() != null) {
                    for (TerminalNode dep : prop.taskDependencies().STRING()) {
                        if (!taskNames.contains(dep.getText())) {
                            int line = prop.getStart().getLine();
                            errors.add(String.format("Line %d: Task %s has non-existing dependency task %s ", line, task.STRING().getText(), dep.getText()));
                        }
                        deps.add(dep.getText());
                    }
                }
            }
            visit(task);
            dependencyGraph.put(taskName, deps);
        }

        Map<String, String> colors = new HashMap<>();
        for (String taskName : taskNames) {
            if (!colors.containsKey(taskName)) {
                detectTaskCycle(taskName, colors, new ArrayList<>());
            }
        }
        return null;
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

    private boolean validateTime(String time, int line) {
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
        periodStart = LocalDate.parse(ctx.DATE(0).getText());
        periodEnd = LocalDate.parse(ctx.DATE(1).getText());

        if (!periodStart.isBefore(periodEnd)) {
            int line = ctx.getStart().getLine();
            errors.add(String.format("Line %d: Start date '%s' must be before end date '%s'",
                    line, periodStart.toString(), periodEnd.toString()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitAvailabilityEntry(LifeScriptParser.AvailabilityEntryContext ctx) {
        int line = ctx.getStart().getLine();

        if (ctx.dayOfWeek() != null) {
            String dayOfWeek = ctx.dayOfWeek().getText();
            DayOfWeek day = DayOfWeek.valueOf(dayOfWeek.toUpperCase());
            boolean found = false;

            LocalDate current = periodStart;
            while (!current.isAfter(periodEnd)) {
                if (current.getDayOfWeek() == day) {
                    found = true;
                    break;
                }
                current = current.plusDays(1);
            }

            if (!found) {
                errors.add(String.format("Line %d: Availability day of week '%s' must be within the period range '%s'-'%s'",
                        line, dayOfWeek, periodStart.toString(), periodEnd.toString()));
            }
        } else if (ctx.DATE() != null) {
            LocalDate inputDate = LocalDate.parse(ctx.DATE().getText());
            if (inputDate.isBefore(periodStart)) {
                errors.add(String.format("Line %d: Availability date '%s' is before the period start date '%s'",
                        line, inputDate.toString(), periodStart.toString()));
            }
            if (inputDate.isAfter(periodEnd)) {
                errors.add(String.format("Line %d: Availability date '%s' is after the period end date '%s'",
                        line, inputDate.toString(), periodEnd.toString()));
            }
        }

        return visitChildren(ctx);
    }
}

