package com.dbeduca.api;

import com.dbeduca.execution.DatabaseExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final DatabaseExecutionService executionService;

    public ExecutionController(
        DatabaseExecutionService executionService
    ) {
        this.executionService = executionService;
    }

    @PostMapping
    public ExecuteDatabaseResponse execute(
        @Valid @RequestBody ExecuteDatabaseRequest request
    ) {
        var result = executionService.execute(
            request.engine(),
            request.toDomain()
        );

        return ExecuteDatabaseResponse.from(result);
    }
}