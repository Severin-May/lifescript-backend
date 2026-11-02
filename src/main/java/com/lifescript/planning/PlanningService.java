package com.lifescript.planning;

import com.lifescript.compiler.CompileResult;
import com.lifescript.compiler.LifeScriptCompiler;
import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Stage;
import com.lifescript.model.Plan;
import com.lifescript.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// Holds no per-request state: one instance serves all requests, so results are returned, never stored.
@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private final LifeScriptCompiler compiler;
    private final Scheduler scheduler;
    private final Clock clock;

    public PlanningService(LifeScriptCompiler compiler, Scheduler scheduler, Clock clock) {
        this.compiler = compiler;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    public List<Diagnostic> checkSyntax(String content) {
        return compiler.syntaxValidate(content);
    }

    public CompileResult validate(String content) {
        return compiler.compile(content);
    }

    public PlanningResult schedule(String content) {
        CompileResult compiled = compiler.compile(content);
        if (compiled.plan() == null) {
            return PlanningResult.failed(compiled.diagnostics());
        }
        Plan plan = compiled.plan();
        try {
            return new PlanningResult(List.of(), plan, scheduler.schedule(plan, LocalDateTime.now(clock)));
        } catch (RuntimeException | StackOverflowError e) {
            // As in the compiler: a crash is our bug, and the plan's text is personal data, so it isn't logged.
            log.error("Scheduler crashed on a plan with {} tasks, {} routines, {} events",
                    plan.tasks().size(), plan.routines().size(), plan.events().size(), e);
            return PlanningResult.failed(List.of(Diagnostic.withoutPosition(Stage.INTERNAL,
                    "Internal error while scheduling the plan (this is a bug in LifeScript, not in your plan)")));
        }
    }
}
