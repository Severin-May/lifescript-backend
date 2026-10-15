package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import com.lifescript.model.Plan;
import org.antlr.v4.runtime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Compiler {

    private static final Logger log = LoggerFactory.getLogger(Compiler.class);

    public void compileFromFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("File not found: " + filePath);
            return;
        }

        try {
            CharStream input = CharStreams.fromFileName(filePath);
            LifeScriptLexer lexer = new LifeScriptLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            LifeScriptParser parser = new LifeScriptParser(tokens);
            LifeScriptParser.PlanContext tree = parser.plan();

            SemanticValidator validator = new SemanticValidator();
            validator.visit(tree);

            if (validator.hasErrors()) {
                validator.getErrors().forEach(System.err::println);
            } else {
                System.out.println("Validation successful!");
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return;
        }
    }

    public List<String> fullCompile(String content) {
        if (content == null || content.isBlank()) {
            return List.of("Plan is empty");
        }

        // Safety net: a crash anywhere in the pipeline is a bug in the compiler, not in the user's plan.
        // Report it as an error instead of failing the whole request, and log the details for debugging.
        try {
            return runPipeline(content);
        } catch (RuntimeException e) {
            log.error("Compiler crashed on input:\n{}", content, e);
            return List.of("Internal error while compiling the plan (this is a bug in LifeScript, not in your plan)");
        }
    }

    private List<String> runPipeline(String content) {
        CharStream input = CharStreams.fromString(content);
        List<String> errors = new ArrayList<>();
        LifeScriptParser.PlanContext tree = parseWithSyntaxChecks(input, errors);

        if (!errors.isEmpty()) return errors;

        SemanticValidator validator = new SemanticValidator();
        validator.visit(tree);
        errors.addAll(validator.getErrors());

        if (!errors.isEmpty()) return errors;

        PlanModelBuilder modelBuilder = new PlanModelBuilder();
        modelBuilder.visit(tree);
        Plan plan = modelBuilder.getPlan();

        errors.addAll(new PlanModelValidator().validate(plan));

        if (!errors.isEmpty()) return errors;

//        try {
//            Scheduler scheduler = new Scheduler(plan);
//            Schedule schedule = scheduler.schedule();
//        } catch (Exception e) {
//            errors.add("Scheduling error: " + e.getMessage());
//        }

        return errors;
    }

    public List<String> syntaxValidate(String content) {
        CharStream input = CharStreams.fromString(content);
        List<String> errors = new ArrayList<>();
        parseWithSyntaxChecks(input, errors);
        return errors;
    }

    private LifeScriptParser.PlanContext parseWithSyntaxChecks(CharStream input, List<String> errors) {
        LifeScriptLexer lexer = new LifeScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LifeScriptParser parser = new LifeScriptParser(tokens);

        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        BaseErrorListener syntaxErrorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                errors.add(String.format("Line %d:%d: Syntax error - %s", line, charPositionInLine, msg));
            }
        };

        lexer.addErrorListener(syntaxErrorListener);
        parser.addErrorListener(syntaxErrorListener);

        return parser.plan();
    }
}
