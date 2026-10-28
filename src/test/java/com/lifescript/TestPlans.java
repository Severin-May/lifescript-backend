package com.lifescript;

import com.lifescript.compiler.LifeScriptCompiler;
import com.lifescript.compiler.PlanModelBuilder;
import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.Plan;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestPlans {

    // Tests run on a fixed "today", so their plan dates don't fall into the past as time goes by.
    public static final LocalDate TODAY = LocalDate.of(2024, 1, 1);

    private TestPlans() {
    }

    public static LifeScriptCompiler compiler() {
        return compiler(new GreedyScheduler());
    }

    public static LifeScriptCompiler compiler(Scheduler scheduler) {
        return new LifeScriptCompiler(scheduler, Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }

    // From src/test/resources/lifescript/.
    public static String load(String name) throws IOException {
        try (InputStream in = TestPlans.class.getResourceAsStream("/lifescript/" + name)) {
            if (in == null) throw new IOException("Test resource not found: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // e.g. "Line 9: Duration '0m' must be greater than zero"
    public static List<String> errors(String source) {
        return format(compiler().compile(source).diagnostics());
    }

    public static List<String> syntaxErrors(String source) {
        return format(compiler().syntaxValidate(source));
    }

    private static List<String> format(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(Diagnostic::format).toList();
    }

    // Asserts the source is valid first: the builder assumes valid input.
    public static Plan build(String source) {
        List<String> errors = errors(source);
        assertTrue(errors.isEmpty(), () -> "Source should be valid but got: " + errors);

        LifeScriptParser parser = new LifeScriptParser(
                new CommonTokenStream(new LifeScriptLexer(CharStreams.fromString(source))));
        return new PlanModelBuilder().build(parser.plan());
    }
}
