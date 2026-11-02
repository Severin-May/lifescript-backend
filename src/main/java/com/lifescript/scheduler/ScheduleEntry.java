package com.lifescript.scheduler;

import com.lifescript.model.TimeRange;

// No date: it's the day key in Schedule. note: the plan's note, null if none.
public record ScheduleEntry(EntryType type, String name, TimeRange timeRange, String note) {
}
