package com.lifescript.scheduler.csp;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Choco loads and solves the kind of problem the CSP scheduler builds: optional tasks with start values
// from a list, no overlap between placed tasks, and the most valuable set placed.
class ChocoSmokeTest {

    @Test
    void threeOneHourTasks_twoFreeHours_theTwoMostValuableArePlaced() {
        Model model = new Model("smoke");
        int[] starts = {0, 60};          // two free hours, in minutes
        int duration = 60;
        int[] weights = {1, 4, 2};       // low, high, medium priority

        IntVar[] start = new IntVar[3];
        BoolVar[] placed = new BoolVar[3];
        for (int i = 0; i < 3; i++) {
            start[i] = model.intVar("start" + i, starts);
            placed[i] = model.boolVar("placed" + i);
        }
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                // Both placed -> one ends before the other starts.
                model.or(placed[i].not(), placed[j].not(),
                        model.arithm(start[i], "+", model.intVar(duration), "<=", start[j]).reify(),
                        model.arithm(start[j], "+", model.intVar(duration), "<=", start[i]).reify()).post();
            }
        }
        IntVar value = model.intVar("value", 0, 7);
        model.scalar(placed, weights, "=", value).post();
        model.setObjective(Model.MAXIMIZE, value);

        Solver solver = model.getSolver();
        Solution best = new Solution(model);
        while (solver.solve()) {
            best.record();
        }

        assertEquals(6, best.getIntVal(value));
        assertEquals(0, best.getIntVal(placed[0]));
        assertTrue(best.getIntVal(start[1]) != best.getIntVal(start[2]));
    }
}
