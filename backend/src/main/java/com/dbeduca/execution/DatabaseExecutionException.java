package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public final class DatabaseExecutionException extends RuntimeException {

    private final DatabaseEngine engine;
    private final String objectName;

    public DatabaseExecutionException(
        DatabaseEngine engine,
        String objectName,
        Throwable cause
    ) {
        super(
            "Falha ao executar operação no banco de dados.",
            cause
        );

        this.engine = engine;
        this.objectName = objectName;
    }

    public DatabaseEngine engine() {
        return engine;
    }

    public String objectName() {
        return objectName;
    }
}