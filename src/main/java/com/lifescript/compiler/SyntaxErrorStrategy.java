package com.lifescript.compiler;

import com.lifescript.diagnostics.Fix;
import com.lifescript.diagnostics.TextEdit;
import com.lifescript.grammar.LifeScriptLexer;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lifescript.grammar.LifeScriptLexer.*;

// ANTLR's recovery, with messages in plain words instead of grammar names like DURATION_VAL or <EOF>.
class SyntaxErrorStrategy extends DefaultErrorStrategy {

    static final IntervalSet TASK_PROPERTIES = set(BREAK, REPEATS, DURATION, PRIORITY, EFFORT, DEADLINE, DEPENDENCIES, NOTE);
    static final IntervalSet ROUTINE_PROPERTIES = set(TIME, REPEATS, ACTIVITIES);
    static final IntervalSet EVENT_PROPERTIES = set(DATE, TIME, NOTE);
    static final IntervalSet SECTIONS = set(SETTINGS, AVAILABILITY, ENERGY, ROUTINES, TASKS, EVENTS);

    // When the parser expects exactly one of these sets, the message can name what kind of word is wrong.
    private static final Map<IntervalSet, String> KINDS = Map.of(
            set(CRITICAL, HIGH, MEDIUM, LOW), "priority",
            set(HIGH, MODERATE, LOW), "energy level",
            days(), "day",
            set(DAILY, WEEKDAYS, WEEKENDS, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY), "repeat pattern",
            TASK_PROPERTIES, "task property",
            ROUTINE_PROPERTIES, "routine property",
            EVENT_PROPERTIES, "event property",
            SECTIONS, "section");

    // Listed first, in this order, when they are among the options; the rest follow in grammar order.
    private static final List<Integer> ORDER = List.of(CRITICAL, HIGH, MODERATE, MEDIUM, LOW,
            DURATION, PRIORITY, EFFORT, DEADLINE, DEPENDENCIES, DATE, TIME, REPEATS, ACTIVITIES, BREAK, NOTE);

    private final SyntaxErrorListener listener;

    SyntaxErrorStrategy(SyntaxErrorListener listener) {
        this.listener = listener;
    }

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
        IntervalSet expected = getExpectedTokens(parser);
        String before = textBefore(parser);
        String message = "Missing " + describe(expected) + (before == null ? "" : " after " + before);
        notify(parser, current, missingColon(parser, expected)
                .map(fix -> SyntaxProblem.withFix(message, fix))
                .orElse(SyntaxProblem.of(message)), null);
    }

    // "duration 1h": the only missing piece the fix can know for sure is the ':', right after the word.
    private static Optional<Fix> missingColon(Parser parser, IntervalSet expected) {
        Token previous = parser.getInputStream().LT(-1);
        if (!expected.contains(COLON) || previous == null || previous.getType() == NEWLINE) {
            return Optional.empty();
        }
        int end = previous.getCharPositionInLine() + 1 + previous.getStopIndex() - previous.getStartIndex() + 1;
        return Optional.of(new Fix("Insert ':'", List.of(new TextEdit(previous.getLine(), end, previous.getLine(), end, ":"))));
    }

    @Override
    protected void reportUnwantedToken(Parser parser) {
        if (inErrorRecoveryMode(parser)) {
            return;
        }
        beginErrorCondition(parser);
        report(parser, parser.getCurrentToken(), getExpectedTokens(parser), null);
    }

    @Override
    protected void reportInputMismatch(Parser parser, InputMismatchException e) {
        report(parser, e.getOffendingToken(), e.getExpectedTokens(), e);
    }

    @Override
    protected void reportNoViableAlternative(Parser parser, NoViableAltException e) {
        report(parser, e.getOffendingToken(), parser.getExpectedTokens(), e);
    }

    private void report(Parser parser, Token found, IntervalSet expected, RecognitionException e) {
        Optional<SyntaxProblem> hint = ContextHints.explain(parser, found, expected);
        if (hint.isPresent()) {
            notify(parser, found, hint.get(), e);
        } else if (UnknownWords.isUnknown(found)) {
            notify(parser, UnknownWords.wholeWord(found), UnknownWords.explain(found, expected), e);
        } else {
            notify(parser, found, unexpected(found, expected), e);
        }
    }

    private void notify(Parser parser, Token token, SyntaxProblem problem, RecognitionException e) {
        listener.attachFixes(problem.fixes());
        parser.notifyErrorListeners(token, problem.message(), e);
    }

    private static SyntaxProblem unexpected(Token found, IntervalSet expected) {
        IntervalSet options = withoutLineAndPlanEnd(expected);
        String kind = KINDS.get(options);
        Optional<Integer> suggestion = isWord(found) ? closestKeyword(found.getText(), options) : Optional.empty();
        if (suggestion.isPresent()) {
            String what = kind != null ? "is not a valid " + kind : "is not valid here";
            return didYouMean(found, what, suggestion.get());
        }
        if (kind != null && isWord(found)) {
            return SyntaxProblem.of(String.format("'%s' is not a valid %s. Use %s", found.getText(), kind, describe(options)));
        }
        return SyntaxProblem.of("Expected " + describe(options) + ", but found " + describeFound(found));
    }

    // e.g. "'hgh' is not a valid priority. Did you mean 'high'?", with the fix that writes 'high'.
    static SyntaxProblem didYouMean(Token found, String what, int suggestion) {
        String message = String.format("'%s' %s. Did you mean %s?", found.getText(), what, describe(suggestion));
        return SyntaxProblem.withFix(message, Fixes.replace(found, found, keyword(suggestion)));
    }

    // The word as typed in a plan, e.g. "duration" for DURATION.
    static String keyword(int type) {
        String literal = LifeScriptLexer.VOCABULARY.getLiteralName(type);
        return literal.substring(1, literal.length() - 1);
    }

    // Returns the token type of the keyword allowed here that the word is most likely a typo of.
    static Optional<Integer> closestKeyword(String word, IntervalSet options) {
        Map<String, Integer> keywords = new HashMap<>();
        for (int type : options.toList()) {
            String literal = LifeScriptLexer.VOCABULARY.getLiteralName(type);
            if (literal != null && literal.matches("'[a-z]+'")) {
                keywords.put(keyword(type), type);
            }
        }
        return Spelling.closest(word, keywords.keySet()).map(keywords::get);
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

    static String describe(IntervalSet expected) {
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

    static String describe(int type) {
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

    // Read-only, as these sets are shared constants.
    static IntervalSet set(int... types) {
        IntervalSet set = new IntervalSet(types);
        set.setReadonly(true);
        return set;
    }

    private static IntervalSet days() {
        return set(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY);
    }
}
