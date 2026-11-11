package com.lifescript.scheduler.csp;

import com.lifescript.model.TimeRange;
import com.lifescript.scheduler.EntryType;
import com.lifescript.scheduler.Placements;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.ScheduleEntry;
import com.lifescript.scheduler.ScheduleWarning;
import com.lifescript.scheduler.TaskOccurrence;
import com.lifescript.scheduler.UnscheduledTask;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Turns any CSP solver's solution into the same Schedule the greedy scheduler returns.
final class CspSchedules {

    private CspSchedules() {
    }

    static Schedule toSchedule(CspProblem problem, CspSolution solution) {
        Map<LocalDate, List<ScheduleEntry>> days = Placements.fixedCommitments(problem.plan());
        List<UnscheduledTask> unscheduled = new ArrayList<>();
        List<ScheduleWarning> warnings = new ArrayList<>();
        List<CspProblem.Item> items = problem.items();

        for (int i = 0; i < items.size(); i++) {
            CspProblem.Item item = items.get(i);
            TaskOccurrence occurrence = item.occurrence();
            if (!solution.isPlaced(i)) {
                unscheduled.add(new UnscheduledTask(occurrence.name(), occurrence.date(), reason(problem, solution, i),
                        occurrence.task().note()));
                continue;
            }
            int start = solution.starts()[i];
            List<ScheduleEntry> day = days.get(problem.dateOf(start));
            TimeRange time = problem.timeOf(start, item.duration());
            day.add(new ScheduleEntry(EntryType.TASK, occurrence.name(), time, occurrence.task().note()));
            Optional<TimeRange> rest = Placements.breakAfter(time, Duration.ofMinutes(item.rest()));
            rest.ifPresent(range -> day.add(new ScheduleEntry(EntryType.BREAK, occurrence.name(), range, null)));
            // Inside the deadline buffer: only when nothing better was possible, as the cost prefers ending before it.
            LocalDateTime end = time.endOn(problem.dateOf(start));
            if (occurrence.preferredEnd() != null && end.isAfter(occurrence.preferredEnd())) {
                warnings.add(new ScheduleWarning(occurrence.name(), problem.dateOf(start),
                        Placements.closeToDeadlineWarning(problem.plan(), occurrence, end)));
            }
        }
        return new Schedule(days, unscheduled, warnings);
    }

    // Like the greedy scheduler: a missing dependency first, then no possible start at all.
    private static String reason(CspProblem problem, CspSolution solution, int index) {
        CspProblem.Item item = problem.items().get(index);
        for (int dependency : item.dependencies()) {
            if (!solution.isPlaced(dependency)) {
                return Placements.dependencyFailedReason(problem.items().get(dependency).occurrence().name());
            }
        }
        if (item.starts().length == 0) {
            return Placements.noSlotReason(item.occurrence(), item.strictRest() * 60);
        }
        return "didn't fit together with the other tasks";
    }
}
