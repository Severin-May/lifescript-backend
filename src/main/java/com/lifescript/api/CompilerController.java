package com.lifescript.api;

import com.lifescript.planning.PlanningService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CompilerController {
    private final PlanningService planning;

    public CompilerController(PlanningService planning) {
        this.planning = planning;
    }

    @PostMapping("/validate")
    public CompileResponse validate(@RequestBody CompileRequest request) {
        return CompileResponse.of(planning.validate(request.content(), TimeZones.parse(request.timeZone())));
    }

    @PostMapping("/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) {
        return CompileResponse.of(planning.schedule(request.content(), TimeZones.parse(request.timeZone())));
    }
}
