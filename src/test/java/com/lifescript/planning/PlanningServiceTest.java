package com.lifescript.planning;

import com.lifescript.diagnostics.Stage;
import com.lifescript.scheduler.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.util.List;

import static com.lifescript.TestPlans.load;
import static com.lifescript.TestPlans.planning;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningServiceTest {

    private static final Scheduler CRASHING = (plan, now) -> { throw new IllegalStateException("boom"); };

    @Test
    void validPlan_isCompiledAndScheduled() throws IOException {
        PlanningResult result = planning().schedule(load("fullPlan.ls"));

        assertEquals(List.of(), result.diagnostics());
        assertEquals("my_week", result.plan().name());
        assertEquals(7, result.schedule().days().size());
    }

    @Test
    void invalidPlan_isNotScheduled() {
        PlanningResult result = planning(CRASHING).schedule("plan: p\n");

        assertFalse(result.diagnostics().isEmpty());
        assertNull(result.plan());
        assertNull(result.schedule());
    }

    @Test
    void validate_compilesWithoutScheduling() throws IOException {
        assertEquals("my_week", planning(CRASHING).validate(load("fullPlan.ls")).plan().name());
    }

    @Test
    void schedulerCrash_isReportedAsInternalError() throws IOException {
        PlanningResult result = planning(CRASHING).schedule(load("fullPlan.ls"));

        assertEquals(Stage.INTERNAL, result.diagnostics().get(0).stage());
        assertNull(result.schedule());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void schedulerCrash_isLoggedWithoutThePlansContents(CapturedOutput output) throws IOException {
        planning(CRASHING).schedule(load("fullPlan.ls"));

        assertTrue(output.getOut().contains("Scheduler crashed"), output::getOut);
        assertTrue(output.getOut().contains("boom"), output::getOut);
        assertFalse(output.getOut().contains("bring insurance card"), output::getOut);
    }
}
