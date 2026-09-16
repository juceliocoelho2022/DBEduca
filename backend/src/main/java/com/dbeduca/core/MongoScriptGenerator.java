package com.dbeduca.core;

import java.util.stream.Collectors;

public final class MongoScriptGenerator implements ScriptGenerator {
    @Override public DatabaseEngine engine() { return DatabaseEngine.MONGODB; }

    @Override
    public String generate(TableDefinition table) {
        String fields = table.columns().stream()
            .map(c -> "    " + c.name() + ": <" + mongoHint(c.type()) + ">")
            .collect(Collectors.joining(",\n"));

        return "db.createCollection(\"" + table.name() + "\");\n\n"
            + "// Estrutura sugerida para os documentos:\n"
            + "// {\n"
            + fields.lines().map(line -> "// " + line).collect(Collectors.joining("\n"))
            + "\n// }";
    }

    private String mongoHint(String sqlType) {
        String t = sqlType.toUpperCase();
        if (t.startsWith("BIGINT") || t.startsWith("INT") || t.startsWith("INTEGER")) return "NumberLong";
        if (t.startsWith("DECIMAL") || t.startsWith("NUMERIC") || t.startsWith("DOUBLE") || t.startsWith("FLOAT")) return "Number";
        if (t.startsWith("BOOLEAN") || t.startsWith("BOOL")) return "Boolean";
        if (t.startsWith("DATE") || t.startsWith("TIMESTAMP")) return "Date";
        return "String";
    }
}
