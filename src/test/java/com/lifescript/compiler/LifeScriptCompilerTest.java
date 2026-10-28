package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Severity;
import com.lifescript.diagnostics.Stage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.lifescript.TestPlans.errors;
import static com.lifescript.TestPlans.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeScriptCompilerTest {

    private final LifeScriptCompiler compiler = new LifeScriptCompiler();

    @Test
    void validPlan_hasNoErrors() throws IOException {
        List<String> errors = errors(load("fullPlan.ls"));

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void validPlan_isScheduled() throws IOException {
        CompileResult result = compiler.compile(load("fullPlan.ls"));

        assertEquals(List.of(), result.diagnostics());
        assertEquals(7, result.schedule().days().size());
    }

    @Test
    void invalidPlan_hasNoSchedule() {
        CompileResult result = compiler.compile("plan: p\n");

        assertFalse(result.diagnostics().isEmpty());
        assertNull(result.schedule());
    }

    @Test
    void schedulerCrash_isReportedAsInternalError() throws IOException {
        LifeScriptCompiler crashing = new LifeScriptCompiler(plan -> { throw new IllegalStateException("boom"); });

        CompileResult result = crashing.compile(load("fullPlan.ls"));

        assertEquals(Stage.INTERNAL, result.diagnostics().get(0).stage());
        assertNull(result.schedule());
    }

    @Test
    void missingContent_isReportedAsEmptyPlan() {
        // A missing "content" arrives as null; this used to crash.
        assertEquals(List.of("Plan is empty"), errors(null));
    }

    @Test
    void blankContent_isReportedAsEmptyPlan() {
        assertEquals(List.of("Plan is empty"), errors(""));
        assertEquals(List.of("Plan is empty"), errors("  \n\n  "));
    }

    @Test
    void planWithoutTrailingNewline_isValid() {
        // The closing """ on the last line means no final newline.
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high""";

        List<String> errors = errors(plan);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void validationErrors_markFromEntryStartToEndOfLine_cycleAtTheClosingTasksDependencies() {
        String plan = """
                plan: p
                period: 2024-01-01 to 2024-01-31

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 0m
                priority: high
                effort: high
                dependencies: b

                task: b
                duration: 1h
                priority: high
                effort: high
                dependencies: a
                """;

        List<Diagnostic> diagnostics = compiler.compile(plan).diagnostics();

        // "duration: 0m" on line 9 (12 characters, so end column 13)
        assertEquals(new Diagnostic(Stage.VALIDATION, Severity.ERROR, "Duration '0m' must be greater than zero",
                9, 1, 9, 13), diagnostics.get(0));
        // the cycle is closed by b, at its "dependencies: a" on line 18 (15 characters)
        assertEquals(new Diagnostic(Stage.VALIDATION, Severity.ERROR, "Circular dependency detected: a → b → a",
                18, 1, 18, 16), diagnostics.get(1));
        assertEquals(2, diagnostics.size(), () -> "Got: " + diagnostics);
    }
}
