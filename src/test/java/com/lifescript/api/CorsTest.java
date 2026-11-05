package com.lifescript.api;

import com.lifescript.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs the real app so CorsConfig applies; the origins list is overridden as a deployment would.
@Import(TestDatabase.class)
@SpringBootTest(properties = "lifescript.cors.allowed-origins=http://localhost:5173,http://app.example")
class CorsTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // The browser's "preflight" request, sent before a cross-origin POST with a JSON body.
    private ResultActions preflight(String origin, String method) throws Exception {
        return mvc.perform(options("/api/compile")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "Content-Type"));
    }

    @Test
    void configuredOrigins_mayPost() throws Exception {
        preflight("http://localhost:5173", "POST").andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        preflight("http://app.example", "POST").andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://app.example"));
    }

    @Test
    void methodsTheApiUses_areAllowed_othersRejected() throws Exception {
        preflight("http://localhost:5173", "PUT").andExpect(status().isOk());
        preflight("http://localhost:5173", "DELETE").andExpect(status().isOk());
        preflight("http://localhost:5173", "PATCH").andExpect(status().isForbidden());
    }

    @Test
    void otherOrigins_areRejected() throws Exception {
        preflight("http://evil.example", "POST").andExpect(status().isForbidden());
    }
}
