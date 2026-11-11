package com.lifescript.scheduler.csp;

import java.time.Duration;

// starts[i]: where item i starts (minutes, see CspProblem), or NOT_PLACED.
// optimal: the solver proved no better solution exists (false when it stopped at its time limit).
public record CspSolution(int[] starts, boolean optimal, Duration solveTime) {

    public static final int NOT_PLACED = -1;

    public boolean isPlaced(int item) {
        return starts[item] != NOT_PLACED;
    }
}
