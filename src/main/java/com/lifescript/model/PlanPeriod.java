package com.lifescript.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// The plan's date range, from "period: 2026-10-05 to 2026-10-11". Both dates are included.
// The date counterpart of TimeRange (which is a range of times within one day).
public record PlanPeriod(LocalDate start, LocalDate end) {

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    // Every date in the period, in order, both ends included.
    public List<LocalDate> dates() {
        return start.datesUntil(end.plusDays(1)).toList();
    }

    // Weekdays that actually occur in the period (all 7 for a week or longer).
    public Set<DayOfWeek> weekdays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (LocalDate date = start; !date.isAfter(end) && days.size() < 7; date = date.plusDays(1)) {
            days.add(date.getDayOfWeek());
        }
        return days;
    }

    // Same format as written in LifeScript, e.g. "2026-10-05 to 2026-10-11".
    @Override
    public String toString() {
        return start + " to " + end;
    }
}
