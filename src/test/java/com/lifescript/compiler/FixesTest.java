package com.lifescript.compiler;

import com.lifescript.diagnostics.Fix;
import com.lifescript.diagnostics.TextEdit;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixesTest {

    private static final String LINE = "dependencies: a, bb, c";

    // A token for the text at that place in LINE (line 3).
    private static Token token(String text) {
        int column = LINE.indexOf(text, "dependencies:".length());
        CommonToken token = new CommonToken(0, text);
        token.setLine(3);
        token.setCharPositionInLine(column);
        token.setStartIndex(column);
        token.setStopIndex(column + text.length() - 1);
        return token;
    }

    private static String applyToLine(Fix fix) {
        TextEdit edit = fix.edits().get(0);
        return LINE.substring(0, edit.startColumn() - 1) + edit.text() + LINE.substring(edit.endColumn() - 1);
    }

    private final List<Token> items = List.of(token("a"), token("bb"), token("c"));

    @Test
    void removeListItem_takesItsCommaWithIt() {
        assertEquals("dependencies: bb, c", applyToLine(Fixes.removeListItem(items, 0, "Remove 'a'")));
        assertEquals("dependencies: a, c", applyToLine(Fixes.removeListItem(items, 1, "Remove 'bb'")));
        assertEquals("dependencies: a, bb", applyToLine(Fixes.removeListItem(items, 2, "Remove 'c'")));
    }

    @Test
    void removeListItem_ofTheOnlyItem_isTheCallersWholeLine() {
        assertThrows(IllegalArgumentException.class, () -> Fixes.removeListItem(List.of(token("a")), 0, "Remove 'a'"));
    }

    @Test
    void replace_andInsertAfter_useTheTokensColumns() {
        assertEquals("dependencies: a, research, c", applyToLine(Fixes.replace(token("bb"), token("bb"), "research")));
        assertEquals("Change to 'research'", Fixes.replace(token("bb"), token("bb"), "research").title());
        assertEquals("dependencies: a, bb, c 17:00", applyToLine(Fixes.insertAfter(token("c"), " 17:00", "Add 17:00")));
    }

    @Test
    void wholeLines_areRemovedAndInsertedWithTheirLineBreak() {
        assertEquals(new Fix("Remove the line", List.of(new TextEdit(5, 1, 6, 1, ""))),
                Fixes.removeLine(5, "Remove the line"));
        assertEquals(new Fix("Add 'effort: moderate'", List.of(new TextEdit(9, 1, 9, 1, "effort: moderate\n"))),
                Fixes.insertLineBelow(8, "effort: moderate", "Add 'effort: moderate'"));
    }
}
