package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

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
        String planName = ctx.IDENTIFIER().getText();

        visit(ctx.period());

        Set<String> seenSections = new HashSet<>();
        boolean hasTasks = false;
        boolean hasAvailability = false;

        for (LifeScriptParser.PlanSectionContext section : ctx.planSection()) {
            String kind = sectionKind(section);
            if (!seenSections.add(kind)) {
                int sectionLine = section.getStart().getLine();
                errors.add(String.format("Line %d: Plan has duplicate '%s' section", sectionLine, kind));
                continue;
            }
            if (section.tasks() != null) hasTasks = true;
            if (section.availability() != null) hasAvailability = true;
            visit(section);
        }

        int line = ctx.getStart().getLine();
        if (!hasTasks) {
            errors.add(String.format("Line %d: Plan %s is missing mandatory property: tasks", line, planName));
        }
        if (!hasAvailability) {
            errors.add(String.format("Line %d: Plan %s is missing mandatory property: availability", line, planName));
        }

        return null;
    }

    private String sectionKind(LifeScriptParser.PlanSectionContext ctx) {
        if (ctx.settings() != null) return "settings";
        if (ctx.availability() != null) return "availability";
        if (ctx.energyProfile() != null) return "energy profile";
        if (ctx.routines() != null) return "routines";
        return "tasks";
    }

    @Override
    public Void visitTask(LifeScriptParser.TaskContext ctx) {
        String taskName = ctx.IDENTIFIER().getText();

        // Every property starts with its keyword (duration, priority, ...), so the first token is its key.
        checkDuplicateKeys(ctx.taskProperty(), p -> p.getStart().getText(), "task property");

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

    private <T extends ParserRuleContext> void checkDuplicateKeys(
            List<T> entries, Function<T, String> keyOf, String label) {
        Set<String> seen = new HashSet<>();
        for (T entry : entries) {
            String key = keyOf.apply(entry);
            if (!seen.add(key)) {
                int line = entry.getStart().getLine();
                errors.add(String.format("Line %d: Duplicate %s '%s'", line, label, key));
            }
        }
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
            String taskName = task.IDENTIFIER().getText();
            if (!taskNames.add(taskName)) {
                int line = task.getStart().getLine();
                errors.add(String.format("Line %d: Duplicate task name %s", line, taskName));
            }
        }

        for (LifeScriptParser.TaskContext task: ctx.task()) {
            String taskName = task.IDENTIFIER().getText();
            List<String> deps = new ArrayList<>();

            for (LifeScriptParser.TaskPropertyContext prop : task.taskProperty()) {
                if (prop.taskDependencies() != null) {
                    for (TerminalNode dep : prop.taskDependencies().IDENTIFIER()) {
                        if (!taskNames.contains(dep.getText())) {
                            int line = prop.getStart().getLine();
                            errors.add(String.format("Line %d: Task %s has non-existing dependency task %s ", line, task.IDENTIFIER().getText(), dep.getText()));
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
    public Void visitRoutines(LifeScriptParser.RoutinesContext ctx) {
        checkDuplicateKeys(ctx.routineEntry(), e -> e.IDENTIFIER().getText(), "routine name");
        return visitChildren(ctx);
    }

    @Override
    public Void visitRoutineActivities(LifeScriptParser.RoutineActivitiesContext ctx) {
        checkDuplicateKeys(ctx.activityEntry(), e -> e.IDENTIFIER().getText(), "activity name");
        return visitChildren(ctx);
    }

    @Override
    public Void visitRoutineEntry(LifeScriptParser.RoutineEntryContext ctx) {
        String routineName = ctx.IDENTIFIER().getText();

        checkDuplicateKeys(ctx.routineProperty(), p -> p.getStart().getText(), "routine property");

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

    @Override
    public Void visitTaskDeadline(LifeScriptParser.TaskDeadlineContext ctx) {
        int line = ctx.getStart().getLine();

        if (ctx.DATE() != null) {
            try {
                Literals.parseDate(ctx.DATE().getText());
            }
            catch (DateTimeParseException e) {
                String invalidDate = ctx.DATE().getText();
                errors.add(String.format("Line %d: Invalid deadline date '%s'", line, invalidDate));
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitTimeRange(LifeScriptParser.TimeRangeContext ctx) {
        // Hour/minute ranges are already enforced by the TIME_VAL lexer rule.
        int line = ctx.getStart().getLine();
        LocalTime start = Literals.parseTime(ctx.TIME_VAL(0).getText());
        LocalTime end = Literals.parseTime(ctx.TIME_VAL(1).getText());

        if (!start.isBefore(end)) {
            errors.add(String.format("Line %d: Start time '%s' must be before end time '%s'",
                    line, ctx.TIME_VAL(0).getText(), ctx.TIME_VAL(1).getText()));
        }
        return visitChildren(ctx);
    }


    @Override
    public Void visitPeriod(LifeScriptParser.PeriodContext ctx) {
        int line = ctx.getStart().getLine();

        try {
            periodStart = Literals.parseDate(ctx.DATE(0).getText());
        } catch (DateTimeParseException e) {
            String invalidDate = ctx.DATE(0).getText();
            errors.add(String.format("Line %d: Invalid date '%s'",
                    line, invalidDate));
            return visitChildren(ctx);
        }

        try {
            periodEnd = Literals.parseDate(ctx.DATE(1).getText());
        } catch (DateTimeParseException e) {
            String invalidDate = ctx.DATE(1).getText();
            errors.add(String.format("Line %d: Invalid date '%s'",
                    line, invalidDate));
            return visitChildren(ctx);
        }

        if (periodStart != null && periodEnd != null && !periodStart.isBefore(periodEnd)) {
            errors.add(String.format("Line %d: Start date '%s' must be before end date '%s'",
                    line, periodStart.toString(), periodEnd.toString()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitSettings(LifeScriptParser.SettingsContext ctx) {
        checkDuplicateKeys(ctx.settingsEntry(), e -> e.namedPeriod().getText(), "settings entry");
        return visitChildren(ctx);
    }

    @Override
    public Void visitAvailability(LifeScriptParser.AvailabilityContext ctx) {
        checkDuplicateKeys(ctx.availabilityEntry(), e -> e.dayOfWeek().getText(), "availability day");
        return visitChildren(ctx);
    }

    @Override
    public Void visitEnergyProfile(LifeScriptParser.EnergyProfileContext ctx) {
        checkDuplicateKeys(ctx.energyProfileEntry(),
                e -> e.DEFAULT() != null ? "default" : e.dayOfWeek().getText(), "energy profile entry");
        return visitChildren(ctx);
    }

    @Override
    public Void visitEnergyProfileEntry(LifeScriptParser.EnergyProfileEntryContext ctx) {
        checkDuplicateKeys(ctx.energyEntry(),
                e -> e.namedPeriod() != null ? e.namedPeriod().getText() : e.timeRange().getText(), "energy entry");
        return visitChildren(ctx);
    }

    @Override
    public Void visitAvailabilityEntry(LifeScriptParser.AvailabilityEntryContext ctx) {
        int line = ctx.getStart().getLine();

        String dayOfWeek = ctx.dayOfWeek().getText();
        DayOfWeek day = Literals.parseDay(dayOfWeek);

        if (periodStart != null && periodEnd != null) {
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
        }

        return visitChildren(ctx);
    }
}

