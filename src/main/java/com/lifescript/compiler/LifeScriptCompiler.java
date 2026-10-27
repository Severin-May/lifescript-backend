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
import java.util.function.Function;

public class LifeScriptCompiler {

    private static final Logger log = LoggerFactory.getLogger(LifeScriptCompiler.class);

    public List<Diagnostic> compile(String content) {
        return safely(content, this::runPipeline);
    }

    public List<Diagnostic> syntaxValidate(String content) {
        return safely(content, text -> {
            DiagnosticCollector collector = new DiagnosticCollector();
            parseWithSyntaxChecks(text, collector);
            return collector.list();
        });
    }

    // A crash is a compiler bug, not the user's: report it instead of failing the request.
    private List<Diagnostic> safely(String content, Function<String, List<Diagnostic>> check) {
        if (content == null || content.isBlank()) {
            return List.of(Diagnostic.withoutPosition(Stage.SYNTAX, "Plan is empty"));
        }
        try {
            return check.apply(content);
        } catch (RuntimeException e) {
            log.error("Compiler crashed on input:\n{}", content, e);
            return List.of(Diagnostic.withoutPosition(Stage.INTERNAL,
                    "Internal error while compiling the plan (this is a bug in LifeScript, not in your plan)"));
        }
    }

    private List<Diagnostic> runPipeline(String content) {
        DiagnosticCollector collector = new DiagnosticCollector();
        LifeScriptParser.PlanContext tree = parseWithSyntaxChecks(content, collector);
        if (!collector.isEmpty()) {
            return collector.list();
        }

        new SemanticValidator(collector).visit(tree);
        if (!collector.isEmpty()) {
            return collector.list();
        }

        PlanModelBuilder modelBuilder = new PlanModelBuilder();
        modelBuilder.visit(tree);
        Plan plan = modelBuilder.getPlan();

        new PlanModelValidator(collector).validate(plan);
        return collector.list();
    }

    private LifeScriptParser.PlanContext parseWithSyntaxChecks(String content, DiagnosticCollector collector) {
        // The grammar needs a final newline, which text from a browser often lacks.
        CharStream input = CharStreams.fromString(content.endsWith("\n") ? content : content + "\n");
        LifeScriptLexer lexer = new LifeScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LifeScriptParser parser = new LifeScriptParser(tokens);

        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        // Lexer errors have no token (offendingSymbol is null).
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
