package com.lifescript.api;

import com.lifescript.TestPlans;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompilerControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompilerController(TestPlans.planning())).build();

    private static String body(String plan) {
        return "{\"content\": \"" + plan.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
    }

    private static String body(String plan, String timeZone) {
        return body(plan).replace("}", ", \"timeZone\": \"" + timeZone + "\"}");
    }

    @Test
    void compile_withTimeZone_usesTheUsersNow() throws Exception {
        // The test clock is 2024-01-01 00:00 UTC, which is already 09:00 in Tokyo.
        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON).content(body(VALID_PLAN, "Asia/Tokyo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.days['2024-01-01'][0].start").value("09:00"));
    }

    @Test
    void compile_unknownTimeZone_isABadRequest() throws Exception {
        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON).content(body(VALID_PLAN, "Mars/Olympus")))
                .andExpect(status().isBadRequest());
    }

    private static final String VALID_PLAN = """
            plan: p
            period: 2024-01-01 to 2024-01-07

            availability:
            monday: flexible

            tasks:
            task: a
            duration: 1h
            priority: high
            effort: high
            note: "first draft"
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
    void compile_nonAsciiName_survivesTheRoundTrip() throws Exception {
        String plan = VALID_PLAN.replace("task: a", "task: házifeladat");

        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON).content(body(plan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.schedule.days['2024-01-01'][0].name").value("házifeladat"));
    }

    @Test
    void compile_validPlan_hasTheSchedule() throws Exception {
        // 2024-01-01 is a Monday; flexible starts at the default morning start, 06:00
        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON).content(body(VALID_PLAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.days.length()").value(7))
                .andExpect(jsonPath("$.schedule.days['2024-01-01'].length()").value(1))
                .andExpect(jsonPath("$.schedule.days['2024-01-01'][0].type").value("TASK"))
                .andExpect(jsonPath("$.schedule.days['2024-01-01'][0].name").value("a"))
                .andExpect(jsonPath("$.schedule.days['2024-01-01'][0].start").value("06:00"))
                .andExpect(jsonPath("$.schedule.days['2024-01-01'][0].end").value("07:00"))
                .andExpect(jsonPath("$.schedule.days['2024-01-01'][0].note").value("first draft"))
                .andExpect(jsonPath("$.schedule.days['2024-01-02']").isEmpty())
                .andExpect(jsonPath("$.schedule.unscheduled").isEmpty())
                .andExpect(jsonPath("$.schedule.warnings").isEmpty());
    }

    @Test
    void compile_unscheduledTask_hasNameDateAndReason() throws Exception {
        String plan = VALID_PLAN.replace("duration: 1h", "duration: 1h\nrepeats: tuesday");

        mvc.perform(post("/api/compile").contentType(MediaType.APPLICATION_JSON).content(body(plan)))
                .andExpect(jsonPath("$.schedule.unscheduled[0].name").value("a"))
                .andExpect(jsonPath("$.schedule.unscheduled[0].date").value("2024-01-02"))
                .andExpect(jsonPath("$.schedule.unscheduled[0].reason").value("no free slot long enough on 2024-01-02"));
    }

    @Test
    void compile_invalidPlan_diagnosticHasExactlyTheMonacoMarkerFields_andFixes() throws Exception {
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
                // always present, so the frontend needn't check for a missing field
                .andExpect(jsonPath("$.diagnostics[0].fixes").isArray())
                .andExpect(jsonPath("$.diagnostics[0].fixes").isEmpty())
                // nothing else in the JSON, e.g. hasPosition()
                .andExpect(jsonPath("$.diagnostics[0].length()").value(8));
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
    void validate_validPlan_isValid_withoutASchedule() throws Exception {
        mvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(body(VALID_PLAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.diagnostics").isEmpty())
                .andExpect(jsonPath("$.schedule").value(nullValue()));
    }

    @Test
    void validate_reportsValidationErrors_notJustSyntax() throws Exception {
        String plan = VALID_PLAN.replace("effort: high", "effort: high\ndependencies: ghost");

        mvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(body(plan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.diagnostics[0].stage").value("VALIDATION"))
                .andExpect(jsonPath("$.diagnostics[0].message").value("Task 'a' depends on 'ghost', which doesn't exist"));
    }

    @Test
    void validate_missingContent_isEmptyPlanWithoutPosition_insteadOfACrash() throws Exception {
        mvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.diagnostics[0].message").value("Plan is empty"))
                .andExpect(jsonPath("$.schedule").value(nullValue()))
                .andExpect(jsonPath("$.diagnostics[0].startLineNumber").value(0));
    }
}
