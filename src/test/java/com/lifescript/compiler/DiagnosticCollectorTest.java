package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Severity;
import com.lifescript.diagnostics.Stage;
import com.lifescript.grammar.LifeScriptLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DiagnosticCollectorTest {

    private static List<? extends Token> tokens(String text) {
        return new LifeScriptLexer(CharStreams.fromString(text)).getAllTokens();
    }

    private static Token first(List<? extends Token> tokens, int type) {
        return tokens.stream().filter(t -> t.getType() == type).findFirst().orElseThrow();
    }

    @Test
    void validationError_marksFromEntryStartToEndOfLine_includingATrailingComment() {
        List<? extends Token> tokens = tokens("task: a\n    duration: 0m   # note\n");
        DiagnosticCollector collector = new DiagnosticCollector();

        collector.error(first(tokens, LifeScriptLexer.DURATION), "Duration '0m' must be greater than zero");

        // "duration: 0m   # note" starts at column 5 and is 21 characters long
        assertEquals(List.of(new Diagnostic(Stage.VALIDATION, Severity.ERROR,
                "Duration '0m' must be greater than zero", 2, 5, 2, 26)), collector.list());
    }

    @Test
    void validationError_windowsLineEndings_stopBeforeCarriageReturn() {
        List<? extends Token> tokens = tokens("task: a\r\n");
        DiagnosticCollector collector = new DiagnosticCollector();

        collector.error(first(tokens, LifeScriptLexer.TASK), "message");

        Diagnostic d = collector.list().get(0);
        assertEquals(1, d.startColumn());
        assertEquals(8, d.endColumn());   // "task: a" is 7 characters
    }

    @Test
    void syntaxError_marksExactlyTheOffendingToken_orOneCharacterAtEndOfFile() {
        List<? extends Token> tokens = tokens("task: low\n");
        Token low = first(tokens, LifeScriptLexer.LOW);
        DiagnosticCollector collector = new DiagnosticCollector();

        collector.syntaxError(low, low.getLine(), low.getCharPositionInLine(), "Syntax error - mismatched input 'low'");
        collector.syntaxError(null, 2, 0, "Syntax error - missing NEWLINE at '<EOF>'");

        assertEquals(new Diagnostic(Stage.SYNTAX, Severity.ERROR, "Syntax error - mismatched input 'low'", 1, 7, 1, 10),
                collector.list().get(0));
        assertEquals(new Diagnostic(Stage.SYNTAX, Severity.ERROR, "Syntax error - missing NEWLINE at '<EOF>'", 2, 1, 2, 2),
                collector.list().get(1));
    }

    @Test
    void format_isTheClassicCompilerForm_andWithoutPositionJustTheMessage() {
        Diagnostic positioned = new Diagnostic(Stage.VALIDATION, Severity.ERROR, "Duration '0m' must be greater than zero",
                9, 5, 9, 17);
        Diagnostic unpositioned = Diagnostic.withoutPosition(Stage.VALIDATION, "Routine a overlaps routine b on monday");

        assertEquals("Line 9: Duration '0m' must be greater than zero", positioned.format());
        assertEquals("Routine a overlaps routine b on monday", unpositioned.format());
        assertFalse(unpositioned.hasPosition());
    }
}
