package com.dbeduca.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ScriptGeneratorRegistry {
    private final Map<DatabaseEngine, ScriptGenerator> generators = new EnumMap<>(DatabaseEngine.class);

    public ScriptGeneratorRegistry(List<ScriptGenerator> generators) {
        generators.forEach(generator -> this.generators.put(generator.engine(), generator));
    }

    public String generate(DatabaseEngine engine, TableDefinition table) {
        var generator = generators.get(engine);
        if (generator == null) {
            throw new IllegalArgumentException("Engine não suportada: " + engine);
        }
        return generator.generate(table);
    }
}
