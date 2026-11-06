package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.compile;
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
        assertEquals(List.of("Line 10: 'urgent' is not a valid priority. Use 'critical', 'high', 'medium' or 'low'"),
                errorsInTasks("task: a\nduration: 1h\npriority: urgent\neffort: high\n"));
        assertEquals(List.of("Line 9: 'length' is not a valid task property. Use 'duration', 'priority', 'effort', "
                        + "'deadline', 'dependencies', 'repeats', 'break' or 'note'"),
                errorsInTasks("task: a\nlength: 1h\npriority: high\neffort: high\n"));
    }

    @Test
    void aTypoOfAnAllowedWord_getsADidYouMean() {
        assertEquals(List.of("Line 10: 'hgh' is not a valid priority. Did you mean 'high'?"),
                errorsInTasks("task: a\nduration: 1h\npriority: hgh\neffort: high\n"));
        assertEquals(List.of("Line 9: 'duraton' is not a valid task property. Did you mean 'duration'?"),
                errorsInTasks("task: a\nduraton: 1h\npriority: high\neffort: high\n"));
        assertEquals(List.of("Line 9: 'Druation' is not a valid task property. Did you mean 'duration'?"),
                errorsInTasks("task: a\nDruation: 1h\npriority: high\neffort: high\n"));
        assertEquals(List.of("Line 5: 'mondy' is not a valid day. Did you mean 'monday'?"),
                syntaxErrors("plan: p\nperiod: 2030-01-07 to 2030-01-13\n\navailability:\nmondy: flexible\n"));
        assertEquals(List.of("Line 2: 'perod' is not valid here. Did you mean 'period'?"),
                syntaxErrors("plan: p\nperod: 2030-01-07 to 2030-01-13\n"));
    }

    @Test
    void aWordFarFromEveryAllowedWord_getsNoGuess() {
        assertEquals(List.of("Line 13: 'goals' is not a valid section. "
                        + "Use 'settings', 'availability', 'energy profile', 'routines', 'tasks' or 'events'"),
                errorsInTasks("task: a\nduration: 1h\npriority: high\neffort: high\n\ngoals:\n"));
    }

    @Test
    void endOfLineAndEndOfPlan_areNamedInWords() {
        assertEquals(List.of("Line 8: Expected 'task', but found the end of the plan"), errorsInTasks(""));
    }

    private static List<String> errorsInTask(String line) {
        return errorsInTasks("task: a\nduration: 1h\npriority: high\neffort: high\n" + line + "\n");
    }

    @Test
    void wronglyWrittenValues_giveOneErrorEach_explainingTheFormat() {
        assertEquals(List.of("Line 12: '90' needs a unit, like 90m or 1h30m"), errorsInTask("break: 90"));
        assertEquals(List.of("Line 12: Durations can't have decimals: write '1.5h' with h and m, like 1h30m"),
                errorsInTask("break: 1.5h"));
        assertEquals(List.of("Line 12: Write the duration '90min' with h and m only, like 30m, 2h or 1h30m"),
                errorsInTask("break: 90min"));
        assertEquals(List.of("Line 12: Write the date '2030/01/10' as year-month-day, like 2030-01-07"),
                errorsInTask("deadline: 2030/01/10 17:00"));
        assertEquals(List.of("Line 12: Write the time '9:00' with two-digit hours and minutes, like 09:00"),
                errorsInTask("deadline: 2030-01-10 9:00"));
        assertEquals(List.of("Line 12: The note is missing its closing \" (a note must end on the same line)"),
                errorsInTask("note: \"bring a laptop"));
        assertEquals(List.of("Line 12: A note must be in quotes: write \"bring a laptop\""),
                errorsInTask("note: bring a laptop"));
        assertEquals(List.of("Line 10: Unexpected character '!'"),
                errorsInTasks("task: a\nduration: 1h\npriority: high!\neffort: high\n"));
    }

    @Test
    void timeRangesAndNames_areExplainedToo() {
        assertEquals(List.of("Line 5: Write the time range '9-17' with two-digit hours and minutes, like 09:00-17:00"),
                syntaxErrors("plan: p\nperiod: 2030-01-07 to 2030-01-13\n\navailability:\nmonday: 9-17\n"));
        assertEquals(List.of("Line 8: '2nd_draft' can't be a name: names must start with a letter or _"),
                errorsInTasks("task: 2nd_draft\nduration: 1h\npriority: high\neffort: high\n"));
    }

    @Test
    void theErrorMarksTheWholeWord() {
        Diagnostic error = compile(HEAD + "task: a\nduration: 1h\npriority: high\neffort: high\ndeadline: 2030-01-10 9:00\n")
                .diagnostics().get(0);

        // "9:00" is columns 22-25; the lexer reads it as "9", ":", "00".
        assertEquals(List.of(12, 22, 12, 26),
                List.of(error.startLineNumber(), error.startColumn(), error.endLineNumber(), error.endColumn()));
    }

    @Test
    void aWordAtTheVeryEnd_isShownWithoutTheLineBreakAddedForTheParser() {
        assertEquals(List.of("Line 11: '5' needs a unit, like 5m or 1h30m"),
                errorsInTasks("task: a\npriority: high\neffort: high\nduration: 5"));
    }

    @Test
    void namesWithSpacesOrDashes_getTheNameWrittenWithUnderscores() {
        assertEquals(List.of("Line 8: Names can't contain spaces: write 'write_my_report'"),
                errorsInTasks("task: write my report\nduration: 1h\npriority: high\neffort: high\n"));
        assertEquals(List.of("Line 8: Names can't contain '-': write 'write_report'"),
                errorsInTasks("task: write-report\nduration: 1h\npriority: high\neffort: high\n"));
        assertEquals(List.of("Line 12: Names can't contain spaces: write 'read_book'"),
                errorsInTask("dependencies: read book"));
    }

    @Test
    void aPropertyOfAnotherKindOfEntry_saysWhichPropertiesFit() {
        assertEquals(List.of("Line 12: 'date' can't be used in a task. Use 'duration', 'priority', 'effort', "
                        + "'deadline', 'dependencies', 'repeats', 'break' or 'note'"),
                errorsInTask("date: 2030-01-08"));
        assertEquals(List.of("Line 11: 'duration' can't be used in an event. Use 'date', 'time' or 'note'"),
                syntaxErrors(HEAD.replace("tasks:\n", "events:\n")
                        + "event: dentist\ndate: 2030-01-08\ntime: 10:00-11:00\nduration: 1h\n"));
    }

    @Test
    void anUnknownPropertyLaterInAnEntry_isNamedAsAProperty() {
        assertEquals(List.of("Line 12: 'start' is not a valid task property. Use 'duration', 'priority', 'effort', "
                        + "'deadline', 'dependencies', 'repeats', 'break' or 'note'"),
                errorsInTask("start: 10:00"));
        assertEquals(List.of("Line 12: 'deadlin' is not a valid task property. Did you mean 'deadline'?"),
                errorsInTask("deadlin: 2030-01-10 17:00"));
    }

    @Test
    void aMissingPlanOrPeriodLine_showsTheLineThatIsExpected() {
        assertEquals(List.of("Line 3: The 'plan:' line must be followed by a 'period:' line, "
                        + "like period: 2030-01-07 to 2030-01-13"),
                syntaxErrors("plan: p\n\ntasks:\ntask: a\nduration: 1h\npriority: high\neffort: high\n"));
        assertEquals(List.of("Line 1: A plan must start with a 'plan:' line, like plan: my_week"),
                syntaxErrors("period: 2030-01-07 to 2030-01-13\n"));
    }

    @Test
    void aMistakeLaterInALine_isReportedWithThatSpotsOptions() {
        // ANTLR used to report this with the options of the previous line's end ("expecting 'period'").
        assertEquals(List.of("Line 2: Expected 'to', but found '-'"),
                syntaxErrors("plan: p\nperiod: 2030-01-07 - 2030-01-13\n"));
    }
}
