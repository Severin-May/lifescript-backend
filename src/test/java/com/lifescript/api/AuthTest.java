package com.lifescript.api;

import com.lifescript.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Goes through the real login flow: the session from logging in is what later requests use.
@Import(TestDatabase.class)
@SpringBootTest
class AuthTest {

    private static final String PASSWORD = "correct horse";
    private static final String PLAN = """
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

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;
    private String email;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        email = UUID.randomUUID() + "@test.example";
    }

    private static String credentials(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    private ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(email, password)));
    }

    private MockHttpSession sessionOf(ResultActions result) {
        return (MockHttpSession) result.andReturn().getRequest().getSession(false);
    }

    @Test
    void register_logsYouIn() throws Exception {
        MockHttpSession session = sessionOf(register(email, PASSWORD).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email)));

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void login_worksWithTheEmailInAnyCase() throws Exception {
        register(email, PASSWORD);

        MockHttpSession session = sessionOf(login(email.toUpperCase(), PASSWORD).andExpect(status().isOk()));

        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void wrongPassword_andUnknownEmail_getTheSameAnswer() throws Exception {
        register(email, PASSWORD);

        login(email, "wrong password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong email or password"));
        login("nobody-" + email, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong email or password"));
    }

    @Test
    void registering_aTakenEmail_isAConflict_andBadInputIsRejected() throws Exception {
        register(email, PASSWORD);

        register(email, PASSWORD).andExpect(status().isConflict());
        register("not-an-email", PASSWORD).andExpect(status().isBadRequest());
        register("x" + email, "short").andExpect(status().isBadRequest());
    }

    @Test
    void logout_endsTheSession() throws Exception {
        MockHttpSession session = sessionOf(register(email, PASSWORD));

        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void csrf_tellsWhichHeaderCarriesTheToken() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void savedPlans_needLogin() throws Exception {
        mvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void compiling_isOpen_withoutLoginOrCsrfToken() throws Exception {
        mvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"plan: p\\n\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void saving_withoutTheCsrfToken_isRefused() throws Exception {
        MockHttpSession session = sessionOf(register(email, PASSWORD));

        mvc.perform(post("/api/plans").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"p\", \"content\": \"plan: p\\n\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherAccount_cantOpenYourPlan() throws Exception {
        MockHttpSession mine = sessionOf(register(email, PASSWORD));
        String created = mvc.perform(post("/api/plans").session(mine).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"p\", \"content\": " + jsonString(PLAN) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        MockHttpSession theirs = sessionOf(register("other-" + email, PASSWORD));
        mvc.perform(get(created).session(theirs)).andExpect(status().isNotFound());
        mvc.perform(get(created).session(mine)).andExpect(status().isOk());
    }

    private static String jsonString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
