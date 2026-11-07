package com.lifescript.api.compiling;

import com.lifescript.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The real web server with its thread pool: many requests at once must not mix up each other's results.
@Import(TestDatabase.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParallelCompileTest {

    private static final int REQUESTS = 100;
    private static final int AT_ONCE = 8;

    @LocalServerPort
    private int port;

    private static String plan(int i) {
        // Each plan has its own task name and length, so a mixed-up answer would show.
        return "plan: p\\nperiod: 2030-01-07 to 2030-01-13\\n\\navailability:\\nmonday: flexible\\n\\ntasks:\\n"
                + "task: task_" + i + "\\nduration: " + (15 + i % 30) + "m\\npriority: high\\neffort: high\\n";
    }

    @Test
    void manyCompilesAtOnce_eachGetsItsOwnSchedule() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ExecutorService pool = Executors.newFixedThreadPool(AT_ONCE);
        try {
            List<Future<HttpResponse<String>>> responses = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/compile"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"content\": \"" + plan(i) + "\", \"timeZone\": \"Europe/Budapest\"}"))
                        .build();
                responses.add(pool.submit(() -> client.send(request, HttpResponse.BodyHandlers.ofString())));
            }

            for (int i = 0; i < REQUESTS; i++) {
                HttpResponse<String> response = responses.get(i).get(60, TimeUnit.SECONDS);
                String body = response.body();
                assertEquals(200, response.statusCode(), body);
                assertTrue(body.contains("\"valid\":true"), body);
                assertTrue(body.contains("\"name\":\"task_" + i + "\""), body);
                // Only its own task: no other request's task name in this answer.
                assertFalse(body.replace("\"name\":\"task_" + i + "\"", "").contains("\"name\":\"task_"), body);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
