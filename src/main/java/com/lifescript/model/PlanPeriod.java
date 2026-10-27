package com.lifescript.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Both dates are included.
public record PlanPeriod(LocalDate start, LocalDate end) {

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public List<LocalDate> dates() {
        return start.datesUntil(end.plusDays(1)).toList();
    }

    public Set<DayOfWeek> weekdays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (LocalDate date = start; !date.isAfter(end) && days.size() < 7; date = date.plusDays(1)) {
            days.add(date.getDayOfWeek());
        }
        return days;
    }

    @Override
    public String toString() {
        return start + " to " + end;
    }
}
