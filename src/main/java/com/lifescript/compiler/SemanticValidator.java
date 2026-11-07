package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;
import com.lifescript.model.Durations;
import com.lifescript.model.PlanPeriod;
import com.lifescript.model.TimeRange;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

public class SemanticValidator extends LifeScriptParserBaseVisitor<Void> {
    // null if the period is invalid; checks that need it are skipped.
    private PlanPeriod period;
    private final DiagnosticCollector diagnostics;
    private final LocalDate today;

    private static final Duration MAX_DURATION = Duration.ofHours(24);
    private static final Duration MAX_BREAK = Duration.ofHours(2);
    private static final int MAX_PERIOD_DAYS = 7;

    public SemanticValidator(DiagnosticCollector diagnostics, LocalDate today) {
        this.diagnostics = diagnostics;
        this.today = today;
    }

    @Override
    public Void visitPlan(LifeScriptParser.PlanContext ctx) {
        String planName = ctx.IDENTIFIER().getText();

        visit(ctx.period());

        boolean hasTasks = false;
        boolean hasAvailability = false;

        for (LifeScriptParser.PlanSectionContext section :
                checkDuplicateKeys(ctx.planSection(), section -> SectionKind.of(section).keyword(), "section")) {
            SectionKind kind = SectionKind.of(section);
            if (kind == SectionKind.TASKS) hasTasks = true;
            if (kind == SectionKind.AVAILABILITY) hasAvailability = true;
            visit(section);
        }

        if (!hasTasks) {
            diagnostics.error(ctx.PLAN().getSymbol(), String.format("Plan '%s' needs a 'tasks' section", planName));
        }
        if (!hasAvailability) {
            diagnostics.error(ctx.PLAN().getSymbol(), String.format("Plan '%s' needs an 'availability' section", planName));
        }

        return null;
    }

    @Override
    public Void visitTask(LifeScriptParser.TaskContext ctx) {
        String taskName = ctx.IDENTIFIER().getText();

        // A property's first token is its keyword.
        List<LifeScriptParser.TaskPropertyContext> properties =
                checkDuplicateKeys(ctx.taskProperty(), p -> Keywords.normalize(p.getStart().getText()), "task property");

        boolean hasDuration = false;
        boolean hasPriority = false;
        boolean hasEffort = false;

        for (LifeScriptParser.TaskPropertyContext prop : properties) {
            if (prop.taskDuration() != null) hasDuration = true;
            if (prop.taskPriority() != null) hasPriority = true;
            if (prop.taskEffort() != null) hasEffort = true;
        }

        if (!hasDuration) {
            reportMissingProperty(ctx, taskName, "duration: 1h");
        }
        if (!hasPriority) {
            reportMissingProperty(ctx, taskName, "priority: medium");
        }
        if (!hasEffort) {
            reportMissingProperty(ctx, taskName, "effort: moderate");
        }

        visitAll(properties);
        return null;
    }

    // The fix adds the property with a middle value right below "task: ...", indented like the task's other lines;
    // the user then changes the value.
    private void reportMissingProperty(LifeScriptParser.TaskContext task, String taskName, String line) {
        String property = line.substring(0, line.indexOf(':'));
        Token indentFrom = task.taskProperty().isEmpty() ? task.getStart() : task.taskProperty(0).getStart();
        diagnostics.error(task, String.format("Task '%s' is missing mandatory property: %s", taskName, property),
                List.of(Fixes.insertLineBelow(task.getStart().getLine(), indentation(indentFrom) + line,
                        String.format("Add '%s'", line))));
    }

    // The spaces or tabs before the token on its line.
    private static String indentation(Token token) {
        int column = token.getCharPositionInLine();
        return column == 0 ? "" : token.getInputStream().getText(Interval.of(token.getStartIndex() - column, token.getStartIndex() - 1));
    }

    // Reports repeated keys and returns only the first of each: a duplicate is not validated further.
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

    @Override
    public Void visitTasks(LifeScriptParser.TasksContext ctx) {
        List<LifeScriptParser.TaskContext> tasks =
                checkDuplicateKeys(ctx.task(), t -> t.IDENTIFIER().getText(), "task name");
        DependencyChecker dependencies = new DependencyChecker(diagnostics, tasks);
        for (LifeScriptParser.TaskContext task : tasks) {
            dependencies.checkTask(task);
            visit(task);
        }
        dependencies.checkCycles();
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
                checkDuplicateKeys(ctx.routineProperty(), p -> Keywords.normalize(p.getStart().getText()), "routine property");

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
            diagnostics.error(ctx, String.format("Routine '%s' is missing mandatory property: time", routineName));
        }
        if (!hasActivities) {
            diagnostics.error(ctx, String.format("Routine '%s' is missing mandatory property: activities", routineName));
        }
        if (!hasRepeats) {
            diagnostics.error(ctx, String.format("Routine '%s' is missing mandatory property: repeats", routineName));
        }

