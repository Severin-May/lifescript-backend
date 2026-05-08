package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;

import java.util.ArrayList;
import java.util.List;

public class SemanticValidator extends LifeScriptParserBaseVisitor<Void> {
    private final List<String> errors = new ArrayList<>();

    List<String> getErrors() {
        return this.errors;
    }

    boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    @Override
    public Void visitTask(LifeScriptParser.TaskContext ctx) {
        String taskName = ctx.STRING().getText();

        boolean hasDuration = false;
        boolean hasPriority = false;
        boolean hasEffort = false;

        for(LifeScriptParser.TaskPropertyContext prop : ctx.taskProperty()) {
            if (prop.taskDuration() != null) hasDuration = true;
            if (prop.taskPriority() != null) hasPriority = true;
            if (prop.taskEffort() != null) hasEffort = true;
        }

        if (!hasDuration) {
            errors.add("Task " + taskName + " is missing mandatory property: duration");
        }
        if (!hasPriority) {
            errors.add("Task " + taskName + " is missing mandatory property: priority");
        }
        if (!hasEffort) {
            errors.add("Task " + taskName + " is missing mandatory property: effort");
        }

        return visitChildren(ctx);
    }
}
