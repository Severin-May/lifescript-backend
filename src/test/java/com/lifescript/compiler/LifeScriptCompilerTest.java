package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Severity;
import com.lifescript.diagnostics.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static com.lifescript.TestPlans.compiler;
import static com.lifescript.TestPlans.errors;
import static com.lifescript.TestPlans.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeScriptCompilerTest {

    private final LifeScriptCompiler compiler = compiler();

    @Test
    void validPlan_hasNoErrors() throws IOException {
        List<String> errors = errors(load("fullPlan.ls"));

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void validPlan_hasThePlan() throws IOException {
        CompileResult result = compiler.compile(load("fullPlan.ls"));

        assertEquals(List.of(), result.diagnostics());
        assertEquals("my_week", result.plan().name());
    }

    @Test
    void invalidPlan_hasNoPlan() {
        CompileResult result = compiler.compile("plan: p\n");

        assertFalse(result.diagnostics().isEmpty());
        assertNull(result.plan());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void stackOverflow_isReportedAsInternalError_andLoggedWithoutThePlansContents(CapturedOutput output) throws Exception {
        StringBuilder plan = new StringBuilder("plan: p\nperiod: 2024-01-01 to 2024-01-07\n\navailability:\nmonday: flexible\n\ntasks:\n");
        for (int i = 0; i < 5000; i++) {
            plan.append("task: t").append(i).append("\nduration: 15m\npriority: low\neffort: low\n")
                    .append(i == 0 ? "" : "dependencies: t" + (i - 1) + "\n").append("\n");
        }
        AtomicReference<Object> outcome = new AtomicReference<>();
        // A small stack makes the dependency recursion overflow on any machine.
        Thread thread = new Thread(null, () -> {
            try {
                outcome.set(compiler.compile(plan.toString()));
            } catch (Throwable t) {
                outcome.set(t);
            }
        }, "small-stack", 256 * 1024);
        thread.start();
        thread.join();

        assertTrue(outcome.get() instanceof CompileResult, () -> "Got: " + outcome.get());
        assertEquals(Stage.INTERNAL, ((CompileResult) outcome.get()).diagnostics().get(0).stage());
        assertTrue(output.getOut().contains("Compiler crashed"), output::getOut);
        assertFalse(output.getOut().contains("duration: 15m"), output::getOut);
    }

    @Test
    void worksTheSameUnderATurkishDefaultLocale() throws IOException {
        // In Turkish, "i".toUpperCase() is "İ", so "friday" would become "FRİDAY".
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertEquals(List.of(), compiler.compile(load("fullPlan.ls")).diagnostics());
            assertEquals(List.of("Line 13: Routine 'a' (09:00-10:00) overlaps routine 'b' (09:30-10:30) on friday"), errors("""
                    plan: p
                    period: 2024-01-01 to 2024-01-07

                    availability:
                    monday: flexible

                    routines:
                    routine: a
                    time: 09:00
                    repeats: friday
                    activities:
                    work: 1h
                    routine: b
                    time: 09:30
                    repeats: friday
                    activities:
                    work: 1h

                    tasks:
                    task: t
                    duration: 1h
                    priority: high
                    effort: high
                    """));
        } finally {
            Locale.setDefault(original);
        }
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
                period: 2024-01-01 to 2024-01-07

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
                period: 2024-01-01 to 2024-01-07

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
