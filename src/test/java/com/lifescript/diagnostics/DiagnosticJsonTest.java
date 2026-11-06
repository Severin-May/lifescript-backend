package com.lifescript.diagnostics;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The JSON the frontend turns into Monaco markers and quick fixes.
class DiagnosticJsonTest {

    @Test
    void aFix_isATitleWithTextEdits_inMonacoPositions() {
        Diagnostic diagnostic = new Diagnostic(Stage.SYNTAX, Severity.ERROR, "'hgh' is not a valid priority", 3, 11, 3, 14,
                List.of(Fix.replace("Change to 'high'", 3, 11, 14, "high")));

        assertEquals("{\"stage\":\"SYNTAX\",\"severity\":\"ERROR\",\"message\":\"'hgh' is not a valid priority\","
                        + "\"startLineNumber\":3,\"startColumn\":11,\"endLineNumber\":3,\"endColumn\":14,"
                        + "\"fixes\":[{\"title\":\"Change to 'high'\",\"edits\":[{\"startLineNumber\":3,\"startColumn\":11,"
                        + "\"endLineNumber\":3,\"endColumn\":14,\"text\":\"high\"}]}]}",
                JsonMapper.builder().build().writeValueAsString(diagnostic));
    }

    @Test
    void withoutFixes_theListIsEmpty() {
        assertEquals(List.of(), Diagnostic.withoutPosition(Stage.SYNTAX, "Plan is empty").fixes());
    }
}
