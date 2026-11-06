package com.lifescript.compiler;

import com.lifescript.diagnostics.Fix;
import org.antlr.v4.runtime.Token;

import java.util.List;

// A syntax error's message with the quick fixes that go with it.
record SyntaxProblem(String message, List<Fix> fixes) {

    static SyntaxProblem of(String message) {
        return new SyntaxProblem(message, List.of());
    }

    static SyntaxProblem withFix(String message, Fix fix) {
        return new SyntaxProblem(message, List.of(fix));
    }

    // Replaces the text from the first token to the last (on one line) with the new text.
    static Fix replace(Token first, Token last, String text) {
        return replace(first, last, text, String.format("Change to '%s'", text));
    }

    static Fix replace(Token first, Token last, String text, String title) {
        int startColumn = first.getCharPositionInLine() + 1;
        int endColumn = last.getCharPositionInLine() + 1 + last.getStopIndex() - last.getStartIndex() + 1;
        return Fix.replace(title, first.getLine(), startColumn, endColumn, text);
    }
}
