package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;
import com.lifescript.model.PlanPeriod;
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

// Checks the parse tree for problems the grammar can't express (missing, duplicate or malformed values),
// reporting each one at the entry it is about.
public class SemanticValidator extends LifeScriptParserBaseVisitor<Void> {
    // Set by visitPeriod when both dates are valid; stays null otherwise, and checks that need it are skipped.
    private PlanPeriod period;
    private final DiagnosticCollector diagnostics;
    private final Set<String> taskNames = new HashSet<>();
    private final Map<String, List<String>> dependencyGraph = new HashMap<>();
    // Each task's "dependencies:" line, where a circular dependency through it is reported.
    private final Map<String, ParserRuleContext> dependencyLines = new HashMap<>();

    private static final String GRAY = "GRAY";
    private static final String BLACK = "BLACK";

    private static final Duration MAX_DURATION = Duration.ofHours(24);

    public SemanticValidator(DiagnosticCollector diagnostics) {
        this.diagnostics = diagnostics;
    }

    @Override
    public Void visitPlan(LifeScriptParser.PlanContext ctx) {
        String planName = ctx.IDENTIFIER().getText();

        visit(ctx.period());

        boolean hasTasks = false;
        boolean hasAvailability = false;

        for (LifeScriptParser.PlanSectionContext section :
                checkDuplicateKeys(ctx.planSection(), this::sectionKind, "section")) {
            if (section.tasks() != null) hasTasks = true;
            if (section.availability() != null) hasAvailability = true;
            visit(section);
        }

        if (!hasTasks) {
            diagnostics.error(ctx.PLAN().getSymbol(), String.format("Plan %s is missing mandatory property: tasks", planName));
        }
        if (!hasAvailability) {
            diagnostics.error(ctx.PLAN().getSymbol(), String.format("Plan %s is missing mandatory property: availability", planName));
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
        List<LifeScriptParser.TaskPropertyContext> properties =
                checkDuplicateKeys(ctx.taskProperty(), p -> p.getStart().getText(), "task property");

        boolean hasDuration = false;
        boolean hasPriority = false;
        boolean hasEffort = false;

        for (LifeScriptParser.TaskPropertyContext prop : properties) {
            if (prop.taskDuration() != null) hasDuration = true;
            if (prop.taskPriority() != null) hasPriority = true;
            if (prop.taskEffort() != null) hasEffort = true;
        }

        if (!hasDuration) {
            diagnostics.error(ctx, String.format("Task %s is missing mandatory property: duration", taskName));
        }
        if (!hasPriority) {
            diagnostics.error(ctx, String.format("Task %s is missing mandatory property: priority", taskName));
        }
        if (!hasEffort) {
            diagnostics.error(ctx, String.format("Task %s is missing mandatory property: effort", taskName));
        }

        visitAll(properties);
        return null;
    }

    // Reports every entry whose key was already seen, and returns only the first occurrence of each key.
    // Callers validate just the returned entries: a duplicate is reported once and not validated further,
    // since anything referring to it by name couldn't tell the copies apart.
    private <T extends ParserRuleContext> List<T> checkDuplicateKeys(
            List<T> entries, Function<T, String> keyOf, String label) {
        Set<String> seen = new HashSet<>();
        List<T> unique = new ArrayList<>();
        for (T entry : entries) {
            String key = keyOf.apply(entry);
            if (seen.add(key)) {
                unique.add(entry);
            } else {
                diagnostics.error(entry, String.format("Duplicate %s '%s'", label, key));
            }
        }
        return unique;
    }

    private void visitAll(List<? extends ParserRuleContext> nodes) {
        for (ParserRuleContext node : nodes) {
            visit(node);
        }
    }

    // GRAY = on the current path, BLACK = fully checked, not in `colors` = not visited yet. The search always finishes a task
    // (even after finding a cycle), so a GRAY task is always on `path` and reaching it again is a cycle.
    // The cycle is reported at the "dependencies:" line of the task that closes it (the last one on the path),
    // e.g. for a → b → a at b's "dependencies: a".
    private void detectTaskCycle(String task, Map<String, String> colors, List<String> path) {
        if (GRAY.equals(colors.get(task))) {
            int cycleStart = path.indexOf(task);
            List<String> cycle = path.subList(cycleStart, path.size());
            String closingTask = path.get(path.size() - 1);
            diagnostics.error(dependencyLines.get(closingTask), String.format("Circular dependency detected: %s → %s",
                    String.join(" → ", cycle), task));
            return;
        }
        if (BLACK.equals(colors.get(task))) return;

        colors.put(task, GRAY);
        path.add(task);

        for (String dep : dependencyGraph.getOrDefault(task, List.of())) {
            detectTaskCycle(dep, colors, path);
        }

        path.remove(path.size() - 1);
        colors.put(task, BLACK);
    }

    @Override
    public Void visitTasks(LifeScriptParser.TasksContext ctx) {
        // Only the first task with each name takes part in the checks below (dependencies, cycles).
        List<LifeScriptParser.TaskContext> tasks =
                checkDuplicateKeys(ctx.task(), t -> t.IDENTIFIER().getText(), "task name");
        Set<String> repeatingTasks = new HashSet<>();

        for (LifeScriptParser.TaskContext task: tasks) {
            String taskName = task.IDENTIFIER().getText();
            taskNames.add(taskName);
            if (task.taskProperty().stream().anyMatch(p -> p.repeats() != null)) {
                repeatingTasks.add(taskName);
            }
        }

        for (LifeScriptParser.TaskContext task: tasks) {
            String taskName = task.IDENTIFIER().getText();
            List<String> deps = new ArrayList<>();

            for (LifeScriptParser.TaskPropertyContext prop : task.taskProperty()) {
                if (prop.taskDependencies() != null) {
                    dependencyLines.put(taskName, prop);
                    for (TerminalNode dep : prop.taskDependencies().IDENTIFIER()) {
                        if (!taskNames.contains(dep.getText())) {
                            diagnostics.error(prop, String.format("Task %s has non-existing dependency task %s ", task.IDENTIFIER().getText(), dep.getText()));
                        }
                        // Dependencies describe a one-off sequence of work, which a repeating task doesn't fit into.
                        if (repeatingTasks.contains(dep.getText())) {
                            diagnostics.error(prop, String.format("Task %s cannot depend on repeating task %s", taskName, dep.getText()));
                        }
                        deps.add(dep.getText());
                    }
                    if (repeatingTasks.contains(taskName)) {
                        diagnostics.error(prop, String.format("Repeating task %s cannot have dependencies", taskName));
                    }
                }
                // Each occurrence of a repeating task is due on its own day, so a single deadline is ambiguous.
                if (prop.taskDeadline() != null && repeatingTasks.contains(taskName)) {
                    diagnostics.error(prop, String.format("Repeating task %s cannot have a deadline", taskName));
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
        visitAll(checkDuplicateKeys(ctx.routineEntry(), e -> e.IDENTIFIER().getText(), "routine name"));
        return null;
    }

    @Override
    public Void visitRoutineActivities(LifeScriptParser.RoutineActivitiesContext ctx) {
        visitAll(checkDuplicateKeys(ctx.activityEntry(), e -> e.IDENTIFIER().getText(), "activity name"));
        return null;
    }

    @Override
    public Void visitRoutineEntry(LifeScriptParser.RoutineEntryContext ctx) {
        String routineName = ctx.IDENTIFIER().getText();

        List<LifeScriptParser.RoutinePropertyContext> properties =
                checkDuplicateKeys(ctx.routineProperty(), p -> p.getStart().getText(), "routine property");

        boolean hasTime = false;
        boolean hasActivities = false;
        boolean hasRepeats = false;
        String startText = null;
        Duration total = Duration.ZERO;
        boolean activityDurationsValid = true;

        for (LifeScriptParser.RoutinePropertyContext prop : properties) {
            if (prop.routineTime() != null) {
                hasTime = true;
                startText = prop.routineTime().TIME_VAL().getText();
            }
            if (prop.routineActivities() != null) {
                hasActivities = true;
                for (LifeScriptParser.ActivityEntryContext activity : prop.routineActivities().activityEntry()) {
                    Duration duration = parseDurationWithinLimit(activity.DURATION_VAL().getText());
                    if (duration == null) {
                        activityDurationsValid = false;
                    } else {
                        total = total.plus(duration);
                    }
                }
            }
            if (prop.repeats() != null) hasRepeats = true;
        }

        if (!hasTime) {
            diagnostics.error(ctx, String.format("Routine %s is missing mandatory property: time", routineName));
        }
        if (!hasActivities) {
            diagnostics.error(ctx, String.format("Routine %s is missing mandatory property: activities", routineName));
        }
        // Repeating is what distinguishes a routine from a task.
        if (!hasRepeats) {
            diagnostics.error(ctx, String.format("Routine %s is missing mandatory property: repeats", routineName));
        }

        // The routine lasts as long as its activities and, like a time range, can't go past midnight.
        // An invalid start time or activity duration is already reported by visitRoutineTime / visitActivityEntry.
        if (startText != null && hasActivities && activityDurationsValid) {
            try {
                LocalTime start = Literals.parseTime(startText);
                if (start.toSecondOfDay() + total.toSeconds() > Duration.ofDays(1).toSeconds()) {
                    diagnostics.error(ctx, String.format("Routine %s starting at %s lasts %s and would run past midnight",
                            routineName, startText, formatDuration(total)));
                }
            } catch (DateTimeParseException ignored) {
            }
        }

        visitAll(properties);
        return null;
    }

    // Applies to both task and routine repeats, e.g. "repeats: monday, monday".
    @Override
    public Void visitDayList(LifeScriptParser.DayListContext ctx) {
        checkDuplicateKeys(ctx.dayOfWeek(), d -> d.getText(), "day in repeats");
        return null;
    }

    @Override
    public Void visitEvents(LifeScriptParser.EventsContext ctx) {
        visitAll(checkDuplicateKeys(ctx.event(), e -> e.IDENTIFIER().getText(), "event name"));
        return null;
    }

    @Override
    public Void visitEvent(LifeScriptParser.EventContext ctx) {
        String eventName = ctx.IDENTIFIER().getText();

        List<LifeScriptParser.EventPropertyContext> properties =
                checkDuplicateKeys(ctx.eventProperty(), p -> p.getStart().getText(), "event property");

        boolean hasDate = false;
        boolean hasTime = false;
        for (LifeScriptParser.EventPropertyContext prop : properties) {
            if (prop.eventDate() != null) hasDate = true;
            if (prop.eventTime() != null) hasTime = true;
        }

        if (!hasDate) {
            diagnostics.error(ctx, String.format("Event %s is missing mandatory property: date", eventName));
        }
        if (!hasTime) {
            diagnostics.error(ctx, String.format("Event %s is missing mandatory property: time", eventName));
        }

        visitAll(properties);
        return null;
    }

    @Override
    public Void visitEventDate(LifeScriptParser.EventDateContext ctx) {
        String text = ctx.DATE_VAL().getText();

        LocalDate date;
        try {
            date = Literals.parseDate(text);
        } catch (DateTimeParseException e) {
            diagnostics.error(ctx, String.format("Invalid event date '%s'", text));
            return visitChildren(ctx);
        }

        if (period != null && !period.contains(date)) {
            diagnostics.error(ctx, String.format("Event date '%s' must be within the period range '%s'-'%s'",
                    text, period.start(), period.end()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitRoutineTime(LifeScriptParser.RoutineTimeContext ctx) {
        checkTime(ctx.TIME_VAL().getText(), ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTaskDuration(LifeScriptParser.TaskDurationContext ctx) {
        checkDuration(ctx.DURATION_VAL().getText(), ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitActivityEntry(LifeScriptParser.ActivityEntryContext ctx) {
        checkDuration(ctx.DURATION_VAL().getText(), ctx);
        return visitChildren(ctx);
    }

    // `entry` is the line the problem is reported at.
    private void checkDuration(String text, ParserRuleContext entry) {
        Duration duration = parseDurationWithinLimit(text);
        if (duration == null) {
            diagnostics.error(entry, String.format("Duration '%s' is too long (at most 24h)", text));
            return;
        }
        if (duration.isZero()) {
            diagnostics.error(entry, String.format("Duration '%s' must be greater than zero", text));
            return;
        }

        // Minutes after hours (the 75 in "1h75m") are most likely a typo, so they must stay under an hour.
        // Minutes on their own ("90m") are fine. The total is at most 24h here, so the number always parses.
        int hIndex = text.indexOf('h');
        if (hIndex >= 0 && hIndex < text.length() - 1) {
            long minutes = Long.parseLong(text.substring(hIndex + 1, text.length() - 1));
            if (minutes > 59) {
                diagnostics.error(entry, String.format("Minutes in '%s' must be at most 59 (write %s)",
                        text, formatDuration(duration)));
            }
        }
    }

    // A single task or activity can't last more than a day. Returns null for anything longer,
    // including values too large to read at all (e.g. 99999999999999999999m).
    private static Duration parseDurationWithinLimit(String text) {
        try {
            Duration duration = Literals.parseDuration(text);
            return duration.compareTo(MAX_DURATION) <= 0 ? duration : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        String dateText = ctx.DATE_VAL().getText();

        // The grammar allows a missing time only so this can explain it better than a syntax error would.
        if (ctx.TIME_VAL() == null) {
            diagnostics.error(ctx, String.format("Deadline needs a time, e.g. 'deadline: %s 17:00'", dateText));
            return visitChildren(ctx);
        }
        String timeText = ctx.TIME_VAL().getText();

        // The time is checked on its own, so an invalid date and an invalid time are both reported.
        checkTime(timeText, ctx);

        try {
            LocalDate deadline = Literals.parseDate(dateText);
            // A deadline after the period ends is allowed: it still orders tasks by urgency.
            if (period != null && deadline.isBefore(period.start())) {
                diagnostics.error(ctx, String.format("Deadline '%s %s' is before the period starts (%s)",
                        dateText, timeText, period.start()));
            }
        }
        catch (DateTimeParseException e) {
            diagnostics.error(ctx, String.format("Invalid deadline date '%s'", dateText));
        }
        return visitChildren(ctx);
    }

    // The lexer accepts any HH:MM; this rejects values like 25:00 or 09:61.
    // Returns null (after reporting it at `entry`) when the time is invalid.
    private LocalTime checkTime(String text, ParserRuleContext entry) {
        try {
            return Literals.parseTime(text);
        } catch (DateTimeParseException e) {
            diagnostics.error(entry, String.format("Invalid time '%s'", text));
            return null;
        }
    }

    @Override
    public Void visitTimeRange(LifeScriptParser.TimeRangeContext ctx) {
        LocalTime start = checkTime(ctx.TIME_VAL(0).getText(), ctx);
        LocalTime end = checkTime(ctx.TIME_VAL(1).getText(), ctx);

        if (start == null || end == null) {
            return visitChildren(ctx);
        }

        TimeRange range = new TimeRange(start, end);

        if (!range.isOrdered()) {
            // End earlier than start usually means the range was meant to cross midnight.
            String hint = end.isBefore(start) && !range.endsAtMidnight()
                    ? " (to go past midnight, end at 00:00 and continue on the next day)"
                    : "";
            diagnostics.error(ctx, String.format("Start time '%s' must be before end time '%s'%s",
                    ctx.TIME_VAL(0).getText(), ctx.TIME_VAL(1).getText(), hint));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitPeriod(LifeScriptParser.PeriodContext ctx) {
        LocalDate start;
        try {
            start = Literals.parseDate(ctx.DATE_VAL(0).getText());
        } catch (DateTimeParseException e) {
            diagnostics.error(ctx, String.format("Invalid date '%s'", ctx.DATE_VAL(0).getText()));
            return visitChildren(ctx);
        }

        LocalDate end;
        try {
            end = Literals.parseDate(ctx.DATE_VAL(1).getText());
        } catch (DateTimeParseException e) {
            diagnostics.error(ctx, String.format("Invalid date '%s'", ctx.DATE_VAL(1).getText()));
            return visitChildren(ctx);
        }

        period = new PlanPeriod(start, end);
        if (!start.isBefore(end)) {
            diagnostics.error(ctx, String.format("Start date '%s' must be before end date '%s'", start, end));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitSettings(LifeScriptParser.SettingsContext ctx) {
        visitAll(checkDuplicateKeys(ctx.settingsEntry(), e -> e.namedPeriod().getText(), "settings entry"));
        return null;
    }

    @Override
    public Void visitAvailability(LifeScriptParser.AvailabilityContext ctx) {
        visitAll(checkDuplicateKeys(ctx.availabilityEntry(), e -> e.dayOfWeek().getText(), "availability day"));
        return null;
    }

    @Override
    public Void visitEnergyProfile(LifeScriptParser.EnergyProfileContext ctx) {
        visitAll(checkDuplicateKeys(ctx.energyProfileEntry(),
                e -> e.DEFAULT() != null ? "default" : e.dayOfWeek().getText(), "energy profile entry"));
        return null;
    }

    @Override
    public Void visitEnergyProfileEntry(LifeScriptParser.EnergyProfileEntryContext ctx) {
        visitAll(checkDuplicateKeys(ctx.energyEntry(),
                e -> e.namedPeriod() != null ? e.namedPeriod().getText() : e.timeRange().getText(), "energy entry"));
        return null;
    }

    @Override
    public Void visitAvailabilityEntry(LifeScriptParser.AvailabilityEntryContext ctx) {
        String dayOfWeek = ctx.dayOfWeek().getText();
        DayOfWeek day = Literals.parseDay(dayOfWeek);

        if (period != null && !period.weekdays().contains(day)) {
            diagnostics.error(ctx, String.format("Availability day of week '%s' must be within the period range '%s'-'%s'",
                    dayOfWeek, period.start(), period.end()));
        }

        return visitChildren(ctx);
    }
}
