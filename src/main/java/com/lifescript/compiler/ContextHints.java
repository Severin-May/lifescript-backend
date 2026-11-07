package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptLexer;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.IntervalSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.lifescript.compiler.SyntaxErrorStrategy.*;
import static com.lifescript.grammar.LifeScriptLexer.*;

// Messages that depend on where the mistake is: right after a name, on a property line, or at the plan's start.
final class ContextHints {

    private static final IntervalSet ALL_PROPERTIES = TASK_PROPERTIES.or(ROUTINE_PROPERTIES).or(EVENT_PROPERTIES);

    private record Entry(String name, int keyword, IntervalSet properties) {
    }

    private ContextHints() {
    }

    static Optional<SyntaxProblem> explain(Parser parser, Token found, IntervalSet expected) {
        TokenStream tokens = parser.getInputStream();
        // The parser reads tokens only as far as it needs; the hints look further ahead on the line.
        if (tokens instanceof BufferedTokenStream buffered) {
            buffered.fill();
        }
        return nameWithSpaces(tokens, found, expected)
                .or(() -> noteWithoutQuotes(tokens, found, expected))
                .or(() -> propertyLine(tokens, found))
                .or(() -> planStart(found, expected).map(SyntaxProblem::of));
    }

    // "task: write report" or "write-report": the name continues where the line should end.
    private static Optional<SyntaxProblem> nameWithSpaces(TokenStream tokens, Token found, IntervalSet expected) {
        int index = found.getTokenIndex();
        if (index < 1 || !(expected.contains(NEWLINE) || expected.contains(COLON) || expected.contains(COMMA))) {
            return Optional.empty();
        }
        Token name = tokens.get(index - 1);
        if (name.getType() != IDENTIFIER || name.getLine() != found.getLine()
                || !(isWord(found) || found.getType() == DASH)) {
            return Optional.empty();
        }
        List<String> words = new ArrayList<>();
        boolean dash = false;
        Token last = name;
        for (int i = index - 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.getLine() != name.getLine() || !(isWord(token) || token.getType() == DASH)) {
                break;
            }
            last = token;
            if (token.getType() == DASH) {
                dash = true;
            } else {
                words.add(token.getText());
            }
        }
        if (words.size() < 2) {
            return Optional.empty();
        }
        String fixed = String.join("_", words);
        String message = String.format("Names can't contain %s: write '%s'", dash ? "'-'" : "spaces", fixed);
        return Optional.of(SyntaxProblem.withFix(message, Fixes.replace(name, last, fixed)));
    }

    // "note: bring a laptop": the rest of the line, put in quotes.
    private static Optional<SyntaxProblem> noteWithoutQuotes(TokenStream tokens, Token found, IntervalSet expected) {
        IntervalSet options = new IntervalSet(expected);
        options.remove(NEWLINE);
        if (!options.equals(IntervalSet.of(STRING)) || found.getType() == NEWLINE || found.getType() == Token.EOF
                || found.getType() == UNCLOSED_STRING) {
            return Optional.empty();
        }
        Token last = found;
        for (int i = found.getTokenIndex() + 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.getType() == NEWLINE || token.getType() == Token.EOF || token.getLine() != found.getLine()) {
                break;
            }
            last = token;
        }
        String text = found.getInputStream().getText(Interval.of(found.getStartIndex(), last.getStopIndex()));
        String quoted = "\"" + text + "\"";
        String message = String.format("A note must be in quotes: write %s", quoted);
        if (text.contains("\"")) {
            return Optional.of(SyntaxProblem.of("A note must be in quotes, like \"bring a laptop\""));
        }
        return Optional.of(SyntaxProblem.withFix(message, Fixes.replace(found, last, quoted, "Put the note in quotes")));
    }

    // A "word:" line inside a task, routine or event that isn't one of its properties.
    private static Optional<SyntaxProblem> propertyLine(TokenStream tokens, Token found) {
        int index = found.getTokenIndex();
        Entry entry = sectionBefore(tokens, index);
        if (entry == null || !isWord(found) || index < 1 || index + 1 >= tokens.size()
                || tokens.get(index - 1).getType() != NEWLINE || tokens.get(index + 1).getType() != COLON) {
            return Optional.empty();
        }
        // After a blank line, a new section is the likelier intent; the general message covers that.
        if (index >= 2 && tokens.get(index - 2).getType() == NEWLINE) {
            return Optional.empty();
        }
        String word = found.getText();
        if (ALL_PROPERTIES.contains(found.getType())) {
            return Optional.of(SyntaxProblem.of(String.format("'%s' can't be used in %s %s. Use %s",
                    word, entry.name().equals("event") ? "an" : "a", entry.name(), describe(entry.properties()))));
        }
        IntervalSet candidates = entry.properties().or(SECTIONS).or(IntervalSet.of(entry.keyword()));
        Optional<Integer> suggestion = closestKeyword(word, candidates);
        if (suggestion.isPresent()) {
            return Optional.of(didYouMean(found, "is not a valid " + entry.name() + " property", suggestion.get()));
        }
        return Optional.of(SyntaxProblem.of(String.format("'%s' is not a valid %s property. Use %s",
                word, entry.name(), describe(entry.properties()))));
    }

    // The parser may already have left the entry when it notices the mistake, so look back at the text instead.
    private static Entry sectionBefore(TokenStream tokens, int index) {
        for (int i = index - 1; i >= 1; i--) {
            if (tokens.get(i - 1).getType() != NEWLINE) {
                continue;
            }
            switch (tokens.get(i).getType()) {
                case TASKS: return new Entry("task", TASK, TASK_PROPERTIES);
                case ROUTINES: return new Entry("routine", ROUTINE, ROUTINE_PROPERTIES);
                case EVENTS: return new Entry("event", EVENT, EVENT_PROPERTIES);
                case SETTINGS, AVAILABILITY, ENERGY: return null;
                default: break;
            }
        }
        return null;
    }

    // Unless it's a typo of 'plan' or 'period', which the general message suggests.
    private static Optional<String> planStart(Token found, IntervalSet expected) {
        boolean typo = isWord(found) && Spelling.closest(found.getText(), List.of("plan", "period"))
                .filter(word -> !word.equalsIgnoreCase(found.getText())).isPresent();
        if (typo || expected.contains(IDENTIFIER)) {
            return Optional.empty();
        }
        if (expected.contains(PLAN)) {
            return Optional.of("A plan must start with a 'plan:' line, like plan: my_week");
        }
        if (expected.contains(PERIOD)) {
            return Optional.of("The 'plan:' line must be followed by a 'period:' line, like period: 2030-01-07 to 2030-01-13");
        }
        return Optional.empty();
    }

    private static boolean isWord(Token token) {
        if (token.getType() == IDENTIFIER || token.getType() == UNKNOWN_WORD) {
            return true;
        }
        String literal = LifeScriptLexer.VOCABULARY.getLiteralName(token.getType());
        return literal != null && literal.matches("'[a-z]+'");
    }
}
