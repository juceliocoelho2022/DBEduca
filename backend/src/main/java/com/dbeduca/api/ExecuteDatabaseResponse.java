package com.dbeduca.api;

import com.dbeduca.core.DatabaseEngine;
import com.dbeduca.execution.ExecutionResult;

public record ExecuteDatabaseResponse(
    DatabaseEngine engine,
    String status,
    String objectType,
    String objectName,
    String message,
    String script
) {

    public static ExecuteDatabaseResponse from(
        ExecutionResult result
    ) {
        return new ExecuteDatabaseResponse(
            result.engine(),
            result.status(),
            result.objectType(),
            result.objectName(),
            result.message(),
            result.script()
        );
    }
}