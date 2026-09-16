package com.dbeduca.core;

public interface ScriptGenerator {
    DatabaseEngine engine();
    String generate(TableDefinition table);
}
