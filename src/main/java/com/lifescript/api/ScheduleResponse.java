package com.lifescript.api;

import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleEntry;
import com.lifescript.scheduler.ScheduleNote;
import com.lifescript.scheduler.UnscheduledTask;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ScheduleResponse(Map<LocalDate, List<Entry>> days, List<UnscheduledTask> unscheduled,
                               List<ScheduleNote> notes) {

    // An end of "00:00" means midnight at the end of the day.
    public record Entry(EntryType type, String name, String start, String end) {

        static Entry from(ScheduleEntry entry) {
            return new Entry(entry.type(), entry.name(),
                    entry.timeRange().startTime().toString(), entry.timeRange().endTime().toString());
        }
    }

    public static ScheduleResponse from(Schedule schedule) {
        Map<LocalDate, List<Entry>> days = new LinkedHashMap<>();
        schedule.days().forEach((date, entries) -> days.put(date, entries.stream().map(Entry::from).toList()));
        return new ScheduleResponse(days, schedule.unscheduled(), schedule.notes());
    }
}
