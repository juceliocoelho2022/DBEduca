package com.dbeduca.core;

public record ColumnDefinition(
        String name,
        String type,
        boolean primaryKey,
        boolean notNull,
        boolean unique
) {
    public ColumnDefinition {
        name = IdentifierRules.requireIdentifier(name, "Nome da coluna");
        type = IdentifierRules.requireSqlType(type);
    }
}
