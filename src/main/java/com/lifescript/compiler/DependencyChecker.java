package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The rules between tasks: unknown, duplicate or repeating dependencies, and dependency cycles.
class DependencyChecker {

    private static final String GRAY = "GRAY";
    private static final String BLACK = "BLACK";

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

    static boolean isRepeating(LifeScriptParser.TaskContext task) {
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
            Set<String> seenDeps = new HashSet<>();
            for (TerminalNode dep : prop.taskDependencies().IDENTIFIER()) {
                if (!seenDeps.add(dep.getText())) {
                    diagnostics.error(prop, String.format("Duplicate dependency '%s'", dep.getText()));
                    continue;
                }
                if (!taskNames.contains(dep.getText())) {
                    String hint = taskNames.stream().filter(name -> name.equalsIgnoreCase(dep.getText()))
                            .findFirst().map(name -> String.format(" (did you mean '%s'?)", name)).orElse("");
                    diagnostics.error(prop, String.format("Task '%s' depends on '%s', which doesn't exist%s",
                            taskName, dep.getText(), hint));
                }
                if (repeatingTasks.contains(dep.getText())) {
                    diagnostics.error(prop, String.format("Task '%s' can't depend on repeating task '%s'", taskName, dep.getText()));
                }
                deps.add(dep.getText());
            }
            if (repeatingTasks.contains(taskName)) {
                diagnostics.error(prop, String.format("Repeating task '%s' can't have dependencies", taskName));
            }
        }
        dependencyGraph.put(taskName, deps);
    }

    void checkCycles() {
        Map<String, String> colors = new HashMap<>();
        for (String taskName : taskNames) {
            if (!colors.containsKey(taskName)) {
                detectTaskCycle(taskName, colors, new ArrayList<>());
            }
        }
    }

    // GRAY = on the current path, BLACK = done. A task is always finished, even after a cycle,
    // so GRAY means on `path`. The cycle is reported at the closing task's dependencies line.
    private void detectTaskCycle(String task, Map<String, String> colors, List<String> path) {
        if (GRAY.equals(colors.get(task))) {
            int cycleStart = path.indexOf(task);
            List<String> cycle = path.subList(cycleStart, path.size());
            String closingTask = path.get(path.size() - 1);
            diagnostics.error(dependencyLines.get(closingTask), String.format("Circular dependency detected: %s → %s",
                    String.join(" → ", cycle), task));
            return;
        }
        if (BLACK.equals(colors.get(task))) return;

        colors.put(task, GRAY);
        path.add(task);

        for (String dep : dependencyGraph.getOrDefault(task, List.of())) {
            detectTaskCycle(dep, colors, path);
        }

        path.remove(path.size() - 1);
        colors.put(task, BLACK);
    }
}
