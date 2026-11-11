package com.lifescript.scheduler.csp;

import com.lifescript.model.Plan;
import com.lifescript.scheduler.Schedule;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.SchedulerSettings;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.loop.lns.INeighborFactory;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.selectors.variables.InputOrder;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// Schedules all tasks at once with the Choco constraint solver: each task gets a start from its allowed starts
// or stays unplaced, placed tasks don't overlap (rest included), dependencies end first, and the placed
// priority weight is as large as possible.
public class ChocoScheduler implements Scheduler {

    // Fixed, so the same neighbourhoods are tried in the same order (results still depend on the time limit).
    private static final long SEED = 42;
    // How hard to try one neighbourhood before moving on to the next.
    private static final long FAILS_PER_NEIGHBOURHOOD = 200;

    private final SchedulerSettings settings;
    private final Duration timeLimit;

    public ChocoScheduler() {
        this(SchedulerSettings.DEFAULTS, Duration.ofSeconds(2));
    }

    public ChocoScheduler(SchedulerSettings settings, Duration timeLimit) {
        this.settings = settings;
        this.timeLimit = timeLimit;
    }

    @Override
    public Schedule schedule(Plan plan, LocalDateTime now) {
        CspProblem problem = CspProblem.of(plan, now, settings);
        return CspSchedules.toSchedule(problem, solve(problem));
    }

    public CspSolution solve(CspProblem problem) {
        return solve(problem, null);
    }

    // initialStarts: a schedule to start from (a start or NOT_PLACED per item), which then only gets better;
    // each start must be one of its item's allowed starts. null: start from nothing.
    public CspSolution solve(CspProblem problem, int[] initialStarts) {
        List<CspProblem.Item> items = problem.items();
        int n = items.size();
        Model model = new Model("lifescript");
        IntVar[] start = new IntVar[n];
        IntVar[] choice = new IntVar[n];
        IntVar[] cost = new IntVar[n];
        BoolVar[] placed = new BoolVar[n];
        for (int i = 0; i < n; i++) {
            CspProblem.Item item = items.get(i);
            if (item.starts().length == 0) {
                // Nowhere to go: fixed as unplaced; the other variables are placeholders.
                start[i] = model.intVar(0);
                choice[i] = model.intVar(0);
                cost[i] = model.intVar(0);
                placed[i] = model.boolVar(false);
                continue;
            }
            // choice picks one candidate; candidates are ordered cheapest first, so the search tries good starts first.
            Integer[] order = IntStream.range(0, item.starts().length).boxed()
                    .sorted(Comparator.comparingInt((Integer k) -> item.costs()[k])).toArray(Integer[]::new);
            int[] starts = Arrays.stream(order).mapToInt(k -> item.starts()[k]).toArray();
            int[] costs = Arrays.stream(order).mapToInt(k -> item.costs()[k]).toArray();
            choice[i] = model.intVar("choice_" + i, 0, starts.length - 1);
            start[i] = model.intVar("start_" + i, item.starts());
            cost[i] = model.intVar("cost_" + i, costs[0], costs[costs.length - 1]);
            model.element(start[i], starts, choice[i]).post();
            model.element(cost[i], costs, choice[i]).post();
            placed[i] = model.boolVar("placed_" + i);
        }

        // At most one task at a time; an unplaced task has height 0, so it takes no room.
        Task[] tasks = new Task[n];
        for (int i = 0; i < n; i++) {
            tasks[i] = model.taskVar(start[i], items.get(i).duration() + items.get(i).rest());
        }
        model.cumulative(tasks, placed, model.intVar(1)).post();

        for (int i = 0; i < n; i++) {
            for (int dependency : items.get(i).dependencies()) {
                model.arithm(placed[i], "<=", placed[dependency]).post();
                model.ifThen(placed[i], model.arithm(start[i], "-", start[dependency], ">=", items.get(dependency).duration()));
            }
        }

        int[] weights = items.stream().mapToInt(item -> Math.toIntExact(item.weight())).toArray();
        IntVar value = model.intVar("value", 0, Math.toIntExact(Arrays.stream(weights).asLongStream().sum()));
        model.scalar(placed, weights, "=", value).post();

        // An unplaced task costs nothing.
        IntVar[] placedCost = new IntVar[n];
        for (int i = 0; i < n; i++) {
            placedCost[i] = model.intVar("placed_cost_" + i, 0, cost[i].getUB());
            model.times(cost[i], placed[i], placedCost[i]).post();
        }
        IntVar totalCost = model.intVar("total_cost", 0, Arrays.stream(placedCost).mapToInt(IntVar::getUB).sum());
        model.sum(placedCost, "=", totalCost).post();

        Solver solver = model.getSolver();
        // Task by task, most valuable first: try to place it at its cheapest start; if no start works, it stays
        // unplaced. Deciding all "placed" first and the starts later finds out too late when things don't fit.
        Set<IntVar> placedVars = new HashSet<>(Arrays.asList(placed));
        IntVar[] decisions = IntStream.range(0, n).boxed()
                .sorted(Comparator.comparingInt((Integer i) -> weights[i]).reversed())
                .flatMap(i -> Stream.of(placed[i], choice[i])).toArray(IntVar[]::new);
        AbstractStrategy<IntVar> search = Search.intVarSearch(new InputOrder<>(model),
                variable -> placedVars.contains(variable) ? variable.getUB() : variable.getLB(), decisions);
        long deadline = System.nanoTime() + timeLimit.toNanos();

        // Round 1: as much priority as possible. Round 2: with that fixed, the lowest cost. Two rounds instead of
        // one weighted sum, because strict weights times costs would overflow Choco's int variables.
        // Round 1 gets at most half the time, so round 2 always has some left for energy and the other costs.
        Solution best = new Solution(model);
        boolean haveStart = initialStarts != null && loadStart(solver, model, search, placed, start, initialStarts, best);
        long firstRoundEnd = System.nanoTime() + timeLimit.toNanos() / 2;
        Round first = optimize(solver, model, Model.MAXIMIZE, value, search, decisions, firstRoundEnd, best, haveStart);
        if (!first.found()) {
            // Not even one solution in time (shouldn't happen: the search finds one quickly): place nothing.
            return new CspSolution(IntStream.range(0, n).map(i -> CspSolution.NOT_PLACED).toArray(), false, timeLimit);
        }
        int bestValue = best.getIntVal(value);
        solver.reset();
        model.arithm(value, "=", bestValue).post();
        Round second = optimize(solver, model, Model.MINIMIZE, totalCost, search, decisions, deadline, best, true);

        int[] starts = new int[n];
        for (int i = 0; i < n; i++) {
            starts[i] = best.getIntVal(placed[i]) == 1 ? best.getIntVal(start[i]) : CspSolution.NOT_PLACED;
        }
        return new CspSolution(starts, first.complete() && second.complete(),
                Duration.ofNanos(timeLimit.toNanos() - (deadline - System.nanoTime())));
    }

