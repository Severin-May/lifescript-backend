package com.lifescript.compiler;

import com.lifescript.diagnostics.Fix;
import com.lifescript.diagnostics.TextEdit;
import org.antlr.v4.runtime.Token;

import java.util.List;

// Builds quick fixes from token positions (ANTLR columns are 0-based, fixes 1-based like Monaco).
final class Fixes {

    private Fixes() {
    }

    // Replaces the text from the first token to the last (on one line).
    static Fix replace(Token first, Token last, String text, String title) {
        return Fix.replace(title, first.getLine(), startColumn(first), endColumn(last), text);
    }

    static Fix replace(Token first, Token last, String text) {
        return replace(first, last, text, String.format("Change to '%s'", text));
    }

    static Fix insertAfter(Token token, String text, String title) {
        int end = endColumn(token);
        return new Fix(title, List.of(new TextEdit(token.getLine(), end, token.getLine(), end, text)));
    }

    // The whole line, with its indentation and line break.
    static Fix removeLine(int line, String title) {
        return new Fix(title, List.of(new TextEdit(line, 1, line + 1, 1, "")));
    }

    // A new line right below the given one, e.g. a missing property under "task: a".
    static Fix insertLineBelow(int line, String text, String title) {
        return new Fix(title, List.of(new TextEdit(line + 1, 1, line + 1, 1, text + "\n")));
    }

    // One item of a comma-separated list on one line, with the comma that belongs to it. Needs at least two items.
    static Fix removeListItem(List<Token> items, int index, String title) {
        if (items.size() < 2) {
            throw new IllegalArgumentException("A list of one item is removed as a whole line");
        }
        Token item = items.get(index);
        TextEdit edit = index > 0
                ? new TextEdit(item.getLine(), endColumn(items.get(index - 1)), item.getLine(), endColumn(item), "")
                : new TextEdit(item.getLine(), startColumn(item), item.getLine(), startColumn(items.get(1)), "");
        return new Fix(title, List.of(edit));
    }

    private static int startColumn(Token token) {
        return token.getCharPositionInLine() + 1;
    }

    // From the positions, not getText(): a name's text is normalized and can be shorter than in the editor.
    private static int endColumn(Token token) {
        return startColumn(token) + token.getStopIndex() - token.getStartIndex() + 1;
    }
}
