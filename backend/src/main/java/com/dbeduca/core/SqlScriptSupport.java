package com.dbeduca.core;

import java.util.stream.Collectors;

final class SqlScriptSupport {
    private SqlScriptSupport() {}

    static String columns(TableDefinition table) {
        return table.columns().stream()
            .map(SqlScriptSupport::column)
            .collect(Collectors.joining(",\n    "));
    }

    private static String column(ColumnDefinition c) {
        var sql = new StringBuilder(c.name()).append(' ').append(c.type());
        if (c.primaryKey()) sql.append(" PRIMARY KEY");
        if (c.notNull()) sql.append(" NOT NULL");
        if (c.unique()) sql.append(" UNIQUE");
        return sql.toString();
    }
}
