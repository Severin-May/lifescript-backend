package com.lifescript.scheduler.greedy;

import com.lifescript.model.TimeRange;
import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleEntry;
import com.lifescript.scheduler.ScheduleNote;
import com.lifescript.scheduler.UnscheduledTask;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.lifescript.TestPlans.build;
import static com.lifescript.TestPlans.load;
import static com.lifescript.scheduler.EntryType.EVENT;
import static com.lifescript.scheduler.EntryType.ROUTINE;
import static com.lifescript.scheduler.EntryType.TASK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedySchedulerTest {

    private static final LocalDate MON = LocalDate.parse("2026-10-05");

    private static Schedule schedule(String source) {
        return new GreedyScheduler().schedule(build(source));
    }

    private static ScheduleEntry entry(EntryType type, String name, String start, String end) {
        return new ScheduleEntry(type, name, new TimeRange(LocalTime.parse(start), LocalTime.parse(end)));
    }

    // 2026-10-05 is a Monday. No energy profile unless `extra` adds one, so all energy is moderate.
    private static String plan(String availability, String extra, String tasks) {
        return """
                plan: p
                period: 2026-10-05 to 2026-10-11

                availability:
                %s

                %s

                tasks:
                %s
                """.formatted(availability, extra, tasks);
    }

    @Test
    void fullPlan_everyTaskPlaced_byEnergyThenEarliest_withBreaks() throws IOException {
        // mon morning has only 08:00-09:50 before team_sync's break, too short for research (2h),
        // so it goes to tue 09:00-11:00; write_report must follow it: wed 08:00-09:30.
        // inbox_zero (low effort) takes its day's first low-energy slot, else the first moderate one.
        Schedule schedule = new GreedyScheduler().schedule(build(load("fullPlan.ls")));

        assertEquals(List.of(
                entry(ROUTINE, "morning_routine", "07:00", "07:35"),
                entry(ROUTINE, "team_sync", "10:00", "10:30"),
                entry(TASK, "inbox_zero", "18:00", "18:20"),
                entry(ROUTINE, "evening_walk", "19:00", "19:45")), schedule.entriesOn(MON));

        // tue: available 09-12 (high), 14-18 (moderate); inbox_zero has no low energy, so the first moderate slot
        assertEquals(List.of(
                entry(ROUTINE, "morning_routine", "07:00", "07:35"),
                entry(TASK, "research", "09:00", "11:00"),
                entry(TASK, "inbox_zero", "14:00", "14:20"),
                entry(ROUTINE, "evening_walk", "19:00", "19:45")), schedule.entriesOn(MON.plusDays(1)));
        assertEquals(List.of(
                entry(ROUTINE, "morning_routine", "07:00", "07:35"),
                entry(TASK, "write_report", "08:00", "09:30"),
                entry(ROUTINE, "team_sync", "10:00", "10:30"),
                entry(EVENT, "dentist", "14:00", "15:00"),
                entry(TASK, "inbox_zero", "18:00", "18:20"),
                entry(ROUTINE, "evening_walk", "19:00", "19:45")), schedule.entriesOn(MON.plusDays(2)));
        // thu: only the afternoon (moderate)
        assertTrue(schedule.entriesOn(MON.plusDays(3)).contains(entry(TASK, "inbox_zero", "13:00", "13:20")));
        // fri: available 09-12:50; 12-13 is uncovered (moderate), which beats high for a low-effort task
        assertTrue(schedule.entriesOn(MON.plusDays(4)).contains(entry(TASK, "inbox_zero", "12:00", "12:20")));
        // sat/sun: only the daily routine
        assertEquals(List.of(entry(ROUTINE, "morning_routine", "07:00", "07:35")), schedule.entriesOn(MON.plusDays(5)));

        assertEquals(List.of(), schedule.unscheduled());
        assertEquals(List.of(), schedule.notes());
    }

    @Test
    void deadlineBufferIsSoft_taskEndingCloseToDeadline_isPlacedWithNote() {
        // Due mon 11:00, so preferably done by 09:00, but availability only starts at 09:00.
        Schedule schedule = schedule(plan("monday: 09:00-12:00", "", """
                task: submit_form
                duration: 1h
                priority: high
                effort: moderate
                deadline: 2026-10-05 11:00
                """));

        assertEquals(List.of(entry(TASK, "submit_form", "09:00", "10:00")), schedule.entriesOn(MON));
        assertEquals(List.of(new ScheduleNote("submit_form", MON, "only fits by ending less than 2h before its deadline")),
                schedule.notes());
    }

    @Test
    void taskTooLongForAnyFreeSlot_isUnscheduledWithReason() {
        Schedule schedule = schedule(plan("monday: 09:00-11:00", "", """
                task: long_one
                duration: 3h
                priority: high
                effort: moderate

                task: due_one
                duration: 3h
                priority: high
                effort: moderate
                deadline: 2026-10-05 17:00
                """));

        assertEquals(List.of(
                new UnscheduledTask("due_one", null, "no free slot long enough ending by 2026-10-05 17:00"),
                new UnscheduledTask("long_one", null, "no free slot long enough in the period")), schedule.unscheduled());
    }

    @Test
    void dependencyNotScheduled_dependentIsUnscheduledToo() {
        Schedule schedule = schedule(plan("monday: 09:00-11:00", "", """
                task: big
                duration: 3h
                priority: high
                effort: moderate

                task: after_big
                duration: 30m
                priority: high
                effort: moderate
                dependencies: big
                """));

        assertEquals(List.of(
                new UnscheduledTask("big", null, "no free slot long enough in the period"),
                new UnscheduledTask("after_big", null, "depends on big, which could not be scheduled")),
                schedule.unscheduled());
    }

    @Test
    void dependentStartsOnlyAfterItsDependencyEnds() {
        // prepare (low effort) picks the low-energy hour 11-12, so present can't start before 12:00;
        // there is no available time after that. A known limit of greedy: it never moves prepare earlier.
        Schedule schedule = schedule(plan("monday: 09:00-12:00", """
                energy profile:
                default:
                09:00-11:00: high
                11:00-12:00: low
                """, """
                task: prepare
                duration: 1h
                priority: low
                effort: low

                task: present
                duration: 1h
                priority: high
                effort: high
                dependencies: prepare
                """));

        assertEquals(List.of(entry(TASK, "prepare", "11:00", "12:00")), schedule.entriesOn(MON));
        assertEquals(List.of(new UnscheduledTask("present", null, "no free slot long enough after prepare ends")),
                schedule.unscheduled());
    }

    @Test
    void repeatingCopyThatDoesntFitItsDay_isUnscheduledForThatDate() {
        // Only monday has availability, so the sunday copy has nowhere to go.
        Schedule schedule = schedule(plan("monday: 09:00-10:00", "", """
                task: stretch_break
                duration: 30m
                priority: low
                effort: low
                repeats: monday, sunday
                """));

        assertEquals(List.of(entry(TASK, "stretch_break", "09:00", "09:30")), schedule.entriesOn(MON));
        assertEquals(List.of(new UnscheduledTask("stretch_break", LocalDate.parse("2026-10-11"),
                "no free slot long enough on 2026-10-11")), schedule.unscheduled());
    }

    @Test
    void taskStartsRightAfterACommitmentAndBreak_offTheQuarterHour() {
        // warmup ends 09:33, plus the break: available from 09:43, the only start that fits before 11:33
        // (09:45 would end at 11:35).
        Schedule schedule = schedule(plan("monday: 09:00-11:33", """
                routines:
                routine: warmup
                time: 09:00
                repeats: monday
                activities:
                stretch: 33m
                """, """
                task: deep_work
                duration: 1h50m
                priority: high
                effort: moderate
                """));

        assertTrue(schedule.entriesOn(MON).contains(entry(TASK, "deep_work", "09:43", "11:33")),
                () -> "Got: " + schedule.entriesOn(MON));
    }

    @Test
    void tasksKeepABreakBetweenThem() {
        // Two 1h tasks in 09:00-11:10: the second can't start before 10:10 (10 minutes after the first).
        Schedule schedule = schedule(plan("monday: 09:00-11:10", "", """
                task: first
                duration: 1h
                priority: high
                effort: moderate

                task: second
                duration: 1h
                priority: low
                effort: moderate
                """));

        assertEquals(List.of(entry(TASK, "first", "09:00", "10:00"), entry(TASK, "second", "10:10", "11:10")),
                schedule.entriesOn(MON));
    }
}
