package com.lifescript.scheduler;

// How good a time slot is for a task's effort (see EnergyMatch.fit):
//   distance        - between the task's effort and the energy level it gets there: 0, 1 or 2
//   matchingSeconds - how much of the time is at exactly the task's effort level
public record EnergyFit(int distance, int matchingSeconds) {

    // Smaller distance wins; with equal distance, more matching time wins. Fully equal fits are
    // neither better nor worse, so the caller's own tie-break (earliest) decides.
    public boolean isBetterThan(EnergyFit other) {
        if (distance != other.distance) {
            return distance < other.distance;
        }
        return matchingSeconds > other.matchingSeconds;
    }
}
