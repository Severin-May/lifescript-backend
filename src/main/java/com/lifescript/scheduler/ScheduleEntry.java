package com.lifescript.scheduler;

import com.lifescript.model.TimeRange;

// One entry in the calendar, e.g. TASK "research" 09:00-11:00. The date is the day it is listed
// under in Schedule, so it isn't repeated here.
public record ScheduleEntry(EntryType type, String name, TimeRange timeRange) {
}
