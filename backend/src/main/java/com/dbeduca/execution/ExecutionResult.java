package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public record ExecutionResult(
    DatabaseEngine engine,
    String status,
    String objectType,
    String objectName,
    String message,
    String script
) {
}