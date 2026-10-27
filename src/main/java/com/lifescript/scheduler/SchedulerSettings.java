package com.lifescript.scheduler;

import java.time.Duration;

// deadlineBuffer is soft: a task may end later if that's the only way to meet its deadline.
public record SchedulerSettings(Duration deadlineBuffer, Duration breakTime, Duration startStep) {

    public static final SchedulerSettings DEFAULTS =
            new SchedulerSettings(Duration.ofHours(2), Duration.ofMinutes(10), Duration.ofMinutes(15));
}
