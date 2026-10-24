package com.lifescript.scheduler;

import com.lifescript.model.Plan;
import com.lifescript.model.TimeRange;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalTime;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.load;
import static com.lifescript.model.EnergyLevel.HIGH;
import static com.lifescript.model.EnergyLevel.LOW;
import static com.lifescript.model.EnergyLevel.MODERATE;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyMatchTest {

    // fullPlan.ls energy: default morning 08:00-12:00 high, afternoon 13:00-17:00 moderate,
    // evening 18:00-22:00 low; friday afternoon 13:00-17:00 low. Anything uncovered is moderate.
    private static Plan plan;

    @BeforeAll
    static void loadPlan() throws IOException {
        plan = build(load("fullPlan.ls"));
    }

    private static TimeRange range(String start, String end) {
        return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void levelDuring_singleLevel() {
        assertEquals(HIGH, EnergyMatch.levelDuring(plan, MONDAY, range("09:00", "11:00"), LOW));
    }

    @Test
    void levelDuring_levelCoveringMostOfTheTimeWins_whateverTheEffort() {
        // high 1h (11-12), moderate 1h30m (12-13 uncovered, 13-13:30 afternoon)
        assertEquals(MODERATE, EnergyMatch.levelDuring(plan, MONDAY, range("11:00", "13:30"), HIGH));
        assertEquals(MODERATE, EnergyMatch.levelDuring(plan, MONDAY, range("11:00", "13:30"), LOW));
        // high 2h, moderate 30m
        assertEquals(HIGH, EnergyMatch.levelDuring(plan, MONDAY, range("09:30", "12:30"), LOW));
    }

    @Test
    void levelDuring_tie_worseMatchForTheTaskWins() {
        // monday 11-13: high 1h, moderate 1h
        assertEquals(MODERATE, EnergyMatch.levelDuring(plan, MONDAY, range("11:00", "13:00"), HIGH));
        assertEquals(HIGH, EnergyMatch.levelDuring(plan, MONDAY, range("11:00", "13:00"), LOW));
        assertEquals(HIGH, EnergyMatch.levelDuring(plan, MONDAY, range("11:00", "13:00"), MODERATE));
        // friday 12-14: moderate 1h (12-13 uncovered), low 1h (friday afternoon)
        assertEquals(LOW, EnergyMatch.levelDuring(plan, FRIDAY, range("12:00", "14:00"), HIGH));
        assertEquals(MODERATE, EnergyMatch.levelDuring(plan, FRIDAY, range("12:00", "14:00"), LOW));
    }

    @Test
    void levelDuring_tieEquallyBadBothWays_lowerLevelWins() {
        // For a moderate task, high and low are both one step away. fullPlan.ls has no adjacent high and
        // low blocks, so this uses its own profile: 1h high then 1h low.
        Plan adjacent = build("""
                plan: p
                period: 2026-10-05 to 2026-10-11

                availability:
                monday: flexible

                energy profile:
                default:
                09:00-10:00: high
                10:00-11:00: low

                tasks:
                task: a
                duration: 1h
                priority: low
                effort: low
                """);
        assertEquals(LOW, EnergyMatch.levelDuring(adjacent, MONDAY, range("09:00", "11:00"), MODERATE));
    }

    @Test
    void fit_distanceOfMajorityLevel_andTimeAtExactlyTheEffortLevel() {
        // monday 11:00-13:30: high 1h, moderate 1h30m (majority moderate)
        assertEquals(new EnergyFit(0, 90 * 60), EnergyMatch.fit(plan, MONDAY, range("11:00", "13:30"), MODERATE));
        assertEquals(new EnergyFit(1, 60 * 60), EnergyMatch.fit(plan, MONDAY, range("11:00", "13:30"), HIGH));
        assertEquals(new EnergyFit(1, 0), EnergyMatch.fit(plan, MONDAY, range("11:00", "13:30"), LOW));
    }

    @Test
    void fit_isBetterThan_smallerDistanceFirst_thenMoreMatchingTime() {
        EnergyFit perfectMostly = new EnergyFit(0, 45 * 60);
        EnergyFit perfectFully = new EnergyFit(0, 60 * 60);
        EnergyFit offByOne = new EnergyFit(1, 60 * 60);

        assertTrue(perfectFully.isBetterThan(perfectMostly));
        assertTrue(perfectMostly.isBetterThan(offByOne));
        assertFalse(perfectFully.isBetterThan(perfectFully));   // equal: neither wins, caller keeps the earliest
    }

    @Test
    void levelDuring_endingAtMidnight() {
        // low 1h (21-22 evening), moderate 2h (22-24 uncovered)
        assertEquals(MODERATE, EnergyMatch.levelDuring(plan, MONDAY, range("21:00", "00:00"), HIGH));
    }

    @Test
    void distance_sameLevelIsZero_highVersusLowIsTwo_bothDirections() {
        assertEquals(0, EnergyMatch.distance(HIGH, HIGH));
        assertEquals(1, EnergyMatch.distance(HIGH, MODERATE));
        assertEquals(1, EnergyMatch.distance(LOW, MODERATE));
        assertEquals(2, EnergyMatch.distance(HIGH, LOW));
        assertEquals(2, EnergyMatch.distance(LOW, HIGH));
    }
}
