package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.errors;
import static com.lifescript.TestPlans.syntaxErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxErrorTest {

    private final LifeScriptCompiler compiler = new LifeScriptCompiler();

    @Test
    void syntaxError_isADiagnosticMarkingExactlyTheOffendingToken() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration 1h
                priority: high
                effort: high
                """;

        Diagnostic first = compiler.compile(plan).diagnostics().get(0);

        // line 9, columns 10-11 ("1h"); the end column is exclusive
        assertEquals(Stage.SYNTAX, first.stage());
        assertEquals(List.of(9, 10, 9, 12),
                List.of(first.startLineNumber(), first.startColumn(), first.endLineNumber(), first.endColumn()));
        assertTrue(errors(plan).get(0).startsWith("Line 9: Syntax error - "), () -> errors(plan).get(0));
    }

    @Test
    void syntaxError_isReported() {
        String plan = """
                plan p
                period: 2024-01-01 to 2024-01-31

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> syntaxErrors = syntaxErrors(plan);
        assertFalse(syntaxErrors.isEmpty());
        assertTrue(syntaxErrors.stream().anyMatch(e -> e.contains("Syntax error")));

        List<String> compileErrors = errors(plan);
        assertEquals(syntaxErrors, compileErrors,
                "compile should stop at syntax errors without running semantic checks");
    }

    @Test
    void keywordUsedAsName_isReportedAsReservedWord_atTheKeyword() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: low
                duration: 1h
                priority: high
                effort: high

                events:
                event: date
                date: 2024-01-03
                time: morning
                """;

        Diagnostic first = compiler.compile(plan).diagnostics().get(0);

        assertEquals("'low' is a reserved word and can't be used as a name", first.message());
        // line 8, columns 7-9 ("low")
        assertEquals(List.of(8, 7, 8, 10),
                List.of(first.startLineNumber(), first.startColumn(), first.endLineNumber(), first.endColumn()));
        assertTrue(errors(plan).contains("Line 14: 'date' is a reserved word and can't be used as a name"),
                () -> "Got: " + errors(plan));
    }

    @Test
    void keywordUsedAsActivityName_afterTheFirstActivity_isReportedAsReservedWord() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: r
                time: 07:00
                repeats: daily
                activities:
                stretch: 10m
                time: 30m

                routine: s
                time: 09:00
                repeats: daily
                activities:
                walk: 10m
                repeats: 15m

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        Diagnostic first = compiler.compile(plan).diagnostics().get(0);

        // the "time" of "time: 30m" on line 13
        assertEquals(List.of(13, 1, 13, 5),
                List.of(first.startLineNumber(), first.startColumn(), first.endLineNumber(), first.endColumn()));
        assertEquals(List.of(
                "Line 13: 'time' is a reserved word and can't be used as a name",
                "Line 20: 'repeats' is a reserved word and can't be used as a name"), errors(plan));
    }

    @Test
    void eventTimeWithADuration_isAnOrdinarySyntaxError() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                events:
                event: dentist
                date: 2024-01-03
                time: 30m

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        assertTrue(errors(plan).get(0).startsWith("Line 10: Syntax error - "), () -> errors(plan).get(0));
    }

    @Test
    void routine_namedPeriodAsTime_isSyntaxError() {
        // A routine's time is a start time; named periods are ranges, so they no longer fit.
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                routines:
                routine: r
                time: morning
                repeats: daily
                activities:
                stretch: 15m

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """;

        List<String> errors = syntaxErrors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Syntax error")), () -> "Got: " + errors);
    }

    @Test
    void taskStart_isNoLongerSupported() {
        // Tasks are always flexible; anything at a fixed time is a routine.
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                start: 10:00
                """;

        List<String> errors = syntaxErrors(plan);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Syntax error")), () -> "Got: " + errors);
    }
}
