package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public final class DatabaseUnavailableException extends RuntimeException {

    private final DatabaseEngine engine;

    public DatabaseUnavailableException(
        DatabaseEngine engine,
        Throwable cause
    ) {
        super(
            "Oracle temporariamente indisponível. Verifique o ambiente do laboratório.",
            cause
        );

        this.engine = engine;
    }

    public DatabaseEngine engine() {
        return engine;
    }
}