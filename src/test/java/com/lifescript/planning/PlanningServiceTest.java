package com.lifescript.planning;

import com.lifescript.diagnostics.Stage;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static com.lifescript.TestPlans.load;
import static com.lifescript.TestPlans.planning;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningServiceTest {

    private static final Scheduler CRASHING = (plan, now) -> { throw new IllegalStateException("boom"); };

    @Test
    void validPlan_isCompiledAndScheduled() throws IOException {
        PlanningResult result = planning().schedule(load("fullPlan.ls"), null);

        assertEquals(List.of(), result.diagnostics());
        assertEquals("my_week", result.plan().name());
        assertEquals(7, result.schedule().days().size());
    }

    @Test
    void theRequestedAlgorithm_makesTheSchedule() throws IOException {
        PlanningService planning = planning(new Schedulers(
                Map.of("crashing", CRASHING, "greedy", new GreedyScheduler()), "crashing"));

        assertEquals(List.of(), planning.schedule(load("fullPlan.ls"), null, "greedy").diagnostics());
        assertEquals(Stage.INTERNAL, planning.schedule(load("fullPlan.ls"), null, null).diagnostics().get(0).stage());
    }

    @Test
    void anUnknownAlgorithm_isRejectedBeforeCompiling() {
        assertThrows(UnknownAlgorithmException.class, () -> planning().schedule("plan: p\n", null, "genetic"));
    }

    @Test
    void invalidPlan_isNotScheduled() {
        PlanningResult result = planning(CRASHING).schedule("plan: p\n", null);

        assertFalse(result.diagnostics().isEmpty());
        assertNull(result.plan());
        assertNull(result.schedule());
    }

    @Test
    void validate_compilesWithoutScheduling() throws IOException {
        assertEquals("my_week", planning(CRASHING).validate(load("fullPlan.ls"), null).plan().name());
    }

    @Test
    void schedulerCrash_isReportedAsInternalError() throws IOException {
        PlanningResult result = planning(CRASHING).schedule(load("fullPlan.ls"), null);

        assertEquals(Stage.INTERNAL, result.diagnostics().get(0).stage());
        assertNull(result.schedule());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void schedulerCrash_isLoggedWithoutThePlansContents(CapturedOutput output) throws IOException {
        planning(CRASHING).schedule(load("fullPlan.ls"), null);

        assertTrue(output.getOut().contains("Scheduler crashed"), output::getOut);
        assertTrue(output.getOut().contains("boom"), output::getOut);
        assertFalse(output.getOut().contains("bring insurance card"), output::getOut);
    }

    // The test clock is 2024-01-01 00:00 UTC: still 2023-12-31 19:00 in New York, already 09:00 in Tokyo.
    private static String planFrom(String start) {
        return """
                plan: p
                period: %s to 2024-01-06

                availability:
                monday: flexible

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """.formatted(start);
    }

    @Test
    void today_isTheUsersDate_notTheServers() {
        String plan = planFrom("2023-12-31");

        assertEquals(List.of("Period can't start in the past (today is 2024-01-01)"),
                planning().validate(plan, null).diagnostics().stream().map(d -> d.message()).toList());
        assertEquals(List.of(), planning().validate(plan, ZoneId.of("America/New_York")).diagnostics());
    }

    @Test
    void now_isTheUsersTime_soNothingIsPlacedInTheirPast() {
        String plan = planFrom("2024-01-01");
        LocalDate monday = LocalDate.of(2024, 1, 1);

        assertEquals(LocalTime.of(6, 0), firstStart(planning().schedule(plan, null), monday));
        assertEquals(LocalTime.of(9, 0), firstStart(planning().schedule(plan, ZoneId.of("Asia/Tokyo")), monday));
    }

    private static LocalTime firstStart(PlanningResult result, LocalDate date) {
        return result.schedule().entriesOn(date).get(0).timeRange().startTime();
    }
}