        // Invalid start times and durations are reported by their own visitors.
        if (startText != null && hasActivities && activityDurationsValid) {
            try {
                LocalTime start = Literals.parseTime(startText);
                if (start.toSecondOfDay() + total.toSeconds() > Duration.ofDays(1).toSeconds()) {
                    diagnostics.error(ctx, String.format("Routine '%s' starting at %s lasts %s and would run past midnight",
                            routineName, startText, Durations.format(total)));
                }
            } catch (DateTimeParseException ignored) {
            }
        }

        visitAll(properties);
        return null;
    }

    @Override
    public Void visitDayList(LifeScriptParser.DayListContext ctx) {
        checkDuplicateKeys(ctx.dayOfWeek(), d -> Keywords.normalize(d.getText()), "day in repeats");
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
                checkDuplicateKeys(ctx.eventProperty(), p -> Keywords.normalize(p.getStart().getText()), "event property");

        boolean hasDate = false;
        boolean hasTime = false;
        for (LifeScriptParser.EventPropertyContext prop : properties) {
            if (prop.eventDate() != null) hasDate = true;
            if (prop.eventTime() != null) hasTime = true;
        }

        if (!hasDate) {
            diagnostics.error(ctx, String.format("Event '%s' is missing mandatory property: date", eventName));
        }
        if (!hasTime) {
            diagnostics.error(ctx, String.format("Event '%s' is missing mandatory property: time", eventName));
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
            diagnostics.error(ctx, String.format("Event date '%s' isn't a real date", text));
            return visitChildren(ctx);
        }

        if (period != null && !period.contains(date)) {
            diagnostics.error(ctx, String.format("Event date '%s' is outside the period (%s to %s)",
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
        checkDuration(ctx.DURATION_VAL().getSymbol(), ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitActivityEntry(LifeScriptParser.ActivityEntryContext ctx) {
        checkDuration(ctx.DURATION_VAL().getSymbol(), ctx);
        return visitChildren(ctx);
    }

    private void checkDuration(Token value, ParserRuleContext entry) {
        String text = value.getText();
        Duration duration = parseDurationWithinLimit(text);
        if (duration == null) {
            diagnostics.error(entry, String.format("Duration '%s' is too long (at most 24h)", text));
            return;
        }
        if (duration.isZero()) {
            diagnostics.error(entry, String.format("Duration '%s' must be greater than zero", text));
            return;
        }

        checkMinutes(value, duration, entry);
    }

    // Unlike a task's duration, a break may be zero.
    private void checkBreak(Token value, ParserRuleContext entry) {
        String text = value.getText();
        Duration duration = parseDurationWithinLimit(text);
        if (duration == null || duration.compareTo(MAX_BREAK) > 0) {
            diagnostics.error(entry, String.format("Break '%s' is too long (at most %s)", text, Durations.format(MAX_BREAK)));
            return;
        }
        checkMinutes(value, duration, entry);
    }

    // Minutes after hours (1h75m) must stay under 60; minutes alone (90m) are fine.
    private void checkMinutes(Token value, Duration duration, ParserRuleContext entry) {
        Literals.DurationLiteral literal = Literals.parseDurationLiteral(value.getText());
        if (literal.hasHours() && literal.minutes() > 59) {
            String fixed = Durations.format(duration);
            diagnostics.error(entry, String.format("Minutes in '%s' must be at most 59 (write %s)", value.getText(), fixed),
                    List.of(Fixes.replace(value, value, fixed)));
        }
    }

    // null if over 24h or too large to read.
    private static Duration parseDurationWithinLimit(String text) {
        try {
            Duration duration = Literals.parseDuration(text);
            return duration.compareTo(MAX_DURATION) <= 0 ? duration : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int repeatsLine(LifeScriptParser.TaskContext task) {
        return task.taskProperty().stream().filter(p -> p.repeats() != null).findFirst().orElseThrow()
                .getStart().getLine();
    }

    @Override
    public Void visitTaskDeadline(LifeScriptParser.TaskDeadlineContext ctx) {
        String dateText = ctx.DATE_VAL().getText();

        // A deadline's parent is a task property, whose parent is the task.
        LifeScriptParser.TaskContext task = (LifeScriptParser.TaskContext) ctx.getParent().getParent();
        if (DependencyChecker.isRepeating(task)) {
            diagnostics.error(ctx, String.format("Repeating task '%s' can't have a deadline", task.IDENTIFIER().getText()),
                    List.of(Fixes.removeLine(ctx.getStart().getLine(), "Remove the deadline"),
                            Fixes.removeLine(repeatsLine(task), "Remove 'repeats' (make it a one-time task)")));
        }

        // The grammar allows a missing time only so this can explain it better than a syntax error.
        if (ctx.TIME_VAL() == null) {
            diagnostics.error(ctx, String.format("Deadline needs a time, e.g. 'deadline: %s 17:00'", dateText),
                    List.of(Fixes.insertAfter(ctx.DATE_VAL().getSymbol(), " 17:00", "Add the time 17:00")));
            return visitChildren(ctx);
        }
        String timeText = ctx.TIME_VAL().getText();

        checkTime(timeText, ctx);

        try {
            LocalDate deadline = Literals.parseDate(dateText);
            // A deadline after the period is allowed: it still orders tasks by urgency.
            if (period != null && deadline.isBefore(period.start())) {
                diagnostics.error(ctx, String.format("Deadline '%s %s' is before the period starts (%s)",
                        dateText, timeText, period.start()));
            }
        }
        catch (DateTimeParseException e) {
            diagnostics.error(ctx, String.format("Deadline date '%s' isn't a real date", dateText));
        }
        return visitChildren(ctx);
    }

    // The lexer accepts any HH:MM, e.g. 25:00.
    private LocalTime checkTime(String text, ParserRuleContext entry) {
        try {
            return Literals.parseTime(text);
        } catch (DateTimeParseException e) {
            diagnostics.error(entry, String.format("Time '%s' isn't valid (hours 00-23, minutes 00-59; midnight is 00:00)", text));
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
            diagnostics.error(ctx, String.format("Date '%s' isn't a real date", ctx.DATE_VAL(0).getText()));
            return visitChildren(ctx);
        }

        LocalDate end;
        try {
            end = Literals.parseDate(ctx.DATE_VAL(1).getText());
        } catch (DateTimeParseException e) {
            diagnostics.error(ctx, String.format("Date '%s' isn't a real date", ctx.DATE_VAL(1).getText()));
            return visitChildren(ctx);
        }

        if (start.isAfter(end)) {
            diagnostics.error(ctx, String.format("Start date '%s' can't be after end date '%s'", start, end));
            return visitChildren(ctx);
        }
        if (start.isBefore(today)) {
            diagnostics.error(ctx, String.format("Period can't start in the past (today is %s)", today));
            return visitChildren(ctx);
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_PERIOD_DAYS) {
            diagnostics.error(ctx, String.format("Period can be at most %d days (this one is %d days)",
                    MAX_PERIOD_DAYS, days));
            return visitChildren(ctx);
        }

        period = new PlanPeriod(start, end);
        return visitChildren(ctx);
    }

    @Override
    public Void visitSettings(LifeScriptParser.SettingsContext ctx) {
        visitAll(checkDuplicateKeys(ctx.settingsEntry(),
                e -> e.BREAK() != null ? "break" : Keywords.normalize(e.namedPeriod().getText()), "settings entry"));
        return null;
    }

    @Override
    public Void visitTaskBreak(LifeScriptParser.TaskBreakContext ctx) {
        checkBreak(ctx.DURATION_VAL().getSymbol(), ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitSettingsEntry(LifeScriptParser.SettingsEntryContext ctx) {
        if (ctx.BREAK() != null) {
            checkBreak(ctx.DURATION_VAL().getSymbol(), ctx);
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitAvailability(LifeScriptParser.AvailabilityContext ctx) {
        visitAll(checkDuplicateKeys(ctx.availabilityEntry(), e -> Keywords.normalize(e.dayOfWeek().getText()), "availability day"));
        return null;
    }

    @Override
    public Void visitEnergyProfile(LifeScriptParser.EnergyProfileContext ctx) {
        visitAll(checkDuplicateKeys(ctx.energyProfileEntry(),
                e -> e.DEFAULT() != null ? "default" : Keywords.normalize(e.dayOfWeek().getText()), "energy profile entry"));
        return null;
    }

    @Override
    public Void visitEnergyProfileEntry(LifeScriptParser.EnergyProfileEntryContext ctx) {
        visitAll(checkDuplicateKeys(ctx.energyEntry(),
                e -> e.namedPeriod() != null ? Keywords.normalize(e.namedPeriod().getText()) : e.timeRange().getText(), "energy entry"));
        return null;
    }
}
