package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptLexer;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.lifescript.grammar.LifeScriptLexer.*;

// ANTLR's recovery, with messages in plain words instead of grammar names like DURATION_VAL or <EOF>.
class SyntaxErrorStrategy extends DefaultErrorStrategy {

    // When the parser expects exactly one of these sets, the message can name what kind of word is wrong.
    private static final Map<IntervalSet, String> KINDS = Map.of(
            set(CRITICAL, HIGH, MEDIUM, LOW), "priority",
            set(HIGH, MODERATE, LOW), "energy level",
            days(), "day",
            set(DAILY, WEEKDAYS, WEEKENDS).or(days()), "repeat pattern",
            set(BREAK, REPEATS, DURATION, PRIORITY, EFFORT, DEADLINE, DEPENDENCIES, NOTE), "task property",
            set(TIME, REPEATS, ACTIVITIES), "routine property",
            set(DATE, TIME, NOTE), "event property",
            set(SETTINGS, AVAILABILITY, ENERGY, ROUTINES, TASKS, EVENTS), "section");

    // Listed first, in this order, when they are among the options; the rest follow in grammar order.
    private static final List<Integer> ORDER = List.of(CRITICAL, HIGH, MODERATE, MEDIUM, LOW,
            DURATION, PRIORITY, EFFORT, DEADLINE, DEPENDENCIES, DATE, TIME, REPEATS, ACTIVITIES, BREAK, NOTE);

    // ANTLR remembers where a line could have ended and reports a later mismatch with that spot's options;
    // forgetting it after each matched word keeps it for mistakes right after a line end only.
    @Override
    public void reportMatch(Parser parser) {
        super.reportMatch(parser);
        nextTokensContext = null;
    }

    @Override
    protected void reportMissingToken(Parser parser) {
        if (inErrorRecoveryMode(parser)) {
            return;
        }
        beginErrorCondition(parser);
        Token current = parser.getCurrentToken();
        String before = textBefore(parser);
        String message = "Missing " + describe(getExpectedTokens(parser))
                + (before == null ? "" : " after " + before);
        parser.notifyErrorListeners(current, message, null);
    }

    @Override
    protected void reportUnwantedToken(Parser parser) {
        if (inErrorRecoveryMode(parser)) {
            return;
        }
        beginErrorCondition(parser);
        Token current = parser.getCurrentToken();
        parser.notifyErrorListeners(current, unexpected(current, getExpectedTokens(parser)), null);
    }

    @Override
    protected void reportInputMismatch(Parser parser, InputMismatchException e) {
        parser.notifyErrorListeners(e.getOffendingToken(), unexpected(e.getOffendingToken(), e.getExpectedTokens()), e);
    }

    @Override
    protected void reportNoViableAlternative(Parser parser, NoViableAltException e) {
        parser.notifyErrorListeners(e.getOffendingToken(), unexpected(e.getOffendingToken(), parser.getExpectedTokens()), e);
    }

    private static String unexpected(Token found, IntervalSet expected) {
        IntervalSet options = withoutLineAndPlanEnd(expected);
        String kind = KINDS.get(options);
        if (kind != null && isWord(found)) {
            return String.format("'%s' is not a valid %s. Use %s", found.getText(), kind, describe(options));
        }
        return "Expected " + describe(options) + ", but found " + describeFound(found);
    }

    // "duration:" reads better than ":" when a value is missing after it.
    private static String textBefore(Parser parser) {
        Token previous = parser.getInputStream().LT(-1);
        if (previous == null || previous.getType() == NEWLINE) {
            return null;
        }
        Token beforeThat = parser.getInputStream().LT(-2);
        if (previous.getType() == COLON && beforeThat != null && beforeThat.getType() != NEWLINE) {
            return "'" + beforeThat.getText() + ":'";
        }
        return "'" + previous.getText() + "'";
    }

    // The end of a line or of the plan is often allowed too, but naming it only adds noise.
    private static IntervalSet withoutLineAndPlanEnd(IntervalSet expected) {
        IntervalSet rest = new IntervalSet(expected);
        rest.remove(NEWLINE);
        rest.remove(Token.EOF);
        return rest.isNil() ? expected : rest;
    }

    private static String describe(IntervalSet expected) {
        List<String> words = withoutLineAndPlanEnd(expected).toList().stream()
                .sorted(Comparator.comparingInt(SyntaxErrorStrategy::rank))
                .map(SyntaxErrorStrategy::describe)
                .toList();
        if (words.size() == 1) {
            return words.get(0);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " or " + words.get(words.size() - 1);
    }

    private static int rank(int type) {
        int index = ORDER.indexOf(type);
        return index >= 0 ? index : ORDER.size() + type;
    }

    private static String describe(int type) {
        return switch (type) {
            case IDENTIFIER -> "a name";
            case DURATION_VAL -> "a duration (like 30m or 1h30m)";
            case TIME_VAL -> "a time (like 09:00)";
            case DATE_VAL -> "a date (like 2030-01-07)";
            case STRING -> "a note in quotes (like \"bring a laptop\")";
            case NEWLINE -> "the end of the line";
            case Token.EOF -> "the end of the plan";
            case ENERGY -> "'energy profile'";
            default -> LifeScriptLexer.VOCABULARY.getLiteralName(type);
        };
    }

    private static String describeFound(Token found) {
        return switch (found.getType()) {
            case NEWLINE -> "the end of the line";
            case Token.EOF -> "the end of the plan";
            default -> "'" + found.getText() + "'";
        };
    }

    private static boolean isWord(Token token) {
        return token.getType() != NEWLINE && token.getType() != Token.EOF;
    }

    private static IntervalSet set(int... types) {
        return new IntervalSet(types);
    }

    private static IntervalSet days() {
        return set(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY);
    }
}