    // found: there is a solution in `best`; complete: the search finished, so it's proven best.
    private record Round(boolean found, boolean complete) {
    }

    // Records the given schedule as the first solution: fix every task to it, solve once, then free them again.
    private static boolean loadStart(Solver solver, Model model, AbstractStrategy<IntVar> search, BoolVar[] placed,
                                     IntVar[] start, int[] starts, Solution best) {
        List<Constraint> fixed = new ArrayList<>();
        for (int i = 0; i < starts.length; i++) {
            boolean isPlaced = starts[i] != CspSolution.NOT_PLACED;
            fixed.add(model.arithm(placed[i], "=", isPlaced ? 1 : 0));
            if (isPlaced) {
                fixed.add(model.arithm(start[i], "=", starts[i]));
            }
        }
        fixed.forEach(Constraint::post);
        solver.setSearch(search);
        solver.removeAllStopCriteria();
        boolean feasible = solver.solve();
        if (feasible) {
            best.record();
        }
        solver.reset();
        fixed.forEach(model::unpost);
        return feasible;
    }

    // A complete search first, for a quarter of the round: small plans finish, so their result is proven best.
    // Otherwise Large Neighbourhood Search for the rest: free part of the best schedule, re-optimize that part,
    // keep improvements, repeat. It explores far more of a big plan than depth-first search, which in a short time
    // only ever changes its last decisions. With a solution already in `best`, only better ones are accepted.
    private static Round optimize(Solver solver, Model model, boolean direction, IntVar objective,
                                  AbstractStrategy<IntVar> search, IntVar[] decisions, long roundEnd, Solution best,
                                  boolean haveBest) {
        model.setObjective(direction, objective);
        if (haveBest) {
            atLeastAsGood(model, direction, objective, best);
        }
        solver.setDFS();
        solver.setSearch(search);
        haveBest |= solveUntil(solver, System.nanoTime() + (roundEnd - System.nanoTime()) / 4, best);
        boolean complete = !solver.isStopCriterionMet();
        if (complete || !haveBest) {
            return new Round(haveBest, complete);
        }

        solver.reset();
        atLeastAsGood(model, direction, objective, best);
        solver.setLNS(INeighborFactory.sequencer(
                        INeighborFactory.propagationGuided(SEED, decisions),
                        INeighborFactory.reversedPropagationGuided(SEED, decisions),
                        INeighborFactory.random(SEED, decisions)),
                new FailCounter(model, FAILS_PER_NEIGHBOURHOOD), best);
        solver.setSearch(search);
        solveUntil(solver, roundEnd, best);
        return new Round(true, false);
    }

    private static void atLeastAsGood(Model model, boolean direction, IntVar objective, Solution best) {
        model.arithm(objective, direction == Model.MAXIMIZE ? ">=" : "<=", best.getIntVal(objective)).post();
    }

    // Records every improving solution in `best` until the search ends or the time is up; true if any was found.
    private static boolean solveUntil(Solver solver, long end, Solution best) {
        solver.removeAllStopCriteria();
        solver.limitTime(Math.max(1, (end - System.nanoTime()) / 1_000_000));
        boolean found = false;
        while (solver.solve()) {
            best.record();
            found = true;
        }
        return found;
    }
}
