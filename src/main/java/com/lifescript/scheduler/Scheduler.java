package com.lifescript.scheduler;

import com.lifescript.model.Plan;

public interface Scheduler {

    Schedule schedule(Plan plan);
}
