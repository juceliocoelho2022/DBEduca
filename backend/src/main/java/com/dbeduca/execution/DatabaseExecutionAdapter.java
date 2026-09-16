package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;

public interface DatabaseExecutionAdapter {

    DatabaseEngine engine();

    ExecutionResult executeCreateTable(
        String tableName,
        String ddl
    );
}