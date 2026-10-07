package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.EnergyLevel;
import com.lifescript.model.Plan;
import com.lifescript.model.Priority;
import com.lifescript.model.Task;
import com.lifescript.model.TimeRange;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static java.time.DayOfWeek.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanModelBuilderTest {

    // Loads a LifeScript file from src/test/resources/lifescript/.
    private String load(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/lifescript/" + name)) {
            if (in == null) throw new IOException("Test resource not found: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Builds a Plan, first asserting the source is valid (the builder assumes valid input).
    private Plan build(String source) {
        List<String> errors = new Compiler().fullCompile(source);
        assertTrue(errors.isEmpty(), () -> "Source should be valid but got: " + errors);

        LifeScriptParser parser = new LifeScriptParser(
                new CommonTokenStream(new LifeScriptLexer(CharStreams.fromString(source))));
        PlanModelBuilder builder = new PlanModelBuilder();
        builder.visit(parser.plan());
        return builder.getPlan();
    }

    private static TimeRange range(String start, String end) {
        TimeRange range = new TimeRange();
        range.setStartTime(LocalTime.parse(start));
        range.setEndTime(LocalTime.parse(end));
        return range;
    }

    private static String planWithAvailability(String settings, String availability) {
        return """
                plan: p
                period: 2026-10-05 to 2026-10-11
                %s
                availability:
                %s

                tasks:
                task: a
                duration: 1h
                priority: high
                effort: high
                """.formatted(settings, availability);
    }

    // --- availability ---

    @Test
    void availability_fullPlan() throws IOException {
        Plan plan = build(load("fullPlan.ls"));
        // fullPlan.ls overrides morning (08:00-12:00) and afternoon (13:00-17:00); evening is default 18:00-22:00.

        assertEquals(List.of(range("08:00", "22:00")), plan.getAvailability().get(MONDAY));
        assertEquals(List.of(range("09:00", "12:00"), range("14:00", "18:00")), plan.getAvailability().get(TUESDAY));
        assertEquals(List.of(range("08:00", "12:00"), range("18:00", "22:00")), plan.getAvailability().get(WEDNESDAY));
        assertEquals(List.of(range("13:00", "17:00")), plan.getAvailability().get(THURSDAY));
        assertEquals(List.of(range("09:00", "17:00")), plan.getAvailability().get(FRIDAY));
    }

    @Test
    void availability_unlistedDays_areOff() throws IOException {
        Plan plan = build(load("fullPlan.ls"));

        assertEquals(7, plan.getAvailability().size());
        assertEquals(List.of(), plan.getAvailability().get(SATURDAY));
        assertEquals(List.of(), plan.getAvailability().get(SUNDAY));
    }

    @Test
    void availability_flexible_usesDefaultSettings() {
        Plan plan = build(planWithAvailability("", "monday: flexible"));

        // No settings section, so flexible = default morning start (06:00) to default evening end (22:00).
        assertEquals(List.of(range("06:00", "22:00")), plan.getAvailability().get(MONDAY));
    }

    @Test
    void availability_flexible_followsOverriddenSettings() {
        String settings = """
                settings:
                morning: 07:30-11:00
                evening: 19:00-00:00
                """;
        Plan plan = build(planWithAvailability(settings, "monday: flexible"));

        // flexible = overridden morning start (07:30) to overridden evening end (00:00).
        assertEquals(List.of(range("07:30", "00:00")), plan.getAvailability().get(MONDAY));
    }

    @Test
    void availability_rangesAreSortedByStartTime() {
        Plan plan = build(planWithAvailability("", "monday: 14:00-18:00, 09:00-12:00"));

        assertEquals(List.of(range("09:00", "12:00"), range("14:00", "18:00")), plan.getAvailability().get(MONDAY));
    }

    @Test
    void availability_namedPeriods_areSortedByStartTime() {
        Plan plan = build(planWithAvailability("", "monday: evening, morning"));

        assertEquals(List.of(range("06:00", "12:00"), range("18:00", "22:00")), plan.getAvailability().get(MONDAY));
    }

    // --- tasks ---

    @Test
    void tasks_fullPlan() throws IOException {
        Plan plan = build(load("fullPlan.ls"));
        List<Task> tasks = plan.getTasks();

        assertEquals(List.of("research", "write_report", "team_sync", "inbox_zero"),
                tasks.stream().map(Task::getName).toList());

        Task writeReport = tasks.get(1);
        assertEquals(Duration.ofMinutes(90), writeReport.getDuration());
        assertEquals(Priority.CRITICAL, writeReport.getPriority());
        assertEquals(EnergyLevel.HIGH, writeReport.getEffort());
        assertEquals(LocalDate.of(2026, 10, 9), writeReport.getDeadline());
        assertEquals(List.of("research"), writeReport.getDependencies());
        assertEquals("final draft for review", writeReport.getNote());
        assertNull(writeReport.getStart());
        assertEquals(List.of(), writeReport.getRepeatDays());

        Task teamSync = tasks.get(2);
        assertEquals(Priority.MEDIUM, teamSync.getPriority());
        assertEquals(LocalTime.of(10, 0), teamSync.getStart());
        assertEquals(List.of(MONDAY, WEDNESDAY), teamSync.getRepeatDays());

        Task inboxZero = tasks.get(3);
        assertEquals(List.<DayOfWeek>of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), inboxZero.getRepeatDays());
    }
}
