package com.lifescript.scheduler;

import com.lifescript.model.Plan;

// What every scheduling algorithm (greedy now, CSP later) promises: given a valid Plan,
// return a Schedule. All algorithms produce the same output types and follow the same rules,
// so the API and frontend don't depend on which one ran.
public interface Scheduler {

    Schedule schedule(Plan plan);
}
