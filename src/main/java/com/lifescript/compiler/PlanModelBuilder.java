package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.grammar.LifeScriptParserBaseVisitor;
import com.lifescript.model.Plan;

import java.time.LocalDate;

public class PlanModelBuilder extends LifeScriptParserBaseVisitor<Void> {
    private final Plan plan = new Plan();

    public Plan getPlan() {
        return plan;
    }

    @Override
    public Void visitPlan(LifeScriptParser.PlanContext ctx) {
        plan.setName(ctx.STRING().getText().replace("\"", ""));
        return visitChildren(ctx);
    }

    @Override
    public Void visitPeriod(LifeScriptParser.PeriodContext ctx) {
        plan.setStartDate(LocalDate.parse(ctx.DATE(0).getText()));
        plan.setEndDate(LocalDate.parse(ctx.DATE(1).getText()));
        return visitChildren(ctx);
    }
}
