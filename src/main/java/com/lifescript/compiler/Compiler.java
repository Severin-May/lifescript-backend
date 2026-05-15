package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Compiler {

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

    public List<String> compileFromString(String content) {
        CharStream input = CharStreams.fromString(content);
        return compileFromCharStream(input);
    }

    private List<String> compileFromCharStream(CharStream input) {
        LifeScriptLexer lexer = new LifeScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LifeScriptParser parser = new LifeScriptParser(tokens);

        LifeScriptParser.PlanContext tree = parser.plan();

        SemanticValidator validator = new SemanticValidator();
        validator.visit(tree);

        return validator.getErrors();
    }
}
