package com.lifescript.diagnostics;

import java.util.List;

// A quick fix the editor can apply in one click, e.g. "Change to 'duration'".
public record Fix(String title, List<TextEdit> edits) {

    public Fix {
        edits = List.copyOf(edits);
    }

    public static Fix replace(String title, int line, int startColumn, int endColumn, String text) {
        return new Fix(title, List.of(new TextEdit(line, startColumn, line, endColumn, text)));
    }
}
