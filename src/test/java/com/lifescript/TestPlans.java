package com.lifescript;

import com.lifescript.compiler.Compiler;
import com.lifescript.compiler.PlanModelBuilder;
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

// Shared test helpers for turning LifeScript text into a Plan.
public final class TestPlans {

    private TestPlans() {
    }

    // Loads a LifeScript file from src/test/resources/lifescript/.
    public static String load(String name) throws IOException {
        try (InputStream in = TestPlans.class.getResourceAsStream("/lifescript/" + name)) {
            if (in == null) throw new IOException("Test resource not found: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Builds a Plan, first asserting the source is valid (the builder assumes valid input).
    public static Plan build(String source) {
        List<String> errors = new Compiler().fullCompile(source);
        assertTrue(errors.isEmpty(), () -> "Source should be valid but got: " + errors);

        LifeScriptParser parser = new LifeScriptParser(
                new CommonTokenStream(new LifeScriptLexer(CharStreams.fromString(source))));
        PlanModelBuilder builder = new PlanModelBuilder();
        builder.visit(parser.plan());
        return builder.getPlan();
    }
}
