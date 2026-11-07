package com.lifescript.compiler;

import com.lifescript.diagnostics.Fix;

import java.util.List;

// A syntax error's message with the quick fixes that go with it.
record SyntaxProblem(String message, List<Fix> fixes) {

    static SyntaxProblem of(String message) {
        return new SyntaxProblem(message, List.of());
    }

    static SyntaxProblem withFix(String message, Fix fix) {
        return new SyntaxProblem(message, List.of(fix));
    }
}
