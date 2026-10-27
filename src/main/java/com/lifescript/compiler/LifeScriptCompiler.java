package com.lifescript.compiler;

import com.lifescript.diagnostics.Diagnostic;
import com.lifescript.diagnostics.Stage;
import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.Plan;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.antlr.v4.runtime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LifeScriptCompiler {

    private static final Logger log = LoggerFactory.getLogger(LifeScriptCompiler.class);

    private final Scheduler scheduler;

    public LifeScriptCompiler() {
        this(new GreedyScheduler());
    }

    public LifeScriptCompiler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public CompileResult compile(String content) {
        if (isBlank(content)) {
            return CompileResult.failed(List.of(emptyPlan()));
        }
        try {
            return runPipeline(content);
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
    private static Diagnostic internalError(String content, RuntimeException e) {
        log.error("Compiler crashed on input:\n{}", content, e);
        return Diagnostic.withoutPosition(Stage.INTERNAL,
                "Internal error while compiling the plan (this is a bug in LifeScript, not in your plan)");
    }

    private CompileResult runPipeline(String content) {
        DiagnosticCollector collector = new DiagnosticCollector();
        LifeScriptParser.PlanContext tree = parseWithSyntaxChecks(content, collector);
        if (!collector.isEmpty()) {
            return CompileResult.failed(collector.list());
        }

        new SemanticValidator(collector).visit(tree);
        if (!collector.isEmpty()) {
            return CompileResult.failed(collector.list());
        }

        Plan plan = new PlanModelBuilder().build(tree);

        new PlanModelValidator(collector).validate(plan);
        if (!collector.isEmpty()) {
            return CompileResult.failed(collector.list());
        }

        return new CompileResult(List.of(), scheduler.schedule(plan));
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
