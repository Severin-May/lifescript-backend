package com.lifescript.scheduler;

import com.lifescript.model.Plan;
import com.lifescript.model.TimeRange;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.lifescript.TestPlans.build;
import static org.junit.jupiter.api.Assertions.assertThrows;

// The checker must reject broken schedules, or passing it would prove nothing.
class ScheduleRulesTest {

    private static final LocalDate MON = LocalDate.parse("2026-10-05");
    private static final LocalDateTime NOW = MON.atStartOfDay();
    private static final Plan PLAN = build("""
            plan: p
            period: 2026-10-05 to 2026-10-05

            settings:
            break: 10m

            availability:
            monday: 09:00-12:00

            tasks:
            task: a
            duration: 1h
            priority: high
            effort: high

            task: b
            duration: 1h
            priority: high
            effort: high
            dependencies: a
            """);

    private static ScheduleEntry entry(EntryType type, String name, String start, String end) {
        return new ScheduleEntry(type, name, new TimeRange(LocalTime.parse(start), LocalTime.parse(end)), null);
    }

    private static Schedule monday(ScheduleEntry... entries) {
        return new Schedule(Map.of(MON, List.of(entries)), List.of(), List.of());
    }

    @Test
    void aCorrectSchedule_passes() {
        ScheduleRules.assertFollowsTheRules(PLAN, NOW, monday(
                entry(EntryType.TASK, "a", "09:00", "10:00"), entry(EntryType.BREAK, "a", "10:00", "10:10"),
                entry(EntryType.TASK, "b", "10:10", "11:10"), entry(EntryType.BREAK, "b", "11:10", "11:20")));
    }

    @Test
    void brokenSchedules_areRejected() {
        // b too close to a (no 10m rest)
        assertRejected(monday(entry(EntryType.TASK, "a", "09:00", "10:00"), entry(EntryType.BREAK, "a", "10:00", "10:10"),
                entry(EntryType.TASK, "b", "10:00", "11:00"), entry(EntryType.BREAK, "b", "11:00", "11:10")));
        // outside the free time
        assertRejected(monday(entry(EntryType.TASK, "a", "09:00", "10:00"), entry(EntryType.BREAK, "a", "10:00", "10:10"),
                entry(EntryType.TASK, "b", "11:30", "12:30"), entry(EntryType.BREAK, "b", "12:30", "12:40")));
        // b before its dependency a
        assertRejected(monday(entry(EntryType.TASK, "b", "09:00", "10:00"), entry(EntryType.BREAK, "b", "10:00", "10:10"),
                entry(EntryType.TASK, "a", "10:10", "11:10"), entry(EntryType.BREAK, "a", "11:10", "11:20")));
        // a break missing
        assertRejected(monday(entry(EntryType.TASK, "a", "09:00", "10:00"),
                entry(EntryType.TASK, "b", "10:10", "11:10"), entry(EntryType.BREAK, "b", "11:10", "11:20")));
        // b neither placed nor listed as unscheduled
        assertRejected(monday(entry(EntryType.TASK, "a", "09:00", "10:00"), entry(EntryType.BREAK, "a", "10:00", "10:10")));
    }

    private static void assertRejected(Schedule schedule) {
        assertThrows(AssertionFailedError.class, () -> ScheduleRules.assertFollowsTheRules(PLAN, NOW, schedule));
    }
}
