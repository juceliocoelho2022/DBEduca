package com.dbeduca.core;

public final class MySqlScriptGenerator implements ScriptGenerator {
    @Override public DatabaseEngine engine() { return DatabaseEngine.MYSQL; }

    @Override
    public String generate(TableDefinition table) {
        return "CREATE TABLE " + table.name() + " (\n    "
            + SqlScriptSupport.columns(table)
            + "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
    }
}
