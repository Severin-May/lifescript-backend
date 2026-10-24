package com.lifescript.scheduler;

import com.lifescript.model.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-10-05");
    private static final LocalDate TUESDAY = LocalDate.parse("2026-10-06");

    private static ScheduleEntry entry(EntryType type, String name, String start, String end) {
        return new ScheduleEntry(type, name, new TimeRange(LocalTime.parse(start), LocalTime.parse(end)));
    }

    @Test
    void entriesAreSortedByStartTime_andDatesInOrder() {
        Map<LocalDate, List<ScheduleEntry>> days = new HashMap<>();
        days.put(TUESDAY, List.of(entry(EntryType.TASK, "b", "09:00", "10:00")));
        days.put(MONDAY, List.of(
                entry(EntryType.ROUTINE, "evening_walk", "19:00", "19:45"),
                entry(EntryType.TASK, "research", "09:00", "11:00"),
                entry(EntryType.ROUTINE, "morning_routine", "07:00", "07:35")));

        Schedule schedule = new Schedule(days, List.of(), List.of());

        assertEquals(List.of(MONDAY, TUESDAY), List.copyOf(schedule.days().keySet()));
        assertEquals(List.of("morning_routine", "research", "evening_walk"),
                schedule.entriesOn(MONDAY).stream().map(ScheduleEntry::name).toList());
    }

    @Test
    void entriesOn_dateWithoutEntries_isEmpty() {
        Schedule schedule = new Schedule(Map.of(), List.of(), List.of());

        assertTrue(schedule.entriesOn(MONDAY).isEmpty());
    }

    @Test
    void isACopy_laterChangesToTheInputDontLeakIn_andItCantBeModified() {
        List<ScheduleEntry> monday = new ArrayList<>(List.of(entry(EntryType.TASK, "a", "09:00", "10:00")));
        Map<LocalDate, List<ScheduleEntry>> days = new HashMap<>(Map.of(MONDAY, monday));
        List<UnscheduledTask> unscheduled = new ArrayList<>();

        Schedule schedule = new Schedule(days, unscheduled, List.of());
        monday.add(entry(EntryType.TASK, "b", "11:00", "12:00"));
        unscheduled.add(new UnscheduledTask("c", null, "no free slot long enough"));

        assertEquals(1, schedule.entriesOn(MONDAY).size());
        assertTrue(schedule.unscheduled().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> schedule.entriesOn(MONDAY).clear());
    }
}
