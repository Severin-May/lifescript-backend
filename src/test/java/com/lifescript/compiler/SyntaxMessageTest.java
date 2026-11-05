package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.syntaxErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;

// The wording of syntax errors: plain words, never grammar names like DURATION_VAL, NEWLINE or <EOF>.
class SyntaxMessageTest {

    private static final String HEAD = """
            plan: p
            period: 2030-01-07 to 2030-01-13

            availability:
            monday: flexible

            tasks:
            """;

    private static List<String> errorsInTasks(String tasks) {
        return syntaxErrors(HEAD + tasks);
    }

    @Test
    void missingColon_namesTheWordBeforeIt() {
        assertEquals(List.of("Line 9: Missing ':' after 'duration'"),
                errorsInTasks("task: a\nduration 1h\npriority: high\neffort: high\n"));
    }

    @Test
    void missingValue_saysWhatKindOfValue_withAnExample() {
        assertEquals(List.of("Line 9: Missing a duration (like 30m or 1h30m) after 'duration:'"),
                errorsInTasks("task: a\nduration:\npriority: high\neffort: high\n"));
        assertEquals(List.of("Line 12: Missing a name after ','"),
                errorsInTasks("task: a\nduration: 1h\npriority: high\neffort: high\ndependencies: b,\n"));
    }

    @Test
    void wrongWord_fromAKnownSet_namesTheSet_andListsItInNaturalOrder() {
        assertEquals(List.of("Line 10: 'hgh' is not a valid priority. Use 'critical', 'high', 'medium' or 'low'"),
                errorsInTasks("task: a\nduration: 1h\npriority: hgh\neffort: high\n"));
        assertEquals(List.of("Line 9: 'duraton' is not a valid task property. Use 'duration', 'priority', 'effort', "
                        + "'deadline', 'dependencies', 'repeats', 'break' or 'note'"),
                errorsInTasks("task: a\nduraton: 1h\npriority: high\neffort: high\n"));
    }

    @Test
    void endOfLineAndEndOfPlan_areNamedInWords() {
        assertEquals(List.of("Line 8: Expected 'task', but found the end of the plan"), errorsInTasks(""));
        assertEquals(List.of("Line 12: Expected a note in quotes (like \"bring a laptop\"), but found 'hello'"),
                errorsInTasks("task: a\nduration: 1h\npriority: high\neffort: high\nnote: hello\n"));
    }

    @Test
    void aMistakeLaterInALine_isReportedWithThatSpotsOptions() {
        // ANTLR used to report this with the options of the previous line's end ("expecting 'period'").
        assertEquals(List.of("Line 2: Expected 'to', but found '-'"),
                syntaxErrors("plan: p\nperiod: 2030-01-07 - 2030-01-13\n"));
    }
}
