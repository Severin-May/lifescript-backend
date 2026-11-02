package com.lifescript.api;

import com.lifescript.planning.PlanningService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;

@RestController
@RequestMapping("/api")
public class CompilerController {
    private final PlanningService planning;

    public CompilerController(PlanningService planning) {
        this.planning = planning;
    }

    @PostMapping("/validate")
    public CompileResponse validate(@RequestBody CompileRequest request) {
        return CompileResponse.of(planning.validate(request.getContent(), zoneOf(request)));
    }

    @PostMapping("/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) {
        return CompileResponse.of(planning.schedule(request.getContent(), zoneOf(request)));
    }

    // A bad zone is the client's bug, not the user's plan, so it's a 400 rather than a diagnostic.
    private static ZoneId zoneOf(CompileRequest request) {
        String timeZone = request.getTimeZone();
        if (timeZone == null || timeZone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone '" + timeZone + "'");
        }
    }
}
