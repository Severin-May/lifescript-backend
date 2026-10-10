package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;
import com.lifescript.model.TimeRange;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.time.DayOfWeek;
import java.time.Duration;
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
        if (ctx.tasks() != null) return "tasks";
        if (ctx.events() != null) return "events";
        // Fail loudly if a new section is added to the grammar but not here.
        throw new IllegalStateException("sectionKind: unhandled section " + ctx.getText());
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
        Set<String> repeatingTasks = new HashSet<>();

        for (LifeScriptParser.TaskContext task: ctx.task()) {
            String taskName = task.IDENTIFIER().getText();
            if (!taskNames.add(taskName)) {
                int line = task.getStart().getLine();
                errors.add(String.format("Line %d: Duplicate task name %s", line, taskName));
            }
            if (task.taskProperty().stream().anyMatch(p -> p.repeats() != null)) {
                repeatingTasks.add(taskName);
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
                        // Dependencies describe a one-off sequence of work, which a repeating task doesn't fit into.
                        if (repeatingTasks.contains(dep.getText())) {
                            int line = prop.getStart().getLine();
                            errors.add(String.format("Line %d: Task %s cannot depend on repeating task %s", line, taskName, dep.getText()));
                        }
                        deps.add(dep.getText());
                    }
                    if (repeatingTasks.contains(taskName)) {
                        int line = prop.getStart().getLine();
                        errors.add(String.format("Line %d: Repeating task %s cannot have dependencies", line, taskName));
                    }
                }
                // Each occurrence of a repeating task is due on its own day, so a single deadline is ambiguous.
                if (prop.taskDeadline() != null && repeatingTasks.contains(taskName)) {
                    int line = prop.getStart().getLine();
                    errors.add(String.format("Line %d: Repeating task %s cannot have a deadline", line, taskName));
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
        boolean hasRepeats = false;
        String startText = null;
        Duration total = Duration.ZERO;

        for (LifeScriptParser.RoutinePropertyContext prop : ctx.routineProperty()) {
            if (prop.routineTime() != null) {
                hasTime = true;
                startText = prop.routineTime().TIME_VAL().getText();
            }
            if (prop.routineActivities() != null) {
                hasActivities = true;
                for (LifeScriptParser.ActivityEntryContext activity : prop.routineActivities().activityEntry()) {
                    total = total.plus(Literals.parseDuration(activity.DURATION_VAL().getText()));
                }
            }
            if (prop.repeats() != null) hasRepeats = true;
        }

        int line = ctx.getStart().getLine();
        if (!hasTime) {
            errors.add(String.format("Line %d: Routine %s is missing mandatory property: time", line, routineName));
        }
        if (!hasActivities) {
            errors.add(String.format("Line %d: Routine %s is missing mandatory property: activities", line, routineName));
        }
        // Repeating is what distinguishes a routine from a task.
        if (!hasRepeats) {
            errors.add(String.format("Line %d: Routine %s is missing mandatory property: repeats", line, routineName));
        }

        // The routine lasts as long as its activities and, like a time range, can't go past midnight.
        // An invalid start time is already reported by visitRoutineTime.
        if (startText != null && hasActivities) {
            try {
                LocalTime start = Literals.parseTime(startText);
                if (start.toSecondOfDay() + total.toSeconds() > Duration.ofDays(1).toSeconds()) {
                    errors.add(String.format("Line %d: Routine %s starting at %s lasts %s and would run past midnight",
                            line, routineName, startText, formatDuration(total)));
                }
            } catch (DateTimeParseException ignored) {
            }
        }

        return visitChildren(ctx);
    }

    // Applies to both task and routine repeats, e.g. "repeats: monday, monday".
    @Override
    public Void visitDayList(LifeScriptParser.DayListContext ctx) {
        checkDuplicateKeys(ctx.dayOfWeek(), d -> d.getText(), "day in repeats");
        return visitChildren(ctx);
    }

    @Override
    public Void visitEvents(LifeScriptParser.EventsContext ctx) {
        checkDuplicateKeys(ctx.event(), e -> e.IDENTIFIER().getText(), "event name");
        return visitChildren(ctx);
    }

    @Override
    public Void visitEvent(LifeScriptParser.EventContext ctx) {
        String eventName = ctx.IDENTIFIER().getText();

        checkDuplicateKeys(ctx.eventProperty(), p -> p.getStart().getText(), "event property");

        boolean hasDate = false;
        boolean hasTime = false;
        for (LifeScriptParser.EventPropertyContext prop : ctx.eventProperty()) {
            if (prop.eventDate() != null) hasDate = true;
            if (prop.eventTime() != null) hasTime = true;
        }

        int line = ctx.getStart().getLine();
        if (!hasDate) {
            errors.add(String.format("Line %d: Event %s is missing mandatory property: date", line, eventName));
        }
        if (!hasTime) {
            errors.add(String.format("Line %d: Event %s is missing mandatory property: time", line, eventName));
        }

        return visitChildren(ctx);
    }

    @Override
    public Void visitEventDate(LifeScriptParser.EventDateContext ctx) {
        int line = ctx.getStart().getLine();
        String text = ctx.DATE_VAL().getText();

        LocalDate date;
        try {
            date = Literals.parseDate(text);
        } catch (DateTimeParseException e) {
            errors.add(String.format("Line %d: Invalid event date '%s'", line, text));
            return visitChildren(ctx);
        }

        if (periodStart != null && periodEnd != null && (date.isBefore(periodStart) || date.isAfter(periodEnd))) {
            errors.add(String.format("Line %d: Event date '%s' must be within the period range '%s'-'%s'",
                    line, text, periodStart, periodEnd));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitRoutineTime(LifeScriptParser.RoutineTimeContext ctx) {
        checkTime(ctx.TIME_VAL().getText(), ctx.getStart().getLine());
        return visitChildren(ctx);
    }

    // 95 minutes -> "1h35m", 2 hours -> "2h", 45 minutes -> "45m" (same style as DURATION_VAL)
    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours == 0) return minutes + "m";
        return minutes == 0 ? hours + "h" : hours + "h" + minutes + "m";
    }

    @Override
    public Void visitTaskDeadline(LifeScriptParser.TaskDeadlineContext ctx) {
        int line = ctx.getStart().getLine();

        if (ctx.DATE_VAL() != null) {
            try {
                Literals.parseDate(ctx.DATE_VAL().getText());
            }
            catch (DateTimeParseException e) {
                String invalidDate = ctx.DATE_VAL().getText();
                errors.add(String.format("Line %d: Invalid deadline date '%s'", line, invalidDate));
            }
        }
        return visitChildren(ctx);
    }

    // The lexer accepts any HH:MM; this rejects values like 25:00 or 09:61.
    // Returns null (after recording an error) when the time is invalid.
    private LocalTime checkTime(String text, int line) {
        try {
            return Literals.parseTime(text);
        } catch (DateTimeParseException e) {
            errors.add(String.format("Line %d: Invalid time '%s'", line, text));
            return null;
        }
    }

    @Override
    public Void visitTimeRange(LifeScriptParser.TimeRangeContext ctx) {
        int line = ctx.getStart().getLine();
        LocalTime start = checkTime(ctx.TIME_VAL(0).getText(), line);
        LocalTime end = checkTime(ctx.TIME_VAL(1).getText(), line);

        if (start == null || end == null) {
            return visitChildren(ctx);
        }

        TimeRange range = new TimeRange();
        range.setStartTime(start);
        range.setEndTime(end);

        if (!range.isOrdered()) {
            // End earlier than start usually means the range was meant to cross midnight.
            String hint = end.isBefore(start) && !range.endsAtMidnight()
                    ? " (to go past midnight, end at 00:00 and continue on the next day)"
                    : "";
            errors.add(String.format("Line %d: Start time '%s' must be before end time '%s'%s",
                    line, ctx.TIME_VAL(0).getText(), ctx.TIME_VAL(1).getText(), hint));
        }
        return visitChildren(ctx);
    }


    @Override
    public Void visitPeriod(LifeScriptParser.PeriodContext ctx) {
        int line = ctx.getStart().getLine();

        try {
            periodStart = Literals.parseDate(ctx.DATE_VAL(0).getText());
        } catch (DateTimeParseException e) {
            String invalidDate = ctx.DATE_VAL(0).getText();
            errors.add(String.format("Line %d: Invalid date '%s'",
                    line, invalidDate));
            return visitChildren(ctx);
        }

        try {
            periodEnd = Literals.parseDate(ctx.DATE_VAL(1).getText());
        } catch (DateTimeParseException e) {
            String invalidDate = ctx.DATE_VAL(1).getText();
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

