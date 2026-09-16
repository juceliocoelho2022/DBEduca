package com.dbeduca.core;

public final class PostgresScriptGenerator implements ScriptGenerator {
    @Override public DatabaseEngine engine() { return DatabaseEngine.POSTGRESQL; }

    @Override
    public String generate(TableDefinition table) {
        return "CREATE TABLE " + table.name() + " (\n    "
            + SqlScriptSupport.columns(table)
            + "\n);";
    }
}
