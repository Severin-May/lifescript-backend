package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Fix;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.applyFix;
import static com.lifescript.TestPlans.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationFixTest {

    private static final String HEAD = """
            plan: p
            period: 2030-01-07 to 2030-01-13

            availability:
            monday: flexible

            tasks:
            task: research
            duration: 1h
            priority: high
            effort: high

            task: gym
            duration: 1h
            priority: medium
            effort: moderate
            repeats: daily

            """;

    private static String withTask(String extraLines) {
        return HEAD + "task: write\nduration: 2h\npriority: high\neffort: high\n" + extraLines + "\n";
    }

    private static Diagnostic onlyError(String plan) {
        List<Diagnostic> diagnostics = compile(plan).diagnostics();
        assertEquals(1, diagnostics.size(), () -> "Expected one error: " + diagnostics);
        return diagnostics.get(0);
    }

    // The fix's title, and that applying it leaves a valid plan.
    private static void assertFixMakesValid(String plan, String title) {
        Fix fix = onlyError(plan).fixes().get(0);
        assertEquals(title, fix.title());
        String fixed = applyFix(plan, fix);
        assertEquals(List.of(), compile(fixed).diagnostics(), () -> "Still broken after the fix:\n" + fixed);
    }

    @Test
    void unknownDependency_closeInSpellingOrCase_isReplaced() {
        assertEquals("Task 'write' depends on 'reserch', which doesn't exist (did you mean 'research'?)",
                onlyError(withTask("dependencies: reserch")).message());
        assertFixMakesValid(withTask("dependencies: reserch"), "Change to 'research'");
        assertFixMakesValid(withTask("dependencies: Research"), "Change to 'research'");
    }

    @Test
    void unknownDependency_farFromEveryTask_getsNoGuess() {
        Diagnostic error = onlyError(withTask("dependencies: shopping"));

        assertEquals("Task 'write' depends on 'shopping', which doesn't exist", error.message());
        assertEquals(List.of(), error.fixes());
    }

    @Test
    void duplicateDependency_isRemovedWithItsComma() {
        String plan = HEAD + "task: draft\nduration: 1h\npriority: low\neffort: low\n\n"
                + "task: write\nduration: 2h\npriority: high\neffort: high\ndependencies: research, draft, research\n";

        assertFixMakesValid(plan, "Remove the second 'research'");
        assertEquals(plan.replace("research, draft, research", "research, draft"),
                applyFix(plan, onlyError(plan).fixes().get(0)));
    }

    @Test
    void dependencyOnARepeatingTask_isRemoved_andTheLineIfItWasTheOnlyOne() {
        assertFixMakesValid(withTask("dependencies: research, gym"), "Remove 'gym' from the dependencies");
        assertFixMakesValid(withTask("dependencies: gym"), "Remove 'gym' from the dependencies");

        String plan = withTask("dependencies: gym");
        assertEquals(plan.replace("dependencies: gym\n", ""), applyFix(plan, onlyError(plan).fixes().get(0)));
    }

    @Test
    void minutesOver59_areRewrittenAsHoursAndMinutes() {
        String plan = HEAD + "task: write\nduration: 1h75m\npriority: high\neffort: high\n";

        assertEquals("Minutes in '1h75m' must be at most 59 (write 2h15m)", onlyError(plan).message());
        assertFixMakesValid(plan, "Change to '2h15m'");
        assertFixMakesValid(withTask("break: 1h60m"), "Change to '2h'");
    }

    @Test
    void repeatingTaskWithADeadline_offersBothWaysOut() {
        String plan = HEAD.replace("repeats: daily\n", "repeats: daily\ndeadline: 2030-01-10 17:00\n");
        List<Fix> fixes = onlyError(plan).fixes();

        assertEquals(List.of("Remove the deadline", "Remove 'repeats' (make it a one-time task)"),
                fixes.stream().map(Fix::title).toList());
        for (Fix fix : fixes) {
            String fixed = applyFix(plan, fix);
            assertEquals(List.of(), compile(fixed).diagnostics(), () -> "Still broken after the fix:\n" + fixed);
        }
    }

    @Test
    void missingProperty_isAddedBelowTheTaskLine_indentedLikeTheOthers() {
        String plan = HEAD + "task: write\n    duration: 2h\n    priority: high\n";

        assertEquals("Task 'write' is missing mandatory property: effort", onlyError(plan).message());
        assertFixMakesValid(plan, "Add 'effort: moderate'");
        assertEquals(HEAD + "task: write\n    effort: moderate\n    duration: 2h\n    priority: high\n",
                applyFix(plan, onlyError(plan).fixes().get(0)));
    }

    @Test
    void aTaskWithOnlyItsName_isCompletedFixByFix() {
        String plan = HEAD + "task: write\nnote: \"first draft\"\n";

        for (int i = 0; i < 3; i++) {
            List<Diagnostic> errors = compile(plan).diagnostics();
            assertEquals(3 - i, errors.size(), () -> "Errors: " + errors);
            plan = applyFix(plan, errors.get(0).fixes().get(0));
        }
        assertEquals(List.of(), compile(plan).diagnostics());
        assertEquals(HEAD + "task: write\neffort: moderate\npriority: medium\nduration: 1h\nnote: \"first draft\"\n", plan);
    }

    @Test
    void deadlineWithoutATime_getsTheSuggestedTime() {
        assertFixMakesValid(withTask("deadline: 2030-01-10"), "Add the time 17:00");
        assertEquals(withTask("deadline: 2030-01-10 17:00"),
                applyFix(withTask("deadline: 2030-01-10"), onlyError(withTask("deadline: 2030-01-10")).fixes().get(0)));
    }

    @Test
    void repeatingTaskWithDependencies_losesTheLine() {
        String plan = HEAD.replace("repeats: daily\n", "repeats: daily\ndependencies: research\n");

        assertFixMakesValid(plan, "Remove the dependencies");
    }
}
