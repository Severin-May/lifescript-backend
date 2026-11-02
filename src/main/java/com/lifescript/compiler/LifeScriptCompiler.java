package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Stage;
import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.Plan;
import org.antlr.v4.runtime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

public class LifeScriptCompiler {

    private static final Logger log = LoggerFactory.getLogger(LifeScriptCompiler.class);

    // today: the user's date, which the period must not start before.
    public CompileResult compile(String content, LocalDate today) {
        if (isBlank(content)) {
            return CompileResult.failed(List.of(emptyPlan()));
        }
        try {
            return runPipeline(content, today);
        } catch (RuntimeException | StackOverflowError e) {
            return CompileResult.failed(List.of(internalError(content, e)));
        }
    }

    public List<Diagnostic> syntaxValidate(String content) {
        if (isBlank(content)) {
            return List.of(emptyPlan());
        }
        try {
            DiagnosticCollector collector = new DiagnosticCollector();
            parseWithSyntaxChecks(content, collector);
            return collector.list();
        } catch (RuntimeException | StackOverflowError e) {
            return List.of(internalError(content, e));
        }
    }

    private static boolean isBlank(String content) {
        return content == null || content.isBlank();
    }

    private static Diagnostic emptyPlan() {
        return Diagnostic.withoutPosition(Stage.SYNTAX, "Plan is empty");
    }

    // A crash is a compiler bug, not the user's: report it instead of failing the request.
    // StackOverflowError is caught too: very deep dependency chains overflow the recursive checks.
    private static Diagnostic internalError(String content, Throwable e) {
        // Plans are personal data, so only their size is logged, never their text.
        log.error("Compiler crashed on a plan of {} characters, {} lines", content.length(), content.lines().count(), e);
        return Diagnostic.withoutPosition(Stage.INTERNAL,
                "Internal error while compiling the plan (this is a bug in LifeScript, not in your plan)");
    }

    private CompileResult runPipeline(String content, LocalDate today) {
        DiagnosticCollector collector = new DiagnosticCollector();
        LifeScriptParser.PlanContext tree = parseWithSyntaxChecks(content, collector);
        if (!collector.isEmpty()) {
            return CompileResult.failed(collector.list());
        }

        new SemanticValidator(collector, today).visit(tree);
        if (!collector.isEmpty()) {
            return CompileResult.failed(collector.list());
        }

        Plan plan = new PlanModelBuilder().build(tree);

        new PlanModelValidator(collector).validate(plan);
        if (!collector.isEmpty()) {
            return CompileResult.failed(collector.list());
        }

        return new CompileResult(List.of(), plan);
    }

    private LifeScriptParser.PlanContext parseWithSyntaxChecks(String content, DiagnosticCollector collector) {
        // The grammar needs a final newline, which text from a browser often lacks.
        CharStream input = CharStreams.fromString(content.endsWith("\n") ? content : content + "\n");
        LifeScriptLexer lexer = new LifeScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LifeScriptParser parser = new LifeScriptParser(tokens);

        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        SyntaxErrorListener syntaxErrorListener = new SyntaxErrorListener(collector, tokens);
        lexer.addErrorListener(syntaxErrorListener);
        parser.addErrorListener(syntaxErrorListener);

        return parser.plan();
    }
}
