package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Fix;
import com.lifescript.diagnostics.TextEdit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.applyFix;
import static com.lifescript.TestPlans.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuickFixTest {

    private static final String HEAD = """
            plan: p
            period: 2030-01-07 to 2030-01-13

            availability:
            monday: flexible

            tasks:
            """;
    private static final String TASK = "task: a\nduration: 1h\npriority: high\neffort: high\n";

    private static Diagnostic firstError(String plan) {
        return compile(plan).diagnostics().get(0);
    }

    private static List<Fix> fixesOf(String plan) {
        return firstError(plan).fixes();
    }

    @Test
    void didYouMean_replacesTheWord() {
        assertEquals(List.of(Fix.replace("Change to 'high'", 10, 11, 14, "high")),
                fixesOf(HEAD + "task: a\nduration: 1h\npriority: hgh\neffort: high\n"));
        assertEquals(List.of(Fix.replace("Change to 'duration'", 9, 1, 9, "duration")),
                fixesOf(HEAD + "task: a\nDruation: 1h\npriority: high\neffort: high\n"));
        assertEquals(List.of(Fix.replace("Change to 'deadline'", 12, 1, 8, "deadline")),
                fixesOf(HEAD + TASK + "deadlin: 2030-01-10 17:00\n"));
    }

    @Test
    void aNameWithSpacesOrDashes_isReplacedAsAWhole() {
        assertEquals(List.of(Fix.replace("Change to 'write_my_report'", 8, 7, 22, "write_my_report")),
                fixesOf(HEAD + "task: write my report\nduration: 1h\npriority: high\neffort: high\n"));
        assertEquals(List.of(Fix.replace("Change to 'write_report'", 8, 7, 19, "write_report")),
                fixesOf(HEAD + "task: write-report\nduration: 1h\npriority: high\neffort: high\n"));
    }

    @Test
    void applyingTheFix_makesThePlanValid() {
        for (String broken : List.of(
                HEAD + "task: a\nduration: 1h\npriority: hgh\neffort: high\n",
                HEAD + "task: a\nduraton: 1h\npriority: high\neffort: high\n",
                HEAD + TASK + "deadlin: 2030-01-10 17:00\n",
                HEAD + "task: write my report\nduration: 1h\npriority: high\neffort: high\n",
                "plan: p\nperiod: 2030-01-07 to 2030-01-13\n\navailability:\nmondy: flexible\n\ntasks:\n" + TASK)) {
            String fixed = applyFix(broken, fixesOf(broken).get(0));

            assertEquals(List.of(), compile(fixed).diagnostics(), () -> "Still broken after the fix:\n" + fixed);
        }
    }

    private static Fix fixOnLine12(String line) {
        return fixesOf(HEAD + TASK + line + "\n").get(0);
    }

    private static Fix insert(String title, int line, int column, String text) {
        return new Fix(title, List.of(new TextEdit(line, column, line, column, text)));
    }

    @Test
    void wronglyWrittenValues_areRewrittenInTheLanguagesFormat() {
        assertEquals(Fix.replace("Change to '90m'", 12, 8, 10, "90m"), fixOnLine12("break: 90"));
        assertEquals(Fix.replace("Change to '1h30m'", 12, 8, 12, "1h30m"), fixOnLine12("break: 1.5h"));
        assertEquals(Fix.replace("Change to '90m'", 12, 8, 13, "90m"), fixOnLine12("break: 90min"));
        assertEquals(Fix.replace("Change to '2h'", 12, 8, 14, "2h"), fixOnLine12("break: 2hours"));
        assertEquals(Fix.replace("Change to '1h30m'", 12, 8, 12, "1h30m"), fixOnLine12("break: 1h30"));
        assertEquals(Fix.replace("Change to '2030-01-10'", 12, 11, 21, "2030-01-10"),
                fixOnLine12("deadline: 2030/01/10 17:00"));
        assertEquals(Fix.replace("Change to '2030-01-09'", 12, 11, 19, "2030-01-09"),
                fixOnLine12("deadline: 2030-1-9 17:00"));
        assertEquals(Fix.replace("Change to '09:00'", 12, 22, 26, "09:00"), fixOnLine12("deadline: 2030-01-10 9:00"));
        assertEquals(List.of(Fix.replace("Change to '09:00-17:00'", 5, 9, 13, "09:00-17:00")),
                fixesOf("plan: p\nperiod: 2030-01-07 to 2030-01-13\n\navailability:\nmonday: 9-17\n"));
    }

    @Test
    void notesAndTheMissingColon_getTheMissingCharacters() {
        assertEquals(insert("Add the closing \"", 12, 22, "\""), fixOnLine12("note: \"bring a laptop"));
        assertEquals(Fix.replace("Put the note in quotes", 12, 7, 21, "\"bring a laptop\""),
                fixOnLine12("note: bring a laptop"));
        assertEquals(List.of(insert("Insert ':'", 9, 9, ":")),
                fixesOf(HEAD + "task: a\nduration 1h\npriority: high\neffort: high\n"));
    }

    @Test
    void applyingAFormatFix_makesThePlanValid() {
        for (String line : List.of("break: 90", "break: 1.5h", "break: 90min", "break: 2hours", "break: 1h30",
                "deadline: 2030/01/10 17:00", "deadline: 2030-1-9 17:00", "deadline: 2030-01-10 9:00",
                "note: \"bring a laptop", "note: bring a laptop")) {
            String broken = HEAD + TASK + line + "\n";
            String fixed = applyFix(broken, fixesOf(broken).get(0));

            assertEquals(List.of(), compile(fixed).diagnostics(), () -> "Still broken after the fix:\n" + fixed);
        }
    }

    @Test
    void noFix_whenTheFormatIsAmbiguous() {
        // 17:05 or 17:50? Day/month or month/day? 1.5 what?
        assertEquals(List.of(), fixesOf(HEAD + TASK + "deadline: 2030-01-10 17:5\n"));
        assertEquals(List.of(), fixesOf(HEAD + TASK + "deadline: 10/01/2030 17:00\n"));
        assertEquals(List.of(), fixesOf(HEAD + TASK + "break: 1.5\n"));
        assertEquals(List.of(), fixesOf(HEAD + TASK + "deadline: 2030-02-30 17:00\n"));
    }

    @Test
    void noFix_whenTheRightWordIsUnknown() {
        assertEquals(List.of(), fixesOf(HEAD + "task: a\nduration: 1h\npriority: urgent\neffort: high\n"));
        // A reserved word as a name: only the user knows the name they want.
        assertEquals(List.of(), fixesOf(HEAD + "task: low\nduration: 1h\npriority: high\neffort: high\n"));
    }
}
