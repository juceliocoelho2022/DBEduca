package com.dbeduca.api;

import com.dbeduca.core.ScriptGeneratorRegistry;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scripts")
public class ScriptController {
    private final ScriptGeneratorRegistry registry;

    public ScriptController(ScriptGeneratorRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/generate")
    public GenerateScriptResponse generate(@Valid @RequestBody GenerateScriptRequest request) {
        String script = registry.generate(request.engine(), request.toDomain());
        return new GenerateScriptResponse(request.engine(), script);
    }
}
