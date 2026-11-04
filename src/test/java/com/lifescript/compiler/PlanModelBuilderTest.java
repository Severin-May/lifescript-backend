package com.lifescript.compiler;

import com.lifescript.diagnostics.SourceLocation;
import com.lifescript.model.Activity;
import com.lifescript.model.EnergyEntry;
import com.lifescript.model.EnergyLevel;
import com.lifescript.model.Event;
import com.lifescript.model.Plan;
import com.lifescript.model.PlanPeriod;
import com.lifescript.model.Priority;
import com.lifescript.model.Routine;
import com.lifescript.model.Task;
import com.lifescript.model.TimeRange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.load;
import static java.time.DayOfWeek.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanModelBuilderTest {

    private static TimeRange range(String start, String end) {
        return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
    }

    private static String planWithAvailability(String extraSections, String availability) {
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
                """.formatted(extraSections, availability);
    }

    @Test
    void nameAndPeriod_fullPlan() throws IOException {
        Plan plan = build(load("fullPlan.ls"));

        assertEquals("my_week", plan.name());
        assertEquals(new PlanPeriod(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-11")), plan.period());
    }

    @Test
    void availability_fullPlan() throws IOException {
        Plan plan = build(load("fullPlan.ls"));
        // fullPlan.ls overrides morning (08:00-12:00) and afternoon (13:00-17:00); evening is default 18:00-22:00.

        assertEquals(List.of(range("08:00", "22:00")), plan.availability().get(MONDAY));
        assertEquals(List.of(range("09:00", "12:00"), range("14:00", "18:00")), plan.availability().get(TUESDAY));
        assertEquals(List.of(range("08:00", "12:00"), range("18:00", "22:00")), plan.availability().get(WEDNESDAY));
        assertEquals(List.of(range("13:00", "17:00")), plan.availability().get(THURSDAY));
        assertEquals(List.of(range("09:00", "17:00")), plan.availability().get(FRIDAY));
    }

    @Test
    void availability_unlistedDays_areOff() throws IOException {
        Plan plan = build(load("fullPlan.ls"));

        assertEquals(7, plan.availability().size());
        assertEquals(List.of(), plan.availability().get(SATURDAY));
        assertEquals(List.of(), plan.availability().get(SUNDAY));
    }

    @Test
    void availability_flexible_usesDefaultSettings() {
        Plan plan = build(planWithAvailability("", "monday: flexible"));

        // No settings section, so flexible = default morning start (06:00) to default evening end (22:00).
        assertEquals(List.of(range("06:00", "22:00")), plan.availability().get(MONDAY));
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
        assertEquals(List.of(range("07:30", "00:00")), plan.availability().get(MONDAY));
    }

    @Test
    void availability_rangesAreSortedByStartTime() {
        Plan plan = build(planWithAvailability("", "monday: 14:00-18:00, 09:00-12:00"));

        assertEquals(List.of(range("09:00", "12:00"), range("14:00", "18:00")), plan.availability().get(MONDAY));
    }

    @Test
    void availability_namedPeriods_areSortedByStartTime() {
        Plan plan = build(planWithAvailability("", "monday: evening, morning"));

        assertEquals(List.of(range("06:00", "12:00"), range("18:00", "22:00")), plan.availability().get(MONDAY));
    }

    @Test
    void energyProfile_fullPlan_builtInFileOrder() throws IOException {
        Plan plan = build(load("fullPlan.ls"));

        // Locations: fullPlan.ls lines 19-21 and 23, indented 4 spaces.
        assertEquals(List.of(
                new EnergyEntry(range("08:00", "12:00"), EnergyLevel.HIGH, new SourceLocation(20, 5, 18)),       // morning: high
                new EnergyEntry(range("13:00", "17:00"), EnergyLevel.MODERATE, new SourceLocation(21, 5, 24)),   // afternoon: moderate
                new EnergyEntry(range("18:00", "22:00"), EnergyLevel.LOW, new SourceLocation(22, 5, 17))),       // evening: low
                plan.defaultEnergyProfile());
        assertEquals(Map.of(FRIDAY, List.of(
                        new EnergyEntry(range("13:00", "17:00"), EnergyLevel.LOW, new SourceLocation(24, 5, 19)))), // afternoon: low
                plan.energyProfileOverrides());
    }

    @Test
    void energyAt_dayBlockMergesWithDefault() throws IOException {
        Plan plan = build(load("fullPlan.ls"));

        assertEquals(EnergyLevel.LOW, plan.energyAt(FRIDAY, LocalTime.parse("14:00")));       // friday override
        assertEquals(EnergyLevel.HIGH, plan.energyAt(FRIDAY, LocalTime.parse("09:00")));      // not overridden: default
        assertEquals(EnergyLevel.MODERATE, plan.energyAt(MONDAY, LocalTime.parse("14:00")));  // default afternoon
        assertEquals(EnergyLevel.LOW, plan.energyAt(MONDAY, LocalTime.parse("19:00")));       // default evening
    }

    @Test
    void energyAt_uncoveredTime_isModerate() throws IOException {
        Plan plan = build(load("fullPlan.ls"));

        assertEquals(EnergyLevel.MODERATE, plan.energyAt(MONDAY, LocalTime.parse("12:30"))); // gap between morning and afternoon
        assertEquals(EnergyLevel.MODERATE, plan.energyAt(MONDAY, LocalTime.parse("23:00"))); // after evening
    }

    @Test
    void energyAt_noEnergyProfile_isModerateEverywhere() {
        Plan plan = build(planWithAvailability("", "monday: flexible"));

        assertTrue(plan.defaultEnergyProfile().isEmpty());
        assertTrue(plan.energyProfileOverrides().isEmpty());
        assertEquals(EnergyLevel.MODERATE, plan.energyAt(MONDAY, LocalTime.parse("09:00")));
        assertEquals(EnergyLevel.MODERATE, plan.energyAt(SUNDAY, LocalTime.parse("21:00")));
    }

    @Test
    void energyAt_timeRangeEndingAtMidnight() {
        String energy = """
                energy profile:
                default:
                22:00-00:00: low
                """;
        Plan plan = build(planWithAvailability(energy, "monday: flexible"));

        assertEquals(EnergyLevel.LOW, plan.energyAt(MONDAY, LocalTime.parse("23:30")));
        assertEquals(EnergyLevel.MODERATE, plan.energyAt(MONDAY, LocalTime.parse("21:00")));
    }

    @Test
    void energyAt_dayBlockOnly_withoutDefault() {
        String energy = """
                energy profile:
                saturday:
                morning: low
                """;
        Plan plan = build(planWithAvailability(energy, "monday: flexible"));

        assertEquals(EnergyLevel.LOW, plan.energyAt(SATURDAY, LocalTime.parse("09:00")));
        assertEquals(EnergyLevel.MODERATE, plan.energyAt(MONDAY, LocalTime.parse("09:00")));
    }

    @Test
    void routines_fullPlan() throws IOException {
        Plan plan = build(load("fullPlan.ls"));
        List<Routine> routines = plan.routines();

        assertEquals(List.of("morning_routine", "evening_walk", "team_sync"),
                routines.stream().map(Routine::name).toList());

        Routine morning = routines.get(0);
        assertEquals(LocalTime.of(7, 0), morning.startTime());
        assertEquals(Duration.ofMinutes(35), morning.duration());     // stretch 15m + shower 20m
        assertEquals(range("07:00", "07:35"), morning.timeRange());
        assertEquals(List.of(DayOfWeek.values()), morning.repeatDays()); // daily
        assertEquals(List.of("stretch", "shower"),
                morning.activities().stream().map(Activity::name).toList());
        assertEquals(List.of(Duration.ofMinutes(15), Duration.ofMinutes(20)),
                morning.activities().stream().map(Activity::duration).toList());

        Routine walk = routines.get(1);
        assertEquals(range("19:00", "19:45"), walk.timeRange());      // start + walk 45m
        assertEquals(List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), walk.repeatDays()); // weekdays
        assertEquals(Duration.ofMinutes(45), walk.activities().get(0).duration());

        Routine teamSync = routines.get(2);
        assertEquals(range("10:00", "10:30"), teamSync.timeRange());
        assertEquals(List.of(MONDAY, WEDNESDAY), teamSync.repeatDays());
    }

    @Test
    void routines_repeatsOnDayList() {
        String routines = """
                routines:
                routine: weekly_review
                time: 17:00
                repeats: friday
                activities:
                plan_next_week: 1h
                tidy_desk: 30m
                """;
        Plan plan = build(planWithAvailability(routines, "monday: flexible"));
        Routine review = plan.routines().get(0);

        assertEquals(List.of(FRIDAY), review.repeatDays());
        assertEquals(Duration.ofMinutes(90), review.duration());       // 1h + 30m
        assertEquals(range("17:00", "18:30"), review.timeRange());
    }

    @Test
    void routines_endingExactlyAtMidnight() {
        String routines = """
                routines:
                routine: night_reading
                time: 23:00
                repeats: daily
                activities:
                read: 1h
                """;
        Plan plan = build(planWithAvailability(routines, "monday: flexible"));
        TimeRange range = plan.routines().get(0).timeRange();

        assertEquals(range("23:00", "00:00"), range);
        assertTrue(range.endsAtMidnight());
        assertTrue(range.isOrdered());
        assertEquals(Duration.ofHours(1), range.duration());
    }

    @Test
    void events_fullPlan() throws IOException {
        Plan plan = build(load("fullPlan.ls"));
        List<Event> events = plan.events();

        assertEquals(List.of("dentist", "team_offsite"), events.stream().map(Event::name).toList());

        Event dentist = events.get(0);
        assertEquals(LocalDate.of(2026, 10, 7), dentist.date());
        assertEquals(range("14:00", "15:00"), dentist.timeRange());
        assertEquals("bring insurance card", dentist.note());

        Event offsite = events.get(1);
        assertEquals(LocalDate.of(2026, 10, 9), offsite.date());
        assertEquals(range("13:00", "17:00"), offsite.timeRange());   // afternoon, overridden in fullPlan settings
        assertNull(offsite.note());
    }

    @Test
    void events_noSection_isEmpty() {
        Plan plan = build(planWithAvailability("", "monday: flexible"));

        assertEquals(List.of(), plan.events());
    }

    @Test
    void tasks_fullPlan() throws IOException {
        Plan plan = build(load("fullPlan.ls"));
        List<Task> tasks = plan.tasks();

        assertEquals(List.of("research", "write_report", "inbox_zero"),
                tasks.stream().map(Task::name).toList());

        Task writeReport = tasks.get(1);
        assertEquals(Duration.ofMinutes(90), writeReport.duration());
        assertEquals(Priority.CRITICAL, writeReport.priority());
        assertEquals(EnergyLevel.HIGH, writeReport.effort());
        assertEquals(LocalDateTime.of(2026, 10, 9, 17, 0), writeReport.deadline());
        assertEquals(List.of("research"), writeReport.dependencies());
        assertEquals("final draft for review", writeReport.note());
        assertEquals(List.of(), writeReport.repeatDays());

        Task inboxZero = tasks.get(2);
        assertEquals(Priority.LOW, inboxZero.priority());
        assertNull(inboxZero.deadline());
        assertEquals(List.<DayOfWeek>of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), inboxZero.repeatDays());
    }

    @Test
    void plan_isReadOnly_includingNestedLists() throws IOException {
        Plan plan = build(load("fullPlan.ls"));
        TimeRange range = new TimeRange(LocalTime.of(20, 0), LocalTime.of(21, 0));

        assertThrows(UnsupportedOperationException.class, () -> plan.tasks().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.availability().put(SUNDAY, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> plan.availability().get(MONDAY).add(range));
        assertThrows(UnsupportedOperationException.class, () -> plan.tasks().get(0).dependencies().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> plan.routines().get(0).activities().clear());
    }
}
