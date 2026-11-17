package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.Durations;
import com.lifescript.model.PlanPeriod;
import com.lifescript.model.TimeRange;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Checks on single values (times, dates, durations, the period) that the lexer accepts by shape only.
// Each check reports its problem and returns the value, or null if it's invalid.
final class ValueChecks {

    private static final Duration MAX_DURATION = Duration.ofHours(24);
    private static final Duration MAX_BREAK = Duration.ofHours(2);
    private static final int MAX_PERIOD_DAYS = 7;

    private final DiagnosticCollector diagnostics;

    ValueChecks(DiagnosticCollector diagnostics) {
        this.diagnostics = diagnostics;
    }

    // The lexer accepts any HH:MM, e.g. 25:00.
    LocalTime checkTime(String text, ParserRuleContext entry) {
        try {
            return Literals.parseTime(text);
        } catch (DateTimeParseException e) {
            diagnostics.error(entry, String.format("Time '%s' isn't valid (hours 00-23, minutes 00-59; midnight is 00:00)", text));
            return null;
        }
    }

    // label: what the date is, e.g. "Event date".
    LocalDate checkDate(String text, ParserRuleContext entry, String label) {
        try {
            return Literals.parseDate(text);
        } catch (DateTimeParseException e) {
            diagnostics.error(entry, String.format("%s '%s' isn't a real date", label, text));
            return null;
        }
    }

    void checkDuration(Token value, ParserRuleContext entry) {
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
    void checkBreak(Token value, ParserRuleContext entry) {
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
    static Duration parseDurationWithinLimit(String text) {
        try {
            Duration duration = Literals.parseDuration(text);
            return duration.compareTo(MAX_DURATION) <= 0 ? duration : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    void checkTimeRange(LifeScriptParser.TimeRangeContext ctx) {
        LocalTime start = checkTime(ctx.TIME_VAL(0).getText(), ctx);
        LocalTime end = checkTime(ctx.TIME_VAL(1).getText(), ctx);
        if (start == null || end == null) {
            return;
        }

        TimeRange range = new TimeRange(start, end);
        if (!range.isOrdered()) {
            String hint = end.isBefore(start) && !range.endsAtMidnight()
                    ? " (to go past midnight, end at 00:00 and continue on the next day)"
                    : "";
            diagnostics.error(ctx, String.format("Start time '%s' must be before end time '%s'%s",
                    ctx.TIME_VAL(0).getText(), ctx.TIME_VAL(1).getText(), hint));
        }
    }

    // today: the user's date, which the period must not start before.
    PlanPeriod checkPeriod(LifeScriptParser.PeriodContext ctx, LocalDate today) {
        LocalDate start = checkDate(ctx.DATE_VAL(0).getText(), ctx, "Date");
        if (start == null) {
            return null;
        }
        LocalDate end = checkDate(ctx.DATE_VAL(1).getText(), ctx, "Date");
        if (end == null) {
            return null;
        }

        if (start.isAfter(end)) {
            diagnostics.error(ctx, String.format("Start date '%s' can't be after end date '%s'", start, end));
            return null;
        }
        if (start.isBefore(today)) {
            diagnostics.error(ctx, String.format("Period can't start in the past (today is %s)", today));
            return null;
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_PERIOD_DAYS) {
            diagnostics.error(ctx, String.format("Period can be at most %d days (this one is %d days)",
                    MAX_PERIOD_DAYS, days));
            return null;
        }
        return new PlanPeriod(start, end);
    }
}
