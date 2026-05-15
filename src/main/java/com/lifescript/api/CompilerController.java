package com.lifescript.api;
import com.lifescript.compiler.Compiler;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CompilerController {
    private final Compiler compiler = new Compiler();

    @PostMapping("/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) {
        List<String> errors = compiler.compileFromString(request.getContent());

        CompileResponse response = new CompileResponse();
        response.setErrors(errors);
        response.setValid(errors.isEmpty());

        return response;
    }
}
