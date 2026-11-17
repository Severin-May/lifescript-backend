package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptLexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lifescript.grammar.LifeScriptLexer.*;

// The grammar's words in plain language, shared by the syntax error messages.
final class SyntaxWords {

    static final IntervalSet TASK_PROPERTIES = set(BREAK, REPEATS, DURATION, PRIORITY, EFFORT, DEADLINE, DEPENDENCIES, NOTE);
    static final IntervalSet ROUTINE_PROPERTIES = set(TIME, REPEATS, ACTIVITIES);
    static final IntervalSet EVENT_PROPERTIES = set(DATE, TIME, NOTE);
    static final IntervalSet SECTIONS = set(SETTINGS, AVAILABILITY, ENERGY, ROUTINES, TASKS, EVENTS);

    // Listed first, in this order, when they are among the options; the rest follow in grammar order.
    private static final List<Integer> ORDER = List.of(CRITICAL, HIGH, MODERATE, MEDIUM, LOW,
            DURATION, PRIORITY, EFFORT, DEADLINE, DEPENDENCIES, DATE, TIME, REPEATS, ACTIVITIES, BREAK, NOTE);

    private SyntaxWords() {
    }

    // Keywords are the tokens whose fixed text is a word, e.g. 'low' (not symbols like ':').
    static boolean isKeyword(int type) {
        String literal = LifeScriptLexer.VOCABULARY.getLiteralName(type);
        return literal != null && literal.matches("'[a-z]+'");
    }

    static boolean isKeyword(Token token) {
        return isKeyword(token.getType());
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
            if (isKeyword(type)) {
                keywords.put(keyword(type), type);
            }
        }
        return Spelling.closest(word, keywords.keySet()).map(keywords::get);
    }

    // e.g. "'hgh' is not a valid priority. Did you mean 'high'?", with the fix that writes 'high'.
    static SyntaxProblem didYouMean(Token found, String what, int suggestion) {
        String message = String.format("'%s' %s. Did you mean %s?", found.getText(), what, describe(suggestion));
        return SyntaxProblem.withFix(message, Fixes.replace(found, found, keyword(suggestion)));
    }

    // The end of a line or of the plan is often allowed too, but naming it only adds noise.
    static IntervalSet withoutLineAndPlanEnd(IntervalSet expected) {
        IntervalSet rest = new IntervalSet(expected);
        rest.remove(NEWLINE);
        rest.remove(Token.EOF);
        return rest.isNil() ? expected : rest;
    }

    static String describe(IntervalSet expected) {
        List<String> words = withoutLineAndPlanEnd(expected).toList().stream()
                .sorted(Comparator.comparingInt(SyntaxWords::rank))
                .map(SyntaxWords::describe)
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

    // Read-only, as these sets are shared constants.
    static IntervalSet set(int... types) {
        IntervalSet set = new IntervalSet(types);
        set.setReadonly(true);
        return set;
    }
}
