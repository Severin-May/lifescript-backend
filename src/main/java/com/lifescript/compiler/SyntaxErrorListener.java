package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptLexer;
import com.lifescript.grammar.LifeScriptParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import java.util.List;

// Turns ANTLR's syntax errors into diagnostics, with clearer messages for keywords used as names.
class SyntaxErrorListener extends BaseErrorListener {

    private final DiagnosticCollector collector;
    private final TokenStream tokens;

    SyntaxErrorListener(DiagnosticCollector collector, TokenStream tokens) {
        this.collector = collector;
        this.tokens = tokens;
    }

    // Lexer errors have no token (offendingSymbol is null).
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg, RecognitionException e) {
        Token offending = offendingSymbol instanceof Token token ? token : null;
        Token misusedKeyword = findKeywordUsedAsActivityName(recognizer, offending, tokens);
        if (misusedKeyword != null) {
            collector.syntaxError(misusedKeyword, misusedKeyword.getLine(),
                    misusedKeyword.getCharPositionInLine(), reservedWord(misusedKeyword));
        } else if (isKeywordWhereNameExpected(recognizer, offending)) {
            collector.syntaxError(offending, line, charPositionInLine, reservedWord(offending));
        } else {
            collector.syntaxError(offending, line, charPositionInLine, "Syntax error - " + msg);
        }
    }

    private static String reservedWord(Token keyword) {
        return String.format("'%s' is a reserved word and can't be used as a name", keyword.getText());
    }

    // Keywords are the tokens whose fixed text is a word, e.g. 'low' (not symbols like ':').
    private static boolean isKeyword(Token token) {
        String literal = LifeScriptLexer.VOCABULARY.getLiteralName(token.getType());
        return literal != null && literal.matches("'[a-z]+'");
    }

    private static boolean isKeywordWhereNameExpected(Recognizer<?, ?> recognizer, Token offending) {
        return recognizer instanceof Parser parser && offending != null && isKeyword(offending)
                && parser.getExpectedTokens().contains(LifeScriptLexer.IDENTIFIER);
    }

    // Indentation is ignored, so after a routine's activities "time: 30m" is read as the routine's own time.
    // A duration there means it was an activity named after a keyword; returns that keyword, else null.
    private static Token findKeywordUsedAsActivityName(Recognizer<?, ?> recognizer, Token offending, TokenStream tokens) {
        if (!(recognizer instanceof Parser parser) || offending == null
                || offending.getType() != LifeScriptLexer.DURATION_VAL || offending.getTokenIndex() < 2) {
            return null;
        }
        Token colon = tokens.get(offending.getTokenIndex() - 1);
        Token keyword = tokens.get(offending.getTokenIndex() - 2);
        if (colon.getType() != LifeScriptLexer.COLON || !isKeyword(keyword)) {
            return null;
        }

        ParserRuleContext ctx = parser.getContext();
        while (ctx != null && !(ctx instanceof LifeScriptParser.RoutinePropertyContext)) {
            ctx = ctx.getParent();
        }
        if (ctx == null) {
            return null;
        }
        List<LifeScriptParser.RoutinePropertyContext> properties =
                ((LifeScriptParser.RoutineEntryContext) ctx.getParent()).routineProperty();
        int index = properties.indexOf(ctx);
        return index > 0 && properties.get(index - 1).routineActivities() != null ? keyword : null;
    }
}
