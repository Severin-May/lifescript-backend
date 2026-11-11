package com.lifescript.api.compiling;

import com.lifescript.api.TimeZones;
import com.lifescript.planning.PlanningService;
import com.lifescript.planning.UnknownAlgorithmException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CompilerController {
    private final PlanningService planning;

    public CompilerController(PlanningService planning) {
        this.planning = planning;
    }

    @GetMapping("/algorithms")
    public AlgorithmsResponse algorithms() {
        return AlgorithmsResponse.from(planning.schedulers());
    }

    @PostMapping("/validate")
    public CompileResponse validate(@RequestBody CompileRequest request) {
        return CompileResponse.of(planning.validate(request.content(), TimeZones.parse(request.timeZone())));
    }

    @PostMapping("/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) {
        try {
            return CompileResponse.of(planning.schedule(
                    request.content(), TimeZones.parse(request.timeZone()), request.algorithm()));
        } catch (UnknownAlgorithmException e) {
            // Like an unknown time zone: the client's bug, not the user's plan.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
