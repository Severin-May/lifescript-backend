package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.model.Task;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.compiler;
import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UnicodeNamesTest {

    // "é" written as one character, and as "e" followed by a combining accent: they look the same.
    private static final String E_ONE_CHAR = "é";
    private static final String E_TWO_CHARS = "é";

    private static String plan(String tasks) {
        return """
                plan: p
                period: 2024-01-01 to 2024-01-07

                availability:
                monday: flexible

                tasks:
                %s
                """.formatted(tasks);
    }

    private static String task(String name, String extra) {
        return "task: " + name + "\nduration: 1h\npriority: high\neffort: high\n" + extra + "\n";
    }

    @Test
    void namesInOtherLanguages_areAccepted_asNamesAndDependencies() {
        String source = plan(task("házifeladat", "") + task("встреча", "dependencies: házifeladat")
                + task("नमस्ते", "dependencies: встреча") + "Task: Ebéd\nDuration: 30m\nPriority: Low\nEffort: Low\n");

        CompileResult result = compiler().compile(source);

        assertEquals(List.of(), result.diagnostics());
        assertEquals(List.of("házifeladat", "встреча", "नमस्ते", "Ebéd"),
                result.plan().tasks().stream().map(Task::name).toList());
    }

    @Test
    void bothWaysOfWritingAnAccent_areTheSameName() {
        String source = plan(task("caf" + E_ONE_CHAR, "") + task("report", "dependencies: caf" + E_TWO_CHARS));

        CompileResult result = compiler().compile(source);

        assertEquals(List.of(), result.diagnostics());
        assertEquals(List.of("caf" + E_ONE_CHAR), result.plan().tasks().get(1).dependencies());
    }

    @Test
    void bothWaysOfWritingAnAccent_countAsADuplicate() {
        assertEquals(List.of("Line 13: Duplicate task name 'café'"),
                errors(plan(task("caf" + E_ONE_CHAR, "") + task("caf" + E_TWO_CHARS, ""))));
    }

    @Test
    void positionsAfterATwoCharacterAccent_matchTheEditorText() {
        // In the editor, "café" written with a combining accent is 5 characters long, so 'low' starts at column 22.
        List<Diagnostic> diagnostics = compiler().compile(
                plan(task("a", "dependencies: caf" + E_TWO_CHARS + ", low"))).diagnostics();

        Diagnostic reserved = diagnostics.get(0);
        assertEquals("'low' is a reserved word and can't be used as a name", reserved.message());
        assertEquals(22, reserved.startColumn());
        assertEquals(25, reserved.endColumn());
    }
}
