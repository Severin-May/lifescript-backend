package com.lifescript.scheduler;

import java.time.Duration;

// The adjustable numbers in the scheduling rules, shared by all algorithms. Fixed defaults for now;
// later they can come from the plan itself (e.g. a "break: 15m" line in settings).
//   deadlineBuffer - a task should end this long before its deadline (soft: may end later if that's
//                    the only way to meet the deadline)
//   breakTime      - free time kept on both sides of every task, between tasks and around routines and events
//   startStep      - tasks start at a free range's own start or on this clock grid (15m: 09:00, 09:15, ...)
public record SchedulerSettings(Duration deadlineBuffer, Duration breakTime, Duration startStep) {

    public static final SchedulerSettings DEFAULTS =
            new SchedulerSettings(Duration.ofHours(2), Duration.ofMinutes(10), Duration.ofMinutes(15));
}
