package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Severity;
import com.lifescript.diagnostics.SourceLocation;
import com.lifescript.diagnostics.Stage;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

import java.util.ArrayList;
import java.util.List;

// ANTLR columns are 0-based; diagnostics are 1-based like Monaco.
public class DiagnosticCollector {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public void syntaxError(Token offending, int line, int charPositionInLine, String message) {
        int startColumn = charPositionInLine + 1;
        int length = offending == null || offending.getType() == Token.EOF || offending.getText() == null
                ? 1 : Math.max(1, offending.getText().length());
        diagnostics.add(new Diagnostic(Stage.SYNTAX, Severity.ERROR, message,
                line, startColumn, line, startColumn + length));
    }

    public void error(ParserRuleContext entry, String message) {
        error(entry.getStart(), message);
    }

    public void error(Token start, String message) {
        error(locationOf(start), message);
    }

    public void error(SourceLocation location, String message) {
        diagnostics.add(new Diagnostic(Stage.VALIDATION, Severity.ERROR, message,
                location.line(), location.startColumn(), location.line(), location.endColumn()));
    }

    public static SourceLocation locationOf(ParserRuleContext entry) {
        return locationOf(entry.getStart());
    }

    public static SourceLocation locationOf(Token start) {
        int startColumn = start.getCharPositionInLine() + 1;
        return new SourceLocation(start.getLine(), startColumn, startColumn + lengthToEndOfLine(start));
    }

    public List<Diagnostic> list() {
        return diagnostics;
    }

    public boolean isEmpty() {
        return diagnostics.isEmpty();
    }

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
