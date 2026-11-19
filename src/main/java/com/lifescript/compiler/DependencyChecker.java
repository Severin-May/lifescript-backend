package com.lifescript.compiler;

import com.lifescript.diagnostics.Fix;
import com.lifescript.grammar.LifeScriptParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// The rules between tasks: unknown, duplicate or repeating dependencies, and dependency cycles.
class DependencyChecker {

    private enum Visit { ON_PATH, DONE }

    private final DiagnosticCollector diagnostics;
    private final Set<String> taskNames = new HashSet<>();
    private final Set<String> repeatingTasks = new HashSet<>();
    private final Map<String, List<String>> dependencyGraph = new HashMap<>();
    private final Map<String, ParserRuleContext> dependencyLines = new HashMap<>();

    // tasks: without duplicates, so each name is defined once.
    DependencyChecker(DiagnosticCollector diagnostics, List<LifeScriptParser.TaskContext> tasks) {
        this.diagnostics = diagnostics;
        for (LifeScriptParser.TaskContext task : tasks) {
            String taskName = task.IDENTIFIER().getText();
            taskNames.add(taskName);
            if (isRepeating(task)) {
                repeatingTasks.add(taskName);
            }
        }
    }

    private static boolean isRepeating(LifeScriptParser.TaskContext task) {
        return task.taskProperty().stream().anyMatch(p -> p.repeats() != null);
    }

    // Called per task in file order, so its messages come out next to the task's other messages.
    void checkTask(LifeScriptParser.TaskContext task) {
        String taskName = task.IDENTIFIER().getText();
        List<String> deps = new ArrayList<>();

        for (LifeScriptParser.TaskPropertyContext prop : task.taskProperty()) {
            if (prop.taskDependencies() == null) {
                continue;
            }
            dependencyLines.put(taskName, prop);
            int line = prop.getStart().getLine();
            List<Token> items = prop.taskDependencies().IDENTIFIER().stream().map(TerminalNode::getSymbol).toList();
            Set<String> seenDeps = new HashSet<>();
            for (int i = 0; i < items.size(); i++) {
                Token dep = items.get(i);
                if (!seenDeps.add(dep.getText())) {
                    diagnostics.error(prop, String.format("Duplicate dependency '%s'", dep.getText()),
                            List.of(Fixes.removeListItem(items, i, String.format("Remove the second '%s'", dep.getText()))));
                    continue;
                }
                if (!taskNames.contains(dep.getText())) {
                    reportUnknown(prop, taskName, dep);
                }
                if (repeatingTasks.contains(dep.getText())) {
                    diagnostics.error(prop, String.format("Task '%s' can't depend on repeating task '%s'", taskName, dep.getText()),
                            List.of(removeDependency(items, i, line)));
                }
                deps.add(dep.getText());
            }
            if (repeatingTasks.contains(taskName)) {
                diagnostics.error(prop, String.format("Repeating task '%s' can't have dependencies", taskName),
                        List.of(Fixes.removeLine(line, "Remove the dependencies")));
            }
        }
        dependencyGraph.put(taskName, deps);
    }

    // Close in spelling or only in case, e.g. 'reserch' or 'Research' for 'research'; never the task itself.
    private void reportUnknown(ParserRuleContext prop, String taskName, Token dep) {
        Optional<String> suggestion = Spelling.closest(dep.getText(),
                taskNames.stream().filter(name -> !name.equals(taskName)).toList());
        String message = String.format("Task '%s' depends on '%s', which doesn't exist%s", taskName, dep.getText(),
                suggestion.map(name -> String.format(" (did you mean '%s'?)", name)).orElse(""));
        diagnostics.error(prop, message, suggestion.map(name -> List.of(Fixes.replace(dep, dep, name))).orElse(List.of()));
    }

    // The only dependency takes the whole line with it: "dependencies:" alone isn't valid.
    private static Fix removeDependency(List<Token> items, int index, int line) {
        String title = String.format("Remove '%s' from the dependencies", items.get(index).getText());
        return items.size() == 1 ? Fixes.removeLine(line, title) : Fixes.removeListItem(items, index, title);
    }

    void checkCycles() {
        Map<String, Visit> visits = new HashMap<>();
        for (String taskName : taskNames) {
            if (!visits.containsKey(taskName)) {
                walkFrom(taskName, visits);
            }
        }
    }

    // Depth-first with an explicit stack rather than recursion, so a long chain of dependencies can't overflow.
    // path holds the tasks being walked; pending holds, for each of them, the dependencies still to visit.
    // A task is always finished (DONE), even after a cycle, so ON_PATH means it's in path.
    private void walkFrom(String start, Map<String, Visit> visits) {
        List<String> path = new ArrayList<>();
        Deque<Iterator<String>> pending = new ArrayDeque<>();
        enter(start, visits, path, pending);

        while (!pending.isEmpty()) {
            Iterator<String> deps = pending.peek();
            if (!deps.hasNext()) {
                pending.pop();
                visits.put(path.remove(path.size() - 1), Visit.DONE);
                continue;
            }
            String dep = deps.next();
            Visit visit = visits.get(dep);
            if (visit == Visit.ON_PATH) {
                reportCycle(path, dep);
            } else if (visit == null) {
                enter(dep, visits, path, pending);
            }
        }
    }

    private void enter(String task, Map<String, Visit> visits, List<String> path, Deque<Iterator<String>> pending) {
        visits.put(task, Visit.ON_PATH);
        path.add(task);
        pending.push(dependencyGraph.getOrDefault(task, List.of()).iterator());
    }

    // Reported at the dependencies line of the task that closes the cycle.
    private void reportCycle(List<String> path, String task) {
        List<String> cycle = path.subList(path.indexOf(task), path.size());
        String closingTask = path.get(path.size() - 1);
        diagnostics.error(dependencyLines.get(closingTask), String.format("Circular dependency detected: %s → %s",
                String.join(" → ", cycle), task));
    }
}
