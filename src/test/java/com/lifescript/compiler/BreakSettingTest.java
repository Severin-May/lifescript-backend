package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BreakSettingTest {

    private static String plan(String settings, String taskName) {
        return """
                plan: p
                period: 2024-01-01 to 2024-01-07

                %s
                availability:
                monday: flexible

                tasks:
                task: %s
                duration: 1h
                priority: high
                effort: high
                """.formatted(settings, taskName);
    }

    private static String withSettings(String... entries) {
        return plan("settings:\n" + String.join("\n", entries) + "\n", "a");
    }

    @Test
    void break_isRead_andIsZeroWhenNotWritten() {
        assertEquals(Duration.ofMinutes(45), build(withSettings("break: 45m")).breakTime());
        assertEquals(Duration.ZERO, build(plan("", "a")).breakTime());
        assertEquals(Duration.ZERO, build(withSettings("morning: 07:00-12:00")).breakTime());
    }

    @Test
    void break_canBeZero_orUpToTwoHours() {
        assertEquals(List.of(), errors(withSettings("break: 0m")));
        assertEquals(List.of(), errors(withSettings("Break: 2H")));
    }

    @Test
    void break_overTwoHours_orWithTooManyMinutes_isReported() {
        assertEquals(List.of("Line 5: Break '2h10m' is too long (at most 2h)"), errors(withSettings("break: 2h10m")));
        assertEquals(List.of("Line 5: Minutes in '1h60m' must be at most 59 (write 2h)"), errors(withSettings("break: 1h60m")));
    }

    @Test
    void break_writtenTwice_isADuplicate() {
        assertEquals(List.of("Line 6: Duplicate settings entry 'break'"), errors(withSettings("break: 10m", "break: 15m")));
    }

    @Test
    void break_nextToNamedPeriods_keepsThemWorking() {
        var plan = build(withSettings("morning: 07:00-12:00", "break: 5m"));

        assertEquals(Duration.ofMinutes(5), plan.breakTime());
        assertEquals("07:00-12:00", plan.timeSettings().get("morning").toString());
    }

    @Test
    void break_isAReservedWord() {
        assertEquals(List.of("Line 9: 'break' is a reserved word and can't be used as a name"),
                errors(plan("", "break")));
    }

    private static String taskWith(String... properties) {
        return plan("", "a\n" + String.join("\n", properties));
    }

    @Test
    void taskBreak_isRead_andIsNullWhenNotWritten() {
        assertEquals(Duration.ofMinutes(30), build(taskWith("break: 30m")).tasks().get(0).breakTime());
        assertEquals(null, build(plan("", "a")).tasks().get(0).breakTime());
    }

    @Test
    void taskBreak_followsTheSameRulesAsTheSetting() {
        assertEquals(List.of(), errors(taskWith("break: 0m")));
        assertEquals(List.of("Line 10: Break '3h' is too long (at most 2h)"), errors(taskWith("break: 3h")));
        assertEquals(List.of("Line 11: Duplicate task property 'break'"), errors(taskWith("break: 10m", "break: 20m")));
    }
}
