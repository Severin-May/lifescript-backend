package com.lifescript.compiler;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.Interval;

import java.util.regex.Pattern;

import static com.lifescript.grammar.LifeScriptLexer.*;

// Explains a word the lexer couldn't read (UNKNOWN_WORD, UNCLOSED_STRING) by its shape.
final class UnknownWords {

    private static final Pattern TIME_RANGE = Pattern.compile("\\d{1,2}(:\\d*)?-\\d{1,2}(:\\d*)?");
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d*");
    private static final Pattern DATE = Pattern.compile("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern DECIMAL = Pattern.compile("\\d+[.,]\\d+\\p{L}*");
    private static final Pattern DURATION_LIKE = Pattern.compile(
            "\\d+(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)(\\d+(m|min|mins|minute|minutes)?)?");

    private UnknownWords() {
    }

    static boolean isUnknown(Token token) {
        return token.getType() == UNKNOWN_WORD || token.getType() == UNCLOSED_STRING;
    }

    static String explain(Token token, IntervalSet expected) {
        if (token.getType() == UNCLOSED_STRING) {
            return "The note is missing its closing \" (a note must end on the same line)";
        }
        String word = wordAt(token);
        String lower = word.toLowerCase();
        if (expected.contains(IDENTIFIER) && Character.isDigit(word.charAt(0))) {
            return String.format("'%s' can't be a name: names must start with a letter or _", word);
        }
        if (TIME_RANGE.matcher(lower).matches()) {
            return String.format("Write the time range '%s' with two-digit hours and minutes, like 09:00-17:00", word);
        }
        if (TIME.matcher(lower).matches()
                || NUMBER.matcher(lower).matches() && expected.contains(TIME_VAL) && !expected.contains(DURATION_VAL)) {
            return String.format("Write the time '%s' with two-digit hours and minutes, like 09:00", word);
        }
        if (DATE.matcher(lower).matches()) {
            return String.format("Write the date '%s' as year-month-day, like 2030-01-07", word);
        }
        if (NUMBER.matcher(lower).matches()) {
            return String.format("'%s' needs a unit, like %sm or 1h30m", word, word);
        }
        if (DECIMAL.matcher(lower).matches()) {
            return String.format("Durations can't have decimals: write '%s' with h and m, like 1h30m", word);
        }
        if (DURATION_LIKE.matcher(lower).matches()) {
            return String.format("Write the duration '%s' with h and m only, like 30m, 2h or 1h30m", word);
        }
        int bad = firstCharNotAllowedInAName(word);
        return bad < 0 ? String.format("Unexpected '%s'", word)
                : String.format("Unexpected character '%s'", word.substring(bad, word.offsetByCodePoints(bad, 1)));
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
