package com.lifescript.compiler;

import com.lifescript.diagnostics.Fix;
import com.lifescript.diagnostics.TextEdit;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lifescript.compiler.SyntaxWords.*;
import static com.lifescript.grammar.LifeScriptLexer.*;

// ANTLR's recovery, with messages in plain words instead of grammar names like DURATION_VAL or <EOF>.
class SyntaxErrorStrategy extends DefaultErrorStrategy {

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
        Optional<Integer> suggestion = isText(found) ? closestKeyword(found.getText(), options) : Optional.empty();
        if (suggestion.isPresent()) {
            String what = kind != null ? "is not a valid " + kind : "is not valid here";
            return didYouMean(found, what, suggestion.get());
        }
        if (kind != null && isText(found)) {
            return SyntaxProblem.of(String.format("'%s' is not a valid %s. Use %s", found.getText(), kind, describe(options)));
        }
        return SyntaxProblem.of("Expected " + describe(options) + ", but found " + describeFound(found));
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

    private static String describeFound(Token found) {
        return switch (found.getType()) {
            case NEWLINE -> "the end of the line";
            case Token.EOF -> "the end of the plan";
            default -> "'" + found.getText() + "'";
        };
    }

    private static boolean isText(Token token) {
        return token.getType() != NEWLINE && token.getType() != Token.EOF;
    }

    private static IntervalSet days() {
        return set(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY);
    }
}
