package com.lifescript;

import com.lifescript.compiler.LifeScriptCompiler;
import com.lifescript.compiler.PlanModelBuilder;
import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.Plan;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestPlans {

    private TestPlans() {
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
        return format(new LifeScriptCompiler().compile(source).diagnostics());
    }

    public static List<String> syntaxErrors(String source) {
        return format(new LifeScriptCompiler().syntaxValidate(source));
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
        PlanModelBuilder builder = new PlanModelBuilder();
        builder.visit(parser.plan());
        return builder.getPlan();
    }
}
