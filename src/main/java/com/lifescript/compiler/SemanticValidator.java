package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;
import com.lifescript.model.Durations;
import com.lifescript.model.PlanPeriod;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

public class SemanticValidator extends LifeScriptParserBaseVisitor<Void> {
    // null if the period is invalid; checks that need it are skipped.
    private PlanPeriod period;
    private final DiagnosticCollector diagnostics;
    private final ValueChecks values;
    private final LocalDate today;

    public SemanticValidator(DiagnosticCollector diagnostics, LocalDate today) {
        this.diagnostics = diagnostics;
        this.values = new ValueChecks(diagnostics);
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

        List<LifeScriptParser.TaskPropertyContext> properties =
                checkDuplicateKeys(ctx.taskProperty(), SemanticValidator::propertyKey, "task property");
        checkRequired(ctx, "Task", taskName, properties, List.of("duration: 1h", "priority: medium", "effort: moderate"));
        checkRepeatingWithDeadline(taskName, properties);

        visitAll(properties);
        return null;
    }

    private void checkRepeatingWithDeadline(String taskName, List<LifeScriptParser.TaskPropertyContext> properties) {
        Optional<LifeScriptParser.TaskPropertyContext> repeats = properties.stream().filter(p -> p.repeats() != null).findFirst();
        Optional<LifeScriptParser.TaskPropertyContext> deadline = properties.stream().filter(p -> p.taskDeadline() != null).findFirst();
        if (repeats.isEmpty() || deadline.isEmpty()) {
            return;
        }
        diagnostics.error(deadline.get(), String.format("Repeating task '%s' can't have a deadline", taskName),
                List.of(Fixes.removeLine(deadline.get().getStart().getLine(), "Remove the deadline"),
                        Fixes.removeLine(repeats.get().getStart().getLine(), "Remove 'repeats' (make it a one-time task)")));
    }

    // A property's first token is its keyword.
    private static String propertyKey(ParserRuleContext property) {
        return Keywords.normalize(property.getStart().getText());
    }

    // required: a sample per property, which the fix adds right below the entry's first line, indented like the
    // entry's other lines; the user then changes the value. A sample may span several lines (activities).
    private void checkRequired(ParserRuleContext entry, String label, String name,
                               List<? extends ParserRuleContext> properties, List<String> required) {
        Set<String> present = new HashSet<>();
        properties.forEach(p -> present.add(propertyKey(p)));
        String indent = indentation(properties.isEmpty() ? entry.getStart() : properties.get(0).getStart());

        for (String sample : required) {
            String property = sample.substring(0, sample.indexOf(':'));
            if (present.contains(property)) {
                continue;
            }
            String text = String.join("\n", sample.lines().map(line -> indent + line).toList());
            diagnostics.error(entry, String.format("%s '%s' is missing mandatory property: %s", label, name, property),
                    List.of(Fixes.insertLineBelow(entry.getStart().getLine(), text,
                            String.format("Add '%s'", sample.lines().findFirst().orElseThrow()))));
        }
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
                checkDuplicateKeys(ctx.routineProperty(), SemanticValidator::propertyKey, "routine property");
        checkRequired(ctx, "Routine", routineName, properties,
                List.of("time: 07:00", "activities:\n    first_activity: 15m", "repeats: daily"));

        String startText = null;
        Duration total = null;
        for (LifeScriptParser.RoutinePropertyContext prop : properties) {
            if (prop.routineTime() != null) {
                startText = prop.routineTime().TIME_VAL().getText();
            }
            if (prop.routineActivities() != null) {
                total = totalDuration(prop.routineActivities());
            }
        }

        // Invalid start times and durations are reported by their own visitors.
        if (startText != null && total != null) {
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

    // null if any activity's duration is invalid.
    private static Duration totalDuration(LifeScriptParser.RoutineActivitiesContext activities) {
        Duration total = Duration.ZERO;
        for (LifeScriptParser.ActivityEntryContext activity : activities.activityEntry()) {
            Duration duration = ValueChecks.parseDurationWithinLimit(activity.DURATION_VAL().getText());
            if (duration == null) {
                return null;
            }
            total = total.plus(duration);
        }
        return total;
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
                checkDuplicateKeys(ctx.eventProperty(), SemanticValidator::propertyKey, "event property");
        LocalDate sampleDate = period != null ? period.start() : today;
        checkRequired(ctx, "Event", eventName, properties, List.of("date: " + sampleDate, "time: 09:00-10:00"));

        visitAll(properties);
        return null;
    }

    @Override
    public Void visitEventDate(LifeScriptParser.EventDateContext ctx) {
        String text = ctx.DATE_VAL().getText();
        LocalDate date = values.checkDate(text, ctx, "Event date");
        if (date != null && period != null && !period.contains(date)) {
            diagnostics.error(ctx, String.format("Event date '%s' is outside the period (%s to %s)",
                    text, period.start(), period.end()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitRoutineTime(LifeScriptParser.RoutineTimeContext ctx) {
        values.checkTime(ctx.TIME_VAL().getText(), ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTaskDuration(LifeScriptParser.TaskDurationContext ctx) {
        values.checkDuration(ctx.DURATION_VAL().getSymbol(), ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitActivityEntry(LifeScriptParser.ActivityEntryContext ctx) {
        values.checkDuration(ctx.DURATION_VAL().getSymbol(), ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitTaskDeadline(LifeScriptParser.TaskDeadlineContext ctx) {
        String dateText = ctx.DATE_VAL().getText();

        // The grammar allows a missing time only so this can explain it better than a syntax error.
        if (ctx.TIME_VAL() == null) {
            diagnostics.error(ctx, String.format("Deadline needs a time, e.g. 'deadline: %s 17:00'", dateText),
                    List.of(Fixes.insertAfter(ctx.DATE_VAL().getSymbol(), " 17:00", "Add the time 17:00")));
            return visitChildren(ctx);
        }
        String timeText = ctx.TIME_VAL().getText();

        values.checkTime(timeText, ctx);

        LocalDate deadline = values.checkDate(dateText, ctx, "Deadline date");
        // A deadline after the period is allowed: it still orders tasks by urgency.
        if (deadline != null && period != null && deadline.isBefore(period.start())) {
            diagnostics.error(ctx, String.format("Deadline '%s %s' is before the period starts (%s)",
                    dateText, timeText, period.start()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitTimeRange(LifeScriptParser.TimeRangeContext ctx) {
        values.checkTimeRange(ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitPeriod(LifeScriptParser.PeriodContext ctx) {
        period = values.checkPeriod(ctx, today);
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
        values.checkBreak(ctx.DURATION_VAL().getSymbol(), ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitSettingsEntry(LifeScriptParser.SettingsEntryContext ctx) {
        if (ctx.BREAK() != null) {
            values.checkBreak(ctx.DURATION_VAL().getSymbol(), ctx);
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
