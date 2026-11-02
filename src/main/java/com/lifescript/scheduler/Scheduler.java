package com.lifescript.scheduler;

import com.lifescript.model.Plan;

import java.time.LocalDateTime;

public interface Scheduler {

    // Nothing is placed before `now`.
    Schedule schedule(Plan plan, LocalDateTime now);
}
