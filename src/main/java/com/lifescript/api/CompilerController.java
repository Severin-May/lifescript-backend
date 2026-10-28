package com.lifescript.api;

import com.lifescript.compiler.LifeScriptCompiler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CompilerController {
    private final LifeScriptCompiler compiler;

    public CompilerController() {
        this(new LifeScriptCompiler());
    }

    CompilerController(LifeScriptCompiler compiler) {
        this.compiler = compiler;
    }

    @PostMapping("/validate")
    public CompileResponse validate(@RequestBody CompileRequest request) {
        return CompileResponse.of(compiler.syntaxValidate(request.getContent()));
    }

    @PostMapping("/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) {
        return CompileResponse.of(compiler.compile(request.getContent()));
    }
}
