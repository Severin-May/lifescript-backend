package com.lifescript;

import com.lifescript.compiler.CompileResult;
import com.lifescript.compiler.LifeScriptCompiler;
import com.lifescript.compiler.PlanModelBuilder;
import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Fix;
import com.lifescript.diagnostics.TextEdit;
import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.Plan;
import com.lifescript.planning.PlanningService;
import com.lifescript.planning.Schedulers;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestPlans {

    // Tests run on a fixed "today", so their plan dates don't fall into the past as time goes by.
    public static final LocalDate TODAY = LocalDate.of(2024, 1, 1);

    private TestPlans() {
    }

    public static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    public static LifeScriptCompiler compiler() {
        return new LifeScriptCompiler();
    }

    public static CompileResult compile(String source) {
        return compiler().compile(source, TODAY);
    }

    public static PlanningService planning() {
        return planning(new GreedyScheduler());
    }

    public static PlanningService planning(Scheduler scheduler) {
        return planning(new Schedulers(Map.of("test", scheduler), "test"));
    }

    public static PlanningService planning(Schedulers schedulers) {
        return new PlanningService(compiler(), schedulers, CLOCK);
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
        return format(compile(source).diagnostics());
    }

    public static List<String> syntaxErrors(String source) {
        return format(compiler().syntaxValidate(source));
    }

    private static List<String> format(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(Diagnostic::format).toList();
    }

    // Applies a quick fix the way the editor does; edits may span lines. Last edit first, so positions stay right.
    public static String applyFix(String source, Fix fix) {
        StringBuilder text = new StringBuilder(source);
        List<TextEdit> edits = fix.edits().stream()
                .sorted(Comparator.comparingInt(TextEdit::startLineNumber).thenComparingInt(TextEdit::startColumn).reversed())
                .toList();
        for (TextEdit edit : edits) {
            text.replace(offset(source, edit.startLineNumber(), edit.startColumn()),
                    offset(source, edit.endLineNumber(), edit.endColumn()), edit.text());
        }
        return text.toString();
    }

    private static int offset(String source, int line, int column) {
        int offset = 0;
        for (int i = 1; i < line; i++) {
            int lineEnd = source.indexOf('\n', offset);
            if (lineEnd < 0) {
                return source.length();
            }
            offset = lineEnd + 1;
        }
        return Math.min(offset + column - 1, source.length());
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
