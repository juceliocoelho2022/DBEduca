package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public final class TableAlreadyExistsException extends RuntimeException {

    private final DatabaseEngine engine;
    private final String objectName;

    public TableAlreadyExistsException(
        DatabaseEngine engine,
        String objectName
    ) {
        super(
            "A tabela " +
            objectName +
            " já existe no Oracle."
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