package com.lifescript.api.compiling;

import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleEntry;
import com.lifescript.scheduler.ScheduleWarning;
import com.lifescript.scheduler.UnscheduledTask;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ScheduleResponse(Map<LocalDate, List<Entry>> days, List<UnscheduledTask> unscheduled,
                               List<ScheduleWarning> warnings) {

    // An end of "00:00" means midnight at the end of the day.
    public record Entry(EntryType type, String name, String start, String end, String note) {

        static Entry from(ScheduleEntry entry) {
            return new Entry(entry.type(), entry.name(),
                    entry.timeRange().startTime().toString(), entry.timeRange().endTime().toString(),
                    entry.note());
        }
    }

    public static ScheduleResponse from(Schedule schedule) {
        Map<LocalDate, List<Entry>> days = new LinkedHashMap<>();
        schedule.days().forEach((date, entries) -> days.put(date, entries.stream().map(Entry::from).toList()));
        return new ScheduleResponse(days, schedule.unscheduled(), schedule.warnings());
    }
}
