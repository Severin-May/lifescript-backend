package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Severity;
import com.lifescript.diagnostics.Stage;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

import java.util.ArrayList;
import java.util.List;

// Collects diagnostics during compilation, working out each position from ANTLR tokens.
// ANTLR counts lines from 1 but columns from 0; diagnostics count both from 1 (like Monaco).
public class DiagnosticCollector {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    // A syntax error marks exactly the token the parser couldn't use (or one character if there is
    // no token to mark, e.g. at the end of the file).
    public void syntaxError(Token offending, int line, int charPositionInLine, String message) {
        int startColumn = charPositionInLine + 1;
        int length = offending == null || offending.getType() == Token.EOF || offending.getText() == null
                ? 1 : Math.max(1, offending.getText().length());
        diagnostics.add(new Diagnostic(Stage.SYNTAX, Severity.ERROR, message,
                line, startColumn, line, startColumn + length));
    }

    // A validation error marks from where the entry starts to the end of that line,
    // e.g. the whole "duration: 0m" or "task: write_report" line.
    public void error(ParserRuleContext entry, String message) {
        error(entry.getStart(), message);
    }

    public void error(Token start, String message) {
        int line = start.getLine();
        int startColumn = start.getCharPositionInLine() + 1;
        diagnostics.add(new Diagnostic(Stage.VALIDATION, Severity.ERROR, message,
                line, startColumn, line, startColumn + lengthToEndOfLine(start)));
    }

    // For validation problems not tied to a place (see Diagnostic.withoutPosition).
    public void errorWithoutPosition(String message) {
        diagnostics.add(Diagnostic.withoutPosition(Stage.VALIDATION, message));
    }

    public List<Diagnostic> list() {
        return diagnostics;
    }

    public boolean isEmpty() {
        return diagnostics.isEmpty();
    }

    // Characters from the token's start to the end of its line (at least 1, so the mark is visible).
    private static int lengthToEndOfLine(Token start) {
        CharStream input = start.getInputStream();
        int from = start.getStartIndex();
        if (input == null || from < 0) {
            return 1;
        }
        int to = from;
        while (to < input.size()) {
            String next = input.getText(Interval.of(to, to));
            if (next.equals("\n") || next.equals("\r")) {
                break;
            }
            to++;
        }
        return Math.max(1, to - from);
    }
}
