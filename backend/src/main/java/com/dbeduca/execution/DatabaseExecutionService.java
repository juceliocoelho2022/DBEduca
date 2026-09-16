package com.dbeduca.execution;

import com.dbeduca.core.DatabaseEngine;
import com.dbeduca.core.ScriptGeneratorRegistry;
import com.dbeduca.core.TableDefinition;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DatabaseExecutionService {

    private final ScriptGeneratorRegistry scriptRegistry;

    private final Map<DatabaseEngine, DatabaseExecutionAdapter> adapters =
        new EnumMap<>(DatabaseEngine.class);

    public DatabaseExecutionService(
        ScriptGeneratorRegistry scriptRegistry,
        List<DatabaseExecutionAdapter> adapters
    ) {
        this.scriptRegistry = scriptRegistry;

        adapters.forEach(
            adapter -> this.adapters.put(
                adapter.engine(),
                adapter
            )
        );
    }

    public ExecutionResult execute(
        DatabaseEngine engine,
        TableDefinition table
    ) {
        var adapter = adapters.get(engine);

        if (adapter == null) {
            throw new IllegalArgumentException(
                "Execução ainda não habilitada para " + engine + "."
            );
        }

        String ddl = scriptRegistry.generate(
            engine,
            table
        );

        return adapter.executeCreateTable(
            table.name(),
            ddl
        );
    }
}