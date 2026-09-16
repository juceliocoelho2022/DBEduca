package com.dbeduca.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class OracleScriptGenerator implements ScriptGenerator {
    private static final Pattern VARCHAR = Pattern.compile("VARCHAR\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECIMAL = Pattern.compile("DECIMAL\\((\\d+)\\s*,\\s*(\\d+)\\)", Pattern.CASE_INSENSITIVE);

    @Override
    public DatabaseEngine engine() {
        return DatabaseEngine.ORACLE;
    }

    @Override
    public String generate(TableDefinition table) {
        String columns = table.columns().stream()
            .map(this::column)
            .collect(Collectors.joining(",\n    "));

        return "CREATE TABLE " + table.name() + " (\n    " + columns + "\n);";
    }

    private String column(ColumnDefinition column) {
        var sql = new StringBuilder(column.name())
            .append(' ')
            .append(toOracleType(column.type()));
        if (column.primaryKey()) sql.append(" PRIMARY KEY");
        if (column.notNull()) sql.append(" NOT NULL");
        if (column.unique()) sql.append(" UNIQUE");
        return sql.toString();
    }

    private String toOracleType(String type) {
        String normalized = type.trim().toUpperCase();
        return switch (normalized) {
            case "BIGINT" -> "NUMBER(19)";
            case "INTEGER" -> "NUMBER(10)";
            case "TEXT" -> "CLOB";
            case "BOOLEAN" -> "BOOLEAN";
            case "DATE" -> "DATE";
            case "TIMESTAMP" -> "TIMESTAMP";
            default -> mapParameterizedType(normalized);
        };
    }

    private String mapParameterizedType(String type) {
        Matcher varchar = VARCHAR.matcher(type);
        if (varchar.matches()) {
            return "VARCHAR2(" + varchar.group(1) + ")";
        }

        Matcher decimal = DECIMAL.matcher(type);
        if (decimal.matches()) {
            return "NUMBER(" + decimal.group(1) + "," + decimal.group(2) + ")";
        }

        return type;
    }
}
