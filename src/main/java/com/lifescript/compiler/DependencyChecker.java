package com.lifescript.compiler;

import com.lifescript.diagnostics.Fix;
import com.lifescript.grammar.LifeScriptParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
