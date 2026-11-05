package com.lifescript.api;

import com.jayway.jsonpath.JsonPath;
import com.lifescript.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestDatabase.class)
@SpringBootTest
class PlanControllerTest {

    private static final String VALID = """
            plan: p
            period: 2030-01-07 to 2030-01-13

            availability:
            monday: flexible

            tasks:
            task: a
            duration: 1h
            priority: high
            effort: high
            """;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private String body(Map<String, Object> fields) {
        return json.writeValueAsString(fields);
    }

    private String create(String name) throws Exception {
        String response = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name, "content", VALID, "timeZone", "Europe/Budapest"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions update(String id, long version, String name) throws Exception {
        return mvc.perform(put("/api/plans/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", name, "content", VALID, "version", version))));
    }

    @Test
    void create_is201_withTheNewPlansAddress() throws Exception {
        mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "my week", "content", VALID))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/plans/")))
                .andExpect(jsonPath("$.name").value("my week"))
                .andExpect(jsonPath("$.content").value(VALID))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    void savedPlan_canBeLoaded_listed_andItsHistoryRead() throws Exception {
        String id = create("my week");

        mvc.perform(get("/api/plans/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(VALID));
        mvc.perform(get("/api/plans")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/plans/" + id + "/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value(0));
    }

    @Test
    void update_withTheLoadedVersion_givesTheNextVersion() throws Exception {
        String id = create("my week");

        update(id, 0, "renamed").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void update_basedOnAnOlderVersion_is409() throws Exception {
        String id = create("my week");
        update(id, 0, "tab A").andExpect(status().isOk());

        update(id, 0, "tab B").andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(containsString("was changed since version 0")));
    }

    @Test
    void unknownPlan_is404() throws Exception {
        mvc.perform(get("/api/plans/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void delete_is204_andThePlanIsGone() throws Exception {
        String id = create("my week");

        mvc.perform(delete("/api/plans/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/plans/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void missingName_orMissingVersion_is400() throws Exception {
        mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", " ", "content", VALID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("needs a name")));

        String id = create("my week");
        mvc.perform(put("/api/plans/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "my week", "content", VALID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("version")));
    }
}
