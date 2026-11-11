package com.lifescript.scheduler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifescript.TestPlans.errors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GeneratedPlansTest {

    @Test
    void generatedWeeks_areValidLifeScript() {
        for (int size : List.of(10, 20, 40, 60)) {
            for (long seed = 1; seed <= 5; seed++) {
                String plan = GeneratedPlans.week(size, seed);
                assertEquals(List.of(), errors(plan), plan);
            }
        }
    }

    @Test
    void theSameSeed_givesTheSamePlan() {
        assertEquals(GeneratedPlans.week(20, 42), GeneratedPlans.week(20, 42));
        assertNotEquals(GeneratedPlans.week(20, 42), GeneratedPlans.week(20, 43));
    }
}
