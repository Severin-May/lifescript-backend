package com.lifescript.compiler;

import com.lifescript.diagnostics.Fix;
import com.lifescript.diagnostics.TextEdit;
import com.lifescript.model.Durations;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.Interval;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.lifescript.grammar.LifeScriptLexer.*;

// Explains a word the lexer couldn't read (UNKNOWN_WORD, UNCLOSED_STRING) by its shape.
final class UnknownWords {

    private static final Pattern TIME_RANGE = Pattern.compile("\\d{1,2}(:\\d*)?-\\d{1,2}(:\\d*)?");
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d*");
    private static final Pattern DATE = Pattern.compile("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern DECIMAL = Pattern.compile("\\d+[.,]\\d+\\p{L}*");
    private static final String HOURS = "(?:h|hr|hrs|hour|hours)";
    private static final String MINUTES = "(?:m|min|mins|minute|minutes)";
    private static final Pattern DURATION_LIKE = Pattern.compile("\\d+(" + HOURS + "|" + MINUTES + ")(\\d+" + MINUTES + "?)?");
    private static final Pattern TIME_PARTS = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?");
    private static final Pattern DATE_PARTS = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern DECIMAL_HOURS = Pattern.compile("(\\d+\\.\\d+)" + HOURS);
    private static final Pattern HOURS_AND_MINUTES = Pattern.compile("(\\d+)" + HOURS + "(?:(\\d+)" + MINUTES + "?)?");
    private static final Pattern MINUTES_ONLY = Pattern.compile("(\\d+)" + MINUTES);

    private UnknownWords() {
    }

    static boolean isUnknown(Token token) {
        return token.getType() == UNKNOWN_WORD || token.getType() == UNCLOSED_STRING;
    }

    static SyntaxProblem explain(Token token, IntervalSet expected) {
        if (token.getType() == UNCLOSED_STRING) {
            int end = token.getCharPositionInLine() + 1 + token.getText().stripTrailing().length();
            return SyntaxProblem.withFix("The note is missing its closing \" (a note must end on the same line)",
                    new Fix("Add the closing \"", List.of(new TextEdit(token.getLine(), end, token.getLine(), end, "\""))));
        }
        String word = wordAt(token);
        String lower = word.toLowerCase();
        if (expected.contains(IDENTIFIER) && Character.isDigit(word.charAt(0))) {
            return SyntaxProblem.of(String.format("'%s' can't be a name: names must start with a letter or _", word));
        }
        if (TIME_RANGE.matcher(lower).matches()) {
            return withFix(token, word, String.format(
                    "Write the time range '%s' with two-digit hours and minutes, like 09:00-17:00", word), timeRange(lower));
        }
        if (TIME.matcher(lower).matches()
                || NUMBER.matcher(lower).matches() && expected.contains(TIME_VAL) && !expected.contains(DURATION_VAL)) {
            return withFix(token, word, String.format(
                    "Write the time '%s' with two-digit hours and minutes, like 09:00", word), time(lower));
        }
        if (DATE.matcher(lower).matches()) {
            return withFix(token, word, String.format("Write the date '%s' as year-month-day, like 2030-01-07", word),
                    date(lower));
        }
        if (NUMBER.matcher(lower).matches()) {
            return withFix(token, word, String.format("'%s' needs a unit, like %sm or 1h30m", word, word),
                    expected.contains(DURATION_VAL) ? word + "m" : null);
        }
        if (DECIMAL.matcher(lower).matches()) {
            return withFix(token, word, String.format(
                    "Durations can't have decimals: write '%s' with h and m, like 1h30m", word), decimalHours(lower));
        }
        if (DURATION_LIKE.matcher(lower).matches()) {
            return withFix(token, word, String.format(
                    "Write the duration '%s' with h and m only, like 30m, 2h or 1h30m", word), durationLike(lower));
        }
        int bad = firstCharNotAllowedInAName(word);
        return SyntaxProblem.of(bad < 0 ? String.format("Unexpected '%s'", word)
                : String.format("Unexpected character '%s'", word.substring(bad, word.offsetByCodePoints(bad, 1))));
    }

    // fixed is null when the right text would be a guess, e.g. "17:5" (17:05 or 17:50?).
    private static SyntaxProblem withFix(Token token, String word, String message, String fixed) {
        if (fixed == null) {
            return SyntaxProblem.of(message);
        }
        int start = token.getCharPositionInLine() + 1;
        return SyntaxProblem.withFix(message,
                Fix.replace(String.format("Change to '%s'", fixed), token.getLine(), start, start + word.length(), fixed));
    }

    // "9" or "9:30" -> "09:00", "09:30"; minutes must have two digits to be sure what they mean.
    private static String time(String text) {
        Matcher m = TIME_PARTS.matcher(text);
        if (!m.matches()) {
            return null;
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        return hour <= 23 && minute <= 59 ? String.format("%02d:%02d", hour, minute) : null;
    }

    private static String timeRange(String text) {
        String[] ends = text.split("-");
        String start = time(ends[0]);
        String end = time(ends[1]);
        return start != null && end != null ? start + "-" + end : null;
    }

    // Year first only: "10/01/2030" could be either day/month or month/day.
    private static String date(String text) {
        Matcher m = DATE_PARTS.matcher(text);
        if (!m.matches()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))
                    .toString();
        } catch (DateTimeException e) {
            return null;
        }
    }

