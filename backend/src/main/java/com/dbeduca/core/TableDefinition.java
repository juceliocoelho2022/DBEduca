package com.dbeduca.core;

import java.util.HashSet;
import java.util.List;

public record TableDefinition(String name, List<ColumnDefinition> columns) {
    public TableDefinition {
        name = IdentifierRules.requireIdentifier(name, "Nome da tabela/collection");
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Adicione pelo menos uma coluna/campo.");
        }
        columns = List.copyOf(columns);
        var names = new HashSet<String>();
        for (var column : columns) {
            if (!names.add(column.name().toLowerCase())) {
                throw new IllegalArgumentException("Coluna/campo duplicado: " + column.name());
            }
        }
    }
}
