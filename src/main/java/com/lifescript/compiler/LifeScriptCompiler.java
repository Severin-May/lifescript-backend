package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Stage;
import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.Plan;
import org.antlr.v4.runtime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LifeScriptCompiler {

    private static final Logger log = LoggerFactory.getLogger(LifeScriptCompiler.class);

    // All problems in the plan, in the order found; empty means the plan is valid.
    public List<Diagnostic> compile(String content) {
        if (content == null || content.isBlank()) {
            return List.of(Diagnostic.withoutPosition(Stage.SYNTAX, "Plan is empty"));
        }

        // Safety net: a crash anywhere in the pipeline is a bug in the compiler, not in the user's plan.
        // Report it as an error instead of failing the whole request, and log the details for debugging.
        try {
            return runPipeline(content);
        } catch (RuntimeException e) {
            log.error("Compiler crashed on input:\n{}", content, e);
            return List.of(Diagnostic.withoutPosition(Stage.INTERNAL,
                    "Internal error while compiling the plan (this is a bug in LifeScript, not in your plan)"));
        }
    }

    // The same problems in the classic compiler form, e.g. "Line 9: Duration '0m' must be greater than zero".
    public List<String> fullCompile(String content) {
        return format(compile(content));
    }

    // Syntax only, for checking while the user types.
    public List<String> syntaxValidate(String content) {
        DiagnosticCollector collector = new DiagnosticCollector();
        parseWithSyntaxChecks(content, collector);
        return format(collector.list());
    }

    private List<Diagnostic> runPipeline(String content) {
        DiagnosticCollector collector = new DiagnosticCollector();
        LifeScriptParser.PlanContext tree = parseWithSyntaxChecks(content, collector);
        if (!collector.isEmpty()) {
            return collector.list();
        }

        SemanticValidator validator = new SemanticValidator();
        validator.visit(tree);
        if (!validator.getErrors().isEmpty()) {
            return withoutPositions(validator.getErrors());
        }

        PlanModelBuilder modelBuilder = new PlanModelBuilder();
        modelBuilder.visit(tree);
        Plan plan = modelBuilder.getPlan();

        return withoutPositions(new PlanModelValidator().validate(plan));
    }

    // Temporary, until the validators create diagnostics themselves: their messages already contain
    // "Line N:" where they have a line, so as position-less diagnostics they format exactly as before.
    private static List<Diagnostic> withoutPositions(List<String> errors) {
        return errors.stream().map(error -> Diagnostic.withoutPosition(Stage.VALIDATION, error)).toList();
    }

    private static List<String> format(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(Diagnostic::format).toList();
    }

    private LifeScriptParser.PlanContext parseWithSyntaxChecks(String content, DiagnosticCollector collector) {
        // Every line must end with a newline, but the last line of a file often doesn't
        // (e.g. text from a browser text box). An extra blank line is harmless.
        CharStream input = CharStreams.fromString(content.endsWith("\n") ? content : content + "\n");
        LifeScriptLexer lexer = new LifeScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LifeScriptParser parser = new LifeScriptParser(tokens);

        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        // The parser reports the token it couldn't use; the lexer has no token yet (offendingSymbol is null).
        BaseErrorListener syntaxErrorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                Token offending = offendingSymbol instanceof Token token ? token : null;
                collector.syntaxError(offending, line, charPositionInLine, "Syntax error - " + msg);
            }
        };

        lexer.addErrorListener(syntaxErrorListener);
        parser.addErrorListener(syntaxErrorListener);

        return parser.plan();
    }
}