    // "1.5h" -> "1h30m"; only hours, and only when they come out as whole minutes.
    private static String decimalHours(String text) {
        Matcher m = DECIMAL_HOURS.matcher(text);
        if (!m.matches()) {
            return null;
        }
        BigDecimal minutes = new BigDecimal(m.group(1)).multiply(BigDecimal.valueOf(60));
        if (minutes.signum() == 0 || minutes.stripTrailingZeros().scale() > 0) {
            return null;
        }
        return Durations.format(Duration.ofMinutes(minutes.longValueExact()));
    }

    // "90min" -> "90m", "2hours" -> "2h", "1h30" -> "1h30m".
    private static String durationLike(String text) {
        Matcher hours = HOURS_AND_MINUTES.matcher(text);
        if (hours.matches()) {
            long minutes = hours.group(2) == null ? 0 : Long.parseLong(hours.group(2));
            return Durations.format(Duration.ofHours(Long.parseLong(hours.group(1))).plusMinutes(minutes));
        }
        Matcher minutes = MINUTES_ONLY.matcher(text);
        return minutes.matches() ? minutes.group(1) + "m" : null;
    }

    // A copy of the token that covers the whole word, e.g. all of "9:00", not only the "9" before the colon.
    static Token wholeWord(Token token) {
        CommonToken copy = new CommonToken(token);
        copy.setStopIndex(token.getStartIndex() + wordAt(token).length() - 1);
        return copy;
    }

    // From the token to the next space, comma or line end: the lexer splits "9:00" at the colon.
    private static String wordAt(Token token) {
        CharStream input = token.getInputStream();
        int from = token.getStartIndex();
        int to = from;
        while (to < input.size()) {
            char c = input.getText(Interval.of(to, to)).charAt(0);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == ',') {
                break;
            }
            to++;
        }
        return to > from ? input.getText(Interval.of(from, to - 1)) : token.getText();
    }

    private static int firstCharNotAllowedInAName(String word) {
        for (int i = 0; i < word.length(); i = word.offsetByCodePoints(i, 1)) {
            int c = word.codePointAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '_' || Character.getType(c) == Character.NON_SPACING_MARK
                    || Character.getType(c) == Character.COMBINING_SPACING_MARK || Character.getType(c) == Character.ENCLOSING_MARK;
            if (!allowed) {
                return i;
            }
        }
        return -1;
    }
}
