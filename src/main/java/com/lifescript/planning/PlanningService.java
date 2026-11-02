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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    // zone: the user's time zone, so "today" and "now" are theirs, not the server's; null = the server's.
    public CompileResult validate(String content, ZoneId zone) {
        return compiler.compile(content, LocalDate.now(clockIn(zone)));
    }

    public PlanningResult schedule(String content, ZoneId zone) {
        Clock userClock = clockIn(zone);
        CompileResult compiled = compiler.compile(content, LocalDate.now(userClock));
        if (compiled.plan() == null) {
            return PlanningResult.failed(compiled.diagnostics());
        }
        Plan plan = compiled.plan();
        try {
            return new PlanningResult(List.of(), plan, scheduler.schedule(plan, LocalDateTime.now(userClock)));
        } catch (RuntimeException | StackOverflowError e) {
            // As in the compiler: a crash is our bug, and the plan's text is personal data, so it isn't logged.
            log.error("Scheduler crashed on a plan with {} tasks, {} routines, {} events",
                    plan.tasks().size(), plan.routines().size(), plan.events().size(), e);
            return PlanningResult.failed(List.of(Diagnostic.withoutPosition(Stage.INTERNAL,
                    "Internal error while scheduling the plan (this is a bug in LifeScript, not in your plan)")));
        }
    }

    private Clock clockIn(ZoneId zone) {
        return zone == null ? clock : clock.withZone(zone);
    }
}
