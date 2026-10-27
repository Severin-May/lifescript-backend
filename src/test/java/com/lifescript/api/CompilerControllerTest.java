package com.lifescript.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Checks the JSON the frontend receives, through the real controller and JSON conversion.
class CompilerControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompilerController()).build();

    // The JSON request body for a plan, with its newlines escaped.
    private static String body(String plan) {
        return "{\"content\": \"" + plan.replace("\n", "\\n") + "\"}";
    }

    private static final String VALID_PLAN = """
            plan: p
            period: 2024-01-01 to 2024-01-31

            availability:
            monday: flexible

            tasks:
            task: a
            duration: 1h
            priority: high
            effort: high
            """;

    @Test
    void compile_validPlan_isValidWithNoDiagnostics() throws Exception {
        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON).content(body(VALID_PLAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.diagnostics").isEmpty())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void compile_invalidPlan_diagnosticHasExactlyTheMonacoMarkerFields() throws Exception {
        String plan = VALID_PLAN.replace("duration: 1h", "duration: 0m");

        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON).content(body(plan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.diagnostics.length()").value(1))
                .andExpect(jsonPath("$.diagnostics[0].stage").value("VALIDATION"))
                .andExpect(jsonPath("$.diagnostics[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.diagnostics[0].message").value("Duration '0m' must be greater than zero"))
                .andExpect(jsonPath("$.diagnostics[0].startLineNumber").value(9))
                .andExpect(jsonPath("$.diagnostics[0].startColumn").value(1))
                .andExpect(jsonPath("$.diagnostics[0].endLineNumber").value(9))
                .andExpect(jsonPath("$.diagnostics[0].endColumn").value(13))
                // nothing else leaks into the JSON (e.g. helper methods of Diagnostic)
                .andExpect(jsonPath("$.diagnostics[0].length()").value(7));
    }

    @Test
    void validate_syntaxErrorOnly_marksTheToken() throws Exception {
        String plan = VALID_PLAN.replace("task: a", "task: low");

        mvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(body(plan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.diagnostics[0].stage").value("SYNTAX"))
                .andExpect(jsonPath("$.diagnostics[0].startLineNumber").value(8))
                .andExpect(jsonPath("$.diagnostics[0].startColumn").value(7))
                .andExpect(jsonPath("$.diagnostics[0].endColumn").value(10));
    }

    @Test
    void validate_missingContent_isEmptyPlanWithoutPosition_insteadOfACrash() throws Exception {
        mvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.diagnostics[0].message").value("Plan is empty"))
                .andExpect(jsonPath("$.diagnostics[0].startLineNumber").value(0));
    }
}
